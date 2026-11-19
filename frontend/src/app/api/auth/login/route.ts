import { login } from "@/bff/sessao";

export const dynamic = "force-dynamic";
export const POST = (request: Request) => login(request);
