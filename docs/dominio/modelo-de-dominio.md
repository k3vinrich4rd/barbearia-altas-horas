# 🧩 Modelo de domínio e máquina de estados

| | |
|---|---|
| 🎯 **Para que serve** | as entidades, quem é dono de quê (módulo) e as transições de status da reserva, com quem dispara cada uma |
| 👥 **Área responsável** | backend · arquitetura: Alicerce |
| ⏰ **Quando usar** | antes de escrever entidade, migração ou regra de status |
| 🧰 **Como** | diagramas Mermaid + tabela de transições (citada pela regra R20) |
| 🚫 **Não cobre** | colunas e índices ([ERD e DDL](../banco/erd.md)) · rotas ([contrato](../contrato/contrato-api.md)) |

> Status: **proposta (13/11/2026)**, aguardando revisão do Alicerce (arquitetura). Valores marcados **HIPÓTESE** estão na [seção 4 do dossiê](../contexto.md#4-hipóteses-em-aberto).

## 1. Entidades por módulo

Os módulos seguem a [arquitetura do Alicerce](../arquitetura/README.md). Entre módulos, uma entidade **nunca** referencia a outra por objeto (`@ManyToOne`); só pelo id (`UUID`) (regra ArchUnit 10). As setas tracejadas abaixo são referências por id.

```mermaid
classDiagram
    direction LR

    namespace conta {
        class Usuario {
            UUID id
            String nome
            Email email
            Telefone whatsapp
            String senhaHash  "NULL só em conta social"
            Set~Papel~ papeis
            Cep cep  "opcional"
            String bairro
            String cidade
            String uf
            boolean ativo
            Instant anonimizadoEm
        }
        class RefreshToken {
            UUID id
            UUID usuarioId
            UUID familiaId
            String hash  "SHA-256"
            Instant expiraEm
            Instant usadoEm
            Instant revogadoEm
        }
        class Papel {
            <<enumeration>>
            CLIENTE
            BARBEIRO
            ADMIN
        }
    }

    namespace agenda {
        class Servico {
            UUID id
            String nome
            int duracaoMin
            Dinheiro preco
            boolean ativo
        }
        class Barbeiro {
            UUID id
            String nomeExibicao
            UUID usuarioId  "opcional: login BARBEIRO"
            boolean ativo
        }
        class Jornada {
            UUID id
            DayOfWeek diaSemana
            LocalTime inicio
            LocalTime fim
        }
        class Folga {
            UUID id
            Instant inicio
            Instant fim
            String motivo
        }
        class Reserva {
            UUID id
            UUID barbeiroId
            UUID servicoId
            UUID clienteId  "NULL no guest"
            Origem origem
            Instant inicio
            Instant fim
            Instant fimOcupacao
            StatusReserva status
            Dinheiro precoCongelado
            Instant expiraEm
            String motivoCancelamento
            long version
            +confirmar(Instant agora)
            +expirar(Instant agora)
            +cancelar(Ator ator, String motivo, Instant agora)
            +concluir(Ator ator, Instant agora)
            +marcarFalta(Ator ator, Instant agora)
            +registrarAceite(int versaoPolitica, Instant agora)
        }
        class ReservaGuest {
            UUID reservaId
            String nome
            Email email
            Telefone whatsapp
            String tokenHash  "acesso do guest"
            Instant anonimizadoEm
        }
        class ConfiguracaoAgenda {
            int antecedenciaMinimaMin  "HIPÓTESE H5"
            int janelaCancelamentoMin  "HIPÓTESE H6"
            int intervaloEntreAtendimentosMin  "HIPÓTESE H4"
            int granularidadeMin  "HIPÓTESE H11"
            int horizonteDias  "HIPÓTESE H12"
            int expiracaoGuestMin  "15"
            int expiracaoPagamentoMin  "15"
        }
        class StatusReserva {
            <<enumeration>>
            AGUARDANDO_PAGAMENTO
            CONFIRMADO
            CONCLUIDO
            NAO_COMPARECEU
            CANCELADO
            EXPIRADO
        }
    }

    namespace pagamento {
        class Cobranca {
            UUID id
            UUID reservaId
            Provedor provedor
            String idCobrancaProvedor
            String idClienteProvedor
            Dinheiro valor
            StatusCobranca status
            String chaveIdempotenciaHash
            String hashCorpo
            String pixCopiaECola
            Instant expiraEm
            Instant pagaEm
        }
        class EventoWebhook {
            UUID id
            Provedor provedor
            String idEventoProvedor  "único"
            String tipo
            Instant recebidoEm
            Instant processadoEm
        }
    }

    namespace privacidade {
        class Consentimento {
            UUID id
            UUID usuarioId
            Finalidade finalidade
            String versaoTexto
            boolean aceito
            Instant registradoEm
            Instant revogadoEm
        }
        class SolicitacaoTitular {
            UUID id
            UUID usuarioId
            TipoSolicitacao tipo
            Instant solicitadaEm
            Instant concluidaEm
        }
    }

    namespace admin {
        class AuditoriaAdmin {
            UUID id
            UUID atorId
            String acao
            String recurso
            UUID recursoId
            String detalhe  "sem dado pessoal"
            Instant ocorridoEm
        }
    }

    Usuario "1" --> "*" RefreshToken
    Barbeiro "1" *-- "*" Jornada
    Barbeiro "1" *-- "*" Folga
    Reserva "1" *-- "0..1" ReservaGuest
    Reserva ..> Barbeiro : barbeiroId
    Reserva ..> Servico : servicoId
    Reserva ..> Usuario : clienteId
    Barbeiro ..> Usuario : usuarioId
    Cobranca ..> Reserva : reservaId
    Cobranca "1" <.. "*" EventoWebhook : idCobrancaProvedor
    Consentimento ..> Usuario : usuarioId
    AuditoriaAdmin ..> Usuario : atorId
```

**Value objects em `comum`** (Alicerce, seção 3): `Dinheiro` (centavos em `long`, nunca `double`), `Cpf`, `Telefone`, `Cep` e `Email`, que validam, sanitizam (só dígitos) e **mascaram no `toString()`**, para um log descuidado não vazar dado pessoal.

## 2. Máquina de estados

```mermaid
stateDiagram-v2
    [*] --> AGUARDANDO_PAGAMENTO: cliente ou guest reserva (R6, R7)

    AGUARDANDO_PAGAMENTO --> CONFIRMADO: PagamentoConfirmado antes de expira_em (R13)
    AGUARDANDO_PAGAMENTO --> EXPIRADO: job ou expiração preguiçosa, expira_em vencido (R16, R17)
    AGUARDANDO_PAGAMENTO --> CANCELADO: admin cancela com motivo (R22)

    EXPIRADO --> CONFIRMADO: pagamento tardio com horário ainda livre (R14)
    CONFIRMADO --> CANCELADO: só a barbearia, com motivo (R22)
    CONFIRMADO --> CONCLUIDO: barbeiro/admin, a partir do início (R21)
    CONFIRMADO --> NAO_COMPARECEU: barbeiro/admin, valor retido (R19, R21)

    EXPIRADO --> [*]
    CANCELADO --> [*]
    CONCLUIDO --> [*]
    NAO_COMPARECEU --> [*]

    note right of EXPIRADO
        Pagamento tardio: horário livre volta a
        CONFIRMADO; horário ocupado deixa a cobrança
        PAGA_APOS_EXPIRACAO como pendência do admin (R14)
    end note
```

### Tabela de transições (fonte da regra R20)

| De | Para | Quem dispara | Condição | Efeito colateral |
|---|---|---|---|---|
| — | `AGUARDANDO_PAGAMENTO` | cliente logado (`POST /agendamentos`) ou guest (`POST /agendamentos/guest`, com limite por IP, R26) | horário livre (R1), dentro da jornada (R4), antecedência e horizonte (R5) | `expira_em = agora + 15 min`; preço congelado (R8); `ReservaCriada` |
| `AGUARDANDO_PAGAMENTO` | `CONFIRMADO` | sistema: listener de `PagamentoConfirmado` | `agora < expira_em` | — |
| `AGUARDANDO_PAGAMENTO` | `EXPIRADO` | sistema: job com ShedLock ou expiração preguiçosa na inserção | `expira_em <= agora` | `ReservaExpirada` → pagamento cancela a cobrança pendente |
| `AGUARDANDO_PAGAMENTO` | `CANCELADO` | admin | motivo obrigatório | `ReservaCancelada` → pagamento cancela a cobrança |
| `EXPIRADO` | `CONFIRMADO` | sistema: pagamento confirmado depois de `expira_em` | horário ainda livre (a constraint decide) | se ocupado, fica `EXPIRADO` e a cobrança vira `PAGA_APOS_EXPIRACAO` (pendência do admin, R14) |
| `CONFIRMADO` | `CANCELADO` | só a barbearia (admin) | motivo obrigatório | `ReservaCancelada`; a cobrança paga vira `DEVOLUCAO_PENDENTE` |
| `CONFIRMADO` | `CONCLUIDO` | barbeiro/admin | `agora >= inicio` | auditoria |
| `CONFIRMADO` | `NAO_COMPARECEU` | barbeiro/admin | `agora >= inicio` | auditoria; o valor pago fica com a barbearia (R19) |
| cliente | `CANCELADO` ou novo horário | — | **não existe** (R18) | sem rota pública |
| qualquer final | qualquer | — | — | `422 TRANSICAO_INVALIDA` |

**Concorrência na transição:** a reserva tem `@Version` (otimista). Duas transições ao mesmo tempo (ex.: webhook confirmando enquanto o job expira) → uma vence e a outra recebe conflito de versão e é reavaliada: se a reserva já expirou, o pagamento cai na regra R14.

### Status da cobrança (módulo `pagamento`)

```mermaid
stateDiagram-v2
    [*] --> PENDENTE: cobrança Pix criada no gateway
    PENDENTE --> PAGA: evento de pagamento recebido e reserva ainda válida
    PENDENTE --> PAGA_APOS_EXPIRACAO: evento de pagamento com a reserva já EXPIRADO (H8)
    PENDENTE --> CANCELADA: ReservaExpirada ou ReservaCancelada
    PENDENTE --> FALHOU: gateway informa falha
    PAGA --> DEVOLUCAO_PENDENTE: reserva cancelada dentro da janela (H7)
    PAGA_APOS_EXPIRACAO --> DEVOLVIDA: admin registra a devolução manual
    DEVOLUCAO_PENDENTE --> DEVOLVIDA: admin registra a devolução manual
    PAGA --> [*]
    CANCELADA --> [*]
    FALHOU --> [*]
    DEVOLVIDA --> [*]
```

## 3. Eventos entre módulos

Conforme o ADR 0002: publicados com o registro de publicação do Spring Modulith (funciona como outbox), ouvidos com `@ApplicationModuleListener` (assíncrono, depois do commit, com nova tentativa). **Todo listener é idempotente.**

| Evento | Emissor | Ouvintes | Efeito |
|---|---|---|---|
| `ContaCriada(usuarioId, aceitouMarketing, versaoTexto)` | conta | privacidade | grava o consentimento |
| `ReservaCriada(reservaId, origem)` | agenda | — (reservado para lembretes pós-beta) | — |
| `ReservaCancelada(reservaId)` | agenda | pagamento | cancela cobrança pendente ou marca devolução |
| `ReservaExpirada(reservaId)` | agenda | pagamento | cancela cobrança pendente |
| `PagamentoConfirmado(reservaId, cobrancaId)` | pagamento | agenda | confirma a reserva se ainda válida |
| `PagamentoFalhou(reservaId, cobrancaId)` | pagamento | agenda | nada muda; a reserva expira no prazo |
| `ExclusaoSolicitada(usuarioId)` | privacidade | conta, agenda, pagamento | cada um anonimiza o que é seu |
| `ContaAnonimizada(usuarioId)` | conta | — | fecha a solicitação do titular |

**Pagamento depois da expiração (R14), sem dependência proibida:**
1. O webhook, antes de marcar a cobrança, consulta `AgendaApi.situacao(reservaId)` (chamada síncrona permitida: `pagamento → agenda`).
2. Reserva ainda `AGUARDANDO_PAGAMENTO` e dentro do prazo → cobrança `PAGA` e evento `PagamentoConfirmado`.
3. Reserva `EXPIRADO` (ou com prazo vencido) → cobrança `PAGA_APOS_EXPIRACAO`, sem evento. Aparece no painel entre os sinais a devolver (`PAGA_APOS_EXPIRACAO` ou `DEVOLUCAO_PENDENTE`; o status é a única fonte).
4. Corrida residual (a reserva vence entre o passo 1 e o listener da agenda): a agenda não confirma reserva vencida e publica `ReservaExpirada`; o listener do pagamento encontra a cobrança `PAGA` e a converte para `PAGA_APOS_EXPIRACAO`. Nenhum lado precisa conhecer o outro além do que a tabela de dependências permite.

A forma final fica para a revisão do Alicerce (arquitetura).
