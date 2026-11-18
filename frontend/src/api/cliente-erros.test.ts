// @vitest-environment node
import { describe, expect, test } from "vitest";
import { erroDeResposta } from "./erro";
import { mensagemDoErro } from "./mensagens";

const problema = (corpo: object, status: number, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(corpo), { status, headers: { "content-type": "application/problem+json", ...headers } });

describe("conversor de erros", () => {
  test("A12: problem+json vira ErroApi por code; HTML vira API_INDISPONIVEL; title e detail não decidem nada", async () => {
    const completo = await erroDeResposta(
      problema(
        { type: "x", title: "Título", detail: "Detalhe interno", status: 400, code: "VALIDACAO_INVALIDA", traceId: "abc", errors: [{ field: "nome", message: "obrigatório" }] },
        400,
        { "retry-after": "7" },
      ),
    );
    expect(completo).toMatchObject({ status: 400, code: "VALIDACAO_INVALIDA", traceId: "abc", retryAfter: 7 });
    expect(completo.erros).toEqual([{ field: "nome", message: "obrigatório" }]);
    expect(completo.doCampo("nome")).toBe("obrigatório");

    const semErros = await erroDeResposta(problema({ title: "t", status: 409, code: "HORARIO_INDISPONIVEL" }, 409));
    expect(semErros.erros).toEqual([]);
    expect(semErros.retryAfter).toBeUndefined();
    expect(semErros.traceId).toBeUndefined();

    const html = await erroDeResposta(new Response("<html><body>Erro 500</body></html>", { status: 500, headers: { "content-type": "text/html" } }));
    expect(html.code).toBe("API_INDISPONIVEL");
    expect(html.acordando).toBe(true);
    expect(html.status).toBe(500);

    // a mensagem sai do code, não do title nem do detail
    const outroTitulo = await erroDeResposta(problema({ title: "OUTRO TÍTULO", detail: "OUTRO DETALHE", status: 409, code: "HORARIO_INDISPONIVEL" }, 409));
    expect(mensagemDoErro(outroTitulo)).toBe(mensagemDoErro(semErros));
    expect(mensagemDoErro(outroTitulo)).not.toContain("OUTRO");
  });
});
