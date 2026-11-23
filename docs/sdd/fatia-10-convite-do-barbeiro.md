# 📐 Spec · Fatia 10 · Convite e login do barbeiro terceiro

| | |
|---|---|
| **Status** | ✅ aprovada pelo Kevin (23/11/2026), com a D-1 ajustada: o dono só gera o convite e o **barbeiro digita o próprio e-mail** e a senha ao aceitar; as demais dúvidas seguem a proposta |
| **Origem** | pedido do Kevin em 23/11/2026, sobre o [ADR 0013](../adr/0013-varios-barbeiros-dono-e-convite.md) (aceito em 20/11) e a D151. Esta fatia reúne as 10A (política única de acesso) e 10B (convite e desligamento) do ADR, porque o isolamento entre barbeiros é o que torna o convite seguro |
| **Entrega em duas PRs** (proposta) | **10A** · política única `AcessoAgenda` e testes de isolamento, sem contrato e sem migração (`feat/fatia-10a-acesso-a-agenda`) · **10B** · convite, aceite, desligamento e telas (`feat/fatia-10b-convite-do-barbeiro`). A 10B só começa com a 10A mergeada |
| **Regras** | R23 (alheio = `404`), R24 (e-mail único), R26 (limite de tentativas), R30 (nada pessoal em log), R33 e R34 (reserva manual e WhatsApp do barbeiro); [H24 a H33](../contexto.md#4-hipóteses-em-aberto) (todas decididas); [D151](../planejamento/decisoes.md) |
| **ADRs** | [0013](../adr/0013-varios-barbeiros-dono-e-convite.md) (decisão), [0005](../adr/0005-erros-rfc-9457.md) (`404` sem oráculo), [0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) (sessão), [0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) (LGPD), [0002](../adr/0002-comunicacao-entre-modulos.md) (módulos) |
| **Contrato** | `1.7.1 → 1.8.0`, só acréscimos |
| **Custo** | **zero**: sem e-mail, sem SMS e sem serviço novo (D59); o link vai por WhatsApp ou em pessoa (H26) |
| **Não cobre** | carteira de clientes por barbeiro (H24 = não, 10C descartada) · cobrança, comissão ou repasse ao barbeiro (fora do beta) · o barbeiro editar a própria jornada ou folgas (H27) · o barbeiro cancelar reserva (H28, D124) · e-mail de convite (gatilho do ADR) · o alcance por nível, que é a [Fatia 11](fatia-11-alcance-por-nivel.md) |

## 1. Objetivo

O Dono/Admin cadastra um barbeiro e gera um **convite de uso único que expira**. O barbeiro abre o link, cria o **próprio** login e senha e passa a ver **só** a própria agenda, fechar **só** os próprios atendimentos e travar horários **só** na própria agenda (reserva manual), para os clientes que marcaram com ele pelo WhatsApp. Os clientes dele marcam pelo app, com ou sem cadastro, escolhendo esse barbeiro. Dinheiro, devoluções, cadastros, auditoria e convites ficam só com o Dono/Admin. Um barbeiro nunca lê nem escreve a agenda, os clientes, o alcance ou as pendências de outro.

## 2. Contrato

Versão **1.8.0** do [`openapi.yaml`](../contrato/openapi.yaml) e do [`contrato-api.md`](../contrato/contrato-api.md) (`npx -y @redocly/cli@latest lint` com 0 erros). Só acréscimos; os tokens, os papéis e as telas atuais continuam valendo.

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/admin/barbeiros/{id}/convite` (**nova**) | só `ADMIN` | `201` `{ codigo, expiraEm }` com `Cache-Control: no-store`. É também o **reenviar**: emitir de novo revoga o convite pendente e devolve um código novo | `400 VALIDACAO_INVALIDA` (e-mail inválido) · `400 CAMPO_DESCONHECIDO` · `401` · `403` (BARBEIRO, CLIENTE; ADMIN com senha inicial: `TROCA_DE_SENHA_OBRIGATORIA`) · `404` (barbeiro inexistente) · `409 USUARIO_JA_VINCULADO` (barbeiro já tem conta ativa, ou e-mail de conta de equipe) |
| `DELETE` | `/admin/barbeiros/{id}/convite` (**nova**) | só `ADMIN` | `204` (idempotente: sem convite pendente também é `204`) | `401` · `403` · `404` (barbeiro inexistente) |
| `POST` | `/auth/convite` (**nova**) | público, com limite por IP; o token do cliente é opcional | `204`, `Cache-Control: no-store` | `400 VALIDACAO_INVALIDA` (formato do código, e-mail ou senha) · `400 CAMPO_DESCONHECIDO` · `422 CONVITE_INVALIDO` (**uma resposta só**) · `429 LIMITE_EXCEDIDO` com `Retry-After` |
| `GET` | `/admin/barbeiros` e `/admin/barbeiros/{id}` (mudam) | só `ADMIN` | `BarbeiroAdmin` ganha `acesso` (só leitura): `SEM_CONTA`, `CONVITE_PENDENTE`, `ATIVO` ou `DESLIGADO`, e `conviteExpiraEm` (nulo fora de `CONVITE_PENDENTE`). O e-mail do barbeiro **não** volta | |
| `PUT` | `/admin/barbeiros/{id}` (muda a descrição) | só `ADMIN` | com `ativo: false` o desligamento tira o acesso (item E9). Reativar exige convite novo (H31) | |

Corpos:

- `POST /admin/barbeiros/{id}/convite`: `{ email? }`. O `email` é obrigatório quando o barbeiro ainda **não** tem conta ligada; num reenvio, sem o campo, vale o e-mail já ligado.
- `POST /auth/convite`: `{ codigo, email, senha? }` (o `email` é a proposta D-1; sem a D-1, o corpo é `{ codigo, senha? }` como no ADR). `senha` só vale para **conta nova**. Para conta que **já tem senha** (H32) o corpo é `{ codigo, email }`, exige o token da própria conta, e mandar `senha` é `400 CAMPO_DESCONHECIDO`.
- `codigo`: 43 caracteres base64url (32 bytes de `SecureRandom`); `senha`: a mesma regra de `POST /auth/cadastro` e de `PUT /me/senha`, de 10 a 72 **bytes** em UTF-8 (Fatia 3, E2).

Código de erro novo: **`422 CONVITE_INVALIDO`** (inexistente, vencido, usado, revogado, e-mail que não bate, ou de outra conta; **o mesmo corpo, o mesmo título e a mesma mensagem nos seis casos**).

### Banco · migração `V?__convite_de_conta.sql`

Vem depois da migração do catálogo inicial (se aquela for a `V8`, esta é a `V9`). Só **uma tabela nova**, nenhuma alteração de tabela existente, nenhum dado pessoal em seed:

```sql
CREATE TABLE convite_conta (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    token_hash   char(64)    NOT NULL UNIQUE,      -- SHA-256 hex do código; o código nunca é guardado
    expira_em    timestamptz NOT NULL,
    usado_em     timestamptz,
    revogado_em  timestamptz,
    tentativas   smallint    NOT NULL DEFAULT 0,   -- falhas de e-mail com código certo (E8)
    criado_por   uuid        NOT NULL REFERENCES usuario (id),
    criado_em    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT convite_conta_hash_ck CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
-- um convite pendente por conta
CREATE UNIQUE INDEX convite_conta_pendente_ux ON convite_conta (usuario_id)
    WHERE usado_em IS NULL AND revogado_em IS NULL;
```

`usuario` e `usuario_papel` **não mudam**: a conta convidada nasce `ativo = false` e `senha_hash = NULL`, que o `V1` já aceita e o login já recusa. `CHECK` de `usuario_papel` continua com os três papéis (nenhum papel novo, ADR 0013, opção B).

## 3. Decisões de desenho

| # | Tema | Decisão | Por quê |
|---|---|---|---|
| E1 | Papéis | **Nenhum papel novo.** "Barbeiro terceiro" é um `BARBEIRO` comum; "Dono/Admin" é o rótulo de tela do `ADMIN`. O dono que atende tem `ADMIN` + `BARBEIRO` + vínculo | ADR 0013, opção B: zero migração de papel e zero quebra de contrato |
| E2 | Isolamento | O `barbeiroId` do BARBEIRO sai **sempre do token** (`sub` → `barbeiro.usuario_id` com `barbeiro.ativo`), nunca da URL nem do corpo. Política única `agenda/AcessoAgenda` (`alvo`, `exigirDonoDaReserva`, `proprio`), chamada por `PainelDaAgenda`, `AlcanceDaAgenda` e `CadastrosDoPainel`; as três cópias saem. Alheio, inexistente ou sem vínculo = `404` igual | D151; `@PreAuthorize` precisaria do banco no SpEL e daria `403` (ADR 0005, R23) |
| E3 | Convite | Código de 32 bytes de `SecureRandom`, mostrado **uma vez** na tela do dono como link `/convite#<codigo>` e QR. O banco guarda só o SHA-256 (sem BCrypt: a entropia é de 256 bits). Vale por `app.conta.convite.validade` (padrão **48 h**, H25; a subida falha se ficar fora de **1 h a 7 dias**). Um pendente por conta (índice parcial) | o código não é senha: expira, serve uma vez e é revogável |
| E4 | Uso único | O aceite é um `UPDATE ... SET usado_em = :agora WHERE token_hash = :hash AND usado_em IS NULL AND revogado_em IS NULL AND expira_em > :agora`, com o `Clock` (ADR 0004). Só segue se afetou **uma** linha | dois aceites ao mesmo tempo: um só vence |
| E5 | Abrir não consome | `GET /convite` (página) não chama a API com o código; só o `POST` consome. O código vai no **fragmento** (`#`), que nem a prévia de link do WhatsApp nem o servidor recebem | H26: robô de prévia não queima o link |
| E6 | Ordem das checagens no `POST /auth/convite` | (1) forma do corpo e da senha, **sem tocar no banco** → `400`; (2) limite por IP → `429`; (3) busca pelo hash, com o relógio → `422` genérico. Assim o `400` da senha fraca nunca revela se o código existe | sem oráculo (sem enumeração) |
| E7 | Sem enumerar e-mail | A emissão devolve o mesmo `201` para e-mail novo e para e-mail de conta de cliente. O aceite com e-mail errado é o mesmo `422`. O e-mail do barbeiro não volta em nenhuma lista | LGPD art. 6º, III (necessidade) e R30 |
| E8 | Tentativas por convite | Além do limite por IP (H30: **10 por hora por IP**, `app.limites.convite-max` e `convite-janela`), um convite com código **certo** e e-mail errado soma `tentativas`; na 5ª (`app.conta.convite.tentativas-max`) é **revogado**. O dono vê `acesso = SEM_CONTA` e gera outro | quem tem o link mas não sabe o e-mail não adivinha em silêncio |
| E9 | Conta que já existe (H32) | Convite para o e-mail de um cliente não cria conta: fica ligado a ela e o papel `BARBEIRO` só entra no aceite, que **exige estar logado nessa conta** (`sub` = `convite.usuario_id`), soma o papel e sobe `versao_credencial`. O convite **nunca** define nem troca a senha de conta que já tem senha (fecha o sequestro por quem tiver o link). E-mail de equipe (`ADMIN` ou `BARBEIRO` vinculado) = `409 USUARIO_JA_VINCULADO` | ADR 0013, 4.1 |
| E10 | Desligar (`ativo: false`) | Sempre: `barbeiro.ativo = false`, tira o papel `BARBEIRO`, sobe `versao_credencial` (o `ContaEmUso` barra o token na hora), revoga o convite e anula `barbeiro.whatsapp`. Conta **também de cliente**: segue ativa como cliente, com dados e reservas; a H33 não se aplica à conta. Conta **só de equipe**: desativa e **anonimiza** no mesmo passo (H33 revisada): anula `senha_hash`, apaga refresh tokens, convite e identidade social, troca nome e e-mail por "lápide". As reservas e a auditoria continuam apontando para a conta | H31 e H33; `auditoria_admin.ator_id` continua válido |
| E11 | Retenção depois do desligamento | `nome_exibicao` nas reservas por **5 anos** depois da última reserva e depois pseudônimo (job `RetencaoDeDados`, ShedLock, `Clock`); IP da auditoria 1 ano (D99); backup 30 dias (D128). **Pendente de advogado:** art. 10 da LGPD e o tipo de vínculo (CLT ou Lei 13.352/2016) | H33; ver D-6 |
| E12 | Orquestração | `admin → conta` e `admin → agenda` (ADR 0001/0002): o caso de uso do `admin` chama `conta.Contas` (`convidarBarbeiro`, `revogarConvite`, `desligarContaDeEquipe`) e `PainelDaAgenda.alterarBarbeiro` na **mesma transação** da auditoria. A rota pública `/auth/convite` fica no `conta`, que não depende da `agenda` | o vínculo já foi gravado na emissão |
| E13 | Auditoria | Só nomes de campos, nunca o código, o e-mail, o nome ou a senha (R30). Ações: `CONVITE_EMITIDO` (ator = dono), `CONVITE_REVOGADO` (dono), `CONVITE_REVOGADO_POR_TENTATIVAS` (ator = sistema), `CONVITE_ACEITO` (ator = o próprio barbeiro) e `BARBEIRO_DESLIGADO` (dono). As duas do meio e a `CONVITE_ACEITO` **acrescentam** ao ADR (que citava três) | quem fez o quê e quando, sem dado pessoal |
| E14 | Clientes do barbeiro | Os clientes dele agendam como hoje (`GET /barbeiros`, `GET /disponibilidade?barbeiroId=`, `POST /agendamentos` e `/agendamentos/guest`), com ou sem cadastro; a reserva leva o `barbeiro_id` e só aparece na agenda dele. O que ele combinar por WhatsApp entra pela **reserva manual própria** (nome opcional, sem contato, R33). **Sem carteira de clientes** (H24) | pedido do Kevin; nada novo no contrato |
| E15 | Dinheiro | Só o `ADMIN` vê e mexe em `/admin/pagamentos/**`, devoluções, `/admin/auditoria`, `/admin/configuracao`, `/admin/servicos/**` e `/admin/barbeiros/**` (incluindo convites). O BARBEIRO vê preço e status do pagamento **só das próprias reservas** (H29) | H27, H28 e H29 |
| E16 | Senha | Mesma política do cadastro (10 a 72 bytes, BCrypt custo `app.seguranca.bcrypt-custo`); sem regra de composição. Senha nunca por chat, e-mail nem log: quem a define é o barbeiro, no app | Fatia 3, E2 e E3 |

## 4. Critérios de aceite

Convenção dos testes: dois barbeiros reais no banco, **A** e **B** (Testcontainers), com reservas de conta, de visitante e manuais em cada um; um `Clock` fixo. `ConviteDoBarbeiroIT` e `IsolamentoEntreBarbeirosIT` são as classes principais.

### 10A · Isolamento entre barbeiros (sem contrato e sem migração)

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | o código da `agenda` | roda o `ArquiteturaTest` | só `AcessoAgenda` chama `BarbeiroRepository.findByUsuarioId`; nenhum fonte da `agenda` fora dela tem SQL filtrando `barbeiro` por `usuario_id`; `PainelDaAgenda` e `AlcanceDaAgenda` não têm mais a cópia | `ArquiteturaTest` |
| A2 | BARBEIRO A | `GET /admin/agenda?barbeiroId=<B>` · e sem filtro | `404 RECURSO_NAO_ENCONTRADO` · só as reservas de A | `IsolamentoEntreBarbeirosIT` |
| A3 | reserva de B | A faz `PATCH /admin/agendamentos/{reservaDeB}/status` (`CONCLUIDO`, `NAO_COMPARECEU`, `CANCELADO`) | `404` nos três (o teste de dono vem **antes** do `403` do cancelamento, D124); a reserva de B não muda | `IsolamentoEntreBarbeirosIT` |
| A4 | horário livre de B | A faz `POST /admin/agendamentos` com `barbeiroId = B` | `404`; nenhuma reserva gravada e nenhuma linha de auditoria | `IsolamentoEntreBarbeirosIT` |
| A5 | A e B com atendimentos em bairros diferentes | A faz `GET /admin/alcance?barbeiroId=<B>` | recebe os números de A, nunca os de B (E36 da Fatia 6C); o corpo não traz sinal do filtro ignorado | `IsolamentoEntreBarbeirosIT` |
| A6 | A e B com jornadas diferentes | A faz `GET /admin/minha-jornada` | a jornada de A | `IsolamentoEntreBarbeirosIT` |
| A7 | BARBEIRO A | chama `/admin/barbeiros/**` (inclui convites), `/admin/servicos/**`, `/admin/configuracao`, `/admin/pagamentos/**` (pendências, devolução, eventos) e `/admin/auditoria` | `403 ACESSO_NEGADO` em todas, sem corpo de dado | `IsolamentoEntreBarbeirosIT` |
| A8 | `barbeiro.ativo = false` de A, conta ainda ativa | A chama as rotas do painel com escopo próprio | `404` em todas | `IsolamentoEntreBarbeirosIT` |
| A9 | ADMIN | `GET /admin/agenda` sem filtro · `?barbeiroId=<B>` · `?barbeiroId=<inexistente>` | A e B · só B · `404` | `IsolamentoEntreBarbeirosIT` |
| A10 | reserva de B e id inexistente | A pede cada uma | `404` com o **mesmo** `code`, `title` e formato, sem o id no corpo | `IsolamentoEntreBarbeirosIT` |
| A11 | cliente com conta e visitante marcaram com A; outro cliente marcou com B | agenda de A e de B; `GET /admin/agenda` de A | cada um vê só as próprias reservas e os nomes dos clientes dele; nenhum nome, WhatsApp ou e-mail de cliente de B aparece para A, nem em erro | `IsolamentoEntreBarbeirosIT` |
| A12 | reserva manual de A às 15:00 | disponibilidade de A e de B às 15:00 | A: ocupado; B: livre; a reserva manual de A não aparece na agenda de B | `IsolamentoEntreBarbeirosIT` |

### 10B · Convite, aceite e desligamento

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A13 | barbeiro sem conta, ADMIN | `POST /admin/barbeiros/{id}/convite` com `{ email }` | `201 { codigo, expiraEm }` com `Cache-Control: no-store`; conta `BARBEIRO` **inativa e sem senha**, ligada ao barbeiro; no banco só o `token_hash` (o código não está em nenhuma coluna) | `ConviteDoBarbeiroIT` |
| A14 | convite válido | `POST /auth/convite` com código, e-mail e senha | `204`; conta ativa; login com o e-mail e a senha novos funciona e `GET /admin/agenda` mostra **só** a agenda daquele barbeiro | `ConviteDoBarbeiroIT` |
| A15 | convite válido | senha com 9 bytes, 73 bytes e 40 letras acentuadas (80 bytes) | `400 VALIDACAO_INVALIDA` no campo `senha`; o convite **não** é consumido e o mesmo código ainda aceita uma senha boa | `ConviteDoBarbeiroIT` |
| A16 | código já usado · vencido (relógio +48 h) · revogado · inventado · e-mail que não bate · de outra conta | `POST /auth/convite` | `422 CONVITE_INVALIDO` nos seis, com o **mesmo** corpo, título e mensagem | `ConviteSegurancaIT` |
| A17 | convite válido | dois `POST /auth/convite` ao mesmo tempo (dois fios) | exatamente um `204` e um `422`; uma só senha gravada | `ConviteSegurancaIT` |
| A18 | convite pendente | emitir de novo (reenviar) · `DELETE .../convite` · `DELETE` repetido · `DELETE` sem convite | o código antigo dá `422` e o novo dá `204` · `204` · `204` · `204`; no reenvio sem `email` vale o e-mail já ligado | `ConviteDoBarbeiroIT` |
| A19 | `app.conta.convite.validade = 48h` e relógio fixo | aceitar em +47h59m59s · em +48h · validade configurada em 30 min · em 8 dias | `204` · `422` · vale a nova · a aplicação **não sobe** com validade fora de 1 h a 7 dias | `ConviteSegurancaIT` |
| A20 | limite de 10 aceites por hora por IP | 11 `POST /auth/convite` (códigos inventados) do mesmo IP | do 11º em diante `429 LIMITE_EXCEDIDO` com `Retry-After`; outro IP não é afetado; a contagem inclui os pedidos inválidos | `ConviteSegurancaIT` |
| A21 | convite válido | 5 `POST` com o código certo e e-mail errado | todos `422` iguais; na 5ª o convite fica **revogado** (`acesso = SEM_CONTA`, `CONVITE_REVOGADO_POR_TENTATIVAS` na auditoria) e o código certo também dá `422` | `ConviteSegurancaIT` |
| A22 | e-mail novo, e-mail de cliente e e-mail inexistente | emitir convite | o mesmo `201` e a mesma forma de resposta; e-mail de conta de equipe é `409 USUARIO_JA_VINCULADO`; nenhuma lista devolve e-mail | `ConviteSegurancaIT` |
| A23 (H32) | cliente `ana@x` com conta e senha; convite emitido para `ana@x` | `POST /auth/convite` sem login · logado em **outra** conta · logado em `ana@x` · logado em `ana@x` mandando `senha` | `422` · `422` · `204` (soma `BARBEIRO` e sobe `versao_credencial`) · `400 CAMPO_DESCONHECIDO`; a senha antiga continua valendo | `ConviteContaExistenteIT` |
| A24 (H32) | conta `CLIENTE` + `BARBEIRO` (reserva de cliente com B, agenda própria) | painel e rotas de cliente | no painel, só a própria agenda; nas rotas de cliente, só as próprias reservas, inclusive as marcadas com outro barbeiro, sem enxergar a agenda dele | `ConviteContaExistenteIT` |
| A25 | BARBEIRO · CLIENTE · sem token · ADMIN com senha inicial | `POST` e `DELETE /admin/barbeiros/{id}/convite` | `403` · `403` · `401` · `403 TROCA_DE_SENHA_OBRIGATORIA` | `ConviteDoBarbeiroIT` |
| A26 | barbeiro **só de equipe** com sessão aberta e convite pendente | `PUT /admin/barbeiros/{id}` com `ativo: false` | token antigo `401` na hora; refresh falha; convite revogado; conta anonimizada (nome, e-mail, `senha_hash`, `whatsapp`, refresh e identidade social anulados; `id` e papel preservados); as reservas e `auditoria_admin.ator_id` continuam válidos | `DesligarBarbeiroIT` |
| A27 (H32) | barbeiro que **também é cliente** | desligar | token antigo `401`; sem o papel `BARBEIRO`; a conta entra como cliente com nome, e-mail e reservas intactos | `DesligarBarbeiroIT` |
| A28 | barbeiro desligado | `PUT` com `ativo: true` · gerar convite novo e aceitar | reativar sozinho não devolve o acesso (`acesso = DESLIGADO`) · só com convite novo (H31) | `DesligarBarbeiroIT` |
| A29 | barbeiro ativo que também é cliente | `DELETE /me` | `409` pedindo que o dono desligue antes; nada apagado | `DesligarBarbeiroIT` |
| A30 (H33) | barbeiro desligado há 5 anos e 1 dia da última reserva (relógio) | job `RetencaoDeDados` | `nome_exibicao` vira pseudônimo nas reservas; antes disso o nome é mantido; IP da auditoria some depois de 1 ano; o job é idempotente | `RetencaoDeBarbeiroIT` |
| A31 | todo o fluxo (emitir, aceitar, revogar, errar, desligar) | ler log, auditoria e corpos de erro | nenhum código, e-mail, nome ou senha; a auditoria só traz nomes de campos (E13) | `SemDadoPessoalNoLogIT` (estendido) |
| A32 | barbeiros em cada estado | `GET /admin/barbeiros` | `acesso` = `SEM_CONTA`, `CONVITE_PENDENTE` (com `conviteExpiraEm`), `ATIVO` ou `DESLIGADO`; sem e-mail no JSON | `ConviteDoBarbeiroIT` |
| A33 | `openapi.yaml` 1.8.0 | `redocly lint` · `scripts/conferir-spec.sh 10 --exigir` | 0 erros; `CONVITE_INVALIDO` e as rotas novas com exemplos reais; cada `A#` tem teste com o número | CI |
| A34 | módulo `conta` | `ArquiteturaTest` | `conta` não depende de `agenda` nem de `admin`; só o `admin` chama `Contas.convidarBarbeiro`; a rota `/auth/convite` está liberada no `SegurancaConfig` e **só ela** | `ArquiteturaTest` |

### Telas (front, depois do contrato)

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A35 | Dono/Admin em Cadastros → Barbeiros | toca em "Gerar convite" e informa o e-mail | o link `/convite#<codigo>` e o QR aparecem **uma vez**, com o prazo (e o botão "Copiar link" e "Enviar pelo WhatsApp"); fechou a tela, só aparece "convite pendente até <data>" com "Gerar outro" (reenviar) e "Revogar"; o rótulo do papel `ADMIN` é "Dono/Admin" | `PainelConvite.test.tsx`, E2E |
| A36 | `/convite#<codigo>` | abrir a página (e o robô de prévia) | nenhum pedido à API leva o código; nenhum consumo; o código sai da barra de endereço depois de lido; `Referrer-Policy: no-referrer` e `Cache-Control: no-store` na página | `ConvitePagina.test.tsx`, `convite.spec.ts` |
| A37 | tela "Criar minha senha" | preencher e-mail e senha (e confirmar a senha) e enviar | o BFF chama `POST /auth/convite`, **não devolve o código** no corpo; sucesso leva ao login com a mensagem "Senha criada. Entre com seu e-mail e a nova senha"; `400` mostra o erro no campo da senha; `422` mostra uma frase só ("Este link não vale mais ou não pode ser usado aqui") com o caminho "Já tenho conta no site: entrar"; `429` mostra o tempo de espera | `ConvitePagina.test.tsx` |
| A38 | acessibilidade da tela | teclado e leitor | rótulos ligados, erro com `role="alert"`, foco no primeiro erro, senha com botão "mostrar" e `autocomplete="new-password"`, contraste do design system | `ConvitePagina.test.tsx`, axe |

## 5. Entregas

- **`agenda`**: `AcessoAgenda` (nova); `PainelDaAgenda`, `AlcanceDaAgenda` e `CadastrosDoPainel` passam a chamá-la; as cópias saem. `ArquiteturaTest` ganha a regra do A1.
- **`conta`**: `ConviteConta` (entidade), `ConviteContaRepository`, `Contas.convidarBarbeiro`, `revogarConvite`, `desligarContaDeEquipe`; `AceitarConvite` (caso de uso do `POST /auth/convite`); `ConviteController`; `CodigoDeConvite` (gera e faz o SHA-256); código de erro `CONVITE_INVALIDO` no `CodigoErroEnum`; propriedades `app.conta.convite.*` e `app.limites.convite-*`.
- **`admin`**: `ConvitesAdminController` (rotas do dono) e o caso de uso que une `conta` + `agenda` + auditoria na mesma transação; `BarbeiroAdmin.acesso` e `conviteExpiraEm`.
- **`config`**: `SegurancaConfig` libera `POST /auth/convite`; as rotas `/admin/barbeiros/**` já são só do `ADMIN`.
- **`privacidade`/retenção**: `RetencaoDeDados` ganha a troca de `nome_exibicao` por pseudônimo (E11).
- **Contrato**: `openapi.yaml` e `contrato-api.md` 1.8.0 (rotas, `BarbeiroAdmin.acesso`, `ConviteAceite`, `422 CONVITE_INVALIDO` e `429`), histórico de versões e `contexto.md` (R# novo para o convite).
- **Banco**: `V?__convite_de_conta.sql`.
- **Front** (BFF e telas): rota `/convite` e o BFF `POST /api/convite`; bloco "Convite" em Cadastros → Barbeiros; rótulo "Dono/Admin"; ver a tabela de telas abaixo.
- **Docs**: guia de testes com a saída real, política de privacidade (parágrafo sobre o barbeiro: dados e retenção, a revisar pelo Cautelar), `docs/fluxo-ponta-a-ponta.md` e o roteiro de visitantes.

### Telas (design system)

O design system é a fonte do visual: **não decido o visual aqui**. O canvas do `frontend/design/README.md` tem "Troca de senha inicial" (`TrocaSenha.dc.html`) e "Painel: cadastros" (`PainelCadastros.dc.html`), mas **nenhum dos dois cobre o convite**. Telas que **precisam de desenho** (marcadas "aguarda design"; o PDF atual não as traz):

| Tela | Situação | Observação |
|---|---|---|
| **Criar minha senha** (`/convite`) | aguarda design | campos e-mail e senha (e confirmação), regra da senha visível, estados: normal, erro de senha, `422` genérico, `429`, sucesso. Parente de `TrocaSenha.dc.html` e `Entrar.dc.html` |
| **Aceitar convite com conta existente** (H32) | aguarda design | pede o login da conta e, depois, "Aceitar convite" com o texto "isto soma o papel de barbeiro à sua conta; sua senha não muda" |
| **Link inválido ou vencido** | aguarda design | frase única, sem dizer o motivo, com "peça outro convite ao dono" |
| **Cadastros → Barbeiros: convite** | aguarda design | botão "Gerar convite", modal com link, QR, prazo e "Copiar" / "WhatsApp"; estado "convite pendente"; "Gerar outro" e "Revogar"; selo de `acesso` por barbeiro (sem conta, convite pendente, ativo, desligado); confirmação de desligamento explicando que anonimiza |
| Rótulo "Dono/Admin" no menu e no cabeçalho | ajuste | só texto, sem tela nova |

## 6. Riscos e cuidados

- **Convite que vaza.** Quem tiver o link e o e-mail cria a conta do barbeiro. Mitigação: 256 bits, 48 h, uso único, e-mail exigido (D-1), 5 tentativas, limite por IP, revogação pelo dono e o aceite nunca troca senha de conta que já tem senha (E9).
- **Oráculo de conta.** Respostas diferentes para "e-mail existe" ou "código existe" revelariam quem é cliente. Por isso: forma antes do banco, `422` único e o mesmo `201` na emissão (E6 e E7). O teste A16 compara os corpos byte a byte.
- **Barbeiro desligado com token vivo.** A conta desativada é barrada pelo `ContaEmUso` e o barbeiro inativo pela política; testado em A8 e A26.
- **Mudança de papel só no token seguinte** (até 15 min, H15) é encurtada porque somar ou tirar `BARBEIRO` sobe `versao_credencial`.
- **Refactor do `AcessoAgenda` com contrato congelado.** A 10A não pode mudar nenhuma resposta: o alcance continua trocando o `barbeiroId` (E36) e o painel continua com `404`. Os testes da Fatia 5 e 6C seguem verdes sem edição.
- **Dado de terceiro.** A reserva manual guarda nome opcional e observação, sem WhatsApp, e-mail ou CPF (R30, D125). O barbeiro pode digitar um nome real: a política deve dizer isso e a retenção de 5 anos (E11) vale só para o nome de exibição **do barbeiro**, não do cliente.
- **Retenção sem parecer.** A H33 depende do advogado (art. 10 e tipo de vínculo). Até lá a spec segue a H33 revisada e **não** amplia prazos.

### Checklist LGPD (aplicando o `cautelar-lgpd`; **não substitui parecer jurídico**, e as fontes oficiais ainda precisam ser consultadas pelo Cautelar antes do merge)

| Ponto | Situação | Observação |
|---|---|---|
| Base legal do dado do barbeiro (nome, e-mail, WhatsApp comercial, hash da senha) | 🟠 risco | execução de contrato ou relação de trabalho (LGPD art. 7º, V), conforme o vínculo, que a H33 deixa **pendente de advogado**; registrar a base escolhida na política |
| Minimização | 🟢 ok | o convite guarda só o hash; o e-mail do barbeiro não volta em lista; a reserva manual não guarda contato do cliente (art. 6º, III) |
| Retenção | 🟠 risco | 5 anos do `nome_exibicao` e pseudônimo depois são da H33 e dependem do advogado; o resto (IP 1 ano, backup 30 dias) já está no ADR 0009 e na D128 |
| Direitos do titular (exportar e excluir) | 🟠 risco | `DELETE /me` de barbeiro ativo é `409` até o desligamento (A29); a exportação do barbeiro precisa listar os dados dele (conta, vínculo, reservas) |
| Transparência | 🟠 risco | a política 1.2 precisa de um parágrafo sobre barbeiros terceiros (dados, retenção e anonimização); o texto passa pelo Cautelar |
| Dado de terceiros digitado pelo barbeiro (reserva manual) | 🟢 ok | só nome opcional e observação; sem contato (R30, D125) |
| Segurança do convite | 🟢 ok | hash, uso único, expiração, limite e `422` único (E3 a E8) |

## 7. Dúvidas para o Kevin

| # | Dúvida | Proposta (padrão se o Kevin aprovar sem mexer) |
|---|---|---|
| D-1 | O pedido diz que o barbeiro "cria o próprio login (e-mail)". O ADR 0013 manda o **dono informar o e-mail** ao gerar o convite. Quem digita o e-mail? | **Os dois**: o dono informa ao gerar (é o que permite o caso da conta que já existe, H32) e o barbeiro **digita o próprio e-mail de novo** no aceite; se não bater, é o `422` genérico. Fica o login dele e o link sozinho não basta. A alternativa (convite sem e-mail, o barbeiro escolhe qualquer um) deixaria quem tem o link criar conta com o e-mail que quiser e não distinguiria cliente de conta nova |
| D-2 | Como a página `/convite` sabe se mostra "Criar minha senha" ou "entrar para aceitar" sem uma rota nova que revele se o e-mail tem conta? | **A página mostra os dois caminhos**: o formulário "Criar minha senha" e o link "Já tenho conta no site: entrar". O `422` genérico do formulário explica as duas saídas (link vencido ou conta existente). Nenhuma rota nova. Alternativa: `POST /auth/convite/consulta` que diz `NOVA_CONTA` ou `CONTA_EXISTENTE` só a quem tem um código válido (aceitável, mas é uma rota a mais fora do ADR) |
| D-3 | Quantas tentativas erradas (com o código certo) revogam o convite? | **5** (`app.conta.convite.tentativas-max`), mais o limite de 10 por hora por IP (H30). Não vale para erro de senha fraca (`400`), que não consome nem conta |
| D-4 | O barbeiro recebe um **link direto de agendamento** para mandar aos clientes por WhatsApp (por exemplo `/agendar?barbeiro=<id>`, que já abre com ele escolhido)? | **Sim, só no front**: a página lê `barbeiro` da querystring e pré-seleciona (sem contrato novo). Fica como entrega opcional da 10B; se o Kevin não quiser agora, sai sem afetar os critérios |
| D-5 | O Dono/Admin pode **trocar o e-mail** de um barbeiro com convite pendente? | **Não**: revoga e gera outro. Evita rota nova e mantém um e-mail por convite |
| D-6 | Retenção do desligado (H33): seguimos a revisada (anonimizar no dia, `nome_exibicao` 5 anos, depois pseudônimo) mesmo com o parecer do advogado pendente? | **Sim, como beta**, e o Kevin leva ao advogado as duas perguntas abertas (art. 10 e tipo de vínculo). Nada aqui amplia prazo; se o parecer mandar encurtar, é só configuração do job |
| D-7 | A auditoria ganha três ações além das três do ADR (`CONVITE_REVOGADO_POR_TENTATIVAS`, `CONVITE_ACEITO` e a divisão do ator)? | **Sim** (E13), sem dado pessoal; é o que deixa o dono ver por que um convite sumiu |
| D-8 | Ordem de merge com a migração do catálogo e com a Fatia 11 (alcance)? | **Catálogo → 10A → 10B → Fatia 11.** O número da migração e da versão do contrato (`1.8.0`, depois `1.9.0`) segue a ordem real do merge |

## 8. Pronto quando

- [ ] todos os critérios A1 a A38 com teste verde no `./mvnw verify` (e no CI, `scripts/conferir-spec.sh 10 --exigir`)
- [ ] contrato 1.8.0 com `redocly lint` em 0 erros e o histórico de versões atualizado
- [ ] o Dono/Admin gera, reenvia e revoga convites; o barbeiro cria a senha e vê só a própria agenda (fluxo ponta a ponta com dois barbeiros reais, saída colada nos docs)
- [ ] telas "aguarda design" desenhadas e implementadas
- [ ] texto da política revisado pelo Cautelar e pendências jurídicas da H33 anotadas
- [ ] [checklist da PR](checklist-pr.md) completo; roteiro de visitantes e de equipe atualizados
