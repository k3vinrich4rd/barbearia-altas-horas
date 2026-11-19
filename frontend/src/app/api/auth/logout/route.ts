import { logout } from "@/bff/sessao";

export const dynamic = "force-dynamic";
export const POST = (request: Request) => logout(request);
