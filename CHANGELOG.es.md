# Verbigem Android — historial de cambios

---

## v1.0.60 (2026-09-10) — versionCode 61

**Build: SDK de compilación y destino subido a API 36 (Android 16).**

- Google Play exige desde el 2026-08-31 que las aplicaciones nuevas apunten al menos a API 36; subimos `compileSdk` y `targetSdk` de 35 a 36.
- Sin cambios de código; solo el destino de compilación. Los usuarios finales no notan ninguna diferencia.

## v1.0.59 (2026-09-10) — versionCode 60

**Corregido: «Imported 5 contact», «1 mutual friends».**

- Tres mensajes con un número decían siempre lo mismo, fuera cual fuese la cifra: al importar un archivo con cinco contactos salía «Imported 5 contact», y un amigo en común aparecía como «1 mutual friends».
- Ahora esos tres mensajes siguen las reglas de plural de cada idioma. El polaco tiene formas propias para 1, 2–4 y 5 o más («1 kontakt», «3 kontakty», «10 kontaktów»); el turco y el chino conservan una sola forma, porque esos idiomas no flexionan el sustantivo tras un número.
- Se ha hecho con los recursos de plural de Android, no con un «si es 1 … si no» escrito a mano, así que añadir un idioma más adelante no exige tocar el código.


## v1.0.58 (2026-09-10) — versionCode 59

**Corregido: dos sitios que fingían que todo había ido bien.**

- Si no se podía guardar el borrado de una conversación, no había ninguna señal: volvías a la bandeja de entrada y la conversación seguía ahí, sin explicación. Ahora el móvil avisa de que no se pudo hacer.
- Una búsqueda de personas fallida (sin conexión o sin permisos) era idéntica a «no existe ese usuario»: solo una lista vacía. Ahora, bajo el campo de búsqueda se indica que la consulta falló. Una lista vacía vuelve a significar únicamente que no se encontró a nadie.
- Los mensajes están en los 6 idiomas, como el resto de la aplicación.


## v1.0.57 (2026-09-10) — versionCode 58

**Corregido: el segundo mensaje podía quedarse atascado en «enviando».**

- Si enviabas algo mientras el mensaje anterior todavía se estaba enviando (típico con dos fotos seguidas), el nuevo esperaba al siguiente disparo: que volviera la red o que enviaras otra cosa. Ahora la cola se vacía otra vez justo después, de inmediato.
- Cuando Leer Pro no podía generar la voz, no hacía nada: ni sonido ni explicación. Ahora dice claramente que ha fallado.


## v1.0.56 (2026-09-10) — versionCode 57

**Corregido: el micrófono se quedaba callado cuando algo fallaba.**

- La falta de permiso de micrófono, la ausencia de reconocimiento de voz en el teléfono y un error de reconocimiento terminaban solo en una línea del registro del sistema. Para ti las tres situaciones eran idénticas: el micrófono «simplemente no funciona», sin motivo.
- Ahora cada una de esas tres situaciones dice claramente qué ocurrió, con un mensaje breve sobre el campo de escritura. Los textos están en 6 idiomas, como el resto de la aplicación.


## v1.0.55 (2026-09-10) — versionCode 56

**Corregido: una foto o un mensaje de voz que no se enviaba desaparecía sin dejar rastro.**

- Un mensaje de texto normal entra en una cola local: si falla la red, aparece un globo rojo «no enviado» con un botón de reintento. Las fotos y los mensajes de voz iban antes por fuera de esa cola: si fallaban no había globo ni aviso de ningún tipo. El mensaje simplemente desaparecía y lo único que quedaba era una línea en el registro del sistema.
- Ahora las fotos y los mensajes de voz pasan por la misma cola que el texto: el globo aparece de inmediato (con una miniatura directamente del teléfono o con la transcripción) y, si el envío falla, muestra «no enviado» y «reintentar», igual que un mensaje de texto. Al reintentar no hace falta volver a elegir la foto.
- Requiere una migración única de la base de datos local (v9 → v10). No se pierde nada.


## v1.0.54 (2026-09-10) — versionCode 55

**Corregido: los mensajes de error salían en polaco (o en inglés) sin importar el idioma de la aplicación.**

- Algunos errores —inicio de sesión, traducción en modo conversación, reconocimiento de texto desde una foto, Lectura Pro— tenían el texto escrito directamente en el código. Con la aplicación en inglés, alemán, español, turco o chino aparecía igualmente una frase en polaco, p. ej. «Błąd logowania».
- Todos los mensajes de error salen ahora de los mismos recursos que el resto de la interfaz, así que están en los 6 idiomas.
- De paso revisamos toda la localización: 482 textos, ninguno falta en ninguno de los 6 idiomas.

## v1.0.53 (2026-09-09) — versionCode 54

**Nuevo: la pantalla del escáner (OCR) tiene su propio selector de motor.**

- Hasta ahora el escáner traducía siempre con el modelo Rápido, sin importar lo que eligieras en el traductor. Ahora hay la misma barra de selección sobre el campo de texto: ⚡ Rápido, 🎯 Preciso, ⚖️ Ambos, ☁️ En línea.
- La elección se comparte con la pantalla del traductor: la ajustas una vez y vale para ambas.
- ⚖️ Ambos muestra los dos resultados uno bajo el otro, cada uno con su etiqueta (⚡ Rápido / 🎯 Preciso).
- Si los pesos del modelo elegido no están descargados, el escáner muestra el mismo diálogo de descarga que el traductor, sin volver a la pantalla principal.

**Corregido: el encabezado del resultado del escáner decía «(Rápido)» en todos los idiomas.**

- Sobre el resultado estaba escrito «Rápido» de forma fija, también en las versiones inglesa, alemana, turca y china, donde quedaba una palabra polaca. Ahora aparece el nombre del motor elegido.
- El botón **Traducir (X)** muestra ahora el nombre corto del motor (Rápido / Preciso / Ambos / En línea) en lugar de la descripción larga con el tamaño del modelo.

## v1.0.52 (2026-09-09) — versionCode 53

**Corregido: en las cuentas gratuitas no se mostraban anuncios.**

- En las cuentas para las que Google no exige consentimiento (fuera del EEE y del Reino Unido), el formulario de consentimiento podía responder „no se pueden solicitar anuncios" — lo más habitual en una cuenta de AdMob recién aprobada sin „Privacy & messaging" configurado. El SDK de anuncios esperaba entonces un consentimiento que nunca llegaría y el banner se quedaba vacío para siempre. Ahora, tras 3 segundos, la app inicializa los anuncios por su cuenta.
- En el EEE y el Reino Unido no cambia nada: sin tu consentimiento no se cargan anuncios — no incumplimos las normas de Google.
- Además, los registros incluyen ahora el estado completo del consentimiento y el código de error cuando un anuncio no carga, así es más fácil saber por qué el banner está vacío.

**Corregido: el diálogo de descarga y el botón Traducir hablaban del modelo Rápido sin importar qué tier se hubiera elegido.**

- La ventana „Descargar modelo" estaba escrita para el modelo Rápido (~440 MB). Al descargar el Preciso (~1,1 GB) se veía „Rápido" en el título y „~1,1 GB" debajo — información contradictoria.
- El título, el cuerpo, el botón de descarga, la etiqueta de progreso y la de „modelo listo" ahora son paramétricos y muestran el modelo que realmente estás descargando.
- El botón **Traducir (X)** de la pantalla del traductor muestra ahora el motor realmente elegido — Rápido, Preciso, Ambos o En línea — y no siempre „Rápido".

## v1.0.50 (2026-09-09) — versionCode 51

**Arreglado: las cuentas PRO vuelven a poder iniciar sesión.**

- La Cloud Function guardaba `noAdsUntil` como `Firestore Timestamp`, pero Android esperaba un número plano (ms). Al leer el perfil la app reventaba con „Failed to convert a value of type com.google.firebase.Timestamp to long" y **las cuentas PRO no podían iniciar sesión**. `UserProfile` ahora acepta ambos formatos y las escrituras nuevas se guardan como `number`.

## v1.0.49 (2026-09-09) — versionCode 50

**Anuncios en la versión gratuita, con control total del consentimiento.**

- En la pantalla de traducción aparece ahora un **banner publicitario de Google**. Las cuentas PRO 💎 (compra de eliminación de anuncios o saldo > 0) no ven anuncios: nada cambia para ellas.
- En el EEE, el Reino Unido y Suiza mostramos un **formulario de consentimiento de Google** en el primer arranque. Los anuncios solo se cargan después de tu decisión, nunca antes.
- En la tarjeta **Privacidad** de tu perfil añadí **Ajustes de privacidad de anuncios**: puedes cambiar o retirar tu consentimiento en cualquier momento.
- Los anuncios los sirve Google. El texto de tus traducciones nunca llega a la red publicitaria: la traducción se hace en tu dispositivo.

## v1.0.48 (2026-09-08) — versionCode 49

**El estado PRO ahora se calcula, no se guarda de forma permanente.**

- La cuenta es PRO solo con compra activa de eliminación de anuncios (`noAdsUntil` en el futuro) o saldo de monedero > 0.
- Al caducar `noAdsUntil` con monedero vacío, la cuenta vuelve a Free. El campo `plan` es solo informativo.

## v1.0.47 (2026-09-08) — versionCode 48

**Estado de la cuenta tras comprar «Quitar anuncios».**

- Al comprarlo **la cuenta pasa a PRO 💎**. No solo desaparece el banner: también se desbloquean las funciones Pro (antes algunas seguían viendo la cuenta como Free).
- La tarjeta de estado muestra ahora **siempre el saldo de la cartera** — si nunca la recargaste verás **0.00**. Quitar anuncios y la cartera son dos pagos distintos.
- Debajo hay un nuevo **contador hasta que vuelvan los anuncios**: ves de un vistazo cuántos días quedan del periodo pagado.

## v1.0.46 (2026-09-08) — versionCode 47

**Quitar anuncios — pago único, sin suscripción.**

- Junto a **Recargar cartera** en la tarjeta de estado de la cuenta aparece ahora el botón **Quitar anuncios**. Abre un pago único seguro: eliges un periodo y, tras pagar, el banner de anuncios se oculta durante ese tiempo.
- Cuatro opciones a elegir: **$1 · 1 mes**, **$3 · 3 meses**, **$5 · 5 meses**, **$10 · 10 meses**.
- **No es una suscripción**: pagas una sola vez, los anuncios se ocultan durante el periodo elegido y nada se renueva automáticamente. **Tu cuenta pasa a PRO 💎.**
- En la tarjeta de estado de la cuenta verás también el **saldo de la cartera** — si nunca la recargaste aparecerá **0.00** (quitar anuncios no añade créditos, es un pago distinto) — y un **contador hasta que vuelvan los anuncios**, es decir, los días que quedan del periodo pagado.

## v1.0.45 (2026-09-08) — versionCode 46

**Corregido: traducción online de pago y recarga de cartera.**

- El servidor de Verbigem se ha mudado a otra región (la misma del proyecto). La app seguía llamando a la dirección antigua, por lo que **la traducción con los modelos online de pago fallaba** — ahora vuelve a funcionar.
- **Recargar la cartera desde la app no funcionaba en absoluto** — el botón no podía abrir la pasarela de pago por el mismo motivo. Corregido; los créditos se añaden automáticamente tras el pago.

## v1.0.44 (2026-09-07) — versionCode 45

**Más fácil usar tu propia clave de OpenRouter.**

- La tarjeta de selección del modelo online ahora se llama **Modelo de traducción en línea predeterminado**, para que quede claro que ajusta el modelo usado en las traducciones online.
- La tarjeta **Clave propia de OpenRouter** (modelos gratis con tu propia clave) ahora tiene un enlace clicable a la página de generación de claves — tanto en la descripción bajo la tarjeta como en la ventana de ayuda. En cuanto te registras en OpenRouter, un toque te lleva directo a crear la clave.

## v1.0.43 (2026-09-07) — versionCode 44

**Recarga tu cuenta desde la propia app.**

- La tarjeta de estado de la cuenta ahora tiene un botón **Recargar monedero**. Abre un pago seguro y, en cuanto pagas, añade los créditos a tu monedero automáticamente — sin recargar ni escribir nada a mano.
- Tres paquetes para elegir: Pequeño (300 créditos), Mediano (500 créditos) y Grande (1000 créditos).
- El saldo de tu monedero se actualiza solo en segundo plano justo después de un pago correcto.

## v1.0.42 (2026-09-06) — versionCode 43

**La ayuda ahora funciona también en los botones desactivados.**

- Mantener pulsado un botón muestra su explicación incluso cuando está grisado — por ejemplo un campo de texto vacío, motores bloqueados en una cuenta gratuita o cuando no hay foto que leer.

## v1.0.41 (2026-09-06) — versionCode 42

**Correcciones de aspecto tras la llegada de las ventanas de ayuda.**

- El botón «Entendido» de las ventanas de ayuda ahora siempre está en el idioma de interfaz que elegiste.
- La barra inferior vuelve a tener 5 iconos: Traducir, Conversación, Chat, Contactos, Perfil.
- La pantalla Traducir se desplaza sobre el teclado mientras escribes.
- Los iconos de motores (preciso, ambos, en línea) muestran ayuda incluso en una cuenta gratuita.
- El perfil ganó una tarjeta **Acerca de la app** con el número de versión y un enlace **Novedades**.
- El icono de la app ahora usa un fondo crema acorde al tema de las páginas.

## v1.0.40 (2026-09-06) — versionCode 41

**Cada icono de la app ahora tiene una ventana explicativa.**

- Tocar un icono ejecuta su acción; mantenerlo pulsado abre una descripción de qué es, qué hace y cómo usarlo.
- Esto cubre las pantallas Traducir, Conversación, Chat, Contactos y Perfil — decenas de nuevas descripciones en 6 idiomas.

## v1.0.39 (2026-09-05) — versionCode 40

**El mayor conjunto de novedades hasta ahora.**

- Chat 1:1 y Contactos: invitaciones, importar contactos guardados (archivos .vcf) y encontrar amigos.
- La verificación del número de teléfono por SMS funciona en cualquier región del mundo, con mensajes de error claros.
- Notificaciones push (FCM) para mensajes nuevos.
- Fotos con vista previa a pantalla completa y lectura de texto desde imágenes (OCR) en el chat.
- Tus propios códigos QR para añadir amigos, más un escáner de códigos.
- Una política de privacidad en 6 idiomas y una explicación clara de los permisos.
- La app funciona en 6 idiomas: polaco, inglés, español, chino, alemán y turco.
