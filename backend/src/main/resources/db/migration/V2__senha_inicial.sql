-- Fatia 3 (E24): o admin criado na subida nasce com a senha que veio da variável de ambiente.
-- Quem configurou o servidor conhece essa senha, então ela precisa ser trocada no primeiro acesso.
ALTER TABLE usuario ADD COLUMN deve_trocar_senha boolean NOT NULL DEFAULT false;
