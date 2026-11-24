-- =============================================================================
-- Migração Flyway V13 · notificação ao barbeiro (Fatia 13, D171 a D177)
--
-- Quando o pagamento confirma uma reserva, o barbeiro dela recebe um aviso no painel e, se tiver
-- aparelho inscrito, uma notificação Web Push. Duas tabelas novas; nenhuma existente muda.
--
-- Nenhum dado de CLIENTE entra aqui (E2, E10): só o nome do serviço e o horário. O endpoint e as chaves
-- da inscrição pertencem ao aparelho do BARBEIRO e têm o mesmo ciclo de vida da conta dele (apagados no
-- desligamento e na exclusão; ON DELETE CASCADE cobre a exclusão física).
-- =============================================================================

CREATE TABLE inscricao_push (
    id              uuid        PRIMARY KEY,
    usuario_id      uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    endpoint        text        NOT NULL,
    chave_p256dh    text        NOT NULL,
    chave_auth      text        NOT NULL,
    criada_em       timestamptz NOT NULL,
    ultimo_envio_ok timestamptz,
    CONSTRAINT inscricao_push_endpoint_unico UNIQUE (endpoint)
);
CREATE INDEX inscricao_push_usuario_idx ON inscricao_push (usuario_id);

CREATE TABLE aviso_barbeiro (
    id              uuid        PRIMARY KEY,
    usuario_id      uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    reserva_id      uuid        NOT NULL,          -- sem FK: a agenda é de outro módulo (ADR 0002)
    servico         text        NOT NULL,          -- nome do serviço no momento do aviso
    inicio          timestamptz NOT NULL,
    criado_em       timestamptz NOT NULL,
    lido_em         timestamptz,
    push_enviado_em timestamptz,                   -- nulo = falta entregar (E6)
    CONSTRAINT aviso_barbeiro_um_por_reserva UNIQUE (reserva_id, usuario_id)
);
CREATE INDEX aviso_barbeiro_lista_idx ON aviso_barbeiro (usuario_id, criado_em DESC);
