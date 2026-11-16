## O que foi feito
<!-- Fatia e spec: docs/sdd/fatia-N-*.md. Liste os critérios (A#) cobertos. -->

## Como testar
<!-- Comandos e o resultado REAL (Tests run / Failures / Skipped, cobertura). -->

## Checklist
- [ ] Spec aprovada antes do código
- [ ] Um teste por critério (`sh scripts/conferir-spec.sh N --exigir`)
- [ ] Contrato atualizado e `redocly lint` com 0 erros
- [ ] Testes de integração com PostgreSQL real, nenhum pulado
- [ ] Erros RFC 9457 com `code` e `traceId`
- [ ] Nenhum dado pessoal em log; nenhum segredo no diff
- [ ] Guia de testes e README só com saída real
- [ ] PR para a `develop` (nunca para a `main`)
