# Narração em áudio da Trajetória

**Objetivo:** a pessoa ouve a Trajetória com a voz aprovada (Kokoro `pm_santa`), com legenda, transcrição e controle pelo teclado, sem piorar o LCP da home.
**Usuários:** quem visita a home, inclusive com teclado ou leitor de tela.
**Fora do escopo:** mudar o texto (já é a versão 3), gerar o modelo no CI, pôr o áudio no cache do service worker.

## Decisões de implementação

- Os arquivos versionados são `docs/assets/audio/historia.{opus,mp3,vtt}` (origem: `audio-final/`). O script `copiar:assets` leva tudo de `docs/assets/` para `frontend/public/assets/` no build, como as outras mídias. `public/assets` continua fora do Git. O script `ferramentas/gerar-narracao.py` roda à mão (o modelo não entra no repo nem no CI). Ele não tem caminho absoluto, segredo nem nome de agente; o `~/modelos/...` do comentário de uso é só exemplo.
- O player fica logo abaixo do título da Trajetória, antes do texto da seção.
- Fontes, legenda e os controles nativos (`<audio controls>`) só entram no DOM ao apertar "Ouvir". Antes disso não há `<audio>` na página: um `<audio controls>` sem fonte deixava o play nativo sem efeito, e um `<track default>` no carregamento fazia o navegador pedir o `.vtt` mesmo com `preload="none"`.
- A fonte Opus usa `type="audio/ogg; codecs=opus"` (não `audio/opus`): no Chromium, o tipo curto fazia o navegador ignorar o Opus e baixar o MP3.
- O botão próprio alterna play/pausa só pelo rótulo (sem `aria-pressed`). Texto visível: "Ouvir a trajetória · 1 min 13 s" (duração real do arquivo, ~73 s; o rascunho da spec citava 1 min 6 s). Em reprodução vira "Pausar". Depois do primeiro "Ouvir", os controles nativos (posição e pausa) ficam disponíveis e funcionam pelo teclado.
- A transcrição é o texto visível da seção (`HISTORIA`). O teste VZ-4 confere que o texto das legendas, juntado, é igual a `HISTORIA.join(" ")`.
- Se o arquivo não carregar, aparece "Não deu para carregar o áudio. Ler com a voz do aparelho?" e um botão "Ler com a voz do aparelho". A Web Speech API só começa se a pessoa apertar esse botão (VZ-5); nada fala sozinho.
- O service worker continua sem cache de instalação; um comentário deixa explícito que o áudio da Trajetória não deve entrar nesse cache.
- LCP: com `preload="none"` o arquivo não entra no carregamento inicial da home. O teto do Lighthouse móvel da home permanece 2,7 s em `lighthouserc.json`.
