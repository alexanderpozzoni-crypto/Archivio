ARCHIVIO 9.0 S26 STABILE

Questa versione riscrive la parte di ricerca per evitare completamente il crash nativo di MediaPipe/LiteRT.

- stesso applicationId: it.archivio.app
- stesso database: archivio.db (quindi l'indice esistente viene mantenuto se installata come aggiornamento)
- ricerca normale separata
- ricerca intelligente per pertinenza separata
- nessun modello AI da caricare
- nessuna libreria MediaPipe/GenAI
- Gmail, scansione, OCR e apertura file restano presenti
- versionCode 50

INSTALLAZIONE: importare il progetto in CodeAssist e compilare/installare SOPRA la versione esistente. Non disinstallare prima, se vuoi conservare il database già indicizzato.
