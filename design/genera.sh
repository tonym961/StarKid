#!/usr/bin/env bash
# =============================================================================
# StarKid - scrive TUTTI i file del marchio, da una geometria sola
#
#     ./design/genera.sh
#
# Niente qui dentro e disegnato a mano: la stella la calcola stella.sh, la
# parola la costruisce parola.sh, e questo script li mette insieme nei file
# che poi usano davvero il sito e l app. Se un giorno la stella diventa piu
# tonda, si cambia un numero qui e si rigenerano favicon, testata, pagina di
# accesso e icona Android TUTTE INSIEME: e il motivo per cui esiste questo
# file invece di sette SVG scritti a mano che col tempo divergono.
#
# Cosa scrive:
#     server/web/stella.svg                     la stella col volto
#     server/web/parola.svg                     la parola, bianca e oro
#     server/web/marchio.svg                    stella + parola, per la testata
#     server/web/icona.svg                      la favicon, stella su notte
#     app/.../drawable/ic_launcher_foreground.xml   icona adattiva, davanti
#     app/.../drawable/ic_launcher_background.xml   icona adattiva, dietro
#     app/.../drawable/ic_launcher_monochrome.xml   icona a tinta unita (A13+)
#     design/out/maschere.css                   le due data URI per app.css
#
# LE MISURE (spazio della stella: R=100, centro geometrico in 0,0)
#   La stella e generata con cy=8.277 cosi il suo ingombro VERO - non quello
#   dei vertici - e centrato sullo zero. Da li: larga 172.648, alta 164.610.
#   Il riquadro quadrato -86.324 .. 86.324 la contiene con 2 unita di aria
#   sopra e sotto. Tutti i numeri del volto stanno in questo stesso spazio.
#
# IL VOLTO c e solo dove si vede: da 26px in su. Nella favicon a 16px due
# occhi da mezzo pixel non sono un volto, sono sporco, e infatti li non c e.
# =============================================================================

set -euo pipefail

QUI=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
RADICE=$(cd "$QUI/.." && pwd)
WEB="$RADICE/server/web"
DRW="$RADICE/app/src/main/res/drawable"
OUT="$QUI/out"
mkdir -p "$OUT"

# --- la geometria scelta, in un posto solo -----------------------------------
RATIO=0.46; DOUT=0.30; DIN=0.22
CY=8.277                 # sposta la stella perche il suo ingombro sia centrato
MEZZO=86.324             # mezzo lato del riquadro quadrato
LATO=172.648

# --- i colori, gli stessi token di app.css -----------------------------------
ORO_CHIARO=#FFE48F
ORO=#FFC846              # --oro
ORO_SCURO=#F09A2B
NOTTE=#070B1E            # --notte
NOTTE_ALTA=#1B2550
VOLTO=#0B1330
GUANCIA=#F2714E
BIANCO=#FFFFFF

STELLA=$("$QUI/stella.sh" percorso 100 $RATIO $DOUT $DIN 0 $CY 3)

# La sfumatura in SVG e dichiarata sul riquadro della figura (0..1); Android
# la vuole in coordinate vere. Sono gli stessi due punti, convertiti qui una
# volta sola invece di essere ricopiati a occhio nel file Android.
GRAD_X1=$(awk -v m=$MEZZO -v l=$LATO 'BEGIN{printf "%.2f", -m+0.1*l}')
GRAD_Y1=-82.305
GRAD_X2=$(awk -v m=$MEZZO -v l=$LATO 'BEGIN{printf "%.2f", -m+0.5*l}')
GRAD_Y2=82.305

# Il volto, nello spazio della stella. Gli occhi stanno un filo sopra la meta
# ottica: sotto la meta sembrerebbero scivolati sul mento.
volto() {
  cat <<EOF
  <g fill="$VOLTO">
    <circle cx="-21" cy="-14" r="9.5"/>
    <circle cx="21" cy="-14" r="9.5"/>
  </g>
  <path d="M-15 8Q0 26 15 8" fill="none" stroke="$VOLTO" stroke-width="7.5" stroke-linecap="round"/>
  <g fill="$GUANCIA" opacity="0.3">
    <ellipse cx="-38" cy="4" rx="11" ry="7"/>
    <ellipse cx="38" cy="4" rx="11" ry="7"/>
  </g>
EOF
}

sfumatura() {
  cat <<EOF
    <linearGradient id="oro" x1="0.1" y1="0" x2="0.5" y2="1">
      <stop offset="0" stop-color="$ORO_CHIARO"/>
      <stop offset="0.55" stop-color="$ORO"/>
      <stop offset="1" stop-color="$ORO_SCURO"/>
    </linearGradient>
EOF
}

# ---------------------------------------------------------------------------
# 1. La stella col volto, da sola
# ---------------------------------------------------------------------------
cat > "$WEB/stella.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="-$MEZZO -$MEZZO $LATO $LATO"
     role="img" aria-label="StarKid">
  <!-- Generato da design/genera.sh: non si modifica a mano. -->
  <defs>
$(sfumatura)
  </defs>
  <path d="$STELLA" fill="url(#oro)"/>
$(volto)
</svg>
EOF

# ---------------------------------------------------------------------------
# 2. La parola, bianca e oro
# ---------------------------------------------------------------------------
LARG_PAROLA=$("$QUI/parola.sh" larghezza)
# parola.sh fa un gruppo solo in currentColor. Qui serve "Star" bianco e "Kid"
# d oro, quindi si taglia il gruppo in due: le prime quattro lettere e le
# ultime tre, piu il punto della i che e un <circle> a parte.
PAROLA_SVG=$("$QUI/parola.sh" svg)
# Il punto della i e un <circle> con fill="currentColor" scritto dentro
# l elemento: un fill sul gruppo NON lo tocca, perche l attributo sul figlio
# vince sempre. Senza questa sostituzione il punto veniva bianco in mezzo a
# "Kid" che e d oro, e si vedeva benissimo nello striscione del README.
STAR=$(printf '%s\n' "$PAROLA_SVG" | sed -n '2,5p' | sed "s/currentColor/$BIANCO/g")
KID=$(printf '%s\n' "$PAROLA_SVG" | sed -n '6,9p' | sed "s/currentColor/$ORO/g")

cat > "$WEB/parola.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -148 $LARG_PAROLA 148"
     role="img" aria-label="StarKid">
  <!-- Generato da design/genera.sh: non si modifica a mano. -->
  <g fill="none" stroke-width="26" stroke-linecap="round" stroke-linejoin="round">
    <g stroke="$BIANCO">
$STAR
    </g>
    <g stroke="$ORO" fill="$ORO">
$KID
    </g>
  </g>
</svg>
EOF

# ---------------------------------------------------------------------------
# 3. Il marchio orizzontale: stella piu parola, per la testata
# ---------------------------------------------------------------------------
# La stella sta alta quanto le maiuscole piu un filo, non quanto tutta la
# riga: una stella alta come gli ascendenti schiaccerebbe la parola.
ALT_STELLA=132
SCALA=$(awk -v a=$ALT_STELLA -v l=$LATO 'BEGIN{printf "%.6f", a/l}')
LARG_STELLA=$(awk -v s=$SCALA -v l=$LATO 'BEGIN{printf "%.3f", s*l}')
ARIA=34
LARG_TOT=$(awk -v a=$LARG_STELLA -v b=$ARIA -v c=$LARG_PAROLA 'BEGIN{printf "%.3f", a+b+c}')
# La stella centrata sulla meta fra linea di base e altezza maiuscola.
STELLA_CY=$(awk 'BEGIN{printf "%.3f", -70}')

cat > "$WEB/marchio.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -148 $LARG_TOT 148"
     role="img" aria-label="StarKid">
  <!-- Generato da design/genera.sh: non si modifica a mano. -->
  <defs>
$(sfumatura)
  </defs>
  <g transform="translate($(awk -v s=$LARG_STELLA 'BEGIN{printf "%.3f", s/2}') $STELLA_CY) scale($SCALA)">
    <path d="$STELLA" fill="url(#oro)"/>
$(volto)
  </g>
  <g transform="translate($(awk -v a=$LARG_STELLA -v b=$ARIA 'BEGIN{printf "%.3f", a+b}') 0)"
     fill="none" stroke-width="26" stroke-linecap="round" stroke-linejoin="round">
    <g stroke="$BIANCO">
$STAR
    </g>
    <g stroke="$ORO" fill="$ORO">
$KID
    </g>
  </g>
</svg>
EOF

# ---------------------------------------------------------------------------
# 3b. Lo striscione del README (docs/logo.svg)
# ---------------------------------------------------------------------------
# Quello di prima aveva un razzo che non c entrava con niente e il nome
# scritto con <text> in Verdana: GitHub lo rende con quello che ha, quindi lo
# striscione del progetto si vedeva diverso a seconda di chi guardava. Qui
# dentro non c e piu nessun <text>: e tutto disegno.
BAN_W=960; BAN_H=280; BAN_ALT=120
BAN_SCALA=$(awk -v a=$BAN_ALT 'BEGIN{printf "%.6f", a/148}')
BAN_LARG=$(awk -v s=$BAN_SCALA -v l=$LARG_TOT 'BEGIN{printf "%.3f", s*l}')
BAN_X=$(awk -v w=$BAN_W -v l=$BAN_LARG 'BEGIN{printf "%.3f", (w-l)/2}')
BAN_Y=$(awk -v h=$BAN_H -v a=$BAN_ALT 'BEGIN{printf "%.3f", h/2 + a/2}')
# Le stelle dello striscione: tutte fuori dalla fascia centrale dove sta il
# marchio, cioe sopra y=70 o sotto y=210.
BAN_CIELO="62 48 2.6 0.85
128 236 2 0.60
246 62 1.8 0.70
300 232 2.4 0.50
412 44 1.8 0.60
520 238 2.2 0.50
636 52 2 0.65
742 236 1.8 0.50
828 60 2.6 0.80
902 230 2 0.60
880 42 1.6 0.45
160 252 1.6 0.40"
BAN_STELLE=$(printf '%s\n' "$BAN_CIELO" | awk '{
  printf "  <circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"#FFFFFF\" opacity=\"%s\"/>\n", $1, $2, $3, $4 }')

cat > "$RADICE/docs/logo.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" width="$BAN_W" height="$BAN_H"
     viewBox="0 0 $BAN_W $BAN_H" role="img" aria-label="StarKid">
  <title>StarKid</title>
  <!-- Generato da design/genera.sh: non si modifica a mano. -->
  <defs>
$(sfumatura)
    <linearGradient id="cielo" x1="0" y1="0" x2="0.3" y2="1">
      <stop offset="0" stop-color="#1F2A63"/>
      <stop offset="1" stop-color="$NOTTE"/>
    </linearGradient>
  </defs>
  <rect width="$BAN_W" height="$BAN_H" rx="40" fill="url(#cielo)"/>
$BAN_STELLE
  <g transform="translate($BAN_X $BAN_Y) scale($BAN_SCALA)">
    <g transform="translate($(awk -v s=$LARG_STELLA 'BEGIN{printf "%.3f", s/2}') $STELLA_CY) scale($SCALA)">
      <path d="$STELLA" fill="url(#oro)"/>
$(volto)
    </g>
    <g transform="translate($(awk -v a=$LARG_STELLA -v b=$ARIA 'BEGIN{printf "%.3f", a+b}') 0)"
       fill="none" stroke-width="26" stroke-linecap="round" stroke-linejoin="round">
      <g stroke="$BIANCO">
$STAR
      </g>
      <g stroke="$ORO" fill="$ORO">
$KID
      </g>
    </g>
  </g>
</svg>
EOF

# ---------------------------------------------------------------------------
# 4. La favicon: stella d oro su riquadro notte, SENZA volto
# ---------------------------------------------------------------------------
# A 16px il volto non e un volto, e sporco. Qui conta solo la silhouette, e il
# riquadro scuro serve a staccare dalla linguetta chiara del browser.
FAV=32; FAV_STELLA=24
FAV_SCALA=$(awk -v a=$FAV_STELLA -v l=$LATO 'BEGIN{printf "%.6f", a/l}')
cat > "$WEB/icona.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $FAV $FAV" role="img" aria-label="StarKid">
  <!-- Generato da design/genera.sh: non si modifica a mano.
       Niente volto: a 16px due occhi da mezzo pixel sembrano sporco. -->
  <defs>
$(sfumatura)
    <linearGradient id="cielo" x1="0" y1="0" x2="0.4" y2="1">
      <stop offset="0" stop-color="$NOTTE_ALTA"/>
      <stop offset="1" stop-color="$NOTTE"/>
    </linearGradient>
  </defs>
  <rect width="$FAV" height="$FAV" rx="7" fill="url(#cielo)"/>
  <g transform="translate(16 16) scale($FAV_SCALA)">
    <path d="$STELLA" fill="url(#oro)"/>
  </g>
</svg>
EOF

# ---------------------------------------------------------------------------
# 5. Icona adattiva Android
# ---------------------------------------------------------------------------
# Le stelline del cielo: scritte una volta sola, poi rese nei due formati.
# Prima erano copiate a mano nel file Android, e la copia in SVG per guardarle
# qui sarebbe stata una seconda copia che prima o poi diverge.
# Nessuna e indispensabile: il lanciatore ritaglia una forma qualsiasi dei
# 108dp e queste possono sparire tutte senza che manchi niente.
# Dove stanno: NON a caso. La stella ha cinque punte, quindi fra una punta e
# l altra ci sono cinque vuoti, a -54, 18, 90, 162 e 234 gradi dal suo centro.
# Una stellina messa li, a 30dp dal centro, non tocca mai il disegno e resta
# dentro il cerchio da 36dp del ritaglio tondo, che e il piu stretto dei tre.
# Le ultime tre stanno fuori dai 72dp mostrati: si vedono solo quando il
# lanciatore sposta l icona per il movimento di parallasse.
CIELO="71.6 32.3 1.5 0.46
82.5 65.9 1.3 0.40
54 86.6 1.6 0.44
25.5 65.9 1.3 0.38
36.4 32.3 1.2 0.34
12 16 1.4 0.36
96 20 1.2 0.30
10 94 1.1 0.30"
STELLINE_AND=$(printf '%s\n' "$CIELO" | awk '{
  printf "    <path android:pathData=\"M%s %sa%s %s 0 1 0 %s 0a%s %s 0 1 0 -%s 0\" android:fillColor=\"#FFFFFF\" android:fillAlpha=\"%s\"/>\n",
         $1-$3, $2, $3, $3, 2*$3, $3, $3, 2*$3, $4 }')
STELLINE_SVG=$(printf '%s\n' "$CIELO" | awk '{
  printf "  <circle cx=\"%s\" cy=\"%s\" r=\"%s\" fill=\"#FFFFFF\" opacity=\"%s\"/>\n", $1, $2, $3, $4 }')

# Il riquadro e 108dp ma il lanciatore ne MOSTRA solo i 72 centrali, e di
# sicuri ce ne sono 66. La stella sta in 54dp: a 62 le punte laterali arrivano
# a 35,4 dal centro contro i 36 del cerchio, cioe toccano. Fuori c e solo cielo.
AND_STELLA=54
AND_SCALA=$(awk -v a=$AND_STELLA -v l=$LATO 'BEGIN{printf "%.6f", a/l}')

cat > "$DRW/ic_launcher_foreground.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- Generato da design/genera.sh: non si modifica a mano.
     La stella sta in 62dp dentro i 66 sicuri del riquadro da 108. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:translateX="54" android:translateY="54"
           android:scaleX="$AND_SCALA" android:scaleY="$AND_SCALA">
        <path android:pathData="$STELLA">
            <aapt:attr name="android:fillColor">
                <gradient android:type="linear"
                    android:startX="$GRAD_X1" android:startY="$GRAD_Y1"
                    android:endX="$GRAD_X2" android:endY="$GRAD_Y2">
                    <item android:offset="0" android:color="$ORO_CHIARO"/>
                    <item android:offset="0.55" android:color="$ORO"/>
                    <item android:offset="1" android:color="$ORO_SCURO"/>
                </gradient>
            </aapt:attr>
        </path>
        <path android:pathData="M-30.5 -14a9.5 9.5 0 1 0 19 0a9.5 9.5 0 1 0 -19 0"
              android:fillColor="$VOLTO"/>
        <path android:pathData="M11.5 -14a9.5 9.5 0 1 0 19 0a9.5 9.5 0 1 0 -19 0"
              android:fillColor="$VOLTO"/>
        <path android:pathData="M-15 8Q0 26 15 8" android:strokeColor="$VOLTO"
              android:strokeWidth="7.5" android:strokeLineCap="round"/>
        <path android:pathData="M-49 4a11 7 0 1 0 22 0a11 7 0 1 0 -22 0"
              android:fillColor="$GUANCIA" android:fillAlpha="0.3"/>
        <path android:pathData="M27 4a11 7 0 1 0 22 0a11 7 0 1 0 -22 0"
              android:fillColor="$GUANCIA" android:fillAlpha="0.3"/>
    </group>
</vector>
EOF

cat > "$DRW/ic_launcher_background.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- Generato da design/genera.sh: non si modifica a mano.
     Cielo, non un colore piatto: e lo stesso cielo del tema Spaziale. Le
     stelline stanno sparse su tutti i 108dp perche il lanciatore ne ritaglia
     una forma qualsiasi e nessuna di loro deve essere indispensabile. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear"
                android:startX="0" android:startY="0"
                android:endX="54" android:endY="108">
                <item android:offset="0" android:color="#FF1B2550"/>
                <item android:offset="1" android:color="#FF070B1E"/>
            </gradient>
        </aapt:attr>
    </path>
$STELLINE_AND
</vector>
EOF

cat > "$DRW/ic_launcher_monochrome.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<!-- Generato da design/genera.sh: non si modifica a mano.
     Android 13 e oltre tinge questo strato con i colori del telefono: deve
     essere una sagoma sola, senza sfumature e senza volto, perche il volto a
     tinta unita sparirebbe comunque. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:translateX="54" android:translateY="54"
           android:scaleX="$AND_SCALA" android:scaleY="$AND_SCALA">
        <path android:pathData="$STELLA" android:fillColor="#FFFFFF"/>
    </group>
</vector>
EOF

# ---------------------------------------------------------------------------
# 5b. Gli stessi due strati in SVG, per guardarli senza compilare la APK
# ---------------------------------------------------------------------------
# Non sono una copia a mano: escono dalle stesse variabili dei due file
# Android qui sopra, nello stesso giro di questo script. Servono a montare
# l anteprima con le maschere dei lanciatori (cerchio, quadrato, goccia).
cat > "$OUT/android-sfondo.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
  <defs><linearGradient id="c" x1="0" y1="0" x2="0.5" y2="1">
    <stop offset="0" stop-color="$NOTTE_ALTA"/><stop offset="1" stop-color="$NOTTE"/>
  </linearGradient></defs>
  <rect width="108" height="108" fill="url(#c)"/>
$STELLINE_SVG
</svg>
EOF
cat > "$OUT/android-primo.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="oro" gradientUnits="userSpaceOnUse"
                    x1="$GRAD_X1" y1="$GRAD_Y1" x2="$GRAD_X2" y2="$GRAD_Y2">
      <stop offset="0" stop-color="$ORO_CHIARO"/>
      <stop offset="0.55" stop-color="$ORO"/>
      <stop offset="1" stop-color="$ORO_SCURO"/>
    </linearGradient>
  </defs>
  <g transform="translate(54 54) scale($AND_SCALA)">
    <path d="$STELLA" fill="url(#oro)"/>
$(volto)
  </g>
</svg>
EOF

# ---------------------------------------------------------------------------
# 6. Le due maschere di app.css
# ---------------------------------------------------------------------------
# Le stelline dei giudizi e delle spie usano la STESSA geometria del marchio,
# senza volto e senza sfumatura: fanno parte dello stesso sistema, ma restano
# icone, non marchi. Prima erano identiche al logo, ed era proprio questo a
# far sembrare il logo una icona qualsiasi.
uri() { printf '%s' "$1" | sed -e 's/#/%23/g' -e 's/</%3C/g' -e 's/>/%3E/g' -e 's/"/'"'"'/g'; }
PIENA="<svg xmlns='http://www.w3.org/2000/svg' viewBox='-$MEZZO -$MEZZO $LATO $LATO'><path d='$STELLA'/></svg>"
VUOTA="<svg xmlns='http://www.w3.org/2000/svg' viewBox='-$MEZZO -$MEZZO $LATO $LATO'><path d='$STELLA' fill='none' stroke='black' stroke-width='15' stroke-linejoin='round'/></svg>"
{
  echo "/* Generato da design/genera.sh: non si modifica a mano. */"
  echo "  --m-stella-piena: url(\"data:image/svg+xml,$(uri "$PIENA")\");"
  echo "  --m-stella-vuota: url(\"data:image/svg+xml,$(uri "$VUOTA")\");"
} > "$OUT/maschere.css"

echo "fatto:"
for f in "$RADICE/docs/logo.svg" "$OUT/android-sfondo.svg" "$OUT/android-primo.svg" "$WEB/stella.svg" "$WEB/parola.svg" "$WEB/marchio.svg" "$WEB/icona.svg" \
         "$DRW/ic_launcher_foreground.xml" "$DRW/ic_launcher_background.xml" \
         "$DRW/ic_launcher_monochrome.xml" "$OUT/maschere.css"; do
  printf '   %6s  %s\n' "$(wc -c < "$f")" "${f#$RADICE/}"
done
