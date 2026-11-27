# Texto da Trajetória, versão 3

**Objetivo:** o texto visível da Trajetória na home é idêntico, vírgula por vírgula, ao texto aprovado pelo Kevin em 27/11/2026.
**Usuários:** quem lê a home.
**Fora do escopo:** narração em áudio (PR seguinte), título e subtítulo da home, `TEXTO_DO_BARBEIRO`, `MARCOS`, print do README.

## Decisões de implementação

- Só a constante `HISTORIA` em `frontend/src/conteudo/historia.ts` mudou (3 parágrafos). Nenhum outro export do arquivo foi tocado.
- A cópia de referência no repositório é `docs/sdd/historia-v3-aprovada.txt`, igual a `amostras/historia-v3-aprovada.txt`. O teste `HR-2` compara `HISTORIA.join("\n\n") + "\n"` com esse arquivo, caractere por caractere.
- As aspas em `"Amor na ponta da tesoura!"` são retas, como no texto aprovado (sem ponto depois da aspa de fechamento).
- O texto não contém "Cândido", "degradê", "platinado" nem "terça a sábado". Os únicos anos são 1994, 1997 e 2017.
