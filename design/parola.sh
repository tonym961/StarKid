#!/usr/bin/env bash
# =============================================================================
# StarKid - la parola del marchio, costruita e non scritta
#
#     ./design/parola.sh            stampa il frammento SVG (un gruppo <g>)
#     ./design/parola.sh misure     stampa larghezze e altezze
#
# PERCHE DISEGNARLA. Prima la parola era "Star" in Verdana grassetto piu
# "Kid" in grassetto d oro. Verdana e un carattere di sistema disegnato per i
# testi piccoli sugli schermi del 1996: va benissimo per leggere, non e un
# marchio. E soprattutto un marchio fatto di testo CAMBIA DISEGNO da un
# dispositivo all altro - su Android Verdana non c e e scatta il ripiego -
# quindi non e un marchio, e un paragrafo.
#
# PERCHE MONOLINEA. Spedire un carattere tipografico vuol dire licenza, peso e
# font-src nella CSP. Ma un alfabeto geometrico arrotondato, quello delle cose
# per bambini, e fatto di cerchi, archi e segmenti tutti dello stesso
# spessore: si COSTRUISCE con riga e compasso invece di ricalcare a mano delle
# curve che verrebbero storte. Qui sotto ogni lettera e fatta di tratti con i
# capi tondi, tutti spessi uguale, su una griglia dichiarata.
#
# LA GRIGLIA (y verso il basso, linea di base a y=0)
#     altezza della x      100     la a, la r, la i
#     altezza maiuscola    140     la S e la K
#     ascendenti           148     la d
#     altezza della t      128     piu bassa della d, come si usa
#     spessore del tratto   26     uguale per tutto
#     raggio di pancia      37     (100-26)/2: cosi la a e alta esattamente
#                                  quanto la x, bordo esterno compreso
#     raggio della S      28.5     due cerchi uguali uno sull altro:
#                                  4*28.5 = 114 = 140-26, cioe la S tocca
#                                  esattamente la linea delle maiuscole
#
# I capi tondi sporgono di meta tratto, quindi ogni segmento parte e finisce
# 13 unita PRIMA del bordo che deve toccare: per questo nei numeri si vede
# sempre il 13.
#
# Il colore non sta nel disegno: e currentColor, cosi la stessa parola e
# bianca sulla testata e scura sulla carta senza farne due copie.
# =============================================================================

set -euo pipefail

# Il secondo argomento e dove sono tagliati i due terminali della S, in gradi
# sopra l orizzontale. Non e un gusto: a -45 e oltre il controforma di sopra
# si apre troppo e la lettera si legge come una "б"; a +35 l arco gira cosi
# tanto che si chiude e si legge "9". Fra i due c e una finestra stretta, ed e
# intorno a -5. Si rivede con:  ./design/parola.sh svg -45
awk -v modo="${1:-svg}" -v A1="${2:--5}" '
BEGIN {
    PI = atan2(0, -1)
    W   = 26; M = W / 2
    XH  = 100; CAP = 140; ASC = 148; TH = 134
    RHO = 37; RS = 28.5; RR = 28
    SP  = 15

    x = 0

    # ---- S: due cerchi uguali tangenti al centro -------------------------
    # Si entra dal terminale in alto a destra, si gira tutto intorno al
    # cerchio di sopra fino al punto di mezzo, poi intorno a quello di sotto
    # fino al terminale in basso a sinistra. Tutti e due gli archi sono piu
    # lunghi di mezzo giro, quindi large-arc = 1; cambia il verso, e per
    # questo il primo ha sweep 0 e il secondo sweep 1.
    cx = x + M + RS
    cyA = -(CAP - M - RS)
    cyB = -(M + RS)
    a1 = A1 * PI / 180
    agg("M" n(cx + RS*cos(a1)) " " n(cyA + RS*sin(a1)))
    agg("A" n(RS) " " n(RS) " 0 1 0 " n(cx) " " n(cyA + RS))
    agg("A" n(RS) " " n(RS) " 0 1 1 " n(cx - RS*cos(a1)) " " n(cyB + RS*sin(a1)))
    chiudi(); misura("S", x, W + 2*RS); x += W + 2*RS + SP

    # ---- t: asta dritta e traversa sulla linea della x -------------------
    sx = x + M
    agg("M" n(sx) " " n(-(TH - M)) "L" n(sx) " " n(-M))
    agg("M" n(x) " " n(-XH) "L" n(x + 48) " " n(-XH))
    chiudi(); misura("t", x, 48); x += 48 + SP - 3    # la t ha il vuoto sotto la traversa

    # ---- a: pancia tonda e asta a destra ---------------------------------
    # La a a un piano, quella che i bambini imparano a scrivere.
    cx = x + M + RHO
    cerchio(cx, -(M + RHO), RHO)
    agg("M" n(cx + RHO) " " n(-(XH - M)) "L" n(cx + RHO) " " n(-M))
    chiudi(); misura("a", x, W + 2*RHO); x += W + 2*RHO + SP - 4   # tondo contro asta

    # ---- r: asta e spalla, un quarto di cerchio --------------------------
    sx = x + M
    agg("M" n(sx) " " n(-(XH - M)) "L" n(sx) " " n(-M))
    agg("M" n(sx) " " n(-(XH - M - RR)))
    agg("A" n(RR) " " n(RR) " 0 0 1 " n(sx + RR) " " n(-(XH - M)))
    chiudi(); misura("r", x, W + RR); x += W + RR + SP - 11  # la r lascia un vuoto in basso a destra

    # ---- K: asta e due diagonali che si incontrano sull asta -------------
    sx = x + M
    gy = -72
    agg("M" n(sx) " " n(-(CAP - M)) "L" n(sx) " " n(-M))
    agg("M" n(sx + 48) " " n(-(CAP - M)) "L" n(sx + 2) " " n(gy))
    agg("M" n(sx + 6) " " n(gy) "L" n(sx + 52) " " n(-M))
    chiudi(); misura("K", x, W + 52); x += W + 52 + SP

    # ---- i: asta e punto -------------------------------------------------
    sx = x + M
    agg("M" n(sx) " " n(-(XH - M)) "L" n(sx) " " n(-M))
    chiudi(); pallino(sx, -(XH + 22)); misura("i", x, W); x += W + SP

    # ---- d: pancia e asta lunga ------------------------------------------
    cx = x + M + RHO
    cerchio(cx, -(M + RHO), RHO)
    agg("M" n(cx + RHO) " " n(-(ASC - M)) "L" n(cx + RHO) " " n(-M))
    chiudi(); misura("d", x, W + 2*RHO); x += W + 2*RHO

    if (modo == "misure") {
        printf "larghezza %s   altezza %s   (il punto della i arriva a %s)\n",
               n(x), n(ASC), n(XH + 22 + M)
        for (i = 1; i <= nl; i++)
            printf "   %s  da %-7s larga %s\n", lett[i], da[i], lar[i]
        exit 0
    }
    if (modo == "larghezza") { print n(x); exit 0 }

    printf "<g fill=\"none\" stroke=\"currentColor\" stroke-width=\"%s\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n", n(W)
    printf "%s", corpo
    printf "%s", pieni
    print "</g>"
}

function agg(s) { fr = fr s }
function chiudi() { corpo = corpo "  <path d=\"" fr "\"/>\n"; fr = "" }
function cerchio(ccx, ccy, rr) {
    # Un cerchio intero come DUE mezzi archi: un unico arco da 360 gradi in
    # SVG non disegna niente, perche partenza e arrivo coincidono.
    agg("M" n(ccx - rr) " " n(ccy))
    agg("A" n(rr) " " n(rr) " 0 1 1 " n(ccx + rr) " " n(ccy))
    agg("A" n(rr) " " n(rr) " 0 1 1 " n(ccx - rr) " " n(ccy))
}
function pallino(px, py) {
    pieni = pieni "  <circle cx=\"" n(px) "\" cy=\"" n(py) "\" r=\"" n(M) "\" fill=\"currentColor\" stroke=\"none\"/>\n"
}
function misura(l, da_, lar_) { nl++; lett[nl] = l; da[nl] = n(da_); lar[nl] = n(lar_) }
function n(v,   s) {
    s = sprintf("%.2f", v)
    sub(/\.?0+$/, "", s)
    if (s == "-0") s = "0"
    return s
}
' /dev/null
