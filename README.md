<div align="center">

<img src="docs/assets/logo-altas-horas.png" width="200" alt="Logo da Barbearia Altas Horas: círculo preto com BARBEARIA em arco, monograma AA serifado com ALTAS HORAS na diagonal e ★ 19 ✂ 97 ★"/>

<picture>
  <source media="(prefers-color-scheme: light)" srcset="docs/assets/banner-claro.svg"/>
  <img src="docs/assets/banner.svg" width="100%" alt="Barbearia e Academy em Osasco, desde 1997. A agenda online do Eduardo Candido, com horário garantido."/>
</picture>

[![Site no ar](https://img.shields.io/website?url=https%3A%2F%2Fbarbearia-altas-horas.vercel.app&label=site&up_message=no%20ar%20%C2%B7%20beta&down_message=fora%20do%20ar&style=flat-square&labelColor=FFFFFF&color=0A0A0A)](https://barbearia-altas-horas.vercel.app)
[![Status](https://img.shields.io/badge/status-beta_no_ar_·_pagamento_simulado-0A0A0A?style=flat-square&labelColor=FFFFFF)](#-roadmap)
[![Backend CI](https://github.com/k3vinrich4rd/barbearia-altas-horas/actions/workflows/backend-ci.yml/badge.svg?branch=develop)](https://github.com/k3vinrich4rd/barbearia-altas-horas/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/k3vinrich4rd/barbearia-altas-horas/actions/workflows/frontend-ci.yml/badge.svg?branch=develop)](https://github.com/k3vinrich4rd/barbearia-altas-horas/actions/workflows/frontend-ci.yml)
[![Java](https://img.shields.io/badge/Java-25-0A0A0A?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.2-0A0A0A?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-0A0A0A?style=flat-square&logo=postgresql&logoColor=white)](docs/adr/0003-anti-double-booking-no-banco.md)
[![Next.js](https://img.shields.io/badge/Next.js-16-0A0A0A?style=flat-square&logo=nextdotjs&logoColor=white)](https://nextjs.org/)
[![Testes](https://img.shields.io/badge/testes_backend-588_✔-0A0A0A?style=flat-square)](docs/guia-testes.md#5e-senha-voz-da-barbearia-agenda-nova-e-fluidez-fatia-14b-14c-e-14d-)
[![Contrato](https://img.shields.io/badge/OpenAPI-3.1-0A0A0A?style=flat-square&logo=openapiinitiative&logoColor=white)](docs/contrato/openapi.yaml)

<h2>A agenda online da Barbearia Altas Horas, de Osasco: o cliente escolhe o horário, paga na hora e a vaga fica garantida.</h2>

<h2><a href="https://barbearia-altas-horas.vercel.app">🌐 barbearia-altas-horas.vercel.app</a></h2>

<sub><a href="https://altas-horas-api.onrender.com/actuator/health">🩺 API</a> · <a href="docs/contrato/openapi.yaml">📑 Contrato</a> · <a href="docs/guia-testes.md">🧪 Testar à mão</a> · <a href="#-rodar-agora">🚀 Rodar agora</a> · <a href="#-como-foi-desenvolvido-com-ia">🤖 Feito com IA</a></sub>

</div>

> [!IMPORTANT]
> O projeto está em **beta**. O pagamento é **simulado** (Pix e cartão não movimentam dinheiro de verdade). A meta é chegar à **produção** (pagamento real) e, depois, à **Play Store**. O caminho curto está no [roadmap](#-roadmap).

| ⚡ [Em 30 segundos](#-em-30-segundos) | 🗓 [3 formas de marcar](#-as-3-formas-de-agendar) | 🎬 [Site no ar](#-o-site-no-ar-beta) | 📖 [História](#-a-história) | 💈 [A barbearia](#-a-barbearia) |
|:-:|:-:|:-:|:-:|:-:|
| ✂ [O que resolve](#-o-que-a-agenda-resolve) | 💳 [Pagamento](#-pagamento-100-antecipado) | 🧱 [Arquitetura](#-arquitetura) | 🚀 [Rodar agora](#-rodar-agora) | 🗺 [Roadmap](#-roadmap) |

<img src="docs/assets/divisor.svg" width="100%" alt=""/>

## ⚡ Em 30 segundos

A agenda da barbearia do meu pai, o **Eduardo Candido**, saiu do caderno e do WhatsApp. Quem marca vê só horários realmente livres, paga em até 3 minutos e, se não pagar, a vaga volta sozinha. O barbeiro abre o painel e o dia já está organizado.

| Destaque | O que isso muda |
|---|---|
| 💳 **Pagamento antecipado** | o horário só é do cliente depois de pagar; falta deixa de ser cadeira vazia |
| 🔒 **Sem conflito de horário** | dois clientes nunca ficam com a mesma vaga, nem clicando no mesmo instante (o banco impede) |
| ⏱ **3 minutos para pagar** | passou o prazo, a vaga volta para a agenda na hora |
| 🛠 **Painel do barbeiro** | preços, jornada e folgas mudam sem alterar o código |

| 🌐 No ar | 💳 Pagamento | ⏱ Prazo | 🧪 Testes do backend |
|:-:|:-:|:-:|:-:|
| [beta na Vercel](https://barbearia-altas-horas.vercel.app) | 100% antecipado, Pix ou cartão (**simulado no beta**) | 3 min para pagar | **588**, 0 falhas |

<sub>Os 588 testes vêm do [guia de testes](docs/guia-testes.md#5e-senha-voz-da-barbearia-agenda-nova-e-fluidez-fatia-14b-14c-e-14d-) (rodada final de 24/11/2026, `./mvnw verify` com PostgreSQL real, depois da Fatia 14). Nenhum número desta página é estimativa.</sub>

## 🗓 As 3 formas de agendar

| | Quem | Como marca | O que acontece |
|:-:|---|---|---|
| 👤 | **1. Com cadastro** | faz login e escolhe o serviço e o horário | paga 100% antes; o horário aparece em **Meus agendamentos** |
| 🙋 | **2. Sem cadastro** (visitante) | informa nome, e-mail e WhatsApp | paga 100% antes; recebe o código da reserva e não precisa de senha |
| 💬 | **3. Pelo WhatsApp do barbeiro** | combina a conversa com o barbeiro, fora do app | o barbeiro registra no painel com a **reserva manual**: nasce confirmada, sem cobrança, e o horário sai da agenda |

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#C0C0C0','lineColor':'#8A8A8A','secondaryColor':'#1F1F1F','tertiaryColor':'#2B2B2B','edgeLabelBackground':'#1F1F1F','fontFamily':'Helvetica, Arial, sans-serif'}}}%%
flowchart LR
    C([Cliente]) --> A["👤 Com cadastro<br/>login e Meus agendamentos"]
    C --> G["🙋 Sem cadastro<br/>nome, e-mail e WhatsApp"]
    C --> W["💬 WhatsApp do barbeiro<br/>conversa fora do app"]
    A --> P["💳 Pagamento 100% antecipado<br/>Pix ou cartão · simulado no beta<br/>3 min para pagar"]
    G --> P
    P -- pagou --> OK([✅ Horário confirmado])
    P -- "não pagou em 3 min" --> X([⏱ Vaga volta para a agenda])
    W --> M["🛠 Reserva manual no painel<br/>o barbeiro trava o horário"]
    M --> OK
```

| 👤 Com cadastro | 🙋 Sem cadastro | 💬 WhatsApp |
|:-:|:-:|:-:|
| <img src="docs/midia/prints/fluxo-agendar-horarios.png" width="260" alt="Tela de agendar com o serviço escolhido, o dia e os horários livres"/> | <img src="docs/midia/prints/fluxo-agendar-dados.png" width="260" alt="Tela de dados do visitante: nome, e-mail e WhatsApp"/> | o cliente combina na conversa; o Eduardo trava o horário no painel, e a vaga some da agenda |
| login e **Meus agendamentos** | nome, e-mail, WhatsApp e o código da reserva | **reserva manual**: confirmada, sem cobrança |

<sub>Os prints do fluxo (horário e dados) foram tirados do front na máquina, contra a API no perfil `dev`, com dados fictícios e pagamento simulado (20/11/2026).</sub>

| Quem | O que faz | Situação |
|---|---|---|
| 🙋 **Visitante** | vê serviços e horários, agenda e paga sem criar conta | ✅ no ar (beta) |
| 👤 **Cliente** | cadastro, login, histórico, exportar e excluir os próprios dados (LGPD) | ✅ no ar (beta) |
| 👑 **Dono/Admin** (o Eduardo) | agenda, fecha atendimentos, cadastra serviços e preços, jornada e folgas, vê pagamentos, auditoria e o alcance por região | ✅ no ar (beta) |
| ✂️ **Barbeiro convidado** | entra por **convite de uso único** criado pelo dono, vê só a própria agenda e trava horários combinados pelo WhatsApp | ✅ no ar (beta) ([ADR 0013](docs/adr/0013-varios-barbeiros-dono-e-convite.md)) |

<sub>Hoje quem está na cadeira é o Eduardo, com os papéis `ADMIN` e `BARBEIRO`. O convite já existe para um profissional a mais, com a agenda isolada.</sub>

## 🎬 O site no ar (beta)

Prints reais de [barbearia-altas-horas.vercel.app](https://barbearia-altas-horas.vercel.app), tirados com Playwright em 20/11/2026. O tema segue o do seu GitHub: escuro por padrão, claro com a marca invertida. **O pagamento, no beta, continua simulado.**

<div align="center">
<a href="https://barbearia-altas-horas.vercel.app">
<picture>
  <source media="(prefers-color-scheme: light)" srcset="docs/midia/prints/site-home-claro.png"/>
  <img src="docs/midia/prints/site-home-escuro.png" width="90%" alt="Home do site da Barbearia Altas Horas: logo, chamada para agendar horário e a parede xadrez preta e branca"/>
</picture>
</a>
</div>

| A história desde 1997 | Como funciona | No celular | Dúvidas |
|:-:|:-:|:-:|:-:|
| <img src="docs/midia/prints/site-secao-historia.png" width="260" alt="Seção da história da barbearia com a linha do tempo de 1994 a hoje"/> | <img src="docs/midia/prints/site-secao-como-funciona.png" width="220" alt="Seção Como funciona: escolher, pagar e chegar"/> | <img src="docs/midia/prints/site-home-celular.png" width="120" alt="Home do site em tela de celular"/> | <img src="docs/midia/prints/site-secao-duvidas.png" width="220" alt="Seção de dúvidas com perguntas que abrem e fecham"/> |

<details>
<summary><b>🖼 O fluxo de reserva sem cadastro, tela a tela</b> (perfil <code>dev</code> local, dados fictícios)</summary>

<br/>

Estes prints são do front na máquina, contra a API no perfil `dev` (cliente e CPF de teste, pagamento simulado).

| 1. Escolher o horário | 2. Seus dados | 3. Pagar em 3 min |
|:-:|:-:|:-:|
| <img src="docs/midia/prints/fluxo-agendar-horarios.png" width="260" alt="Tela de agendar com o serviço escolhido, o dia e os horários livres"/> | <img src="docs/midia/prints/fluxo-agendar-dados.png" width="260" alt="Tela de dados do visitante: nome, e-mail e WhatsApp"/> | <img src="docs/midia/prints/fluxo-pagamento-contador.png" width="260" alt="Tela de pagamento com o contador de 3 minutos e o resumo do horário"/> |

| 4. Pix (simulado) | 5. Horário confirmado |
|:-:|:-:|
| <img src="docs/midia/prints/fluxo-pagamento-pix.png" width="260" alt="Tela do Pix copia e cola com QR Code e o botão Simular pagamento"/> | <img src="docs/midia/prints/fluxo-confirmacao.png" width="260" alt="Tela de horário confirmado com o resumo e o código da reserva"/> |

</details>

Como rodar e testar à mão, com a saída real de cada passo: **[guia de testes](docs/guia-testes.md)**. O fluxo por rotas e classes está em **[docs/fluxo-ponta-a-ponta.md](docs/fluxo-ponta-a-ponta.md)**.

## 📖 A história

### Quase três décadas de tradição, agora com agenda inteligente

<img align="right" src="docs/assets/eduardo-candido-avatar.png" width="190" alt="Eduardo Candido, barbeiro e professor da Altas Horas, de camisa branca e gravata"/>

O caminho do **Carlos Eduardo Candido** (o Eduardo), meu pai, na barbearia começou em **1994**, com o primeiro mestre. Três anos depois, em **1997**, ele abriu a **Barbearia Altas Horas** no Jardim Bonança, em Osasco, e desde então comanda a cadeira: quase 30 anos atendendo de terça a sábado, das 7h às 20h, do corte social clássico ao degradê navalhado. Em 2017, esse trabalho foi reconhecido duas vezes, com o **Prêmio Barbeiros do Brasil**, que ele fez questão de trazer para Osasco, e o **Prêmio Master Barber · Êxito Profissional**. Instrutor formado pela Ibel Formação, hoje ele também ensina na **Altas Horas Barbearia Academy**, preparando a próxima geração de barbeiros.

Com o dia dividido entre os clientes e os alunos, a agenda sempre ficou no caderno e no WhatsApp. É aí que aparece um problema clássico da barbearia de bairro: o cliente marca, não aparece, e aquele horário não volta mais. Para quem atende sozinho, cadeira vazia é tempo perdido e dinheiro que deixa de entrar.

**Como desenvolvedor, criei esta agenda para resolver exatamente essa dor:**

- 💳 **Reserva com pagamento antecipado:** o cliente escolhe o serviço e o horário e paga na hora, por Pix ou cartão.
- 🔒 **Vaga garantida, sem conflito:** o horário só entra na agenda depois que o pagamento é confirmado, e dois clientes nunca ficam com o mesmo horário.
- ⏱️ **Liberação automática:** quem não conclui o pagamento em 3 minutos devolve a vaga para o próximo.

No fim, o Eduardo começa a manhã com um painel limpo e o dia já organizado, cada atendimento confirmado. Sobra tempo para o que importa: a cadeira e os alunos.

> [!NOTE]
> **Onde o projeto está:** o site e a API estão no ar em **beta** desde 18/11/2026, com [pagamento simulado](https://barbearia-altas-horas.vercel.app). A API mostra horários livres de verdade, impede conflito de horário, tem conta, painel, privacidade (LGPD) e as fatias 1 a 14 (exceto a 9, que fica para depois do beta). São **588 testes** no backend em PostgreSQL real, 0 falhas ([guia de testes](docs/guia-testes.md#5e-senha-voz-da-barbearia-agenda-nova-e-fluidez-fatia-14b-14c-e-14d-), 24/11/2026, `./mvnw verify`).

<br clear="right"/>

<details>
<summary><b>🏆 Trajetória e prêmios</b></summary>

<br/>

<table>
<tr>
<td align="center" width="50%">
<img src="docs/assets/premios/premio-barbeiros-do-brasil-2017.jpg" width="100%" alt="Eduardo Candido, de terno escuro, segurando o troféu hexagonal do Prêmio Barbeiros do Brasil, com uma barra de barbeiro no centro"/>
<br/><b>Prêmio Barbeiros do Brasil</b><br/><sub>2017 · trazido para Osasco</sub>
</td>
<td align="center" width="50%">
<img src="docs/assets/premios/premio-master-barber-2017.jpg" width="100%" alt="Eduardo Candido, de terno preto e gravata, sorrindo e segurando o troféu dourado do Prêmio Master Barber"/>
<br/><b>Prêmio Master Barber · Êxito Profissional</b><br/><sub>2017</sub>
</td>
</tr>
</table>

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#FFFFFF','lineColor':'#7A7A7A','fontFamily':'Georgia, serif'}}}%%
timeline
    1994 : começa no ofício, com o primeiro mestre
    1997 : abre a Barbearia Altas Horas no Jardim Bonança, Osasco
    2017 : Prêmio Barbeiros do Brasil
         : Prêmio Master Barber · Êxito Profissional
    Hoje : barbeiro na cadeira e professor na Altas Horas Barbearia Academy
         : a agenda sai do caderno e vai para a internet
```

<div align="center">

<div align="center">
<sub><i>"Amor na ponta da tesoura!"</i> · Eduardo Candido, ao receber o Prêmio Barbeiros do Brasil</sub>
</div>

</details>

## 💈 A barbearia

📍 R. São Judas Tadeu, 313 · Jd. Bonança · Osasco/SP &nbsp;·&nbsp; 🕖 terça a sábado, 07h às 20h (almoço 12h às 13h) &nbsp;·&nbsp; 📸 [@altashorascabeleireiros](https://www.instagram.com/altashorascabeleireiros/) · [@eduardo_candido_barber](https://www.instagram.com/eduardo_candido_barber/)

Um barbeiro só, que faz todos os serviços. É para esse ritmo que a agenda foi pensada:

<img src="docs/assets/agenda-semana.svg" width="100%" alt="Página da agenda: terça a sábado, das 7h às 20h, com a faixa do almoço das 12h às 13h e exemplos de horários confirmados e aguardando pagamento; horários de 30 em 30 minutos"/>

<sub>Os horários marcados na imagem são ilustrativos: mostram como o dia aparece para o Eduardo, com o que está pago e o que ainda aguarda pagamento.</sub>

<details>
<summary><b>📖 Abrir a página de um dia</b> (resposta real da API para uma terça livre)</summary>

<br/>

`GET /disponibilidade` do **Corte Degradê** (60 min, como todos os serviços): **22 horários**.

| Manhã | | | | | Tarde | | | | | |
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| 07:00 | 07:30 | 08:00 | 08:30 | 09:00 | 13:00 | 13:30 | 14:00 | 14:30 | 15:00 | 15:30 |
| 09:30 | 10:00 | 10:30 | 11:00 | | 16:00 | 16:30 | 17:00 | 17:30 | 18:00 | 18:30 |
| | | | | | 19:00 | | | | | |

Com todos os serviços em 60 min, somem o 11:30 (invadiria o almoço) e o 19:30 (passaria das 20h): sobram os mesmos **22** para qualquer serviço do catálogo. Horário reservado, mesmo ainda aguardando pagamento, sai da lista; se o prazo de 3 min vence, ele volta sozinho.

```bash
curl "http://localhost:8080/disponibilidade?servicoId=6f1c2a8e-0000-4000-8000-000000000002&data=2026-12-01"
```

</details>

### Quadro de serviços

<table>
<tr><th align="left">Serviço</th><th>Tempo</th><th align="right">Valor</th></tr>
<tr><td>✂️ Corte Clássico / Social</td><td align="center">60 min</td><td align="right"><kbd>R$ 45,00</kbd></td></tr>
<tr><td>✂️ Corte Degradê / Fade / Navalhado</td><td align="center">60 min</td><td align="right"><kbd>R$ 55,00</kbd></td></tr>
<tr><td>✂️ Corte Moderno / Visagismo (tesoura)</td><td align="center">60 min</td><td align="right"><kbd>R$ 60,00</kbd></td></tr>
<tr><td>🪒 Barba Completa / Desenhada</td><td align="center">60 min</td><td align="right"><kbd>R$ 30,00</kbd></td></tr>
<tr><td>🪒 Acabamento (pezinho) / Sobrancelha</td><td align="center">60 min</td><td align="right"><kbd>R$ 20,00</kbd></td></tr>
<tr><td>✂️ Descoloração / Platinado (química)</td><td align="center">60 min</td><td align="right"><kbd>R$ 80,00</kbd></td></tr>
</table>

<sub>Sem combos. Preços, horários e regras são dados que o Eduardo muda no painel, sem mexer no código. Os cinco primeiros entram na migração V8 (20/11/2026); a descoloração/platinado entra na V10.</sub>

<details>
<summary><b>📸 Da cadeira</b> (galeria e fachada)</summary>

<br/>

No site a galeria é uma grade de **18 fotos** (autorização de imagem confirmada, D158), com a foto inteira ao tocar.

<table>
<tr>
<td align="center" width="33%"><img src="docs/assets/galeria/eduardo-em-atendimento.jpg" width="100%" alt="Eduardo Candido, de camisa listrada e gravata borboleta, finalizando um corte com topo reto e degradê, com as prateleiras pretas do salão ao fundo"/><br/><sub>O Eduardo em atendimento</sub></td>
<td align="center" width="33%"><img src="docs/assets/galeria/corte-topete-parede-do-logo.jpg" width="100%" alt="Cliente de perfil com topete alto e degradê, em frente à parede xadrez preta e branca com o logo da barbearia"/><br/><sub>A parede xadrez do logo</sub></td>
<td align="center" width="33%"><img src="docs/assets/galeria/corte-degrade-com-desenho.jpg" width="100%" alt="Cliente de costas com cabelo crespo em degradê, pontas descoloridas e um desenho feito na navalha na nuca"/><br/><sub>Degradê com desenho na navalha</sub></td>
</tr>
</table>

<details>
<summary><b>🚪 A fachada</b></summary>

<br/>
<div align="center">
<img src="docs/assets/fachada.jpg" width="520" alt="Fachada de vidro da Barbearia Altas Horas com dois adesivos redondos do logo e, ao fundo, as cadeiras, o balcão e os troféus"/>
</div>

</details>

<sub>Preto e branco, como o logo e a parede xadrez do salão. Fotos do acervo da barbearia e do Instagram do Eduardo.</sub>

</details>

## ✂ O que a agenda resolve

| No caderno e no WhatsApp | Na agenda online |
|---|---|
| o cliente manda mensagem e espera o fim de um corte para ter resposta | vê os horários livres na hora, a qualquer hora |
| dois clientes no mesmo horário: quem respondeu primeiro leva | só um consegue, mesmo clicando no mesmo instante |
| o cliente desiste e nem sempre avisa | o horário só é dele depois de pagar; sem pagar em 3 min, a vaga volta |
| a falta vira cadeira vazia e prejuízo | o valor já foi pago antes do corte |
| a agenda de amanhã está espalhada entre caderno e conversas | o Eduardo abre o painel e o dia está pronto |

Por trás de cada linha dessa tabela há uma decisão de engenharia. Abra para ver como cada regra é garantida:

<details>
<summary><b>Dois clientes nunca ficam com o mesmo horário</b></summary>

<br/>

Quem garante é o banco, não um `if` no código: uma constraint `EXCLUDE USING gist` com `tstzrange '[)'` no PostgreSQL. Com duas requisições no mesmo milissegundo, ou duas instâncias da API no ar, o banco deixa passar só uma. O teste coloca 12 conexões disputando o mesmo horário e confere que exatamente uma vence. [ADR 0003](docs/adr/0003-anti-double-booking-no-banco.md)

</details>

<details>
<summary><b>O horário só é confirmado com o dinheiro na conta</b></summary>

<br/>

A reserva nasce `AGUARDANDO_PAGAMENTO` e só vira `CONFIRMADO` quando o meio de pagamento avisa. Esse aviso pode chegar repetido, então o id do evento é chave única: o mesmo aviso duas vezes não confirma duas vezes. E a API não confia só no corpo do aviso: consulta a cobrança antes de confirmar. [ADR 0007](docs/adr/0007-gateway-de-pagamento-sandbox.md)

</details>

<details>
<summary><b>Quem não paga em 3 minutos libera a vaga</b></summary>

<br/>

A reserva guarda o prazo. Passou dele, o horário volta a aparecer como livre na mesma hora, sem esperar nenhuma rotina de limpeza rodar: a consulta de horários já ignora reserva vencida. Importa porque a hospedagem gratuita hiberna, e uma rotina agendada atrasaria.

</details>

<details>
<summary><b>O número do cartão nunca passa pela API</b></summary>

<br/>

O pagamento com cartão acontece na página do próprio meio de pagamento. A API só recebe o resultado, o que tira o projeto do escopo pesado do PCI DSS. O CPF, exigido para gerar a cobrança, é validado, repassado e não fica guardado. [ADR 0009](docs/adr/0009-lgpd-marketing-cpf-e-retencao.md)

</details>

<details>
<summary><b>Preço, horário e regra mudam sem mexer no código</b></summary>

<br/>

Serviços, preços, jornada, almoço e prazo de pagamento são dados no banco (a antecedência mínima e o limite de dias à frente deixaram de existir em 24/11/2026: a agenda só recusa o horário que já passou). O Eduardo muda o horário de almoço no painel e a agenda acompanha, sem depender de uma nova versão do sistema.

</details>

<details>
<summary><b>A senha do administrador nunca passa pelo repositório</b></summary>

<br/>

O primeiro acesso do Eduardo nasce de variáveis de ambiente na hora em que a API sobe, e a senha inicial precisa ser trocada antes de o painel abrir. Sessões se renovam sem pedir senha, e um token de renovação usado duas vezes derruba a sessão inteira: sinal de que alguém copiou. [spec da Fatia 3](docs/sdd/fatia-3-conta-e-acesso.md)

</details>

**Em uma linha, para quem avalia código:** concorrência resolvida no banco, pagamento idempotente, máquina de estados explícita, erros em RFC 9457, LGPD desde a primeira linha e testes com PostgreSQL real, tudo especificado antes de ser escrito ([Spec-Driven Development](#-como-foi-desenvolvido-com-ia)). Stack: Java 25 · Spring Boot 4.2 · Spring Security · JPA · Flyway · PostgreSQL · Testcontainers · Next.js 16; no ar em beta na Vercel, no Render e na Aiven.

## 💳 Pagamento 100% antecipado

> [!WARNING]
> No **beta**, o pagamento é **simulado**. Não há cobrança real. A tela `/pagamento-simulado/{cobrancaId}` não pede número de cartão. Isso só muda quando o projeto for para produção, com um gateway de verdade.

| Regra | Como o sistema garante |
|---|---|
| **Todo** agendamento (com conta ou sem conta) paga 100% antes | a reserva nasce `AGUARDANDO_PAGAMENTO` e só vira `CONFIRMADO` pelo webhook do gateway |
| Pix ou cartão de crédito | Pix devolve QR Code e copia e cola; cartão abre o checkout hospedado do gateway (no beta, a tela simulada do próprio site) |
| O horário fica travado por **3 min** enquanto o cliente paga | a constraint do banco conta a reserva pendente como ocupada até `expira_em` |
| Não pagou em 3 min | a reserva vira `EXPIRADO` e o horário volta para a agenda |
| **Sem cancelamento, remarcação ou estorno** por desistência ou falta | não há botão nem rota para o cliente cancelar; antes de pagar, ele marca o aceite obrigatório (`aceitePolitica: true`, com a versão do texto gravada) |
| Falta (*no-show*) | o barbeiro marca `NAO_COMPARECEU` no painel; o valor fica com a barbearia |
| Pagamento que chega depois dos 3 min | com o horário ainda livre, confirma normalmente; com o horário já ocupado, vira pendência no painel do admin |

> [!NOTE]
> O CPF é pedido **só** na tela de pagamento, porque o gateway exige. Ele é validado, repassado ao gateway e **não é guardado** no banco.

<details>
<summary><b>Checkout por dentro e ciclo de vida</b> (diagramas)</summary>

<br/>

### Checkout por dentro

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#C0C0C0','lineColor':'#8A8A8A','secondaryColor':'#1F1F1F','tertiaryColor':'#2B2B2B','actorBkg':'#0A0A0A','actorTextColor':'#FFFFFF','actorBorder':'#C0C0C0','noteBkgColor':'#2B2B2B','noteTextColor':'#FFFFFF','fontFamily':'Helvetica, Arial, sans-serif'}}}%%
sequenceDiagram
    autonumber
    actor C as Cliente
    participant F as Next.js (BFF)
    participant A as API Spring Boot
    participant DB as PostgreSQL
    participant G as Gateway (sandbox)

    C->>F: escolhe serviço e horário
    F->>A: POST /agendamentos
    A->>DB: INSERT reserva AGUARDANDO_PAGAMENTO (expira_em = agora + 3 min)
    alt horário já ocupado
        DB-->>A: 23P01 (EXCLUDE gist)
        A-->>F: 409 HORARIO_INDISPONIVEL (problem+json)
    else livre
        A-->>F: 201 reserva
    end
    F->>C: mostra a política e o checkbox (desmarcado)
    C->>F: marca o aceite e escolhe Pix ou Cartão
    F->>A: POST /pagamentos/cobrancas (X-Idempotency-Key, aceitePolitica=true)
    A->>DB: cobrança PENDENTE + aceite (versão e data)
    A->>G: cria cobrança de 100% (CPF só em trânsito)
    G-->>A: QR Code Pix ou URL do checkout do cartão
    A-->>F: 201 cobrança
    C->>G: paga
    G->>A: POST /pagamentos/webhook (token do gateway)
    A->>G: reconsulta a cobrança (não confia só no corpo)
    A->>DB: evento único + reserva CONFIRMADO (mesma transação)
    A-->>G: 200 (repetição do mesmo evento: 200 sem efeito)
    F->>A: consulta o status
    A-->>F: CONFIRMADO
```

### Ciclo de vida do agendamento

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#C0C0C0','lineColor':'#8A8A8A','secondaryColor':'#1F1F1F','tertiaryColor':'#2B2B2B','fontFamily':'Helvetica, Arial, sans-serif'}}}%%
stateDiagram-v2
    [*] --> AGUARDANDO_PAGAMENTO: cliente ou visitante reserva
    AGUARDANDO_PAGAMENTO --> CONFIRMADO: webhook aprova em até 3 min
    AGUARDANDO_PAGAMENTO --> EXPIRADO: 3 min sem pagamento
    EXPIRADO --> CONFIRMADO: pagamento tardio e horário ainda livre
    CONFIRMADO --> CONCLUIDO: barbeiro conclui
    CONFIRMADO --> NAO_COMPARECEU: falta (valor retido)
    CONFIRMADO --> CANCELADO: só a barbearia, com motivo
    AGUARDANDO_PAGAMENTO --> CANCELADO: só a barbearia, com motivo
    CONCLUIDO --> [*]
    NAO_COMPARECEU --> [*]
    CANCELADO --> [*]
    EXPIRADO --> [*]
```

<sub>Qualquer transição fora do diagrama responde `422 TRANSICAO_INVALIDA`. Tabela completa em [`docs/dominio/modelo-de-dominio.md`](docs/dominio/modelo-de-dominio.md#2-máquina-de-estados).</sub>

</details>

## 🧱 Arquitetura

**Monólito modular** (Spring Modulith): um deploy só, oito módulos com fronteiras verificadas por teste (ArchUnit + `ApplicationModules.verify()`), conversando por API pública e eventos com *outbox*. Detalhes em [`docs/arquitetura/`](docs/arquitetura/README.md).

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#C0C0C0','lineColor':'#8A8A8A','secondaryColor':'#1F1F1F','tertiaryColor':'#2B2B2B','clusterBkg':'#141414','clusterBorder':'#8A8A8A','edgeLabelBackground':'#1F1F1F','fontFamily':'Helvetica, Arial, sans-serif'}}}%%
flowchart LR
    cliente([Cliente / Visitante]) --> front[Next.js + BFF<br/>Vercel]
    barbeiro([Eduardo · Admin]) --> front
    front -- "cookie HttpOnly" --> api
    subgraph api [API Spring Boot · Render · Docker]
        direction TB
        conta[conta]
        agenda[agenda]
        pagamento[pagamento]
        cep[cep]
        privacidade[privacidade]
        admin[admin]
        comum[comum · plataforma]
    end
    api --> db[(PostgreSQL<br/>Aiven · SSL)]
    cep -- "timeout curto + cache" --> viacep[ViaCEP]
    cep -. fallback .-> brasilapi[BrasilAPI]
    pagamento -- "Pix / checkout de cartão" --> gateway[Gateway · sandbox]
    gateway -- "webhook autenticado" --> pagamento
```

<details>
<summary><b>🧱 Módulos, monorepo, deploy, ADRs, segurança e painel</b></summary>

<br/>

### Estrutura do monorepo

```text
barbearia-altas-horas/
├── backend/                         # API Java 25 + Spring Boot 4.2
│   └── src/main/java/io/github/k3vinrich4rd/altashoras/
│       ├── comum/        # Dinheiro, Cpf, Cep (mascaram no toString), erros RFC 9457
│       ├── plataforma/   # segurança, Clock, observabilidade, ShedLock
│       ├── conta/        # cadastro, login, JWT, refresh rotativo
│       ├── agenda/       # serviços, barbeiro, jornada, disponibilidade, reservas
│       ├── pagamento/    # cobrança 100% (Pix/cartão), webhook idempotente
│       ├── cep/          # ViaCEP → BrasilAPI, cache e degradação
│       ├── privacidade/  # consentimento, exportação e exclusão (LGPD)
│       └── admin/        # painel: agenda, catálogo, pagamentos, auditoria
├── frontend/             # Next.js (BFF guarda os tokens em cookie HttpOnly)
├── docs/                 # contrato, ADRs, domínio, banco, guia de testes
├── render.yaml           # Blueprint do Render (API, plano free)
└── compose.yaml          # PostgreSQL 18 local para o perfil dev
```

### Os módulos e quem pode chamar quem

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#C0C0C0','lineColor':'#8A8A8A','fontFamily':'Helvetica, Arial, sans-serif'}}}%%
flowchart LR
  admin --> agenda & conta & pagamento & comum
  privacidade --> conta & agenda & pagamento & comum
  pagamento --> agenda & comum
  agenda --> conta & comum
  conta --> cep & comum
  cep --> comum
  plataforma --> comum
```

<sub>A seta quer dizer "pode chamar a API pública de". Qualquer outra dependência reprova o `ModulithTest` e o `ArquiteturaTest`. Detalhes em [`docs/arquitetura/`](docs/arquitetura/README.md).</sub>

### Deploy: Vercel, Render e Aiven

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#C0C0C0','lineColor':'#8A8A8A','clusterBkg':'#141414','clusterBorder':'#8A8A8A','fontFamily':'Helvetica, Arial, sans-serif'}}}%%
flowchart LR
    N([Navegador]) -- HTTPS --> F
    subgraph V [Vercel]
        F[Next.js<br/>telas + BFF]
    end
    subgraph R [Render · plano free · Docker]
        A[API Spring Boot]
    end
    subgraph AV [Aiven · PostgreSQL free]
        D[(PostgreSQL 18<br/>SSL)]
    end
    F -- HTTPS --> A
    A -- JDBC/SSL --> D
    GH[GitHub Actions] -. backup semanal criptografado .-> D
```

Está no ar em beta desde 18/11/2026: a API no Render ([health](https://altas-horas-api.onrender.com/actuator/health)) e o front na Vercel ([site](https://barbearia-altas-horas.vercel.app)), ainda com pagamento simulado. No plano grátis, a API dorme sem uso e a primeira chamada pode passar de um minuto (em 20/11/2026 levou cerca de 90 segundos).

### Decisões de arquitetura (ADRs)

São **13** ADRs no repositório (`docs/adr/0001` a `0011`, `0013` e `0014`; não há `0012`), conferidos em 26/11/2026 na `develop`.

| Decisão | Alternativa descartada | Por quê | ADR |
|---|---|---|---|
| **Anti double booking no banco** com `EXCLUDE USING gist` + `tstzrange '[)'` + `btree_gist` | `synchronized` / lock em memória / `SELECT` antes do `INSERT` | lock em memória não protege duas instâncias e o `SELECT` tem corrida; a constraint vale para qualquer caminho de escrita | [0003](docs/adr/0003-anti-double-booking-no-banco.md) |
| **Webhook idempotente** pelo id do evento + reconsulta da cobrança | confiar no corpo do aviso | o gateway entrega "pelo menos uma vez"; o corpo pode ser forjado (OWASP API10) | [0007](docs/adr/0007-gateway-de-pagamento-sandbox.md) |
| **Erros RFC 9457** (`application/problem+json`) com `code` e `traceId` | JSON de erro próprio | padrão atual (substitui a RFC 7807); o front decide pelo `code`, não pelo texto | [0005](docs/adr/0005-erros-rfc-9457.md) |
| **Monólito modular** (Spring Modulith) | microsserviços | 1 pessoa, 1 deploy, fronteiras testadas; extrair serviço só com motivo real | [0001](docs/adr/0001-monolito-modular-com-spring-modulith.md) |
| **Hexagonal só onde paga o custo** (regra difícil pura; sistema externo atrás de porta) | hexagonal completo em todo módulo | CRUD com porta, adaptador e mapeador é cerimônia; a regra dos horários, pura, tem 12 testes em milissegundos | [0011](docs/adr/0011-hexagonal-seletivo-no-monolito-modular.md) |
| **Tempo em `timestamptz` + `Clock` injetado; dinheiro em centavos (`long`)** | `LocalDateTime` e `double` | fuso `America/Sao_Paulo` explícito e testes que "adiantam o relógio"; sem erro de arredondamento | [0004](docs/adr/0004-tempo-e-dinheiro.md) |
| **JWT curto + refresh rotativo em cookie `HttpOnly`** via BFF | token no `localStorage` | XSS não rouba sessão; reuso de refresh revoga a família | [0006](docs/adr/0006-sessao-jwt-refresh-cookie-bff.md) |
| **LGPD**: CPF só em trânsito, marketing por consentimento | guardar CPF cifrado | princípio da necessidade (art. 6º, III): o que não é guardado não vaza | [0009](docs/adr/0009-lgpd-marketing-cpf-e-retencao.md) |
| **Testcontainers** com PostgreSQL real | H2 em memória | `EXCLUDE`, `tstzrange` e `SKIP LOCKED` não existem no H2 | [0010](docs/adr/0010-versoes-da-plataforma.md) |

<sub>Todas as decisões com data e fonte: [`docs/planejamento/decisoes.md`](docs/planejamento/decisoes.md).</sub>

<sub>Todas as decisões com data e fonte: [`docs/planejamento/decisoes.md`](docs/planejamento/decisoes.md).</sub>

### Segurança e LGPD

| Tema | Decisão |
|---|---|
| Sessão | JWT de acesso de 15 min, validado como Resource Server; refresh rotativo guardado só como hash: usado duas vezes, a sessão inteira cai. ✅ |
| Recurso alheio | busca já filtrada pelo dono; responde **404** em vez de confirmar que o id existe (OWASP API1, BOLA) |
| Pagamento | webhook só com o token do gateway; cobrança com `X-Idempotency-Key`; cartão só no checkout do gateway |
| CPF | pedido só no pagamento, validado, repassado e **nunca gravado nem logado** |
| Aceite da política | versão do texto e data gravadas na reserva (prova em caso de contestação) |
| Admin | criado na subida por `ADMIN_EMAIL` / `ADMIN_SENHA` (variáveis de ambiente, só hash), com **troca obrigatória** da senha inicial antes de abrir o painel. ✅ |
| Abuso | limite de tentativas no login (por e-mail + IP e por IP) e no cadastro: `429` com `Retry-After`. ✅ |
| Negar por padrão | só as rotas listadas são públicas; rota nova nasce protegida mesmo que alguém esqueça de pensar em segurança. ✅ |
| Titular | exporta e exclui os próprios dados; marketing só com consentimento opcional e desmarcado |

### Painel do barbeiro

Área do papel `ADMIN`/`BARBEIRO`. É onde o Eduardo vê o dia, fecha os atendimentos e ajusta preços e horários sem depender de um novo deploy.

| O admin pode | Detalhe |
|---|---|
| Ver a agenda do dia e da semana | com o status de cada horário |
| Fechar o atendimento | `CONCLUIDO`, `NAO_COMPARECEU` (valor retido) ou cancelar com motivo |
| Gerenciar serviços e preços | nome, duração e preço, sem deploy |
| Editar jornada e folgas | inclusive a pausa de almoço |
| Acompanhar pagamentos | pendentes, confirmados e **pendências de pagamento tardio** |
| Convidar um barbeiro | convite de uso único; o convidado vê só a própria agenda |
| Ajustar regras | intervalo entre atendimentos, grade de horários, prazo de pagamento, texto da política |

</details>

## 🚀 Rodar agora

> [!TIP]
> Pré-requisitos: **Docker Desktop** e **JDK 25**. Sem `.env`: o perfil `dev` usa os padrões locais. A API sobe em `http://localhost:8080`.

```bash
git clone https://github.com/k3vinrich4rd/barbearia-altas-horas.git
cd barbearia-altas-horas && docker compose up -d
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

<details>
<summary><b>Front, testes e endereços locais</b></summary>

<br/>

O health fica em `http://localhost:8080/actuator/health`. Para o primeiro admin, suba com `ADMIN_EMAIL` e `ADMIN_SENHA` (a senha inicial precisa ser trocada antes de o painel abrir).

```bash
cd backend && ./mvnw verify      # 588 testes com PostgreSQL real via Testcontainers (24/11/2026, depois da Fatia 14)
```

O contrato da API ([`docs/contrato/openapi.yaml`](docs/contrato/openapi.yaml)) descreve cada rota, com os erros em pt-BR no formato RFC 9457:

```bash
npx @redocly/cli lint docs/contrato/openapi.yaml   # valida o contrato OpenAPI 3.1
```

Precisa de **Node 22** e da API no ar. O navegador só fala com o BFF do Next (`/api/**`), que guarda a sessão em cookies `HttpOnly`.

```bash
cd frontend
npm ci
npm run dev                 # http://localhost:3000
npm run lint && npm run typecheck && npm test
NEXT_PUBLIC_PAGAMENTO_SIMULADO=true npm run build && npx playwright test
bash ../scripts/conferir-spec.sh 14 --exigir
```

O visual vem **só** dos tokens do [design system](frontend/design/README.md). As variáveis estão em [`frontend/.env.example`](frontend/.env.example).

| O que | Endereço local |
|---|---|
| Health | `http://localhost:8080/actuator/health` |
| Serviços, política e horários livres | `/servicos` · `/politica-pagamento` · `/disponibilidade` |
| Conta e sessão | `/auth/**` · `/me` |
| Reserva, pagamento (simulado) e painel | `/agendamentos` · `/pagamentos/cobrancas` · `/admin/**` |
| Front | `http://localhost:3000` |

O passo a passo para testar à mão (visitante, cliente, barbeiro e admin) está em **[`docs/guia-testes.md`](docs/guia-testes.md)**.

</details>

## ✅ Qualidade em números reais

| Número | Fonte |
|---|---|
| **588** testes no backend, 0 falhas; cobertura **95%** | [guia de testes, seção 5e](docs/guia-testes.md#5e-senha-voz-da-barbearia-agenda-nova-e-fluidez-fatia-14b-14c-e-14d-), 24/11/2026, `./mvnw verify` com PostgreSQL real (Testcontainers), depois da Fatia 14 |
| **206** testes Vitest no front; `tsc`, `lint` e `build` limpos | a mesma rodada de 24/11/2026, seção 5e |
| `conferir-spec.sh 14 --exigir`: **62 de 62** | a mesma rodada: todo critério `A#` da Fatia 14 tem um teste com esse número |
| **17** specs SDD, **701** linhas de critério `A#`, **13** ADRs, **3** skills, **7** agentes de IA, **558** testes de backend com `@DisplayName("A…")` e **105** arquivos de teste do front | contados neste repositório em 26/11/2026, na `develop` (`e91e85d`) |

CI da `develop`: [backend](https://github.com/k3vinrich4rd/barbearia-altas-horas/actions/workflows/backend-ci.yml) e [frontend](https://github.com/k3vinrich4rd/barbearia-altas-horas/actions/workflows/frontend-ci.yml). Nenhum número desta página é estimativa.

## 🤖 Como foi desenvolvido com IA

Projeto individual: o Kevin (backend Java) usa IA como par de programação, e o que impede a IA de "inventar" é o **método**, não o prompt. Cada peça abaixo é um arquivo que você pode abrir. O relato completo está em [`docs/como-foi-construido.md`](docs/como-foi-construido.md).

<div align="center">

| 📐 Fatias com spec | ✅ Critérios `A#` nas specs | 🧪 Testes do backend | 🧭 ADRs | 🧑‍💻 Agentes | 🛠 Skills |
|:-:|:-:|:-:|:-:|:-:|:-:|
| **17** em [`docs/sdd`](docs/sdd/README.md) | **701** linhas `A#` | **588** (`./mvnw verify`) | **13** em [`docs/adr`](docs/adr) | **7** em [`.claude/agents`](.claude/agents) | **3** em [`.claude/skills`](.claude/skills) |

</div>

<sub>Specs, critérios, ADRs, agentes e skills foram contados no repositório em 26/11/2026, na `develop`. Os 588 testes são a rodada de 24/11/2026 do [guia de testes](docs/guia-testes.md#5e-senha-voz-da-barbearia-agenda-nova-e-fluidez-fatia-14b-14c-e-14d-).</sub>

### O fluxo de uma fatia, da spec ao deploy

```mermaid
%%{init: {'theme':'base','themeVariables':{'primaryColor':'#0A0A0A','primaryTextColor':'#FFFFFF','primaryBorderColor':'#C0C0C0','lineColor':'#8A8A8A','fontFamily':'Helvetica, Arial, sans-serif'}}}%%
flowchart LR
    B["🔎 Contexto<br/>buscar-contexto.sh · RAG"] --> E["📐 /especificar-fatia<br/>spec com critérios A#"]
    E --> OK{"✋ OK do dono<br/>(Kevin)"}
    OK -- "não" --> E
    OK -- "sim" --> I["🛠 /implementar-fatia<br/>contrato → testes A# → código → docs"]
    I --> R["🧐 agente de revisão<br/>só leitura"]
    R --> PR["🔀 PR para a develop"]
    PR --> CI["✅ CI<br/>testes · conferir-spec.sh · CodeQL · gitleaks"]
    PR --> CP["🤝 Revisão do Copilot"]
    CI --> M{"tudo verde?"}
    CP --> M
    M -- "sim" --> D["🚀 merge na develop<br/>release → main → deploy<br/>(Vercel + Render)"]
    M -- "não" --> I
```

1. **Contexto barato.** Antes de escrever, a IA busca o que já foi decidido (regra, decisão, ADR, spec, teste) em vez de reler tudo.
2. **Spec.** `/especificar-fatia` escreve `docs/sdd/fatia-N-*.md` com objetivo, contrato, decisões e critérios *Dado/Quando/Então*. **Para aí.** Nenhuma linha de código sem o OK do Kevin.
3. **Implementação.** `/implementar-fatia` segue a ordem contrato, testes (um por critério), código, docs com saída real. Número só de execução real.
4. **Revisão.** Um agente de revisão (só leitura) confere o diff contra a spec; depois o Copilot revisa a PR.
5. **CI.** A PR só entra com testes, `conferir-spec.sh`, CodeQL e gitleaks verdes.
6. **Deploy.** Merge na `develop`, release `develop → main` e publicação (front na Vercel, API no Render, banco na Aiven); o [guia de testes](docs/guia-testes.md) é rodado contra o ambiente publicado.

<details>
<summary><b>SDD, RAG, skills e os papéis dos agentes</b></summary>

<br/>

### 📐 SDD: Spec-Driven Development

Quando a IA escreve boa parte do código, quem garante a qualidade é a **especificação**. Sem spec, a IA preenche lacunas com suposição; com spec, ela implementa critérios que viram testes.

- **Uma spec por fatia** em [`docs/sdd/`](docs/sdd/README.md) (17 arquivos, [modelo](docs/sdd/modelo-de-spec.md) e [checklist de PR](docs/sdd/checklist-pr.md)).
- **Critérios `A#`** em tabela, cada um com o arquivo e o teste que o prova. Ex.: a [Fatia 2](docs/sdd/fatia-2-catalogo-disponibilidade.md) tem 29 critérios e 29 testes.
- **`conferir-spec.sh`** ([`scripts/conferir-spec.sh`](scripts/conferir-spec.sh)): um "eval" determinístico. Para cada linha `| A<n> |`, procura um teste com o número dentro de `@DisplayName("A7…")`, `test("A7…")` ou `it("A7…")`; comentário não conta. Com `--exigir`, falha o CI se faltar algum. Roda em [`backend-ci.yml`](.github/workflows/backend-ci.yml) e [`frontend-ci.yml`](.github/workflows/frontend-ci.yml), e o próprio script tem teste ([`testar-conferir-spec.sh`](scripts/testar-conferir-spec.sh)).
- **OK do dono antes do código.** A spec é aprovada pelo Kevin; só então a skill de implementação roda. Algo que a spec não decide? A IA para e pergunta, e a resposta vira decisão registrada na spec.

### 🔎 RAG e recuperação de contexto

Duas ferramentas, para dois tipos de pergunta, ambas medidas.

| Ferramenta | Para que serve | Como funciona |
|---|---|---|
| [`scripts/buscar-contexto.sh`](scripts/buscar-contexto.sh) `R15` | achar **por identificador** (`R15`, `D40`, `A7`, código de erro): regra, decisão, spec, contrato, ADR e código/teste, com arquivo e linha | `grep` em `docs/` e `backend/src`, com palavra inteira (`R1` não traz `R15`). Sem dependência, instantâneo |
| [`ferramentas/rag`](ferramentas/rag/README.md) | achar **por pergunta solta** ("o cliente pode cancelar depois de pagar?") | Spring AI 2.0.2 + pgvector + embeddings locais (ONNX, sem chave de API). Divide os `.md` por título (390 trechos de 37 arquivos), gera vetores, busca por distância de cosseno; identificador vai para busca exata (**híbrida**) |

Medido em 17 perguntas (17/11/2026): só vetor acerta 10; a busca híbrida, **15**. O vetor errou todos os identificadores (`R15` e `R16` são quase o mesmo texto para o modelo). Por isso o script é a primeira escolha para ID e o RAG fica para a linguagem natural. Os dois erros que sobraram estão registrados, sem maquiar a avaliação ([detalhes](docs/ia/rag-vetorial.md)). O RAG é ferramenta de desenvolvimento, fora da API; o [assistente para clientes](docs/sdd/fatia-9-assistente-de-duvidas.md) (Fatia 9) é spec, sem código ainda.

### 🛠 Skills

Receitas repetíveis em [`.claude/skills/`](.claude/skills/):

| Skill | O que faz |
|---|---|
| [`/especificar-fatia N`](.claude/skills/especificar-fatia/SKILL.md) | escreve a spec SDD da fatia (objetivo, contrato, decisões, critérios `A#` com o teste de cada um) e **para** no OK do Kevin, sem escrever código |
| [`/implementar-fatia N`](.claude/skills/implementar-fatia/SKILL.md) | a partir da spec aprovada: contrato, testes (um por `A#`), código, docs com saída real e PR para a `develop`, explicando os conceitos |
| [`/verificar-contrato`](.claude/skills/verificar-contrato/SKILL.md) | compara controllers, DTOs e códigos de erro com o `openapi.yaml` e lista divergências de rota, campo, tipo, status e erro; só lê e relata |

### 🧑‍💻 Agentes de IA, pelo papel

Há **7** agentes no repositório, cada um num arquivo de [`.claude/agents/`](.claude/agents/), com papel e **permissões limitadas** (quais ferramentas pode usar). Aqui eles entram pelo o que fazem, não pelo nome:

| Papel | O que faz | Pode |
|---|---|---|
| Agente de arquitetura | escreve e revisa spec, ADR, contrato e decisões, antes de qualquer código | ler e escrever docs; não implementa nem mergeia |
| Agente de implementação | implementa a partir de spec aprovada: testes `A#` primeiro, código, docs com saída real | escrever código e dar push; não abre PR nem mergeia |
| Agente de revisão de fatia | segundo par de olhos: diff contra spec, contrato, camadas, segurança e privacidade | só leitura |
| Agente de QA | sobe API e front e executa os critérios `A#` à mão, gravando a saída real no guia de testes | rodar e documentar |
| Agente de segurança de aplicação | sessão, cookies, CORS, pagamento, webhook, dependências, OWASP | só leitura |
| Agente de privacidade | dado pessoal, consentimento, retenção e alcance por região | só leitura e pesquisa |
| Agente de operação e deploy | deploy e operação nos planos grátis: Render, Aiven, Vercel, ping e backup | só leitura |

### 🤝 O revisor do Copilot

O GitHub Copilot lê as mesmas regras do `CLAUDE.md` por [`.github/copilot-instructions.md`](.github/copilot-instructions.md) e entra de três formas ([`docs/automacao.md`](docs/automacao.md)): sugestões no editor; **revisor de PR**, uma segunda opinião de quem não escreveu o código (não aprova nem mergeia); e agente de código para tarefa pequena e fechada, nunca em pagamento, concorrência ou segurança. O [`copilot-setup-steps.yml`](.github/workflows/copilot-setup-steps.yml) deixa JDK 25 e PostgreSQL prontos para ele. Qualquer PR, de quem for, passa pelo mesmo CI.

### Como a qualidade é garantida

| Parte | Como é feita | Como a qualidade é garantida |
|---|---|---|
| **Especificação** | escrita antes de qualquer código | critérios numerados; nada de código antes do OK |
| **Backend** (Java 25 + Spring Boot 4.2) | IA como par de programação | um teste por critério, PostgreSQL real (Testcontainers), regras de arquitetura (Spring Modulith + ArchUnit), CodeQL e gitleaks |
| **Frontend** (Next.js) | gerado com IA a partir do contrato OpenAPI e das specs de tela, com revisão humana | tipos gerados do contrato, testes das telas críticas, `conferir-spec.sh` das Fatias 7, 12 e 14 |
| **Deploy** | Vercel + Render + Aiven, IA apoiando os passos | guia de testes rodado contra o ambiente publicado |

<sub>Números e saídas do README vêm de execução real, nunca de estimativa da IA. Mais: [como foi construído](docs/como-foi-construido.md) · [automação](docs/automacao.md) · [`CLAUDE.md`](CLAUDE.md).</sub>

</details>

## 🗺 Roadmap

O projeto está em **beta**, com pagamento **simulado**. O que vem a seguir é curto e nesta ordem:

| | Etapa | O que significa |
|:-:|---|---|
| 1 | **Agora · beta no ar** | site e API publicados; o cliente já agenda e "paga", mas o dinheiro não é real |
| 2 | **Produção** | gateway de pagamento real, domínio e operação do dia a dia da barbearia |
| 3 | **Play Store** | o app na loja, para o cliente marcar pelo celular |
| — | **Depois do beta** | assistente de dúvidas (Fatia 9, hoje só em spec, desligado por padrão) |

<details>
<summary><b>📊 Status das fatias</b> (1 a 14 no repositório; 9 fica para depois do beta)</summary>

<br/>

✅ no ar ou pronta · 📝 planejada. Cada fatia nasceu como especificação e só depois como código. A fonte da verdade é o [`docs/sdd/README.md`](docs/sdd/README.md).

| Fatia | Status | O que entrega |
|---|:-:|---|
| **0 · Especificação** | ✅ | domínio, máquina de estados, contrato OpenAPI, DDL proposto e ADRs |
| **1 · Esqueleto** | ✅ | Maven, 8 módulos, RFC 9457, `Clock`, Docker Compose, Flyway V1 com a constraint anti-sobreposição, CI |
| **2 · Catálogo e disponibilidade** | ✅ | serviços, política e horários livres ([spec](docs/sdd/fatia-2-catalogo-disponibilidade.md)) |
| **3 · Conta e acesso** | ✅ | cadastro, login, refresh rotativo, troca de senha, admin inicial ([spec](docs/sdd/fatia-3-conta-e-acesso.md)) |
| **4 · Agendamento e checkout** | ✅ | reserva com trava de 3 min, cobrança 100% Pix/cartão, aceite, webhook, expiração ([spec](docs/sdd/fatia-4-agendamento-e-pagamento.md)) |
| **5 · Painel admin** | ✅ | agenda, fechamento, catálogo, pagamentos e auditoria |
| **6A–6D · CEP, privacidade, alcance e WhatsApp** | ✅ | ViaCEP com degradação, LGPD, alcance com k = 5, reserva manual |
| **7 · Telas Next.js** | ✅ | [no ar em beta](https://barbearia-altas-horas.vercel.app): home, agendar, checkout, painel |
| **8 e 8B · Deploy** | ✅ | API no Render, PostgreSQL na Aiven, front na Vercel, backup e subida mais rápida; o pagamento ainda é simulado |
| **Catálogo inicial (V8)** | ✅ | 5 serviços, o barbeiro **Eduardo Candido** e a jornada no banco (a química entra na V10) |
| **10 · Convite do barbeiro** | ✅ | convite de uso único e isolamento entre agendas ([spec](docs/sdd/fatia-10-convite-do-barbeiro.md)) |
| **11 · Alcance por nível** | ✅ | consentimento, k = 5 e subida de bairro → cidade → região → UF ([spec](docs/sdd/fatia-11-alcance-por-nivel.md)) |
| **12 · Site mais fluido** | ✅ | home, galeria, Trajetória e agendar em chips ([spec](docs/sdd/fatia-12-site-fluido.md)) |
| **13 · Notificação ao barbeiro** | ✅ | Web Push e aviso no painel quando o horário é confirmado ([spec](docs/sdd/fatia-13-notificacao-ao-barbeiro.md)) |
| **14 · Experiência premium** | ✅ | mais 1 minuto no Pix, senha visível, voz, agenda nova e fluidez ([spec](docs/sdd/fatia-14-experiencia-premium.md)) |
| **9 · Assistente de dúvidas** | 📝 | RAG para o cliente, desligado por padrão; **depois do beta** ([spec](docs/sdd/fatia-9-assistente-de-duvidas.md)) |

</details>

## 📚 Documentação

| Documento | O que tem |
|---|---|
| [Contrato da API](docs/contrato/contrato-api.md) · [OpenAPI](docs/contrato/openapi.yaml) | rotas, exemplos, erros e histórico de versões |
| [Dossiê de contexto](docs/contexto.md) | regras de negócio numeradas e decisões em aberto |
| [Modelo de domínio](docs/dominio/modelo-de-dominio.md) | agregados, eventos e máquina de estados |
| [Arquitetura](docs/arquitetura/README.md) | módulos, dependências e fluxos críticos |
| [Banco](docs/banco/erd.md) | ERD, constraints e DDL proposto |
| [ADRs](docs/adr/README.md) | as decisões de arquitetura, uma por arquivo |
| [Painel admin](docs/admin/painel-admin.md) | matriz de acesso |
| [Guia de testes](docs/guia-testes.md) | teste você mesmo, à mão: o banco, os erros e os cenários de visitante, cliente e admin |
| [Como foi construído](docs/como-foi-construido.md) | o projeto de ponta a ponta: método, linha do tempo, skills e o papel de cada IA |
| [SDD](docs/sdd/README.md) | o processo, o modelo de spec e a spec de cada fatia |
| [Automação](docs/automacao.md) | por que cada Action existe, o papel do Copilot e a regra de recuperação de contexto |
| [RAG vetorial](docs/ia/rag-vetorial.md) | como a busca semântica na documentação funciona e o que a avaliação mostrou |
| [Estudos aplicados](docs/estudos-aplicados.md) | metas de estudo (Java/Spring e IA) ligadas a arquivos e testes deste projeto |

<img src="docs/assets/divisor.svg" width="100%" alt=""/>

## 🖤 Homenagem

<div align="center">

<img src="docs/assets/logo-altas-horas.png" width="96" alt="Logo da Barbearia Altas Horas"/>

**Para o meu pai, Eduardo Candido**, que mantém a cadeira da **Barbearia Altas Horas** girando desde **1997** e ensina o ofício na **Altas Horas Barbearia Academy**.
Este projeto é a minha forma de levar para o código o cuidado que ele coloca em cada corte.

**★ 19 ✂ 97 ★**

</div>

## 👤 Autor

**Kevin Richard** · desenvolvedor backend Java · Osasco/SP · [GitHub @k3vinrich4rd](https://github.com/k3vinrich4rd)

Construo backends em Java e Spring com foco em regra de negócio bem protegida: concorrência resolvida no banco, integrações que falham sem derrubar o sistema e testes que provam o que a especificação promete.

<sub>Logo, fotos e identidade visual pertencem à Barbearia Altas Horas. Feito com Spec-Driven Development e apoio de IA; cada decisão tem fonte e data. Projeto em beta, com pagamento simulado.</sub>
