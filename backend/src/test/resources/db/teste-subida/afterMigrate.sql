-- Só para o ExpiracaoJobIT (A21): uma reserva já vencida existe ANTES de a API terminar de subir.
-- Flyway roda este callback logo depois das migrações, antes de o agendador de jobs começar.
-- "Agora" nos testes é 17/11/2026 08:00 (-03:00); esta reserva venceu às 07:00.
INSERT INTO barbeiro (id, nome_exibicao, ativo)
VALUES ('b0000000-0000-4000-8000-0000000000a1', 'Barbeiro da subida', true)
ON CONFLICT DO NOTHING;

INSERT INTO servico (id, nome, duracao_min, preco_centavos, ativo)
VALUES ('5e000000-0000-4000-8000-0000000000a1', 'Serviço da subida', 30, 5500, true)
ON CONFLICT DO NOTHING;

INSERT INTO reserva (id, barbeiro_id, servico_id, origem, inicio, fim, fim_ocupacao, status, preco_centavos, expira_em)
VALUES ('e0000000-0000-4000-8000-0000000000a1', 'b0000000-0000-4000-8000-0000000000a1',
        '5e000000-0000-4000-8000-0000000000a1', 'GUEST', '2026-12-01 10:00-03', '2026-12-01 10:30-03',
        '2026-12-01 10:30-03', 'AGUARDANDO_PAGAMENTO', 5500, '2026-11-17 07:00-03')
ON CONFLICT DO NOTHING;
