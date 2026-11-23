import { expect, test } from "@playwright/test";
import { cadastrarCliente, emailUnico, entrarComoAdmin, horarioLivre, reservaPagaDeVisitante } from "./apoio";

// A59: o painel contra a API real. O admin entra, troca a senha inicial (obrigatória antes de qualquer tela), vê a reserva
// que o teste criou na agenda, cancela com motivo, resolve a devolução, confere a auditoria e abre o alcance.
test("A59: troca da senha inicial, agenda, cancelar com motivo, devolução, auditoria sem IP e alcance; o cliente não entra no painel", async ({
  page,
  request,
  browser,
}) => {
  // 1. a reserva paga do visitante (a cobrança fica PAGA)
  const horario = await horarioLivre(request);
  const nome = await reservaPagaDeVisitante(page, horario);
  await page.context().clearCookies();

  // 2. admin: a troca da senha inicial vem antes de qualquer tela
  await entrarComoAdmin(page);
  await expect(page.getByRole("navigation", { name: "Painel" })).toBeVisible();
  for (const item of ["Agenda", "Reserva manual", "Pendências", "Cadastros", "Auditoria", "Alcance"])
    await expect(page.getByRole("navigation", { name: "Painel" }).getByRole("link", { name: item })).toBeVisible();

  // 3. a agenda do dia da reserva mostra o visitante (confirmado, com a cobrança paga) e nunca e-mail nem CPF
  await page.goto(`/painel?data=${horario.dia}`);
  const item = page.getByRole("listitem").filter({ hasText: nome });
  await expect(item).toBeVisible();
  await expect(item).toHaveAttribute("data-estado", "confirmado");
  await expect(item).toContainText("Pagamento: Paga");
  await expect(page.locator("main")).not.toContainText(/@|\d{3}\.\d{3}\.\d{3}-\d{2}/);

  // 4. cancelar com motivo: o item deixa de ser confirmado e perde os botões de fechar
  await item.getByRole("button", { name: "Cancelar pela barbearia" }).click();
  await page.getByLabel("Motivo do cancelamento").fill("Barbeiro com imprevisto (teste E2E)");
  await page.getByRole("button", { name: "Confirmar cancelamento" }).click();
  await expect(item).toHaveAttribute("data-estado", "cancelado");
  await expect(item).toContainText("Motivo: Barbeiro com imprevisto (teste E2E)");
  await expect(item.getByRole("button", { name: "Concluído" })).toHaveCount(0);
  await expect(item.getByRole("button", { name: "Cancelar pela barbearia" })).toHaveCount(0);

  // 5. a pendência de devolução aparece (a cobrança foi para DEVOLUCAO_PENDENTE) e é resolvida
  await page.goto("/painel/pendencias");
  const linha = page.getByRole("row").filter({ hasText: nome });
  await expect(linha).toBeVisible();
  await expect(linha).toContainText("Devolução pendente");
  await expect(linha).toContainText("Cancelado pela barbearia: Barbeiro com imprevisto (teste E2E)");
  await linha.getByRole("button", { name: /Registrar devolução/ }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Registrar devolução" }).click();
  await expect(page.getByText("Devolução registrada.")).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: nome })).toHaveCount(0);
  // na API a cobrança virou DEVOLVIDA
  const devolvidas = await (await page.request.get("/api/admin/pagamentos?status=DEVOLVIDA&tamanho=50")).json();
  expect(devolvidas.itens.some((c: { contato?: { nome?: string } }) => c.contato?.nome === nome)).toBe(true);

  // 6. a auditoria mostra as duas ações, sem IP
  await page.goto("/painel/auditoria");
  const tabela = page.getByRole("table");
  await expect(tabela).toContainText("Reserva cancelada pela barbearia");
  await expect(tabela).toContainText("Devolução registrada");
  await expect(page.getByRole("columnheader", { name: /IP/i })).toHaveCount(0);
  await expect(page.locator("main")).not.toContainText(/\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/);

  // 7. o alcance abre com o período padrão, sem erro
  await page.goto("/painel/alcance");
  await expect(page.getByRole("heading", { name: "Alcance por região" })).toBeVisible();
  // o anunciador de rotas do Next também é um alert, mas vazio: erro de verdade tem texto
  await expect(page.getByRole("alert").filter({ hasText: /\S/ })).toHaveCount(0);
  await expect(page.getByRole("table").or(page.getByText("Ainda não há atendimentos suficientes"))).toBeVisible();

  // 8. o cliente (conta comum) não entra em /painel
  const contexto = await browser.newContext();
  const outra = await contexto.newPage();
  await cadastrarCliente(outra, emailUnico("cliente-painel"));
  await outra.goto("/painel");
  await expect(outra.getByRole("heading", { name: "Acesso negado" })).toBeVisible();
  await expect(outra.getByRole("navigation", { name: "Painel" })).toHaveCount(0);
  await contexto.close();
});

test("A59: reserva manual do painel trava o horário, aparece na agenda com a etiqueta e some da disponibilidade", async ({
  page,
  request,
}) => {
  const horario = await horarioLivre(request);
  await entrarComoAdmin(page);
  await page.goto("/painel/reserva-manual");
  // O rodapé do site também tem uma região "Horário" (PDF, pág. 2): os campos são buscados só dentro do conteúdo principal.
  const formulario = page.getByRole("main");
  await formulario.getByLabel("Serviço").selectOption({ index: 1 });
  await formulario.getByLabel("Barbeiro").selectOption(horario.barbeiroId);
  await formulario.getByLabel("Dia").fill(horario.dia);
  await formulario.getByLabel("Horário").fill(horario.hora);
  const nome = `Manual E2E ${Date.now()}`;
  await formulario.getByLabel("Nome do cliente (opcional)").fill(nome);
  await formulario.getByLabel("Observação (opcional)").fill("Combinado pelo WhatsApp.");
  await page.getByRole("button", { name: "Criar reserva" }).click();
  await expect(page).toHaveURL(new RegExp(`/painel\\?data=${horario.dia}`));
  const item = page.getByRole("listitem").filter({ hasText: nome });
  await expect(item).toContainText("MANUAL · WHATSAPP");
  await expect(item).toContainText("Observação: Combinado pelo WhatsApp.");
  await expect(item).not.toContainText(/Pagamento|devolu/i); // sem cobrança, sem pagamento nem devolução
  // o app deixa de oferecer o horário
  const d = await (
    await request.get(`${process.env.API_URL ?? "http://localhost:8080"}/disponibilidade`, {
      params: { servicoId: horario.servicoId, data: horario.dia },
    })
  ).json();
  const doBarbeiro = d.barbeiros.find((b: { barbeiroId: string }) => b.barbeiroId === horario.barbeiroId);
  expect(doBarbeiro.horarios).not.toContain(horario.inicio);
});
