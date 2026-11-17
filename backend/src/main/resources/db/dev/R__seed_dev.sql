-- =============================================================================
-- Seed do perfil dev (só entra com spring.flyway.locations incluindo db/dev).
-- NUNCA em prod. Catálogo, jornada e barbeiro reais da Barbearia Altas Horas
-- (16/11/2026, D36 e D37); nenhum dado pessoal de cliente.
-- Repetível (R__): roda de novo quando este arquivo muda; ON CONFLICT mantém
-- o que o admin já alterou no painel.
-- =============================================================================

INSERT INTO barbeiro (id, nome_exibicao)
VALUES ('b0000000-0000-4000-8000-000000000001', 'Carlos Eduardo Cândido')
ON CONFLICT (id) DO NOTHING;

INSERT INTO servico (id, nome, duracao_min, preco_centavos) VALUES
    ('6f1c2a8e-0000-4000-8000-000000000001', 'Corte Clássico / Social',             30, 4500),
    ('6f1c2a8e-0000-4000-8000-000000000002', 'Corte Degradê / Fade / Navalhado',    30, 5500),
    ('6f1c2a8e-0000-4000-8000-000000000003', 'Corte Moderno / Visagismo (Tesoura)', 60, 6000),
    ('6f1c2a8e-0000-4000-8000-000000000004', 'Barba Completa / Desenhada',          30, 3000),
    ('6f1c2a8e-0000-4000-8000-000000000005', 'Acabamento (Pezinho) / Sobrancelha',  30, 2000)
ON CONFLICT (id) DO NOTHING;

-- Terça (2) a sábado (6), ISO-8601. Dois intervalos por dia: o buraco das
-- 12h00 às 13h00 é o almoço, editável pelo admin como qualquer intervalo.
INSERT INTO jornada (id, barbeiro_id, dia_semana, hora_inicio, hora_fim)
SELECT ('a1000000-0000-4000-8000-0000000000' || lpad((dia * 10 + turno)::text, 2, '0'))::uuid,
       'b0000000-0000-4000-8000-000000000001',
       dia,
       CASE turno WHEN 1 THEN time '07:00' ELSE time '13:00' END,
       CASE turno WHEN 1 THEN time '12:00' ELSE time '20:00' END
FROM generate_series(2, 6) AS dia, generate_series(1, 2) AS turno
ON CONFLICT (id) DO NOTHING;
