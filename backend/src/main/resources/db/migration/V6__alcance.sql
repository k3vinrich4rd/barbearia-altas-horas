-- Fatia 6C · alcance por região.
-- A reserva guarda uma CÓPIA da região de quem foi atendido (bairro, cidade e uf), tirada no momento da reserva: a
-- estatística não muda se o cliente se mudar e sobrevive à exclusão da conta (E30, E34). Bairro e cidade sem nome nem
-- contato não identificam ninguém. As três colunas são nuláveis: reserva sem região é a regra quando o CEP não foi
-- informado ou não foi resolvido (R31).
ALTER TABLE reserva
    ADD COLUMN regiao_bairro varchar(120),
    ADD COLUMN regiao_cidade varchar(120),
    ADD COLUMN regiao_uf     char(2),
    ADD CONSTRAINT reserva_regiao_par_ck CHECK ((regiao_cidade IS NULL) = (regiao_uf IS NULL)),
    ADD CONSTRAINT reserva_regiao_uf_ck CHECK (regiao_uf IS NULL OR regiao_uf ~ '^[A-Z]{2}$');

-- O CEP do visitante é dado pessoal (localiza a casa): fica na tabela do contato, com o mesmo CHECK do usuario, e
-- vira NULL na anonimização do visitante (A74). A região da reserva fica.
ALTER TABLE reserva_guest
    ADD COLUMN cep char(8),
    ADD CONSTRAINT reserva_guest_cep_ck CHECK (cep IS NULL OR cep ~ '^[0-9]{8}$');

-- O alcance só conta CONFIRMADO e CONCLUIDO, por período: um índice parcial pequeno cobre a consulta do painel e da vitrine.
CREATE INDEX reserva_alcance_ix ON reserva (inicio) WHERE status IN ('CONFIRMADO', 'CONCLUIDO');
