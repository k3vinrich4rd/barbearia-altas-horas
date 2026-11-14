# 0006. Sessão: JWT curto + refresh rotativo em cookie `HttpOnly` via BFF

- Status: **proposta — revisão do Alicerce**
- Data: 2026-11-13
- Decisores: Alicerce (dono), Quarteto Dev (texto)

## Contexto
O front roda na Vercel e a API no Render (domínios diferentes). Guardar token no `localStorage` expõe a sessão a XSS. O plano grátis não comporta um provedor de identidade separado.

## Opções consideradas
1. JWT longo no `localStorage`.
2. **JWT de acesso curto + refresh rotativo guardado pelo BFF do Next em cookie `HttpOnly`.**
3. Sessão com estado no servidor (cookie de sessão da API).
4. Provedor OIDC externo (Keycloak etc.).

## Decisão
Opção 2.
- **Acesso:** JWT HS256 (`JWT_SEGREDO` ≥ 32 bytes, sem padrão em `prod`; a API não sobe sem ele), validade curta (**H15**: 15 min), claims `sub`, `papeis`, `ver` (versão da credencial). Validado **só pelas claims**, sem ir ao banco (módulo `plataforma`).
- **Refresh:** valor aleatório opaco, de uso único, guardado só como **hash SHA-256** (`refresh_token`), com `familia_id`. Cada `POST /auth/refresh` gera um novo e marca o anterior como usado. **Reuso** de um já usado → revoga a família inteira e responde `401 REFRESH_REUTILIZADO` (R27).
- **BFF:** o navegador fala só com o Next (`app/api/...`). O Next guarda o refresh em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` no próprio domínio e chama a API com `Authorization: Bearer`. O JavaScript da página nunca vê o refresh.
- **CSRF:** a API é stateless e só aceita `Bearer`, então CSRF fica desligado na API (com comentário no código). O BFF é a única origem que fala com a API (CORS restrito por `CORS_ORIGENS`) e protege as próprias rotas com `SameSite=Strict` + verificação de `Origin`.
- **Revogação:** troca de senha incrementa `versao_credencial`; tokens com `ver` antigo são recusados na renovação. O acesso já emitido vale até expirar (15 min), risco aceito.
- Opção 4 descartada para o beta: custo e operação. Gatilho abaixo.

## Consequências
- ✅ Refresh fora do alcance de XSS; sessão longa sem token longo.
- ⚠️ O BFF vira peça de segurança (testes próprios na fatia do front).
- ⚠️ Logout encerra a família do refresh, mas o acesso atual vale até expirar.

## Gatilhos para revisitar
- Outro serviço precisar validar o token → RS256/ES256 com JWKS.
- Login social ligado → revisar o vínculo de contas (ADR futuro).
