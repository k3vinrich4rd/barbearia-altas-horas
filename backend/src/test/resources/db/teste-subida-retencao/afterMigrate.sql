-- Só para o RetencaoNaSubidaIT (A61b): um visitante já vencido existe ANTES de a API terminar de subir.
-- "Agora" nos testes é 17/11/2026 08:00 (-03:00); esta reserva terminou em 01/06/2026, bem mais de 90 dias antes.
INSERT INTO barbeiro (id, nome_exibicao, ativo)
VALUES ('b0000000-0000-4000-8000-0000000000b1', 'Barbeiro da retenção', true)
ON CONFLICT DO NOTHING;

INSERT INTO servico (id, nome, duracao_min, preco_centavos, ativo)
VALUES ('5e000000-0000-4000-8000-0000000000b1', 'Serviço da retenção', 30, 5500, true)
ON CONFLICT DO NOTHING;

INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos)
VALUES ('e0000000-0000-4000-8000-0000000000b1', 'b0000000-0000-4000-8000-0000000000b1',
        '5e000000-0000-4000-8000-0000000000b1', 'GUEST', '2026-06-01 10:00-03', '2026-06-01 10:30-03',
        '2026-06-01 10:30-03', 'CANCELADO', 5500)
ON CONFLICT DO NOTHING;

INSERT INTO reserva_guest (reserva_id, nome, email, whatsapp, token_hash)
VALUES ('e0000000-0000-4000-8000-0000000000b1', 'Visitante Fictício da Subida', 'visitante.subida@exemplo.test',
        '11988880002', repeat('b', 64))
ON CONFLICT DO NOTHING;
