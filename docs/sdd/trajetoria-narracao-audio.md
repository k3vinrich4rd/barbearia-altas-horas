# Narração em áudio da Trajetória

**Objetivo:** a pessoa ouve a Trajetória com a voz aprovada (Kokoro `pm_santa`), com legenda, transcrição e controle pelo teclado, sem piorar o LCP da home.
**Usuários:** quem visita a home, inclusive com teclado ou leitor de tela.
**Fora do escopo:** mudar o texto (já é a versão 3), gerar o modelo no CI, pôr o áudio no cache do service worker.

## Decisões de implementação

- Os arquivos versionados são `docs/assets/audio/historia.{opus,mp3,vtt}` (origem: `audio-final/`). O script `copiar:assets` leva tudo de `docs/assets/` para `frontend/public/assets/` no build, como as outras mídias. `public/assets` continua fora do Git. O script `ferramentas/gerar-narracao.py` roda à mão (o modelo não entra no repo nem no CI). Ele não tem caminho absoluto, segredo nem nome de agente; o `~/modelos/...` do comentário de uso é só exemplo.
- O `<audio controls preload="none">` só ganha as fontes (Opus, reserva MP3) e o `<track kind="captions" srclang="pt-BR" label="Português" default>` ao apertar "Ouvir". Com o `track` no DOM desde o carregamento, o navegador pedia o `.vtt` mesmo com `preload="none"` (o e2e do VZ-1 falhava).
- O botão próprio alterna play/pausa (`aria-pressed`). Texto visível: "Ouvir a trajetória · 1 min 13 s" (duração real do arquivo, ~73 s; o rascunho da spec citava 1 min 6 s). Em reprodução vira "Pausar". Os controles nativos (posição e pausa) ficam disponíveis e funcionam pelo teclado.
- A transcrição é o texto visível da seção (`HISTORIA`). O teste VZ-4 confere que o texto das legendas, juntado, é igual a `HISTORIA.join(" ")`.
- Se o arquivo não carregar, aparece "Não deu para carregar o áudio. Ler com a voz do aparelho?" e o botão passa a usar a Web Speech API.
- O service worker continua sem cache de instalação; um comentário deixa explícito que o áudio da Trajetória não deve entrar nesse cache.
- LCP: com `preload="none"` o arquivo não entra no carregamento inicial da home. O teto do Lighthouse móvel da home permanece 2,7 s em `lighthouserc.json`.
