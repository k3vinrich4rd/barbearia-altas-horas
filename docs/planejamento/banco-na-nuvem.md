# 🗄️ Banco de dados na nuvem: Aiven e as alternativas gratuitas

| | |
|---|---|
| 🎯 **Para que serve** | escolher onde o PostgreSQL roda no deploy gratuito (Fase 1, D38) e ter um plano B pronto se o plano grátis mudar |
| ⏰ **Quando usar** | na fatia de deploy (Fase 1) e sempre que um provedor anunciar mudança de plano |
| 📅 **Pesquisado em** | 17/11/2026, nas páginas oficiais (links no fim). **Limites mudam**: conferir de novo no dia do deploy |
| 🔗 **Decisão** | D66 em [decisoes.md](decisoes.md) |

## 1. O que mudou no Aiven

- O Aiven **aposentou o plano gratuito do MySQL**. Desde 06/11/2026 não dá para criar MySQL grátis, e em **21/12/2026** os MySQL gratuitos que restarem serão **desligados, com os dados e os backups apagados**.
- O **PostgreSQL gratuito continua**: a própria página de migração do Aiven indica ir para ele, e a documentação do plano não traz aviso de aposentadoria.
- **Para este projeto:** a Barbearia usa **PostgreSQL 18** (constraint `EXCLUDE USING gist`, ADR 0003). Nada muda hoje, mas o anúncio mostra que plano grátis pode acabar com pouco aviso. Por isso fica registrado um plano B.

## 2. O que a API precisa do banco

| Requisito | Por quê |
|---|---|
| PostgreSQL de verdade (não "compatível") com `btree_gist`, `tstzrange` e `EXCLUDE` | é o que impede duas reservas no mesmo horário (ADR 0003) |
| conexão JDBC comum (TCP, TLS) | Spring Boot + Hikari + Flyway |
| pelo menos ~10 conexões | o Hikari usa 5 (D12), mais Flyway e o ShedLock |
| alguns MB de dados | uma barbearia com 1 barbeiro gera poucas linhas por dia; 500 MB já sobram |
| não apagar os dados sozinho | backups ou, no mínimo, `pg_dump` agendado |

## 3. Comparação dos planos gratuitos

| Provedor | Armazenamento | Ficar parado | Backup no grátis | Conexões | Encaixe |
|---|---|---|---|---|---|
| **Aiven PostgreSQL** (atual, D12) | 1 GB · 1 CPU · 1 GB RAM | pode ser desligado sem uso (com aviso); religa no painel | ✅ incluído | `max_connections` 20, sem pooler | ✅ **fica**: sempre ligado, sem cold start |
| **Neon** (plano B) | 1 GB por projeto (20 GB no total) · 100 CU-hora/mês por projeto | dorme após **5 min** sem uso e acorda na 1ª conexão | histórico de 6 h e 1 snapshot manual | pooler com até 10 mil conexões | ✅ bom: PostgreSQL padrão, sem cartão, sem prazo |
| **Supabase** | 500 MB · 2 projetos ativos | **pausa após 1 semana** sem uso | ❌ sem backup automático | pooler incluso | ⚠️ serve, mas exige `pg_dump` próprio e acesso semanal |
| **Prisma Postgres** | ~2 GB · cobrado por operações (~200 mil/mês) | sem prazo | a confirmar | foco no Prisma ORM; JDBC direto a confirmar | ⚠️ pensado para Node/Prisma; conferir antes |
| **Render PostgreSQL** | 1 GB | **expira em 30 dias** | ❌ | — | ❌ só para teste curto |
| **Koyeb** | 1 GB | ~5 horas de computação por mês | — | — | ❌ não aguenta uma API no ar |
| **VPS própria** (Oracle Always Free, ou VPS paga da Fase 2) | o disco da VM | sempre ligada | quem faz é você (`pg_dump`) | sem limite | ✅ controle total, mas vira trabalho de operação |

## 4. Recomendação

1. **Fase 1 (beta):** continuar no **Aiven PostgreSQL free**. É o único da lista sempre ligado e com backup no grátis, e o pool de 5 conexões (D12) já foi dimensionado para ele.
2. **Plano B: Neon.** Troca sem mudar código: só `DB_URL`, `DB_USUARIO` e `DB_SENHA`. Cuidados:
   - usar a **conexão direta** (sem o sufixo `-pooler`) para o Flyway e o ShedLock, que precisam de sessão; o pooler do Neon funciona em modo transação;
   - o banco dorme após 5 min: a primeira requisição depois disso leva alguns segundos a mais (o Render free já dorme junto, então o efeito quase não aparece);
   - conferir que `CREATE EXTENSION btree_gist` funciona no Neon antes de migrar (a V1 cria a extensão; sem ela a constraint anti double booking não existe).
3. **Proteção contra "o plano acabou":** um `pg_dump` semanal guardado fora do provedor (job do GitHub Actions com o dump criptografado como artefato, ou à mão antes de cada demonstração). Restaurar é `psql < dump.sql` em qualquer provedor da tabela.
4. **Produção de verdade (saída do beta):** sair do plano grátis. O caminho recomendado é a VPS com Docker da Fase 2 ou o plano pago de entrada de um destes provedores.

## 5. Como migrar, se precisar (≈ 15 minutos)

```bash
# 1. exportar do provedor atual (formato custom, com dados e schema)
pg_dump "$DB_URL_ANTIGA" -Fc -f altashoras.dump

# 2. criar o banco no novo provedor e restaurar
pg_restore -d "$DB_URL_NOVA" --no-owner --no-privileges altashoras.dump

# 3. conferir: a última migração do Flyway e a constraint anti double booking
psql "$DB_URL_NOVA" -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
psql "$DB_URL_NOVA" -c "SELECT conname FROM pg_constraint WHERE conname = 'reserva_sem_sobreposicao';"
```

Depois, trocar `DB_URL`, `DB_USUARIO` e `DB_SENHA` no Render e reiniciar a API. O `/actuator/health` deve responder `UP`.

## Fontes (consultadas em 17/11/2026)

- Aiven: [aposentadoria do MySQL free](https://aiven.io/changelog/507f8b1b-dd8f-4841-ac4a-940ac492b688) · [opções de migração](https://aiven.io/docs/products/mysql/howto/mysql-free-tier-migration-options) · [plano free do PostgreSQL](https://aiven.io/docs/products/postgresql/concepts/pg-free-tier) · [regras do plano free](https://aiven.io/docs/platform/concepts/free-plan)
- [Neon: preços e limites](https://neon.com/pricing) · [Supabase: preços](https://supabase.com/pricing)
- Comparações de terceiros (só para a triagem; números conferidos nas páginas oficiais quando possível): [Prisma](https://www.prisma.io/blog/serverless-postgres) · [Swyftstack](https://swyftstack.com/blog/free-postgresql-hosting) · [selfhost.dev](https://www.selfhost.dev/blog/best-free-postgresql-hosting/)
