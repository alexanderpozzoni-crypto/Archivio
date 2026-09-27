ARCHIVIO 8.0 S26 COMPLETO

Funzioni incluse
- Scansione diretta memoria telefono: nessuna copia in Documenti_Unificati.
- Indicizzazione incrementale: file invariati saltati.
- Deduplica SHA-256.
- PDF testuali e PDF scansionati con OCR.
- OCR JPG/JPEG/PNG/WEBP.
- TXT, CSV, LOG, EML, RTF, DOCX, XLSX, ZIP.
- Apertura del file originale.
- Gmail in sola lettura tramite OAuth.
- AI locale vera con MediaPipe LLM Inference.
- RAG: Archivio cerca i documenti pertinenti e passa il contenuto al modello locale.
- Risposte con richiesta esplicita di citare i nomi dei documenti.
- Pulsante SCARICA AI: login/licenza Gemma e download vengono gestiti nello stesso flusso dentro Archivio.
- Pulsante IMPORTA AI: resta disponibile per qualsiasi modello .task compatibile già presente sul telefono.

PRIMO AVVIO
1. ACCESSO FILE -> concedere gestione completa file.
2. SCARICA AI -> nella pagina Hugging Face accedere e accettare la licenza Gemma se richiesta.
3. Premere SCARICA MODELLO. Il file viene salvato direttamente nella memoria privata di Archivio e caricato.
4. GMAIL -> scegliere/autorizzare l'account.
5. AGGIORNA TUTTO.
6. Scrivere una domanda e premere CHIEDI AD ARCHIVIO.

NOTA GMAIL
L'APK compilato con CodeAssist deve avere package it.archivio.app e il certificato SHA-1 di firma registrato nel client OAuth Android di Google Cloud con Gmail API abilitata. Questa configurazione è esterna al codice e non può essere incorporata genericamente nel progetto.

NOTA GEMMA
Archivio non aggira la licenza. Usa la sessione web dell'utente per scaricare il modello solo dopo login/accettazione. Se Hugging Face risponde 401/403, la schermata indica di completare login/licenza e ripremere il pulsante.
