"use client";
// Pagamento (A26 a A30): política, CPF só aqui, Pix ou cartão, contagem do prazo e acompanhamento do status.
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, chamar } from "@/api/cliente";
import { ErroApi } from "@/api/erro";
import { formatarDataEHora, formatarPreco } from "@/api/formato";
import { useConsulta, useEnvio } from "@/api/hooks";
import type { components } from "@/api/schema";
import { cpfValido } from "@/api/validacao";
import { Aviso, AvisoDeErro } from "@/componentes/Aviso";
import { Botao, LinkBotao } from "@/componentes/Botao";
import { Icone } from "@/componentes/Icone";
import { Campo } from "@/componentes/Campo";
import { CopiarTexto } from "@/componentes/CopiarTexto";
import { Cronometro } from "@/componentes/Cronometro";
import { EstadoDaConsulta } from "@/componentes/EstadoDaConsulta";
import { QrPix } from "@/componentes/QrPix";
import { useSessao } from "@/componentes/Sessao";

type Reserva = components["schemas"]["Agendamento"];
type Cobranca = components["schemas"]["Cobranca"];
type Metodo = components["schemas"]["MetodoPagamento"];

const INTERVALO_STATUS_MS = 3000;
const FOLGA_APOS_PRAZO_MS = 30_000;
const FINAIS = ["CONFIRMADO", "EXPIRADO", "CANCELADO"];
/** Erro que NÃO é definitivo: repetir com a MESMA chave de idempotência. */
const repetivel = (e: ErroApi) => e.acordando || e.status === 429 || e.status === 502 || e.status === 503;

export function Pagamento({ reservaId }: { reservaId: string }) {
  const roteador = useRouter();
  const sessao = useSessao();
  const politica = useConsulta(() => chamar(api().GET("/politica-pagamento")), []);

  const buscarReserva = useCallback(
    (): Promise<Reserva> =>
      sessao.autenticado
        ? chamar(api().GET("/agendamentos/{id}", { params: { path: { id: reservaId } } }))
        : chamar(api().GET("/agendamentos/guest/{id}", { params: { path: { id: reservaId } } })),
    [sessao.autenticado, reservaId],
  );
  const inicial = useConsulta(buscarReserva, [sessao.autenticado, reservaId], !sessao.carregando);
  const [atual, setAtual] = useState<Reserva | undefined>();
  const reserva = atual ?? inicial.dados;

  const [aceite, setAceite] = useState(false);
  const [cpf, setCpf] = useState("");
  const [erroCpf, setErroCpf] = useState<string | undefined>();
  const [metodo, setMetodo] = useState<Metodo>("PIX");
  const [cobranca, setCobranca] = useState<Cobranca | undefined>();
  const [acompanhando, setAcompanhando] = useState(false);
  const [simuladoDisponivel, setSimuladoDisponivel] = useState(true);
  const envio = useEnvio();
  const chaveRef = useRef<string | null>(null);
  const aceiteRef = useRef<HTMLInputElement>(null);

  const status = reserva?.status;
  const expirada = status === "EXPIRADO" || status === "CANCELADO";

  const atualizarStatus = useCallback(async () => {
    try {
      const r = await buscarReserva();
      setAtual(r);
      return r;
    } catch {
      return undefined;
    }
  }, [buscarReserva]);

  // Chegou em CONFIRMADO: segue para a confirmação.
  useEffect(() => {
    if (status === "CONFIRMADO") roteador.push(`/agendar/confirmacao/${reservaId}`);
  }, [status, reservaId, roteador]);

  // Acompanhamento: GET a cada 3 s até um estado final ou até expiraEm + 30 s.
  const expiraEm = reserva?.expiraEm ?? undefined;
  useEffect(() => {
    if (!(cobranca || acompanhando) || (status && FINAIS.includes(status))) return;
    const limite = expiraEm ? new Date(expiraEm).getTime() + FOLGA_APOS_PRAZO_MS : Infinity;
    let parar = false;
    let t: ReturnType<typeof setTimeout>;
    const ciclo = async () => {
      if (parar || Date.now() > limite) return;
      const r = await atualizarStatus();
      if (parar || (r && FINAIS.includes(r.status))) return;
      t = setTimeout(ciclo, INTERVALO_STATUS_MS);
    };
    t = setTimeout(ciclo, INTERVALO_STATUS_MS);
    return () => {
      parar = true;
      clearTimeout(t);
    };
  }, [cobranca, acompanhando, status, expiraEm, atualizarStatus]);

  async function pagar(e: React.FormEvent) {
    e.preventDefault();
    if (!aceite || !politica.dados) {
      aceiteRef.current?.focus();
      return;
    }
    if (!cpfValido(cpf)) {
      setErroCpf("CPF inválido. Confira os números.");
      document.getElementById("cpf")?.focus();
      return;
    }
    setErroCpf(undefined);
    chaveRef.current ??= crypto.randomUUID();
    const chave = chaveRef.current;
    const versaoPolitica = politica.dados.versao;
    const cpfEnviado = cpf;
    const criada = await envio.executar(async () => {
      try {
        return await chamar(
          api().POST("/pagamentos/cobrancas", {
            params: { header: { "X-Idempotency-Key": chave } },
            body: { reservaId, metodo, cpf: cpfEnviado, aceitePolitica: true, versaoPolitica },
          }),
        );
      } catch (erro) {
        if (erro instanceof ErroApi && !repetivel(erro)) chaveRef.current = null;
        throw erro;
      }
    });
    if (criada) {
      setCpf("");
      chaveRef.current = null;
      setCobranca(criada);
    } else if (envio.erro === undefined) {
      // o erro vem do estado do hook na próxima renderização
    }
  }

  // Erros específicos que mudam a tela.
  useEffect(() => {
    const c = envio.erro?.code;
    if (c === "COBRANCA_EM_ANDAMENTO") setAcompanhando(true);
    if (c === "CPF_INVALIDO") setErroCpf("CPF inválido. Confira os números.");
    if (c === "ACEITE_POLITICA_OBRIGATORIO") aceiteRef.current?.focus();
  }, [envio.erro]);

  async function simular() {
    if (!cobranca) return;
    try {
      await chamar(api().POST("/pagamentos/simulacao/{cobrancaId}/confirmar", { params: { path: { cobrancaId: cobranca.id } } }));
      await atualizarStatus();
    } catch (e) {
      if (e instanceof ErroApi && e.status === 404) setSimuladoDisponivel(false);
    }
  }

  const mostrarSimular = process.env.NEXT_PUBLIC_PAGAMENTO_SIMULADO === "true" && simuladoDisponivel;

  return (
    <>
      <p className="sobretitulo">Pagamento</p>
      <h1>Pague para garantir seu horário</h1>
      <p className="destaque">O horário já está separado para você. Ele só fica confirmado depois que o pagamento for aprovado.</p>
      <EstadoDaConsulta consulta={inicial}>
        {(r) => {
          const res = atual ?? r;
          return (
            <>
              <aside aria-labelledby="resumo-pagamento" className="resumo">
                {!expirada && res.expiraEm && <Cronometro expiraEm={res.expiraEm} aoZerar={() => void atualizarStatus()} />}
                <h2 id="resumo-pagamento">Seu horário</h2>
                <dl>
                  <dt>Serviço</dt>
                  <dd>{res.servico?.nome}</dd>
                  <dt>Quando</dt>
                  <dd>{formatarDataEHora(res.inicio)}</dd>
                  {res.servico?.duracaoMin ? (
                    <>
                      <dt>Duração</dt>
                      <dd>{res.servico.duracaoMin} min</dd>
                    </>
                  ) : null}
                  {res.barbeiro ? (
                    <>
                      <dt>Barbeiro</dt>
                      <dd>{res.barbeiro.nomeExibicao}</dd>
                    </>
                  ) : null}
                  <dt>Total</dt>
                  <dd>{formatarPreco(res)}</dd>
                </dl>
              </aside>

              {expirada ? (
                <>
                  <Aviso tipo="aviso" focar>
                    O tempo acabou e o horário voltou para a agenda. Nada foi cobrado.
                  </Aviso>
                  <LinkBotao href="/agendar" variante="principal">
                    Escolher outro horário
                  </LinkBotao>
                </>
              ) : (
                <>
                  {!cobranca && !acompanhando && (
                    <form onSubmit={pagar} noValidate aria-label="Pagar">
                      <EstadoDaConsulta consulta={politica}>
                        {(p) => (
                          <fieldset>
                            <legend>Política de pagamento</legend>
                            <p>{p.texto}</p>
                            <div className="caixa">
                              <input ref={aceiteRef} id="aceite" type="checkbox" checked={aceite} onChange={(e) => setAceite(e.target.checked)} />
                              <label htmlFor="aceite">Li e aceito a política de pagamento</label>
                            </div>
                          </fieldset>
                        )}
                      </EstadoDaConsulta>
                      <Campo
                        id="cpf"
                        rotulo="CPF"
                        inputMode="numeric"
                        autoComplete="off"
                        ajuda="Pedimos o CPF só para gerar a cobrança. Não guardamos o número."
                        value={cpf}
                        onChange={(e) => setCpf(e.target.value)}
                        erro={erroCpf}
                      />
                      <fieldset>
                        <legend>Como você quer pagar?</legend>
                        <label className="opcao">
                          <input type="radio" name="metodo" value="PIX" checked={metodo === "PIX"} onChange={() => setMetodo("PIX")} />
                          <span>Pix</span>
                        </label>
                        <label className="opcao">
                          <input type="radio" name="metodo" value="CARTAO_CREDITO" checked={metodo === "CARTAO_CREDITO"} onChange={() => setMetodo("CARTAO_CREDITO")} />
                          <span>Cartão de crédito</span>
                        </label>
                      </fieldset>
                      {envio.erro && !envio.erro.acordando && envio.erro.code !== "COBRANCA_EM_ANDAMENTO" && envio.erro.code !== "CPF_INVALIDO" && (
                        <>
                          <AvisoDeErro erro={envio.erro} />
                          {envio.erro.code === "RESERVA_EXPIRADA" && <Link href="/agendar">Escolher outro horário</Link>}
                        </>
                      )}
                      {envio.erro?.acordando && <Aviso tipo="aviso">O servidor está acordando. Toque em Pagar de novo em instantes: o pagamento não será cobrado duas vezes.</Aviso>}
                      {envio.esperando && <Aviso tipo="aviso">Tente de novo em {envio.restante} s.</Aviso>}
                      <Botao type="submit" variante="principal" disabled={!aceite || envio.enviando || envio.esperando}>
                        {envio.enviando ? "Gerando cobrança…" : "Pagar"}
                      </Botao>
                    </form>
                  )}

                  {acompanhando && !cobranca && <Aviso tipo="aviso">Já existe um pagamento em andamento para esta reserva. Estamos acompanhando.</Aviso>}

                  {cobranca && (
                    <section aria-labelledby="como-pagar">
                      <h2 id="como-pagar">{cobranca.metodo === "CARTAO_CREDITO" ? "Pague com cartão" : "Pague com Pix"}</h2>
                      {cobranca.pix?.copiaECola && (
                        <>
                          <CopiarTexto id="pix" rotulo="Pix copia e cola" texto={cobranca.pix.copiaECola} />
                          <QrPix copiaECola={cobranca.pix.copiaECola} qrCodeBase64={cobranca.pix.qrCodeBase64} />
                        </>
                      )}
                      {cobranca.cartao?.urlCheckout && (
                        <p>
                          <a className="botao botao-principal" href={cobranca.cartao.urlCheckout}>
                            Ir para o pagamento com cartão
                          </a>
                        </p>
                      )}
                      <p role="status">Aguardando a confirmação do pagamento…</p>
                      {mostrarSimular && (
                        <Botao onClick={simular}><Icone nome="visto" /> Simular pagamento</Botao>
                      )}
                    </section>
                  )}
                </>
              )}
            </>
          );
        }}
      </EstadoDaConsulta>
    </>
  );
}
