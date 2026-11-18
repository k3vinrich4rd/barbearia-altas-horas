import { codigoDaReserva } from "@/bff/visitante";

export const dynamic = "force-dynamic";
export const GET = (request: Request) => codigoDaReserva(request);
