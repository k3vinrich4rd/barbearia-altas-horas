"use client";
// QR do Pix (D68): se a API mandar o PNG (qrCodeBase64), usa a imagem; senão o QR é gerado no navegador a partir do copia e cola.
import { useEffect, useState } from "react";
import QRCode from "qrcode";

export function QrPix({ copiaECola, qrCodeBase64 }: { copiaECola: string; qrCodeBase64?: string | null }) {
  const [gerado, setGerado] = useState<string | undefined>();
  useEffect(() => {
    if (qrCodeBase64) return;
    let vivo = true;
    QRCode.toDataURL(copiaECola, { margin: 2, width: 256 }).then(
      (url) => vivo && setGerado(url),
      () => vivo && setGerado(undefined),
    );
    return () => {
      vivo = false;
    };
  }, [copiaECola, qrCodeBase64]);

  const src = qrCodeBase64 ? `data:image/png;base64,${qrCodeBase64}` : gerado;
  if (!src) return null;
  return (
    // imagem de dados gerada no navegador: o otimizador do next/image não se aplica
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      width={256}
      height={256}
      alt="QR Code do Pix: aponte a câmera do app do seu banco para pagar"
      className="qr"
    />
  );
}
