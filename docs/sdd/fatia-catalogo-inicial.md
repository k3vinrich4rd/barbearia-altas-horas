# 📐 Spec · Catálogo inicial (serviços, barbeiro e jornada no banco)

| | |
|---|---|
| **Status** | ✅ pedida pelo Kevin (20/11/2026) · implementada (20/11/2026) |
| **Origem** | no beta, `/agendar` mostrava "Sem serviços no momento" e o seletor de barbeiro só tinha "Qualquer barbeiro": o banco de produção nasceu sem catálogo. O Kevin pediu que os serviços reais, com nome e valor, e o barbeiro **Eduardo Candido** aparecessem |
| **Decisão** | [D152](../planejamento/decisoes.md) |
| **Regras** | D36 (jornada), D94 (todos os serviços com 60 min), D122 (WhatsApp público do barbeiro, opcional) |
| **Custo** | **zero**: só uma migração |
| **Não cobre** | nome completo do barbeiro (o modelo não tem campo; só o nome de exibição) · vários barbeiros com login e agenda próprios (ADR à parte) · telas |

## 1. Objetivo

Num banco novo ou no banco do beta, a API sobe com o catálogo real: os 5 serviços com nome, 60 min e valor, o
barbeiro Eduardo Candido e a jornada de terça a sábado. O cliente já consegue escolher serviço, barbeiro e horário.
O admin continua dono do catálogo pelo painel.

## 2. Contrato

Nenhuma rota muda. `GET /servicos`, `GET /barbeiros` e `GET /disponibilidade` passam a devolver o catálogo da V8.

### Banco · `V8__catalogo_inicial.sql`

| Tabela | O que entra | Idempotência |
|---|---|---|
| `servico` | os 5 serviços da tabela abaixo, 60 min cada, valor em centavos | ids fixos (os do seed dev) + `ON CONFLICT DO NOTHING` (id e nome único sem diferenciar maiúsculas) |
| `barbeiro` | `Eduardo Candido` (só o nome de exibição; o modelo não tem campo de nome completo) e o WhatsApp comercial público `11906941689`, o (11) 90694-1689 do rodapé e do cadastro do barbeiro no PDF de telas | `WHERE NOT EXISTS` pelo id fixo e pelo nome, com ou sem acento e em qualquer caixa |
| `jornada` | terça (2) a sábado (6): 07:00–12:00 e 13:00–20:00 (almoço das 12h às 13h) | só para o barbeiro da V8 e só se ele ainda não tem jornada |
| `barbeiro.usuario_id` | liga o barbeiro à conta admin que já existia | só se há exatamente um admin, ele não é de nenhum barbeiro e o da V8 é o único sem conta (a regra do `VinculoDoBarbeiro`) |

| Serviço | Duração | Valor | Fonte |
|---|---|---|---|
| Corte Clássico / Social | 60 min | R$ 45,00 | tabela oficial definida pelo dono, seção Serviços do PDF de telas |
| Corte Degradê / Fade / Navalhado | 60 min | R$ 55,00 | idem |
| Corte Moderno / Visagismo (tesoura) | 60 min | R$ 60,00 | idem |
| Barba Completa / Desenhada | 60 min | R$ 30,00 | idem |
| Acabamento (pezinho) / Sobrancelha | 60 min | R$ 20,00 | idem |

## 3. Decisões de desenho

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| 1 | Onde mora o catálogo inicial? | migração versionada `V8`, em todos os perfis | o seed `R__seed_dev.sql` só entra no perfil `dev`; produção precisava do mesmo dado, sem deploy de código nem cadastro manual |
| 2 | Ids? | os mesmos do seed dev | no perfil `dev` a V8 roda antes do seed repetível e o `ON CONFLICT` dele não duplica nada |
| 3 | E o que o admin já mudou? | nada é sobrescrito | `DO NOTHING` e `NOT EXISTS`: preço, nome, jornada e desativação feitos no painel valem |
| 4 | WhatsApp do barbeiro? | o comercial público, (11) 90694-1689, gravado só com dígitos | é o contato da barbearia no rodapé e no cadastro do barbeiro do PDF de telas, confirmado pelo dono; o site monta o `wa.me` com o número vindo da API (D122). O admin troca ou apaga pelo painel |
| 5 | Testes que montam o próprio catálogo | continuam apagando antes (`CadastrosBase`, `DadosDeTeste`) | o teste da V8 roda num contexto (e banco) só dele; o `AdminInicialIT` passa a usar o barbeiro da V8 como "o único barbeiro" |

## 4. Critérios de aceite

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | banco migrado | `GET /servicos` | 5 serviços, todos com 60 min, com os valores da tabela | `V8CatalogoInicialIT` |
| A2 | banco migrado | `GET /barbeiros` | só o Eduardo Candido, com `whatsapp = 11906941689` | `V8CatalogoInicialIT` |
| A3 | banco migrado | `GET /disponibilidade` numa terça livre e na segunda seguinte | 22 horários na terça; nenhum na segunda; jornada de terça a sábado | `V8CatalogoInicialIT` |
| A4 | banco migrado | rodar a V8 mais duas vezes | 5 serviços, 1 barbeiro, 10 intervalos de jornada | `V8CatalogoInicialIT` |
| A5 | admin mudou preço, nome, jornada e desativou um serviço | rodar a V8 de novo | nada sobrescrito nem recriado | `V8CatalogoInicialIT` |
| A6 | admin já cadastrou o mesmo serviço (outra caixa) e o mesmo barbeiro com outro id | rodar a V8 | nenhuma cópia; nenhuma jornada para um barbeiro que não é o da V8 | `V8CatalogoInicialIT` |
| A7 | banco com o primeiro admin e sem barbeiro | rodar a V8 | o barbeiro nasce ligado à conta admin, com a jornada | `V8CatalogoInicialIT` |

## 5. Entregas

- `backend/src/main/resources/db/migration/V8__catalogo_inicial.sql`
- `backend/src/test/java/.../banco/V8CatalogoInicialIT.java`
- `SeedDevWhatsappIT` (A12 da 6D): o seed dev continua sem gravar WhatsApp; o número do perfil dev passa a ser o da V8
- `R__seed_dev.sql`, `SeedDevIT` e `AdminInicialIT`: nome de exibição `Eduardo Candido` e nomes dos serviços como na tabela oficial
- `scripts/testar-imagem.sh` (A9b): a imagem `prod` sobe com os 5 serviços da V8 e sem o seed dev no histórico do Flyway

## 6. Riscos e cuidados

- **Deploy:** o Flyway roda sozinho na subida da API (`spring.flyway`, em todos os perfis). Não precisa de passo manual.
- **Serviço com nome diferente já cadastrado no painel** (ex.: "Corte Social") não é reconhecido como o mesmo e
  fica ao lado do da V8. O admin desativa o que sobrar.
- **WhatsApp:** é dado comercial público da barbearia, não de cliente. Se o número mudar, o admin troca no painel; a V8 não volta a gravar por cima.
- **Nome com acento:** o nome de exibição é `Eduardo Candido`, sem acento, como pedido pelo Kevin.

## 7. Pronto quando

- [x] A1 a A7 com teste verde no `./mvnw verify`
- [x] `scripts/conferir-spec.sh catalogo --exigir` sem falta
