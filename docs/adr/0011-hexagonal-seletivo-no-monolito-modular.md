# ADR 0011 · Hexagonal seletivo dentro do monólito modular

- Status: aceito em 16/11/2026
- Decisor: Kevin Richard
- Contexto da pergunta: "vale reescrever o projeto em arquitetura hexagonal?"

## Contexto

O projeto é um **monólito modular** (ADR 0001): 8 módulos do Spring Modulith com fronteiras testadas. A arquitetura hexagonal (portas e adaptadores) é uma resposta a outra pergunta. As duas não competem:

| | Monólito modular | Hexagonal |
|---|---|---|
| Organiza | **entre** módulos (quem pode falar com quem) | **dentro** de um módulo (regra no centro, tecnologia nas bordas) |
| Garante | `agenda` não acessa as tabelas de `pagamento` | a regra não importa Spring, JPA nem HTTP |
| Custo | baixo (pacotes + teste de arquitetura) | médio: porta, adaptador e, no formato completo, um modelo de domínio separado da entidade JPA, com mapeador |

## Opções

| Opção | A favor | Contra |
|---|---|---|
| A. Hexagonal completo em todos os módulos | regra 100% isolada; fácil trocar banco ou framework | para CRUD (catálogo, política, auditoria) vira cerimônia: 3 classes e um mapeador para ler uma tabela; 1 desenvolvedor; ninguém vai trocar o PostgreSQL (a regra central **depende** dele: `EXCLUDE USING gist`) |
| B. Só camadas, sem portas | simples | a regra difícil (horários, máquina de estados, pagamento) fica grudada em JPA e em cliente HTTP: teste lento e troca de gateway dolorosa |
| **C. Monólito modular + hexagonal onde paga o custo** | isola o que é difícil ou externo; mantém o simples simples | exige critério: decidir caso a caso |

## Decisão

**Opção C.** Dentro de cada módulo, usa-se porta e adaptador **quando há pelo menos um destes motivos**:

1. **Regra difícil que precisa de muitos casos de teste** → núcleo puro, sem framework.
2. **Sistema externo que pode trocar ou cair** → porta de saída com adaptador.

| Módulo | Aplicação | Fatia |
|---|---|---|
| `agenda` (disponibilidade) | `CalculadoraDeHorarios` é o núcleo puro; `FonteDeBloqueios` é a porta; `BloqueiosJdbc`, o adaptador; o controller é o adaptador de entrada | ✅ 2 |
| `agenda` (reserva) | máquina de estados da `Reserva` sem dependência de framework | 🔜 4 |
| `pagamento` | porta `GatewayDePagamento` com adaptador Asaas (e Mercado Pago como alternativa documentada no ADR 0007) | 🔜 4 |
| `cep` | porta `BuscaDeCep` com adaptadores ViaCEP e BrasilAPI | 🔜 6 |
| catálogo, política, auditoria | camadas simples: controller → repositório Spring Data, com projeção em DTO | — |

Uma regra do `ArquiteturaTest` cobra o item 1: classes `Calculadora*` não podem depender de `org.springframework`, `jakarta`, `org.hibernate` nem `java.sql`.

## Consequências

- ✅ A regra dos horários tem 12 testes que rodam em milissegundos, sem subir Spring nem banco.
- ✅ Trocar o gateway de pagamento será escrever um adaptador, não mexer na regra.
- ✅ Nada de mapeador domínio ↔ entidade onde a entidade não tem comportamento.
- ⚠️ Módulos com estilos diferentes: quem chega precisa ler este ADR para entender o critério.
- 🔁 Rever se um módulo simples ganhar regra de verdade (aí ele ganha núcleo e porta).
