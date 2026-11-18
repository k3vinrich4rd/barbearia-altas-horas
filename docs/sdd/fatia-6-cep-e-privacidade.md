# 📐 Spec · Fatia 6 · CEP com degradação e privacidade (LGPD)

| | |
|---|---|
| **Status** | ✅ aprovada pelo Kevin em 17/11/2026 (D-1 a D-7 aceitas como propostas; 6C acrescentada a pedido dele, D-8 e D-9) |
| **Fatias do plano** | S15 (CEP) + S16 (privacidade) + as pendências que as Fatias 3, 4 e 5 empurraram para cá: consentimento de marketing completo (E20 da 3), CEP no cadastro (E21 da 3), anonimizar `reserva_guest` (E8 da 4), retenção do IP e da auditoria (D77), guia C13 |
| **Entrega em três PRs** | **6A** · CEP: consulta, cache, degradação e CEP no cadastro (`feat/fatia-6a-cep`) · **6B** · privacidade: consentimentos, política, exportar, excluir e retenção (`feat/fatia-6b-privacidade`) · **6C** · alcance por região: CEP opcional do visitante, região na reserva, painel e vitrine pública (`feat/fatia-6c-alcance`). Cada uma só começa com a anterior na `main` |
| **Regras** | [R28](../contexto.md#3-regras-de-negócio) (consentimento opcional, versionado, revogável), R29 (guest anonimizado após o prazo, H13), R30 (nada pessoal em log), R31 (CEP nunca derruba o cadastro), R23 (recurso alheio → 404), R26 (limite de tentativas, H14); seção 8 do dossiê (inventário LGPD) |
| **ADRs** | [0008](../adr/0008-cep-com-cache-e-degradacao.md) (CEP), [0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md) (LGPD), [0002](../adr/0002-comunicacao-entre-modulos.md) (módulos e eventos), [0005](../adr/0005-erros-rfc-9457.md) (erros) |
| **Custo** | **zero** (D59): ViaCEP e BrasilAPI são gratuitas e sem conta; nenhum serviço pago e nenhuma chave. Nos testes, **nenhuma chamada de rede real** (A22) |
| **Não cobre** | telas (Fatia 7) · "completar o endereço" depois do cadastro (não existe `PUT /me` no contrato; pós-beta) · mapa com pontos (precisa de coordenadas; pós-beta) · pedido de direitos feito por **visitante** sem conta (no beta é por contato com a barbearia, E19) · CPF (não é guardado, ADR 0009) · avisar o titular por e-mail ou WhatsApp (pós-beta) · parecer jurídico: prazos e bases legais seguem como **H13, pendentes de validação** |

## 1. Objetivo

Quem se cadastra digita o CEP e o bairro e a cidade aparecem sozinhos, **e se ViaCEP e BrasilAPI caírem o cadastro continua** (6A). O titular consegue **ver e revogar o consentimento de marketing, baixar seus dados, apagar a conta** e ler a política de privacidade; o contato dos visitantes some depois de 90 dias e o IP da auditoria depois de 1 ano, sem ninguém precisar lembrar (6B). Depois desta fatia o back-end do beta está completo; falta a tela (Fatia 7) e o deploy (Fatia 8).

## 2. Contrato

As rotas já existem no [`openapi.yaml`](../contrato/openapi.yaml), exceto as marcadas como **nova**. Todas as de `/me/**` exigem login (`401`) e, para o admin com senha inicial, a troca de senha (`403 TROCA_DE_SENHA_OBRIGATORIA`, como as demais rotas de conta).

### 6A · CEP

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `GET` | `/cep/{cep}` | público (sem token) | `200` `Endereco` (`fonte`: `VIACEP`, `BRASILAPI` ou `CACHE`) | `400 CEP_INVALIDO` · `404 CEP_NAO_ENCONTRADO` · `429` (com `Retry-After`) · `503 CEP_INDISPONIVEL` |
| `POST` | `/auth/cadastro` (muda o comportamento) | público | `201` `Perfil` com `endereco.completo` verdadeiro quando o CEP foi resolvido | os de sempre; **nunca** `404` ou `503` por causa do CEP (R31) |

### 6B · privacidade

| Método | Rota | Quem | Sucesso | Erros (além do `401`) |
|---|---|---|---|---|
| `GET` | `/me/consentimentos` | qualquer conta logada | `200` lista com o **estado atual** de cada finalidade | |
| `PUT` | `/me/consentimentos/marketing` | qualquer conta logada | `200` `Consentimento` | `400 VALIDACAO_INVALIDA` (corpo ruim ou `versaoTexto` que não é a vigente) · `400 CAMPO_DESCONHECIDO` |
| `GET` | `/me/dados` | qualquer conta logada | `200` `ExportacaoDados` (`Cache-Control: no-store`) | `429` |
| `DELETE` | `/me` | só `CLIENTE` | `204` | `403 ACESSO_NEGADO` (BARBEIRO e ADMIN) · `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` (**novo**) |
| `GET` | `/politica-privacidade` (**nova**) | público | `200` `PoliticaPrivacidade` `{ versao, textoConsentimento, politica, atualizadaEm }` | |

### 6C · alcance por região

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/agendamentos/guest` (muda) | público | o corpo aceita `cep` **opcional** (8 dígitos, com ou sem hífen) | `400 CEP_INVALIDO` só para formato errado; CEP inexistente ou provedor fora **nunca** impedem a reserva (R31) |
| `GET` | `/admin/alcance` (**nova**) | `ADMIN` (todos, com filtro `barbeiroId`) e `BARBEIRO` (sempre só os próprios) | `200` `AlcancePainel` `{ periodo, total, semRegiao, cidades[{ cidade, uf, atendimentos, percentual, bairros[{ bairro, atendimentos, percentual }] }] }` | `400 VALIDACAO_INVALIDA` (período invertido ou maior que 366 dias) · `401` · `403` · `404` (barbeiro inexistente) |
| `GET` | `/alcance` (**nova**) | público (sem token) | `200` `AlcancePublico` `{ atualizadoEm, totalCidades, totalBairros, regioes[{ cidade, uf, bairros[] }] }`, **sem contagens**, últimos 12 meses | `429` |

### Ajustes no contrato (v1.5.0), aplicados depois da aprovação

1. Rota nova `GET /politica-privacidade` e schema `PoliticaPrivacidade` (a versão devolvida é a mesma que o `PUT /me/consentimentos/marketing` exige).
2. Código novo `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` em `DELETE /me` (e no catálogo de erros do `contrato-api.md`).
3. `429` documentado em `GET /me/dados`; `403` já existe em `DELETE /me`.
4. `ExportacaoDados` detalhado: `conta { nome, email, whatsapp, endereco { cep, bairro, cidade, uf } }` e `agendamentos[] { id, inicio, fim, status, servico, precoCentavos, motivoCancelamento }` (hoje são `object` solto).
5. `GET /me/consentimentos`: o texto "consentimentos atuais" passa a dizer "estado atual por finalidade (a linha mais recente)"; o histórico completo sai só na exportação.
6. 6C: campo `cep` opcional em `NovoAgendamentoGuest`, rotas `GET /admin/alcance` e `GET /alcance`, schemas `AlcancePainel` e `AlcancePublico`.

### Banco

**6C:** `V6__alcance.sql` acrescenta `reserva.regiao_bairro varchar(120)`, `reserva.regiao_cidade varchar(120)`, `reserva.regiao_uf char(2)` (nuláveis) e `reserva_guest.cep char(8)` (nulável, com o mesmo `CHECK` de 8 dígitos do `usuario`). **6A e 6B: nenhuma migração obrigatória.** `consentimento`, `solicitacao_titular`, `reserva_guest.anonimizado_em`, `usuario.anonimizado_em`, `auditoria_admin.ip` (nulável) e `shedlock` já existem na V1. Só se a 6B precisar de um índice para o job de retenção da auditoria sai uma `V6__privacidade.sql` pequena (`auditoria_admin (ocorrido_em)` já tem índice: provavelmente não precisa).

## 3. Decisões de desenho

### 6A · CEP

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Quem fala com os provedores | O módulo `cep` expõe só `CepApi.buscar(Cep): Optional<Endereco>` (e uma exceção `CepIndisponivel`); controller, clientes dos provedores e cache ficam no pacote interno. O controller só faz: sanitizar, limitar, chamar `CepApi` e traduzir (`vazio` → `404`, `CepIndisponivel` → `503`). `conta` passa a declarar `allowedDependencies = {"cep", "privacidade", "comum"}` | ADR 0002; o cadastro reaproveita a mesma porta e o mesmo cache |
| E2 | Formato do que entra | Aceita `01001000` e `01001-000` (padrão do contrato), normaliza para 8 dígitos com `Cep.de` (já existe em `comum`); qualquer outra coisa → `400 CEP_INVALIDO` **antes** de qualquer chamada externa. `00000000` é formato válido (vai para o provedor e dá "não encontrado") | OWASP API7 (SSRF/injeção): só 8 dígitos chegam na URL do provedor; o CEP nunca é montado por concatenação com texto livre |
| E3 | Ordem e o que é "falha" | ViaCEP primeiro, BrasilAPI depois (ADR 0008). **Falha** = timeout (conexão 1 s, leitura 2 s), erro de rede, `5xx`, `429`, corpo que não é JSON, corpo sem os campos esperados ou com `cep` diferente do pedido: passa para o próximo provedor. **Não é falha**: ViaCEP respondendo `{"erro": true}` (ou `404`) = o CEP não existe → vazio, **sem** consultar a BrasilAPI (não gasta 3 s à toa). Se o ViaCEP **falhou** e a BrasilAPI responde `404` → vazio (`404 CEP_NAO_ENCONTRADO`). Os dois falharam → `CepIndisponivel` → `503 CEP_INDISPONIVEL`. **Sem retry** dentro da requisição | pior caso 3 s; o fallback já é a segunda tentativa |
| E4 | Mapeamento | ViaCEP: `logradouro`, `bairro`, `localidade`→`cidade`, `uf`. BrasilAPI v1: `street`→`logradouro`, `neighborhood`→`bairro`, `city`→`cidade`, `state`→`uf`. Campo vazio vira `null` (CEP "geral" de cidade pequena não tem logradouro nem bairro). Texto cortado em 120 caracteres (tamanho das colunas de `usuario`); `uf` com 2 letras maiúsculas, senão a resposta é inválida (falha, E3) | o contrato do `Endereco` aceita `null`; a coluna `usuario.uf` tem `CHECK` de 2 letras |
| E5 | Cache | **Caffeine**, em memória, dois caches: **encontrado** (TTL 7 dias, máximo 10 mil entradas) e **inexistente** (TTL 1 h, mesmo teto). **Falha de provedor nunca entra no cache.** Acerto no cache responde com `fonte: CACHE`. Os valores de TTL e teto vêm de `app.cep.*` (padrão = ADR 0008), para o teste poder usar valores pequenos. O tempo do cache usa um `Ticker` injetável (falso nos testes): **sem `Thread.sleep`** | cache de falha esconderia a volta do provedor; relógio injetável é o padrão do projeto (`Clock`) |
| E6 | CEP no cadastro (R31) | `CadastrarCliente` consulta `CepApi` **antes** de abrir a transação (nunca segura conexão do banco esperando rede), com todo erro engolido: resolveu → grava `bairro`, `cidade`, `uf` junto com o CEP; vazio, `CepIndisponivel` ou qualquer exceção inesperada → grava só o CEP e deixa os três nulos. O `Perfil` responde `endereco.completo = true` só quando bairro e cidade existem. Sem CEP no pedido, **nenhuma** chamada externa. O limite de cadastro (5 por hora por IP, H14) já protege os provedores | a regra R31 e a E21 da Fatia 3 |
| E7 | Limite da rota pública | Nova hipótese **H21** (H15 já era a validade das sessões): `GET /cep/{cep}` aceita **30 consultas por minuto por IP** (`app.limites.cep-max`, `cep-janela`); passou → `429` com `Retry-After`, como R26. É a primeira rota pública que gasta um serviço de terceiros | sem limite, um robô usa nossa instância para martelar o ViaCEP e nós levamos o bloqueio |
| E8 | Nada de CEP no log | O CEP (inteiro ou parcial) não vai para log, mensagem de exceção nem métrica. Falha de provedor loga só `provedor`, `tipo` (`TIMEOUT`, `HTTP_5XX`, `CORPO_INVALIDO`) e `traceId`. Usa `Cep.toString()`, que já mascara | R30; o CEP é dado pessoal (localiza a casa) |
| E9 | Nenhuma rede nos testes | Os clientes dos provedores são `RestClient` montados por um `RestClient.Builder` injetado; os testes ligam `MockRestServiceServer` nesse builder e conferem cada requisição (URL, método). No perfil `test` as URLs base (`app.cep.viacep-url`, `app.cep.brasilapi-url`) apontam para `http://localhost:1/` (porta fechada): se algum teste esquecer o mock, falha na hora em vez de sair para a internet. Timeout e erro de rede são simulados por um falso que lança, porque o `MockRestServiceServer` não simula espera | custo zero (D59) e testes que passam sem internet |

### 6B · privacidade

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E10 | Mapa de módulos sem ciclo | `conta` já chama `privacidade` (cadastro grava o consentimento), então `privacidade` **não** pode chamar `conta` nem `agenda`. A 6B usa dois contratos publicados por `privacidade`: (1) `ExclusaoSolicitada(usuarioId)` e (2) a interface `FonteDeDadosDoTitular { String chave(); Map<String,Object> exportar(UUID usuarioId); }`. `conta` e `agenda` **implementam** a fonte (a exportação junta as respostas) e **escutam** o evento. `privacidade` só toca nas próprias tabelas (`consentimento`, `solicitacao_titular`). O `allowedDependencies` de `privacidade` fica `{"comum"}` | ADR 0002 e o mapa de eventos da arquitetura (`privacidade.ExclusaoSolicitada`); nenhuma tabela alheia é lida |
| E11 | Consentimento: estado atual e histórico | `consentimento` é só inserção (R28). `GET /me/consentimentos` devolve **a linha mais recente** de cada finalidade (hoje só `MARKETING_CEP`); conta sem nenhuma linha (admin inicial) devolve lista vazia. O histórico completo só sai na exportação (E14) | o titular quer saber "estou aceitando?", e o histórico é prova |
| E12 | `PUT` de consentimento | `aceito` obrigatório; `versaoTexto` precisa ser **a vigente** (`Consentimentos.VERSAO_DO_TEXTO`, hoje `1.0`, a mesma de `GET /politica-privacidade`); outra versão → `400 VALIDACAO_INVALIDA` com `errors[].field = versaoTexto` (a tela recarrega o texto) e nada é gravado. Se o estado pedido **já é** o atual com a mesma versão, devolve `200` com a linha existente e **não** grava outra (duplo clique não polui o histórico); mudou de estado → linha nova. Revogar **não** apaga CEP, bairro e cidade da conta: eles são da operação, o consentimento é só para o uso em marketing | R28; finalidade separada (ADR 0009) |
| E13 | Política de privacidade | Os textos ficam versionados no repositório (`backend/src/main/resources/privacidade/`, um arquivo por versão), servidos por `GET /politica-privacidade`: `versao`, `textoConsentimento` (o texto da caixa do cadastro), `politica` (texto corrido: o que se guarda, para quê, por quanto tempo, como exercer os direitos e o canal de contato), `atualizadaEm`. O canal de contato vem de `app.privacidade.contato` (variável de ambiente; sem dado pessoal no repositório). Mudou o texto → versão nova, **sem apagar a anterior** | a prova do consentimento precisa do texto exato que a pessoa viu |
| E14 | Exportação (`GET /me/dados`) | Monta com `FonteDeDadosDoTitular`: `conta` (nome, e-mail, WhatsApp, endereço), `consentimentos` (**histórico inteiro**), `agendamentos` (as reservas em que a conta é o cliente: id, início, fim, status, nome do serviço, preço, motivo do cancelamento). **Nunca** sai: hash de senha, `versaoCredencial`, refresh token, token de visitante, CPF (não existe no banco), IP, ids de outras pessoas. Grava `solicitacao_titular` `EXPORTACAO` com `concluida_em`. Limite: **5 por hora por conta** (H15, `429`); `Cache-Control: no-store`. Sem paginação (o beta tem poucas reservas por cliente; o ADMIN não é o foco) | direito de acesso (LGPD art. 18) sem virar vazamento; é a rota que mais devolve dado pessoal por chamada |
| E15 | Exclusão (`DELETE /me`): quem e o quê | Só `CLIENTE`; `BARBEIRO` e `ADMIN` → `403 ACESSO_NEGADO` (a conta de equipe está ligada a barbeiro e à auditoria e se encerra pelo ADMIN fora do app). Numa **única transação** (listeners **síncronos**, não o outbox, porque o `204` precisa significar "acabou"): `privacidade` grava `solicitacao_titular` `EXCLUSAO` e publica `ExclusaoSolicitada`; `conta` **anonimiza**; `agenda` cancela as reservas futuras não pagas; `privacidade` grava a revogação final do consentimento. Falhou qualquer passo → tudo desfaz | a arquitetura previa o evento, mas o login tem que falhar logo depois do `204` (C13) |
| E16 | Anonimizar a conta | `usuario`: `nome = 'Conta excluída'`, `email = 'excluido-<id>@anonimo.invalid'` (o e-mail antigo fica livre para um novo cadastro; o índice único é por `lower(email)`), `whatsapp`, `senha_hash`, `cep`, `bairro`, `cidade`, `uf` = `NULL`, `ativo = false`, `anonimizado_em = agora`, `versao_credencial + 1` (derruba todo token de acesso já emitido). **Todos** os `refresh_token` da conta são apagados. O `usuario` **não** é apagado (reservas, auditoria e `solicitacao_titular` apontam para ele) | LGPD art. 18 (eliminação) sem quebrar chave estrangeira; a linha sobra sem dado pessoal |
| E17 | Reservas na exclusão (ver A61a: devolução pendente também bloqueia) | **Futura e não paga** (`AGUARDANDO_PAGAMENTO` com `inicio` depois de agora): vira `CANCELADO` com `motivo_cancelamento = 'Conta excluída pelo titular'` e `cancelado_por` = o próprio usuário; o `ReservaCancelada` de sempre (D72) cancela a cobrança pendente e o horário volta a ficar livre. **Futura e paga** (`CONFIRMADO`): a exclusão é **bloqueada** com `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` e nada muda: sem a conta e sem o WhatsApp, o Eduardo não teria a quem devolver o Pix (R14, D33: cliente não cancela sozinho). O titular combina o cancelamento com a barbearia e depois exclui. **Passadas** (`CONCLUIDO`, `NAO_COMPARECEU`, `CANCELADO`, `EXPIRADO`) ficam: a reserva já não tem dado pessoal (nome e contato vinham do `usuario`, agora anonimizado) e a cobrança só guarda ids e valores (ADR 0009: registro financeiro, H13). O painel mostra "Conta excluída" no nome e **sem** WhatsApp | muda a linha do ADR 0009 ("cancela reservas futuras"): só cancela as **não pagas**. Ver dúvida D-1 |
| E18 | Retenção: o job | `RetencaoDeDados` (módulo `privacidade`, `@Scheduled` diário às 03:00 no fuso da barbearia, **ShedLock**, mesmo padrão dos jobs existentes e ligado/desligado por `app.jobs.habilitados`). Ele não lê tabela alheia: chama cada `LimpezaPorRetencao { String nome(); int executar(Instant agora); }` publicada pelo módulo dono do dado (`agenda` para `reserva_guest`, `admin` para o IP da auditoria, `pagamento` para `evento_webhook`), e loga só `nome` e quantidade. Cada limpeza é idempotente (`WHERE anonimizado_em IS NULL`), em lotes de até 500 linhas por transação, e usa o `Clock` | os prazos são hipóteses (H13) e vêm de propriedades; o job não pode travar a tabela inteira de uma vez |
| E19 | `reserva_guest` (R29, H13) | Passou **90 dias do fim da reserva** (`reserva.fim <= agora - 90 dias`, **inclusivo**: no dia 90 em ponto já anonimiza) e `anonimizado_em IS NULL`: `nome = 'Visitante anonimizado'`, `email = 'anonimizado-<reservaId>@anonimo.invalid'`, `whatsapp = '00000000000'` (a `CHECK` exige 10 a 13 dígitos), `token_hash` trocado por um SHA-256 de valor aleatório (o link que o visitante guardou deixa de funcionar: `404`), `anonimizado_em = agora`. A reserva e a cobrança **não** mudam. Prazo em `app.privacidade.retencao-guest-dias` (padrão 90). Visitante pedir exclusão antes do prazo: no beta é por contato com a barbearia (canal da política), o Eduardo não tem tela para isso (pós-beta) | o dossiê já diz que a anonimização é por prazo; sem rota para visitante porque ele não tem conta nem identidade verificável |
| E20 | Auditoria e o IP (D77) | Passou **1 ano** (`ocorrido_em <= agora - 365 dias`): `ip = NULL`. A **linha fica** (ator, ação, recurso, `detalhe`, data): continua provando quem fez o quê, e não tem dado pessoal. Prazo em `app.privacidade.retencao-ip-dias` (padrão 365). Ver dúvida D-2 | o IP é dado pessoal; "quem fez o quê" é prova de segurança |
| E21 | `evento_webhook` | Eventos **já processados** (`processado_em` preenchido) com mais de 1 ano (`recebido_em`) são apagados; os que ainda aguardam ou estão em `REVISAO_MANUAL` ficam. Só ids e resultado (sem payload), então é limpeza de tamanho de tabela, não de dado pessoal | ADR 0009 (1 ano, sem payload); o Aiven free tem 1 GB |
| E22 | Registro dos pedidos | `solicitacao_titular` guarda **tipo, usuário e datas**, nada do conteúdo exportado | prova de que o pedido foi atendido, sem copiar o dado |
| E23 | Log e auditoria | Exportar, excluir e consentir **não** são ações de painel: não vão para `auditoria_admin` (o titular age sobre os próprios dados; o registro é `solicitacao_titular` e `consentimento`). O log só tem `traceId`, rota e status | R30: o log não pode ser outra cópia do que acabou de ser apagado |

### 6C · alcance por região

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E30 | De onde vem a região | **Cliente:** cópia de `usuario.bairro/cidade/uf` no momento da reserva (via `conta.Contas`, sem ler a tabela alheia). **Visitante:** o `cep` opcional é resolvido pelo `CepApi` **fora** da transação; encontrado → grava a região e o CEP em `reserva_guest.cep`; não encontrado ou provedor fora → reserva criada sem região | a estatística não muda se o cliente se mudar e sobrevive à exclusão da conta (E17) |
| E31 | O que conta | só reservas `CONFIRMADO` e `CONCLUIDO` com `inicio` no período; as demais ficam fora. Reserva sem região entra em `semRegiao` | mede atendimento de verdade, não tentativa |
| E32 | k-anonimato (ADR 0009) | bairro com **menos de 5** atendimentos no recorte vira "Outros bairros" na cidade; cidade com menos de 5 vira "Outras cidades" (`uf` nulo). Vale no painel e na vitrine | nenhum grupo pequeno aponta para uma pessoa |
| E33 | Base legal (D-8) | o resumo agregado com k ≥ 5 usa **legítimo interesse** (estatística da própria barbearia) e conta todos os atendimentos, não só quem consentiu; a política 1.0 explica. Mandar promoção para alguém continua exigindo `MARKETING_CEP` | atualiza o ADR 0009 ("conta só quem consentiu" passa a valer só para marketing direto) |
| E34 | Retenção | na anonimização do visitante (90 dias, E18) `reserva_guest.cep` vira `NULL`; a região da reserva **fica**. Na exclusão da conta a região também fica | bairro e cidade, sem nome nem contato, não identificam ninguém |
| E35 | Vitrine pública (D-9) | `GET /alcance` devolve **só nomes** de cidades e bairros que passam no k ≥ 5, ordenados por volume, sem números, dos últimos 12 meses; cache de 1 hora; limite `app.limites.alcance-*` de 30 por minuto por IP | chamariz para quem vai agendar, sem expor o movimento da barbearia |
| E36 | Barbeiro | `BARBEIRO` vê só os próprios atendimentos: o `barbeiroId` da query é trocado pelo dele. `ADMIN` vê tudo ou filtra por barbeiro | cada barbeiro vê o alcance do próprio trabalho |

## 4. Critérios de aceite

Relógio fixo: terça, **01/12/2026, 09:30** (-03:00). Datas conferidas por código: 01/12 é terça; 90 dias antes é **02/09/2026 09:30**; 365 dias antes é **01/12/2025 09:30**. CEPs dos testes: `01001000` (Praça da Sé, São Paulo, SP) e `99999999` (não existe). Contas, nomes, e-mails e IPs são fictícios (`cliente.a@exemplo.test`, `203.0.113.7`).

### 6A · CEP

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | ViaCEP devolve Praça da Sé, Sé, São Paulo, SP | `GET /cep/01001000` sem token | `200` com `logradouro`, `bairro = Sé`, `cidade = São Paulo`, `uf = SP`, `fonte = VIACEP`; uma única chamada, na URL do ViaCEP com os 8 dígitos | `CepIT` |
| A2 | o mesmo CEP | `GET /cep/01001-000` (com hífen) | mesma resposta do A1 (a requisição ao provedor leva só dígitos) | `CepIT` |
| A3 | nenhum provedor esperando chamada | `GET /cep/123` · `0100100` · `010010000` · `abcdefgh` · `01001%2F000` | `400 CEP_INVALIDO` em todos, **zero** chamadas externas | `CepIT` |
| A4 | ViaCEP responde `{"erro": true}` para `99999999` | `GET /cep/99999999` | `404 CEP_NAO_ENCONTRADO`; a BrasilAPI **não** é consultada | `CepIT` |
| A5 | ViaCEP em timeout (falso que lança) · ViaCEP `503` · ViaCEP com corpo que não é JSON; BrasilAPI responde `{street, neighborhood, city: São Paulo, state: SP}` | `GET /cep/01001000` (um cenário por caso) | `200` com `fonte = BRASILAPI` e os campos mapeados (`street`→`logradouro`...) | `CepFallbackIT` |
| A6 | ViaCEP fora e BrasilAPI responde `404` | `GET /cep/99999999` | `404 CEP_NAO_ENCONTRADO` | `CepFallbackIT` |
| A7 | ViaCEP e BrasilAPI fora | `GET /cep/01001000` | `503 CEP_INDISPONIVEL` em RFC 9457 com `traceId`; as duas tentativas aconteceram, **nenhuma** terceira (sem retry) | `CepDegradacaoIT` |
| A8 | ViaCEP já respondeu `01001000` | `GET /cep/01001000` de novo | `200` com `fonte = CACHE` e **nenhuma** nova chamada externa | `CepCacheIT` |
| A9 | cache de encontrados, `Ticker` falso | consultar 6 dias, 23 h, 59 min e 59 s depois · consultar 7 dias depois | primeira: `CACHE`, sem chamada · segunda: nova chamada ao ViaCEP | `CepCacheIT` |
| A10 | `99999999` já deu `404` | consultar 59 min depois · consultar 1 h depois | primeira: `404` sem chamada externa · segunda: consulta o ViaCEP de novo | `CepCacheIT` |
| A11 | os dois provedores fora e depois o ViaCEP volta | `GET /cep/01001000` duas vezes | `503` e em seguida `200` com `fonte = VIACEP` (a falha nunca ficou em cache) | `CepCacheIT` |
| A12 | teto do cache em 3 entradas (`app.cep.cache-max=3`) | consultar 4 CEPs diferentes e `cleanUp` | no máximo 3 entradas guardadas; o padrão de produção é 10 000 | `CepCacheIT` |
| A13 | ViaCEP responde `200` com `cep` de outro CEP · com `uf = "São Paulo"` · com corpo `{}`; BrasilAPI responde certo | `GET /cep/01001000` | cada resposta do ViaCEP vale como **falha** e cai na BrasilAPI (`fonte = BRASILAPI`) | `CepRespostaInvalidaIT` |
| A14 | 30 consultas do IP `203.0.113.7` no último minuto | a 31ª consulta | `429` com `Retry-After`; outro IP segue com `200`; um minuto depois o IP volta a consultar | `CepLimiteIT` |
| A15 | cadastro com `cep = 01001000` e ViaCEP respondendo | `POST /auth/cadastro` | `201`; o banco guarda `cep`, `bairro = Sé`, `cidade = São Paulo`, `uf = SP`; o `Perfil` traz `endereco.completo = true` | `CadastroComCepIT` |
| A16 | cadastro com `cep` e os dois provedores fora | `POST /auth/cadastro` | `201` (R31); o banco guarda só `cep`, com bairro, cidade e uf nulos; `endereco.completo = false`; o tempo total fica abaixo de 3,5 s | `CepDegradacaoIT` |
| A17 | cadastro com `cep = 99999999` (inexistente) | `POST /auth/cadastro` | `201`, igual ao A16 | `CadastroComCepIT` |
| A18 | cadastro **sem** `cep` | `POST /auth/cadastro` | `201`; **zero** chamadas aos provedores | `CadastroComCepIT` |
| A19 | dois cadastros com o mesmo CEP | `POST /auth/cadastro` duas vezes | uma única chamada ao ViaCEP (o segundo usa o cache) e as duas contas com o endereço completo | `CadastroComCepIT` |
| A20 | consultas com sucesso, `404`, falha de provedor e cadastros com CEP, com os CEPs fictícios acima | ler o log da execução | nenhum CEP (com ou sem hífen) aparece no log (R30) | `SemDadoPessoalNoLogCepIT` |
| A21 | módulos | `./mvnw verify` | `cep` só expõe `CepApi`, `Endereco` e `CepIndisponivel`; `conta` depende de `cep` e nada de `cep` depende de `conta`; clientes dos provedores não são acessíveis de fora | `ModulithTest`, `ArquiteturaTest` |
| A22 | perfil `test` | subir o contexto | os dois provedores têm timeout de conexão de 1 s e leitura de 2 s; as URLs base apontam para `localhost` (nenhum teste sai para a internet) | `CepConfigTest` |

### 6B · consentimento e política

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A23 | cliente cadastrado sem `aceitaMarketing` | `GET /me/consentimentos` | `200` com 1 item: `MARKETING_CEP`, `aceito = false`, `versaoTexto = 1.0`, `registradoEm` = data do cadastro | `ConsentimentoIT` |
| A24 | cliente cadastrado com `aceitaMarketing = true` | `GET /me/consentimentos` | `aceito = true` | `ConsentimentoIT` |
| A25 | cliente com `aceito = false` | `PUT /me/consentimentos/marketing` `{aceito: true, versaoTexto: "1.0"}` | `200` com `aceito = true`; o banco tem **2** linhas (a antiga fica); `registradoEm` = 01/12 09:30 | `ConsentimentoIT` |
| A26 | o cliente do A25 | revogar com `{aceito: false}` e depois `GET /me/consentimentos` | `200`; **3** linhas no banco; o `GET` devolve **1** item (a mais recente, `aceito = false`) | `ConsentimentoIT` |
| A27 | consentimento já `aceito = true` na versão 1.0 | `PUT` com `aceito = true` de novo | `200` com a linha existente; **nenhuma** linha nova | `ConsentimentoIT` |
| A28 | cliente logado | `PUT` com `versaoTexto = "0.9"` · sem `aceito` · com campo extra `finalidade` · com `versaoTexto` de 21 caracteres | `400 VALIDACAO_INVALIDA` (com `errors[].field`) · `400` · `400 CAMPO_DESCONHECIDO` · `400`; nada gravado | `ConsentimentoIT` |
| A29 | dois clientes, A e B | A aceita e revoga | os consentimentos de B não mudam (nenhuma rota recebe id de usuário); sem token → `401` | `ConsentimentoIT` |
| A30 | cliente com CEP, bairro e cidade guardados | revogar o consentimento | `GET /me` continua com CEP, bairro e cidade (revogar não apaga a operação) | `ConsentimentoIT` |
| A31 | ADMIN sem consentimento no banco | `GET /me/consentimentos` | `200` com lista vazia | `ConsentimentoIT` |
| A32 | sem token | `GET /politica-privacidade` | `200` com `versao = 1.0` (igual a `Consentimentos.VERSAO_DO_TEXTO`), `textoConsentimento`, `politica` e `atualizadaEm` preenchidos; o texto cita o canal de contato configurado e o prazo de 90 dias | `PoliticaPrivacidadeIT` |

### 6B · exportar e excluir

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A33 | cliente A com consentimento (3 linhas, como no A26) e 2 reservas (uma `CONFIRMADO` e uma `CANCELADO`) | `GET /me/dados` | `200`; `geradoEm` = 01/12 09:30; `conta` com nome, e-mail, WhatsApp e endereço; `consentimentos` com as **3** linhas, da mais antiga para a mais nova; `agendamentos` com as 2 (id, início, fim, status, serviço, preço e motivo do cancelamento) | `ExportacaoDadosIT` |
| A34 | o mesmo cliente | `GET /me/dados` | o JSON **não** tem `senha`, `senhaHash`, `versaoCredencial`, `token`, `cpf`, `ip` nem `usuarioId` de outra pessoa (conferido no texto cru da resposta) | `ExportacaoDadosIT` |
| A35 | cliente B com reservas e um visitante com reserva no mesmo horário de outro dia | `GET /me/dados` como A | só as reservas de A; as de B e a do visitante não aparecem | `ExportacaoDadosIT` |
| A36 | cliente A | `GET /me/dados` | a resposta traz `Cache-Control: no-store`; o banco ganha 1 linha em `solicitacao_titular` (`EXPORTACAO`, `concluida_em` = 01/12 09:30) e **nenhuma** em `auditoria_admin` | `ExportacaoDadosIT` |
| A37 | sem token · token vencido | `GET /me/dados` | `401` nos dois | `ExportacaoDadosIT` |
| A38 | cliente com 5 exportações na última hora | a 6ª · a 6ª uma hora depois da primeira | `429` com `Retry-After` · `200`; outra conta não é afetada | `ExportacaoLimiteIT` |
| A39 | cliente A sem reservas, com refresh token e token de acesso válidos | `DELETE /me` | `204`; depois: login com e-mail e senha antigos → `401`; `GET /me` com o token antigo → `401`; `POST /auth/refresh` com o refresh antigo → `401` | `ExclusaoContaIT` |
| A40 | o cliente do A39 | `DELETE /me`, ler o banco | `nome = 'Conta excluída'`, `email = 'excluido-<id>@anonimo.invalid'`; `whatsapp`, `senha_hash`, `cep`, `bairro`, `cidade`, `uf` nulos; `ativo = false`; `anonimizado_em` = 01/12 09:30; **nenhum** `refresh_token` da conta | `ExclusaoContaIT` |
| A41 | o cliente do A39, com 3 linhas de consentimento | `DELETE /me` | o histórico fica e ganha uma linha final `aceito = false`; `solicitacao_titular` `EXCLUSAO` com `concluida_em` = 01/12 09:30 | `ExclusaoContaIT` |
| A42 | o e-mail `cliente.a@exemplo.test` da conta excluída | `POST /auth/cadastro` com o mesmo e-mail | `201` (o e-mail ficou livre, nenhum `EMAIL_JA_CADASTRADO`) | `ExclusaoContaIT` |
| A43 | BARBEIRO · ADMIN | `DELETE /me` | `403 ACESSO_NEGADO` e a conta continua ativa e intacta; sem token → `401`; o mesmo token usado de novo depois de um `204` → `401` | `ExclusaoContaIT` |
| A44 | cliente com 1 reserva futura `AGUARDANDO_PAGAMENTO` (02/12 10:00, prazo aberto) e cobrança `PENDENTE` | `DELETE /me` | `204`; reserva `CANCELADO` com `motivo_cancelamento = 'Conta excluída pelo titular'` e `cancelado_por` = o usuário; cobrança `CANCELADA`; o horário de 02/12 10:00 volta em `GET /disponibilidade` | `ExclusaoReservasIT` |
| A45 | cliente com 1 reserva futura `CONFIRMADO` (cobrança `PAGA`) e outra `AGUARDANDO_PAGAMENTO` | `DELETE /me` | `409 EXCLUSAO_BLOQUEADA_POR_RESERVA`; **nada** muda: conta ativa e com dados, as duas reservas e a cobrança como estavam, sem linha em `solicitacao_titular` | `ExclusaoReservasIT` |
| A46 | cliente com reservas passadas `CONCLUIDO` (30/11 10:00) e `NAO_COMPARECEU` (29/11 10:00) | `DELETE /me` e depois `GET /admin/agenda` do dia 30/11 | `204`; as reservas continuam com o mesmo status e preço; na agenda o cliente aparece como "Conta excluída" **sem** WhatsApp | `ExclusaoReservasIT` |
| A47 | falha injetada (trigger do PostgreSQL recusa o cancelamento da reserva futura, padrão da D75) | `DELETE /me` | resposta de erro (não `204`); a conta continua ativa, com todos os dados e o login funcionando; sem `solicitacao_titular` | `ExclusaoAtomicaIT` |
| A48 | a cobrança e a reserva de uma conta excluída | consultar o banco | `cobranca` continua com valor, status e datas (sem dado pessoal); nenhum campo de `reserva`, `cobranca` ou `solicitacao_titular` guarda nome, e-mail ou WhatsApp do titular | `ExclusaoContaIT` |

### 6B · retenção

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A49 | visitante com reserva cujo `fim` é 02/09/2026 09:30 (exatamente 90 dias) | rodar o job de retenção em 01/12 09:30 | `nome = 'Visitante anonimizado'`, `email = 'anonimizado-<reservaId>@anonimo.invalid'`, `whatsapp = '00000000000'`, `anonimizado_em` = 01/12 09:30 | `RetencaoGuestIT` |
| A50 | outra reserva de visitante com `fim` em 02/09/2026 09:31 (1 minuto antes dos 90 dias) | rodar o job | dados **intactos**, `anonimizado_em` nulo | `RetencaoGuestIT` |
| A51 | a reserva do A49 | rodar o job uma segunda vez | 0 linhas afetadas; `anonimizado_em` continua o do primeiro job | `RetencaoGuestIT` |
| A52 | o link do visitante do A49 (id da reserva + token antigo) | `GET /agendamentos/guest/{id}` com o token | `404` (o token deixou de valer) | `RetencaoGuestIT` |
| A53 | a reserva e a cobrança do A49 | depois do job | status, valores, horários e `cobranca` idênticos aos de antes; na agenda do painel o contato aparece como "Visitante anonimizado" | `RetencaoGuestIT` |
| A54 | `app.privacidade.retencao-guest-dias=30`, reserva com `fim` há 31 dias e outra há 29 dias | rodar o job | só a de 31 dias é anonimizada | `RetencaoGuestIT` |
| A55 | 3 linhas de `auditoria_admin`: ocorrida em 01/12/2025 09:30 (365 dias), em 01/12/2025 09:31 e em 30/11/2026 | rodar o job | só a primeira fica com `ip = NULL`; as três mantêm `ator_id`, `acao`, `recurso` e `detalhe`; `GET /admin/auditoria` segue sem `ip` | `RetencaoAuditoriaIT` |
| A56 | `evento_webhook` processado há 366 dias, outro processado há 10 dias e outro **sem** `processado_em` há 400 dias | rodar o job | só o primeiro é apagado | `RetencaoWebhookIT` |
| A57 | 1200 visitantes vencidos | rodar o job | todos anonimizados, em lotes de no máximo 500 linhas por transação; a contagem logada é 1200 e o log não tem nome, e-mail nem WhatsApp | `RetencaoJobIT` |
| A58 | dois disparos simultâneos do job com a mesma trava (ShedLock) | executar os dois | cada linha é anonimizada **uma** vez, sem erro; com `app.jobs.habilitados=false` o job não roda sozinho | `RetencaoJobIT` |

### 6C · alcance por região

Relógio da 6C: o mesmo das Fatias 4 e 5 (terça, 17/11/2026 08:00 -03:00). Período padrão do painel: últimos 30 dias.

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A62 | ViaCEP devolve Sé, São Paulo, SP | visitante agenda com `cep = 01001-000` | `201`; `reserva.regiao_*` = Sé, São Paulo, SP e `reserva_guest.cep = 01001000` | `AlcanceDaReservaIT` |
| A63 | provedores fora (`503`) ou CEP `99999999` | visitante agenda com esse `cep` | `201`; reserva sem região e `reserva_guest.cep` nulo | `AlcanceDaReservaIT` |
| A64 | — | visitante agenda com `cep = 123` | `400 CEP_INVALIDO`, nenhuma reserva criada, zero chamadas externas | `AlcanceDaReservaIT` |
| A65 | visitante agenda sem `cep` | `POST /agendamentos/guest` | `201` como antes (testes da Fatia 4 continuam verdes) | `AlcanceDaReservaIT` |
| A66 | cliente com Centro, Osasco, SP no cadastro | reserva | a reserva copia Centro, Osasco, SP; mudar o endereço do usuário depois não muda a reserva | `AlcanceDaReservaIT` |
| A67 | 6 atendimentos `CONCLUIDO` em Centro/Osasco, 2 em Sé/São Paulo, 1 `EXPIRADO` em Centro, 1 `CONFIRMADO` sem região | `GET /admin/alcance` como ADMIN | `total = 9` (`EXPIRADO` fora), `semRegiao = 1`; Osasco com Centro 6; os 2 de São Paulo saem em "Outras cidades"; percentuais com 1 casa decimal | `AlcancePainelIT` |
| A68 | 5 atendimentos em Osasco: 4 no Centro e 1 em Presidente Altino | painel | Osasco aparece (5); dentro dela só "Outros bairros" com 5 | `AlcancePainelIT` |
| A69 | atendimentos de dois barbeiros | `BARBEIRO` A chama `GET /admin/alcance?barbeiroId=<B>` | vê só os próprios | `AlcancePainelIT` |
| A70 | ADMIN | `?barbeiroId=<inexistente>` · `?de=2026-11-10&ate=2026-11-01` · período de 400 dias · sem token · cliente | `404` · `400` · `400` · `401` · `403` | `AlcancePainelIT` |
| A71 | o cenário do A67 | `GET /alcance` sem token | `200` com `regioes = [{ Osasco, SP, [Centro] }]`, `totalCidades = 1`, `totalBairros = 1`, **sem** número de atendimentos no JSON | `AlcancePublicoIT` |
| A72 | atendimentos de 13 meses atrás em outro bairro (5 ou mais) | `GET /alcance` | não aparecem (só últimos 12 meses) | `AlcancePublicoIT` |
| A73 | duas chamadas com um atendimento novo no meio; depois 31 chamadas no mesmo minuto do mesmo IP | `GET /alcance` | a segunda vem do cache (1 hora, `Ticker` de teste); a 31ª é `429` com `Retry-After` | `AlcancePublicoIT` |
| A74 | visitante com `cep` vencido há 90 dias | job de retenção | `reserva_guest.cep = NULL`; `reserva.regiao_*` continua igual | `RetencaoJobIT` |
| A75 | roteiro da 6C | ler o log | nenhum CEP em texto claro (o log diz só "região resolvida: sim/não") | `SemDadoPessoalNoLogIT` |

### Transversais

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A59 | roteiro com dados fictícios: cadastro com CEP e marketing, consulta de CEP, `PUT` de consentimento, exportação, exclusão e job de retenção | ler o log | nenhum e-mail, WhatsApp, CEP, CPF nem IP em texto claro (R30; fecha a S16) | `SemDadoPessoalNoLogIT` |
| A60 | módulos | `./mvnw verify` | `privacidade` não depende de `conta`, `agenda`, `pagamento` nem `admin` (só `comum`); `conta` e `agenda` implementam as portas e escutam `ExclusaoSolicitada`; só `privacidade` toca `consentimento` e `solicitacao_titular` | `ModulithTest`, `ArquiteturaTest` |
| A61 | as rotas novas e as que mudaram | `./mvnw verify` | cada uma tem teste de `401` (e `403` quando há papel) nas classes acima; `redocly lint` do contrato 1.5.0 sem erro | `ContratoPrivacidadeTest` |
| A61a | cliente com reserva `CANCELADO` e cobrança `DEVOLUCAO_PENDENTE`, ou com pagamento tardio sem devolução (`PAGA_APOS_EXPIRACAO`) (decisão do Kevin sobre a D100) | `DELETE /me` | `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` e nada muda (mesmo motivo da D-1: sem o WhatsApp não há a quem devolver); depois de a cobrança virar `DEVOLVIDA`, a exclusão dá `204`. O pagamento confere pela API pública da agenda e pela própria tabela de cobrança | `ExclusaoReservasIT` |
| A61b | `app.jobs.habilitados=true` e um visitante vencido já no banco | subir a API | o `RetencaoDeDados` roda sozinho na subida (como o `ExpiracaoJob`), pela trava do ShedLock `retencao-de-dados`, e anonimiza o visitante | `RetencaoNaSubidaIT` |
| A61c | admin com a senha inicial ainda em uso | `GET /me/dados`, `GET /me/consentimentos`, `PUT /me/consentimentos/marketing` | `403 TROCA_DE_SENHA_OBRIGATORIA`; `GET /me` e `PUT /me/senha` seguem acessíveis (é por elas que ele troca a senha) | `ContratoPrivacidadeTest` |

## 5. Entregas

| Módulo | 6A | 6B |
|---|---|---|
| `cep` | `CepApi`, `Endereco`, `CepIndisponivel`; internos: `CepController`, `ViaCepCliente`, `BrasilApiCliente`, `CepConfig` (`RestClient`, timeouts, `Ticker`), cache Caffeine de encontrados e de inexistentes; `app.cep.*` | — |
| `conta` | `CadastrarCliente` chama `CepApi` fora da transação e grava bairro, cidade e uf; `Usuario` ganha os campos do endereço resolvido | `FonteDeDadosDoTitular` (conta), `AnonimizaContaDaExclusao` (listener de `ExclusaoSolicitada`: dados, `versao_credencial`, refresh tokens) |
| `agenda` | — | `FonteDeDadosDoTitular` (agendamentos), `CancelaReservasDaExclusao` (futuras não pagas; bloqueio das pagas), `LimpezaPorRetencao` de `reserva_guest` |
| `privacidade` | — | `Consentimentos` (consultar, registrar, histórico), `ConsentimentoController`, `PoliticaPrivacidadeController` + textos versionados, `ExportarDados` + `ExportacaoController`, `ExcluirConta` + `ExclusaoController`, `ExclusaoSolicitada`, portas `FonteDeDadosDoTitular` e `LimpezaPorRetencao`, `RetencaoDeDados` (job com ShedLock), `app.privacidade.*` |
| `admin` | — | `LimpezaPorRetencao` do IP da auditoria |
| `pagamento` | — | `LimpezaPorRetencao` de `evento_webhook` |
| `plataforma` | limite `app.limites.cep-*` (H21) | limite `app.limites.exportacao-*`; `403` para papel de equipe em `DELETE /me` no `SegurancaConfig` |
| 6C | `agenda`: `RegiaoDaReserva`, `ConsultaAlcance` (agregado com k ≥ 5), `AlcancePublicoController` com cache e a API `AlcanceDaAgenda`; `admin`: `AlcanceController`; `privacidade`: limpeza do `reserva_guest.cep` na retenção; `V6__alcance.sql`; contrato item 6; guia de testes com saída real | |
| dependências | `spring-boot-starter-cache` e `caffeine` (versões do BOM do Spring Boot) | — |
| banco | — | nenhuma migração obrigatória (ver seção 2) |
| docs | contrato 1.5.0 (itens 1 e 3 da seção 2 para o CEP), `docs/contexto.md` (H21 e a linha de direitos do titular), guia de testes (B3 e B4 do CEP; **C13** com saída real, e a rotina do visitante depois dos 90 dias), README, decisões novas em `decisoes.md` (D87 em diante), atualizar o ADR 0008 e o ADR 0009 de "proposta" para "aceita" com os números finais | contrato 1.5.0 completo (itens 1 a 5) e o mapa do `docs/arquitetura/README.md` (listeners síncronos) |

## 6. Riscos e cuidados

- **Terceiro fora do nosso controle:** ViaCEP e BrasilAPI não têm SLA nem garantem os termos para sempre. Mitigação: timeout curto, fallback, cache e degradação; se algum passar a exigir chave, o ADR 0008 manda revisitar (gatilho já registrado).
- **Cache em memória some quando o Render hiberna** (aceito no ADR 0008: é só otimização). O teto de 10 mil entradas é ~ poucos MB, cabe nos 512 MB.
- **`DELETE /me` é irreversível e não pede a senha** (D-3). Quem roubar um token de acesso (15 min) e o refresh em cookie consegue apagar a conta. Mitigação no beta: `HttpOnly`, token curto, `409` se houver reserva paga. Re-autenticar fica como melhoria.
- **Exclusão bloqueada por reserva paga:** o cliente que quer sair fica dependente do Eduardo para cancelar. É o preço de não perder o contato necessário para a devolução (D-1). O texto da política precisa dizer isso com clareza.
- **Prazos e bases legais são hipóteses (H13)** e esta spec **não é parecer jurídico**. O que o código garante é o mecanismo (propriedades, jobs, testes); o número final precisa de validação antes de uso real, e o texto de `politica` (E13) é um rascunho técnico até alguém revisar.
- **Job de retenção silencioso:** se parar de rodar ninguém nota. Mitigação: o job loga a contagem diária e o guia de testes ensina a conferir; alerta fica para o deploy (Fatia 8).
- **Anonimização por `UPDATE` não limpa backup** do Aiven: o dado antigo pode viver nos backups do provedor até expirarem. Registrar na política.
- **Hash do token do guest:** ao anonimizar, o `token_hash` vira um valor aleatório; um bug aqui deixaria o link do visitante vivo. O A52 prova.
- **Ordem dos listeners síncronos:** a exclusão depende de `agenda` verificar o bloqueio **antes** de `conta` anonimizar; se um falhar a transação inteira desfaz (A47), mas a ordem precisa ser explícita (`@Order`).
- **Dado pessoal em exceção:** mensagens de erro do Jackson, do JDBC e do `RestClient` podem trazer o valor recebido. O `TratadorGlobalDeErros` já devolve só o `code`; o A59 pega a regressão.

## 7. Dúvidas para o Kevin (com a proposta de cada uma)

| # | Dúvida | Proposta |
|---|---|---|
| D-1 | Cliente com **reserva futura paga** pede para excluir a conta. O ADR 0009 diz "cancela as reservas futuras", mas cancelar paga gera devolução e, sem WhatsApp, o Eduardo não tem a quem devolver | **bloquear** com `409 EXCLUSAO_BLOQUEADA_POR_RESERVA` até a reserva ser cancelada pela barbearia ou concluída; só as não pagas são canceladas na exclusão (E17). Alternativa: cancelar e manter o WhatsApp só na linha da devolução pendente (exige migração e mais regra) |
| D-2 | **Retenção da auditoria** (D77): apagar a linha inteira ou só o IP? | **zerar só o IP** depois de 1 ano e manter a linha (quem fez o quê não tem dado pessoal); apagar linhas antigas fica para um prazo maior se a tabela crescer |
| D-3 | `DELETE /me` deve exigir a **senha atual** no corpo? | **não exigir no beta** (contrato e a tela C13 ficam simples; risco na seção 6). Entra como melhoria se for para produção real |
| D-4 | Quem escreve o **texto da política e da caixa de marketing** (versão 1.0) e qual o **canal de contato** do titular | eu escrevo um rascunho técnico em `backend/src/main/resources/privacidade/politica-1.0.md` (e o texto do consentimento), marcado "não é parecer jurídico"; você revisa. O contato vem da variável `APP_PRIVACIDADE_CONTATO` (por exemplo o WhatsApp da barbearia), nunca escrito no repositório |
| D-5 | Dividir em **6A (CEP, A1 a A22)** e **6B (privacidade, A23 a A61)**? São 61 critérios, entre a Fatia 4 (45) e a 5 (71) | **sim**, a 6A é pequena e já destrava o cadastro com endereço; a 6B só começa com a 6A na `main` |
| D-6 | Limites novos **H21**: 30 consultas de CEP por minuto por IP e 5 exportações por hora por conta | **aceitar** esses valores (configuráveis em `app.limites.*`) |
| D-8 | ✅ Alcance agregado sem pedir consentimento (legítimo interesse, k ≥ 5) | **aceita** pelo Kevin (E33) |
| D-9 | ✅ Mostrar o alcance também para quem vai agendar | **aceita** pelo Kevin: vitrine pública só com nomes de regiões (E35); tela no front-end e no mobile na Fatia 7 |
| D-7 | **Visitante** quer apagar o contato antes dos 90 dias | **sem rota no beta**: ele pede ao Eduardo (canal da política) e o Eduardo anonimiza por fora; a retenção de 90 dias cobre o resto. Rota para visitante fica pós-beta |

## 8. Pronto quando

- [ ] **6A:** A1 a A22 com teste verde no `./mvnw verify` (`scripts/conferir-spec.sh 6 --exigir` cobre A1 a A22 antes de começar a 6B)
- [ ] **6B:** A23 a A61 com teste verde no `./mvnw verify` e `scripts/conferir-spec.sh 6 --exigir` com 61 de 61
- [ ] **6C:** A62 a A75 com teste verde e `scripts/conferir-spec.sh 6 --exigir` com 75 de 75
- [ ] A9 da Fatia 4 e os testes das Fatias 3 e 5 ainda verdes depois de `CadastrarCliente` e da agenda mudarem
- [ ] `redocly lint` com 0 erros no contrato 1.5.0
- [ ] nenhum teste faz chamada de rede real (A22) e nenhuma conta ou serviço pago foi criado (D59)
- [ ] rotina rodada à mão no perfil `dev`, com saída real no guia de testes: CEP com os provedores ligados e desligados (B3 e B4), cadastro com e sem CEP, **C13** (`GET /me/dados`, `DELETE /me`, login falhando depois) e `grep` do log com 0 ocorrências de e-mail, WhatsApp, CEP e IP
- [ ] decisões D87 em diante registradas em `decisoes.md`; ADRs 0008 e 0009 marcados como aceitos com os números finais
- [ ] [checklist da PR](checklist-pr.md) completo
