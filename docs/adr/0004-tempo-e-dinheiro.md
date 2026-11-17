# 0004. Tempo em `timestamptz`/UTC com `Clock` injetado; dinheiro em centavos

- Status: **aceito** (revisão do Alicerce, 13/11/2026)
- Data: 2026-11-13 (proposta e aceite)
- Decisores: Alicerce (dono), Quarteto Dev (texto)


> **Atualização 16/11/2026 (D31 a D35):** a barbearia definiu pagamento **100% antecipado** por Pix ou cartão para todos, sem cancelamento ou remarcação pelo cliente. Onde este registro fala em "sinal" ou remarcação, vale a regra nova; o ADR fica como histórico da decisão.

## Contexto
A agenda mistura instantes (reserva às 10h de 01/12) com regras de calendário local (jornada de terça a sábado, 9h às 19h, em `America/Sao_Paulo`). O sinal é dinheiro e não pode ter arredondamento de ponto flutuante.

## Decisão
**Tempo**
- Banco em `timestamptz` (UTC). Java usa `Instant` para instantes e `LocalDate`/`LocalTime` + `ZoneId` só para regras de calendário (jornada, "dia" da disponibilidade).
- O fuso da barbearia vem de `configuracao_agenda.fuso` (`America/Sao_Paulo`); a API recebe e devolve ISO-8601 **com offset** (ex.: `2026-12-01T10:00:00-03:00`).
- `Clock` injetado em todo cálculo de tempo; `Instant.now()` sem argumento é proibido fora de `comum` (regra ArchUnit 12). Testes usam `Clock.fixed`/mutável para expiração e janelas.
- `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.

**Dinheiro**
- Centavos em `long` (`bigint` no banco), encapsulado no value object `Dinheiro` (`comum`). Na API: inteiro `precoCentavos` + texto `precoFormatado` só para exibição.
- **Sinal congelado na reserva (R8):** `sinal = ceil(preco × percentual / 100)` em centavos, gravado em `reserva.sinal_centavos` junto do `preco_centavos`. Mudar o preço do serviço depois não muda reservas existentes.

## Consequências
- ✅ Sem bug de horário de verão (o Brasil não tem hoje, mas a regra não depende disso) e sem arredondamento de `double`.
- ⚠️ Disponibilidade precisa converter a jornada local em instantes do dia pedido (teste com datas de virada de mês e fuso).
- ⚠️ Divergência com o padrão do time (`decimal com 2 casas` na skill de contrato): registrada em `decisoes.md`; centavos em inteiro evitam ambiguidade no JSON.

## Gatilhos para revisitar
- Barbearia com unidade em outro fuso → fuso por unidade.
