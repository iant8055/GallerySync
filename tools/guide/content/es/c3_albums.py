TRANSLATIONS = {
    "_chapter": {
        "title": "La pestaña Albums",
        "intro": "Donde eliges el modo de cada álbum y ejecutas las copias.",
        "en_hash": "7c39f69f07321c58",
    },
    "albums-overview": {
        "title": "Cómo está organizada la pestaña Albums",
        "body": """• **Cabecera verde:** botones de modo, totales y controles de copia.
• **Línea de estado:** se muestra mientras algo se ejecuta o acaba de terminar.
• **Lista de álbumes:** una tarjeta por álbum, de la A a la Z. Dos columnas en pantalla ancha.

Cada vez que abres la pestaña, mira el teléfono y vuelve a consultar OneDrive (**Rescan** muestra **Checking...**). Las demás Clouds se consultan sobre un archivo justo antes de optimizarlo o quitarlo.

Mirar, filtrar y desplazarse nunca cambian un archivo. Solo elegir un modo lo hace.""",
        "en_hash": "3c7f69542cac04f4",
    },

    "albums-new": {
        "title": "Álbumes nuevos",
        "body": """Cuando aparece un álbum nuevo, una ventana emergente dice **1 new Album is waiting for you to choose a mode**.
• **Show new Albums:** muestra solo los nuevos. **Show all Albums** devuelve el resto.
• **Dismiss:** cierra el mensaje.

Los álbumes nuevos llevan una etiqueta **New** y siempre empiezan en **Off**. Un álbum nuevo nunca puede empezar en Archive.""",
        "en_hash": "8f8d4b55c34a1c4d",
    },

    "albums-filter": {
        "title": "All Albums y los cuatro botones de modo",
        "body": """• Pulsa **Backup**, **Sync**, **Archive** u **Off** para ver solo los álbumes en ese modo.
• Pulsa **All Albums**, o el mismo botón otra vez, para verlos todos.

Filtrar solo cambia lo que ves. Para cambiar un modo, usa el botón de la tarjeta del álbum. Consulta [[modes-in-depth]].""",
        "en_hash": "d4865c5cf08d3857",
    },

    "albums-totals": {
        "title": "Album Mode Count",
        "body": """**Album Mode Count:** el total de cada modo. Se muestra con **All Albums** seleccionado. Los álbumes en **Off** son aquellos sobre los que aún no has decidido.""",
        "en_hash": "61afcd38a3bee22c",
    },

    "albums-all-backed-up": {
        "title": "Todo lo de aquí tiene copia",
        "body": """Se muestra cuando todos los álbumes a la vista tienen archivos y ninguno está esperando a enviarse. Sale del propio registro de la app; la respuesta de la propia Cloud es la última línea de cada tarjeta de álbum.""",
        "en_hash": "79b43044d6005ada",
    },

    "albums-optimised-line": {
        "title": "Optimised and Saved",
        "body": """Con **Sync** seleccionado: cuántos archivos de los álbumes Sync se han optimizado, y el espacio ahorrado.

Los originales se quedan en la Cloud. **Restore** los recupera. Consulta [[how-optimising-works]].""",
        "en_hash": "1fbc295f2322e4d0",
    },

    "albums-archive-lines": {
        "title": "Files archived y Scheduled to leave this phone",
        "body": """Con **Archive** seleccionado:
• **N files archived · X in your Cloud:** archivos que han salido del teléfono, y el espacio que ocupan sus copias en la Cloud.
• **N Scheduled to leave this phone:** archivos que aún están en el teléfono en álbumes Archive, sin contar los que elegiste conservar. **Nothing left to archive** cuando no hay ninguno.

Aquí no se quita nada. La eliminación ocurre en la pestaña Archive. Consulta [[archive-overview]].""",
        "en_hash": "00b01adb4a7cf037",
    },

    "albums-run-controls": {
        "title": "Sync now, Rescan, Pause, Resume y Stop",
        "body": """## Cuando no se está ejecutando nada
• **Sync now:** hace la copia ahora. Si un interruptor Optimise de Settings está activado con su **Mode** en **Manual**, entonces también optimiza (consulta [[settings-optimise-mode]]). Aparece en gris cuando no hay nada que enviar ni optimizar.
• **Rescan:** mira el teléfono y vuelve a consultar OneDrive.

## Mientras se ejecuta una copia
• El progreso aparece como **Syncing** con un porcentaje (por datos, no por número de archivos).
• **Pause:** retiene todas las copias hasta **Resume**.
• **Stop:** termina esta ejecución. Las copias automáticas continúan.

## Conviene saber
• **Sync now** no espera al cargador, pero sigue respetando tu ajuste de Wi-Fi. Consulta [[settings-mobile-data]].
• Hace copia de todos los álbumes en **Backup**, **Sync** o **Archive**, pero solo optimiza archivos de los álbumes **Sync**.""",
        "en_hash": "846b4240970f2d4a",
    },

    "albums-status-line": {
        "title": "La línea de estado bajo la tarjeta",
        "body": """• **Mientras se sube:** el recuento de archivos, el archivo que se está enviando y su progreso.
• **Mientras se prepara:** **Scanning...** o **No permission to read media.**
• **Después de una ejecución:** un resumen de lo que corresponda: **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (se reintenta más tarde), **still to go** o **stopped:** con el motivo.""",
        "en_hash": "78b2b8f3d6801853",
    },

    "albums-list": {
        "title": "La pestaña Albums",
        "body": """## Cabecera
• Pulsa **Backup**, **Sync**, **Archive** u **Off** para ver solo los álbumes en ese modo. Pulsa **All Albums** para volver a verlos todos.
• La línea bajo los botones da el total de cada modo.

## Tarjetas de álbum
• **Nombre, número de archivos y tamaño** del álbum en tu teléfono.
• **Segunda línea** (solo cuando hace falta): cuántos archivos están optimizados, conservados a tamaño completo, pendientes o con fallo.
• **Línea de la Cloud:** **verified in OneDrive** significa que OneDrive confirmó los archivos con el tamaño correcto. Se comprueba cada vez que abres la pestaña. Las demás Clouds muestran **sent to** esa Cloud.
• **Botón de modo** (a la derecha): púlsalo para cambiar el modo del álbum. Archive te pide confirmar antes. Si la Cloud del álbum no puede hacer ese modo, la tarjeta lo indica.
• **Pulsa la tarjeta** para ver los archivos del álbum. Consulta [[album-detail]].""",
        "en_hash": "52297f12afc2a7eb",
    },

    "album-detail": {
        "title": "La lista de archivos de un álbum",
        "body": """Se abre al pulsar una tarjeta de álbum.

• **Cabecera:** flecha atrás, nombre del álbum, modo y número de archivos, **Backs up to** (la Cloud y la carpeta a las que van los archivos nuevos, más cualquier otra Cloud que ya tenga algunos), los recuentos ([[album-file-status]]), **Sort by** ([[album-file-sort]]) y **Keep at full size** ([[album-file-pin]]).
• **Archivos:** una tarjeta por archivo con su tamaño, fecha y marcas. Un archivo optimizado muestra en el teléfono su tamaño menor.
• **Camera folder** (si se eligió en Settings): controles de optimización adicionales. Consulta [[album-camera-optimise]].

Sin miniaturas ni vistas previas. Usa tu app de galería para ver las fotos.""",
        "en_hash": "c5a7c5d01cd7947b",
    },

    "album-file-status": {
        "title": "Marcas y recuentos de archivos en un álbum",
        "body": """• **✓ backed up:** en la Cloud, a tamaño completo en el teléfono.
• **✓ backed up · optimised:** en la Cloud; el teléfono tiene una copia más pequeña.
• **⟳ pending:** esperando a enviarse.
• **✗ failed:** no se pudo enviar tras varios intentos.

## Reintentar los fallidos
**Retry** vuelve a poner en cola los archivos fallidos y empieza una copia. No quita nada.""",
        "en_hash": "a1305dbfcdea48b5",
    },

    "album-file-sort": {
        "title": "Ordenar los archivos de un álbum",
        "body": """• **Name:** de la A a la Z.
• **Date:** primero los más nuevos. Un archivo restaurado tiene la fecha en que se restauró.
• **Status:** fallidos, luego pendientes, luego optimizados, luego con copia.

La lista se abre cada vez ordenada por nombre. Ordenar no cambia nada de los archivos.""",
        "en_hash": "030835fb143a638a",
    },

    "album-file-pin": {
        "title": "Keep at full size",
        "body": """Una casilla junto a los archivos que **Restore** recuperó. Mientras esté seleccionada, GallerySync deja ese archivo en paz:
• En un álbum **Sync**, no se optimiza.
• En un álbum **Archive**, no se quita.
• En **Backup** u **Off**, no cambia nada, pero la elección se recuerda.

Restore la selecciona automáticamente. Deselecciónala para que el archivo vuelva a seguir el modo de su álbum.""",
        "en_hash": "34b89b8b5829a956",
    },

    "album-camera-optimise": {
        "title": "Optimizar fotos y vídeos antiguos de tu Camera folder",
        "body": """Solo la **Camera folder** que elegiste en Settings, en **Camera Sync** ([[settings-section-camera]]), tiene estos controles.

## Seleccionar archivos
• **Select Photos/Videos older than:** de **1 day** a **1 year**, o **All**.
• **Photos** y **Videos:** qué tipos seleccionar.
• Desliza un archivo hacia la derecha para seleccionarlo, hacia la izquierda para deseleccionarlo. La tarjeta muestra cuántos hay seleccionados y el ahorro estimado.

## Botones
• **Sync now:** hace copia de lo que espera en la carpeta y luego optimiza solo los archivos seleccionados.
• **Rescan:** vuelve a comprobar la Cloud.

## Conviene saber
• Solo se pueden seleccionar los archivos que OneDrive ha confirmado a tamaño completo.
• No se optimiza nada hasta que pulsas **Sync now**. Las fotos nuevas se quedan como están.
• La Camera folder no tiene modo **Sync**, y los interruptores Optimise de Settings no la afectan.
• Si sales con las opciones cambiadas, se pregunta si quieres convertirlas en tus valores predeterminados.""",
        "en_hash": "f06a5a7ce89e99c4",
    },

    "album-camera-select": {
        "title": "Seleccionar archivos en tu Camera folder",
        "body": """Muestra cuántos archivos hay seleccionados y el espacio estimado que se ahorra, o **Nothing selected**.

• **Photos**, **Videos** y la antigüedad seleccionan todo lo que coincide.
• Desliza hacia la derecha para seleccionar, hacia la izquierda para deseleccionar.
• **Sync now** optimiza solo los archivos seleccionados.

Un archivo en gris no se puede seleccionar: aún no está confirmado en OneDrive, o ya está optimizado. Consulta [[album-camera-optimise]].""",
        "en_hash": "9904773f638bae7b",
    },

    "album-merge-warning": {
        "title": "Nombres de álbum duplicados detectados",
        "body": """Una sola carpeta puede acabar registrada con dos grafías que solo se diferencian en las mayúsculas. GallerySync lo detecta, las une en un solo álbum y pone su modo en **Off** para que elijas de nuevo. **Dismiss** cierra la tarjeta.

Es la única vez que la app establece un modo, y solo puede establecer **Off**.""",
        "en_hash": "559da2287e55a253",
    },

    "dialog-archive-confirm": {
        "title": "Archive this album? (la confirmación)",
        "body": """Se muestra cuando eliges **Archive**:
• Archive comprueba que todos los archivos están en la Cloud.
• Los archivos archivados pasan a la papelera de tu teléfono.
• Vacía la papelera para liberar el espacio.

**Archive** confirma; **Cancel** no cambia nada.

## Conviene saber
Confirmas una sola vez. Los archivos que se añadan al álbum más tarde también quedan cubiertos, hasta que el álbum esté vacío. Android pregunta una vez por cada lote de hasta 2000 archivos. Consulta [[dialog-android-trash]].""",
        "en_hash": "ac36f7c88ce4bc76",
    },

}
