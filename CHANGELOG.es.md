# Verbigem Android — registro de cambios

Lo más reciente arriba. Cada versión se publica también como GitHub Release:
<https://github.com/ihletru/verbigem_android/releases>

> ⚠️ Las etiquetas `v1.0.1`–`v1.0.3` son **compilaciones históricas tempranas** (versionCode 2–3).
> La versión actual es **v1.0.39** (versionCode 40).
> La versión está en `app/build.gradle.kts` (`versionCode` / `versionName`).

---

## v1.0.39 (2026-09-05) — versionCode 40

Resumen de todo el desarrollo desde la v1.0.3 hasta hoy.

**Novedades**

- **Chat 1:1 + Contactos (Fase 1–3)**
  - Bandeja de entrada, hilo de chat, pestañas en Contactos (TabRow, 4 pestañas).
  - Importación de `.vcf` (parser propio, sin dependencias), búsqueda en mensajes, «Quizá conozcas a» (`suggestFriends`), invitaciones por número de teléfono.
- **Verificación de número y SMS (Fase 2.4 / 2.6)**
  - Errores comprensibles de Phone Auth, región de SMS desbloqueada (**ALL** — todo el mundo).
  - Correcciones de cierres inesperados: «no activity» (v37), cierre al pulsar «enviar SMS» (v38), errores comprensibles (v39).
- **Notificaciones push FCM** (Fase 2): Cloud Functions + FCM, App Check (secreto HMAC), `matchContacts`.
- **Fotos y OCR** (Fase 5): vista previa de la foto a pantalla completa + progreso de carga, OCR en el chat, transcripción STT en vivo.
- **Códigos QR** (Fase 4): mi código QR (ZXing), escáner GMS Code Scanner, App Links + `assetlinks.json`.
- **Marca Firefly**: icono de launcher transparente (v40), logotipo, paso de App Check en el plan de publicación en Play Store.
- **Privacidad**: política de privacidad en 6 idiomas, aviso destacado para `READ_CONTACTS`.
- **6 idiomas** (pl, en, es, zh, de, tr); auditoría de textos ×6 (257 claves, sin huecos).
- **Runtime de Cloud Functions**: Node 20 → nodejs22 (7 funciones de Android).

---

## v1.0.3 — versionCode 3

Autoactualización desde GitHub raw (`master`), correcciones de interfaz: altavoz Pro en lugar de estrella,
OCR Pro junto al OCR gratuito, menú oculto sobre el teclado.

## v1.0.2

Sincronización reactiva, papelera roja, altavoz Pro para usuarios free con tooltip,
OCR Pro + historial de OCR, el teclado ya no tapa al Traductor.

## v1.0.1 — versionCode 2

Compilación de prueba de autoactualización. Iconos Leer Pro / Eliminar, sincronización con Firestore,
autoactualización desde GitHub Releases.
