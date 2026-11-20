# 0008. CEP: ViaCEP + BrasilAPI, cache Caffeine e degradação graciosa

- Status: **aceita** (17/11/2026, implementada na Fatia 6A; números finais abaixo)
- Data: 2026-11-13
- Decisores: Kevin Richard

## Contexto
O cadastro aceita CEP opcional para preencher bairro e cidade. APIs públicas de CEP não têm SLA publicado e podem cair; o cadastro não pode falhar por isso (R31). O Render free tem 512 MB e uma instância só.

## Opções consideradas
1. Só ViaCEP, erro se cair.
2. **ViaCEP com fallback BrasilAPI, timeouts curtos, cache local e degradação.**
3. Base de CEPs própria no banco (não cabe no 1 GB do Aiven free nem compensa).

## Decisão
Opção 2, no módulo `cep` (`CepApi.buscar(Cep): Optional<Endereco>`):
- `RestClient` com **conexão 1 s e leitura 2 s** em cada provedor.
- Ordem: ViaCEP → BrasilAPI. Resposta inválida ou "CEP não encontrado" de um provedor não é falha de rede: CEP inexistente devolve vazio sem consultar o segundo (evita gastar 3 s à toa).
- **Cache Caffeine** (memória), dois caches: **encontrado** com TTL de **7 dias** e **inexistente** com TTL de **1 hora**, cada um com no máximo **10 mil entradas** (cabe com folga em 512 MB). Falha de provedor **nunca** entra no cache. Os valores vêm de `app.cep.*`, e o tempo do cache usa um `Ticker` injetável (falso nos testes, sem `Thread.sleep`).
- **Sem retry** dentro da requisição do usuário (o fallback já é a segunda tentativa); circuit breaker simples por provedor fica como melhoria se os logs mostrarem quedas frequentes.
- Sanitização antes de chamar: `01001000` ou `01001-000` (qualquer outro formato, inclusive `%2F` codificado, é `400 CEP_INVALIDO`, sem chamada externa, OWASP API7); só 8 dígitos chegam à URL do provedor.
- **O que é falha** (passa para o próximo provedor): timeout, erro de rede, `5xx`, `429`, outro `4xx`, corpo que não é JSON, corpo sem os campos esperados, `cep` diferente do pedido ou `uf` que não são 2 letras maiúsculas. **Não é falha**: ViaCEP com `{"erro": true}` ou `404` (o CEP não existe; a BrasilAPI nem é consultada). BrasilAPI com `404` depois de uma falha do ViaCEP também é "não existe".
- **Limite da rota pública:** 30 consultas por minuto por IP (`app.limites.cep-max` e `cep-janela`), `429` com `Retry-After`; é a primeira rota pública que gasta serviço de terceiros (hipótese H21).
- **Degradação:** se os dois falharem, o cadastro grava o CEP e deixa bairro, cidade e UF nulos; o perfil mostra "complete o endereço". `GET /cep/{cep}` responde `503 CEP_INDISPONIVEL` nesse caso (o front pode deixar digitar à mão). O cadastro consulta o `CepApi` **antes** de abrir a transação (nunca segura conexão do banco esperando rede) e engole qualquer erro (R31).
- O CEP nunca vai para o log, nem parcial, nem na mensagem de exceção: o log diz só "cep resolvido: sim/não" e a fonte, e a falha de provedor loga `provedor` e `tipo` (`TIMEOUT`, `REDE`, `HTTP_5XX`, `HTTP_429`, `HTTP_4XX`, `CORPO_INVALIDO`).
- Nos testes **nenhuma chamada sai para a internet**: as URLs base apontam para `localhost` (porta fechada) e os provedores são um `MockRestServiceServer` ligado ao `RestClient.Builder` dos clientes.

## Consequências
- ✅ Cadastro nunca cai por causa de CEP. Pior caso medido por configuração: conexão 1 s + leitura 2 s por provedor; com os dois lentos o teto teórico passa de 3 s (ver D91 em `decisoes.md`).
- ⚠️ Cache local some quando o Render hiberna (aceito: é só otimização).

## Gatilhos para revisitar
- Mais de uma instância → cache distribuído só se medir ganho.
- Algum provedor exigir chave ou mudar termos.
- Reclamação de cadastro lento: pôr um prazo total de ~3 s na consulta (`orTimeout`) ou reduzir a leitura da BrasilAPI (D91; o pior caso teórico hoje é ~6 s, aceito no beta pelo revisor).
- Em produção, confirmar que o IP contado no limite (H21) é o do cliente e não o do proxy do Render (`forward-headers-strategy: framework`).
