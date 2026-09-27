ARCHIVIO 7.0 DEFINITIVO - CODEASSIST

FUNZIONI
- Scansione diretta della memoria del telefono: non usa Documenti_Unificati e non copia i file.
- Aggiornamento incrementale.
- Deduplica SHA-256.
- OCR foto e PDF scansione.
- PDF, JPG/JPEG/PNG/WEBP, ZIP, RTF, DOCX, XLSX, TXT, CSV, LOG, EML.
- Gmail in sola lettura: indicizza nuove email direttamente nel database locale.
- Vero LLM locale tramite Google AI Edge MediaPipe tasks-genai.
- Chat RAG locale: cerca prima i documenti pertinenti e passa il contesto al modello.
- I documenti del telefono non vengono inviati al modello cloud: il modello è locale.
- Un pulsante AGGIORNA TUTTO aggiorna telefono + Gmail.

PRIMO AVVIO
1. ACCESSO FILE -> abilita accesso a tutti i file.
2. MODELLO AI -> seleziona un modello compatibile MediaPipe LLM Inference in formato .task.
   L'app ne crea una copia privata in /data/data/it.archivio.app/files/local_model.task.
3. GMAIL -> scegli l'account Google e autorizza accesso gmail.readonly.
4. Premi AGGIORNA TUTTO.
5. Scrivi una domanda e premi CHIEDI AD ARCHIVIO.

IMPORTANTE GMAIL / CODEASSIST
Google OAuth identifica le app Android tramite package name + certificato SHA.
Package: it.archivio.app
Per usare Gmail con una build firmata da CodeAssist, il relativo certificato SHA-1 deve essere
registrato nel client OAuth Android del progetto Google Cloud con Gmail API abilitata.
Questa configurazione Google non può essere incorporata genericamente nello ZIP perché dipende
dalla chiave con cui CodeAssist firma l'APK.

MODELLO AI
Dipendenza: com.google.mediapipe:tasks-genai:0.10.24
L'app non contiene il modello per non creare un APK enorme; lo selezioni una volta.
Serve un modello .task compatibile con LLM Inference. Dopo il caricamento lavora on-device.

FIX1: rimossi setTopK/setTemperature da LlmInferenceOptions per compatibilità tasks-genai 0.10.24.
