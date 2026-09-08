# Verbigem Android — historial de cambios

---

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
