"""Server di configurazione di StarKid.

Un solo inquilino, un solo account, qualche tablet. Gira dietro nginx su
127.0.0.1 con un solo worker uvicorn, su una macchina con meno di un giga di
RAM: ogni scelta di questo pacchetto e fatta per quella macchina.
"""

VERSIONE_API = "v1"
