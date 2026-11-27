// Verificador de tokens (A56): nenhum CSS de src usa valor visual literal fora de tokens.css.
// Proíbe: cor literal (#, rgb(, hsl(), cor nomeada como red ou white), font-family fora de var(--font-*), box-shadow,
// medida em px, rem e em soltos (rem e em só dentro de uma variável --medida-*), duração literal (ms ou s; use var(--mov-*)) e o atributo style= nos componentes TSX.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function listarCss(dir) {
  const achados = [];
  for (const nome of readdirSync(dir)) {
    const caminho = join(dir, nome);
    if (statSync(caminho).isDirectory()) achados.push(...listarCss(caminho));
    else if (nome.endsWith(".css") && nome !== "tokens.css") achados.push(caminho);
  }
  return achados;
}

// Cores nomeadas do CSS (ficam de fora transparent, currentColor e as palavras-chave inherit, initial e unset).
const CORES_NOMEADAS = new Set(
  (
    "aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown burlywood " +
    "cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray " +
    "darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen " +
    "darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue " +
    "firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray green greenyellow grey honeydew " +
    "hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan " +
    "lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen lightskyblue " +
    "lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine " +
    "mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen mediumturquoise " +
    "mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive olivedrab orange " +
    "orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum " +
    "powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna " +
    "silver skyblue slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise violet " +
    "wheat white whitesmoke yellow yellowgreen"
  ).split(" "),
);
const PROPRIEDADE_DE_COR =
  /(^|[;{\s])(color|background|background-color|border(-[a-z]+)*|outline(-[a-z]+)*|fill|stroke|text-decoration(-color)?|caret-color|accent-color|column-rule(-color)?)\s*:\s*([^;}]*)/gi;

function coresNomeadasEm(linha) {
  const achadas = [];
  for (const m of linha.matchAll(PROPRIEDADE_DE_COR)) {
    const valor = m[m.length - 1].replace(/var\([^)]*\)/g, " ");
    for (const palavra of valor.toLowerCase().match(/[a-z]+/g) ?? [])
      if (CORES_NOMEADAS.has(palavra)) achadas.push(palavra);
  }
  return achadas;
}

function listarTsx(dir) {
  const achados = [];
  for (const nome of readdirSync(dir)) {
    const caminho = join(dir, nome);
    if (statSync(caminho).isDirectory()) achados.push(...listarTsx(caminho));
    else if (nome.endsWith(".tsx") && !nome.endsWith(".test.tsx")) achados.push(caminho);
  }
  return achados;
}

export function verificar(dir = join(raiz, "src")) {
  const problemas = [];
  for (const arquivo of listarTsx(dir)) {
    readFileSync(arquivo, "utf8")
      .split("\n")
      .forEach((linha, i) => {
        if (/<[A-Za-z][^>]*\sstyle\s*=/.test(linha) || /^\s*style\s*=\s*[{"']/.test(linha))
          problemas.push(`${relative(raiz, arquivo)}:${i + 1} style= no TSX (o visual é do CSS com tokens)`);
      });
  }
  // toda variável --medida-* usada precisa estar definida (e nunca em função de si mesma): var() inválido some em silêncio
  const todos = listarCss(dir).map((f) => [f, readFileSync(f, "utf8")]);
  const definidas = new Set();
  for (const [, texto] of todos)
    for (const m of texto.matchAll(/(--medida-[a-z0-9-]+)\s*:\s*([^;]+);/g)) {
      definidas.add(m[1]);
      if (m[2].includes(`var(${m[1]})`)) problemas.push(`${m[1]} definida em função de si mesma`);
    }
  for (const [arquivo, texto] of todos)
    for (const m of texto.matchAll(/var\((--medida-[a-z0-9-]+)\)/g))
      if (!definidas.has(m[1])) problemas.push(`${relative(raiz, arquivo)}: ${m[1]} usada e nunca definida`);
  for (const arquivo of listarCss(dir)) {
    const texto = readFileSync(arquivo, "utf8").replace(/\/\*[\s\S]*?\*\//g, (c) => c.replace(/[^\n]/g, " "));
    texto.split("\n").forEach((linha, i) => {
      const onde = `${relative(raiz, arquivo)}:${i + 1}`;
      if (/#[0-9a-fA-F]{3,8}\b/.test(linha)) problemas.push(`${onde} cor literal (#)`);
      if (/\b(rgb|rgba|hsl|hsla)\(/i.test(linha)) problemas.push(`${onde} cor literal (rgb/hsl)`);
      if (/font-family\s*:/.test(linha) && !/font-family\s*:\s*var\(--(font|tipo)-[a-z-]+\)\s*;?/.test(linha))
        problemas.push(`${onde} font-family fora de var(--font-*)`);
      if (/box-shadow\s*:/.test(linha)) problemas.push(`${onde} box-shadow (o design system não tem sombra)`);
      for (const cor of coresNomeadasEm(linha)) problemas.push(`${onde} cor nomeada (${cor}); use um token de cor`);
      // rem e em só como valor de uma variável --medida-*; o @media não aceita var(), então fica de fora
      if (
        !/^\s*--medida-[a-z0-9-]+\s*:/.test(linha) &&
        !/^\s*@media\b/.test(linha) &&
        /(^|[^\w.-])-?\d*\.?\d+(rem|em)\b/.test(linha)
      )
        problemas.push(`${onde} medida em rem/em solta (use var(--medida-*), var(--space-*) ou outro token)`);
      if (/(^|[^\w.-])-?\d*\.?\d+px\b/.test(linha))
        problemas.push(`${onde} medida em px solta (use var(--space-*), var(--radius-*) ou var(--traco-*))`);
      if (/(^|[^\w.-])-?\d*\.?\d+m?s\b/.test(linha)) problemas.push(`${onde} duração literal (use var(--mov-*))`);
    });
  }
  return problemas;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const problemas = verificar();
  if (problemas.length) {
    console.error(problemas.join("\n"));
    process.exit(1);
  }
  console.log("verificador de tokens: nenhum valor visual literal nos CSS");
}
