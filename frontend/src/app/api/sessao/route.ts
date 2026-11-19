import { sessao } from "@/bff/sessao";

export const dynamic = "force-dynamic";
export const GET = (request: Request) => sessao(request);
