# 0008. CEP: ViaCEP + BrasilAPI, cache Caffeine e degradação graciosa

- Status: **proposta** (dono: Quarteto Dev; o Alicerce revisa o impacto)
- Data: 2026-11-13
- Decisores: Quarteto Dev

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
- **Cache Caffeine** (memória) só de respostas encontradas: TTL 7 dias, máximo 10 mil entradas (proposta; cabe com folga em 512 MB). CEP inexistente fica em cache curto (1 h) para não martelar os provedores.
- **Sem retry** dentro da requisição do usuário (o fallback já é a segunda tentativa); circuit breaker simples por provedor fica como melhoria se os logs mostrarem quedas frequentes.
- Sanitização antes de chamar: só dígitos, exatamente 8 (`Cep` em `comum`); senão `400 CEP_INVALIDO`, sem chamada externa (OWASP API7).
- **Degradação:** se os dois falharem, o cadastro grava o CEP e deixa bairro, cidade e UF nulos; o perfil mostra "complete o endereço". `GET /cep/{cep}` responde `503 CEP_INDISPONIVEL` nesse caso (o front pode deixar digitar à mão).
- O CEP nunca vai para o log (o `toString()` de `Cep` mascara).

## Consequências
- ✅ Cadastro nunca cai por causa de CEP; pior caso ~3 s a mais quando os dois provedores estão lentos.
- ⚠️ Cache local some quando o Render hiberna (aceito: é só otimização).

## Gatilhos para revisitar
- Mais de uma instância → cache distribuído só se medir ganho.
- Algum provedor exigir chave ou mudar termos.
