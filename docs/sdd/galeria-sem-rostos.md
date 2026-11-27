# Galeria sem rostos e sem crianças

**Status:** aprovada pelo Kevin em 27/11/2026 · **Base:** `develop` · **PR:** `feature/ux-galeria-sem-rostos`

## Objetivo · Usuários · Fora do escopo
- **Objetivo:** o site vai aparecer no LinkedIn e parte das fotos da galeria mostrava crianças e pessoas sem autorização de uso da imagem. A galeria passa a ter só fotos sem rosto identificável e sem criança, e o herói passa a usar um recorte da fachada sem as TVs (que mostravam o rosto de uma pessoa) e sem o reflexo de quem tirou a foto.
- **Usuários:** visitante da home, no celular primeiro, e quem chega pelo LinkedIn.
- **Fora do escopo:** fotos novas (nada de banco de imagens), as fotos do Eduardo (barbeiro, retrato, prêmios) e o registro em `decisoes.md` (fica para o PR de docs da arquitetura, que revisa a D158).

## Triagem (cada foto conferida olhando a imagem)
Regra: sai foto com rosto identificável (mesmo parcial: olhos, perfil nítido), com criança ou com dúvida. Na dúvida, sai.

| Arquivo | Decisão | Motivo |
|---|---|---|
| `galeria/eduardo-em-atendimento.jpg` | sai | cliente de perfil nítido |
| `galeria/corte-topete-parede-do-logo.jpg` | sai | rosto em três quartos |
| `galeria/corte-degrade-com-desenho.jpg` | sai | dúvida: parece adolescente e tem traços marcantes |
| `galeria/corte-social-grisalho-na-regua.jpg` | sai | rosto de frente |
| `galeria/corte-infantil-cacheado-com-degrade.jpg` | sai | criança |
| `galeria/corte-franja-curta-jovem.jpg` | sai | rosto de frente |
| `galeria/platinado-cliente-de-oculos.jpg` | sai | perfil com olho visível |
| `galeria/corte-infantil-risca-lateral.jpg` | sai | criança |
| `galeria/corte-cacheado-volume.jpg` | sai | rosto de frente |
| `galeria/platinado-cliente-e-eduardo.jpg` | sai | rosto de frente |
| `galeria/corte-topo-volumoso-com-degrade.jpg` | sai | rosto de frente |
| `galeria/fade-perfil-capa-altas-horas.jpg` | sai | perfil nítido |
| `galeria/barba-cheia-desenhada.jpg` | sai | rosto de frente |
| `galeria/degrade-parede-do-logo.jpg` | sai | perfil nítido |
| `galeria/degrade-baixo-alinhamento-na-cadeira.jpg` | sai | rosto de frente |
| `galeria/franja-reta-alinhada.jpg` | sai | rosto de frente |
| `galeria/degrade-grisalho-penteado-para-tras.jpg` | sai | perfil nítido |
| `galeria/salao-vitrine-com-logo.jpg` | sai; entra o recorte `galeria/salao-vitrine-sem-tv.jpg` | TVs com o rosto de uma pessoa e reflexo no vidro |
| `fachada.jpg` | sai; entra o recorte `fachada-sem-tv.jpg` | as mesmas TVs e o mesmo reflexo |

Os dois recortes vêm das próprias fotos (nenhuma imagem inventada). Além do corte, os porta-retratos da prateleira e da bancada, pequenos demais para ver quem está neles, ganharam um desfoque, para não sobrar nenhum rosto.

## Telas
- **Galeria com 1 foto (hoje):** sobretítulo "Da cadeira", título "O salão da Altas Horas", apoio "No Jardim Bonança, em Osasco, desde 1997.", a foto larga na proporção real com moldura fina (`--borda-forte`) e a legenda "O salão". No desktop, texto à esquerda e foto à direita. Sem visualizador e sem botão (não há o que folhear).
- **0 fotos:** a seção não aparece e o link "Galeria" sai do menu.
- **2 ou 3 fotos:** 1 coluna no celular, 2 ou 3 no desktop, quadros quadrados; foto com pessoa sempre inteira (`contain`), foto sem pessoa pode preencher o quadro (`cover`). Visualizador como antes.
- **4 ou mais:** a grade de antes (3 colunas no celular, 4 no desktop).
- **Herói:** mesma posição, mesmo tamanho declarado (560×420), `priority` e os mesmos formatos do `next/image`; só o arquivo muda.

## Dados e LGPD
- Imagem de pessoa é dado pessoal; de criança e adolescente pede cuidado redobrado (LGPD, art. 14).
- As 19 fotos saem da versão atual do repositório (`git rm`, commit normal) e do site: o `copiar-assets.mjs` limpa `public/assets/galeria` e a fachada antiga antes de copiar, para não sobrar arquivo velho publicado.
- **Limites:** o histórico do Git continua com as fotos (não foi reescrito), e deploys antigos da Vercel podem continuar servindo os arquivos pelas URLs deles.

## Critérios de aceite (Dado/Quando/Então)
- **GS-1** Dado a home, quando a galeria aparece, então ela só tem as fotos aprovadas (hoje, `salao-vitrine-sem-tv.jpg`), sem rosto e sem criança. Teste: `galeria.test.tsx` (A11).
- **GS-2** Dado o PR, quando comparado à `develop`, então as 19 fotos saíram da versão atual por `git rm`, o histórico não foi reescrito e só entraram os dois recortes. Teste: `galeria.test.tsx` (A11) confere que os arquivos não existem mais.
- **GS-3** Dado o build, quando se pede `/assets/galeria/<foto que saiu>.jpg` ou `/assets/fachada.jpg`, então a resposta é 404; os dois recortes respondem 200. Teste: `e2e/galeria-sem-rostos.spec.ts`.
- **GS-4** Dado 0, 1, 2, 3 ou 4+ fotos, quando a home abre, então o desenho é o desta spec, sem buraco na grade e sem rolagem horizontal a 360 px. Teste: `galeria.test.tsx` (A12, A13).
- **GS-5** Dado a home a 360×800 e a 1280, nos temas escuro e claro, quando o axe roda com WCAG 2.2 AA, então há 0 violações. Teste: `e2e/galeria-sem-rostos.spec.ts` e `e2e/site-fluido-axe.spec.ts` (A14).
- **GS-6** Dado a home rolada até o fim, quando o CLS é medido, então CLS ≤ 0,1 (e2e e Lighthouse móvel); o LCP da home não piora.
- **GS-7** Dado o README no GitHub, quando a seção "Da cadeira" abre, então ele não mostra nenhuma das fotos que saíram.
