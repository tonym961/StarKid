#!/usr/bin/env bash
#
# Pubblica su GitHub una release con dentro una APK firmata.
#
# Serve perche Obtainium - e in generale chi aggiorna una app fuori dal Play
# Store - guarda le RELEASE del repository, non i file in una cartella. La
# cartella dist resta (l'aggiornamento dentro l'app guarda tutte e due), ma
# da sola non basta a chi sta fuori.
#
# IL TOKEN NON STA QUI DENTRO e non deve starci: si passa nell'ambiente.
#
#     GITHUB_TOKEN=... ./strumenti/pubblica-release.sh genitori 1.0.5
#
# Serve un token con permesso di scrittura sui contenuti di questo
# repository. Lo script non lo stampa e non lo scrive da nessuna parte.
#
# PERCHE I TAG HANNO UN PREFISSO. Nel repository ci sono due app: il
# launcher che sta sul tablet e l'app dei genitori che sta sul telefono.
# Hanno numeri di versione loro e si aggiornano per conto loro, quindi
# ognuna ha le sue release: "genitori-1.0.5" e "launcher-1.0.11". Una
# release sola con dentro tutte e due avrebbe un numero di versione solo, e
# sarebbe sbagliato per una delle due.

set -euo pipefail

REPO="tonym961/StarKid"
QUALE="${1:-}"
VERSIONE="${2:-}"

if [ -z "$QUALE" ] || [ -z "$VERSIONE" ]; then
    echo "uso: GITHUB_TOKEN=... $0 <genitori|launcher> <versione>" >&2
    echo "esempio: GITHUB_TOKEN=... $0 genitori 1.0.5" >&2
    exit 2
fi

if [ -z "${GITHUB_TOKEN:-}" ]; then
    echo "manca GITHUB_TOKEN nell'ambiente: senza, GitHub rifiuta." >&2
    exit 2
fi

case "$QUALE" in
    genitori)
        FILE="dist/StarKid-Genitori-${VERSIONE}.apk"
        TAG="genitori-${VERSIONE}"
        TITOLO="StarKid Genitori ${VERSIONE}"
        ;;
    launcher)
        FILE="dist/StarKid-${VERSIONE}.apk"
        TAG="launcher-${VERSIONE}"
        TITOLO="StarKid ${VERSIONE}"
        ;;
    *)
        echo "primo argomento: genitori oppure launcher" >&2
        exit 2
        ;;
esac

RADICE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RADICE"

if [ ! -f "$FILE" ]; then
    echo "non trovo $FILE" >&2
    echo "l'APK va messa in dist con quel nome esatto: e il nome che" >&2
    echo "l'aggiornamento dentro l'app cerca, e il filtro che usa Obtainium." >&2
    exit 1
fi

IMPRONTA="$(sha256sum "$FILE" | cut -d' ' -f1)"
echo "file:     $FILE"
echo "tag:      $TAG"
echo "SHA-256:  $IMPRONTA"

api() {
    curl -sS -H "Authorization: Bearer ${GITHUB_TOKEN}" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" "$@"
}

if api "https://api.github.com/repos/${REPO}/releases/tags/${TAG}" \
    | grep -q '"id"'; then
    echo "la release ${TAG} esiste gia: alza la versione invece di sovrascriverla." >&2
    echo "una release gia pubblicata non si tocca: chi l'ha scaricata ha quella." >&2
    exit 1
fi

CORPO=$(cat <<FINE
{
  "tag_name": "${TAG}",
  "name": "${TITOLO}",
  "body": "APK firmata con la chiave di StarKid.\\n\\nSHA-256: \`${IMPRONTA}\`\\n\\nAndroid rifiuta di installarla sopra una copia firmata da un'altra chiave: e quello il controllo che conta.",
  "draft": false,
  "prerelease": false
}
FINE
)

RISPOSTA="$(api -X POST -d "$CORPO" "https://api.github.com/repos/${REPO}/releases")"
ID="$(printf '%s' "$RISPOSTA" | grep -m1 '"id"' | tr -dc '0-9')"

if [ -z "$ID" ]; then
    echo "GitHub non ha creato la release. Risposta:" >&2
    printf '%s\n' "$RISPOSTA" | head -20 >&2
    exit 1
fi

echo "release creata (id $ID), carico il file..."
api -X POST \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary "@${FILE}" \
    "https://uploads.github.com/repos/${REPO}/releases/${ID}/assets?name=$(basename "$FILE")" \
    > /dev/null

echo "fatto: https://github.com/${REPO}/releases/tag/${TAG}"
