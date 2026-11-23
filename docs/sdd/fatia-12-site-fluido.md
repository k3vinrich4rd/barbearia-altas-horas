# 📐 Spec · Fatia 12 · Site mais fluido e mais completo (home, agendar e galeria)

| | |
|---|---|
| **Status** | ✅ aprovada pelo Kevin (23/11/2026) com as 12 propostas (D-1 a D-12) · entrega em 12A, 12B e 12C, na mesma branch `feat/fatia-12-site-fluido` |
| **Origem** | pedido do Kevin em 23/11/2026: não gostou do andamento nem da divisão do site no ar (<https://barbearia-altas-horas.vercel.app>). Quer o site **mais fluido e mais completo**, mantendo a **paleta** ([`frontend/design/tokens.json`](../../frontend/design/tokens.json): preto, branco e cinzas, xadrez, Playfair) e a **lógica de funcionamento** |
| **Não muda** | as 3 formas de agendar (com cadastro, sem cadastro e WhatsApp com reserva manual, R33) · o pagamento antecipado, com o prazo vindo de `expiraEm` (D121, 3 a 60 min) · o painel (Fatias 5 e 7B) · as regras de negócio e o contrato dos endpoints existentes |
| **Entrega em três PRs** (proposta) | **12A** · galeria, serviço novo e renomear "Trajetória" (`feat/fatia-12a-galeria-e-trajetoria`) · **12B** · nova home, mobile-first (`feat/fatia-12b-home-fluida`) · **12C** · fluxo de agendar em poucos toques (`feat/fatia-12c-agendar-fluido`) |
| **Regras** | R28 e R30 (alcance só com dados reais, ADR 0014), R33 (reserva manual), D121 (prazo de pagamento), D148 (visual pelo canvas, comportamento pela spec), D152 (catálogo inicial) |
| **Depende de** | [Fatia 11](fatia-11-alcance-por-nivel.md) (o bloco "de onde vêm nossos clientes" só ganha dados na 11C) · [Fatia 10](fatia-10-convite-do-barbeiro.md) (pode usar a próxima migração: ver E5) · **desenho novo da sessão de design** (seção 8): a spec **não decide o visual** (D148) |
| **Contrato** | **sem mudança** (nenhuma rota nova nem campo novo) |
| **Custo** | **zero**: as fotos vão no repositório e passam pelo `next/image` da Vercel; nada de serviço pago, mapa embutido ou biblioteca nova pesada |
| **Não cobre** | avaliações com nota inventada · mapa embutido · login social · fidelidade e combos · a Fatia 11 em si · reescrever o painel |

## 1. Objetivo

Quem abre o site no celular entende em segundos **quem é o Eduardo, o que ele faz e quanto custa**, vê trabalho de verdade (galeria), chega ao horário em **poucos toques** e sabe como pagar. A informação deixa de estar espalhada em blocos soltos e passa a seguir a ordem em que o cliente decide: **agendar → serviços → barbeiro → trabalho → confiança → dúvidas → onde fica**.

## 2. Pesquisa de referências (padrões de experiência, não de marca)

Consultadas em **23/11/2026**. Nada de marca, texto ou layout foi copiado: só o padrão de experiência. Onde a página pública não confirma um recurso, está escrito "não confirmado".

| # | Referência | O que torna a experiência fluida | Fonte |
|---|---|---|---|
| 1 | **Fresha** | O cliente escolhe os serviços (vários numa reserva), depois o profissional (opcional, ou um específico por serviço) e vê horários reais da agenda, com antecedência mínima configurável. A ordem dos serviços na tela é a que o dono definiu | <https://www.fresha.com/help-center/knowledge-base/online-presence/101646-learn-how-clients-book-appointments-online> |
| 2 | **Square Appointments** | Site de reserva **compartilhável por link**, botão "Book Now" embutível em qualquer página e rede social, escolha de profissional por serviço e **depósito com política clara no checkout** | <https://squareup.com/help/us/en/article/8096-deposits-on-square-appointments> · <https://squareup.com/us/en/appointments/features> |
| 3 | **Squire** (barbearias) | Botões de reserva em vários pontos de entrada (Google, Maps, Instagram), **lembrete e confirmação automáticos**, **reagendar antes de sair** da cadeira, depósito antecipado e assistente que tira dúvidas. Galeria e escolha de profissional: **não confirmados** na página pública | <https://getsquire.com/> |
| 4 | **Trinks** (Brasil) | Entrada por categoria e por **cidade e bairro**, promessa de marcar "a qualquer hora", depoimentos de clientes e **lembrete por SMS**. Passos do fluxo: **não confirmados** na página pública | <https://www.trinks.com/> |
| 5 | **AppBarber** (Brasil) | App da barbearia com **lembretes, histórico de atendimentos, pontos de fidelidade e lista de espera**. Detalhe das telas: **não confirmado** (só a descrição da loja de apps) | <https://apps.apple.com/app/id1602535418> |
| 6 | **Vagaro** | O estabelecimento **sobe fotos na página de reserva, associadas aos serviços**, e pode **limitar avaliações a clientes verificados**. Só visto em avaliações de usuários, não na documentação | <https://apps.apple.com/app/id346778559> |
| 7 | **Baymard Institute** | Data e horário em **hierarquia visual clara**; o botão primário é procurado no canto inferior do bloco. Detalhes só no plano pago | <https://baymard.com/mcommerce-usability/benchmark/mobile-page-types/time-booking-interface> |
| 8 | **Xola, Ralabs e Crazy Egg** (guias de reserva e checkout) | **CTA fixo e largo no rodapé** no celular (um caso reportou +4,17% de conversão); **calendário só para o dia e lista para o horário**; separar data e hora em páginas diferentes aumenta o esforço; **horário indisponível em cinza, com sugestão do próximo livre**; campos pré-preenchidos | <https://www.xola.com/articles/checkout-usability-mistakes> · <https://ralabs.org/blog/booking-ux-best-practices/> · <https://www.crazyegg.com/blog/perfect-mobile-ecommerce-checkout/> |

| 9 | **Booksy** | Perfil do negócio numa página só: **serviços com preço, disponibilidade, portfólio de fotos, avaliações e botão de reservar**; reagendar dentro da janela de aviso, e o horário original é reaberto. Descrição do próprio fornecedor, não verificada de forma independente | <https://biz.booksy.com/features/booksy-profile> · <https://biz.booksy.com/features/online-booking> |

**Limitação honesta:** os sites de barbearias premium com agenda própria **não foram consultados** (a busca foi interrompida por limite de uso); ficam como pesquisa a refazer se o Kevin quiser. As páginas dos fornecedores são material de divulgação; as lições abaixo vêm só do que foi confirmado.

### Padrões que viram requisito

| Padrão | Onde entra |
|---|---|
| CTA "Agendar horário" **sempre à mão** no celular (barra fixa) | A5, A6 |
| Serviço **com preço e duração no cartão** e botão que já leva ao agendamento | A7, A8 |
| Profissional **com foto e contato direto** | A9, A10 |
| Horários do dia em **chips**, indisponível em cinza, **próximo dia livre** sugerido | A16, A17 |
| **Resumo** da reserva sempre visível, com a política do pagamento antecipado | A18, A19 |
| **Prova de trabalho** (galeria) e de confiança (prêmios, trajetória) perto do CTA | A11 a A14 |
| Informação prática (endereço, horário, mapa em link) a um toque | A20 |

## 3. Diagnóstico do site atual (23/11/2026)

`curl` nas páginas do beta: `/`, `/servicos`, `/agendar`, `/entrar` e `/cadastro` respondem **200** em 0,2 a 0,35 s (HTML de 12 a 24 KB). O problema **não é velocidade de servidor**: é a **ordem e a divisão da informação**, visível em `frontend/src/app/page.tsx`, `SecoesDaHome.tsx`, `ListaDeServicos.tsx`, `Menu.tsx` e `telas/Agendar.tsx`.

| # | O que trava o fluxo | Evidência |
|---|---|---|
| P1 | **Home de 10 blocos em fila longa**: herói, divisor, história (com linha do tempo), prêmios, serviços, como funciona, galeria, dúvidas, alcance e rodapé. Quem quer agendar passa pela história e pelos prêmios antes de ver um preço | ordem em `app/page.tsx` |
| P2 | **"Agendar horário" só no herói e no topo**: depois de rolar, o botão some; no celular não há CTA fixo | `Menu.tsx` e herói |
| P3 | **Os serviços da home não levam ao agendamento**: são lista; `/servicos` é outra página | `ServicosDaHome` e `/servicos` |
| P4 | **Não existe bloco do barbeiro**: foto, nome e WhatsApp do Eduardo aparecem só no herói e no rodapé; não há "Agendar com o Eduardo" | `WhatsappDaHome` no herói |
| P5 | **Galeria com 3 fotos**, uma por vez, com Anterior e Próxima: pobre como portfólio, e fica depois de "Como funciona" | `GALERIA` em `conteudo/historia.ts` e `Galeria()` |
| P6 | **"A história" e "Trajetória e prêmios" são dois blocos** com nomes quase iguais | `Historia()` e `Premios()` |
| P7 | **O bloco "De onde vêm nossos clientes" não aparece**: `AlcanceDaHome` devolve `null` com lista vazia ou erro, e hoje não há cliente com região consentida | `ListaDeServicos.tsx`, `if (!alcance \|\| alcance.regioes.length === 0) return null` |
| P8 | **`/agendar` em 3 passos empilhados** (serviço, dia, horário), com o barbeiro num `<select>` e o dia no `<input type="date">`: o cliente com **um só barbeiro** ainda escolhe barbeiro, o horário só aparece depois do dia e o resumo vem no fim | `telas/Agendar.tsx` |
| P9 | **Menu com 5 itens de âncora + "Onde estamos"** que só existe no rodapé; "A história" não diz o que o bloco é | `Menu.tsx` (`SECOES_DA_HOME`) |
| P10 | **Sem serviço de química**: quem quer platinado ou descoloração não acha (o Eduardo posta esse trabalho no Instagram) | V8 (5 serviços) |

## 4. Nova arquitetura da informação

### 4.1 Home (mobile-first, uma coluna; no desktop as mesmas seções em largura de leitura)

| Ordem | Seção | Conteúdo | Vem de | O que muda |
|---|---|---|---|---|
| 1 | **Herói** | foto da fachada, "Jardim Bonança, Osasco, desde 1997", título, **"Agendar horário"** (principal), "Falar no WhatsApp" (secundário) e uma linha de fatos | herói atual | fatos viram uma linha curta; o botão "Serviços e preços" sai do herói |
| 1b | **Faixa "Como funciona"** | 3 passos de uma linha (Escolha · Pague · Confirmado), com o prazo **sem número fixo** (A19) | `ComoFunciona` | **junta** ao herói como faixa; o bloco grande sai |
| 2 | **Serviços** | cartões com nome, **duração**, **preço** e "Agendar este serviço" (leva a `/agendar?servicoId=...`); link "Ver todos" para `/servicos` | `ServicosDaHome` | cartões com ação; **sobe da 5ª para a 2ª** |
| 3 | **Barbeiro** | foto, nome profissional, **WhatsApp do barbeiro** e "Agendar com Eduardo" | herói e `WhatsappDaHome` | **seção nova**; dados de `GET /barbeiros` |
| 4 | **Galeria** | grade de 18 fotos (E3), toque abre a foto inteira com legenda, teclado completo | `Galeria` (3 fotos, uma por vez) | **cresce e sobe** (de 7ª para 4ª); vira grade |
| 5 | **Trajetória** | texto, "Ouvir a trajetória", linha do tempo e os dois prêmios, num bloco só | `Historia` e `Premios` | **junta e renomeia** (E1) |
| 6 | **De onde vêm nossos clientes** | municípios em faixas **quando houver dados**; senão, **estado vazio digno** (A15) | `AlcanceDaHome` | passa a ter sempre presença visível |
| 7 | **Dúvidas** | perguntas em `<details>` | `Duvidas` | sem mudança de conteúdo |
| 8 | **Onde estamos** (rodapé) | endereço, terça a sábado, **link "Abrir no mapa"**, WhatsApp, redes | `Rodape` | ganha o link de mapa (E7) |
| fixo | **Barra "Agendar horário"** | só no celular, largura total | novo | **novo** (E8); some em `/agendar/*`, `/entrar`, `/cadastro` e `/painel/*` |

**Saem:** o divisor xadrez entre o herói e a história (vira detalhe dentro do herói), o bloco `Premios` separado e o `ComoFunciona` grande. **Juntam:** história com prêmios (Trajetória) e como funciona com o herói. **Mudam de ordem:** serviços (5ª para 2ª), galeria (7ª para 4ª) e alcance (depois da prova social, antes das dúvidas).

**Menu:** Serviços · Barbeiro · Galeria · **Trajetória** · Dúvidas · Onde estamos, mais o botão "Agendar horário". "Como funciona" sai do menu (virou faixa). A âncora é `#trajetoria`; a antiga `#historia` redireciona para ela (E1).

### 4.2 Fluxo de agendar (`/agendar`, mobile-first)

Mantém as rotas e a lógica (`/agendar` → `/agendar/dados` → `/agendar/pagamento/{id}` → `/agendar/confirmacao/{id}`). Muda **a ordem e o peso** dos controles da primeira tela:

1. **Serviço**: cartões de escolha única; o que veio de `?servicoId=` já vem marcado.
2. **Barbeiro**: **some** quando só há um barbeiro ativo (hoje é um `<select>`); com mais de um, vira cartões com foto.
3. **Dia**: tira de **7 dias** na horizontal (chips "ter 25"), com o próximo dia que tem horário já selecionado; o seletor nativo fica como "outro dia" (até 30 dias, regra existente).
4. **Horário**: **chips** do dia, indisponíveis em cinza com `aria-disabled`, e "ver o próximo dia com horário" (A16, A17).
5. **Resumo fixo** no rodapé da tela (serviço, dia e hora, preço) com **"Continuar"**, habilitado só com serviço e horário (A18).

Depois: `/agendar/dados` (as **3 formas** continuam: entrar, cadastrar ou seguir sem cadastro, e "Prefere combinar pelo WhatsApp?" da 6D), `/agendar/pagamento/{id}` (cronômetro com `expiraEm`, Pix e cartão) e `/agendar/confirmacao/{id}`. **Nada disso muda de comportamento.**

## 5. Decisões de desenho

| # | Pergunta | Decisão | Por quê |
|---|---|---|---|
| E1 | Como renomear "A história" para **Trajetória**? | Menu, sobretítulo, `<h2>` e âncora passam a "Trajetória" e `#trajetoria`. O título do bloco continua "Quase três décadas na mesma cadeira". A âncora antiga `#historia` continua funcionando (redireciona) para não quebrar link já divulgado. A "Linha do tempo" fica dentro da Trajetória | pedido do Kevin; os dois blocos viram um só |
| E2 | Quais fotos vão para a galeria? | As dos Instagrams do Eduardo e da barbearia, **selecionadas** (E3). Vão para `docs/assets/galeria/` com nomes descritivos, **sem renomear nem apagar** o que já existe ali (`corte-degrade-com-desenho.jpg`, `corte-topete-parede-do-logo.jpg`, `eduardo-em-atendimento.jpg`). Cada foto tem **texto alternativo** próprio em `frontend/src/conteudo/historia.ts` | preserva o que existe; texto alternativo é requisito de acessibilidade (WCAG 1.1.1) |
| E3 | Qual a seleção? | **15 fotos novas** (18 com as 3 atuais), tabela 5.1. Ficam de fora: posts só de texto ou de humor, vídeos-capa, propaganda, aulas, selfies sem corte e o troféu (já está nos prêmios) | a galeria mostra **trabalho**, não anúncio |
| E4 | Como exibir? | Grade de miniaturas (3 colunas no celular, 4 no desktop) com `object-fit: contain` (**foto inteira**, sem corte) sobre o cinza do token. Ao tocar, abre a foto inteira num `<dialog>` (padrão do `Dialogo.tsx`) com legenda, setas, `Esc` e foco devolvido. Tudo pelo **`next/image`**, com `width`/`height` reais, `sizes`, `loading="lazy"` fora da 1ª linha e AVIF/WebP pela otimização da Vercel | sem corte, sem salto de layout (CLS), peso baixo |
| E5 | Como entra o serviço novo? | Migração **nova**: `V9` se a Fatia 10 ainda não tiver mergeado a dela, senão a próxima livre (**implementada como `V10`**, porque a `V9` é da Fatia 10: D156). **Hoje a `develop` termina na `V8__catalogo_inicial.sql`; a Fatia 10 prevê a `V9` (convite) e a 11 pode usar outra: conferir na implementação o número livre** (`ls backend/src/main/resources/db/migration`), sem editar migração já mergeada. O serviço: **"Descoloração / Platinado (química)"**, **60 min**, **R$ 80,00** (`preco_centavos = 8000`), no molde da V8 (id fixo, `ON CONFLICT DO NOTHING`, idempotente, sem dado pessoal). O admin continua dono do catálogo | **preço definido pelo Kevin em 23/11/2026**; D152 já estabeleceu catálogo por migração idempotente |
| E6 | E a autorização de imagem? | Registrar: **"o Kevin confirmou (23/11/2026) que o Eduardo tem a autorização de uso de imagem dos clientes das fotos"**. Vai para `docs/planejamento/decisoes.md` (número seguinte ao último, **conferir na implementação**). O cliente que pedir para sair da galeria é atendido (D-5) | LGPD (imagem é dado pessoal) e rastreabilidade |
| E7 | Mapa e horários? | **Sem iframe de mapa**: link "Abrir no mapa" (Google Maps, mesmo endereço do rodapé) em nova aba com `rel="noopener noreferrer"`, e o horário de terça a sábado em texto | iframe puxa terceiros (peso, cookies, LGPD) e piora o Lighthouse |
| E8 | CTA fixo no celular | Barra "Agendar horário" **fixa no rodapé** abaixo de 768 px, com `padding-bottom: env(safe-area-inset-bottom)`, altura reservada no layout para **não gerar CLS** e sem cobrir o rodapé nem o botão de acessibilidade | padrão da pesquisa (itens 7 e 8) |
| E9 | Avaliações | **Sem nota nem depoimento inventado.** Entra uma linha com **link para as avaliações públicas** só se o Kevin passar a URL; sem URL, o bloco não existe | honestidade do projeto; o Vagaro limita a clientes verificados |
| E10 | Alcance na home | O bloco **sempre existe**. Com dados que passam no k, mostra os municípios em faixas (11C). **Sem dados**, mostra o estado vazio "Em breve: de onde vêm nossos clientes" e a frase "Mostramos a região quando há clientes suficientes para ninguém ser identificado". Sem botão, sem gráfico vazio, sem "0" | hoje o bloco devolve `null` (P7); ver a seção 6 |
| E11 | O que não muda no pagamento | `POST /agendamentos` e `/guest` continuam como estão; o prazo **só aparece com número** onde existe `expiraEm` (tela de pagamento, `Cronometro.tsx`). Antes da reserva o texto diz "você terá alguns minutos para pagar", **sem número** | D121: o prazo é configurável de 3 a 60 min; prometer "3 minutos" na home seria falso quando mudar |
| E12 | Quem manda no visual? | **O canvas (D148).** Esta spec fixa ordem, conteúdo, comportamento e critérios. O visual dos blocos novos vem do **desenho da sessão de design** (seção 8). Até lá, usar só componentes e tokens que já existem | evita decidir visual aqui |

### 5.1 Galeria: seleção e texto alternativo (proposta; o alt final é escrito vendo a foto, na 12A)

Origem: pasta `fotos-ig` (32 arquivos e `indice.txt`). Destino: `docs/assets/galeria/`. Cada linha foi conferida olhando a foto.

| Origem | Nome novo | Alt (rascunho) | Legenda |
|---|---|---|---|
| `ig-altashoras-01.jpg` | `corte-social-grisalho-na-regua.jpg` | Eduardo finalizando um corte social em cliente grisalho, em frente ao espelho do salão | Social na régua |
| `ig-altashoras-02.jpg` | `corte-infantil-cacheado-capa-altas-horas.jpg` | Menino de cabelo cacheado, com o Eduardo atrás, na capa preta da barbearia | Corte infantil |
| `ig-altashoras-03.jpg` | `corte-franja-curta-capa-altas-horas.jpg` | Cliente com franja curta e laterais baixas, de capa com o logo, e o Eduardo atrás | Franja e laterais |
| `ig-altashoras-04.jpg` | `platinado-cliente-sorrindo.jpg` | Cliente sorrindo, de cabelo platinado e óculos, com o Eduardo atrás | Platinado |
| `ig-altashoras-05.jpg` | `corte-infantil-risca-lateral.jpg` | Menino de cabelo liso com risca lateral e o Eduardo penteando | Corte infantil |
| `ig-eduardo-04.jpg` | `corte-cacheado-volume.jpg` | Cliente de cabelo cacheado volumoso, com o Eduardo ao lado | Volume e definição |
| `ig-eduardo-05.jpg` | `platinado-cliente-e-eduardo.jpg` | Eduardo e uma cliente de cabelo platinado, ambos sorrindo | Descoloração e platinado |
| `ig-eduardo-06.jpg` | `corte-degrade-cacheado-jovem.jpg` | Jovem de cabelo cacheado com degradê, com o Eduardo atrás | Degradê cacheado |
| `ig-eduardo-08.jpg` | `fade-perfil-capa-altas-horas.jpg` | Cliente de perfil com degradê baixo, de capa com o logo da barbearia | Fade de perfil |
| `ig-eduardo-09.jpg` | `barba-cheia-desenhada.jpg` | Cliente de barba cheia e desenhada, de frente | Barba desenhada |
| `ig-eduardo-11.jpg` | `degrade-parede-do-logo.jpg` | Cliente de perfil com topete e degradê, ao lado do logo na parede | Degradê e logo |
| `ig-eduardo-12.jpg` | `corte-social-cliente-na-cadeira.jpg` | Eduardo alinhando o cabelo de um cliente sentado, em corte social | Alinhamento |
| `ig-eduardo-13.jpg` | `franja-reta-alinhada.jpg` | Cliente com franja reta e corte alinhado, em frente ao logo | Franja alinhada |
| `ig-eduardo-14.jpg` | `salao-vitrine-com-logo.jpg` | Vitrine de vidro da barbearia com o logo "AA 1997" em dois círculos xadrez | O salão |
| `ig-eduardo-20.jpg` | `degrade-grisalho-penteado-para-tras.jpg` | Cliente grisalho de perfil, cabelo penteado para trás, com degradê | Degradê grisalho |

Ficaram de fora: `altashoras-06 a 08` e `eduardo-02, 03, 07, 22` (texto e humor), `eduardo-01` e `-10` (texto sobre a imagem), `eduardo-15` (selfie), `eduardo-16` (propaganda), `eduardo-17` e `-18` (aulas), `eduardo-19` (vídeo-capa), `eduardo-21` (troféu, já nos prêmios), `eduardo-23` (evento com público ao fundo) e `eduardo-24` e `-14` (ver D-2). Fotos infantis e de pessoas de fundo seguem a confirmação do E6 (D-5).

## 6. Alcance: por que o Kevin "não viu os indicativos de clientes e regiões"

- **O bloco existe, mas só aparece com dados de verdade**: `AlcanceDaHome` devolve `null` quando a lista é vazia (P7). Hoje não há cliente com região **consentida** e **sem CEP guardado**: isso é a [Fatia 11A](fatia-11-alcance-por-nivel.md) (consentimento), ainda **por implementar**.
- **A regra de k = 5 do [ADR 0014](../adr/0014-alcance-k-anonimato-e-consentimento-do-cep.md)** impede mostrar um lugar com menos de 5 **pessoas**: "1 cliente de Carapicuíba" identificaria alguém. Num beta recém-lançado, quase nada passa no k. Isso é **proteção, não defeito**.
- **O painel por estado, município e região** (Dono/Admin e barbeiro) é a 11D.

**Proposta para o layout desde já (E10):** o bloco ganha **sempre uma presença visível**. Com dados, a lista em faixas (11C); sem dados, o estado vazio "Em breve: de onde vêm nossos clientes". Assim o Kevin enxerga o **lugar** do indicativo na home e o desenho já prevê os dois estados. Nada de número falso nem dado de seed em produção.

## 7. Critérios de aceite

Vitest com Testing Library (nome do critério no `it`), Playwright (E2E), axe e Lighthouse móvel no CI. Os critérios de métrica rodam contra o **build de produção** (`next build` e `next start`) com a API simulada dos E2E atuais.

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | a home | abro a página | as seções aparecem **nesta ordem**: herói, serviços, barbeiro, galeria, trajetória, alcance, dúvidas, onde estamos; `#como-funciona` e `#premios` **não existem** como seções | `home-ordem` (Vitest, `A1:`) |
| A2 | o menu | leio os itens | existem **Serviços, Barbeiro, Galeria, Trajetória, Dúvidas, Onde estamos**; **não existe** "A história" nem "Como funciona" | `navegacao` (Vitest, `A2:`) |
| A3 | a home | procuro `id="trajetoria"` | existe, com `<h2>` ligado por `aria-labelledby`; **não existe** `id="historia"`; o menu aponta para `/#trajetoria` | `home-historia` (Vitest, `A3:`) |
| A4 | o link antigo `/#historia` | a página abre com esse hash | a janela vai para a Trajetória | `home-fluida` (E2E, `A4:`) |
| A5 | o celular (375 px), em qualquer ponto da home | rolo até o rodapé | a barra "Agendar horário" está **visível e clicável** e leva a `/agendar`; em tela ≥ 1024 px ela **não existe** | `home-fluida` (E2E, `A5:`) |
| A6 | `/agendar`, `/agendar/dados`, `/entrar`, `/cadastro` e `/painel` | abro no celular | **não há** barra fixa de CTA | `navegacao` (Vitest, `A6:`) |
| A7 | a lista de serviços da API (6, com o novo) | abro a home | há um cartão por serviço com **nome, duração em minutos e preço em reais** | `servicos-home` (Vitest, `A7:`) |
| A8 | o cartão "Descoloração / Platinado (química)" | toco "Agendar este serviço" | vou a `/agendar?servicoId=<id>` com **esse serviço já marcado** | `home-fluida` (E2E, `A8:`) |
| A9 | a API com um barbeiro com foto e WhatsApp | abro a home | o bloco "Barbeiro" mostra nome, foto com texto alternativo e botão do WhatsApp (`wa.me/55…`, **só dígitos**, D122); **sem WhatsApp, sem botão** | `barbeiro-home` (Vitest, `A9:`) |
| A10 | o botão "Agendar com Eduardo" | toco | vou a `/agendar?barbeiroId=<id>` com o barbeiro marcado | `barbeiro-home` (Vitest, `A10:`) |
| A11 | a galeria | abro a home | há **18 fotos** (3 atuais e 15 novas), **todas com `alt` não vazio** e todas com arquivo existente na pasta de imagens | `galeria` (Vitest, `A11:`) |
| A12 | cada foto da galeria | inspeciono | usa `object-fit: contain` (foto inteira) e é renderizada pelo `next/image` (`srcset` e `sizes`; `loading="lazy"` fora da 1ª linha) | `galeria` (Vitest, `A12:`) |
| A13 | uma miniatura | toco ou aperto `Enter` | abre a foto inteira com legenda; `←` e `→` trocam; `Esc` fecha; o foco **volta** à miniatura | `galeria` (Vitest, `A13:`) |
| A14 | a galeria | rodo o axe | **0 violações** (nome acessível, contraste, foco) | `site-fluido-axe` (E2E com axe, `A14:`) |
| A15 | `GET /alcance` devolve lista vazia (ou erro) | abro a home | o bloco "De onde vêm nossos clientes" **aparece** com o estado vazio "Em breve…", **sem número** e sem botão; com dados, mostra as faixas | `home-alcance` (Vitest, `A15:`) |
| A16 | `/agendar` com serviço escolhido e disponibilidade da API | escolho um dia | os horários aparecem como **chips**; os **indisponíveis** em cinza com `aria-disabled="true"` | `agendar-horarios` (Vitest, `A16:`) |
| A17 | um dia **sem** horário livre | escolho esse dia | aparece "Sem horários neste dia" e o botão "Ver o próximo dia com horário", que seleciona o próximo dia livre | `agendar-horarios` (Vitest, `A17:`) |
| A18 | serviço e horário escolhidos | olho o rodapé da tela | o **resumo fixo** mostra serviço, dia e hora e preço, e "Continuar" fica habilitado; **sem horário, desabilitado** | `agendar-resumo` (Vitest, `A18:`) |
| A19 | a home e o resumo, antes da reserva | leio o texto do pagamento | diz que há **um prazo curto**, **sem número fixo**; o número vem **só** do `expiraEm` da tela de pagamento (o teste muda o prazo de 3 para 10 min e a tela mostra 10) | `agendar-resumo` e `pagamento-prazo` (Vitest, `A19:`) |
| A20 | o rodapé | leio "Onde estamos" | há endereço, horário de terça a sábado e o link "Abrir no mapa" com `target="_blank"` e `rel="noopener noreferrer"`; **sem `<iframe>`** | `navegacao` (Vitest, `A20:`) |
| A21 | o banco na subida | a migração nova roda | existe "Descoloração / Platinado (química)", 60 min, `preco_centavos = 8000`; rodar de novo **não duplica**; serviço de mesmo nome criado pelo admin **não é sobrescrito** | `V10ServicoDescoloracaoPlatinadoIT` (IT da migração, `A21:`, no molde do V8CatalogoInicialIT) |
| A22 | o fluxo de reserva | vou da home à confirmação com a API simulada | as **3 formas** continuam em `/agendar/dados` (entrar, cadastrar, sem cadastro) mais o WhatsApp com reserva manual; pagamento e prazo (`expiraEm`) **funcionam como hoje** | `agendar-fluido` (E2E, `A22:`, reaproveitando os E2E atuais) |
| A23 | a home no celular (375 px) | conto os toques até o formulário de dados | **no máximo 4**: "Agendar horário" → serviço → horário (chip) → "Continuar" (sem contar digitação; o próximo dia livre e, com um só barbeiro, o barbeiro já vêm escolhidos) | `agendar-fluido` (E2E, `A23:`) |
| A24 | a home e `/agendar` no celular | rodo o **Lighthouse móvel** (perfil mobile, 4× CPU, Slow 4G) no build de produção | **Desempenho ≥ 90**, **Acessibilidade ≥ 95**, **LCP ≤ 2,5 s**, **CLS ≤ 0,1**, **TBT ≤ 200 ms** | `lighthouse-ci` (Vitest sobre o orçamento em lighthouserc.json, `A24:`; a medição roda no CI e fica registrada na PR) |
| A25 | `/`, `/servicos`, `/agendar` e `/agendar/dados` | rodo o axe | **0 violações**; ordem de foco coerente; barra fixa e botão de acessibilidade **não se sobrepõem** | `site-fluido-axe` (E2E com axe, `A25:`) |
| A26 | a home no celular | somo o peso de imagens baixado antes de rolar | a home **não baixa** as 15 fotos novas de uma vez (só as miniaturas visíveis); peso inicial de imagens **≤ 600 KB** | `home-fluida` (E2E com page.on('response'), `A26:`) |
| A27 | o CSS das seções novas | rodo o teste de tokens | só cores de `tokens.json`; **nenhuma cor nova** | `tokens` (Vitest, `A27:`) |

## 8. Telas para a sessão de design (o Kevin repassa)

Em **390 × 844** (celular) e **1280 × 800** (desktop), nos temas escuro e claro, **sempre com estados**:

1. **Home inteira, na nova ordem** (herói com faixa "Como funciona", serviços, barbeiro, galeria, trajetória, alcance, dúvidas, onde estamos).
2. **Barra fixa "Agendar horário"** (celular), sobre o rodapé e o botão de acessibilidade.
3. **Cartão de serviço** (nome, duração, preço, "Agendar este serviço"), com nome longo (o novo).
4. **Cartão do barbeiro** (foto, nome, WhatsApp, "Agendar com Eduardo"), com e sem WhatsApp.
5. **Galeria**: grade de 18 fotos com `contain`, visualizador da foto inteira e foto carregando.
6. **Trajetória**: bloco único (texto, "Ouvir a trajetória", linha do tempo, os 2 prêmios).
7. **Alcance**: com dados (faixas) e **estado vazio** "Em breve: de onde vêm nossos clientes".
8. **Onde estamos**: endereço, horário e "Abrir no mapa".
9. **`/agendar`**: serviço, tira de 7 dias, chips de horário (livre, selecionado, indisponível, "sem horários neste dia") e **resumo fixo** com "Continuar".
10. **Erros**: API fora (serviços, barbeiro, horários) e foto que não carrega.

## 9. Entregas

- **12A**: 15 fotos em `docs/assets/galeria/`, `GALERIA` em `frontend/src/conteudo/historia.ts`, `Galeria()` em grade com visualizador, migração do serviço, `Historia` e `Premios` viram `Trajetoria`, menu, âncora e redirecionamento de `#historia`, decisões novas em `decisoes.md`.
- **12B**: `app/page.tsx` na nova ordem, `BarbeiroDaHome`, cartões de serviço com ação, faixa "Como funciona", estado vazio do alcance, barra fixa, link do mapa, `lighthouserc.json`.
- **12C**: `telas/Agendar.tsx` (tira de dias, chips, resumo fixo, barbeiro oculto com um só), E2E de toques, métricas e axe.

## 10. Riscos e cuidados

| Risco | Cuidado |
|---|---|
| Imagem de cliente é **dado pessoal** (LGPD) | E6 e D-5: autorização confirmada pelo Kevin em 23/11; canal de remoção; nenhum nome de cliente na legenda |
| Galeria pesada derruba o Lighthouse | `next/image` com `sizes`, lazy e AVIF/WebP; A26 mede o peso |
| Barra fixa gera CLS ou cobre conteúdo | altura reservada (E8); A24 mede o CLS; A25 confere a sobreposição |
| Número da migração colide com a Fatia 10 ou 11 | E5: conferir na implementação; nunca editar migração mergeada |
| Mudar âncoras quebra links divulgados | E1 e A4: `#historia` redireciona |
| Prometer prazo de pagamento fixo | A19: só o `expiraEm` fala número |
| Decidir visual sem desenho | E12 e D148: só tokens e componentes existentes até o desenho chegar |
| Pesquisa incompleta (Booksy, sites premium) | declarada na seção 2; refazer se o Kevin pedir |

## 11. Dúvidas (D-#), com proposta

| # | Dúvida | Proposta |
|---|---|---|
| D-1 | A ordem da home (4.1) está certa? Em especial: serviços antes da galeria e a Trajetória só em 5º | manter; é a ordem em que o cliente decide (agendar, preço, quem faz, prova, confiança) |
| D-2 | Quais fotos? São 15 novas e 3 atuais. Quer incluir `ig-eduardo-24` (corte colorido) ou `ig-eduardo-23` (evento)? | aprovar as 15; o Kevin diz o que entra ou sai antes da 12A |
| D-3 | **Avaliações**: o Kevin tem o link do Google Meu Negócio ou outro lugar com avaliações reais? | sem link, sem bloco (E9); nada inventado |
| D-4 | **Mapa**: link "Abrir no mapa" ou iframe? | link; iframe só se o Kevin aceitar o peso e os cookies de terceiros |
| D-5 | **Remoção de foto sob pedido** e **fotos de crianças** (`altashoras-02` e `-05`): o Kevin confirmou a autorização dos clientes; vale também para os responsáveis dos menores? | pedido pelo WhatsApp do Eduardo, foto removida em até 48 h; as duas fotos infantis só entram se o Kevin confirmar a autorização dos responsáveis |
| D-6 | **Duração do platinado**: 60 min como definido, mas química costuma levar mais | 60 min agora (D94: todos os serviços valem 60); o Eduardo ajusta no painel |
| D-7 | **Barra fixa** só no celular (proposto) ou também no tablet? | só abaixo de 768 px |
| D-8 | **Barbeiro único**: ocultar o seletor quando só há um ativo | sim; volta como cartões com 2 ou mais (Fatia 10) |
| D-9 | **Texto do estado vazio do alcance** | aprovar; o texto final vem do desenho |
| D-10 | **Foto do barbeiro**: usar `eduardo-candido.jpg` ou o avatar que já estão em `docs/assets` | sim |
| D-11 | **Como funciona** vira faixa no herói ou continua bloco? | faixa; o bloco grande some |
| D-12 | **Entrega** em três PRs (12A, 12B, 12C) ou uma só? | três, nessa ordem; a 12A não depende do desenho novo |

## 12. Pronto quando

- [ ] todos os critérios A1 a A27 com teste verde (Vitest, Playwright, axe, Lighthouse CI e o IT da migração)
- [ ] `scripts/conferir-spec.sh 12 --exigir` passa
- [ ] Lighthouse móvel: Desempenho ≥ 90, Acessibilidade ≥ 95, CLS ≤ 0,1 (saída real colada na PR)
- [ ] decisões novas em `decisoes.md` (preço do platinado, autorização de imagem, ordem da home)
- [ ] [checklist da PR](checklist-pr.md) completo; `docs/sdd/README.md` atualizado
