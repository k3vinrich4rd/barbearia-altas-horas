# Instruções para o GitHub Copilot · Barbearia Altas Horas

Agenda online da Barbearia Altas Horas (Osasco), feita pelo Kevin Richard para o pai, o barbeiro Eduardo Cândido. Projeto individual. Responda e escreva comentários, commits e PRs em **português do Brasil**.

## Regra de ouro: Spec-Driven Development
- Nenhum código de produção sem spec aprovada em `docs/sdd/fatia-N-*.md`. Se a tarefa não tem spec ou critério (`A#`) correspondente, **pare e peça a spec** em vez de supor.
- Cada critério `A#` da spec vira um teste com o número no `@DisplayName` (`"A7: ..."`). `sh scripts/conferir-spec.sh N --exigir` precisa passar.
- O contrato `docs/contrato/openapi.yaml` é a fonte da verdade das rotas: não invente rota, campo ou código de erro.

## Onde consultar antes de escrever
- Regras de negócio `R#`: `docs/contexto.md` · decisões `D#`: `docs/planejamento/decisoes.md` · ADRs: `docs/adr/`
- Schema: `backend/src/main/resources/db/migration/` (migração mergeada nunca é editada; mudança vira `V<n+1>`)
- Busca por identificador: `sh scripts/buscar-contexto.sh R15`

## Padrões do backend (Java 25, Spring Boot 4.1, PostgreSQL 18)
- Pacote `io.github.k3vinrich4rd.altashoras`; 8 módulos do Spring Modulith. Um módulo só usa a API pública do outro.
- Hexagonal seletivo (ADR 0011): regra difícil em classe pura (sem Spring, JPA, JDBC); sistema externo atrás de uma porta (interface) com adaptador. CRUD simples fica em controller + repositório.
- Tempo só pelo `Clock` injetado (nunca `Instant.now()`); dinheiro em `long` de centavos (nunca `double`).
- Erros: lance `ExcecaoDeNegocio` com um `CodigoErro`; a resposta sai em RFC 9457 pelo `TratadorGlobalDeErros`.
- Regras configuráveis vêm de `configuracao_agenda`, nunca fixas no código.
- Testes de integração com PostgreSQL real (`PostgresTestcontainers`), nunca H2. Cada teste cria os próprios dados.
- Nenhum CPF, telefone, CEP ou e-mail em log ou em mensagem de erro. Nenhum segredo no repositório.
- Segurança: negar por padrão. Rota pública nova precisa entrar na lista do `SegurancaConfig` e na spec; o id do usuário vem sempre do token (`sub`), nunca de parâmetro.

## Regras do produto que não podem ser quebradas
- Todo agendamento paga 100% antes (Pix ou cartão); só o webhook do gateway confirma.
- O cliente **não** cancela nem remarca: não crie rota, botão ou campo para isso.
- A API nunca recebe número de cartão.

## Ao revisar uma PR
Confira, nesta ordem: o diff cumpre os critérios da spec citada; há teste para cada critério e ele prova o "Então"; o contrato bate com os DTOs (nenhum campo a mais); os padrões acima; N+1 e consulta sem índice; dado pessoal em log. Aponte arquivo e linha.

## Git
Branch a partir da `develop`; commits semânticos em português; PR para a `develop`. Nunca para a `main`.
