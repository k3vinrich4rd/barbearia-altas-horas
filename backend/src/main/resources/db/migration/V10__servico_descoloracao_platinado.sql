-- =============================================================================
-- Migração Flyway V10 · serviço "Descoloração / Platinado (química)" (Fatia 12, E5 e D156)
--
-- Por quê: quem quer descoloração ou platinado não achava o serviço no catálogo, e o
-- Eduardo posta esse trabalho no Instagram. O preço (R$ 80,00) foi definido pelo Kevin em
-- 23/11/2026; a duração segue a regra de 60 min de todos os serviços (D94) e o Eduardo
-- ajusta pelo painel se a química precisar de mais tempo.
--
-- Número da migração: a V9 é da Fatia 10 (convite do barbeiro, ainda não mergeada na
-- develop). Esta é a V10 para não colidir: migração mergeada nunca é editada.
--
-- Idempotente, no molde da V8: id fixo e ON CONFLICT DO NOTHING (pega o id e o nome
-- único sem diferenciar maiúsculas). Se o admin já cadastrou um serviço com o mesmo
-- nome, ele não é sobrescrito nem duplicado. Nenhum dado pessoal.
-- =============================================================================

INSERT INTO servico (id, nome, duracao_min, preco_centavos) VALUES
    ('6f1c2a8e-0000-4000-8000-000000000006', 'Descoloração / Platinado (química)', 60, 8000)
ON CONFLICT DO NOTHING;
