# 0002. Comunicação entre módulos: API pública síncrona + eventos com registro de publicação

- Status: **aceito** (revisão do Alicerce, 13/11/2026)
- Data: 2026-11-13 (proposta e aceite)
- Decisores: Alicerce (dono), Quarteto Dev (texto)

## Contexto
Os módulos precisam conversar sem criar ciclo (ADR 0001). Exemplo: o pagamento precisa do valor do sinal (agenda), e a agenda precisa saber que o pagamento foi confirmado (pagamento).

## Opções consideradas
1. Só chamadas síncronas entre fachadas.
2. **Chamada síncrona a favor da seta + eventos para inverter a dependência**, com o registro de publicação do Spring Modulith.
3. Mensageria externa (RabbitMQ/Kafka).

## Decisão
Opção 2.
- **Síncrono só a favor da seta**, pela API pública (ex.: `pagamento → AgendaApi.dadosParaCobranca`).
- **Eventos** para o sentido contrário (ex.: `PagamentoConfirmado` ouvido pela agenda). Publicados na mesma transação da escrita; o Spring Modulith grava em `event_publication` (funciona como outbox) e entrega depois do commit.
- Listeners com `@ApplicationModuleListener` (assíncrono, transação própria) e **idempotentes**: reprocessar o mesmo evento não muda o resultado (ex.: confirmar reserva já `CONFIRMADO` é no-op).
- **Onde ficam e como são liberados:** os eventos de cada módulo ficam no subpacote `<modulo>.eventos`, marcado com `@NamedInterface("eventos")` no `package-info.java`. Quem ouve declara no próprio `package-info.java` a dependência só desse pacote, por exemplo `@ApplicationModule(allowedDependencies = {"comum", "pagamento::eventos"})` na `agenda`. Assim a `agenda` ouve `PagamentoConfirmado` sem depender do módulo `pagamento` inteiro, e o `ApplicationModules.verify()` (regra ArchUnit 2) recusa qualquer outro acesso.
- Eventos carregam **só ids e flags**, nunca dado pessoal, porque ficam serializados no banco.
- Mensageria externa descartada: custo e operação sem ganho para 1 instância.

## Consequências
- ✅ Sem ciclos; a escrita e o evento não se perdem (mesma transação).
- ⚠️ Confirmação **eventual**: entre o webhook e a reserva `CONFIRMADO` pode haver alguns segundos. A tela do guest consulta o status (polling curto).
- ⚠️ **Hibernação do Render:** um evento publicado e não concluído antes de a instância dormir fica em `event_publication` como incompleto. Na subida, os incompletos são reenviados (`spring.modulith.events.republish-outstanding-events-on-restart=true`, conferir o nome exato da propriedade na versão fixada). Um job periódico também reenvia os incompletos antigos.
- ⚠️ Evento atrasado não pode quebrar regra: a agenda nunca confirma reserva já vencida (R14) e o pagamento corrige a cobrança ao ouvir `ReservaExpirada`.

## Gatilhos para revisitar
- Mais de uma instância da API (eventos em memória + registro ainda servem, mas revisar a entrega).
- Consumidor fora do monólito → fila externa com o outbox.
