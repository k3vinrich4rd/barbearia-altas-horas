# 0001. Monólito modular com Spring Modulith

- Status: **proposta — revisão do Alicerce**
- Data: 2026-11-13
- Decisores: Alicerce (arquitetura, dono), Quarteto Dev (texto), Kevin (aprovação no release)
- Consultados: Gemini (`gemini-3.8-flash-medium`, ataque à proposta: [`docs/ia/`](../ia/))

## Contexto
Forças do [perfil arquitetural](../arquitetura/perfil.md): 1 dev, 1 barbearia, 6 subdomínios de negócio, consistência forte na agenda e no pagamento, deploy num Render free com **512 MB** e num Aiven free com **20 conexões** (pool de 5). O código também precisa servir de **template** para outros projetos. Atributos prioritários: segurança, testabilidade, manutenibilidade.

## Opções consideradas
1. **Monólito em camadas** (`controllers/services/repositories`).
2. **Monólito modular** com fronteiras verificadas (Spring Modulith + ArchUnit).
3. **Microsserviços** (agenda, pagamento e conta separados).

## Comparação
Pesos definidos **antes** das notas: segurança 3, testabilidade 3, manutenibilidade 2, custo de operação 2, simplicidade para 1 dev 2. Notas de 1 a 5 são **julgamento** do time, não medição.

| Critério (peso) | Camadas | Modular | Microsserviços |
|---|:-:|:-:|:-:|
| Segurança (3) | 3 | 4 | 3 |
| Testabilidade (3) | 3 | 4 | 3 |
| Manutenibilidade (2) | 2 | 5 | 3 |
| Custo de operação (2) | 5 | 5 | 1 |
| Simplicidade para 1 dev (2) | 5 | 4 | 1 |
| **Total ponderado** | **42** | **52** | **28** |
| Custo de mudar de ideia depois | alto (regras espalhadas viram "service gigante") | baixo (extrair um módulo vira serviço) | alto (juntar serviços e dados) |

## Decisão
**Monólito modular** com Spring Modulith: um jar, um container, um banco. Módulos `comum`, `plataforma`, `conta`, `cep`, `agenda`, `pagamento`, `privacidade` e `admin`, com as dependências e as 14 regras de [`docs/arquitetura/README.md`](../arquitetura/README.md#5-regras-verificadas-por-teste-archunit--spring-modulith). Pacote raiz `io.github.k3vinrich4rd.altashoras` (**HIPÓTESE H10**). Virtual threads ligadas e `Clock` injetado.

**Anotações JPA no domínio:** permitidas por pragmatismo. Separar entidade de domínio e entidade JPA dobraria as classes sem ganho para 1 dev; a regra 9 do ArchUnit ainda proíbe `web`, `persistencia` e `integracao` no domínio.

## Consequências
- ✅ Fronteiras testadas (`ApplicationModules.verify()` + ArchUnit) e documentação dos módulos gerada.
- ✅ Um deploy só cabe no plano grátis; nada de rede entre serviços.
- ✅ Template reaproveitável: troca-se o domínio e mantém-se `comum` e `plataforma`.
- ⚠️ Mais pacotes e regras que um monólito em camadas; a curva de aprendizado do Modulith fica no README de implementação.
- ⚠️ Eventos assíncronos exigem listeners idempotentes (ADR 0002).

## Como extrair um módulo no futuro
1. O módulo já fala com os outros só pela API pública e por eventos.
2. Troque a fachada por um cliente HTTP e os eventos por uma fila (o registro de publicação vira o outbox).
3. Separe as tabelas do módulo (as FKs entre módulos são poucas e conhecidas no ERD).

## Gatilhos para revisitar
- Mais de um time mexendo ao mesmo tempo em módulos diferentes.
- Um módulo precisar escalar ou subir separado (ex.: pagamento com volume próprio).
- Teste de arquitetura sendo desligado para "dar conta": sinal de fronteira errada.
