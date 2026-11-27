# 📐 Spec · Fatia 10 · Convite e login do barbeiro terceiro

| | |
|---|---|
| **Status** | ✅ aprovada pelo Kevin (23/11/2026), com a D-1 ajustada: o dono só gera o convite e o **barbeiro digita o próprio e-mail** e a senha ao aceitar; as demais dúvidas seguem a proposta. **Implementada** em 23/11/2026 (branch `feat/fatia-10-convite-do-barbeiro`); as telas novas estão **aguardando desenho** (layout neutro, só com tokens) |
| **Origem** | pedido do Kevin em 23/11/2026, sobre o [ADR 0013](../adr/0013-varios-barbeiros-dono-e-convite.md) (aceito em 20/11) e a D151. Esta fatia reúne as 10A (política única de acesso) e 10B (convite e desligamento) do ADR, porque o isolamento entre barbeiros é o que torna o convite seguro |
| **Entrega** | uma branch só (`feat/fatia-10-convite-do-barbeiro`), com commits separados: **10A** · política única `AcessoAgenda` e testes de isolamento, sem contrato e sem migração · **10B** · convite, aceite, desligamento e telas. Migração `V9`, contrato `1.8.0` |
| **Regras** | R23 (alheio = `404`), R24 (e-mail único), R26 (limite de tentativas), R30 (nada pessoal em log), R33 e R34 (reserva manual e WhatsApp do barbeiro); [H24 a H33](../contexto.md#4-hipóteses-em-aberto) (todas decididas); [D151](../planejamento/decisoes.md) |
| **ADRs** | [0013](../adr/0013-varios-barbeiros-dono-e-convite.md) (decisão), [0005](../adr/0005-erros-rfc-9457.md) (`404` sem oráculo), [0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) (sessão), [0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) (LGPD), [0002](../adr/0002-comunicacao-entre-modulos.md) (módulos) |
| **Contrato** | `1.7.1 → 1.8.0`, só acréscimos |
| **Quem pode o quê** (esclarecimento do Kevin, 23/11) | Só o **barbeiro ADMINISTRADOR** (Dono/Admin) mexe na agenda de outro barbeiro (ver, reserva manual, fechar e cancelar, quando houver problema), adiciona um barbeiro novo (convite) e o tira do sistema. O barbeiro **convidado NUNCA mexe na agenda de outro**, só na própria. "Excluir" barbeiro é o **desligamento** do ADR 0013 / H33: tira o acesso e mantém o histórico pela retenção; **não apaga de verdade** ([D153](../planejamento/decisoes.md)) |
| **Custo** | **zero**: sem e-mail, sem SMS e sem serviço novo (D59); o link vai por WhatsApp ou em pessoa (H26) |
| **Não cobre** | carteira de clientes por barbeiro (H24 = não, 10C descartada) · cobrança, comissão ou repasse ao barbeiro (fora do beta) · o barbeiro editar a própria jornada ou folgas (H27) · o barbeiro cancelar reserva (H28, D124) · e-mail de convite (gatilho do ADR) · o alcance por nível, que é a [Fatia 11](fatia-11-alcance-por-nivel.md) |

## 1. Objetivo

O Dono/Admin cadastra um barbeiro e gera um **convite de uso único que expira** (sem informar e-mail nenhum). O barbeiro abre o link, digita o **próprio e-mail**, cria a senha e passa a ver **só** a própria agenda, fechar **só** os próprios atendimentos e travar horários **só** na própria agenda (reserva manual), para os clientes que marcaram com ele pelo WhatsApp. Os clientes dele marcam pelo app, com ou sem cadastro, escolhendo esse barbeiro. Dinheiro, devoluções, cadastros, auditoria e convites ficam só com o Dono/Admin. Um barbeiro nunca lê nem escreve a agenda, os clientes, o alcance ou as pendências de outro; **só o Dono/Admin** vê, cria reserva manual, fecha e cancela na agenda de qualquer barbeiro.

## 2. Contrato

Versão **1.8.0** do [`openapi.yaml`](../contrato/openapi.yaml) e do [`contrato-api.md`](../contrato/contrato-api.md) (`npx -y @redocly/cli@latest lint` com 0 erros). Só acréscimos; os tokens, os papéis e as telas atuais continuam valendo.

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/admin/barbeiros/{id}/convite` (**nova**) | só `ADMIN` | **sem corpo** (o dono não informa e-mail). `201` `{ codigo, expiraEm }` com `Cache-Control: no-store`. É também o **reenviar**: emitir de novo revoga o convite pendente e devolve um código novo | `401` · `403` (BARBEIRO, CLIENTE; ADMIN com senha inicial: `TROCA_DE_SENHA_OBRIGATORIA`) · `404` (barbeiro inexistente) · `409 USUARIO_JA_VINCULADO` (o barbeiro já tem acesso ativo) |
| `DELETE` | `/admin/barbeiros/{id}/convite` (**nova**) | só `ADMIN` | `204` (idempotente: sem convite pendente também é `204`) | `401` · `403` · `404` (barbeiro inexistente) |
| `POST` | `/auth/convite` (**nova**) | público, com limite por IP; o token do cliente é opcional | `204`, `Cache-Control: no-store` | `400 VALIDACAO_INVALIDA` (formato do código, e-mail ou senha; senha ausente para e-mail novo) · `400 CAMPO_DESCONHECIDO` (mandou `senha` para conta que já tem senha) · `422 CONVITE_INVALIDO` (**uma resposta só**) · `429 LIMITE_EXCEDIDO` com `Retry-After` |
| `GET` | `/admin/barbeiros` e `/admin/barbeiros/{id}` (mudam) | só `ADMIN` | `BarbeiroAdmin` ganha `acesso` (só leitura): `SEM_CONTA`, `CONVITE_PENDENTE`, `ATIVO` ou `DESLIGADO`, e `conviteExpiraEm` (nulo fora de `CONVITE_PENDENTE`). O e-mail do barbeiro **não** volta | |
| `PUT` | `/admin/barbeiros/{id}` (muda a descrição) | só `ADMIN` | com `ativo: false` o **desligamento** tira o acesso e mantém o histórico (E10, D153). Reativar exige convite novo (H31) | |
| `DELETE` | `/me` (muda a descrição) | só quem tem `CLIENTE` | cliente que também é **barbeiro ativo** recebe `409 USUARIO_JA_VINCULADO` ("peça ao dono para desligar antes"); nada é apagado (A29) | |

Corpos:

- `POST /admin/barbeiros/{id}/convite`: **sem corpo** (D-1 ajustada pelo Kevin: o dono só gera o convite; quem digita o e-mail é o barbeiro).
- `POST /auth/convite`: `{ codigo, email, senha? }`. O `email` é o **próprio e-mail do barbeiro** e vira o login. `senha` só vale para **conta nova** (e-mail que ainda não tem conta; sem ela, `400` no campo `senha` e o convite não é consumido). Para conta de **cliente que já existe** (H32) o corpo é `{ codigo, email }`, exige o token **da própria conta** e mandar `senha` é `400 CAMPO_DESCONHECIDO`.
- `codigo`: 43 caracteres base64url (32 bytes de `SecureRandom`); `senha`: a mesma regra de `POST /auth/cadastro` e de `PUT /me/senha`, de 10 a 72 **bytes** em UTF-8 (Fatia 3, E2).

Código de erro novo: **`422 CONVITE_INVALIDO`** (inexistente, vencido, usado, revogado, e-mail de conta de equipe ou desativada, ou de conta que já existe sem estar logada nela; **o mesmo corpo, o mesmo título e a mesma mensagem nos seis casos**).

### Banco · migração `V9__convite_de_conta.sql`

Vem depois da `V8` (catálogo inicial). Só **uma tabela nova**, nenhuma alteração de tabela existente, nenhum dado pessoal em seed. Como o dono não informa e-mail, **não existe conta** até o aceite: o convite aponta para o **barbeiro** (e não para um `usuario`), guarda o nome de exibição dele (vira o nome da conta) e **nenhum e-mail**. Versão real (a que está em `backend/src/main/resources/db/migration/`):

```sql
CREATE TABLE convite_conta (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    barbeiro_id  uuid        NOT NULL REFERENCES barbeiro (id),
    nome         varchar(120) NOT NULL,            -- nome de exibição do barbeiro, copiado na emissão
    token_hash   char(64)    NOT NULL UNIQUE,      -- SHA-256 hex do código; o código nunca é guardado
    expira_em    timestamptz NOT NULL,
    usado_em     timestamptz,
    revogado_em  timestamptz,
    tentativas   smallint    NOT NULL DEFAULT 0,   -- falhas de e-mail com código certo (E8)
    criado_por   uuid        NOT NULL REFERENCES usuario (id),
    criado_em    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT convite_conta_hash_ck CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
-- um convite pendente por barbeiro
CREATE UNIQUE INDEX convite_conta_pendente_ux ON convite_conta (barbeiro_id)
    WHERE usado_em IS NULL AND revogado_em IS NULL;
```

`usuario` e `usuario_papel` **não mudam**: a conta nasce **no aceite**, já ativa e com senha. `CHECK` de `usuario_papel` continua com os três papéis (nenhum papel novo, ADR 0013, opção B). O vínculo `barbeiro.usuario_id` é gravado no aceite pela agenda, que escuta o evento `ConviteAceito` da conta (a conta não conhece a agenda).

## 3. Decisões de desenho

| # | Tema | Decisão | Por quê |
|---|---|---|---|
| E1 | Papéis | **Nenhum papel novo.** "Barbeiro terceiro" é um `BARBEIRO` comum; "Dono/Admin" é o rótulo de tela do `ADMIN`. O dono que atende tem `ADMIN` + `BARBEIRO` + vínculo. **Só o ADMIN** mexe na agenda de outro barbeiro, convida e desliga; o convidado só mexe na própria | ADR 0013, opção B: zero migração de papel e zero quebra de contrato |
| E2 | Isolamento | O `barbeiroId` do BARBEIRO sai **sempre do token** (`sub` → `barbeiro.usuario_id` com `barbeiro.ativo`), nunca da URL nem do corpo. Política única `agenda/AcessoAgenda` (`alvo`, `alvoDoAlcance`, `exigirDonoDaReserva`, `proprio`), chamada por `PainelDaAgenda`, `AlcanceDaAgenda` e `CadastrosDoPainel`; as três cópias saem. **O ADMIN passa por `alvo` sem restrição** (qualquer barbeiro existente); o barbeiro **desligado** (`ativo = false`) responde `404` (A8). Alheio, inexistente ou sem vínculo = `404` igual | D151; `@PreAuthorize` precisaria do banco no SpEL e daria `403` (ADR 0005, R23) |
| E3 | Convite | Código de 32 bytes de `SecureRandom`, mostrado **uma vez** na tela do dono como link `/convite#<codigo>` e QR. O banco guarda só o SHA-256 (sem BCrypt: a entropia é de 256 bits). Vale por `app.conta.convite.validade` (padrão **48 h**, H25; a subida falha se ficar fora de **1 h a 7 dias**). Um pendente por conta (índice parcial) | o código não é senha: expira, serve uma vez e é revogável |
| E4 | Uso único | O aceite é um `UPDATE ... SET usado_em = :agora WHERE token_hash = :hash AND usado_em IS NULL AND revogado_em IS NULL AND expira_em > :agora`, com o `Clock` (ADR 0004). Só segue se afetou **uma** linha | dois aceites ao mesmo tempo: um só vence |
| E5 | Abrir não consome | `GET /convite` (página) não chama a API com o código; só o `POST` consome. O código vai no **fragmento** (`#`), que nem a prévia de link do WhatsApp nem o servidor recebem | H26: robô de prévia não queima o link |
| E6 | Ordem das checagens no `POST /auth/convite` | (1) forma do corpo e da senha, **sem tocar no banco** → `400`; (2) limite por IP → `429`; (3) busca pelo hash, com o relógio → `422` genérico. Assim o `400` da senha fraca nunca revela se o código existe | sem oráculo (sem enumeração) |
| E7 | Sem enumerar e-mail | A emissão não recebe nem guarda e-mail (D-1): a resposta é sempre a mesma forma. No aceite, e-mail de conta de equipe ou desativada, ou de cliente que não está logado nela, é o mesmo `422`. Quem tem um código **válido** pode distinguir "e-mail sem conta" (pede senha, `400`) de "e-mail com conta" (`422`); isso exige o código de 256 bits e some depois de 5 tentativas (E8). O e-mail do barbeiro não volta em nenhuma lista | LGPD art. 6º, III (necessidade) e R30 |
| E7b | Exceção ao 422 único | O 422 único vale para tudo que **não** é um aceite certo. Só **com código válido** (256 bits, de quem o dono escolheu) a API responde diferente em dois casos, e só nesses: `400 VALIDACAO_INVALIDA` no campo `senha` quando o e-mail não tem conta e a senha não veio (o convite continua valendo), e `400 CAMPO_DESCONHECIDO` quando a conta logada já tem senha e o corpo mandou `senha`. Quem não tem um código válido nunca vê nenhum dos dois. O tempo da resposta do e-mail existente e do novo é igualado com um BCrypt fictício | o 400 não é oráculo para quem adivinha (precisa do código) e a tela precisa dele para ajudar o barbeiro |
| E8 | Tentativas por convite | Além do limite por IP (H30: **10 por hora por IP**, `app.limites.convite-max` e `convite-janela`), um convite com código **certo** e e-mail que não serve (conta de equipe, desativada, ou de cliente sem login) soma `tentativas`; na 5ª (`app.conta.convite.tentativas-max`) é **revogado**. O dono vê `acesso = SEM_CONTA` e gera outro | o link sozinho não deixa tentar e-mails de outras pessoas em silêncio |
| E9 | Conta que já existe (H32) | O barbeiro digita o e-mail no aceite. Se é de um cliente, o aceite **exige estar logado nessa conta** (`sub` = a conta do e-mail), sem `senha`: soma o papel `BARBEIRO` e sobe `versao_credencial`. O convite **nunca** define nem troca a senha de conta que já tem senha (fecha o sequestro por quem tiver o link). E-mail de equipe (`ADMIN` ou `BARBEIRO`) ou de conta desativada = o mesmo `422` genérico | ADR 0013, 4.1 |
| E10 | Desligar (`ativo: false`) = **"excluir" o barbeiro, sem apagar** (D153) | Sempre: `barbeiro.ativo = false`, tira o papel `BARBEIRO`, sobe `versao_credencial` (o `ContaEmUso` barra o token na hora), revoga o convite e anula `barbeiro.whatsapp`. Conta **também de cliente**: segue ativa como cliente, com dados e reservas; a H33 não se aplica à conta. Conta **só de equipe**: desativa e **anonimiza** no mesmo passo (H33 revisada): anula `senha_hash`, apaga refresh tokens, convite e identidade social, troca nome e e-mail por "lápide". As reservas e a auditoria continuam apontando para a conta. O token antigo de quem perdeu o papel `BARBEIRO` e continua ativo (cliente) é barrado na hora pelo `ContaEmUso` (o token não pode ter papel que a conta já não tem) | H31 e H33; `auditoria_admin.ator_id` continua válido |
| E11 | Retenção depois do desligamento | `nome_exibicao` nas reservas por **5 anos** depois da última reserva e depois pseudônimo (job `RetencaoDeDados`, ShedLock, `Clock`); IP da auditoria 1 ano (D99); backup 30 dias (D128). **Pendente de advogado:** art. 10 da LGPD e o tipo de vínculo (CLT ou Lei 13.352/2016) | H33; ver D-6 |
| E12 | Orquestração | `admin → conta` e `admin → agenda` (ADR 0001/0002): o caso de uso do `admin` chama `conta.Contas` (`emitirConvite`, `revogarConvite`, `desligarContaDeEquipe`) e `PainelDaAgenda.alterarBarbeiro` na **mesma transação** da auditoria. A rota pública `/auth/convite` fica no `conta`, que não depende da `agenda` nem do `admin`: o aceite publica `ConviteAceito` (síncrono, na mesma transação), que a **agenda** escuta para ligar `barbeiro.usuario_id` e o **admin** escuta para auditar | o vínculo é gravado no aceite; se não puder (barbeiro desligado, ou já com outra conta ativa), tudo desfaz e o aceite é o `422` genérico |
| E13 | Auditoria | Só nomes de campos, nunca o código, o e-mail, o nome ou a senha (R30). Ações: `CONVITE_EMITIDO` (ator = dono), `CONVITE_REVOGADO` (dono), `CONVITE_REVOGADO_POR_TENTATIVAS` (ator = sistema), `CONVITE_ACEITO` (ator = o próprio barbeiro) e `BARBEIRO_DESLIGADO` (dono). As duas do meio e a `CONVITE_ACEITO` **acrescentam** ao ADR (que citava três). A tabela exige um autor: na revogação por tentativas o autor é o dono que gerou o convite (`detalhe.origem = TENTATIVAS`) | quem fez o quê e quando, sem dado pessoal |
| E14 | Clientes do barbeiro | Os clientes dele agendam como hoje (`GET /barbeiros`, `GET /disponibilidade?barbeiroId=`, `POST /agendamentos` e `/agendamentos/guest`), com ou sem cadastro; a reserva leva o `barbeiro_id` e só aparece na agenda dele. O que ele combinar por WhatsApp entra pela **reserva manual própria** (nome opcional, sem contato, R33). **Sem carteira de clientes** (H24) | pedido do Kevin; nada novo no contrato |
| E15 | Dinheiro | Só o `ADMIN` vê e mexe em `/admin/pagamentos/**`, devoluções, `/admin/auditoria`, `/admin/configuracao`, `/admin/servicos/**` e `/admin/barbeiros/**` (incluindo convites). O BARBEIRO vê preço e status do pagamento **só das próprias reservas** (H29) | H27, H28 e H29 |
| E17 | Admin na agenda de outro (esclarecimento do Kevin, 23/11) | O ADMIN faz, na agenda de **qualquer** barbeiro: ver (`GET /admin/agenda?barbeiroId=`), reserva manual (`POST /admin/agendamentos`), concluir, marcar falta e cancelar (`PATCH .../status`), com a auditoria registrando o admin como autor. O BARBEIRO convidado faz tudo isso **só na própria** (cancelar continua só do dono, D124) e recebe `404` na de outro | A39, A40 e A41 |
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
| A13 | barbeiro sem conta, ADMIN | `POST /admin/barbeiros/{id}/convite` (sem corpo) | `201 { codigo, expiraEm }` com `Cache-Control: no-store`; **nenhuma conta nasce ainda**; no banco só o `token_hash` (o código não está em nenhuma coluna); barbeiro inexistente `404`, barbeiro com acesso ativo `409 USUARIO_JA_VINCULADO` | `ConviteDoBarbeiroIT` |
| A14 | convite válido | `POST /auth/convite` com código, o **próprio e-mail do barbeiro** e a senha | `204`; conta ativa só com `BARBEIRO` e ligada ao barbeiro, com a auditoria `CONVITE_ACEITO`; login com o e-mail e a senha novos funciona e `GET /admin/agenda` mostra **só** a agenda daquele barbeiro | `ConviteDoBarbeiroIT` |
| A15 | convite válido | senha com 9 bytes, 73 bytes e 40 letras acentuadas (80 bytes) | `400 VALIDACAO_INVALIDA` no campo `senha`; o convite **não** é consumido e o mesmo código ainda aceita uma senha boa | `ConviteDoBarbeiroIT` |
| A16 | código já usado · vencido (relógio +48 h) · revogado · inventado · e-mail que não bate · de outra conta | `POST /auth/convite` | `422 CONVITE_INVALIDO` nos seis, com o **mesmo** corpo, título e mensagem | `ConviteSegurancaIT` |
| A17 | convite válido | dois `POST /auth/convite` ao mesmo tempo (dois fios) | exatamente um `204` e um `422`; uma só senha gravada | `ConviteSegurancaIT` |
| A18 | convite pendente | emitir de novo (reenviar) · `DELETE .../convite` · `DELETE` repetido · `DELETE` sem convite · `DELETE` de barbeiro inexistente | o código antigo dá `422` e o novo dá `204` · `204` · `204` · `204` · `404` | `ConviteDoBarbeiroIT` |
| A19 | `app.conta.convite.validade = 48h` e relógio fixo | aceitar em +47h59m59s · em +48h · validade configurada em 30 min · em 8 dias | `204` · `422` · a aplicação **não sobe** com validade fora de 1 h a 7 dias (30 min e 8 dias recusam; 1 h e 7 dias valem) | `ConviteSegurancaIT` (prazo) e `ParametrosDoConviteTest` (configuração) |
| A20 | limite de 10 aceites por hora por IP | 11 `POST /auth/convite` (códigos inventados) do mesmo IP | do 11º em diante `429 LIMITE_EXCEDIDO` com `Retry-After`; outro IP não é afetado; a contagem inclui os pedidos inválidos | `ConviteSegurancaIT` |
| A21 | convite válido | 5 `POST` com o código certo e e-mail de conta que não serve (cliente sem login) | todos `422` iguais; na 5ª o convite fica **revogado** (`acesso = SEM_CONTA`, `CONVITE_REVOGADO_POR_TENTATIVAS` na auditoria) e o código certo também dá `422` | `ConviteSegurancaIT` |
| A22 | barbeiros diferentes, e um aceite com e-mail | emitir convite · listar · ler auditoria e erro | a emissão tem sempre a mesma forma (`codigo`, `expiraEm`, sem e-mail); a tabela não tem coluna de e-mail; nenhuma lista, auditoria ou erro traz o e-mail do barbeiro | `ConviteSegurancaIT` |
| A23 | (H32) cliente `ana@x` com conta e senha; convite emitido | `POST /auth/convite` com `ana@x` sem login · logado em **outra** conta · logado em `ana@x` · logado em `ana@x` mandando `senha` | `422` · `422` · `204` (soma `BARBEIRO`, sobe `versao_credencial`, liga ao barbeiro) · `400 CAMPO_DESCONHECIDO` sem consumir o convite; a senha antiga continua valendo | `ConviteContaExistenteIT` |
| A24 | (H32) conta `CLIENTE` + `BARBEIRO` (reserva de cliente com B, agenda própria) | painel e rotas de cliente | no painel, só a própria agenda; nas rotas de cliente, só as próprias reservas, inclusive as marcadas com outro barbeiro, sem enxergar a agenda dele | `ConviteContaExistenteIT` |
| A25 | BARBEIRO · CLIENTE · sem token · ADMIN com senha inicial | `POST` e `DELETE /admin/barbeiros/{id}/convite` | `403` · `403` · `401` · `403 TROCA_DE_SENHA_OBRIGATORIA` | `ConviteDoBarbeiroIT` |
| A26 | barbeiro **só de equipe** com sessão aberta (e, em outro caso, com convite pendente) | `PUT /admin/barbeiros/{id}` com `ativo: false` | token antigo `401` na hora; refresh falha; convite revogado; conta anonimizada (nome, e-mail, `senha_hash`, `whatsapp`, refresh e identidade social anulados; `id` e papel preservados); as reservas e `auditoria_admin.ator_id` continuam válidos | `DesligarBarbeiroIT` |
| A27 | (H32) barbeiro que **também é cliente** | desligar | token antigo `401`; sem o papel `BARBEIRO`; a conta entra como cliente com nome, e-mail e reservas intactos | `DesligarBarbeiroIT` |
| A28 | barbeiro desligado | `PUT` com `ativo: true` · gerar convite novo e aceitar | reativar sozinho não devolve o acesso (`acesso = DESLIGADO`) · só com convite novo (H31) | `DesligarBarbeiroIT` |
| A29 | barbeiro ativo que também é cliente | `DELETE /me` | `409 USUARIO_JA_VINCULADO` pedindo que o dono desligue antes; nada apagado; depois de desligado, a exclusão de cliente passa | `DesligarBarbeiroIT` |
| A30 | (H33) barbeiro desligado há 5 anos e 1 dia da última reserva (relógio) | job `RetencaoDeDados` | `nome_exibicao` vira pseudônimo nas reservas; antes disso o nome é mantido; IP da auditoria some depois de 1 ano; o job é idempotente | `RetencaoDeBarbeiroIT` |
| A31 | todo o fluxo (emitir, aceitar, revogar, errar, desligar) | ler log, auditoria e corpos de erro | nenhum código, e-mail, nome ou senha; a auditoria só traz nomes de campos (E13) | `SemDadoPessoalNoLogConviteIT` |
| A32 | barbeiros em cada estado | `GET /admin/barbeiros` | `acesso` = `SEM_CONTA`, `CONVITE_PENDENTE` (com `conviteExpiraEm`), `ATIVO` ou `DESLIGADO`; sem e-mail no JSON | `ConviteDoBarbeiroIT` |
| A33 | `openapi.yaml` 1.8.0 | `redocly lint` · `scripts/conferir-spec.sh 10 --exigir` | 0 erros; `CONVITE_INVALIDO` e as rotas novas documentados; cada `A#` tem teste com o número | `ContratoExemplosDeErroTest` (e o redocly e o `conferir-spec.sh` no CI) |
| A39 | ADMIN e os barbeiros A e B (esclarecimento do Kevin) | o ADMIN faz `POST /admin/agendamentos` na agenda de B | `201`, a reserva é de B, a auditoria registra o admin como autor, aparece na agenda de B e não na de A. (O BARBEIRO A fazendo o mesmo é o A4: `404`) | `IsolamentoEntreBarbeirosIT` |
| A40 | reservas de B | o ADMIN conclui, marca falta e cancela (com motivo) reservas de B | `200` nos três, com `cancelado_por` = admin e uma linha de auditoria cada. (O BARBEIRO A nos mesmos casos é o A3: `404`) | `IsolamentoEntreBarbeirosIT` |
| A41 | barbeiro **convidado** que acabou de aceitar | faz reserva manual, conclui e cancela na agenda de **outro** barbeiro · e na própria | `404` nos três da agenda de outro, sem mudar a reserva nem gravar auditoria · na própria: reserva manual `201`, concluir `200`, cancelar `403` (só o dono cancela, D124) | `ConviteDoBarbeiroIT` |
| A34 | módulo `conta` | `ArquiteturaTest` | `conta` não depende de `agenda` nem de `admin`; só o `admin` chama `Contas.emitirConvite`; a rota `/auth/convite` está liberada no `SegurancaConfig` e **só ela** | `ArquiteturaTest` |

### Telas (front, depois do contrato)

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A35 | Dono/Admin em Cadastros → Barbeiros | toca em "Gerar convite" (sem informar e-mail) | o link `/convite#<codigo>` e o QR aparecem **uma vez**, com o prazo (e o botão "Copiar link" e "Enviar pelo WhatsApp"); fechou a tela, só aparece "convite pendente até <data>" com "Gerar outro" (reenviar) e "Revogar"; o rótulo do papel `ADMIN` é "Dono/Admin" | `PainelConvite`, E2E |
| A36 | `/convite#<codigo>` | abrir a página (e o robô de prévia) | nenhum pedido à API leva o código; nenhum consumo; o código sai da barra de endereço depois de lido; `Referrer-Policy: no-referrer` e `Cache-Control: no-store` na página | `ConvitePagina`, `convite` |
| A37 | tela "Criar minha senha" | o barbeiro digita o **próprio e-mail**, a senha e a confirmação e envia | o BFF chama `POST /auth/convite`, **não devolve o código** no corpo; sucesso leva ao login com a mensagem "Senha criada. Entre com seu e-mail e a nova senha"; `400` mostra o erro no campo da senha; `422` mostra uma frase só ("Este link não vale mais ou não pode ser usado aqui") com o caminho "Já tenho conta no site: entrar"; `429` mostra o tempo de espera | `ConvitePagina`, `bff-convite` |
| A38 | acessibilidade da tela | teclado e leitor | rótulos ligados, erro com `role="alert"`, foco no primeiro erro, senha com botão "mostrar" e `autocomplete="new-password"`, contraste do design system | `ConvitePagina`, axe |

## 5. Entregas

- **`agenda`**: `AcessoAgenda` (nova); `PainelDaAgenda`, `AlcanceDaAgenda` e `CadastrosDoPainel` passam a chamá-la; as cópias saem. `ArquiteturaTest` ganha a regra do A1.
- **`conta`**: `ConviteConta` (entidade), `ConviteContaRepository`, `Contas.convidarBarbeiro`, `revogarConvite`, `desligarContaDeEquipe`; `AceitarConvite` (caso de uso do `POST /auth/convite`); `ConviteController`; `CodigoDeConvite` (gera e faz o SHA-256); código de erro `CONVITE_INVALIDO` no `CodigoErroEnum`; propriedades `app.conta.convite.*` e `app.limites.convite-*`.
- **`admin`**: `ConvitesAdminController` (rotas do dono) e o caso de uso que une `conta` + `agenda` + auditoria na mesma transação; `BarbeiroAdmin.acesso` e `conviteExpiraEm`.
- **`config`**: `SegurancaConfig` libera `POST /auth/convite`; as rotas `/admin/barbeiros/**` já são só do `ADMIN`.
- **`privacidade`/retenção**: `RetencaoDeDados` ganha a troca de `nome_exibicao` por pseudônimo (E11).
- **Contrato**: `openapi.yaml` e `contrato-api.md` 1.8.0 (rotas, `BarbeiroAdmin.acesso`, `ConviteAceite`, `422 CONVITE_INVALIDO` e `429`), histórico de versões e `contexto.md` (R# novo para o convite).
- **Banco**: `V?__convite_de_conta.sql`.
- **Front** (BFF e telas): rota `/convite` e o BFF `POST /api/convite`; bloco "Convite" em Cadastros → Barbeiros; rótulo "Dono/Admin"; ver a tabela de telas abaixo.
- **Docs**: guia de testes com a saída real, política de privacidade (parágrafo sobre o barbeiro: dados e retenção, a revisar na revisão jurídica), `docs/fluxo-ponta-a-ponta.md` e o roteiro de visitantes.

### Telas (design system) · **aguardam desenho**

O design system é a fonte do visual: **não decido o visual aqui**. O canvas do `frontend/design/README.md` tem "Troca de senha inicial" (`TrocaSenha.dc.html`) e "Painel: cadastros" (`PainelCadastros.dc.html`), mas **nenhum dos dois cobre o convite**. Telas que **precisam de desenho** (marcadas "aguarda design"; o PDF atual não as traz). Enquanto o desenho não vem, foram feitas em **layout neutro**, só com tokens e os componentes que já existem (`Campo`, `Botao`, `Aviso`, `Dialogo`, `CopiarTexto`, `QrPix`), e **trocam de visual quando o desenho chegar** (os testes A35 a A38 não dependem do visual):

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

### Checklist LGPD (aplicando o agente jurídico; **não substitui parecer jurídico**, e as fontes oficiais ainda precisam ser consultadas na revisão jurídica antes do merge)

| Ponto | Situação | Observação |
|---|---|---|
| Base legal do dado do barbeiro (nome, e-mail, WhatsApp comercial, hash da senha) | 🟠 risco | execução de contrato ou relação de trabalho (LGPD art. 7º, V), conforme o vínculo, que a H33 deixa **pendente de advogado**; registrar a base escolhida na política |
| Minimização | 🟢 ok | o convite guarda só o hash; o e-mail do barbeiro não volta em lista; a reserva manual não guarda contato do cliente (art. 6º, III) |
| Retenção | 🟠 risco | 5 anos do `nome_exibicao` e pseudônimo depois são da H33 e dependem do advogado; o resto (IP 1 ano, backup 30 dias) já está no ADR 0009 e na D128 |
| Direitos do titular (exportar e excluir) | 🟠 risco | `DELETE /me` de barbeiro ativo é `409` até o desligamento (A29); a exportação do barbeiro precisa listar os dados dele (conta, vínculo, reservas) |
| Transparência | 🟠 risco | a política 1.2 precisa de um parágrafo sobre barbeiros terceiros (dados, retenção e anonimização); o texto passa pela revisão jurídica |
| Dado de terceiros digitado pelo barbeiro (reserva manual) | 🟢 ok | só nome opcional e observação; sem contato (R30, D125) |
| Segurança do convite | 🟢 ok | hash, uso único, expiração, limite e `422` único (E3 a E8) |

## 7. Dúvidas para o Kevin (decididas em 23/11/2026)

> **D-1 decidida com ajuste:** o dono **só gera o convite**; o **barbeiro digita o próprio e-mail** e a senha ao aceitar. D-2 a D-8 seguem a proposta. A tabela abaixo guarda o texto original; onde ele fala de "o dono informa o e-mail", vale o ajuste.

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

- [x] todos os critérios A1 a A41 com teste verde (backend no `./mvnw verify`, front no vitest e no E2E; `scripts/conferir-spec.sh 10 --exigir` confere 41 de 41)
- [x] contrato 1.8.0 com `redocly lint` em 0 erros e o histórico de versões atualizado
- [x] o Dono/Admin gera, reenvia e revoga convites; o barbeiro cria a senha e vê só a própria agenda (E2E `convite.spec.ts` contra a API real; o fluxo manual no [guia de testes](../guia-testes.md#74-convite-do-barbeiro-fatia-10) fica "a preencher")
- [ ] **telas "aguarda design" desenhadas** e o visual trocado (hoje em layout neutro, só tokens): depende do desenho do Kevin
- [ ] texto da política de privacidade revisado na revisão jurídica (parágrafo do barbeiro) e as pendências jurídicas da H33 levadas ao advogado (art. 10 e tipo de vínculo): **não feito nesta entrega**
- [ ] [checklist da PR](checklist-pr.md) completo na abertura da PR (o Kevin abre; esta branch não abriu PR)

