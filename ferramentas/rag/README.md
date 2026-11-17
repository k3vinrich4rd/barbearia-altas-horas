# RAG da documentação

Busca semântica na documentação da Barbearia Altas Horas com Spring AI 2.0.2, pgvector e embeddings locais. Ferramenta de desenvolvimento, separada da API.

```bash
docker compose --profile rag up -d rag-db
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=indexar
./mvnw -q spring-boot:run "-Dspring-boot.run.arguments=buscar 'onde o CPF fica guardado?'"
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=avaliar
```

Como funciona, por que é híbrida e os números da avaliação: [`docs/ia/rag-vetorial.md`](../../docs/ia/rag-vetorial.md).
