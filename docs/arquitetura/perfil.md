# 📐 Perfil arquitetural

Preenchido a partir do prompt aprovado (13/11/2026) e da arquitetura do Alicerce. O que não veio da ideia está marcado **hipótese**.

| Força | Resposta |
|---|---|
| Domínio | 6 subdomínios de negócio (conta, cep, agenda, pagamento, privacidade, admin) + 2 transversais (comum, plataforma). Regra rica só na agenda (disponibilidade, concorrência, estados) e no pagamento (idempotência, webhook) |
| Tamanho e equipe | 1 dev (Kevin) + IAs, 1 repositório (monorepo), 1 time |
| Volume | 1 barbearia, poucos agendamentos por dia (hipótese: < 1 requisição por segundo em média; pico no fim de semana) |
| Consistência | forte (ACID) na reserva e no pagamento; eventual entre módulos (confirmação após o webhook, anonimização) |
| Latência | p95 < 300 ms na disponibilidade e < 400 ms na reserva com a API acordada (hipótese, RNF1/RNF2); cold start de ~1 min aceito |
| Integrações | ViaCEP e BrasilAPI (síncronas, podem falhar); gateway Pix em sandbox (síncrono para criar, assíncrono por webhook) |
| Dados | relacional (PostgreSQL), com intervalos de tempo (`tstzrange`) |
| Segurança e LGPD | e-mail, WhatsApp, CEP e CPF (só no pagamento); 3 papéis; pagamento |
| Custo e deploy | custo zero: Render free (512 MB, hiberna), Aiven free (20 conexões), Vercel Hobby |
| Evolução | login social (flag), alcance geográfico, lembrete por WhatsApp; reaproveitar como template |
| Atributos prioritários | **1. segurança · 2. testabilidade · 3. manutenibilidade** (custo zero é restrição, não atributo) |

**Conclusão:** mais de 2 subdomínios e consistência difícil na agenda → disputa completa de opções (skill arquitetura sob medida, seção 3). A disputa está no [ADR 0001](../adr/0001-monolito-modular-com-spring-modulith.md).
