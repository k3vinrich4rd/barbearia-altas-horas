-- Fatia 4A · outbox do Spring Modulith (E11, D41).
-- Cópia do schema oficial da versão 2.1.2 (spring-modulith-events-jdbc, schemas/v2/schema-postgresql.sql).
-- Cada evento entre módulos (ex.: ReservaExpirada) é gravado aqui na MESMA transação da mudança que o gerou;
-- completion_date fica nula até o listener terminar. Se a API cair no meio, o evento não se perde.
-- Ao subir de versão do Modulith, conferir se o schema oficial mudou (ContextoSobeIT e ExpiracaoJobIT quebram).
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

-- ShedLock com a hora do banco (usingDbTime) grava e compara timezone('utc', now()), um timestamp SEM fuso.
-- Numa coluna timestamptz, o PostgreSQL converteria esse valor pelo fuso da sessão, e duas instâncias com fusos
-- diferentes discordariam sobre quando a trava acaba (pego pelo teste A19). O tipo recomendado pelo ShedLock
-- é timestamp sem fuso, sempre em UTC; a tabela está vazia até o primeiro job rodar.
ALTER TABLE shedlock
    ALTER COLUMN lock_until TYPE timestamp USING lock_until AT TIME ZONE 'UTC',
    ALTER COLUMN locked_at  TYPE timestamp USING locked_at  AT TIME ZONE 'UTC';
