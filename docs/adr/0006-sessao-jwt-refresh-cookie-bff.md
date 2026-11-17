# 0006. Sessão: JWT curto + refresh rotativo em cookie `HttpOnly` via BFF

- Status: **aceito** (revisão do Alicerce, 13/11/2026)
- Data: 2026-11-13 (proposta e aceite)
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
- **Onde o BFF guarda o token de acesso:** em **outro cookie `HttpOnly; Secure; SameSite=Strict; Path=/api`**, com `Max-Age` igual à validade do JWT (H15). As funções da Vercel não têm estado entre chamadas, então guardar em memória do servidor não funciona; e o navegador nunca recebe o JWT em JavaScript, `localStorage` ou corpo de resposta. A cada chamada, a rota do BFF lê o cookie no servidor e repassa como `Authorization: Bearer`; se a API responder `401`, o BFF usa o refresh uma vez e repete a chamada.
- **CSRF:** a API é stateless e só aceita `Bearer`. *Atualizado em 17/11/2026 (D58):* em vez de desligado, o CSRF fica ligado e é exigido quando a requisição altera dados e chega com cookie; o uso com `Bearer` não é afetado. O BFF é a única origem que fala com a API (CORS restrito por `CORS_ORIGENS`) e protege as próprias rotas com `SameSite=Strict` + verificação de `Origin`.
- **Revogação:** troca de senha incrementa `versao_credencial`; tokens com `ver` antigo são recusados na renovação. O acesso já emitido vale até expirar (15 min), risco aceito.
- Opção 4 descartada para o beta: custo e operação. Gatilho abaixo.

## Consequências
- ✅ Refresh fora do alcance de XSS; sessão longa sem token longo.
- ⚠️ O BFF vira peça de segurança (testes próprios na fatia do front).
- ⚠️ Logout encerra a família do refresh, mas o acesso atual vale até expirar.

## Gatilhos para revisitar
- Outro serviço precisar validar o token → RS256/ES256 com JWKS.
- Login social ligado → revisar o vínculo de contas (ADR futuro).
