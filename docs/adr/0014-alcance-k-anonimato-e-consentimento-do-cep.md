# ADR 0014 · Alcance: subir de nível, k = 5 e consentimento para a região do CEP

- Status: **aceito** (Alicerce, 20/11/2026, a partir da revisão do Cautelar que o Kevin autorizou)
- Cobre: F6 (CEP mínimo), F7 e F8 (alcance em níveis) do redesign de 20/11
- Relacionados: ADR 0009 (LGPD), D128 (backup 30 dias), R30 (nada pessoal em log)

## Contexto

O alcance mostra de onde vêm os clientes. Hoje um bairro com menos de 5 clientes some ou vira "Outros bairros". O Kevin teme que isso deixe a função inútil. O Cautelar lembra que **o k é prática de anonimização, não exigência da lei**, e pede uma única base legal para a região derivada do CEP.

## Decisão

### 1. Base legal: consentimento (LGPD art. 7º, I)

- A F6 **não** usa legítimo interesse, então não precisa do registro do art. 10.
- Na tela, o "CEP (opcional)" vem com uma **caixinha desmarcada**: "Autorizo usar só o meu bairro e a minha cidade, somados com outros clientes, para mostrar de onde vêm os clientes da barbearia. Não guardamos o CEP." O CEP só é enviado com a caixinha marcada.
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

## Critérios de aceite (para o SDD)

1. Uma reserva ou conta sem a caixinha marcada não grava região, e o CEP não é enviado.
2. Com a caixinha marcada, grava a região, o código IBGE e a versão do consentimento, e nenhuma tabela tem coluna `cep`.
3. A revogação apaga a região do perfil e das reservas da conta, e o alcance seguinte deixa de contá-la.
4. `GET /me` e a exportação nunca trazem o CEP, e nenhum log o contém (R30).
5. Um bairro com 3 clientes aparece como parte de "Outros bairros". Se esse grupo também tiver menos de k, sobe para a cidade.
6. **Teste de propriedade:** para dados aleatórios, nenhuma combinação de respostas públicas do alcance (totais, níveis e filtros) permite calcular uma contagem positiva menor que k.
7. `app.alcance.k` muda o comportamento sem mudar código.

## Consequências

- O alcance continua útil com poucos clientes, porque o dado sobe de nível em vez de sumir.
- Há um pouco mais de atrito no cadastro (a caixinha), em troca de uma base legal simples e revogável, sem registro do art. 10.
- A tabela estática do IBGE (municípios e regiões imediatas) é mais um arquivo para manter, atualizado só quando o IBGE mudar.
