#!/usr/bin/env python3
"""Gera a narração da Trajetória (historia.opus, historia.mp3 e historia.vtt) a partir do texto aprovado.

Roda à mão, fora do CI. O modelo (325 MB) não entra no repo. O arquivo gerado é versionado.
Voz: Kokoro v1.0, `pm_santa` (decisão de 27/11). Licenças: pesos do modelo Apache-2.0
(https://huggingface.co/hexgrad/Kokoro-82M); kokoro-onnx MIT (https://github.com/thewh1teagle/kokoro-onnx).

Dependências fixas:
    pip install kokoro-onnx==0.6.1 onnxruntime==1.32.0 soundfile numpy
    eSpeak NG (fonemas, usado pelo kokoro-onnx) e FFmpeg com libopus no PATH.
Modelo (release `model-files-v1.0` do kokoro-onnx):
    kokoro-v1.0.onnx  SHA-256 7d5df8ecf7d4b1878015a32686053fd0eebe2bc377234608764cc0ef3636a6c5
    voices-v1.0.bin   SHA-256 bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d

Uso:
    python ferramentas/gerar-narracao.py \
        --historia-ts frontend/src/conteudo/historia.ts \
        --modelo ~/modelos/kokoro-v1.0.onnx --vozes ~/modelos/voices-v1.0.bin \
        --saida frontend/public/assets/audio/historia
    (ou --texto arquivo.txt, com parágrafos separados por linha em branco)

A legenda (.vtt) tem o texto EXATO da tela, uma legenda por frase; o tempo vem da duração do áudio de cada frase.
As trocas de PRONUNCIA valem só para a entrada do TTS e são impressas no fim.
"""
import argparse, hashlib, re, subprocess, sys, tempfile
from pathlib import Path

import numpy as np
import soundfile as sf

SHA = {
    "modelo": "7d5df8ecf7d4b1878015a32686053fd0eebe2bc377234608764cc0ef3636a6c5",
    "vozes": "bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d",
}
VOZ, IDIOMA, VELOCIDADE = "pm_santa", "pt-br", 0.95
PAUSA_FRASE, PAUSA_PARAGRAFO = 0.25, 0.45

# Só na entrada do TTS: o texto da tela e da legenda não muda.
PRONUNCIA = [
    ("Master Barber · Êxito", "Máster Bárber, Êxito"),
    ("Academy", "Acádemi"),
    ("WhatsApp", "Uatsáp"),
    ('"', ""),
    # com o ponto final, o "ele" da frase curta soava como "eles" (transcrição de conferência, 27/11)
    ("fala por ele.", "fala por ele"),
]


def sha256(caminho: Path) -> str:
    h = hashlib.sha256()
    with open(caminho, "rb") as f:
        for bloco in iter(lambda: f.read(1 << 20), b""):
            h.update(bloco)
    return h.hexdigest()


def paragrafos_do_ts(caminho: Path) -> list[str]:
    fonte = caminho.read_text(encoding="utf-8")
    m = re.search(r"export const HISTORIA[^=]*=\s*\[(.*?)\]", fonte, re.S)
    if not m:
        sys.exit("HISTORIA não encontrada em " + str(caminho))
    itens = re.findall(r'"((?:[^"\\]|\\.)*)"|`((?:[^`\\]|\\.)*)`|\'((?:[^\'\\]|\\.)*)\'', m.group(1))
    escapes = {"n": "\n", "t": "\t", '"': '"', "'": "'", "`": "`", "\\": "\\"}
    desfaz = lambda txt: re.sub(r"\\(.)", lambda m: escapes.get(m.group(1), m.group(1)), txt)
    return [desfaz(a or b or c) for a, b, c in itens]


def frases(paragrafo: str) -> list[str]:
    # corta depois de . ! ? ou : quando a próxima começa com maiúscula (aspas ficam junto da frase anterior)
    return [f for f in re.split(r"(?<=[.!?:])\s+(?=[A-ZÁÉÍÓÚÂÊÔÃÕÇ])", paragrafo.strip()) if f]


def para_tts(frase: str, usadas: set) -> str:
    for de, para in PRONUNCIA:
        if de in frase:
            usadas.add((de, para))
            frase = frase.replace(de, para)
    return frase


def tempo(s: float) -> str:
    return f"{int(s // 3600):02d}:{int(s % 3600 // 60):02d}:{s % 60:06.3f}"


def main() -> None:
    ap = argparse.ArgumentParser()
    fonte = ap.add_mutually_exclusive_group(required=True)
    fonte.add_argument("--historia-ts", type=Path)
    fonte.add_argument("--texto", type=Path)
    ap.add_argument("--modelo", type=Path, required=True)
    ap.add_argument("--vozes", type=Path, required=True)
    ap.add_argument("--saida", type=Path, required=True, help="caminho sem extensão")
    a = ap.parse_args()

    for nome, caminho in (("modelo", a.modelo), ("vozes", a.vozes)):
        if sha256(caminho) != SHA[nome]:
            sys.exit(f"SHA-256 de {caminho} não confere com o modelo v1.0 anotado")

    from kokoro_onnx import Kokoro

    k = Kokoro(str(a.modelo), str(a.vozes))
    pars = paragrafos_do_ts(a.historia_ts) if a.historia_ts else [
        p.strip() for p in a.texto.read_text(encoding="utf-8").split("\n\n") if p.strip()
    ]
    partes, legendas, t, taxa, usadas = [], [], 0.0, 24000, set()
    for i, p in enumerate(pars):
        fs = frases(p)
        for j, f in enumerate(fs):
            audio, taxa = k.create(para_tts(f, usadas), voice=VOZ, speed=VELOCIDADE, lang=IDIOMA)
            legendas.append((t, t + len(audio) / taxa, f))
            partes.append(audio)
            t += len(audio) / taxa
            pausa = PAUSA_PARAGRAFO if j == len(fs) - 1 and i < len(pars) - 1 else PAUSA_FRASE
            partes.append(np.zeros(int(taxa * pausa), dtype=audio.dtype))
            t += pausa
    a.saida.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "narracao.wav"
        sf.write(wav, np.concatenate(partes), taxa)
        base = ["ffmpeg", "-loglevel", "error", "-y", "-i", str(wav), "-ac", "1", "-map_metadata", "-1"]
        subprocess.run(base + ["-c:a", "libopus", "-b:a", "24k", str(a.saida) + ".opus"], check=True)
        subprocess.run(base + ["-c:a", "libmp3lame", "-b:a", "64k", str(a.saida) + ".mp3"], check=True)
    vtt = "WEBVTT\n\n" + "\n".join(f"{n}\n{tempo(i)} --> {tempo(f)}\n{txt}\n" for n, (i, f, txt) in enumerate(legendas, 1))
    Path(str(a.saida) + ".vtt").write_text(vtt, encoding="utf-8")
    print(f"{len(legendas)} frases, {t:.1f} s")
    for de, para in sorted(usadas):
        print(f"pronúncia (só no TTS): {de!r} -> {para!r}")


if __name__ == "__main__":
    main()
