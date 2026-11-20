# 0013. Vários barbeiros: Dono/Admin, isolamento por `barbeiroId` e convite de uso único

- Status: **proposta** (aguarda o ok do Kevin)
- Data: 2026-11-20
- Decisores: revisão de arquitetura (dona), Kevin Richard (produto)
- Ponto de partida: `develop` em `178b2a0` (contrato 1.7.1, migrações até `V7`)
- Número: o 0012 está reservado pela [spec da Fatia 9](../sdd/fatia-9-assistente-de-duvidas.md) (assistente, pós-beta); por isso este é o 0013. As hipóteses começam em H24 porque a H23 também é da Fatia 9.

## Contexto

O Kevin pediu que a barbearia tenha **vários barbeiros**. O Eduardo Cândido é o dono e administrador e cadastra os barbeiros novos pelo app. Cada barbeiro tem login e agenda próprios, separados da agenda do dono, e trava ali os horários combinados por fora (reserva manual). O cliente escolhe o barbeiro ao marcar.

Boa parte disso **já existe** no código. O que segue foi lido na `develop`:

| Peça | Onde | Situação |
|---|---|---|
| Papéis `CLIENTE`, `BARBEIRO`, `ADMIN` | `V1__agenda.sql:53-58` (`usuario_papel`, `CHECK` com os três); `Usuario.java:26-27` | pronto |
| Vínculo conta ↔ barbeiro | `V1__agenda.sql:147-156` (`barbeiro.usuario_id`, `UNIQUE`, opcional); `BarbeiroRepository.findByUsuarioId` | pronto |
| Dono = `ADMIN` + `BARBEIRO` | `AdminInicial.java:72` cria o admin da subida com os dois papéis; `Ator.java:14-20` e `PainelDaAgenda.Escopo` (linha 49): quem tem os dois vale como ADMIN (E3 da Fatia 5) | pronto |
| Rotas só do ADMIN | `SegurancaConfig.java:92-94` (`/admin/pagamentos`, `/admin/auditoria`, `/admin/servicos`, `/admin/barbeiros`, `/admin/configuracao`); resto de `/admin/**` é `painel()` (linha 95, ADMIN ou BARBEIRO) | pronto |
| Senha inicial | `V2__senha_inicial.sql` (`deve_trocar_senha`), claim `trocar` (`JwtConfig.java:108`), `barrarSenhaInicial` (`SegurancaConfig.java:172`) → `403 TROCA_DE_SENHA_OBRIGATORIA`; cookie `ah_troca` no BFF (D133) | pronto, mas **só o admin da subida** nasce assim |
| Escopo do BARBEIRO na agenda | `PainelDaAgenda.java:188-189` (`agenda`), `:215-216` (`criarReservaManual`), `:226-246` (`barbeiroDoPedido`), `:261` (`mudarStatus`, dono da reserva), `:264` (cancelar só ADMIN, D124) | pronto; alheio, inexistente ou sem vínculo = `404` (R23) |
| Escopo no alcance | `AlcanceDaAgenda.java:123-137`: **cópia** do `barbeiroDoPedido`, com outra regra contratada (o BARBEIRO recebe sempre o próprio, E36; o painel responde `404`) | pronto, duplicado |
| Jornada do próprio barbeiro | `GET /admin/minha-jornada` (D142, A61): `AgendaAdminController.java:72`, `CadastrosDoPainel.java:197-201`, barbeiro pelo `sub` | pronto |
| Reserva manual | `POST /admin/agendamentos` (A62, D146): `ReservaManualRequest.java:17-22` (nome opcional até 80, observação até 200, **sem** WhatsApp, e-mail ou CPF, R30/D125); tela `PainelReservaManual.tsx:55-63` (ADMIN escolhe, BARBEIRO só o próprio) | pronto |
| Cadastro de barbeiro | `/admin/barbeiros` (`BarbeirosAdminController.java:38-77`, `CadastroDeBarbeiros.java:51-95`): nome, `usuarioId` opcional, WhatsApp, `ativo`, jornada, folgas | pronto, com uma lacuna (abaixo) |
| Conta desativada perde o token na hora | `ContaEmUso.java:25` (`ValidadorDeConta`, E16) e login/refresh só com `ativo` (`Sessoes.java:93`, `:119`) | pronto |
| Cliente escolhe o barbeiro | `GET /barbeiros` e `GET /disponibilidade?barbeiroId=` (opcional, junta todos quando ausente); `Agendar.tsx:99` (select), `ReservarHorario.java:292` (barbeiro ativo) | pronto |
| Filtro por barbeiro no painel | `GET /admin/agenda?barbeiroId=` (ADMIN), `PainelAgenda.tsx:81` | pronto |
| Isolamento do módulo `admin` | `ArquiteturaTest`: A71 (linha 79, cadastros só por `PainelDaAgenda` e `Contas`) e A40 (linha 168, admin sem SQL fora da auditoria) | pronto |

**Lacunas reais:**

1. **Não há como criar uma conta de barbeiro pelo app.** `CadastrarCliente.java:75` cria só `CLIENTE`; `AdminInicial` cria só o dono. `CadastroDeBarbeiros.exigirContaDeBarbeiro` (`:91-95`) exige uma conta **que já tenha** o papel `BARBEIRO`, e nada concede esse papel. Hoje um segundo barbeiro só teria login com SQL na mão.
2. **A regra de escopo está em três lugares** (`PainelDaAgenda` duas vezes, `AlcanceDaAgenda` uma) e **não olha `barbeiro.ativo`**: um barbeiro desativado no cadastro, com a conta ainda ativa, continua lendo a própria agenda.
3. **Desativar o barbeiro não encerra o acesso**: `ativo = false` no `barbeiro` não desativa a conta nem revoga as sessões.
4. **Não existe envio de e-mail** no projeto (nenhum `JavaMailSender` ou SMTP no `application.yml`), e o custo é zero (D59).
5. **"Cadastra os próprios clientes" não existe**: o painel não tem cadastro de cliente; o mais próximo é o nome opcional da reserva manual.

## Opções consideradas

### Papéis

| Opção | A favor | Contra |
|---|---|---|
| A. Papel novo `DONO` | nome igual ao do negócio | migração do `CHECK` e dos dados, claim `papeis` muda, `SegurancaConfig`, `ExigePapel` do front e contrato mudam; quebra quem lê `papeis` |
| **B. `ADMIN` é o dono** (rótulo "Dono/Admin" na tela) | zero migração de papel, zero quebra de contrato; o dono que atende já é `ADMIN` + `BARBEIRO` + vínculo (`AdminInicial.java:72`) | "admin" no código e "dono" na tela (resolvido com o rótulo) |

### Onde fica a autorização por `barbeiroId`

| Opção | A favor | Contra |
|---|---|---|
| A. `@PreAuthorize` com SpEL nos controllers | declarativo | a regra precisa do banco (o barbeiro da reserva); o Spring responde `403`, e o projeto exige `404` sem oráculo (ADR 0005, R23); SpEL espalhado é difícil de testar |
| B. Cada serviço decide (como hoje) | já funciona | três cópias com diferenças sutis; a quarta rota esquece uma |
| **C. Política única no módulo `agenda`, chamada pelos serviços de aplicação** | um lugar, testável sem HTTP, devolve `404`; o dado (`barbeiro.usuario_id`, `barbeiro.ativo`) é da `agenda`, e o `admin` não pode ler SQL (A40) | um refactor pequeno |

### Cadastro do barbeiro

| Opção | A favor | Contra |
|---|---|---|
| A. E-mail de convite | prático | não há envio de e-mail e um provedor fere o custo zero; e-mail é canal fora do app |
| B. Senha inicial gerada + troca obrigatória | reaproveita 100% do mecanismo da E24 | o dono **conhece** a senha até a troca; é uma senha que circula entre pessoas |
| **C. Convite de uso único mostrado na tela do dono** | ninguém além do barbeiro conhece a senha; o código não é senha, expira e só serve uma vez; zero serviço novo | uma tabela e uma rota pública novas |

## Decisão

### 1. Papéis: `ADMIN` é o Dono/Admin; `BARBEIRO` fica como está (opção B)

**Nenhum papel novo e nenhuma migração de papel.** Na tela, `ADMIN` passa a se chamar "Dono/Admin". O dono que também atende (o Eduardo) tem `ADMIN` + `BARBEIRO` + o vínculo `barbeiro.usuario_id` com a própria conta: tem agenda própria, como qualquer barbeiro, e enxerga as dos outros.

| | Dono/Admin (`ADMIN`) | Barbeiro (`BARBEIRO`) |
|---|---|---|
| Agenda (`GET /admin/agenda`) | todas, ou uma com `?barbeiroId=` | só a própria; outro `barbeiroId` = `404` |
| Reserva manual (`POST /admin/agendamentos`) | para qualquer barbeiro ativo | só para si; outro = `404` |
| Concluir e marcar falta (`PATCH .../status`) | qualquer reserva | só as próprias; alheia = `404` |
| Cancelar (`PATCH .../status` com `CANCELADO`) | sim, com motivo (R22) | não: `403 ACESSO_NEGADO` **depois** do teste de dono (D124) |
| Minha jornada (`GET /admin/minha-jornada`) | a própria, se tiver vínculo; sem vínculo = `404` (D142) | a própria |
| Alcance (`GET /admin/alcance`) | todos ou o filtrado | sempre o próprio (E36, como hoje) |
| Barbeiros, convites, jornada e folgas de qualquer um, serviços, configuração, pagamentos, auditoria | sim | não: `403 ACESSO_NEGADO` pela rota (`SegurancaConfig.java:92-94`) |

O `403` das rotas só do dono não é oráculo: a existência da rota está no contrato público e não revela nenhum id. O `404` vale para **recurso** alheio.

### 2. Isolamento por `barbeiroId`: política única `AcessoAgenda` no módulo `agenda` (opção C)

- **Componente:** `agenda/AcessoAgenda.java` (pacote raiz da `agenda`, ao lado do `PainelDaAgenda`). Recebe o `PainelDaAgenda.Escopo` que já existe, montado pelo `Ator` a partir do token (`sub` e papel `ADMIN`).
- **Regra:** o `barbeiroId` efetivo do BARBEIRO sai **sempre** do token (`sub` → `barbeiro.usuario_id` com `barbeiro.ativo = true`), nunca da URL nem do corpo. Para o ADMIN, o `barbeiroId` do pedido é um **filtro opcional** (inexistente = `404`).
- **Operações:**
  - `alvo(escopo, barbeiroIdDoPedido)` → todos (ADMIN sem filtro) ou um barbeiro; BARBEIRO com id alheio, sem vínculo ou com barbeiro inativo = `404`. Usada por `agenda` e `criarReservaManual`.
  - `exigirDonoDaReserva(escopo, barbeiroDaReserva)` → `404` se não for dele. Usada por `mudarStatus`, antes do teste de cancelamento.
  - `proprio(escopo)` → o barbeiro da conta ou `404`. Usada por `minhaJornada` e pelo alcance, que mantém a troca contratada da E36 (unificar com o `404` do painel mudaria o contrato; fica como está).
- **Quem chama:** `PainelDaAgenda`, `AlcanceDaAgenda` e `CadastrosDoPainel`. As três cópias (`PainelDaAgenda.java:226-246` e `:261`, `AlcanceDaAgenda.java:123-137`) saem. O `admin` não muda: continua montando o `Escopo` e chamando a API pública da `agenda`.
- **Por que não no `admin`:** o vínculo e o `ativo` são tabelas da `agenda`, e o `admin` não lê SQL (A40). **Por que não `@PreAuthorize`:** precisaria do banco no SpEL e responderia `403`, contra a ADR 0005.
- **Mudança de papel** só vale no token seguinte (até 15 min, H15). **Desligamento** vale na hora, porque a conta desativada é barrada pelo `ContaEmUso` e o barbeiro inativo é barrado pela política.
- **Prova:**
  - uma regra nova no `ArquiteturaTest`: só `AcessoAgenda` chama `BarbeiroRepository.findByUsuarioId`, e nenhum fonte da `agenda` fora dela tem SQL com `barbeiro` filtrado por `usuario_id` (leitura de arquivo, como o teste da linha 148);
  - **um teste IT por rota do painel** com dois barbeiros, A e B: "A não vê nem altera nada do B" (matriz nas fatias abaixo).

### 3. O que muda no contrato, no modelo e nas migrações

**Contrato (previsto; muda na fatia, não neste PR):** `1.7.1 → 1.8.0`, **só acréscimos**:

| Rota ou schema | Mudança |
|---|---|
| `POST /admin/barbeiros/{id}/convite` (ADMIN) | corpo `{ email }`. Cria a conta do barbeiro (papel `BARBEIRO`, **inativa e sem senha**), liga `barbeiro.usuario_id` e devolve `201 { codigo, expiraEm }` com `no-store`. Barbeiro que já tem conta ativa: `409 USUARIO_JA_VINCULADO` (código que já existe). E-mail em uso: `409 EMAIL_JA_CADASTRADO` (H32). Emitir de novo revoga o convite pendente. |
| `DELETE /admin/barbeiros/{id}/convite` (ADMIN) | revoga o convite pendente: `204` (idempotente). |
| `POST /auth/convite` (público, com limite por IP, H30) | corpo `{ codigo, senha }`. Define a senha com as regras da troca de senha (Fatia 3), ativa a conta e queima o convite: `204`. Código inexistente, expirado, usado ou revogado: **uma resposta só**, `422 CONVITE_INVALIDO` (código novo), sem dizer qual caso foi. |
| `BarbeiroAdmin` | campo novo, só leitura: `acesso` = `SEM_CONTA` \| `CONVITE_PENDENTE` \| `ATIVO` \| `DESLIGADO`. O e-mail do barbeiro não volta na lista. |
| `PUT /admin/barbeiros/{id}` com `ativo: false` | sem campo novo; a descrição passa a dizer que o desligamento desativa a conta, revoga o convite e as sessões. Reativar exige convite novo (H31). |
| `BarbeiroRequest.usuarioId` | continua, porque é como o dono liga o próprio cadastro de barbeiro à própria conta. |

**Modelo:**

- `usuario` e `usuario_papel` **não mudam**. A conta convidada nasce `ativo = false` e `senha_hash = NULL`, que já são aceitos (`V1__agenda.sql:34` e `:39`); o login já recusa esse estado (`Sessoes.java:93`).
- Tabela nova `convite_conta`, do módulo `conta`:
  - `id`; `usuario_id` (FK, `ON DELETE CASCADE`);
  - `token_hash char(64)` (`UNIQUE`; SHA-256 do código, que tem 32 bytes de `SecureRandom` e por isso dispensa BCrypt);
  - `expira_em`, `usado_em`, `revogado_em`, `criado_por` e `criado_em`;
  - índice único parcial: **um convite pendente por conta**.
- O aceite é um `UPDATE ... SET usado_em = :agora WHERE token_hash = :hash AND usado_em IS NULL AND revogado_em IS NULL AND expira_em > :agora`, com o `Clock` (ADR 0004). Só segue se afetou uma linha, o que garante o uso único mesmo com dois aceites ao mesmo tempo.
- **Orquestração** (respeita ADR 0001/0002: `admin → conta`, `admin → agenda`): o caso de uso do `admin` chama `conta.Contas` (novo: `convidarBarbeiro`, `revogarConvite`, `desligarContaDeEquipe`) e `PainelDaAgenda.alterarBarbeiro` na **mesma transação** da auditoria (E11). A auditoria grava só `CONVITE_EMITIDO`, `CONVITE_REVOGADO` e `BARBEIRO_DESLIGADO` com nomes de campos; nunca o código, o e-mail ou o nome (R30). A rota pública `/auth/convite` fica no `conta`, que não depende da `agenda`: o vínculo já foi gravado na emissão.

**Migrações Flyway:** hoje vão até `V7__whatsapp_e_reserva_manual.sql`. Há um PR paralelo que cria o catálogo inicial por migração; ele vem **antes**. Esta feature usa **uma** migração, `V? depois da migração do catálogo` (se o catálogo for a `V8`, esta é a `V9__convite_de_conta.sql`), só com a tabela `convite_conta` e o índice. Nenhuma alteração de tabela existente e nenhum dado pessoal em seed.

### 4. Fluxo de cadastro do barbeiro: convite de uso único (opção C)

1. O dono cria o barbeiro em Cadastros (nome, WhatsApp comercial opcional, jornada); isso já existe.
2. O dono toca em "Gerar convite" e informa o e-mail de login do barbeiro. A API cria a conta inativa e liga o barbeiro a ela.
3. A tela do dono mostra **uma única vez** o link `/convite#<codigo>` e o QR dele, gerado no navegador como o QR do Pix (D135 já permite `img-src data:`), com o prazo (H25). Fechou a tela, o código não volta: a API guarda só o hash.
4. O dono **entrega em pessoa**: o barbeiro lê o QR no celular (H26).
5. Na página `/convite`, o código vem do **fragmento** (`#`), que não vai ao servidor nem a log (como o código do visitante, D134). O barbeiro escolhe a própria senha, e o BFF chama `POST /auth/convite`. Depois ele entra pelo login normal.
6. **Senha nunca** vai por chat, e-mail ou log: quem define a senha é o barbeiro, no app. O código do convite também nunca entra em log, auditoria ou URL de consulta.
7. **Desligar** (`ativo: false`) desativa a conta (sobe `versao_credencial`, derruba as sessões, o `ContaEmUso` barra o token na hora) e revoga o convite pendente. No mesmo passo, aplica a H33 revisada: anula `senha_hash` e `barbeiro.whatsapp`, apaga refresh tokens, convite e identidade social, e anonimiza nome e e-mail, deixando a "lápide". As reservas e a auditoria continuam apontando para a conta. A troca de `nome_exibicao` por pseudônimo 5 anos depois da última reserva entra no job de retenção (ShedLock, Clock).

**Plano B**, só se o Kevin recusar o convite: senha inicial gerada com `deve_trocar_senha = true`, mostrada uma única vez na tela do dono e entregue em pessoa. Reaproveita a E24 inteira (`403 TROCA_DE_SENHA_OBRIGATORIA`, `ah_troca`), mas o dono fica sabendo a senha até a troca.

### 5. "Cadastra os próprios clientes"

Não existe cadastro de cliente pelo painel. Nesta decisão, isso é a **reserva manual** que já existe: nome opcional (até 80) e observação, **sem** WhatsApp, e-mail ou CPF (R30, D125, LGPD art. 6º, III). Uma **carteira de clientes por barbeiro** fica como hipótese (H24) e só vira fatia se o Kevin confirmar, com base legal e retenção definidas antes.

### 6. Fatias sugeridas para o SDD

Cada fatia ganha a própria spec no formato do [modelo](../sdd/modelo-de-spec.md), com os critérios abaixo como ponto de partida.

**Fatia 10A · Política única de acesso à agenda.** Sem contrato e sem migração; só refactor com teste.

- **A1:** `AcessoAgenda` é a única classe que resolve o barbeiro da conta (regra nova no `ArquiteturaTest`), e as cópias de `PainelDaAgenda` e `AlcanceDaAgenda` saem.
- **A2:** BARBEIRO A pede `GET /admin/agenda?barbeiroId=<B>`: `404 RECURSO_NAO_ENCONTRADO`. Sem filtro, vem só a agenda de A.
- **A3:** BARBEIRO A chama `PATCH /admin/agendamentos/{reservaDeB}/status`: `404`, e a reserva de B não muda.
- **A4:** BARBEIRO A chama `POST /admin/agendamentos` com `barbeiroId` de B: `404`, sem reserva gravada.
- **A5:** BARBEIRO A chama `GET /admin/alcance?barbeiroId=<B>`: recebe os números de A, nunca os de B (E36).
- **A6:** BARBEIRO A chama `GET /admin/minha-jornada`: recebe a jornada de A.
- **A7:** BARBEIRO A recebe `403 ACESSO_NEGADO` em todas as rotas só do dono (`/admin/barbeiros/**`, `/admin/servicos/**`, `/admin/configuracao`, `/admin/pagamentos/**`, `/admin/auditoria`).
- **A8:** Com o barbeiro de A marcado `ativo = false` e a conta ainda ativa, toda rota do painel com escopo próprio responde `404`.
- **A9:** O Dono/Admin vê as reservas de A e de B sem filtro e só as de B com `?barbeiroId=<B>`. Um `barbeiroId` inexistente responde `404`.
- **A10:** As respostas `404` de "alheio" e "inexistente" são iguais: mesmo `code`, mesmo `title`, sem o id.

**Fatia 10B · Convite e desligamento do barbeiro.** Contrato 1.8.0, a migração `V?` e as telas.

- **A1:** `POST /admin/barbeiros/{id}/convite` cria a conta `BARBEIRO` inativa e sem senha, liga o barbeiro e devolve `201 { codigo, expiraEm }` com `Cache-Control: no-store`. O banco guarda só o hash.
- **A2:** Com o código certo, `POST /auth/convite` define a senha, ativa a conta e responde `204`. Depois, o login com o e-mail e a senha nova funciona e mostra a agenda só daquele barbeiro.
- **A3:** O mesmo código, usado de novo, vencido (relógio adiantado), revogado ou inventado responde sempre `422 CONVITE_INVALIDO`, com o mesmo corpo.
- **A4:** Dois aceites simultâneos do mesmo código: só um responde `204`.
- **A5:** Emitir um convite novo invalida o anterior, e `DELETE /admin/barbeiros/{id}/convite` revoga o pendente com `204`, também quando repetido.
- **A6:** `PUT /admin/barbeiros/{id}` com `ativo: false` desativa a conta. O próximo pedido com o token antigo responde `401`, o refresh falha, o convite pendente é revogado e a conta fica anonimizada como na H33 (nome, e-mail, `senha_hash`, `whatsapp` e identidade social; `id` e papel preservados; `auditoria_admin.ator_id` continua válido).
- **A7:** O BARBEIRO recebe `403 ACESSO_NEGADO` em `POST` e `DELETE /admin/barbeiros/{id}/convite`.
- **A8:** O código, o e-mail e a senha nunca aparecem em log, auditoria ou resposta de erro (`SemDadoPessoalNoLogIT` estendido, R30).
- **A9:** Acima do limite de aceite por IP (H30), a resposta é `429 LIMITE_EXCEDIDO` com `Retry-After`.
- **A10:** Na tela do dono, o link e o QR aparecem uma vez. Ao reabrir, só aparecem "convite pendente" e o prazo. O rótulo do papel `ADMIN` é "Dono/Admin".
- **A11:** A página `/convite` lê o código só do fragmento. Nenhum pedido leva o código em URL, e o BFF não o devolve no corpo (como na D150).
- **A12:** `BarbeiroAdmin.acesso` mostra `SEM_CONTA`, `CONVITE_PENDENTE`, `ATIVO` ou `DESLIGADO` conforme o caso.

**Fatia 10C · Carteira de clientes por barbeiro** (só se a H24 for confirmada). Spec própria, com base legal, retenção e escopo pela mesma `AcessoAgenda`.

## Consequências

- Nenhuma quebra de contrato: 1.8.0 só acrescenta. Os tokens, os papéis e as telas atuais continuam valendo.
- A regra de escopo passa a ter um dono só, e cada rota nova do painel tem que passar por ela, com a regra de arquitetura e o IT cobrando isso.
- O módulo `conta` ganha uma rota pública nova, que precisa de limite por IP e de resposta única.
- A H9 do [dossiê](../contexto.md#4-hipóteses-em-aberto) passa a ter efeito: o barbeiro vê só a própria agenda e o dono vê todas.
- Custo zero: nenhum serviço novo, sem e-mail e sem SMS.

## Gatilhos para revisitar

- Se a barbearia quiser mais de um administrador sem ser dono (gerente), avaliar um papel novo com um ADR que substitua este.
- Se entrar envio de e-mail gratuito e confiável, avaliar mandar **só o link do convite**, nunca a senha.
- Se a H24 for confirmada, a Fatia 10C vem com uma revisão de LGPD antes do código.

## Hipóteses abertas (para o Kevin)

| # | Pergunta | Valor provisório |
|---|---|---|
| H24 | Cada barbeiro tem uma carteira de clientes (nome e contato) além da reserva manual? | **não**: só a reserva manual com nome opcional, sem contato |
| H25 | Validade do convite | 48 horas (`app.conta.convite.validade`) |
| H26 | O dono pode mandar o link do convite pelo WhatsApp dele, além de mostrar o QR em pessoa? | **só em pessoa**; o link não é senha, mas é de uso único e dá acesso à conta |
| H27 | O barbeiro edita a própria jornada e as próprias folgas? | **não**: só o dono, como hoje |
| H28 | O barbeiro cancela uma reserva própria? | **não** (D124 mantida) |
| H29 | O barbeiro vê preço e status do pagamento das próprias reservas? | **sim**, como hoje no painel |
| H30 | Limite de tentativas de aceite de convite | 10 por hora por IP (`app.limites.convite-*`) |
| H31 | Reativar um barbeiro desligado | como o desligamento anonimiza a conta (H33), "reativar" é cadastrar de novo: conta nova, por convite novo, ligada ao mesmo `barbeiro` se ainda dentro dos 5 anos |
| H32 | E-mail do barbeiro igual ao de uma conta de cliente que já existe | `409 EMAIL_JA_CADASTRADO`: usar outro e-mail. A alternativa (somar o papel à conta existente) fica para depois |
| H33 | Retenção da conta do barbeiro desligado | **revisada (20/11, parecer do Cautelar)**: **não** segue a H13. No dia do desligamento: `senha_hash` anulado; refresh tokens, convite pendente e identidade social apagados; `barbeiro.whatsapp` anulado; nome e e-mail de `usuario` anonimizados, ficando uma linha "lápide" (`id`, papel, `criado_em`, `anonimizado_em`) para `auditoria_admin.ator_id` não quebrar (LGPD arts. 12, 15 I e 16). `barbeiro.nome_exibicao` fica nas reservas por **5 anos depois da última reserva** dele e depois vira pseudônimo ("Ex-barbeiro #N") (LGPD art. 7º IX e VI; CDC art. 27). IP da auditoria: 1 ano; backups: até 30 dias (D128). **Para advogado:** teste de legítimo interesse (LGPD art. 10) e tipo de vínculo do barbeiro (CLT ou Lei 13.352/2016). Corrigir antes da 10B com barbeiro real |
