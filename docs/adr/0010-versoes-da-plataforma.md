# 0010. Versões: Java 25 LTS e Spring Boot 4.1.2

- Status: **proposta**
- Data: 2026-11-13
- Decisores: Quarteto Dev; pesquisa: Gemini; conferência: coordenador na fonte oficial

## Contexto
O prompt aprovado pede Java 25 LTS e "Spring Boot 4.x na última versão estável, com a versão exata fixada no `pom.xml` e fonte citada".

## Decisão
| Item | Versão | Fonte e data |
|---|---|---|
| Java | 25 (LTS), fixado com `maven-enforcer-plugin` em `[25,26)` | prompt aprovado |
| Spring Boot | **4.1.2** (GA de 22/10/2026) | API de releases do GitHub (`spring-projects/spring-boot`), consultada em 13/11/2026 |
| Não usar | 4.2.0 (em RC2 em 05/11/2026, não é GA) | idem |
| Demais bibliotecas (Spring Modulith, springdoc, Testcontainers, ShedLock, ArchUnit) | versões compatíveis com o Boot 4.1.2, conferidas e citadas no PR da fatia S00 | a pesquisar na S00 |

Detalhes e divergências com o Gemini: verificação interna da equipe (13/11/2026).

## Consequências
- ✅ Versão estável e suportada; Maven Wrapper garante a mesma versão do Maven para todos.
- ⚠️ Se a 4.2.0 sair GA antes da S00, a troca é uma linha em `decisoes.md` (não exige novo ADR).

## Gatilhos para revisitar
- Fim do suporte OSS da linha 4.1 (conferir em spring.io/projects/spring-boot#support).
- CVE relevante numa dependência.
