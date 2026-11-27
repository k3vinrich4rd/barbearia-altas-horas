# 📜 Decisões de arquitetura (ADRs)

Formato MADR (adr.github.io/madr). Um ADR aceito nunca é reescrito: um ADR novo o substitui com `Substitui: 000N`.

| # | Decisão | Dono | Status |
|---|---|---|---|
| [0001](0001-monolito-modular-com-spring-modulith.md) | Monólito modular com Spring Modulith | revisão de arquitetura | aceito em 13/11/2026 (revisão de arquitetura) |
| [0002](0002-comunicacao-entre-modulos.md) | Comunicação entre módulos: API pública síncrona + eventos com registro de publicação | revisão de arquitetura | aceito em 13/11/2026 (revisão de arquitetura) |
| [0003](0003-anti-double-booking-no-banco.md) | Anti double booking no banco com `EXCLUDE USING gist` | revisão de arquitetura | aceito em 13/11/2026 (revisão de arquitetura) |
| [0004](0004-tempo-e-dinheiro.md) | Tempo em `timestamptz`/UTC com `Clock` injetado; dinheiro em centavos | revisão de arquitetura | aceito em 13/11/2026 (revisão de arquitetura) |
| [0005](0005-erros-rfc-9457.md) | Erros no padrão RFC 9457 com `code` estável | revisão de arquitetura | aceito em 13/11/2026 (revisão de arquitetura) |
| [0006](0006-sessao-jwt-refresh-cookie-bff.md) | Sessão: JWT curto + refresh rotativo em cookie `HttpOnly` via BFF | revisão de arquitetura | aceito em 13/11/2026 (revisão de arquitetura) |
| [0007](0007-gateway-de-pagamento-sandbox.md) | Gateway de pagamento: Asaas sandbox | Kevin Richard | proposta |
| [0008](0008-cep-com-cache-e-degradacao.md) | CEP: ViaCEP + BrasilAPI, cache Caffeine e degradação graciosa | Kevin Richard | proposta |
| [0009](0009-lgpd-marketing-cpf-e-retencao.md) | LGPD: marketing por consentimento, CPF não persistido, retenção do guest | Kevin Richard | proposta |
| [0010](0010-versoes-da-plataforma.md) | Versões: Java 25 LTS e Spring Boot 4.1.2 | Kevin Richard | proposta |
| [0011](0011-hexagonal-seletivo-no-monolito-modular.md) | Hexagonal seletivo dentro do monólito modular | Kevin | aceito em 16/11/2026 |
| [0013](0013-varios-barbeiros-dono-e-convite.md) | Vários barbeiros: Dono/Admin, isolamento por `barbeiroId` e convite de uso único | revisão de arquitetura | aceito |
| [0014](0014-alcance-k-anonimato-e-consentimento-do-cep.md) | Alcance: subir de nível, k = 5 e consentimento para a região do CEP | revisão de arquitetura | aceito |
| [0015](0015-ferias-folgas-e-agenda-sem-limite.md) | Férias e folgas no painel (tipo, edição, conflitos sem cancelar sozinho), expediente público na disponibilidade e agenda sem limite de data | revisão de arquitetura | proposto |
