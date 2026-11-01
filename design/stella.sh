#!/usr/bin/env bash
# =============================================================================
# StarKid - la geometria della stella del marchio, calcolata
#
#     ./design/stella.sh percorso R ratio dOut dIn cx cy [precisione]
#     ./design/stella.sh bbox    R ratio dOut dIn
#
# PERCHE UN GENERATORE E NON UN PATH SCRITTO A MANO. Il path che c'era prima
# era questo:
#     M16 5.5l3.2 6.9 7.3.9-5.4 5.1 1.4 7.4L16 22.2l-6.5 3.6 1.4-7.4-5.4-5.1z
# dieci vertici messi a occhio, con le punte non tutte uguali e gli angoli
# vivi. A 16px non si vede, a 108dp sull'icona dell'app si vede benissimo. Una
# stella e cinque punte a 72 gradi l'una dall'altra: e un calcolo, non un
# disegno, e se e un calcolo va calcolato.
#
# COME E COSTRUITA
#   - 5 punte sul raggio R, a -90+72k gradi: la prima in alto, esatta
#   - 5 incavi sul raggio R*ratio, a meta fra due punte
#   - ratio sceglie la ciccia: 0,382 e la stella del pentagramma, spigolosa;
#     piu alto e piu tonda e piu piena, che a 16px si legge meglio
#   - ogni vertice e arrotondato con una quadratica il cui controllo E il
#     vertice: si entra a distanza d sul lato precedente, si esce a distanza d
#     sul successivo. Una punta cosi resta una punta (si assottiglia), mentre
#     un arco di cerchio la taglierebbe piatta
#   - dOut e dIn sono frazioni della lunghezza del lato (tutti e dieci i lati
#     sono uguali, per simmetria), quindi l'arrotondamento scala con la
#     stella: ingrandire non scopre mai angoli vivi
#
# Con "bbox" stampa l'ingombro vero dei vertici: serve a centrare la stella
# nel suo riquadro invece di fidarsi che il centro geometrico sia il centro
# ottico. In una stella con la punta in su non lo e: sotto ci sono due punte
# e sopra una sola.
# =============================================================================

set -euo pipefail

modo=${1:?uso: stella.sh percorso|bbox ...}
shift

awk -v modo="$modo" -v R="${1:-100}" -v ratio="${2:-0.5}" \
    -v dOut="${3:-0.34}" -v dIn="${4:-0.26}" \
    -v cx="${5:-0}" -v cy="${6:-0}" -v prec="${7:-3}" '
BEGIN {
    PI = atan2(0, -1)
    r  = R * ratio

    # I dieci vertici, in giro: punta, incavo, punta, incavo...
    # y cresce in giu (coordinate SVG), per questo il seno e negato: la punta
    # 0 deve stare IN ALTO.
    for (i = 0; i < 10; i++) {
        raggio = (i % 2 == 0) ? R : r
        a = (-90 + i * 36) * PI / 180
        vx[i] = cx + raggio * cos(a)
        vy[i] = cy + raggio * sin(a)
    }

    # La lunghezza del lato: uguale per tutti e dieci, ma la si misura invece
    # di crederci.
    L = dist(vx[0], vy[0], vx[1], vy[1])
    for (i = 1; i < 10; i++) {
        d = dist(vx[i], vy[i], vx[(i+1)%10], vy[(i+1)%10])
        if (d < L - 1e-9 || d > L + 1e-9) {
            printf("ERRORE: lato %d lungo %.6f invece di %.6f\n", i, d, L) > "/dev/stderr"
            exit 1
        }
    }

    if (dOut + dIn > 1) {
        printf("ERRORE: dOut+dIn = %.3f > 1: gli arrotondamenti si sovrappongono\n",
               dOut + dIn) > "/dev/stderr"
        exit 1
    }

    # Per ogni vertice: P sul lato entrante, Q su quello uscente.
    for (i = 0; i < 10; i++) {
        t = ((i % 2 == 0) ? dOut : dIn)
        p = (i + 9) % 10
        n = (i + 1) % 10
        px[i] = vx[i] + (vx[p] - vx[i]) * t
        py[i] = vy[i] + (vy[p] - vy[i]) * t
        qx[i] = vx[i] + (vx[n] - vx[i]) * t
        qy[i] = vy[i] + (vy[n] - vy[i]) * t
    }

    # Ingombro VERO della figura arrotondata, che non e quello dei vertici:
    # una quadratica col controllo sul vertice non ci arriva mai, si ferma al
    # punto di t=0,5, cioe 0,25P + 0,5V + 0,25Q. Centrare sui vertici
    # lascerebbe aria in cima, dove la punta e una sola, e la stella starebbe
    # storta nel riquadro. Per una quadratica lo estremo sta in
    # t = (P-V)/(P-2V+Q), se cade fra 0 e 1.
    # NB: niente apostrofi qui dentro, il programma awk sta fra apici singoli.
    if (modo == "bbox") {
        minx = 1e9; maxx = -1e9; miny = 1e9; maxy = -1e9
        for (i = 0; i < 10; i++) {
            t = ((i % 2 == 0) ? dOut : dIn)
            p = (i + 9) % 10
            n = (i + 1) % 10
            ax = vx[i] + (vx[p] - vx[i]) * t; ay = vy[i] + (vy[p] - vy[i]) * t
            bx = vx[i] + (vx[n] - vx[i]) * t; by = vy[i] + (vy[n] - vy[i]) * t
            estremi(ax, vx[i], bx, "x")
            estremi(ay, vy[i], by, "y")
        }
        printf("minx %.4f maxx %.4f miny %.4f maxy %.4f largh %.4f alt %.4f lato %.4f\n",
               minx, maxx, miny, maxy, maxx-minx, maxy-miny, L)
        exit 0
    }

    fmt = "%." prec "f"
    out = "M" n2(px[0]) " " n2(py[0])
    for (i = 0; i < 10; i++) {
        out = out "Q" n2(vx[i]) " " n2(vy[i]) " " n2(qx[i]) " " n2(qy[i])
        j = (i + 1) % 10
        if (i < 9) out = out "L" n2(px[j]) " " n2(py[j])
    }
    print out "Z"
}
function dist(ax, ay, bx, by) { return sqrt((ax-bx)*(ax-bx) + (ay-by)*(ay-by)) }
function estremi(a, v, b, asse,   t, e, den) {
    agg(a, asse); agg(b, asse)
    den = a - 2*v + b
    if (den > 1e-12 || den < -1e-12) {
        t = (a - v) / den
        if (t > 0 && t < 1) {
            e = (1-t)*(1-t)*a + 2*(1-t)*t*v + t*t*b
            agg(e, asse)
        }
    }
}
function agg(val, asse) {
    if (asse == "x") { if (val < minx) minx = val; if (val > maxx) maxx = val }
    else             { if (val < miny) miny = val; if (val > maxy) maxy = val }
}
function n2(v) {
    s = sprintf("%." prec "f", v)
    sub(/\.?0+$/, "", s)          # 12.500 -> 12.5, 12.000 -> 12
    if (s == "-0") s = "0"
    return s
}
' /dev/null
