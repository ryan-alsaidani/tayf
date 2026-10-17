#!/usr/bin/env bash
# Downloads the binary assets the app needs (OCR models for Arabic/English and the PDF font).
# Safe to run many times: existing valid files are kept.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TESS="$ROOT/app/src/main/assets/tessdata"
FONTS="$ROOT/app/src/main/assets/fonts"
mkdir -p "$TESS" "$FONTS"

fetch() { # url dest minBytes
  local url="$1" dest="$2" min="$3"
  if [ -f "$dest" ] && [ "$(wc -c < "$dest")" -ge "$min" ]; then echo "OK (exists): $dest"; return 0; fi
  rm -f "$dest"
  if curl -fL --retry 3 --connect-timeout 30 -o "$dest.part" "$url" && [ "$(wc -c < "$dest.part")" -ge "$min" ]; then
    mv "$dest.part" "$dest"; echo "OK (downloaded): $dest"; return 0
  fi
  rm -f "$dest.part"; return 1
}

for L in ara eng; do
  fetch "https://github.com/tesseract-ocr/tessdata_best/raw/main/$L.traineddata" "$TESS/$L.traineddata" 1000000 \
    || { echo "FAILED: $L.traineddata"; exit 1; }
done

FONT="$FONTS/NotoNaskhArabic-Regular.ttf"
for U in \
  "https://github.com/notofonts/arabic/raw/main/fonts/NotoNaskhArabic/hinted/ttf/NotoNaskhArabic-Regular.ttf" \
  "https://github.com/notofonts/arabic/raw/main/fonts/NotoNaskhArabic/unhinted/ttf/NotoNaskhArabic-Regular.ttf" \
  "https://github.com/notofonts/noto-fonts/raw/main/hinted/ttf/NotoNaskhArabic/NotoNaskhArabic-Regular.ttf" \
  "https://github.com/notofonts/noto-fonts/raw/main/unhinted/ttf/NotoNaskhArabic/NotoNaskhArabic-Regular.ttf"; do
  fetch "$U" "$FONT" 50000 && break
done
[ -f "$FONT" ] || { echo "FAILED: NotoNaskhArabic-Regular.ttf (download it manually into $FONTS)"; exit 1; }
echo "All assets ready."
