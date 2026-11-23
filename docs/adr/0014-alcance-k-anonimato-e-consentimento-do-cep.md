# ADR 0014 · Alcance: subir de nível, k = 5 e consentimento para a região do CEP

- Status: **aceito** (Alicerce, 20/11/2026, a partir da revisão do Cautelar que o Kevin autorizou)
- Cobre: F6 (CEP mínimo), F7 e F8 (alcance em níveis) do redesign de 20/11
- Relacionados: ADR 0009 (LGPD), D128 (backup 30 dias), R30 (nada pessoal em log)

## Contexto

O alcance mostra de onde vêm os clientes. Hoje um bairro com menos de 5 clientes some ou vira "Outros bairros". O Kevin teme que isso deixe a função inútil. O Cautelar lembra que **o k é prática de anonimização, não exigência da lei**, e pede uma única base legal para a região derivada do CEP.

## Decisão

### 1. Base legal: consentimento (LGPD art. 7º, I)

- A F6 **não** usa legítimo interesse, então não precisa do registro do art. 10.
- Na tela, o "CEP (opcional)" vem com uma **caixinha desmarcada**: "Autorizo usar só meu bairro e minha cidade, somados aos de outros clientes, para mostrar no site de onde vêm os clientes da barbearia. Não guardamos o CEP. Posso retirar esta autorização quando quiser." (texto revisado pelo Cautelar). A caixinha não é obrigatória e o registro guarda a data e a versão do texto (art. 8º §2º). Ao lado do campo aparece o canal `APP_PRIVACIDADE_CONTATO`, que precisa estar preenchido em produção. A confirmação da reserva do visitante traz um link para retirar a autorização, que usa o `tokenGuest` da reserva, passa pelo BFF e apaga a região na hora (art. 8º §5º). O CEP só é enviado com a caixinha marcada.
- Para provar o consentimento (art. 8º, §2º):
  - na conta, o registro vai em `consentimento`, com a finalidade nova `REGIAO_ALCANCE` e a versão do texto;
  - no visitante, a reserva guarda `regiao_consentimento_versao` junto com a região.
- Revogar pela tela de consentimentos (ou pelo `PUT /me` sem CEP) apaga a localidade do perfil **e** a região das reservas dessa conta, que sai do alcance. A região do visitante segue a retenção da reserva de visitante.
- O CEP completo **não é guardado** por ninguém. As colunas `usuario.cep` e `reserva_guest.cep` são anuladas e removidas numa migração (a próxima V livre). Ficam só `regiao_bairro`, `regiao_cidade`, `regiao_uf`, `regiao_municipio_ibge` e, derivada deste último, a região imediata do IBGE (tabela estática versionada).
- Contrato: versão **minor**, sem quebra. O `cep` continua aceito na entrada. `PerfilResposta.endereco.cep` fica `deprecated` e sempre `null`, e o `cep` da exportação passa a aceitar `null`. Os dois saem na próxima major.
- Política de privacidade 1.2. O texto da caixinha e da política passam pelo Cautelar antes do merge.

### 2. k = 5, configurável

- O padrão é `app.alcance.k = 5`, documentado como **decisão de produto**. Mudar o valor exige uma D nova, e ele nunca pode ficar abaixo de 3.

### 3. Subir de nível em vez de sumir

- A ordem é **bairro → cidade → região imediata (IBGE) → UF → "Outros estados"**.
- Um lugar com contagem abaixo de k não some: entra em "Outros bairros" da cidade, "Outras cidades" da região, "Outras regiões" da UF ou "Outros estados".
- Se o próprio grupo "Outros" ficar abaixo de k, ele sobe junto para o nível de cima.
- O k vale em **cada combinação de filtro** (barbeiro, período e nível), e isso inclui os grupos "Outros".
- **Supressão complementar** (contra descobrir por subtração): quando o total do nível de cima aparece e só um lugar ficou escondido, o "Outros" mostra "menos de k", sem número. Se mesmo assim desse para calcular, o total do nível de cima também não aparece.
- Valores abaixo de k nunca aparecem como número, percentual, `0`, `null` ou "NaN".

### 4. Vitrine pública: porcentagem em faixas, sem total (pedido do Kevin, regras do Cautelar)

- Na home, o bloco "de onde vêm os clientes" mostra os municípios. Ao clicar num município, aparecem os bairros com **porcentagem**, não com número.
- Porcentagem só para bairro com **pelo menos k (5) clientes**. Os outros entram em "Outros bairros", **sem porcentagem**.
- A porcentagem é arredondada para baixo em **faixas de 5%** e aparece como "cerca de 15%". Abaixo de 5%, fica "menos de 5%". O "Outros bairros" nunca mostra porcentagem, então o "100 menos a soma" não revela um bairro, porque as faixas arredondam para baixo.
- Município com **menos de 20 clientes**: só os nomes dos bairros que passam no k, sem porcentagem.
- **Nunca** expor o total de clientes nem contagens absolutas no `GET /alcance` público. Na vitrine, o contador vira "clientes de N bairros", onde N conta só os bairros que passam no k.
- **O que se conta são pessoas diferentes, não atendimentos.** O k (5), o limite de 20 e as porcentagens usam clientes distintos, porque 5 atendimentos podem ser de uma pessoa só. Cliente com conta é contado pelo `usuario.id`. Convidado é contado pelo `whatsapp` normalizado, ou pelo `email` em minúsculas quando o WhatsApp faltar. Convidado e conta com o mesmo WhatsApp ou e-mail contam como uma pessoa só. A comparação usa um hash desses campos, feito só durante o cálculo. Nenhum dado novo é criado ou guardado para isso (art. 6º III). Cada pessoa entra no bairro do seu consentimento mais recente. A política de privacidade (1.1, linha 35) deve trocar "atendimentos" por "clientes" na versão 1.2.
- Tudo é calculado no backend. O front recebe as faixas prontas, nunca os números para calcular.
- Painel do Dono/Admin (autenticado): continua com contagens, aplicando as regras de k e de subida de nível da seção 3.
- Contrato: no `GET /alcance` público entra o campo `faixaPercentual` (string, ex.: `"15"` ou `"<5"`, nula quando não há porcentagem). O `atendimentos` e qualquer total passam a vir sempre `null`, marcados como `deprecated`, até a próxima major. Isso entra no mesmo minor da seção 1.

## Critérios de aceite (para o SDD)

1. Uma reserva ou conta sem a caixinha marcada não grava região, e o CEP não é enviado.
2. Com a caixinha marcada, grava a região, o código IBGE e a versão do consentimento, e nenhuma tabela tem coluna `cep`.
3. A revogação apaga a região do perfil e das reservas da conta, e o alcance seguinte deixa de contá-la.
4. `GET /me` e a exportação nunca trazem o CEP, e nenhum log o contém (R30).
5. Um bairro com 3 clientes aparece como parte de "Outros bairros". Se esse grupo também tiver menos de k, sobe para a cidade.
6. **Teste de propriedade:** para dados aleatórios, nenhuma combinação de respostas públicas do alcance (totais, níveis e filtros) permite calcular uma contagem positiva menor que k.
7. `app.alcance.k` muda o comportamento sem mudar código.
8. `GET /alcance` público nunca traz contagem nem total. Só traz nomes e `faixaPercentual` em múltiplos de 5 (ou `"<5"`), e o "Outros bairros" vem sem faixa.
9. Um município com 19 clientes devolve só os nomes dos bairros que passam no k, sem faixa. Com 20 ou mais, devolve as faixas.
10. O teste de propriedade do item 6 também cobre as faixas: a partir das respostas públicas, não dá para isolar a faixa de um bairro escondido.

11. Uma pessoa com 5 atendimentos no mesmo bairro conta como 1 cliente e, sozinha, não libera a porcentagem do bairro.
12. O link de revogação da confirmação apaga a região da reserva do visitante. Ele só funciona com o `tokenGuest` certo e, para qualquer outro, responde 404.
## Consequências

- O alcance continua útil com poucos clientes, porque o dado sobe de nível em vez de sumir.
- Há um pouco mais de atrito no cadastro (a caixinha), em troca de uma base legal simples e revogável, sem registro do art. 10.
- A tabela estática do IBGE (municípios e regiões imediatas) é mais um arquivo para manter, atualizado só quando o IBGE mudar.
