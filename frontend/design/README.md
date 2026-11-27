# Barbearia Altas Horas

A identidade visual da **Barbearia Altas Horas**, de Eduardo Candido, no Jardim Bonança, em Osasco, desde 1997. Este design system é a fonte da verdade visual da agenda online e do site (Next.js, Fatia 7 do projeto). Ele foi tirado do repositório [`k3vinrich4rd/barbearia-altas-horas`](https://github.com/k3vinrich4rd/barbearia-altas-horas) (`main@3f259ff`): os grafismos de `docs/assets/`, o logo, as fotos e o README. A barbearia real (fachada, adesivos no vidro, parede xadrez) serviu de referência.

> **Para quem vai implementar (pessoa ou IA):** leia este arquivo inteiro antes de escrever qualquer CSS. Use os tokens pelo nome (`var(--acao-fundo)`), nunca o valor solto. O que estiver marcado **PROPOSTA** ainda não está no código e pode mudar.

> **Quem decide o visual.** Este design system e o canvas de telas são a **única** fonte de decisão visual do projeto: tokens, tipografia, componentes, estados e telas. A sessão de backend e o código do front só **leem** daqui. O `tokens.css` do front é gerado a partir do `tokens.json` deste sistema; para mudar um token, mude o `tokens.json`. Cada tela do front recebe o acabamento visual quando a tela correspondente estiver marcada como "desenhada" na tabela "Telas da Fatia 7".

## A marca em uma frase

Preto e branco, xadrez e serifa: o adesivo redondo do vidro, a parede xadrez atrás da cadeira, o selo **★ 19 ✂ 97 ★**. É uma barbearia de bairro com quase 30 anos e dois prêmios, sem enfeite. O site deve parecer a própria barbearia, não um aplicativo genérico.

## Conteúdo e voz

- **Quem fala.** A história é contada pelo Kevin, filho do Eduardo, em primeira pessoa quando é sobre o projeto ("Como desenvolvedor, criei esta agenda…"). Quando é sobre a barbearia, a referência é o Eduardo, pelo nome.
- **Tom.** Direto, caloroso, de bairro. Frases curtas e concretas: "cadeira vazia é tempo perdido", "horário garantido, pago antes". Nada de jargão de startup ("revolucione", "experiência única").
- **Números reais.** Datas, prêmios, horários e preços saem do código ou do README, nunca de estimativa: desde **1997**, dois prêmios em **2017**, terça a sábado das **7h às 20h**, almoço das **12h às 13h**.
- **O prazo para pagar vem da API** (`expiraEm`; hoje são 3 minutos). Nunca escreva os minutos fixos no layout: mostre o contador e, em textos de ajuda, "no prazo mostrado na tela".
- **Grade e duração são coisas diferentes.** A **grade** da agenda é de 30 em 30 min: são os horários de início possíveis. A **duração** de todos os serviços é **60 min**, então cada atendimento ocupa **2 casas** da grade, e a terça tem **22 horários** de início. Escreva "horários a cada 30 min" para a grade e "60 min" para o serviço; nunca "serviço de 30 min".
- **Nomes dos serviços** como estão no catálogo: Corte Clássico / Social, Corte Degradê / Fade / Navalhado, Corte Moderno / Visagismo (tesoura), Barba Completa / Desenhada, Acabamento (pezinho) / Sobrancelha, todos com 60 min. Preço sempre em `R$ 45,00` (com vírgula e espaço).
- **Caixa alta** só em rótulos curtos (dias da semana, "AGENDA · ALTAS HORAS", botões). Títulos e frases vão em caixa normal.
- **Português do Brasil**, com acentos ("Clássico", "Bonança", "degradê").
- **Emoji:** o README do GitHub usa emoji (✂️ 💳 🔒), mas **a interface não usa**. No site, os únicos símbolos são os do logo: ★ e ✂.

## Fundamentos visuais

- **Duas cores.** A marca é `preto` (#0A0A0A) e `branco`. Os cinzas (`cinza-950` a `cinza-100`) só dão profundidade e hierarquia; nunca viram cor de marca. **Não existe cor de destaque.** O destaque é a inversão: um bloco branco sobre o preto (`acao-fundo`), como uma casa clara do xadrez.
- **Tema escuro primeiro.** "Altas Horas" e todos os grafismos do repositório (banner, divisor, agenda) são preto com branco, então o tema `escuro` é o principal. O tema `claro` é a mesma marca invertida, igual ao interior branco da barbearia com o logo preto.
- **O xadrez é o padrão gráfico.** Vem da parede atrás da cadeira, no salão e aparece como faixa no topo do banner (casas de `xadrez-14`), do divisor (`xadrez-12`) e da agenda (`xadrez-10`). Use em faixas finas, de uma casa de altura, ou em blocos recortados. **Nunca** como fundo atrás de texto.
- **Bordas, não sombras.** O código não tem nenhuma sombra. A separação vem de `superficie` sobre `fundo`, de `borda` (decorativa) e de `borda-forte` (controles).
- **Cantos pequenos.** `radius-sm` (4px) nos slots e etiquetas, `radius-md` (10px) em cartões, `radius-lg` (14px) e `radius-xl` (18px) em peças grandes. Nada muito arredondado, a não ser a pílula do selo.
- **Fotografia real.** Só fotos da barbearia, do Eduardo e de cortes feitos lá (grupos Fotos e Prêmios). Nada de banco de imagens. Aceitamos o enquadramento simples, de celular: é autêntico.
- **Foto de pessoa aparece inteira.** Retratos, fotos de prêmio e fotos de atendimento nunca são recortadas para caber num formato: use `object-fit: contain` (ou a proporção original da foto), com `superficie` nas sobras. Cortar a cabeça ou o rosto de alguém é proibido. `object-fit: cover` só vale para fotos sem pessoas, como a fachada, e mesmo assim sem perder o assunto.

## Cor

| Token | Escuro | Claro | Uso |
| --- | --- | --- | --- |
| `fundo` | preto | branco | fundo da página |
| `superficie` | cinza-950 | cinza-100 | cartões, células, painéis |
| `texto` | branco | preto | texto principal |
| `texto-suave` | cinza-200 | cinza-700 | subtítulos, legendas, descrições |
| `texto-apagado` | cinza-500 | cinza-600 | notas pequenas, **só sobre `fundo`** |
| `borda` | cinza-700 | cinza-200 | divisórias decorativas |
| `borda-forte` | cinza-500 | cinza-500 | bordas de campos e botões secundários |
| `acao-fundo` / `acao-texto` | branco / preto | preto / branco | botão principal |
| `foco` | branco | preto | anel de foco |

Regras:
- **Um botão principal por tela**, em `acao-fundo` com `acao-texto`. O secundário é contorno em `borda-forte` com texto em `texto`.
- **Contraste.** Todos os pares de texto acima passam de 4,5:1 nos dois temas. A exceção é `texto-apagado` sobre `superficie` no escuro, que fica em 4,3:1. Esse par vem da agenda-semana.svg ("horários ilustrativos" em #7A7A7A sobre #141414); foi mantido, mas use `texto-suave` dentro de cartões. `borda` não tem 3:1: nunca seja a única borda de um controle.
- **Foco visível sempre:** contorno de 3px em `foco`, afastado 3px, em todo link, botão, campo e `summary` (WCAG 2.2, 2.4.7 e 2.4.13).
- **`tinta-logo`** (#1E1D25) é a tinta amostrada da placa do logo. Use só para reproduzir o selo impresso. Na tela, o logo é a imagem, e o resto usa `preto`.

## Tipografia

- **Títulos em serifa.** O código usa `Georgia` (família `serif`) nos títulos do banner e da agenda. Para o site, a família `display` é a **Playfair Display** (Google Fonts, peso 700, **decidida em 18/11/2026**) em `titulo-hero` (64px), `titulo-secao` (40px) e `contador` (72px), com Georgia como reserva. Carregue com `next/font/google` e `display: swap`.
- **Texto em sans.** `Helvetica Neue`, Helvetica, Arial (família `sans`), como no banner e na agenda.
- **Estilos do código:** `titulo-banner` (30px, espaçamento 2), `titulo-agenda` (30px negrito, CAIXA ALTA, espaçamento 3), `selo` (14px serifa, espaçamento 3), `subtitulo` (19px), `corpo` e `rotulo` (16px; o rótulo em negrito, CAIXA ALTA, espaçamento 3), `legenda` (15px), `nota` (13px), `micro` (11px negrito, só dentro de slots).
- Linha de texto com no máximo cerca de 70 caracteres. Números de horário e preço não quebram linha.

## Espaço e forma

- **Espaçamento:** escala de 4px **PROPOSTA** (`space-1` 4px a `space-16` 64px), porque o código não define uma. Seções separadas por `space-12` (celular) e `space-16` (desktop); padding de cartão `space-4`; entre cartões `space-6`.
- **Traços:** `traco-1` para contornos, `traco-2` para linhas de base e foco, `traco-3` para as listras do almoço.

## Padrões da agenda (de `agenda-semana.svg`)

A agenda é o coração do produto. O estado de cada horário tem uma forma, não uma cor:

| Estado | Forma | Tokens |
| --- | --- | --- |
| Livre | célula vazia em `superficie`, contorno `borda`, linha tracejada (3 5) no meio | `superficie`, `borda` |
| Confirmado (pago) | bloco sólido arredondado, ocupando as 2 casas do serviço, texto `micro` | `status-confirmado`, `status-confirmado-texto`, `radius-sm` |
| Aguardando pagamento | só contorno tracejado (5 4), texto na mesma cor; no painel, com o tempo que falta ("2 min") | `status-aguardando`, `radius-sm` |
| Almoço (12h–13h) | listras diagonais a 45°, rótulo em CAIXA ALTA espaçada | `almoco-fundo`, `almoco-listra`, `almoco-texto` |
| Fechado (domingo e segunda) | o dia não aparece na grade | — |
| Concluído (painel) | bloco sólido mais baixo que o confirmado, ícone de visto, rótulo "concluído" | `status-concluido`, `status-concluido-texto` |
| Não compareceu (painel) | contorno **sólido** de 2px, ícone ✕, rótulo "não compareceu" | `status-nao-compareceu` |
| Cancelado (painel) | texto **riscado**, contorno de 1px, motivo na segunda linha ("Cancelado: barbeiro doente") | `status-cancelado`, `borda-forte` |
| Expirado (painel) | contorno **pontilhado** (2 3), ícone de relógio, rótulo "expirado" | `status-expirado`, `borda-forte` |

Os sete estados da reserva (`AGUARDANDO_PAGAMENTO`, `CONFIRMADO`, `CONCLUIDO`, `NAO_COMPARECEU`, `CANCELADO`, `EXPIRADO`, mais o horário livre) se distinguem por **preenchimento × contorno sólido × tracejado × pontilhado × riscado**, sempre com o rótulo escrito. O cliente só vê livre, aguardando e confirmado; os outros quatro aparecem no painel e em "Meus agendamentos".

**Origem da reserva (painel).** A reserva paga pelo app e a reserva manual (combinada pelo WhatsApp e lançada pelo barbeiro, já confirmada e sem cobrança) têm o mesmo bloco de confirmado, mas a manual leva uma **etiqueta** contornada dentro do bloco, com ícone de balão de conversa e a palavra **"MANUAL"**, e mostra a observação na segunda linha. A paga pelo app leva a etiqueta **"PAGO"**. Nunca só cor: a diferença é a etiqueta escrita.

Cabeçalho dos dias: blocos brancos com o dia em `rotulo` preto. Horas à esquerda em `corpo`. A legenda fica sob a grade, em `legenda`. Como o estado nunca depende só da cor, a agenda funciona para daltônicos e em tela monocromática.

## Mensagens: erro, aviso e sucesso

A marca não tem vermelho nem verde. Cada tipo de mensagem tem **forma, ícone e palavra** próprios, nunca só cor:

| Tipo | Forma | Ícone | Leitor de tela | Tokens |
| --- | --- | --- | --- | --- |
| Erro de campo | contorno do campo passa a 2px; texto embaixo do campo começando por "Erro:" | alerta (triângulo com !) | campo com `aria-invalid="true"` e `aria-describedby` apontando para o texto | `erro` |
| Erro da página | caixa com contorno de 2px no topo do formulário, que recebe o foco | alerta | `role="alert"` | `erro`, `erro-fundo` |
| Aviso | caixa com contorno tracejado de 1px | informação (círculo com i) | `role="status"` | `aviso` |
| Sucesso | bloco invertido, como o botão principal | visto | `role="status"`, e o foco vai para o título | `sucesso-fundo`, `sucesso-texto` |

O texto vem do `code` da API (RFC 9457), nunca da mensagem técnica. Frases curtas, que dizem o que aconteceu e o que fazer:

| `code` | Onde | Tipo | Texto |
| --- | --- | --- | --- |
| `HORARIO_INDISPONIVEL` | checkout | erro da página | "Esse horário acabou de ser reservado por outra pessoa. Escolha outro." **PROPOSTA** |
| `CEP_INVALIDO` | CEP (visitante e cadastro) | erro de campo | "O CEP deve ter 8 dígitos." |
| `CEP_NAO_ENCONTRADO` | cadastro | aviso | "Não encontramos esse CEP. Preencha o endereço à mão." **PROPOSTA** · no agendamento do visitante não aparece aviso nenhum |
| `CEP_INDISPONIVEL` | cadastro | aviso | "Não conseguimos buscar o endereço agora. Preencha à mão." **PROPOSTA** |
| `EXCLUSAO_BLOQUEADA_POR_RESERVA` | Meus dados | aviso | "Você tem um agendamento em aberto. Para excluir a conta, fale com a barbearia." **PROPOSTA** |
| `LIMITE_EXCEDIDO` | login, cadastro, agendamento | erro da página | "Muitas tentativas. Tente de novo daqui a X min." (X vem do `Retry-After`) **PROPOSTA** |
| `GATEWAY_INDISPONIVEL` | checkout | erro da página | "Não conseguimos gerar o pagamento agora. Seu horário continua reservado até o fim do prazo; tente de novo." **PROPOSTA** |
| `TROCA_DE_SENHA_OBRIGATORIA` | login do admin | aviso, e leva à tela de troca | "Crie uma senha nova para continuar." **PROPOSTA** |

## Contador do pagamento

O checkout mostra um contador regressivo grande e sempre visível, no topo do resumo:

- Algarismos em `contador` (mm:ss) com "para pagar" ao lado, e uma barra horizontal que esvazia (`texto` sobre `borda`). O tempo é calculado a partir de `expiraEm`, que vem da API.
- **Leitor de tela:** o contador tem `role="timer"` e não fala a cada segundo. Uma região `aria-live="polite"` à parte avisa a cada minuto e aos 30 segundos ("Faltam 2 minutos para pagar").
- **Último minuto:** a barra passa a listrada (as listras do almoço) e aparece o texto "Falta menos de 1 minuto". A mudança é de forma e de texto, não de cor.
- **Tempo esgotado:** o contador some e entra o aviso "O tempo acabou e o horário voltou para a agenda." com o botão principal "Escolher outro horário".
- Com "Sem animação", a barra muda em degraus de 10 segundos, sem transição.

## WhatsApp do barbeiro

Para quem não quer marcar pelo app, a home e a tela de serviço e horário têm uma saída **"Prefere combinar pelo WhatsApp?"**: botão secundário com ícone de balão de conversa e o texto "Falar com o Eduardo no WhatsApp". O link é `https://wa.me/55` + o número público do barbeiro, que vem da API; se o barbeiro não tiver número, o bloco não aparece. O `aria-label` avisa que o link abre o WhatsApp. Nunca é o botão principal da tela: o principal continua sendo agendar.

## Formulários

- **Campo:** rótulo sempre visível acima (nunca só placeholder), contorno `borda-forte` de 1px, altura mínima de 48px, texto `corpo`. Com foco: anel `foco`. Com erro: contorno de 2px em `erro` mais o texto do erro abaixo.
- **Opcional ou obrigatório** escrito no rótulo: "CEP (opcional)".
- **CEP:** `inputmode="numeric"`, `autocomplete="postal-code"`, máscara `00000-000`. A validação de formato acontece ao sair do campo, não a cada tecla.
  - **Visitante:** CEP opcional. Formato errado mostra `CEP_INVALIDO`. CEP inexistente não mostra nada.
  - **Cadastro:** ao completar o CEP, o endereço se preenche, e uma região `aria-live` diz "Endereço preenchido pelo CEP. Confira os campos." Se a busca falhar, aparece o aviso e os campos ficam livres para digitar. O cadastro segue mesmo com o endereço incompleto (`endereco.completo = false`): nada de bloquear o botão por isso.
- **Botão de enviar** fica sempre ativo. Se algo estiver errado, a página mostra o erro e move o foco para ele, em vez de desativar o botão sem explicar.

## Privacidade (LGPD)

- **Marketing:** caixa separada do aceite da política, **desmarcada** por padrão, com rótulo claro: "Quero receber novidades e promoções da barbearia (opcional)".
- **Política de privacidade:** página de leitura com coluna de até 70 caracteres, títulos `titulo-secao`, sumário com links no topo e data da versão.
- **Meus dados:** blocos em cartões (`superficie`), cada um com sua ação: ver dados, "Exportar meus dados" (botão secundário), "Revogar consentimento de marketing" (botão secundário, com confirmação em uma linha).
- **Excluir conta:** última seção da página, separada por um divisor, com título "Excluir conta". O botão usa contorno de 2px em `erro` e ícone de lixeira, nunca o estilo de botão principal. A confirmação abre um diálogo (`<dialog>` com foco preso) que explica o que se perde, pede uma caixa "Entendo que isso não tem volta" e só então libera "Excluir minha conta". Se vier `EXCLUSAO_BLOQUEADA_POR_RESERVA`, o diálogo vira um aviso com o contato da barbearia.

## Alcance por região

- **Home, bloco "De onde vêm nossos clientes":** só nomes de cidades e bairros, em linha, separados por " · ", em `subtitulo`. **Sem números.** Estado vazio: o bloco inteiro não aparece (**PROPOSTA**: melhor do que um bloco vazio na home).
- **Painel, tela "Alcance":** uma tabela de verdade (`<table>` com `<caption>`), cidade e seus bairros abaixo, com o percentual em texto e uma barra horizontal monocromática (`texto` sobre `superficie`, `aria-hidden`). Grupo com menos de 5 atendimentos mostra "menos de 5" em `texto-suave`, sem barra e sem número. "Outras cidades" e "Outros bairros" sempre por último. Estado vazio: "Ainda não há atendimentos suficientes para mostrar o alcance."

## Telas da Fatia 7

O design de cada tela fica no canvas "Site · Barbearia Altas Horas". A spec da Fatia 7 marca o acabamento das telas sem design como "aguarda design".

| Tela | Situação | Artboard no canvas |
| --- | --- | --- |
| Home (com "De onde vêm nossos clientes" e WhatsApp) | desenhada | `Main.dc.html` |
| Serviço e horário | desenhada | `Agendar.dc.html` |
| Checkout com contador | desenhada | `Checkout.dc.html` |
| Confirmação | desenhada | `Confirmacao.dc.html` |
| Consulta da reserva pelo link do visitante | desenhada | `ConsultaReserva.dc.html` |
| Cadastro e login | desenhada | `Entrar.dc.html` |
| Meus agendamentos | desenhada | `MeusAgendamentos.dc.html` |
| Meus dados (exportar, revogar, excluir) | desenhada | `MeusDados.dc.html` |
| Política de privacidade (layout; texto a validar) | desenhada | `Privacidade.dc.html` |
| Painel: agenda do dia | desenhada | `PainelAgenda.dc.html` |
| Painel: reserva manual | desenhada | `PainelReservaManual.dc.html` |
| Painel: pendências | desenhada | `PainelPendencias.dc.html` |
| Painel: cadastros (serviços, barbeiros, regras) | desenhada | `PainelCadastros.dc.html` |
| Painel: auditoria | desenhada | `PainelAuditoria.dc.html` |
| Painel: alcance | desenhada | `PainelAlcance.dc.html` |
| Troca de senha inicial | desenhada | `TrocaSenha.dc.html` |
| Referência de estados e componentes | desenhada | `Estados.dc.html` |

Telas com o botão Play no canvas são interativas e mostram os estados: no checkout, o contador corre e dá para simular `HORARIO_INDISPONIVEL` e `GATEWAY_INDISPONIVEL` pelos ajustes da tela; o cadastro simula os estados do CEP e o `LIMITE_EXCEDIDO`; a consulta do visitante simula os sete status; "Meus dados" abre o diálogo de exclusão, inclusive o bloqueado.

## Acessibilidade e interação

A meta é **WCAG 2.2 nível AA** e a **ABNT NBR 17225**, a norma brasileira de acessibilidade web (março de 2025), que atende ao artigo 63 da Lei Brasileira de Inclusão. Na prática:

- **Estrutura para leitor de tela.** Um link "Pular para o conteúdo" como primeiro item; regiões `header`, `nav`, `main` e `footer`; um `h1` por página e seções com `h2`. Botões são `<button>`, links são `<a>`, campos têm `<label>`. Símbolos decorativos (★ ✂, o xadrez) ficam com `aria-hidden`.
- **Textos alternativos** descrevem a pessoa e a cena ("Eduardo Candido, de terno escuro, segurando o troféu…"). Imagem só decorativa leva `alt=""`.
- **Links com sentido sozinhos.** Botões repetidos como "Agendar" levam `aria-label` com o serviço e o preço ("Agendar Corte Degradê, 60 minutos, R$ 55,00").
- **Alvos de toque** com pelo menos 44×44px (o mínimo da 2.5.8 é 24px; aqui usamos o nível AAA).
- **Painel de acessibilidade** no topo da página, sempre visível: diminuir e aumentar o texto (100% a 150%; os tamanhos são em `em`, então tudo cresce junto), tema claro, alto contraste e sem animação. Cada botão de alternar usa `aria-pressed`.
- **Alto contraste.** Sobre o tema escuro, `texto-suave`, `texto-apagado`, `borda` e `borda-forte` viram `branco` e `superficie` vira `preto`; sobre o claro, o inverso. Assim todo texto e toda borda passam de 7:1.
- **Leitura em voz alta.** Trechos longos (a história) têm um botão "Ouvir" com a voz do próprio navegador em pt-BR, que vira "Parar leitura" e avisa o estado numa região `role="status"`. Isso ajuda quem tem baixa visão e não usa leitor de tela; quem usa, continua com o dele.
- **Movimento.** Só microinterações curtas (cartão sobe 4px, foto cresce 2%, 200ms). Tudo some com `prefers-reduced-motion` ou com o botão "Sem animação". Nada pisca, nada roda sozinho.
- **Mudanças anunciadas.** Seleções que mudam conteúdo (ano da linha do tempo, foto da galeria, resumo do agendamento) atualizam uma região `aria-live="polite"`.
- **Agendamento.** Grupos de opções com rótulo (`role="group"` + `aria-labelledby`), escolha marcada por forma (bloco invertido) e por `aria-pressed`, contagem de horários livres, e a política de não cancelamento num checkbox obrigatório antes de pagar, como exige a regra R18 do projeto.

**Interações da página** (todas funcionam por teclado): linha do tempo clicável (1994, 1997, 2017, hoje), galeria com miniaturas e setas anterior/próxima, perguntas frequentes em `<details>` e cartões de serviço com botão de agendar. O estilo segue as tendências atuais sem trair a marca: tipografia grande, blocos em grade e microinterações, sem 3D, gradientes ou cores novas.

## Logo

- O arquivo é `assets/Logos/logo-altas-horas.png` (512×512): o selo redondo com BARBEARIA em arco, o monograma **AA** com ALTAS HORAS na diagonal e ★ 19 ✂ 97 ★. Use sempre o arquivo; **nunca redesenhe, recolora ou distorça**.
- Funciona sobre `fundo` nos dois temas, porque o selo tem anel branco. Deixe em volta pelo menos 1/4 do diâmetro livre. Tamanho mínimo: 48px na tela.
- Não existe versão em vetor (SVG) no repositório. Para impressão grande, peça o arquivo original à barbearia.

## Iconografia

O repositório não tem biblioteca de ícones. Os símbolos da marca são **★** e **✂**, usados como no selo e no divisor ("19 ✂ 97"). Para ícones de interface (calendário, relógio, voltar), use um conjunto de traço fino e cantos retos, com traço `traco-2`, na cor do texto ao lado, nunca colorido. Ícones de interface ainda precisam ser escolhidos.

## Usar no front (Next.js)

1. Carregue `tokens.css` deste sistema (gerado a partir de `tokens.json`) e use as variáveis (`var(--fundo)`, `var(--space-4)`, `var(--font-display)`).
2. Ponha `data-theme="escuro"` (padrão) ou `data-theme="claro"` no `<html>`.
3. Carregue a Playfair Display 700 com `next/font/google` (`display: swap`).
4. As imagens oficiais estão em `docs/assets/` do repositório: os mesmos arquivos dos grupos Logos, Grafismos, Fotos e Prêmios. Mantenha os textos alternativos descritivos que o README já usa.

## Referências da barbearia

As fotos do Instagram do Eduardo (`@eduardo_candido_barber`) confirmam a identidade: o selo preto em adesivos no vidro da fachada, o interior branco com móveis e cadeiras pretas e os troféus na prateleira. No interior aparece também um móvel vermelho, mas a marca não usa vermelho e ele **não foi adotado** como cor.

## Não sincronizado

- **Componentes:** o front em Next.js ainda está sendo escrito (Fatia 7), então este sistema não tem componentes em código. A especificação deles são os padrões deste README e as telas do canvas "Site · Barbearia Altas Horas".
- **Fontes:** o código só usa fontes do sistema (Georgia, Helvetica). Não há arquivo de fonte para copiar: a Playfair Display vem do Google Fonts.
- **Espaçamento:** a escala de 4px é **PROPOSTA**. Os tamanhos de casa do xadrez, os raios e os traços vêm do código.
- **Textos de erro:** só o de `CEP_INVALIDO` veio do backend; os outros estão marcados **PROPOSTA** e devem ser confirmados na spec da Fatia 7.
- **Logo vetorial:** só existe o PNG.
