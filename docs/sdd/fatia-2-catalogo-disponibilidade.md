# 📐 Spec · Fatia 2 · Catálogo, política e disponibilidade

| | |
|---|---|
| **Status** | 📝 **aguarda aprovação do Kevin** (escrita em 16/11/2026) |
| **Fatias do plano** | S02 (catálogo e configuração) + S03 (disponibilidade) + `GET /politica-pagamento` |
| **Branch** | `feat/fatia-2-catalogo-disponibilidade` |
| **Regras** | [R3](../contexto.md#3-regras-de-negócio) (duração e ocupação), R4 (jornada, folga, slots), R5 (antecedência e horizonte), R15 (pendente vencida não ocupa), R1/R2 (o que ocupa horário) |
| **Já entregue na Fatia 1** | a constraint `EXCLUDE USING gist` e o teste de concorrência: a Fatia 2 usa **a mesma definição de "ocupa horário"** na leitura |
| **Não cobre** | criar reserva e o `409 HORARIO_INDISPONIVEL` (Fatia 4) · editar catálogo, jornada e folgas pelo painel (Fatia 5) · login (Fatia 3): as quatro rotas desta fatia são públicas |

## 1. Objetivo

Um visitante, sem login, vê os 5 serviços com preço, o texto da política de pagamento e os **horários realmente livres** de um dia, já descontando almoço, folgas, reservas e a antecedência de 2 horas. É a primeira tela do app.

## 2. Contrato

| Método | Rota | Sucesso | Erros |
|---|---|---|---|
| `GET` | `/servicos` | `200` lista de serviços **ativos** | — |
| `GET` | `/barbeiros` | `200` lista de barbeiros **ativos** | — |
| `GET` | `/politica-pagamento` | `200` `{ versao, texto }` vigente | — |
| `GET` | `/disponibilidade?servicoId&data[&barbeiroId]` | `200` horários livres do dia | `400 VALIDACAO_INVALIDA` · `404 RECURSO_NAO_ENCONTRADO` · `422 SERVICO_INATIVO` · `422 FORA_DO_HORIZONTE` |

**Ajustes no contrato (v1.1.2), aplicados depois da aprovação desta spec:**
1. Exemplos de `GET /servicos` e `GET /barbeiros` com o catálogo e o barbeiro reais (hoje mostram os fictícios de 13/11).
2. `GET /disponibilidade`: `data` **antes de hoje** também dá `422 FORA_DO_HORIZONTE`; serviço existente porém inativo dá `422 SERVICO_INATIVO` (igual à reserva); `barbeiroId` inexistente ou inativo dá `404`.
3. `horarios` sempre com segundos e offset (`2026-12-01T07:00:00-03:00`), em ordem crescente.

## 3. Decisões de desenho

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Onde fica o cálculo? | `CalculadoraDeHorarios`: **função pura** (sem Spring, sem banco) que recebe jornada, folgas, ocupações, configuração, duração e `agora` e devolve os inícios livres | regra difícil testada em milissegundos, com dezenas de casos; o serviço só busca os dados |
| E2 | Quando um início `h` é oferecido? | `h` está na grade (do início do intervalo da jornada, de `granularidadeMin` em `granularidadeMin`) **e** `[h, h + duração + intervalo)` cabe inteiro num intervalo da jornada **e** `h ≥ agora + antecedência` (inclusivo) **e** não sobrepõe folga nem ocupação | é exatamente o que a constraint do banco vai aceitar na Fatia 4: a tela não oferece o que o banco recusaria |
| E3 | O que "ocupa" | `CONFIRMADO`, ou `AGUARDANDO_PAGAMENTO` com `expira_em > agora`; `EXPIRADO`, `CANCELADO`, `CONCLUIDO` e `NAO_COMPARECEU` não | R15: a pendente vencida libera o horário mesmo antes do job de expiração rodar (o Render hiberna, D13) |
| E4 | Quantas consultas | no máximo **5** por chamada: configuração, serviço, barbeiros + jornadas do dia, folgas do dia, ocupações do dia (filtro `inicio < fimDoDia AND fim_ocupacao > inicioDoDia`, usando o índice `reserva_barbeiro_inicio_ix`) | nada de N+1 por barbeiro ou por slot |
| E5 | Leitura de reservas | consulta de leitura (`JdbcClient`) no módulo `agenda`; a entidade JPA `Reserva` nasce na Fatia 4, quando houver escrita | não criar entidade que ainda não tem comportamento |
| E6 | Fuso | `data` é um dia em `America/Sao_Paulo`; jornada (`time`) + `data` vira `ZonedDateTime` e sai como `OffsetDateTime` | a regra é local; o banco guarda UTC (ADR 0004) |
| E7 | Formato do horário | serializado com o padrão `yyyy-MM-dd'T'HH:mm:ssXXX` | o ISO padrão do Java **omite** `:00` dos segundos; o contrato mostra os segundos e o front não pode depender de dois formatos |
| E8 | `precoFormatado` | montado à mão: `R$ 45,00`, `R$ 1.234,56` (espaço normal) | o `NumberFormat` pt-BR usa espaço não separável (` `), que quebra comparação no front e no teste |
| E9 | Ordem | serviços e barbeiros por `nome` (sem diferença de maiúsculas) | ordem estável; ordem manual de exibição só se o admin pedir (Fatia 5) |
| E10 | Barbeiro sem horário | aparece com `horarios: []` | o front mostra "sem horários neste dia" em vez de sumir com o barbeiro |
| E11 | Consistência | a disponibilidade é uma foto: um horário pode ser pego 1 ms depois. **Quem garante é a constraint** na reserva (Fatia 4, `409`) | ler com trava só deixaria a tela lenta sem garantir nada |
| E12 | Cache HTTP | `/servicos`, `/barbeiros`, `/politica-pagamento`: `Cache-Control: public, max-age=60`; `/disponibilidade`: `no-store` | catálogo muda pouco; disponibilidade muda a cada reserva |
| E13 | Política vigente | a de **maior `versao`** | mudar o texto = nova versão; a reserva guarda qual foi aceita |
| E14 | Módulos | catálogo, configuração e disponibilidade no `agenda`; política no `pagamento` | a política pertence ao checkout |

## 4. Critérios de aceite

Nos testes de integração, `agora` é fixado por um `Clock` de teste em **terça, 17/11/2026, 08:00 (-03:00)**; terça, 01/12/2026 é um dia útil livre, salvo quando o critério diz o contrário. "Serviço 30" e "serviço 60" são serviços de 30 e 60 minutos criados pelo teste.

### Catálogo e política

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | serviços ativos e um inativo | `GET /servicos` | `200`; só os ativos, por nome; cada item com `id`, `nome`, `duracaoMin`, `precoCentavos`, `precoFormatado` e **nenhum outro campo** (sem `ativo`, `version`) | `CatalogoIT` |
| A2 | preço 4500 e 123456 centavos | formatar | `R$ 45,00` e `R$ 1.234,56`, com espaço normal | `PrecoFormatadoTest` |
| A3 | um barbeiro ativo e um inativo | `GET /barbeiros` | `200`; só o ativo, com `id` e `nomeExibicao` | `CatalogoIT` |
| A4 | políticas versão 1 e 2 | `GET /politica-pagamento` | `200 { "versao": 2, "texto": ... }` | `PoliticaPagamentoIT` |
| A5 | qualquer rota de catálogo | resposta | `Cache-Control: max-age=60, public` | `CatalogoIT` |

### Disponibilidade: a grade

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A6 | terça livre, serviço 30 | consultar 01/12 | **24** horários: 07:00 a 11:30 e 13:00 a 19:30; nenhum às 12:00 ou 12:30 (almoço) | `CalculadoraDeHorariosTest`, `DisponibilidadeIT` |
| A7 | terça livre, serviço 60 (visagismo) | consultar 01/12 | **22** horários; **sem** 11:30 (invadiria o almoço) e **sem** 19:30 (passaria das 20:00) | idem |
| A8 | segunda (30/11) e domingo (06/12) | consultar | `200` com `horarios: []` | `DisponibilidadeIT` |
| A9 | `agora` = 01/12 09:10 | consultar 01/12, serviço 30 | primeiro horário **11:30** (09:10 + 2 h = 11:10, próximo da grade) | `CalculadoraDeHorariosTest` |
| A10 | `agora` = 01/12 09:00 | consultar 01/12, serviço 30 | **11:00** aparece (antecedência inclusiva, exatamente 2 h) | `CalculadoraDeHorariosTest` |
| A11 | `agora` = 01/12 09:10 | consultar 01/12, serviço 60 | primeiro horário **13:00** (11:30 invadiria o almoço) | `CalculadoraDeHorariosTest` |

### Disponibilidade: o que ocupa

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A12 | reserva `CONFIRMADO` 10:00–10:30 | serviço 30 | sem 10:00; 09:30 e 10:30 continuam | `DisponibilidadeIT` |
| A13 | a mesma reserva | serviço 60 | sem 09:30 **e** sem 10:00 (09:30–10:30 sobrepõe) | `DisponibilidadeIT` |
| A14 | `AGUARDANDO_PAGAMENTO` 10:00 com `expira_em` no futuro | consultar | 10:00 ocupado | `DisponibilidadeIT` |
| A15 | `AGUARDANDO_PAGAMENTO` 10:00 com `expira_em` no passado | consultar | 10:00 **livre** (R15) | `DisponibilidadeIT` |
| A16 | reservas `EXPIRADO` e `CANCELADO` às 10:00 | consultar | 10:00 livre | `DisponibilidadeIT` |
| A17 | folga 14:00–16:00 | serviço 30 / serviço 60 | sem 14:00 a 15:30 / também sem 13:30 | `CalculadoraDeHorariosTest`, `DisponibilidadeIT` |
| A18 | folga cobrindo o dia todo | consultar | `horarios: []` | `CalculadoraDeHorariosTest` |
| A19 | reserva de **outro** barbeiro às 10:00 | consultar com `barbeiroId` | 10:00 livre | `DisponibilidadeIT` |

### Disponibilidade: entrada e saída

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A20 | `agora` = 17/11 | `data` = 17/12 (hoje + 30) | `200` | `DisponibilidadeIT` |
| A21 | `agora` = 17/11 | `data` = 18/12, ou `data` = 16/11 (ontem) | `422 FORA_DO_HORIZONTE` em `problem+json` | `DisponibilidadeIT` |
| A22 | `servicoId` que não existe | consultar | `404 RECURSO_NAO_ENCONTRADO` | `DisponibilidadeIT` |
| A23 | serviço inativo | consultar | `422 SERVICO_INATIVO` | `DisponibilidadeIT` |
| A24 | `barbeiroId` inexistente ou inativo | consultar | `404 RECURSO_NAO_ENCONTRADO` | `DisponibilidadeIT` |
| A25 | sem `data`; `data=amanha`; `servicoId=abc` | consultar | `400 VALIDACAO_INVALIDA` com `errors[0].field` = `data`, `data` e `servicoId` | `DisponibilidadeIT` |
| A26 | sem `barbeiroId` | consultar | todos os barbeiros ativos na lista, cada um com seus horários | `DisponibilidadeIT` |
| A27 | qualquer consulta válida | resposta | `data`, `fuso: "America/Sao_Paulo"`, `servicoId`, `duracaoMin`; horários em ordem crescente, formato `2026-12-01T07:00:00-03:00`; `Cache-Control: no-store` | `DisponibilidadeIT` |
| A28 | perfil `dev` | `GET /servicos` e `GET /disponibilidade` do Corte Degradê numa terça | os 5 serviços reais e 24 horários | `SeedDevIT` (estendido) |

## 5. Entregas

| Módulo | Arquivos |
|---|---|
| `agenda` | entidades `Servico`, `Barbeiro`, `Jornada`, `Folga`, `ConfiguracaoAgenda` (leitura) · `CatalogoController` (`/servicos`, `/barbeiros`) · `DisponibilidadeController` · `ConsultaDisponibilidade` (busca os dados, E4) · `CalculadoraDeHorarios` (E1, pura) · `OcupacaoRepository` (`JdbcClient`, E5) · DTOs `record` |
| `pagamento` | entidade `PoliticaPagamento` · `PoliticaPagamentoController` |
| `comum` | `PrecoFormatado` (E8) · formato de data e hora do contrato (E7) |
| `plataforma` | `TratadorGlobalDeErros` preenche `errors[]` para parâmetro ausente e tipo inválido (A25) · exceções de negócio (`RecursoNaoEncontrado`, `RegraDeNegocio` com `CodigoErro`) |
| testes | `RelogioFixoDeTeste` (`Clock` fixo, `@Primary`) e os testes da coluna "Teste" |
| docs | contrato 1.1.2 · ERD redesenhado a partir da V1 real · guia de testes passos 2 e 3 com saída real · README |

## 6. Riscos e cuidados

- **Jornada que muda de dia** (ex.: 22:00–02:00): não existe na barbearia; o banco já impede (`hora_fim > hora_inicio`).
- **Horário de verão**: o Brasil não tem desde 2019, mas o cálculo usa `ZonedDateTime` e não soma horas "na mão"; se voltar, continua certo.
- **Serviço de 60 min e o fim do expediente**: o erro clássico é oferecer 19:30. A7 cobre.
- **Hibernate `validate`**: as entidades têm de bater com a V1 (tipos `timestamptz`, `time`, `smallint`); o `ContextoSobeIT` quebra se não baterem.
- **Dados entre testes**: os testes dividem o banco; cada um cria o próprio barbeiro e serviços, e as asserções de catálogo usam "contém" / "não contém", nunca "exatamente estes".

## 7. Pronto quando

- [ ] A1 a A28 com teste verde no `./mvnw verify`, nenhum pulado
- [ ] `redocly lint` com 0 erros no contrato 1.1.2
- [ ] respostas reais de `/servicos` e `/disponibilidade` (perfil `dev`) no guia de testes
- [ ] [checklist da PR](checklist-pr.md) completo
