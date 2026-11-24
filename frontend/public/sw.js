// Service worker do Altas Horas (Fatia 13, D177): existe SÓ para receber o Web Push do painel do barbeiro.
// Sem cache de página e sem rota offline: nada daqui guarda dado do site. Nenhum dado pessoal passa por aqui (o aviso traz
// serviço, dia e hora). Não usa console: o conteúdo da notificação não vai para log.

const TEXTO_PADRAO = "Novo horário no painel";
const TITULO_PADRAO = "Altas Horas";
const LINK_PADRAO = "/painel";
const ICONE = "/assets/logo-altas-horas.png";

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (evento) => {
  evento.waitUntil(self.clients.claim());
});

/** Só um caminho do próprio site: nunca uma URL de fora (o payload vem do serviço de push). */
function caminhoSeguro(valor) {
  if (typeof valor !== "string") return LINK_PADRAO;
  try {
    const url = new URL(valor, location.origin);
    return url.origin === location.origin ? url.pathname + url.search + url.hash : LINK_PADRAO;
  } catch {
    return LINK_PADRAO;
  }
}

/** Lê o corpo do push. Payload ilegível não derruba a notificação: vale o texto padrão. */
function lerPayload(evento) {
  try {
    const dados = evento.data ? evento.data.json() : {};
    return dados && typeof dados === "object" ? dados : {};
  } catch {
    return {};
  }
}

/** Avisa as abas abertas para atualizarem o selo "Novidades", sem polling. */
function avisarAbas() {
  return self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((abas) => {
    abas.forEach((aba) => aba.postMessage({ tipo: "novidade" }));
  });
}

self.addEventListener("push", (evento) => {
  const dados = lerPayload(evento);
  const titulo = typeof dados.titulo === "string" && dados.titulo ? dados.titulo : TITULO_PADRAO;
  const texto = typeof dados.texto === "string" && dados.texto ? dados.texto : TEXTO_PADRAO;
  const opcoes = {
    body: texto,
    icon: ICONE,
    badge: ICONE,
    data: { link: caminhoSeguro(dados.link) },
  };
  // A tag junta repetidos da mesma reserva no aparelho.
  if (typeof dados.tag === "string" && dados.tag) opcoes.tag = dados.tag;
  evento.waitUntil(Promise.all([self.registration.showNotification(titulo, opcoes), avisarAbas()]));
});

self.addEventListener("notificationclick", (evento) => {
  evento.notification.close();
  const link = caminhoSeguro(evento.notification.data && evento.notification.data.link);
  evento.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((abas) => {
      const painel = abas.find((aba) => {
        try {
          return new URL(aba.url).pathname.startsWith("/painel");
        } catch {
          return false;
        }
      });
      if (painel) return painel.focus();
      return self.clients.openWindow(link);
    }),
  );
});
