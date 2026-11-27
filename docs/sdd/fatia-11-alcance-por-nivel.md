# 📐 Spec · Fatia 11 · Alcance por estado, município e região (k-anonimato e consentimento)

| | |
|---|---|
| **Status** | ✅ aprovada pelo Kevin (23/11/2026), com as dúvidas D-1 a D-9 como propostas (a D-1 atualiza o ADR 0014 na implementação) |
| **Origem** | pedido do Kevin em 23/11/2026, sobre o [ADR 0014](../adr/0014-alcance-k-anonimato-e-consentimento-do-cep.md) (aceito em 20/11). Implementa o ADR inteiro (consentimento sem guardar o CEP, contagem de pessoas, k = 5, subida de nível, supressão complementar e vitrine em faixas) e acrescenta o **painel por estado, município e região** para o Dono/Admin e para o barbeiro convidado |
| **Substitui** | **a regra de "sem limite" de 19/11** (o painel do alcance contar qualquer quantidade de atendimentos, sem mínimo para aparecer). Ela **não está registrada** em `decisoes.md` nem em spec (procurei em `docs/` na `develop`); fica aqui revogada por escrito. Valem o k do ADR 0014, a D109 (k = 5 por bairro e por cidade) só como ponto de partida, e a D114 (grupo "Outros" abaixo de 5 nulo), **trocadas** pelas regras desta spec. O `percentual` e o `atendimentos` do painel atual (contagem de atendimentos) passam a contar **pessoas** |
| **Entrega em quatro PRs** (proposta) | **11A** · consentimento e região sem CEP (`feat/fatia-11a-consentimento-regiao`) · **11B** · pessoas distintas, k e subida de nível, no backend (`feat/fatia-11b-k-e-niveis`) · **11C** · vitrine pública em faixas (`feat/fatia-11c-vitrine-em-faixas`) · **11D** · painel por nível e telas (`feat/fatia-11d-painel-por-nivel`) |
| **Regras** | R28 (consentimento opcional, versionado e revogável), R30 (CEP nunca em log), R31 (CEP nunca derruba o cadastro), R33 (reserva manual); LGPD art. 7º, I e art. 8º; D105 a D114 (alcance da 6C), D127 (reserva manual fora do alcance) |
| **ADRs** | [0014](../adr/0014-alcance-k-anonimato-e-consentimento-do-cep.md), [0009](../adr/0009-lgpd-marketing-cpf-e-retencao.md), [0008](../adr/0008-cep-com-cache-e-degradacao.md), [0013](../adr/0013-varios-barbeiros-dono-e-convite.md) (o barbeiro convidado; a [Fatia 10](fatia-10-convite-do-barbeiro.md) vem antes) |
| **Contrato** | `1.8.0 → 1.9.0` (minor, sem quebra; se a Fatia 10 não tiver sido mergeada ainda, o primeiro a mergear leva a 1.8.0) |
| **Custo** | **zero**: a tabela do IBGE vai dentro do repositório; nenhuma chamada paga em tempo de execução |
| **Não cobre** | mapa ou geolocalização (só nomes) · alcance por rua ou CEP · avisar o cliente por e-mail ou WhatsApp sobre o consentimento · o login social (R32) · cobrança por barbeiro |

## 1. Objetivo

(1) O cliente só informa o CEP se **marcar a caixinha** de consentimento, e o CEP nunca é guardado: ficam o bairro, o município, a UF e o código IBGE. (2) O alcance conta **pessoas diferentes**, não atendimentos, com k = 5 configurável, e um lugar pequeno **sobe de nível** (bairro → município → região imediata do IBGE → UF → "Outros estados") em vez de sumir, sem que dê para descobrir um número menor que k por subtração. (3) A home mostra os municípios e, ao clicar, os bairros em **faixas de porcentagem**, sem total. (4) O Dono/Admin (todos os barbeiros, com filtro) e o barbeiro convidado (**só os clientes dele**) veem os bairros e os clientes atendidos **por estado, por município e por região**, com o mesmo k em cada recorte.

## 2. Contrato

Versão **1.9.0** do [`openapi.yaml`](../contrato/openapi.yaml) e do [`contrato-api.md`](../contrato/contrato-api.md) (`redocly lint` com 0 erros). Só acréscimos e `deprecated`; as remoções ficam para a próxima major (ADR 0014).

| Método | Rota | Quem | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/auth/cadastro` (muda) | público | corpo ganha `autorizaRegiao` (boolean, padrão `false`) e `versaoTextoRegiao`. O `cep` continua aceito, mas **só vale com `autorizaRegiao = true`**; sem isso é ignorado (nem consultado, nem guardado) | `400 VALIDACAO_INVALIDA` (`versaoTextoRegiao` diferente da vigente, ou faltando com `autorizaRegiao = true`) |
| `PUT` | `/me` (muda) | logado | mesmo par de campos. Sem `autorizaRegiao = true` (ou sem CEP) = revoga: apaga a localidade do perfil e a região das reservas da conta | idem |
| `POST` | `/agendamentos/guest` (muda) | público | mesmo par de campos; o `201` e `GET /agendamentos/guest/{id}` ganham `regiaoAutorizada` (boolean) | idem |
| `DELETE` | `/agendamentos/guest/{id}/regiao` (**nova**) | visitante, com `X-Guest-Token` | `204`, **idempotente**; apaga a região da reserva na hora (LGPD art. 8º, § 5º) | `404` para token errado, ausente ou de outra reserva (R23, mesmo corpo) · `429` |
| `PUT` | `/me/consentimentos/regiao` (**nova**) | logado | `200` `Consentimento` (finalidade `REGIAO_ALCANCE`), no molde do de marketing; revogar apaga a região | `400 VALIDACAO_INVALIDA` (`versaoTexto` não vigente) · `401` |
| `GET` | `/me` e `/me/dados` (mudam) | logado | `endereco.cep` e o `cep` da exportação ficam `deprecated` e **sempre `null`**; aparecem `regiaoAutorizada` e a localidade (bairro, município, UF) | |
| `GET` | `/alcance` (muda) | público | `atendimentos` e qualquer total viram `null` e `deprecated`; entra `faixaPercentual` (`"15"` ou `"<5"`, `null` sem porcentagem); `totalBairros` conta só os bairros que passam no k | `429` como hoje |
| `GET` | `/admin/alcance` (muda) | `ADMIN` (todos ou `?barbeiroId=`) · `BARBEIRO` (**só os próprios clientes**; o `barbeiroId` é trocado pelo dele, E36) | ver o esquema abaixo | `400 VALIDACAO_INVALIDA` (`nivel`, `periodo`, `de`, `ate`) · `401` · `403` · `404` (`barbeiroId` inexistente) |
| `GET` | `/politica-privacidade` (muda) | público | versão **1.2** (consentimento, "clientes" no lugar de "atendimentos", faixas, retenção da região) | |

**Parâmetros novos de `GET /admin/alcance`:** `nivel` = `ESTADO` \| `REGIAO` \| `MUNICIPIO` (padrão `MUNICIPIO`; é o **seletor de nível** da tela); `uf` (opcional, restringe a uma UF); `periodo` = `ULTIMOS_30_DIAS` (padrão) \| `ULTIMOS_90_DIAS` \| `ULTIMOS_12_MESES`. `de` e `ate` continuam aceitos (D108) para quem já os usa, mas valem a ressalva do D-4.

**Esquema da resposta** (`AlcancePainel`, que **substitui os campos** `cidades` e `semRegiao` por estes; os antigos ficam `deprecated` até a próxima major, preenchidos como antes quando `nivel = MUNICIPIO`):

```json
{
  "periodo": { "de": "2026-10-25", "ate": "2026-11-23" },
  "nivel": "MUNICIPIO",
  "k": 5,
  "totalVisivel": true,
  "clientes": 42,
  "semRegiao": { "clientes": 9, "abaixoDoLimite": false },
  "grupos": [
    { "tipo": "MUNICIPIO", "codigo": "3534401", "nome": "Osasco", "uf": "SP",
      "clientes": 31, "percentual": 73.8, "abaixoDoLimite": false, "agrupadoEm": null,
      "filhos": [
        { "tipo": "BAIRRO", "nome": "Centro", "clientes": 18, "percentual": 42.9, "abaixoDoLimite": false },
        { "tipo": "OUTROS", "nome": "Outros bairros", "clientes": 13, "percentual": 31.0, "abaixoDoLimite": false }
      ] },
    { "tipo": "OUTROS", "codigo": "350001", "nome": "Outros municípios da região imediata de São Paulo",
      "uf": "SP", "clientes": null, "percentual": null, "abaixoDoLimite": true, "agrupadoEm": "REGIAO", "filhos": [] }
  ]
}
```

- `tipo` do grupo = o nível escolhido (`UF`, `REGIAO`, `MUNICIPIO`) ou `OUTROS`; `filhos` é o nível de baixo (UF → regiões, região → municípios, município → bairros).
- `clientes` e `percentual` são **`null` quando `abaixoDoLimite`** (a tela escreve "menos de 5"); `agrupadoEm` diz **até que nível** o grupo subiu (`MUNICIPIO`, `REGIAO`, `UF` ou `PAIS`), nulo quando ficou no nível pedido.
- `totalVisivel = false` quando a conta fecharia um número menor que k (E10): nesse caso `clientes` (total) e todos os `percentual` vêm `null`.
- `percentual` = `clientes` do grupo sobre o `clientes` total do recorte, 1 casa decimal.

### Banco · `V?__regiao_sem_cep.sql` (uma só migração, depois da `V?` da Fatia 10)

1. `consentimento_finalidade_ck` passa a aceitar `'REGIAO_ALCANCE'` (além de `'MARKETING_CEP'`).
2. `usuario`: `ADD regiao_municipio_ibge char(7)` (e `bairro`, `cidade`, `uf` continuam como a localidade do perfil); `DROP COLUMN cep` e `usuario_cep_ck`.
3. `reserva`: `ADD regiao_municipio_ibge char(7)`, `ADD regiao_consentimento_versao varchar(20)`, `CHECK (regiao_municipio_ibge IS NULL OR regiao_municipio_ibge ~ '^[0-9]{7}$')`; o índice parcial `reserva_alcance_ix` ganha `regiao_municipio_ibge`.
4. `reserva_guest`: `DROP COLUMN cep` e `reserva_guest_cep_ck`.
5. **Dado antigo sem prova de consentimento** (art. 8º, § 2º): `UPDATE reserva SET regiao_* = NULL` onde `regiao_consentimento_versao IS NULL`, e a mesma limpeza da localidade de `usuario` sem linha `REGIAO_ALCANCE` aceita (D-3).
6. Nenhuma coluna `cep` sobra em nenhuma tabela. A migração já mergeada (`V6`) **não é editada**.

### Fonte dos códigos IBGE (viabilidade conferida em 23/11/2026)

| Fonte | O que devolve | Custo | Uso |
|---|---|---|---|
| **ViaCEP** (já é o provedor principal, ADR 0008) | o campo `ibge` com o código do **município** (7 dígitos); conferido com o CEP 06010-000: `3534401` (Osasco) | grátis, sem chave | principal |
| **BrasilAPI v2** (`/api/cep/v2/{cep}`, já é o fallback) | `ibge.city` (7 dígitos) e `ibge.state`; mesmo CEP: `3534401` | grátis, sem chave | fallback |
| **Tabela embutida** `backend/src/main/resources/ibge/municipios.csv` | `codigo;municipio;uf;regiaoImediataCodigo;regiaoImediataNome` para os **5.571** municípios e **510** regiões imediatas (consulta ao serviço de localidades do IBGE em 23/11/2026) | zero, no próprio jar | traduz município → região imediata e valida o código |

- O CSV é **gerado uma vez**, por `scripts/gerar-tabela-ibge.sh` (baixa do serviço público de localidades do IBGE, sem chave), **conferido na revisão** e commitado com uma linha de cabeçalho `# fonte, data e contagem`. **Nenhuma chamada ao IBGE, à ViaCEP ou à BrasilAPI é feita para traduzir região em tempo de execução**: o CEP é consultado só no cadastro ou na reserva (como hoje, ADR 0008) para pegar bairro, município e o código. Atualiza-se a tabela só quando o IBGE mudar a divisão (a classe `TabelaIbge` guarda a `versao` e o log da subida mostra só ela).
- O ADR 0014 diz "tabela estática versionada": é exatamente este arquivo.
- Provedor sem `ibge` (o CEP "geral" de cidade pequena ou resposta incompleta): procura-se o município por **nome sem acento e UF** na tabela; achou um só, usa; **zero ou mais de um** = sem município (o cliente entra no alcance só pela UF) e a reserva **segue** (R31).

## 3. Decisões de desenho

| # | Tema | Decisão | Por quê |
|---|---|---|---|
| E1 | Base legal | **Consentimento** (LGPD art. 7º, I), não legítimo interesse. A caixinha nasce **desmarcada**, com o texto do ADR 0014 (revisado na revisão jurídica), a versão em `regiao_consentimento_versao` (visitante) ou em `consentimento` (conta, finalidade `REGIAO_ALCANCE`), e o canal `APP_PRIVACIDADE_CONTATO` ao lado do campo | ADR 0014, 1 |
| E2 | CEP | **Nunca é guardado** e só é enviado com a caixinha marcada. Sem consentimento o backend ignora o campo (nem consulta o provedor). Ficam `bairro`, `cidade`, `uf` e `regiao_municipio_ibge` | minimização (art. 6º, III) |
| E3 | Revogar | Pela tela de consentimentos, pelo `PUT /me` sem CEP ou pelo `DELETE /agendamentos/guest/{id}/regiao`: apaga a localidade do perfil **e** `regiao_*` de **todas** as reservas da conta; a pessoa sai do bairro e passa a `semRegiao`. A região do visitante também segue a retenção da reserva de visitante (90 dias do fim, D99) | art. 8º, § 5º |
| E4 | Quem é uma pessoa | O alcance conta **pessoas distintas**. Conta: `usuario.id`. Visitante: `whatsapp` normalizado ou, se faltar, `email` em minúsculas. Visitante e conta com o mesmo WhatsApp **ou** e-mail são **uma** pessoa (comparação por HMAC calculado só durante a consulta, com a chave `app.alcance.chave-de-pessoa` que só vive na memória, nunca em coluna nem em log). **Nenhum dado novo é criado** para isso. O `agenda` não lê `usuario`: pede à `conta` (`Contas`) os identificadores já normalizados | ADR 0014, 4; art. 6º, III |
| E5 | Lugar da pessoa | Cada pessoa entra **um só lugar**: o da reserva **mais recente com região** (de qualquer barbeiro e período, ainda dentro da retenção). A pessoa conta num recorte (barbeiro, período) se tiver **pelo menos uma** reserva `CONFIRMADO` ou `CONCLUIDO` nele; o lugar dela é o mesmo em qualquer recorte | mantém os recortes comparáveis (um subconjunto do outro) e impede contar a mesma pessoa em dois bairros |
| E6 | k | `app.alcance.k`, padrão **5**, **mínimo 3** (abaixo disso a aplicação não sobe). Decisão de produto: mudar exige uma D nova. Vale em **cada recorte** (barbeiro × período × nível) e nos grupos "Outros" | ADR 0014, 2 |
| E7 | Subir de nível | Bairro → município → região imediata → UF → "Outros estados". Lugar com menos de k vai para o "Outros" do pai (`Outros bairros` do município, `Outros municípios da região X`, `Outras regiões de UF`, `Outros estados`). Se o "Outros" também tem menos de k, **sobe junto** para o nível de cima, até "Outros estados". Sem município (E2): entra direto na UF. O barbeiro com poucos clientes **sobe sozinho**: um barbeiro com 6 clientes em 6 bairros mostra o município (se 5 ou mais) ou a região | pedido do Kevin; ADR 0014, 3 |
| E8 | O nível pedido | `nivel` escolhe **qual nível aparece nas linhas** (UF, região ou município); cada linha traz os `filhos` do nível de baixo. Quem sobe aparece no "Outros" do nível pedido, com `agrupadoEm` dizendo até onde subiu | o seletor da tela |
| E9 | Supressão complementar **dentro de uma resposta** | Em cada grupo, `sobra = clientes do grupo − soma dos filhos nomeados`. Se `sobra` está entre 1 e k − 1, o **menor filho nomeado** (empate: ordem alfabética) é **rebaixado** para dentro de "Outros" até a sobra ser 0 ou pelo menos k. Assim nenhum número menor que k pode ser calculado por subtração entre o grupo e os filhos. Quando não houver o que rebaixar, o "Outros" mostra "menos de k" (`abaixoDoLimite = true`, sem número) **e** o total do grupo some (`null`) — que é o texto literal do ADR 0014, usado só como último recurso (D-1) | ADR 0014, 3 (supressão complementar) |
| E10 | Total do recorte | O total e os percentuais só aparecem (`totalVisivel = true`) se o que ficou escondido no topo (`semRegiao` e "Outros estados") for **0 ou pelo menos k** cada um. Senão o total e **todos** os `percentual` vêm `null` (com o total escondido o percentual denunciaria a contagem) | ADR 0014, 3 |
| E11 | Recortes diferentes não se subtraem | Uma célula do recorte com filtro de barbeiro **F** só é publicada como número se a diferença para o recorte "todos os barbeiros" no mesmo lugar e período, `n(todos) − n(F)`, for **0 ou pelo menos k**; senão a célula é tratada como menor que k e **sobe de nível** (e o teste se repete no nível de cima). A mesma regra vale entre as janelas padrão (30 dias, 90 dias, 12 meses): `n(janela maior) − n(janela menor)` em 0 ou ≥ k. O barbeiro convidado só enxerga o recorte dele (não há "todos"), então a regra vale para o Dono/Admin | ADR 0014, critério 6; sem isso, "todos" menos "o barbeiro X" revela quem é só dos outros |
| E12 | Vitrine pública | Só **municípios** com pelo menos k pessoas, e dentro deles os bairros com pelo menos k, com `faixaPercentual` = porcentagem sobre o município **arredondada para baixo em múltiplos de 5** (`"15"`), ou `"<5"` abaixo de 5%. O "Outros bairros" aparece só se tiver k ou mais, **sem faixa**. Município com menos de **20** pessoas: só os nomes dos bairros que passam no k, sem faixa. **Nunca** número, total ou contagem de pessoas; `totalBairros` conta só os bairros que passam | ADR 0014, 4 |
| E13 | Tudo no backend | O front recebe o resultado pronto; nenhum número bruto sai para ele calcular. Painel autenticado, **sem cache** (`Cache-Control: no-store`); vitrine em cache de 1 h e 30 consultas por minuto por IP (D111, intactos) | ADR 0014, 4 |
| E14 | O que entra na conta | Só `CONFIRMADO` e `CONCLUIDO` com `inicio` no período (E31 da 6C); a reserva **manual não conta** (D127: não tem região nem consentimento); `EXPIRADO` e `CANCELADO` ficam fora | D108 mantida |
| E15 | Barbeiro convidado | Vê **só as pessoas que têm reserva com ele** e o lugar delas (E5), com o **mesmo k**. O `AcessoAgenda.proprio` (Fatia 10) dá o barbeiro; o `barbeiroId` da query é ignorado e o corpo da resposta não traz sinal disso. Barbeiro inativo ou sem vínculo = `404`. Um barbeiro com poucos clientes vê o município, a região ou "menos de k", **nunca** um bairro pequeno | pedido do Kevin; ADR 0013 |
| E16 | Tabela do IBGE | O arquivo da seção 2, carregado **uma vez** na subida em memória (`Map` imutável). Uma classe de arquitetura proíbe cliente HTTP no pacote `ibge`. O teste confere 5.571 municípios, 510 regiões imediatas, as 27 UFs, e que todo município aponta uma região | custo zero; ADR 0014, Consequências |
| E17 | Registro do consentimento | Linha nova a cada aceite ou revogação em `consentimento` (histórico só de inserção, R28). A tela mostra o estado atual em "Meus dados", ao lado do de marketing | art. 8º, § 2º |
| E18 | Log | Nunca o CEP, o bairro de uma pessoa, o WhatsApp ou o e-mail; a subida e o alcance só logam contagens e a `versao` da tabela do IBGE (R30) | R30 |

## 4. Critérios de aceite

Convenção: as tabelas de teste usam um **IBGE de teste** pequeno (Osasco e Carapicuíba na região imediata de São Paulo, Campinas em outra, Rio de Janeiro no RJ) e um `Clock` fixo. O teste de propriedade usa a tabela de teste e **200 sementes fixas** (sem dependência nova).

### 11A · Consentimento e região sem CEP

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A1 | cadastro, `PUT /me` e reserva de visitante com `cep` e **sem** `autorizaRegiao` | enviar | `201` ou `200` como antes; nenhuma região gravada; **zero** chamadas à ViaCEP e à BrasilAPI; nenhum CEP em nenhuma coluna | `RegiaoConsentidaIT` |
| A2 | `autorizaRegiao = true`, `versaoTextoRegiao` vigente, CEP de Osasco/Centro | cadastro, `PUT /me` e reserva de visitante | grava bairro, município, UF, `regiao_municipio_ibge = 3534401` e a versão; conta ganha linha `REGIAO_ALCANCE` aceita; o **esquema do banco não tem nenhuma coluna `cep`** (consulta ao `information_schema`) | `RegiaoConsentidaIT`, `EsquemaSemCepIT` |
| A3 | `versaoTextoRegiao` velha, ou faltando com `autorizaRegiao = true` | enviar | `400 VALIDACAO_INVALIDA` em `versaoTextoRegiao`; nada gravado | `RegiaoConsentidaIT` |
| A4 | conta com região e 3 reservas | `PUT /me/consentimentos/regiao` com `aceito = false` | `200`; localidade do perfil e `regiao_*` das 3 reservas ficam `NULL`; o alcance seguinte não conta a pessoa em bairro nenhum (passa a `semRegiao`); a linha de revogação fica no histórico | `RevogarRegiaoIT` |
| A5 | conta com região | `PUT /me` sem CEP (ou `autorizaRegiao = false`) | mesmo efeito do A4 | `RevogarRegiaoIT` |
| A6 | reserva de visitante com região | `DELETE /agendamentos/guest/{id}/regiao` com o `X-Guest-Token` certo · de novo · token errado · sem token · token de outra reserva | `204` e região apagada · `204` · `404` · `404` · `404`, os três `404` com o mesmo corpo; a reserva em si segue intacta | `RevogarRegiaoGuestIT` |
| A7 | conta com consentimento | `GET /me` e `GET /me/dados` | `endereco.cep` e `cep` da exportação são `null`; aparece `regiaoAutorizada`; a exportação lista o consentimento `REGIAO_ALCANCE` | `PerfilSemCepIT` |
| A8 | todo o fluxo A1 a A7 e o ViaCEP | ler o log | nenhum CEP, bairro de pessoa, WhatsApp ou e-mail (`SemDadoPessoalNoLogIT` estendido, R30) | `SemDadoPessoalNoLogIT` |
| A9 | banco na `V7` com reservas e contas com região, uns com prova de consentimento e outros sem | rodar a migração nova | sem prova: região **anulada**; com prova: preservada; nenhuma coluna `cep`; `flyway validate` limpo; segunda execução sem efeito | `MigracaoRegiaoSemCepIT` |
| A10 | visitante com região e reserva de 91 dias (relógio) | job `RetencaoDeDados` | a região da reserva é apagada junto com o contato (E3); o alcance de 12 meses deixa de contar essa pessoa | `RetencaoJobIT` |

### 11B · Pessoas distintas, k e subida de nível (backend)

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A11 | uma pessoa com 5 atendimentos no mesmo bairro | alcance do painel | conta **1** cliente; sozinha não libera o bairro nem a faixa pública | `PessoasDistintasIT` |
| A12 | conta e visitante com o mesmo WhatsApp · o mesmo e-mail · e-mails que só diferem em maiúsculas | alcance | **uma** pessoa nos três casos; nenhuma tabela ou coluna nova guarda o hash (conferido por diff do esquema) | `PessoasDistintasIT` |
| A13 | pessoa com reserva no Centro e, depois, outra em Presidente Altino | alcance | conta **uma vez**, em Presidente Altino (a mais recente com região) | `PessoasDistintasIT` |
| A14 | Centro com 7, Vila A com 3 e Vila B com 2 pessoas em Osasco | `nivel = MUNICIPIO` | Osasco 12; Centro 7; "Outros bairros" 5 (Vila A + Vila B somam ≥ k, fica no município) | `SubidaDeNivelIT` |
| A15 | Osasco com 5 (todas em bairros pequenos de 2, 2 e 1) | `nivel = MUNICIPIO` | Osasco 5 com `filhos = [Outros bairros 5]`; nenhum bairro nomeado | `SubidaDeNivelIT` |
| A16 | Osasco com 3, Carapicuíba com 3 (mesma região imediata) | `nivel = MUNICIPIO` | os dois sobem: "Outros municípios da região imediata de São Paulo" com 6, `agrupadoEm = REGIAO` | `SubidaDeNivelIT` |
| A17 | região de São Paulo com 3 em "Outros municípios" e Campinas com 3 em SP | `nivel = MUNICIPIO` | sobem juntas: "Outras regiões de SP" com 6, `agrupadoEm = UF`; e, se ainda menos que k, "Outros estados" | `SubidaDeNivelIT` |
| A18 | 2 pessoas no RJ e as demais em SP | `nivel = ESTADO` | RJ vira "Outros estados" com **menos de k** (`abaixoDoLimite = true`, `clientes = null`) | `SubidaDeNivelIT` |
| A19 | pessoas sem município (cidade ambígua) | alcance | entram direto na UF; o cadastro nunca falhou (R31) | `SubidaDeNivelIT` |
| A20 | município com 8: Centro 5 e dois bairros de 2 e 1 (sobra 3 < k) | `nivel = MUNICIPIO` | o menor nomeado (Centro, 5) é **rebaixado**: `filhos = [Outros bairros 8]`; nenhum número menor que k e nenhuma subtração dá 1 a 4 | `SupressaoComplementarIT` |
| A21 | qualquer resposta | procurar no JSON | nenhum valor de 1 a k − 1 como `clientes`, `percentual`, `0`, `null` com sentido de contagem nem `NaN`; valores abaixo de k só como `abaixoDoLimite = true` | `SupressaoComplementarIT` |
| A22 | `semRegiao` com 2 · `semRegiao` com 0 · "Outros estados" com 3 | alcance | `totalVisivel = false` e total e percentuais `null` · `totalVisivel = true` · `totalVisivel = false` | `TotalVisivelIT` |
| A23 | `app.alcance.k = 3` e `= 8` · `= 2` | alcance · subir a aplicação | os limites mudam sem mudar código · a aplicação **não sobe** com k menor que 3 | `KConfiguravelIT` |
| A24 | tabela embutida | `TabelaIbgeTest` | 5.571 municípios, 510 regiões imediatas, 27 UFs; Osasco `3534401` → região `350001` São Paulo; todo município tem região; código fora da tabela devolve vazio | `TabelaIbgeTest` |
| A25 | pacote `ibge` e o alcance | `ArquiteturaTest` | nenhum `RestClient`, `HttpClient` ou `WebClient` no pacote `ibge` e nenhuma chamada de rede para traduzir região | `ArquiteturaTest` |
| A26 | ViaCEP com `ibge` · só BrasilAPI v2 com `ibge.city` · provedor sem código mas município único na tabela · nome ambíguo · provedores fora | cadastro ou reserva com consentimento | município resolvido pelo código · idem · resolvido pelo nome e UF · sem município (só UF) · sem região; **nunca** derruba o cadastro nem a reserva | `CepIbgeIT` |
| A27 | reserva manual, `EXPIRADO` e `CANCELADO` | alcance | não contam (D127, E31) | `PessoasDistintasIT` |

### 11C · Vitrine pública em faixas

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A28 | qualquer base | `GET /alcance` sem token | nenhum número de pessoas no JSON (`atendimentos` e totais `null` e `deprecated`); só nomes e `faixaPercentual` em múltiplos de 5 ou `"<5"`; o "Outros bairros" vem **sem** faixa | `VitrineEmFaixasIT` |
| A29 | município com 19 pessoas · com 20 | `GET /alcance` | só os nomes dos bairros que passam no k, sem faixa · as faixas aparecem | `VitrineEmFaixasIT` |
| A30 | bairro com 14,9% · 15,0% · 4,9% do município | faixa | `"10"` · `"15"` · `"<5"` (arredonda para baixo) | `VitrineEmFaixasIT` |
| A31 | município com 4 pessoas | `GET /alcance` | o município **não aparece**; um "Outros bairros" abaixo de k também não | `VitrineEmFaixasIT` |
| A32 | 5 atendimentos de uma pessoa só | `GET /alcance` | não libera bairro nem faixa (A11 na vitrine) | `VitrineEmFaixasIT` |
| A33 | duas chamadas com um dado novo no meio · 31 chamadas por minuto | `GET /alcance` | a segunda vem do cache de 1 h (`Ticker` de teste) · a 31ª é `429` com `Retry-After` (D111 intacto) | `VitrineEmFaixasIT` |

### 11D · Painel por nível (Dono/Admin e barbeiro convidado)

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A34 | ADMIN, dados em SP e RJ | `nivel = ESTADO` · `REGIAO` · `MUNICIPIO` (padrão) | linhas por UF com regiões embaixo · por região imediata com municípios embaixo · por município com bairros embaixo; `nivel` inválido é `400` | `AlcancePorNivelIT` |
| A35 | ADMIN | `?uf=SP` | só SP, nos três níveis | `AlcancePorNivelIT` |
| A36 | ADMIN, barbeiros A (10 clientes em Osasco) e B (3 em Osasco) | `?barbeiroId=<B>` | o k vale no recorte de B: Osasco 3 sobe para a região (`agrupadoEm = REGIAO`) ou "menos de k"; nunca "3" | `AlcancePorBarbeiroIT` |
| A37 | BARBEIRO A | `GET /admin/alcance` e `?barbeiroId=<B>` | só pessoas que reservaram com A; o filtro é ignorado e nada do corpo denuncia isso; B nunca aparece | `AlcancePorBarbeiroIT` |
| A38 | BARBEIRO A com 6 clientes em 6 bairros diferentes | painel | nenhum bairro nomeado; sobe para o município (6) ou a região | `AlcancePorBarbeiroIT` |
| A39 | barbeiro A inativo · conta sem vínculo · CLIENTE · sem token | painel | `404` · `404` · `403` · `401` | `AlcancePorBarbeiroIT` |
| A40 | ADMIN, "todos" com Osasco 10 e `?barbeiroId=A` com Osasco 8 (diferença 2) | os dois pedidos | no pedido de A, o Osasco **sobe de nível** (a diferença 2 seria um número menor que k); com diferença 0 ou ≥ 5 a célula aparece | `ComplementoPorBarbeiroIT` |
| A41 | `periodo` padrão, `ULTIMOS_90_DIAS`, `ULTIMOS_12_MESES` e `de`/`ate` antigos | painel | respeitam os períodos; `de` depois de `ate` e janela acima de 366 dias seguem `400` (D108); a diferença entre janelas padrão também cumpre a E11 | `AlcancePorNivelIT` |
| A42 | o painel respondido | cabeçalhos | `Cache-Control: no-store`; o `percentual` tem 1 casa e é sobre o total do recorte | `AlcancePorNivelIT` |
| A43 | **teste de propriedade** (200 sementes, k = 3, 5 e 8): pessoas aleatórias (conta e visitante, 1 a 3 reservas, 1 a 4 barbeiros, alguns revogaram, alguns sem município) | juntar **todas** as respostas públicas e do painel possíveis (vitrine; painel por `nivel` × `uf` × barbeiro {todos, cada um} × as três janelas) | (P1) nenhum número publicado é positivo e menor que k; (P2) para todo par "recorte maior, recorte menor" (todos × um barbeiro; janela maior × menor; grupo × soma dos filhos nomeados) a diferença é 0 ou ≥ k; (P3) quando `totalVisivel`, total − soma dos visíveis é 0 ou ≥ k; (P4) na vitrine, nenhum campo é número de pessoas e, para todo total T ≥ 20 compatível com as faixas, o resto escondido do município não fica determinado como positivo e menor que k | `AlcancePropriedadeTest` |
| A44 | o teste A43 com a regra da E11 e da E9 **desligadas** (mutação) | rodar | o teste **falha**: prova que ele pega o vazamento por subtração | `AlcancePropriedadeTest` |
| A45 | `openapi.yaml` 1.9.0 · `scripts/conferir-spec.sh 11 --exigir` | lint e conferência | 0 erros; cada `A#` tem teste com o número; exemplos reais no `AlcancePainel` | `ContratoExemplosDeErroTest` |
| A46 | política de privacidade | `GET /politica-privacidade` | versão **1.2**: consentimento, "clientes", faixas, retenção da região, canal de contato; texto revisado na revisão jurídica | `PoliticaPrivacidadeIT` |

### Telas (front)

| # | Dado | Quando | Então | Teste |
|---|---|---|---|---|
| A47 | cadastro, perfil e checkout do visitante | abrir | a caixinha de consentimento **desmarcada**, com o texto do ADR 0014 e o canal de contato; o campo de CEP só envia com a caixinha marcada; sem marcar, o CEP nem sai do navegador | `Consentimento` |
| A48 | confirmação e consulta da reserva do visitante | abrir | o link "Retirar minha autorização de região" chama o BFF, que usa o `tokenGuest` e apaga na hora (A6); estado de sucesso e de erro | `ConfirmacaoRegiao` |
| A49 | "Meus dados" | abrir | o consentimento de região mostrado e revogável, ao lado do de marketing, com confirmação em uma linha | `MeusDados` |
| A50 | home | abrir e clicar num município | lista de municípios; ao clicar, os bairros com "cerca de 15%", "menos de 5%" ou só o nome (município com menos de 20); **nenhum** número de pessoas; o bloco some se a vitrine vier vazia ou der `429` | `HomeAlcance` |
| A51 | painel do Dono/Admin | abrir "Alcance" | seletor de nível (Estado, Região, Município), filtro de barbeiro, de UF e de período; `<table>` com `<caption>`, linhas e filhos; "menos de 5" em texto, sem barra | `PainelAlcance` |
| A52 | painel do barbeiro convidado | abrir "Alcance" | o mesmo seletor e período, **sem** filtro de barbeiro, com o aviso "só os seus clientes"; nunca mostra bairro com menos de k | `PainelAlcance` |
| A53 | `totalVisivel = false` | abrir | o total e as porcentagens somem e a tela explica em uma frase ("a soma ficou escondida para proteger os clientes") | `PainelAlcance` |
| A54 | acessibilidade das telas novas | teclado e leitor | rótulos, foco, `role="alert"` nos erros, contraste do design system (axe) | `PainelAlcance` |

## 5. Entregas

- **`cep`**: `Endereco` ganha `municipioIbge`; `ViaCep` e `BrasilApi` leem `ibge` e `ibge.city` (cliente BrasilAPI na versão v2); `ResolverRegiao` (caso de uso: resolve o município pelo código, ou por nome e UF).
- **`ibge`** (novo pacote do `cep` ou módulo próprio, sem rede): `TabelaIbge` (carrega o CSV na subida, `versao`), `municipios.csv`, `scripts/gerar-tabela-ibge.sh`.
- **`conta`**: `regiao_municipio_ibge`; `Contas.regiaoDoCliente`, `revogarRegiao`; `Contas.identificadoresDePessoa` (para o `agenda`, E4); consentimento `REGIAO_ALCANCE`.
- **`agenda`**: `alcance/ConsultaAlcance` reescrita (pessoas distintas, lugar da pessoa, níveis); `alcance/NiveisDeAlcance` (subida, E9, E10, E11); `AlcanceDaAgenda` e `AlcancePublicoController`; revogação da região do visitante; `regiao_*` e a versão do consentimento na reserva; `RetencaoDeDados` apaga a região do visitante.
- **`admin`**: `AlcanceController` com `nivel`, `uf` e `periodo`.
- **`privacidade`**: política 1.2 e o texto da caixinha (`consentimento-1.2.md`).
- **Contrato**: `openapi.yaml` e `contrato-api.md` 1.9.0, histórico de versões.
- **Banco**: `V?__regiao_sem_cep.sql`.
- **Front**: caixinha, link de retirar, "Meus dados", home com drill e faixas, painel com seletor.
- **Docs**: guia de testes, `docs/contexto.md` (R# novo, H# do k, ADR 0014 atualizado como implementado), `decisoes.md` (a regra de 19/11 revogada), `docs/fluxo-ponta-a-ponta.md` e o roteiro de visitantes.

### Telas (design system)

O visual vem do design system e do canvas; **não decido o visual aqui**. O `frontend/design/README.md` lista "Painel: alcance" (`PainelAlcance.dc.html`, só cidade e bairro) e "Home" (só nomes) como desenhadas, **mas sem os itens abaixo**. Telas que **precisam de desenho** (marcar "aguarda design"):

| Tela | Situação | Observação |
|---|---|---|
| **Painel: alcance com seletor de nível** (Estado, Região, Município), filtro de barbeiro (só Dono/Admin), de UF e de período | aguarda design | tabela hierárquica com linhas e `filhos`; estados: "menos de 5", "Outros ... (subiu para a região)", `totalVisivel = false`, vazio e carregando |
| **Painel do barbeiro convidado: alcance** | aguarda design | a mesma tela sem filtro de barbeiro e com o aviso "só os seus clientes" |
| **Home: "De onde vêm nossos clientes" com município clicável e bairros em faixas** | aguarda design | "cerca de 15%", "menos de 5%", só nome; estado vazio; sem números |
| **Caixinha de consentimento de região** (cadastro, perfil e checkout do visitante) com o canal de contato | aguarda design | texto longo, desmarcada, foco e erro |
| **Link "Retirar minha autorização de região"** (confirmação e consulta da reserva) | aguarda design | confirmação em uma linha e o estado de sucesso |
| **Meus dados: consentimento de região** | aguarda design | no molde do de marketing |

## 6. Riscos e cuidados

- **Reidentificação por subtração.** O ADR 0014 pede o teste de propriedade (A43); o A44 prova que ele detecta o vazamento. Se um caso passar no teste mas parecer perigoso, a regra aperta (k maior, nível mais alto), nunca o teste afrouxa.
- **Janelas livres de período.** A E11 cobre as três janelas padrão e o filtro por barbeiro; um `de`/`ate` arbitrário de um Dono/Admin autenticado ainda pode subtrair janelas (D-4).
- **A região do visitante some em 90 dias** (E3, ADR 0014). Em consequência, o alcance de 12 meses só tem visitantes dos últimos 90 dias (D-5).
- **Dado antigo sem consentimento** é anulado pela migração (D-3): vale rodar uma contagem antes e depois no beta.
- **Identificador de pessoa.** O HMAC de WhatsApp e e-mail existe só em memória durante a consulta e nunca é log, coluna ou resposta; a chave `app.alcance.chave-de-pessoa` é segredo de ambiente (como `GATEWAY_SEGREDO_WEBHOOK`).
- **Visitante anonimizado** não tem mais WhatsApp nem e-mail: não dá para saber se é a mesma pessoa de outra reserva (D-2).
- **Desempenho.** A consulta junta reservas, pessoas e níveis em memória sobre o período; com o volume de uma barbearia é pequeno. Se crescer, o índice parcial da migração e o cache do painel por período resolvem.
- **Barbeiro convidado que conhece os clientes.** O k protege pouco quando a pessoa foi atendida por ele; vale o limite de **recorte** (ele nunca vê o dos outros) e a regra "nunca um bairro pequeno".

### Checklist LGPD (aplicando o agente jurídico; **não substitui parecer jurídico**; as fontes oficiais, planalto.gov.br e gov.br/anpd, ainda precisam ser consultadas na revisão jurídica antes do merge)

| Ponto | Situação | Observação |
|---|---|---|
| Base legal | 🟢 ok | consentimento, art. 7º, I (ADR 0014); sem legítimo interesse, então sem o registro do art. 10 |
| Consentimento livre, informado, revogável e provado (art. 8º) | 🟠 risco | caixinha desmarcada, não obrigatória, versão e data gravadas, link de revogação do visitante; **o texto final da caixinha e da política 1.2 passam pela revisão jurídica** antes do merge |
| Minimização | 🟢 ok | CEP não é guardado (A2); só bairro, município, UF e código IBGE |
| Retenção | 🟠 risco | conta: enquanto existir e enquanto o consentimento valer; visitante: 90 dias do fim da reserva (D-5) |
| Direitos do titular (exportar e excluir) | 🟢 ok | `GET /me/dados` mostra a localidade e o consentimento; `DELETE /me` apaga a região |
| Transparência | 🟠 risco | política 1.2 com "clientes" e as faixas; revisão jurídica |
| Dado de terceiros digitado pelo barbeiro | 🟢 ok | a reserva manual não entra no alcance (E14) |
| Reidentificação no alcance (k-anonimato) | 🟠 risco | **k é prática de anonimização, não exigência da lei** (ADR 0014); mitigado por A11 a A22 e A43 |

## 7. Dúvidas para o Kevin

| # | Dúvida | Proposta (padrão se o Kevin aprovar sem mexer) |
|---|---|---|
| D-1 | Para o "sobra menor que k" dentro de um grupo (E9), seguir o texto literal do ADR (o "Outros" mostra "menos de k" e **o total do grupo some**) ou **rebaixar o menor filho** para o "Outros" (o total e o município continuam visíveis)? | **Rebaixar o menor filho** (E9): mantém o número do município, que é o que o dono quer ver, e continua sem expor nada menor que k. O texto literal fica como último recurso. Se o Kevin preferir o literal, é trocar a E9 e o A20 |
| D-2 | O visitante **anonimizado** (depois de 90 dias) não tem WhatsApp nem e-mail: como contar as pessoas? | **A região do visitante some junto com o contato** (E3, A10): então ele simplesmente deixa de contar. Assim ninguém é contado duas vezes e nenhum dado novo é guardado. A alternativa (guardar um hash do WhatsApp) cria dado pessoal novo e foi recusada pelo ADR |
| D-3 | A migração **anula** a região de reservas e perfis antigos sem prova de consentimento? | **Sim.** O beta tem dados de teste; sem a prova do art. 8º, § 2º não dá para sustentar o dado. O Kevin confere uma contagem antes e depois. Se já houver cliente real com região, a alternativa é pedir um novo consentimento no próximo login |
| D-4 | O Dono/Admin pode escolher qualquer `de`/`ate`; a diferença entre duas janelas arbitrárias pode revelar um grupo pequeno. Como tratar? | **Oferecer `periodo` com três janelas padrão** (30 dias, 90 dias, 12 meses), que a regra E11 e o teste A43 cobrem, **manter `de`/`ate` por compatibilidade** e registrar o risco residual como aceito para um Dono/Admin autenticado. Se o Kevin quiser fechar, a próxima major remove `de` e `ate` do painel |
| D-5 | A região do visitante some em 90 dias (ADR 0014): o alcance de 12 meses fica só com contas e com os visitantes recentes. Aceito? | **Sim, como beta**: a conta (com consentimento) cobre quem volta, e o visitante só alimenta o recente. Se o Kevin quiser o histórico de visitantes, é um consentimento com prazo maior e um parecer jurídico |
| D-6 | O barbeiro convidado vê o alcance dos **seus** clientes, mesmo que ele os conheça. Basta o k? | **Sim**, o k e o "nunca um bairro pequeno" (E15, A38). A Fatia 10 já impede ver clientes de outro barbeiro |
| D-7 | O teste de propriedade usa 200 sementes fixas sem biblioteca nova. Aceita? | **Sim** (sem dependência nova, custo zero). Se quiser `jqwik`, entra por outra PR |
| D-8 | A tabela do IBGE é conferida só na revisão; quem a atualiza quando o IBGE mudar a divisão? | **O Kevin**, com o script (`scripts/gerar-tabela-ibge.sh`) e uma PR de uma linha de cabeçalho e a contagem nova; um teste falha se a contagem não bater com o cabeçalho |
| D-9 | Ordem de merge e numeração com a catálogo e a Fatia 10? | **Catálogo → Fatia 10 → Fatia 11.** A versão do contrato (`1.8.0`, `1.9.0`) e o número da `V?` seguem o merge real |

## 8. Pronto quando

- [ ] todos os critérios A1 a A54 com teste verde no `./mvnw verify` (e `scripts/conferir-spec.sh 11 --exigir` no CI); o A43 e o A44 rodam em k = 3, 5 e 8
- [ ] contrato 1.9.0 com `redocly lint` em 0 erros e o histórico de versões atualizado
- [ ] a tabela do IBGE conferida na revisão e sem nenhuma chamada de rede para traduzir região
- [ ] nenhum `cep` em nenhuma tabela, em nenhum log e em nenhuma resposta
- [ ] texto da caixinha e da política 1.2 revisados na revisão jurídica
- [ ] telas "aguarda design" desenhadas e implementadas; fluxo ponta a ponta com um barbeiro convidado de poucos clientes (saída colada nos docs)
- [ ] [checklist da PR](checklist-pr.md) completo; a regra de "sem limite" de 19/11 revogada em `decisoes.md`
