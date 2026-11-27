# Contato da política de privacidade formatado

**Objetivo:** o canal `APP_PRIVACIDADE_CONTATO`, quando é um telefone brasileiro, aparece na política como `(11) 90000-0000` (padrão de celular de 11 dígitos ou fixo de 10) e, na página `/privacidade`, vira um link `https://wa.me/55` + dígitos.
**Usuários:** qualquer pessoa que leia a política ou autorize o uso da região.
**Fora do escopo:** mudar o contrato (`canalDeContato` continua sendo uma string), hardcodar número, formatar e-mail ou o texto de fallback.

## Decisões de implementação

- O backend formata o valor de `app.privacidade.contato` em `FormatadorDeCanalDeContato` e entrega o texto já formatado em `canalDeContato` e na substituição de `{{contato}}` no Markdown. O campo do contrato permanece `string`; clientes que já tratam o valor como texto livre continuam compatíveis. Um telefone chega agora como `(11) 90000-0001` em vez de `11900000001`.
- Celular (11 dígitos): `(AA) NNNNN-NNNN`. Fixo (10 dígitos): `(AA) NNNN-NNNN`. O prefixo do país `55` ou `+55` também entra (13 dígitos no celular, 12 no fixo) e sai no mesmo padrão. E-mail (contém `@`) e qualquer outro texto — inclusive o fallback "ainda não configurado nesta instalação" — ficam iguais. Nenhum número comercial está escrito no repositório; os testes usam números fictícios (`11900000001`, `+5511900000001`, `5511900000001`, `1133334444`).
- Na página `/privacidade`, `linkificarTelefoneNaPolitica` transforma o telefone já formatado no Markdown em `[ (11) 90000-0001 ](https://wa.me/5511900000001)`. O componente do link acrescenta `target="_blank"`, `rel="noopener noreferrer"` e o nome acessível `"… (abre o WhatsApp)"`, que contém o texto visível.
- A caixinha de região em `/cadastro` continua mostrando `canalDeContato` como texto corrido ("Para falar sobre seus dados: …"). Só a página da política vira o telefone em link.
- Os testes de integração que usam `contato@exemplo.test` não mudam: e-mail não é formatado. O `PoliticaPrivacidadeTelefoneIT` cobre o telefone mascarado no `GET /politica-privacidade` com `@TestPropertySource` (`app.privacidade.contato=11900000001`), sem trocar o e-mail global de `application.properties`.
