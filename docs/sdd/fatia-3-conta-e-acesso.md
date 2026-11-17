# 📐 Spec · Fatia 3 · Conta e acesso

| | |
|---|---|
| **Status** | ✅ **implementada** (17/11/2026): `./mvnw verify` com 114 testes, 0 falhas, 0 pulados, 97% de cobertura de instruções; `conferir-spec.sh 3`: 43 de 43 critérios com teste |
| **Fatias do plano** | S04 (cadastro, login, `/me`) + S05 (refresh rotativo, logout) + S06 (admin inicial, limite de tentativas, matriz de acesso) |
| **Branch** | `feat/fatia-3-conta-e-acesso` |
| **Regras** | [R23](../contexto.md#3-regras-de-negócio) (recurso alheio → 404), R24 (e-mail único), R25 (admin inicial), R26 (limite de tentativas), R27 (refresh rotativo), R30 (nada pessoal em log) · decisões do [ADR 0006](../adr/0006-sessao-jwt-refresh-cookie-bff.md) |
| **Banco** | `usuario`, `usuario_papel` e `refresh_token` já existem na V1; entra só a **V2** com `usuario.deve_trocar_senha` (resposta à dúvida D-1/D-3) |
| **Não cobre** | consulta de CEP no cadastro (Fatia 6: aqui o CEP é só validado e guardado) · exportar e excluir conta (Fatia 6) · rotas do painel (Fatia 5: aqui só a **regra** de acesso de `/admin/**`) · cookies do BFF (Fatia 7) · login social (pós-beta) · "esqueci minha senha" (ver dúvida D-1) |

## 1. Objetivo

Um cliente cria a conta e entra; o Eduardo entra como administrador com uma senha que nunca passou pelo repositório; a sessão se renova sem pedir senha de novo e pode ser encerrada; e a API passa a saber **quem** está chamando, respondendo `401`, `403` e `404` nos lugares certos. É a fundação do agendamento (Fatia 4) e do painel (Fatia 5).

## 2. Contrato

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/auth/cadastro` | público | `201` + `Location: /me` | `400 VALIDACAO_INVALIDA` · `400 CAMPO_DESCONHECIDO` · `400 JSON_MALFORMADO` · `409 EMAIL_JA_CADASTRADO` · `429 LIMITE_EXCEDIDO` |
| `POST` | `/auth/login` | público | `200` com par de tokens | `400` · `401 CREDENCIAIS_INVALIDAS` · `429 LIMITE_EXCEDIDO` |
| `POST` | `/auth/refresh` | público (com refresh) | `200` com novo par | `400` · `401 REFRESH_INVALIDO` · `401 REFRESH_REUTILIZADO` |
| `POST` | `/auth/logout` | público (com refresh) | `204` | `400` |
| `GET` | `/me` | logado | `200` perfil | `401 NAO_AUTENTICADO` |
| `PUT` | `/me/senha` | logado | `204` | `400 VALIDACAO_INVALIDA` · `401 CREDENCIAIS_INVALIDAS` (senha atual errada) · `401 NAO_AUTENTICADO` |

**Ajustes no contrato (v1.2.0), aplicados depois da aprovação:**
1. `POST /auth/cadastro` ganha `429 LIMITE_EXCEDIDO` (R26 já previa; faltava no OpenAPI).
2. Toda resposta `401` de rota protegida leva `WWW-Authenticate: Bearer`.
3. O contrato cita o cabeçalho `X-Request-Id`; o que a API usa desde a Fatia 1 é **`X-Trace-Id`**. Corrige o texto do contrato (CORS: aceitos `Authorization`, `Content-Type`, `X-Idempotency-Key`, `X-Guest-Token`, `X-Trace-Id`; expostos `Retry-After`, `X-Trace-Id`, `Location`).
4. `senha`: de 10 a 72 **bytes** em UTF-8 (o BCrypt corta em 72 bytes, e letra acentuada ocupa 2).
5. Rota nova `PUT /me/senha` (`senhaAtual`, `novaSenha`); `deveTrocarSenha` no login e em `GET /me`; código novo `403 TROCA_DE_SENHA_OBRIGATORIA`.

## 3. Decisões de desenho

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Como validar o JWT | API como **Resource Server** do Spring Security (`spring-boot-starter-security-oauth2-resource-server`), HS256, claims `sub` (id do usuário), `papeis`, `ver` e `exp`; validado só pelas claims, sem ir ao banco | é o caminho padrão do Spring, sem filtro feito à mão; o mesmo código serve se um dia o emissor for externo (OIDC) |
| E2 | Segredo do JWT | `JWT_SEGREDO` com no mínimo 32 bytes; ausente ou curto, **a API não sobe** (em qualquer perfil que não seja teste) | falhar na subida é melhor que assinar token com segredo fraco |
| E3 | Hash da senha | BCrypt com custo configurável (`app.seguranca.bcrypt-custo`, padrão 12; 4 nos testes) | padrão do contrato; custo alto só em produção para o teste não ficar lento |
| E4 | E-mail inexistente no login | executa um BCrypt "de mentira" e responde o **mesmo** `401 CREDENCIAIS_INVALIDAS`, com a mesma mensagem | sem isso, o tempo de resposta e o texto revelam quais e-mails têm conta (enumeração de usuários) |
| E5 | Refresh | valor aleatório de 32 bytes (`r1_` + base64url), guardado só como SHA-256; rotação a cada uso; `familia_id` liga a cadeia | ADR 0006; vazou o banco, não vazou a sessão |
| E6 | Rotação atômica | `UPDATE refresh_token SET usado_em = :agora WHERE hash = :hash AND usado_em IS NULL AND revogado_em IS NULL AND expira_em > :agora` e confere **1 linha afetada**; 0 linhas = decidir entre inválido e reutilizado | dois refreshes simultâneos com o mesmo token: o banco deixa um só vencer, sem `synchronized` |
| E7 | Reuso de refresh já usado | revoga **a família inteira** e responde `401 REFRESH_REUTILIZADO` (R27) | token usado duas vezes = alguém copiou; derruba o ladrão e o dono, que entra de novo com senha |
| E8 | Renovação e mudança de credencial | no refresh, confere `usuario.ativo`; a troca de senha (E23) revoga todos os refresh do usuário na hora. *Mudou na implementação:* o refresh é opaco e não carrega a versão, então a comparação `ver` × `versao_credencial` deu lugar à revogação direta | conta desativada ou senha trocada para de renovar em até 15 min |
| E9 | Logout | revoga a família do refresh enviado; token desconhecido também responde `204` | idempotente; não revela se o token existia |
| E10 | Limite de tentativas (R26) | porta `LimitadorDeTentativas` (hexagonal, ADR 0011) com adaptador **em memória** (janela deslizante, relógio do `Clock`): login 5 erros em 15 min por e-mail + IP; cadastro 5 por hora por IP; sucesso no login zera o contador | uma instância só no beta (Render free); a porta deixa trocar por Redis ou tabela sem mexer na regra. Limite aceito: reinício zera os contadores |
| E11 | Chave do limite | hash do e-mail normalizado + IP; o IP vem de `request.getRemoteAddr()` e só considera `X-Forwarded-For` em `prod` (`server.forward-headers-strategy=native`) | não guarda e-mail em claro na memória nem confia em cabeçalho que qualquer um forja em `dev` |
| E12 | `401` e `403` | `AuthenticationEntryPoint` e `AccessDeniedHandler` próprios devolvendo RFC 9457 (`NAO_AUTENTICADO`, `ACESSO_NEGADO`) com `traceId` | sem isso o Spring Security responde fora do padrão de erro da API |
| E13 | Rotas públicas | `/servicos`, `/barbeiros`, `/politica-pagamento`, `/disponibilidade`, `/auth/**`, `/actuator/health`; `/admin/**` exige `ADMIN` ou `BARBEIRO`; **todo o resto exige login** (negar por padrão) | rota nova nasce protegida; esquecer de anotar não abre nada |
| E14 | Matriz de acesso sem rotas de painel | testa a **regra** em `/admin/qualquer-coisa`: anônimo `401`, CLIENTE `403`, ADMIN `404` (a rota só nasce na Fatia 5) | prova a autorização sem inventar rota fora do contrato |
| E15 | CORS e CSRF | CORS só para as origens de `CORS_ORIGENS` (nunca `*`); CSRF desligado, com comentário: a API é stateless e só aceita `Bearer` | ADR 0006 |
| E16 | Atribuição em massa | o corpo do cadastro aceita só os campos do contrato; `papeis`, `id` ou `ativo` no JSON → `400 CAMPO_DESCONHECIDO` | ninguém se cadastra como ADMIN mandando um campo a mais (OWASP API3) |
| E17 | Normalização | e-mail: `trim` + minúsculas; WhatsApp e CEP: só dígitos (value objects em `comum`, que mascaram no `toString()`) | R24 e R30 |
| E18 | E-mail duplicado em corrida | além da consulta, captura a violação do índice único `usuario_email_uk` e responde `409` | dois cadastros simultâneos com o mesmo e-mail |
| E19 | Admin inicial (R25) | na subida: se **não existe** usuário com papel `ADMIN` e há `ADMIN_EMAIL` + `ADMIN_SENHA` (10 a 72 bytes), cria com papéis `ADMIN` e `BARBEIRO` e vincula ao barbeiro se houver **exatamente um** sem usuário. Já existe admin → não faz nada. Variáveis ausentes → sobe sem admin e registra um aviso **sem valores** | senha nunca no repositório nem sobrescrita por variável; o Eduardo é admin e barbeiro (D36) |
| E20 | Consentimento de marketing no cadastro | `aceitaMarketing` é gravado como primeira linha de `consentimento` pela API pública do módulo `privacidade` (só `registrarConsentimentoInicial`); o resto do módulo fica para a Fatia 6 | o campo já está no contrato do cadastro; aceitar e não gravar seria perder a prova do consentimento |
| E21 | CEP no cadastro | valida o formato, guarda os 8 dígitos e responde `endereco.completo: false`; bairro e cidade chegam com a Fatia 6 | a regra R31 já diz que CEP não resolvido não impede o cadastro |
| E23 | Troca de senha | `PUT /me/senha` confere a senha atual, grava o hash novo, **incrementa `versao_credencial`** e revoga todos os refresh do usuário | é o que faz E8 funcionar: sessão antiga para de renovar; e o Eduardo troca a senha inicial por uma que só ele conhece |
| E24 | Senha inicial do admin | o admin criado na subida nasce com `deve_trocar_senha = true`; o token leva a claim `trocar`; enquanto ela for verdadeira, `/admin/**` responde `403 TROCA_DE_SENHA_OBRIGATORIA` | quem configurou o servidor conhece a senha inicial; a regra obriga a troca antes de qualquer ação no painel (mesmo padrão da senha temporária do RKF Bank) |
| E25 | Limite por IP | além de e-mail + IP, 20 logins errados em 15 min **do mesmo IP** (com qualquer e-mail) bloqueiam o IP | o limite por e-mail não segura quem testa uma senha comum em muitos e-mails |
| E22 | Módulos | `conta` (cadastro, login, refresh, admin inicial) · `plataforma` (configuração de segurança, emissão e leitura do JWT, limitador) · `comum` (value objects `Email`, `Telefone`, `Cep`) | conta não conhece Spring Security por dentro; plataforma não conhece `usuario` |

## 4. Critérios de aceite

Relógio fixo dos testes: terça, 17/11/2026, 08:00 (-03:00). Dados fictícios (`@exemplo.test`).

### Cadastro

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | corpo válido do contrato | `POST /auth/cadastro` | `201`, `Location: /me`, `papeis: ["CLIENTE"]`, `endereco.cep` com 8 dígitos e `completo: false`; resposta **sem** `senha` nem `senhaHash` | `CadastroIT` |
| A2 | cadastro feito | olhar o banco | `senha_hash` começa com `$2` e é diferente da senha; e-mail em minúsculas; WhatsApp só dígitos | `CadastroIT` |
| A3 | conta `cliente.a@exemplo.test` | cadastrar `CLIENTE.A@Exemplo.Test` | `409 EMAIL_JA_CADASTRADO` (R24) | `CadastroIT` |
| A4 | campos inválidos (nome de 1 letra, e-mail sem `@`, WhatsApp de 5 dígitos, senha de 9, CEP de 7) | cadastrar | `400 VALIDACAO_INVALIDA` com **um item em `errors[]` por campo** | `CadastroIT` |
| A5 | corpo com `"papeis": ["ADMIN"]` | cadastrar | `400 CAMPO_DESCONHECIDO`; nenhuma conta criada | `CadastroIT` |
| A6 | JSON quebrado | cadastrar | `400 JSON_MALFORMADO` | `CadastroIT` |
| A7 | senha com 37 letras acentuadas (74 bytes) | cadastrar | `400 VALIDACAO_INVALIDA` no campo `senha` | `CadastroIT` |
| A8 | `aceitaMarketing: true` / ausente | cadastrar | uma linha em `consentimento` com `aceito = true` / `false`, com versão do texto | `CadastroIT` |
| A9 | dois cadastros simultâneos com o mesmo e-mail | cadastrar | exatamente um `201` e um `409`; uma conta no banco | `CadastroIT` |

### Login e `/me`

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A10 | conta existente | `POST /auth/login` com a senha certa | `200` com `accessToken`, `tipo: "Bearer"`, `expiraEmSegundos: 900`, `refreshToken` começando por `r1_`, `refreshExpiraEm` = agora + 7 dias e `usuario` | `LoginIT` |
| A11 | o token do A10 | decodificar | HS256; `sub` = id do usuário; `papeis` = `["CLIENTE"]`; `ver` = 0; `exp` = agora + 15 min | `LoginIT` |
| A12 | conta existente | login com senha errada **e** login com e-mail que não existe | os dois `401 CREDENCIAIS_INVALIDAS` com `title` e `detail` **idênticos** | `LoginIT` |
| A13 | login com o e-mail em MAIÚSCULAS | entrar | `200` | `LoginIT` |
| A14 | usuário com `ativo = false` | login com a senha certa | `401 CREDENCIAIS_INVALIDAS` | `LoginIT` |
| A15 | token válido | `GET /me` | `200` com o mesmo formato do cadastro | `MeIT` |
| A16 | sem token · token com assinatura errada · token vencido (relógio adiantado 16 min) | `GET /me` | `401 NAO_AUTENTICADO` em `problem+json`, com `WWW-Authenticate: Bearer` e `traceId` | `MeIT` |

### Refresh e logout

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A17 | refresh válido | `POST /auth/refresh` | `200` com par **novo**; no banco só existe o hash (64 hex), nunca o valor | `RefreshRotativoIT` |
| A18 | o refresh antigo do A17 | usar de novo | `401 REFRESH_REUTILIZADO`; **toda a família** fica revogada: o refresh novo também para de funcionar (R27) | `RefreshRotativoIT` |
| A19 | refresh desconhecido · vencido (relógio + 8 dias) | renovar | `401 REFRESH_INVALIDO` | `RefreshRotativoIT` |
| A20 | usuário desativado depois do login | renovar | `401 REFRESH_INVALIDO` (o caso da senha trocada está no A40) | `RefreshRotativoIT` |
| A21 | o mesmo refresh em 8 requisições simultâneas | renovar | **no máximo uma** `200`; as demais `401`; nenhuma dupla de tokens válidos da mesma origem | `RefreshRotativoIT` |
| A22 | sessão aberta | `POST /auth/logout` e depois renovar | `204`; renovação `401`; logout repetido ou com token desconhecido também `204` | `LogoutIT` |

### Admin inicial

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A23 | banco sem admin, `ADMIN_EMAIL` e `ADMIN_SENHA` definidos (senha gerada no teste) | subir | usuário criado com papéis `ADMIN` e `BARBEIRO`, senha só como hash, vinculado ao único barbeiro sem usuário; login responde `200` com os dois papéis | `AdminInicialIT` |
| A24 | admin já existe | subir de novo com **outra** `ADMIN_SENHA` | nada muda: a senha antiga continua valendo, a nova não entra | `AdminInicialIT` |
| A25 | banco sem admin e sem as variáveis | subir | a API sobe, não há admin e o aviso no log **não** contém e-mail nem senha | `AdminInicialIT` |
| A26 | `ADMIN_SENHA` com 5 caracteres | subir | a subida falha com mensagem que não mostra a senha | `AdminInicialIT` |

### Limite de tentativas

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A27 | 5 logins errados em 15 min para o mesmo e-mail e IP | 6º login (mesmo com a senha **certa**) | `429 LIMITE_EXCEDIDO` com `Retry-After` em segundos > 0 | `LimiteTentativasIT` |
| A28 | o bloqueio do A27 | relógio + 15 min | o login volta a funcionar | `LimitadorDeTentativasTest`, `LimiteTentativasIT` |
| A29 | 4 erros e depois um acerto | mais 4 erros | não bloqueia (o acerto zerou a contagem) | `LimitadorDeTentativasTest` |
| A30 | 5 erros para o e-mail A | login do e-mail B, mesmo IP | `200` (a chave é e-mail + IP) | `LimiteTentativasIT` |
| A31 | 5 cadastros do mesmo IP em 1 hora | 6º cadastro | `429 LIMITE_EXCEDIDO` com `Retry-After` | `LimiteTentativasIT` |

### Acesso, CORS e privacidade

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A32 | sem login | `GET /servicos`, `/barbeiros`, `/politica-pagamento`, `/disponibilidade`, `/actuator/health` | continuam `200` (públicas) | `MatrizAcessoIT` |
| A33 | anônimo · CLIENTE · ADMIN | `GET /admin/qualquer-coisa` | `401 NAO_AUTENTICADO` · `403 ACESSO_NEGADO` · `404 RECURSO_NAO_ENCONTRADO` | `MatrizAcessoIT` |
| A34 | sem login | uma rota que não é pública nem existe (`/qualquer-rota-nova`) | `401` (negar por padrão), não `404` | `MatrizAcessoIT` |
| A35 | `CORS_ORIGENS=https://front.exemplo.test` | preflight `OPTIONS /auth/login` dessa origem · de outra origem | permitido com `Access-Control-Allow-Origin` igual à origem · recusado; nunca `*` | `SegurancaWebIT` |
| A36 | `JWT_SEGREDO` ausente ou com 10 bytes | subir | a API **não sobe**, com mensagem que não mostra o segredo | `SegurancaWebIT` |
| A37 | cadastro, login errado, login certo, refresh e logout | ler o log capturado | **nenhum** e-mail, WhatsApp, CEP, senha, token ou refresh aparece (R30) | `SemDadoPessoalNoLogIT` |
| A38 | value objects `Email`, `Telefone`, `Cep` | `toString()` | mascarado (`c***@exemplo.test`, `*******0001`, `*****000`) | `ValueObjectsTest` |
| A40 | usuário logado | `PUT /me/senha` com a senha atual certa e uma nova válida | `204`; a senha antiga não entra mais, a nova entra; `versao_credencial` + 1; os refresh antigos param de renovar | `TrocaDeSenhaIT` |
| A41 | usuário logado | `PUT /me/senha` com a senha atual errada · com nova senha de 5 caracteres | `401 CREDENCIAIS_INVALIDAS` · `400 VALIDACAO_INVALIDA`; a senha não muda | `TrocaDeSenhaIT` |
| A42 | admin recém-criado na subida | login · `GET /admin/qualquer-coisa` · trocar a senha, entrar de novo e repetir | `deveTrocarSenha: true` · `403 TROCA_DE_SENHA_OBRIGATORIA` · `deveTrocarSenha: false` e `404` | `AdminInicialIT` |
| A43 | 20 logins errados do mesmo IP com e-mails diferentes | 21º login desse IP | `429 LIMITE_EXCEDIDO` | `LimiteTentativasIT` |
| A39 | código da fatia | `./mvnw verify` | `ModulithTest` e `ArquiteturaTest` verdes: `conta` não acessa pacotes internos de `plataforma` nem o contrário | `ModulithTest` |

## 5. Entregas

| Módulo | Arquivos |
|---|---|
| `comum` | value objects `Email`, `Telefone`, `Cep` (validam, normalizam e mascaram) |
| `plataforma` | `SegurancaConfig` (cadeia de filtros, rotas públicas, CORS, Resource Server) · `EmissorDeToken` (API pública: emite o JWT a partir de id, papéis e versão) · `TratadoresDeAcesso` (401 e 403 em RFC 9457) · porta `LimitadorDeTentativas` + adaptador em memória · validação do `JWT_SEGREDO` na subida |
| `conta` | entidades `Usuario`, `RefreshToken` · `CadastroController`, `SessaoController` (login, refresh, logout), `PerfilController` (`/me`) · casos de uso `CadastrarCliente`, `Entrar`, `RenovarSessao`, `EncerrarSessao` · `AdminInicial` (subida) |
| `privacidade` | API pública `Consentimentos.registrarInicial(...)` e a entidade `Consentimento` |
| docs | contrato 1.2.0 · decisões · guia de testes (cenário do cliente C1 a C3, C9 e do admin AD1, AD2 viram ✅ com saída real) · roteiro privado (login do admin pela API) · `.env.example` · README · `como-foi-construido.md` |

## 6. Riscos e cuidados

- **Limite em memória**: reinício da API (o Render free hiberna) zera os contadores. Aceito no beta; o gatilho para trocar o adaptador é a Fase 2 (produção real).
- **Refresh em corrida legítima**: duas abas renovando ao mesmo tempo derrubam a sessão (A21). O BFF (Fatia 7) renova num lugar só; registrado como comportamento esperado.
- **Token de acesso não é revogável**: vale até 15 min depois de logout ou desativação (ADR 0006, risco aceito).
- **BCrypt e 72 bytes**: validar bytes, não caracteres (A7).
- **Teste de log (A37)**: captura a saída real do processo; precisa cobrir também as mensagens de erro do Spring Security.

## 7. Dúvidas respondidas (17/11/2026)

| # | Dúvida | Decisão |
|---|---|---|
| D-1 | "Esqueci minha senha" | **clientes: pós-beta** (precisa de envio de e-mail; quem esquecer ainda agenda sem conta). **Admin:** passo a passo privado de redefinição pelo banco. E entra já nesta fatia a **troca de senha logado** (`PUT /me/senha`, E23), que resolve o caso mais comum sem e-mail |
| D-2 | Limites e validades | adotados e configuráveis: login 5 erros em 15 min por e-mail + IP, mais 20 por IP (E25); cadastro 5 por hora por IP; acesso 15 min; refresh 7 dias, renovado a cada uso |
| D-3 | Admin inicial é o Eduardo | sim: papéis `ADMIN` e `BARBEIRO`, vinculado ao cadastro de barbeiro dele, com **troca obrigatória da senha inicial** (E24) |

## 8. Pronto quando

- [ ] A1 a A43 com teste verde (`sh scripts/conferir-spec.sh 3 --exigir`), nenhum pulado
- [ ] `redocly lint` com 0 erros no contrato 1.2.0
- [ ] cadastro, login, `/me`, refresh e logout rodados à mão no perfil `dev`, com as respostas no guia de testes
- [ ] [checklist da PR](checklist-pr.md) completo
