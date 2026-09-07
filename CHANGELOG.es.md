# Verbigem Android — registro de cambios


> ⚠️ Las etiquetas `v1.0.1`–`v1.0.3` son **compilaciones históricas tempranas** (versionCode 2–3).
> La versión actual es **v1.0.41** (versionCode 42).
> La versión está en `app/build.gradle.kts` (`versionCode` / `versionName`).

---

## v1.0.41 (2026-09-06) — versionCode 42

**Correcciones de UI tras la regla global de ventanas de ayuda (v40).**

- **"Entiendo"** en la ventana de ayuda ahora sigue el idioma de la interfaz (antes quedaba en polaco). `HelpWindow` captura `LocalContext` antes de `Dialog{}` y lo restaura con `CompositionLocalProvider`.
- **Barra inferior vuelve a 5 iconos** (Traductor, Conversación, Chat, Contactos, Perfil). La sexta posición OCR saturaba la barra.
- **Traductor**: el botón "Traducir" se desplaza sobre el teclado (`bringIntoViewRequester` + `LaunchedEffect(WindowInsets.isImeVisible)` con `delay(250)`).
- **Iconos de motor (preciso, ambos, en línea)** muestran ayuda con pulsación larga incluso desactivados.
- **Conversación y OCR**: subtítulos redundantes eliminados — el botón "?" ya explica la página.
- **Contactos → Del teléfono**: los botones "Buscar amigos en contactos" e "Importar .vcf" ahora tienen ayuda (pulsación larga).
- **Perfil**: el selector de idioma tiene marco visible; debajo de la tarjeta de privacidad aparece la tarjeta **Acerca de** con versión y enlace **Novedades**.
- **Icono de la app**: fondo del icono adaptable de verde (#2C6B85) a crema (`CalmDayBg` #F7F5F1). PNGs en cinco densidades 108/162/216/324/432 px (Lanczos).

---

## v1.0.40 (2026-09-06) — versionCode 41

**Regla global de interfaz: tocar un icono = hace su trabajo, mantenerlo pulsado = ventana de ayuda.**

- **Cada icono de la aplicación** se explica ahora: qué es, qué hace y cómo se usa. Nueva
  infraestructura común en `ui/components/HelpDialog.kt` (`HelpWindow`, `helpClickable`,
  `HelpIconButton`, `HelpFramedIconButton`, `QuestionMarkButton`, `ScreenHeader`).
- **Cada encabezado de pantalla**: logo de luciérnaga (transparente) + título + un botón **„?"**
  a la derecha que abre la explicación de toda la página.
- **Traductor**: ayuda en ambos selectores de idioma y en el icono de intercambio; cuatro motores
  con subtítulos cortos (rápido / preciso / ambos / en línea) y ventanas completas — las antiguas
  descripciones bajo los motores han **desaparecido**; micrófono / cámara / cámara Pro ahora tienen
  **marco** y los subtítulos „de la voz" / „de la foto" / „de la foto pro"; ayuda para el botón
  Traducir, los cinco iconos de las tarjetas de historial y resultado, y la barra inferior.
- **Conversación**: logo + „?" (incluida la nota de que la conversación nunca se guarda y nunca
  sale del dispositivo), ayuda en los campos de idioma, intercambio, micrófono y botón de envío.
- **OCR ya tiene entrada en la barra inferior** — la barra tiene seis pestañas (era la única
  pantalla sin navegación).
- **Contactos**: Amigos / Invitaciones / Del teléfono / Externos como **icono sobre texto de
  11.sp** (como en la barra inferior) más ventanas de ayuda.
- **Chat, Perfil, mi código QR, verificación de teléfono** — encabezados „?" y ayuda en los iconos.
- **~50 textos de ayuda nuevos en 6 idiomas** (394 claves, no falta nada en ningún idioma).

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
