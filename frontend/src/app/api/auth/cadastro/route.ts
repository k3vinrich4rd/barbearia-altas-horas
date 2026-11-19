import { cadastro } from "@/bff/sessao";

export const dynamic = "force-dynamic";
export const POST = (request: Request) => cadastro(request);
