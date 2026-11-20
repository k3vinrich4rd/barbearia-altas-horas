-- =============================================================================
-- PROPOSTA de migração Flyway V1 · Barbearia Altas Horas · 13/11/2026
-- Status: proposta, aguardando a revisão de arquitetura.
-- Destino na fatia S01: backend/src/main/resources/db/migration/V1__agenda.sql
--
-- Por que um V1 com o schema inteiro do beta: nada está em produção e o desenho
-- foi revisado de uma vez. Depois do primeiro deploy, toda mudança vira V2+,
-- no padrão expandir e contrair. Migração mergeada nunca é editada.
--
-- Convenções:
--   * tempo em timestamptz (UTC no banco; America/Sao_Paulo só na regra e na tela);
--   * dinheiro em centavos (bigint), nunca float;
--   * ids UUID gerados pela aplicação (gen_random_uuid() só como padrão de apoio);
--   * enums como varchar + CHECK (mais simples de evoluir que CREATE TYPE);
--   * nenhuma senha, segredo ou dado pessoal real neste arquivo.
-- =============================================================================

-- btree_gist permite misturar igualdade (barbeiro_id WITH =) e sobreposição de
-- intervalo (&&) na mesma constraint EXCLUDE. Sem ela, o "=" em uuid não tem
-- operador gist. (Aiven lista btree_gist entre as extensões suportadas:
-- conferir no primeiro deploy.)
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- -----------------------------------------------------------------------------
-- Módulo conta
-- -----------------------------------------------------------------------------
CREATE TABLE usuario (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome               varchar(120) NOT NULL,
    email              varchar(254) NOT NULL,
    whatsapp           varchar(13),                      -- só dígitos, com DDD (e DDI opcional)
    senha_hash         varchar(255),                     -- NULL só em conta social (pós-beta)
    cep                char(8),                          -- opcional, só dígitos
    bairro             varchar(120),                     -- NULL quando o CEP não foi resolvido (R31)
    cidade             varchar(120),
    uf                 char(2),
    ativo              boolean      NOT NULL DEFAULT true,
    versao_credencial  integer      NOT NULL DEFAULT 0,  -- sobe na troca de senha: invalida tokens antigos
    criado_em          timestamptz  NOT NULL DEFAULT now(),
    atualizado_em      timestamptz  NOT NULL DEFAULT now(),
    anonimizado_em     timestamptz,
    CONSTRAINT usuario_whatsapp_ck CHECK (whatsapp IS NULL OR whatsapp ~ '^[0-9]{10,13}$'),
    CONSTRAINT usuario_cep_ck      CHECK (cep IS NULL OR cep ~ '^[0-9]{8}$'),
    CONSTRAINT usuario_uf_ck       CHECK (uf IS NULL OR uf ~ '^[A-Z]{2}$')
);
-- E-mail único sem diferenciar maiúsculas (R24).
CREATE UNIQUE INDEX usuario_email_uk ON usuario (lower(email));
-- Painel de alcance (pós-beta): agregados por cidade e bairro.
CREATE INDEX usuario_cidade_bairro_ix ON usuario (cidade, bairro);

CREATE TABLE usuario_papel (
    usuario_id uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    papel      varchar(20) NOT NULL,
    PRIMARY KEY (usuario_id, papel),
    CONSTRAINT usuario_papel_ck CHECK (papel IN ('CLIENTE', 'BARBEIRO', 'ADMIN'))
);

-- Refresh token rotativo: guarda só o hash (SHA-256 em hex) e a família da
-- sessão, para revogar tudo quando um token já usado reaparece (R27).
CREATE TABLE refresh_token (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    familia_id      uuid        NOT NULL,
    hash            char(64)    NOT NULL,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    expira_em       timestamptz NOT NULL,
    usado_em        timestamptz,
    revogado_em     timestamptz,
    substituido_por uuid,
    CONSTRAINT refresh_token_hash_uk UNIQUE (hash),
    CONSTRAINT refresh_token_validade_ck CHECK (expira_em > criado_em)
);
CREATE INDEX refresh_token_usuario_ix ON refresh_token (usuario_id);
CREATE INDEX refresh_token_familia_ix ON refresh_token (familia_id);

-- Login social (pós-beta, atrás da flag app.features.social-login=false).
CREATE TABLE identidade_social (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       uuid         NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    provedor         varchar(20)  NOT NULL,
    sujeito          varchar(255) NOT NULL,              -- "sub" do provedor OIDC
    email_verificado boolean      NOT NULL,
    criado_em        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT identidade_social_provedor_ck CHECK (provedor IN ('GOOGLE', 'FACEBOOK')),
    CONSTRAINT identidade_social_uk UNIQUE (provedor, sujeito)
);
CREATE INDEX identidade_social_usuario_ix ON identidade_social (usuario_id);

-- -----------------------------------------------------------------------------
-- Módulo agenda
-- -----------------------------------------------------------------------------

-- Regras que a barbearia muda pelo painel, sem deploy. Linha única (id = 1).
-- Os valores iniciais abaixo são HIPÓTESES (docs/contexto.md, seção 4) e
-- ficam aqui por serem configuração de produto, não dado pessoal nem segredo.
CREATE TABLE configuracao_agenda (
    id                               smallint     PRIMARY KEY DEFAULT 1,
    fuso                             varchar(40)  NOT NULL DEFAULT 'America/Sao_Paulo',
    antecedencia_minima_min          integer      NOT NULL,  -- H5
    janela_cancelamento_min          integer      NOT NULL,  -- H6
    intervalo_entre_atendimentos_min integer      NOT NULL,  -- H4
    granularidade_min                integer      NOT NULL,  -- H11
    horizonte_dias                   integer      NOT NULL,  -- H12
    expiracao_guest_min              integer      NOT NULL,  -- 15 no prompt aprovado
    percentual_sinal                 integer      NOT NULL,  -- 50 no prompt aprovado
    politica_sinal                   varchar(500) NOT NULL,  -- H7: texto exibido ao guest
    atualizado_em                    timestamptz  NOT NULL DEFAULT now(),
    atualizado_por                   uuid         REFERENCES usuario (id),
    CONSTRAINT configuracao_agenda_unica_ck CHECK (id = 1),
    CONSTRAINT configuracao_agenda_valores_ck CHECK (
        antecedencia_minima_min >= 0
        AND janela_cancelamento_min >= 0
        AND intervalo_entre_atendimentos_min BETWEEN 0 AND 120
        AND granularidade_min IN (5, 10, 15, 20, 30, 60)
        AND horizonte_dias BETWEEN 1 AND 365
        AND expiracao_guest_min BETWEEN 5 AND 60
        AND percentual_sinal BETWEEN 0 AND 100
    )
);
INSERT INTO configuracao_agenda (id, antecedencia_minima_min, janela_cancelamento_min,
                                 intervalo_entre_atendimentos_min, granularidade_min,
                                 horizonte_dias, expiracao_guest_min, percentual_sinal,
                                 politica_sinal)
VALUES (1, 60, 120, 0, 30, 30, 15, 50,
        'HIPÓTESE: em caso de falta, o sinal fica com a barbearia. Cancelamento dentro do prazo tem o sinal devolvido manualmente.');

CREATE TABLE servico (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome           varchar(80)  NOT NULL,
    descricao      varchar(300),
    duracao_min    integer      NOT NULL,
    preco_centavos bigint       NOT NULL,
    ativo          boolean      NOT NULL DEFAULT true,
    version        bigint       NOT NULL DEFAULT 0,
    criado_em      timestamptz  NOT NULL DEFAULT now(),
    atualizado_em  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT servico_duracao_ck CHECK (duracao_min BETWEEN 5 AND 480),
    CONSTRAINT servico_preco_ck   CHECK (preco_centavos >= 0)
);
CREATE UNIQUE INDEX servico_nome_uk ON servico (lower(nome));

CREATE TABLE barbeiro (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    nome_exibicao varchar(80) NOT NULL,
    usuario_id    uuid        REFERENCES usuario (id),   -- login com papel BARBEIRO (opcional)
    ativo         boolean     NOT NULL DEFAULT true,
    version       bigint      NOT NULL DEFAULT 0,
    criado_em     timestamptz NOT NULL DEFAULT now(),
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT barbeiro_usuario_uk UNIQUE (usuario_id)
);

-- Jornada semanal: um barbeiro pode ter mais de um intervalo no mesmo dia
-- (ex.: manhã e tarde). Sobreposição entre intervalos é validada no caso de uso.
CREATE TABLE jornada (
    id          uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    barbeiro_id uuid     NOT NULL REFERENCES barbeiro (id) ON DELETE CASCADE,
    dia_semana  smallint NOT NULL,                       -- ISO-8601: 1 = segunda ... 7 = domingo
    hora_inicio time     NOT NULL,                       -- hora local da barbearia (configuracao_agenda.fuso)
    hora_fim    time     NOT NULL,
    CONSTRAINT jornada_dia_ck     CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT jornada_horario_ck CHECK (hora_fim > hora_inicio)
);
CREATE INDEX jornada_barbeiro_dia_ix ON jornada (barbeiro_id, dia_semana);

CREATE TABLE folga (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    barbeiro_id uuid         NOT NULL REFERENCES barbeiro (id) ON DELETE CASCADE,
    inicio      timestamptz  NOT NULL,
    fim         timestamptz  NOT NULL,
    motivo      varchar(200),
    criado_por  uuid         REFERENCES usuario (id),
    criado_em   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT folga_periodo_ck CHECK (fim > inicio)
);
-- Disponibilidade pergunta "há folga que sobrepõe este dia?": gist no intervalo.
CREATE INDEX folga_barbeiro_periodo_ix ON folga USING gist (barbeiro_id, tstzrange(inicio, fim, '[)'));

CREATE TABLE reserva (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    barbeiro_id         uuid         NOT NULL REFERENCES barbeiro (id),
    servico_id          uuid         NOT NULL REFERENCES servico (id),
    cliente_id          uuid         REFERENCES usuario (id),  -- NULL no guest
    origem              varchar(10)  NOT NULL,
    inicio              timestamptz  NOT NULL,
    fim                 timestamptz  NOT NULL,                 -- inicio + duração do serviço (R3)
    fim_ocupacao        timestamptz  NOT NULL,                 -- fim + intervalo entre atendimentos (R3)
    status              varchar(25)  NOT NULL,
    preco_centavos      bigint       NOT NULL,                 -- preço congelado na reserva (R8)
    sinal_centavos      bigint       NOT NULL DEFAULT 0,       -- 0 para cliente logado (R6)
    expira_em           timestamptz,                           -- só em AGUARDANDO_PAGAMENTO (R7)
    motivo_cancelamento varchar(300),
    cancelado_por       uuid,
    cancelado_em        timestamptz,
    finalizado_por      uuid,                                  -- quem marcou CONCLUIDO / NAO_COMPARECEU
    finalizado_em       timestamptz,
    version             bigint       NOT NULL DEFAULT 0,       -- @Version: transições concorrentes
    criado_em           timestamptz  NOT NULL DEFAULT now(),
    atualizado_em       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT reserva_status_ck CHECK (status IN ('AGUARDANDO_PAGAMENTO', 'CONFIRMADO', 'CONCLUIDO',
                                                   'NAO_COMPARECEU', 'CANCELADO', 'EXPIRADO')),
    CONSTRAINT reserva_origem_ck CHECK (
        (origem = 'CLIENTE' AND cliente_id IS NOT NULL AND sinal_centavos = 0)
        OR (origem = 'GUEST' AND cliente_id IS NULL)
    ),
    CONSTRAINT reserva_horario_ck  CHECK (fim > inicio AND fim_ocupacao >= fim),
    CONSTRAINT reserva_valores_ck  CHECK (preco_centavos >= 0 AND sinal_centavos BETWEEN 0 AND preco_centavos),
    CONSTRAINT reserva_expiracao_ck CHECK (status <> 'AGUARDANDO_PAGAMENTO' OR expira_em IS NOT NULL),
    -- R1 + R2: nenhum barbeiro com duas reservas sobrepostas entre as que ocupam
    -- horário. '[)' = intervalo meio aberto: 09:00-09:30 e 09:30-10:00 convivem.
    -- O banco garante mesmo com duas requisições simultâneas; a violação
    -- (SQLSTATE 23P01) vira 409 HORARIO_INDISPONIVEL (ADR 0003).
    -- now() não pode entrar no WHERE (precisa ser imutável): por isso a
    -- expiração preguiçosa (R16) e o job (R17) tiram as vencidas do filtro.
    CONSTRAINT reserva_sem_sobreposicao EXCLUDE USING gist (
        barbeiro_id WITH =,
        tstzrange(inicio, fim_ocupacao, '[)') WITH &&
    ) WHERE (status IN ('AGUARDANDO_PAGAMENTO', 'CONFIRMADO'))
);
-- Agenda do barbeiro por dia/semana (admin e disponibilidade).
CREATE INDEX reserva_barbeiro_inicio_ix ON reserva (barbeiro_id, inicio);
-- Agenda do dia de todos os barbeiros (painel).
CREATE INDEX reserva_inicio_ix ON reserva (inicio);
-- "Meus agendamentos" do cliente, mais recentes primeiro.
CREATE INDEX reserva_cliente_inicio_ix ON reserva (cliente_id, inicio DESC) WHERE cliente_id IS NOT NULL;
-- Job de expiração: só as que aguardam pagamento, pela data de vencimento.
CREATE INDEX reserva_expiracao_ix ON reserva (expira_em) WHERE status = 'AGUARDANDO_PAGAMENTO';

-- Contato do guest separado da reserva: facilita a anonimização (R29) e deixa
-- a tabela reserva sem dado pessoal.
CREATE TABLE reserva_guest (
    reserva_id     uuid         PRIMARY KEY REFERENCES reserva (id) ON DELETE CASCADE,
    nome           varchar(120) NOT NULL,
    email          varchar(254) NOT NULL,
    whatsapp       varchar(13)  NOT NULL,
    token_hash     char(64)     NOT NULL,                -- SHA-256 do token de acesso do guest
    criado_em      timestamptz  NOT NULL DEFAULT now(),
    anonimizado_em timestamptz,
    CONSTRAINT reserva_guest_token_uk UNIQUE (token_hash),
    CONSTRAINT reserva_guest_whatsapp_ck CHECK (whatsapp ~ '^[0-9]{10,13}$')
);
CREATE INDEX reserva_guest_retencao_ix ON reserva_guest (criado_em) WHERE anonimizado_em IS NULL;

-- -----------------------------------------------------------------------------
-- Módulo pagamento
-- -----------------------------------------------------------------------------

-- O CPF NÃO é guardado aqui: vai direto ao gateway na criação do cliente da
-- cobrança (ADR 0009). Guardamos só os ids do provedor.
CREATE TABLE cobranca (
    id                      uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id              uuid         NOT NULL REFERENCES reserva (id),
    provedor                varchar(20)  NOT NULL,
    id_cobranca_provedor    varchar(100),                -- NULL até o gateway responder
    id_cliente_provedor     varchar(100),
    valor_centavos          bigint       NOT NULL,
    status                  varchar(25)  NOT NULL,
    chave_idempotencia_hash char(64)     NOT NULL,       -- SHA-256 do X-Idempotency-Key (R10)
    hash_corpo              char(64)     NOT NULL,       -- HMAC-SHA256(IDEMPOTENCIA_HMAC_SEGREDO, reservaId|cpf) em hex;
                                                         -- mesma chave + corpo diferente = 422. HMAC, não SHA-256 puro:
                                                         -- o CPF tem só 10^9 combinações e um hash sem segredo
                                                         -- seria revertido por força bruta (ADR 0009)
    pix_copia_e_cola        text,                        -- o QR Code é gerado no front a partir dele
    pix_expira_em           timestamptz,
    paga_em                 timestamptz,
    devolvida_em            timestamptz,
    devolvida_por           uuid,
    version                 bigint       NOT NULL DEFAULT 0,
    criado_em               timestamptz  NOT NULL DEFAULT now(),
    atualizado_em           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT cobranca_provedor_ck CHECK (provedor IN ('ASAAS', 'MERCADO_PAGO')),
    CONSTRAINT cobranca_status_ck   CHECK (status IN ('PENDENTE', 'PAGA', 'PAGA_APOS_EXPIRACAO', 'CANCELADA',
                                                      'FALHOU', 'DEVOLUCAO_PENDENTE', 'DEVOLVIDA')),
    CONSTRAINT cobranca_valor_ck    CHECK (valor_centavos > 0),
    CONSTRAINT cobranca_idempotencia_uk UNIQUE (chave_idempotencia_hash),
    CONSTRAINT cobranca_provedor_uk     UNIQUE (provedor, id_cobranca_provedor)
);
CREATE INDEX cobranca_reserva_ix ON cobranca (reserva_id);
-- No máximo uma cobrança viva por reserva.
CREATE UNIQUE INDEX cobranca_ativa_por_reserva_uk ON cobranca (reserva_id) WHERE status IN ('PENDENTE', 'PAGA');
-- Painel: sinais a devolver (o status é a única fonte; não há coluna booleana paralela).
CREATE INDEX cobranca_devolucao_ix ON cobranca (criado_em) WHERE status IN ('PAGA_APOS_EXPIRACAO', 'DEVOLUCAO_PENDENTE');

-- Idempotência do webhook (R12): o id do EVENTO do provedor é único.
-- O payload inteiro não é guardado (pode trazer dados do pagador): só o necessário.
CREATE TABLE evento_webhook (
    id                   uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    provedor             varchar(20)  NOT NULL,
    id_evento_provedor   varchar(120) NOT NULL,
    tipo                 varchar(60)  NOT NULL,          -- ex.: PAYMENT_RECEIVED
    id_cobranca_provedor varchar(100),
    recebido_em          timestamptz  NOT NULL DEFAULT now(),
    processado_em        timestamptz,                    -- NULL = ainda não processado: um reenvio do mesmo
                                                         -- evento (ON CONFLICT) REPROCESSA em vez de ignorar,
                                                         -- para o pagamento não se perder
    resultado            varchar(30),                    -- PROCESSADO, IGNORADO, PAGO_APOS_EXPIRACAO, REVISAO_MANUAL...
    tentativas           smallint     NOT NULL DEFAULT 0, -- tentativas do job de reprocessamento (H20)
    ultima_tentativa_em  timestamptz,
    ultimo_erro          varchar(60),                    -- só o code do erro, nunca payload ou dado pessoal
    CONSTRAINT evento_webhook_provedor_ck CHECK (provedor IN ('ASAAS', 'MERCADO_PAGO')),
    CONSTRAINT evento_webhook_tentativas_ck CHECK (tentativas >= 0),
    CONSTRAINT evento_webhook_uk UNIQUE (provedor, id_evento_provedor)
);
CREATE INDEX evento_webhook_cobranca_ix ON evento_webhook (provedor, id_cobranca_provedor);
-- Job de reprocessamento: eventos gravados, não processados e ainda não mandados para revisão manual.
CREATE INDEX evento_webhook_pendente_ix ON evento_webhook (recebido_em)
    WHERE processado_em IS NULL AND resultado IS DISTINCT FROM 'REVISAO_MANUAL';
-- Painel: eventos que esgotaram as tentativas.
CREATE INDEX evento_webhook_revisao_ix ON evento_webhook (recebido_em) WHERE resultado = 'REVISAO_MANUAL';

-- -----------------------------------------------------------------------------
-- Módulo privacidade
-- -----------------------------------------------------------------------------

-- Histórico só de inserção: cada aceite ou revogação é uma linha nova (prova).
CREATE TABLE consentimento (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    finalidade    varchar(30) NOT NULL,
    versao_texto  varchar(20) NOT NULL,
    aceito        boolean     NOT NULL,
    registrado_em timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT consentimento_finalidade_ck CHECK (finalidade IN ('MARKETING_CEP'))
);
CREATE INDEX consentimento_usuario_ix ON consentimento (usuario_id, finalidade, registrado_em DESC);

CREATE TABLE solicitacao_titular (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   uuid        NOT NULL REFERENCES usuario (id),
    tipo         varchar(20) NOT NULL,
    solicitada_em timestamptz NOT NULL DEFAULT now(),
    concluida_em timestamptz,
    CONSTRAINT solicitacao_titular_tipo_ck CHECK (tipo IN ('EXPORTACAO', 'EXCLUSAO'))
);
CREATE INDEX solicitacao_titular_usuario_ix ON solicitacao_titular (usuario_id);

-- -----------------------------------------------------------------------------
-- Módulo admin
-- -----------------------------------------------------------------------------

-- Quem fez o quê e quando no painel. "detalhe" nunca leva dado pessoal
-- (ex.: guarda ids e campos alterados, não nome, telefone ou CPF).
CREATE TABLE auditoria_admin (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    ator_id     uuid        NOT NULL REFERENCES usuario (id),
    acao        varchar(60) NOT NULL,                    -- ex.: SERVICO_ALTERADO
    recurso     varchar(40) NOT NULL,                    -- ex.: servico
    recurso_id  uuid,
    detalhe     jsonb,
    ip          varchar(45),
    ocorrido_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auditoria_admin_ocorrido_ix ON auditoria_admin (ocorrido_em DESC);
CREATE INDEX auditoria_admin_ator_ix     ON auditoria_admin (ator_id, ocorrido_em DESC);

-- -----------------------------------------------------------------------------
-- Módulo plataforma
-- -----------------------------------------------------------------------------

-- ShedLock (provider JDBC): garante que o job de expiração não roda duplicado.
CREATE TABLE shedlock (
    name       varchar(64)  PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);

-- Registro de publicação de eventos do Spring Modulith (funciona como outbox).
-- ATENÇÃO: na fatia S00, copiar o schema oficial do spring-modulith-events-jdbc
-- da versão fixada no pom.xml; as colunas abaixo seguem o formato da linha 2.x
-- e precisam ser conferidas. Eventos nunca levam dado pessoal (só ids), porque
-- serialized_event fica gravado aqui.
CREATE TABLE event_publication (
    id                     uuid        PRIMARY KEY,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text,
    completion_attempts    integer,
    last_resubmission_date timestamptz
);
CREATE INDEX event_publication_completion_ix ON event_publication (completion_date);
