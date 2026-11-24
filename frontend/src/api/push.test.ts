import { describe, expect, test } from "vitest";
import { chaveParaBytes, estadoDoPush, versaoDoIos, type AmbienteDePush } from "./push";

const base: AmbienteDePush = {
  temServiceWorker: true,
  temPushManager: true,
  temNotification: true,
  userAgent: "Mozilla/5.0 (Linux; Android 14) Chrome/130.0 Mobile Safari/537.36",
  standalone: false,
  permissao: "default",
  inscrito: false,
};

describe("estado do push no aparelho", () => {
  test("A41: um estado para cada situação", () => {
    expect(estadoDoPush(base).estado).toBe("desligado");
    expect(estadoDoPush({ ...base, temPushManager: false }).estado).toBe("sem-suporte");
    expect(estadoDoPush({ ...base, permissao: "denied" }).estado).toBe("permissao-negada");
    expect(estadoDoPush({ ...base, permissao: "granted", inscrito: true }).estado).toBe("ativo");
    // permissão dada mas sem inscrição (o usuário limpou os dados do site): volta a oferecer ativar
    expect(estadoDoPush({ ...base, permissao: "granted", inscrito: false }).estado).toBe("desligado");
  });

  test("A41: iPhone e iPad fora da Tela de Início; iOS menor que 16.4 pede atualização; 16.4 e 17 passam", () => {
    const ua = (v: string) => `Mozilla/5.0 (iPhone; CPU iPhone OS ${v} like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148`;
    expect(estadoDoPush({ ...base, userAgent: ua("17_4"), standalone: false }).estado).toBe("ios-fora-da-tela-inicial");
    expect(estadoDoPush({ ...base, userAgent: ua("17_4"), standalone: true }).estado).toBe("desligado");
    expect(estadoDoPush({ ...base, userAgent: ua("16_4"), standalone: true }).estado).toBe("desligado");
    expect(estadoDoPush({ ...base, userAgent: ua("16_3"), standalone: true })).toEqual({ estado: "sem-suporte", iosAntigo: true });
    expect(estadoDoPush({ ...base, userAgent: ua("15_8"), standalone: false })).toEqual({ estado: "sem-suporte", iosAntigo: true });
    expect(versaoDoIos(ua("16_4_1"))).toEqual({ principal: 16, secundaria: 4 });
    expect(versaoDoIos(base.userAgent)).toBeNull();
  });

  test("a chave pública VAPID (base64url) vira os bytes do subscribe", () => {
    const bytes = chaveParaBytes("BAECAw");
    expect(Array.from(bytes)).toEqual([4, 1, 2, 3]);
  });
});
