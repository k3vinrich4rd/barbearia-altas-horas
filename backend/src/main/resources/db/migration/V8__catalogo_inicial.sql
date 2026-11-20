-- =============================================================================
-- Migração Flyway V8 · catálogo inicial da Barbearia Altas Horas (D152)
--
-- Por quê: o beta subiu com o catálogo vazio, e /agendar mostrava "Sem serviços
-- no momento". O catálogo real (5 serviços, 1 barbeiro, jornada de terça a
-- sábado) entra aqui, em todos os perfis, e o admin continua dono dele: pode
-- editar preço, nome, jornada e desativar pelo painel.
--
-- Fonte: telas aprovadas do site (PDF "Site · Barbearia Altas Horas", pág. 3,
-- "Serviços", e pág. 13, "Cadastros") e o README do design system. Todos os
-- serviços duram 60 min (D94). Nenhum dado pessoal: só o nome profissional do
-- barbeiro e o WhatsApp comercial público da barbearia, o mesmo que as telas
-- mostram no rodapé e no cadastro do barbeiro (D122). O admin muda pelo painel.
--
-- Idempotente: ids fixos (os mesmos do seed dev), ON CONFLICT DO NOTHING (pega o
-- id e o nome único sem diferenciar maiúsculas) e WHERE NOT EXISTS. Se o admin
-- já cadastrou o mesmo serviço ou o mesmo barbeiro, nada é duplicado nem
-- sobrescrito.
-- =============================================================================

-- Barbeiro: só entra se ainda não existe um com o mesmo nome (com ou sem acento).
-- O modelo só tem o nome de exibição; o nome completo (Carlos Eduardo Candido) não tem campo.
INSERT INTO barbeiro (id, nome_exibicao, whatsapp)
SELECT 'b0000000-0000-4000-8000-000000000001', 'Eduardo Candido', '11906941689'
WHERE NOT EXISTS (
    SELECT 1 FROM barbeiro
    WHERE id = 'b0000000-0000-4000-8000-000000000001'
       OR translate(lower(nome_exibicao), 'âáàãéêíóôõúç', 'aaaaeeiooouc')
          IN ('eduardo candido', 'carlos eduardo candido')
);

-- Serviços com o nome e o preço (em centavos) das telas aprovadas.
INSERT INTO servico (id, nome, duracao_min, preco_centavos) VALUES
    ('6f1c2a8e-0000-4000-8000-000000000001', 'Corte Clássico / Social',             60, 4500),
    ('6f1c2a8e-0000-4000-8000-000000000002', 'Corte Degradê / Fade / Navalhado',    60, 5500),
    ('6f1c2a8e-0000-4000-8000-000000000003', 'Corte Moderno / Visagismo (tesoura)', 60, 6000),
    ('6f1c2a8e-0000-4000-8000-000000000004', 'Barba Completa / Desenhada',          60, 3000),
    ('6f1c2a8e-0000-4000-8000-000000000005', 'Acabamento (pezinho) / Sobrancelha',  60, 2000)
ON CONFLICT DO NOTHING;

-- Jornada padrão: terça (2) a sábado (6), ISO-8601, das 7h às 20h com almoço das
-- 12h às 13h (dois intervalos por dia). Só para o barbeiro criado acima e só se
-- ele ainda não tem jornada: a que o admin ajustou no painel não é tocada.
INSERT INTO jornada (id, barbeiro_id, dia_semana, hora_inicio, hora_fim)
SELECT ('a1000000-0000-4000-8000-0000000000' || lpad((dia * 10 + turno)::text, 2, '0'))::uuid,
       'b0000000-0000-4000-8000-000000000001',
       dia,
       CASE turno WHEN 1 THEN time '07:00' ELSE time '13:00' END,
       CASE turno WHEN 1 THEN time '12:00' ELSE time '20:00' END
FROM generate_series(2, 6) AS dia, generate_series(1, 2) AS turno
WHERE EXISTS (SELECT 1 FROM barbeiro WHERE id = 'b0000000-0000-4000-8000-000000000001')
  AND NOT EXISTS (SELECT 1 FROM jornada WHERE barbeiro_id = 'b0000000-0000-4000-8000-000000000001')
ON CONFLICT DO NOTHING;

-- Banco que já tinha o primeiro admin (o caso do beta): o evento que liga o admin ao barbeiro (VinculoDoBarbeiro)
-- só roda quando o admin nasce, e aqui o barbeiro nasce depois. A mesma regra vale agora: liga o barbeiro criado
-- acima à conta admin só se existe exatamente uma, se ela ainda não é de nenhum barbeiro e se ele é o único
-- barbeiro sem conta. Em qualquer outro caso, o admin escolhe no painel. Banco novo: a V8 roda antes da subida e o
-- evento faz o vínculo quando o admin nasce.
UPDATE barbeiro b
SET usuario_id = (SELECT p.usuario_id FROM usuario_papel p WHERE p.papel = 'ADMIN'),
    atualizado_em = now()
WHERE b.id = 'b0000000-0000-4000-8000-000000000001'
  AND b.usuario_id IS NULL
  AND (SELECT count(*) FROM usuario_papel WHERE papel = 'ADMIN') = 1
  AND (SELECT count(*) FROM barbeiro WHERE usuario_id IS NULL) = 1
  AND NOT EXISTS (SELECT 1 FROM barbeiro o
                  JOIN usuario_papel p ON p.usuario_id = o.usuario_id AND p.papel = 'ADMIN');
