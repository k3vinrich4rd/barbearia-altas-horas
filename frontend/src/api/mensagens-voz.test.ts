// @vitest-environment node
// Voz da barbearia (Fatia 14B, seção 5 e regra E21 da spec).
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, test } from "vitest";
import { parse } from "yaml";
import { DUVIDAS } from "@/conteudo/historia";
import { TEXTOS } from "@/conteudo/textos";
import { API_INDISPONIVEL } from "./erro";
import { MENSAGEM_GENERICA, MENSAGENS, TEXTO_ACORDANDO, TEXTO_SEM_RESPOSTA } from "./mensagens";

const contrato = parse(readFileSync(resolve(__dirname, "../../../docs/contrato/openapi.yaml"), "utf8"));
const CODIGOS: string[] = contrato.components.schemas.CodigoErro.enum;

/** O catálogo inteiro que a regra E21 cobre. */
const CATALOGO: [string, string][] = [
  ...Object.entries(MENSAGENS),
  ["MENSAGEM_GENERICA", MENSAGEM_GENERICA],
  ["TEXTO_ACORDANDO", TEXTO_ACORDANDO],
  ["TEXTO_SEM_RESPOSTA", TEXTO_SEM_RESPOSTA],
];

const PROIBIDAS = [
  /servidor/i,
  /sess[ãa]o/i,
  /requisi[çc][ãa]o/i,
  /\btoken\b/i,
  /\bAPI\b/,
  /erro interno/i,
  /inv[áa]lid/i,
  /falh/i,
];

/** Verbos de próximo passo (E21: escolha, toque, confira, entre, espere, chame... e os da mesma família). */
const VERBOS =
  /(?<!\p{L})(escolha|toque|confira|entre|espere|chame|tente|recarregue|digite|informe|aceite|ajuste|cancele|peça|fale|use|comece|volte|crie|continue|salve|envie|veja|procure)(?!\p{L})/iu;

/**
 * Exceções aprovadas palavra por palavra na seção 5 (A37), em que o próximo passo é dito como fato: o botão de mais tempo
 * só aparece no último minuto (a tela nem o mostra antes); e a API dormindo é chamada de novo sozinha (o passo é esperar).
 */
const ACORDANDO = /a gente tenta de novo sozinho\.$/;
const PASSO_DESCRITO: Record<string, RegExp> = {
  PRORROGACAO_FORA_DA_JANELA: /O botão aparece no último minuto\.$/,
  API_INDISPONIVEL: ACORDANDO,
  TEXTO_ACORDANDO: ACORDANDO,
};

describe("mensagens com a voz da barbearia (Fatia 14B)", () => {
  test("A34: nenhuma palavra proibida (servidor, sessão, requisição, token, API, erro interno, inválido, falha) em MENSAGENS, MENSAGEM_GENERICA, TEXTO_ACORDANDO e TEXTO_SEM_RESPOSTA", () => {
    expect(CATALOGO.length).toBeGreaterThan(40);
    for (const [chave, texto] of CATALOGO) {
      for (const proibida of PROIBIDAS) expect(texto, `${chave} tem ${proibida}`).not.toMatch(proibida);
    }
  });

  test("A35: todo texto de erro do catálogo tem um verbo de ação (o próximo passo)", () => {
    for (const [chave, texto] of CATALOGO)
      expect(texto, `${chave} sem próximo passo`).toMatch(PASSO_DESCRITO[chave] ?? VERBOS);
  });

  test("A36: nenhum número de regra configurável fixo no catálogo (só o {n} do LIMITE_EXCEDIDO)", () => {
    for (const [chave, texto] of CATALOGO) {
      expect(texto, chave).not.toMatch(/\d+ (hora|horas|dia|dias|minuto|minutos)\b/i);
    }
    expect(MENSAGENS.LIMITE_EXCEDIDO).toContain("{n}");
  });

  test("A37: os 20 textos da seção 5 e os também revistos são os aprovados, palavra por palavra", () => {
    const aprovados: [string, string][] = [
      [TEXTOS.carregando, "Só um instante, já estamos trazendo."],
      [
        MENSAGENS[API_INDISPONIVEL],
        "A agenda estava descansando e já está acordando. Leva uns segundos, e a gente tenta de novo sozinho.",
      ],
      [
        TEXTO_ACORDANDO,
        "A agenda estava descansando e já está acordando. Leva uns segundos, e a gente tenta de novo sozinho.",
      ],
      [MENSAGENS.BANCO_INDISPONIVEL, "A agenda está acordando. Espere uns segundos e toque em Tentar de novo."],
      [
        TEXTO_SEM_RESPOSTA,
        "A agenda não respondeu desta vez. Toque em Tentar de novo; se continuar, chame a gente no WhatsApp.",
      ],
      [MENSAGENS.HORARIO_INDISPONIVEL, "Alguém pegou esse horário agorinha. Escolha outro logo abaixo."],
      [TEXTOS.diaCheio, "Esse dia já está cheio. Veja o próximo dia com horário."],
      [MENSAGENS.ANTECEDENCIA_INSUFICIENTE, "Esse horário já passou. Escolha um horário mais para frente."],
      [TEXTOS.notaAgendar, "Só aparecem os horários livres. Pode marcar para hoje ou para qualquer data futura."],
      [MENSAGENS.FORA_DO_HORIZONTE, "Essa data não está disponível. Escolha outra em Outro dia."],
      [MENSAGENS.VALIDACAO_INVALIDA, "Falta ajustar os campos marcados. Confira e envie de novo."],
      [MENSAGENS.EMAIL_JA_CADASTRADO, "Esse e-mail já tem conta aqui. Entre com ele, ou use outro e-mail."],
      [
        MENSAGENS.CREDENCIAIS_INVALIDAS,
        "E-mail ou senha não conferem. Confira e tente de novo; o botão Mostrar a senha ajuda.",
      ],
      [
        MENSAGENS.REFRESH_INVALIDO,
        "Por segurança, você saiu da conta depois de um tempo parado. Entre de novo para continuar.",
      ],
      [MENSAGENS.CPF_INVALIDO, "Esse CPF não confere. Confira os 11 números."],
      [
        MENSAGENS.GATEWAY_INDISPONIVEL,
        "Não conseguimos gerar o pagamento agora. Seu horário continua separado até o fim do contador: toque em Pagar de novo.",
      ],
      [TEXTOS.esperandoPagamento, "Assim que o pagamento cair, a gente confirma aqui. Pode deixar esta tela aberta."],
      [
        TEXTOS.ultimoMinutoComBotao("Acrescentar 2 minutos"),
        "Menos de 1 minuto. Se precisar, toque em Acrescentar 2 minutos.",
      ],
      [TEXTOS.ultimoMinuto, "Menos de 1 minuto para pagar."],
      [TEXTOS.prazoZerado, "Conferindo se o pagamento chegou…"],
      [
        TEXTOS.tempoAcabou,
        "O tempo para pagar acabou e o horário voltou para a agenda. Nada foi cobrado. Escolha um horário de novo.",
      ],
      [
        TEXTOS.redeCaiuNaReserva,
        "A conexão caiu no meio do caminho e não sabemos se a reserva entrou. Confira em Meus agendamentos antes de tentar de novo.",
      ],
      [
        MENSAGENS.ERRO_INTERNO,
        "Deu um problema do nosso lado, não foi você. Tente de novo; se continuar, chame a gente no WhatsApp.",
      ],
      [
        MENSAGEM_GENERICA,
        "Deu um problema do nosso lado, não foi você. Tente de novo; se continuar, chame a gente no WhatsApp.",
      ],
      // também revistos
      [MENSAGENS.ACESSO_NEGADO, "Esta área é só da equipe da barbearia. Volte para o início."],
      [MENSAGENS.NAO_AUTENTICADO, "Entre na sua conta para continuar."],
      [TEXTOS.confirmado, "Fechado! Seu horário está garantido."],
      [TEXTOS.painelSemClientes, "Nenhum cliente marcado neste dia ainda."],
      [
        MENSAGENS.PRORROGACAO_FORA_DA_JANELA,
        "Ainda não é hora de acrescentar tempo. O botão aparece no último minuto.",
      ],
      [MENSAGENS.CONFLITO_CONCORRENTE, "Muita gente marcando ao mesmo tempo. Toque de novo em um instante."],
      [
        DUVIDAS.find((d) => d.pergunta === "Com quanta antecedência posso agendar?")?.resposta ?? "",
        "Quando quiser: para hoje mesmo, desde que o horário ainda não tenha passado, ou para qualquer data futura. Pagou, o horário é seu.",
      ],
      [
        TEXTOS.buscaSemHorario,
        "Não achamos horário livre nos próximos 30 dias. Procure mais 30 dias ou escolha em Outro dia.",
      ],
    ];
    for (const [atual, aprovado] of aprovados) expect(atual).toBe(aprovado);
    // sem exclamação em erro: o único "!" é o da confirmação (sucesso)
    for (const [chave, texto] of CATALOGO) expect(texto, chave).not.toContain("!");
  });

  test("MT-7: os textos da Fatia 15 são os aprovados, palavra por palavra (pedidos restantes, tempo extra esgotado e dia fechado)", () => {
    expect(TEXTOS.pedidosRestantes(9)).toBe("Restam 9 pedidos.");
    expect(TEXTOS.pedidosRestantes(2)).toBe("Restam 2 pedidos.");
    expect(TEXTOS.pedidosRestantes(1)).toBe("Resta 1 pedido.");
    expect(MENSAGENS.PRORROGACAO_ESGOTADA).toBe(
      "Você já usou todo o tempo extra. Conclua o pagamento agora ou escolha outro horário.",
    );
    expect(TEXTOS.diaFechado).toBe("Fechado nesse dia. Veja o próximo dia com horário.");
  });

  test("A38: todo code do CodigoErro do contrato 1.11.0 tem texto próprio no catálogo (inclusive PRORROGACAO_FORA_DA_JANELA)", () => {
    expect(contrato.info.version).toMatch(/^1\.1[1-9]\./);
    expect(CODIGOS).toContain("PRORROGACAO_FORA_DA_JANELA");
    for (const code of CODIGOS) {
      expect(MENSAGENS[code], `${code} sem texto`).toBeTruthy();
      // o ERRO_INTERNO é, de propósito, o mesmo texto do genérico (linha 20 da seção 5)
      if (code !== "ERRO_INTERNO") expect(MENSAGENS[code], `${code} caiu no genérico`).not.toBe(MENSAGEM_GENERICA);
    }
  });
  test("FF-11: o texto final do FOLGA_COM_RESERVAS (D214) é o aprovado, palavra por palavra", () => {
    expect(MENSAGENS.FOLGA_COM_RESERVAS).toBe(
      "Há clientes marcados nesse período. Confira a lista abaixo: você pode manter essas reservas ou cancelar uma por uma antes de salvar a folga.",
    );
  });
});
