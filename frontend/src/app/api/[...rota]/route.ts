import { encaminhar } from "@/bff/proxy";

export const dynamic = "force-dynamic";

type Contexto = { params: Promise<{ rota: string[] }> };

async function tratar(request: Request, contexto: Contexto) {
  const { rota } = await contexto.params;
  return encaminhar(request, rota);
}

export const GET = tratar;
export const POST = tratar;
export const PUT = tratar;
export const PATCH = tratar;
export const DELETE = tratar;
