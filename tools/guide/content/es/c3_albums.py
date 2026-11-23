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
        "en_hash": "e3efef2f9212aaf3",
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

    "albums-optimized-line": {
        "title": "Optimized and Saved",
        "body": """Con **Sync** seleccionado: cuántos archivos de los álbumes Sync se han optimizado, y el espacio ahorrado.

Los originales se quedan en la Cloud. **Restore** los recupera. Consulta [[how-optimizing-works]].""",
        "en_hash": "701f3a7e8e63d389",
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
• **Sync now:** hace la copia ahora. Si un interruptor de optimización de Settings está activado con su **Modo** en **Manual**, entonces también optimiza (consulta [[settings-optimize-mode]]). Aparece en gris cuando no hay nada que enviar ni optimizar.
• **Rescan:** mira el teléfono y vuelve a consultar OneDrive.

## Mientras se ejecuta una copia
• El progreso aparece como **Syncing** con un porcentaje (por datos, no por número de archivos).
• **Pause:** retiene todas las copias hasta **Resume**.
• **Stop:** termina esta ejecución. Las copias automáticas continúan.

## Conviene saber
• **Sync now** no espera al cargador, pero sigue respetando tu ajuste de Wi-Fi. Consulta [[settings-mobile-data]].
• Hace copia de todos los álbumes en **Backup**, **Sync** o **Archive**, pero solo optimiza archivos de los álbumes **Sync**.""",
        "en_hash": "4c3628a3516a9b52",
    },

    "albums-status-line": {
        "title": "Progreso de subida y mensajes de estado",
        "body": """• **Mientras se sube** (dentro de la tarjeta): el recuento de archivos, el álbum, el archivo que se está enviando y los bytes enviados del tamaño total del archivo. Si aún no se sabe el tamaño del archivo, aparece un porcentaje en su lugar. La barra de progreso va debajo. **▲ Hide Details ▲** oculta las dos.
• **Mientras se prepara** (bajo la tarjeta): **Scanning...** o **No permission to read media.**
• **Después de una ejecución** (bajo la tarjeta): un resumen de lo que corresponda: **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (se reintenta más tarde), **still to go** o **stopped:** con el motivo.""",
        "en_hash": "5e5b683e646d7541",
    },

    "albums-list": {
        "title": "La pestaña Albums",
        "body": """## Cabecera
• Pulsa **Backup**, **Sync**, **Archive** u **Off** para ver solo los álbumes en ese modo. Pulsa **All Albums** para volver a verlos todos.
• La línea bajo los botones da el total de cada modo.
• **▲ Hide Details ▲** al pie de la tarjeta oculta la línea del total de modo y, mientras hay una ejecución en marcha, la línea de subida y su barra; **▼ Show Details ▼** las devuelve. **All Albums**, los cuatro botones de modo y **Sync now** / **Rescan** quedan, y mientras hay una ejecución en marcha también quedan **Syncing…**, **Pause** y **Stop**. La pestaña recuerda tu elección.

## Tarjetas de álbum
• **Nombre, número de archivos y tamaño** del álbum en tu teléfono.
• **Segunda línea** (solo cuando hace falta): cuántos archivos están optimizados, conservados a tamaño completo, pendientes o con fallo.
• **Línea de la Cloud:** **N verified in <Cloud>** o **N of M verified in <Cloud>** significa que la Cloud confirmó los archivos con el tamaño correcto. OneDrive, Dropbox, Google Drive, pCloud, Backblaze B2 e IDrive e2 se comprueban cada vez que abres la pestaña y con **Rescan**. **Could not reach <Cloud>** significa que la última comprobación falló. Antes de la primera comprobación —y siempre en Google Photos, al que no se puede preguntar— la línea pone **N sent to <Cloud>**. La línea está verde cuando todas las Clouds en ella han verificado todo.
• **Botón de modo** (a la derecha): púlsalo para cambiar el modo del álbum. Archive te pide confirmar antes. Si la Cloud del álbum no puede hacer ese modo, la tarjeta lo indica.
• **Pulsa la tarjeta** para ver los archivos del álbum. Consulta [[album-detail]].""",
        "en_hash": "d76ad64ef37ca818",
    },

    "album-detail": {
        "title": "La lista de archivos de un álbum",
        "body": """Se abre al pulsar una tarjeta de álbum.

• **Cabecera:** flecha atrás, nombre del álbum, modo y número de archivos, **Backs up to** (la Cloud y la carpeta a las que van los archivos nuevos, más cualquier otra Cloud que ya tenga algunos), los recuentos ([[album-file-status]]), **Sort by** ([[album-file-sort]]) y **Keep at full size** ([[album-file-pin]]).
• **▲ Hide Details ▲** al pie de la cabecera la pliega hasta el nombre del álbum, el modo y el número de archivos; **▼ Show Details ▼** devuelve el resto. La página recuerda tu elección.
• **Archivos:** una tarjeta por archivo con su tamaño, fecha y marcas. Un archivo optimizado muestra en el teléfono su tamaño menor.
• **Carpeta de la cámara** (si se eligió en Settings): controles de optimización adicionales. Consulta [[album-camera-optimize]].

Sin miniaturas ni vistas previas. **Mantén pulsado un archivo** para abrirlo en la app de fotos o vídeos de tu teléfono, o en su Cloud si ya no está en el teléfono. Consulta [[settings-open-not-on-phone]].""",
        "en_hash": "daf3817bf4e259d3",
    },

    "album-file-status": {
        "title": "Marcas y recuentos de archivos en un álbum",
        "body": """• **✓ backed up:** en la Cloud, a tamaño completo en el teléfono.
• **✓ backed up · optimized:** en la Cloud; el teléfono tiene una copia más pequeña.
• **⟳ pending:** esperando a enviarse.
• **✗ failed:** no se pudo enviar tras varios intentos.

## Reintentar los fallidos
**Retry** vuelve a poner en cola los archivos fallidos y empieza una copia. No quita nada.""",
        "en_hash": "0b4ffba87f22d1c6",
    },

    "album-resend": {
        "title": 'Archivos sin copia',
        "body": """Archivos que este álbum envió y que ya no están en su Cloud. **Rescan** en la pestaña Albums los encuentra, y la tarjeta del álbum los cuenta en rojo como **N files not backed up**.

• Cada uno aparece atenuado y marcado **not in <Cloud>**.
• Nada se vuelve a enviar por sí solo. Puede que se borraran en la Cloud a propósito.
• Desliza a la derecha para seleccionar y a la izquierda para deseleccionar. **Select all** y **Clear** actúan sobre todos.
• **Resend** vuelve a enviar los archivos seleccionados a la Cloud a la que va ahora su carpeta. No elimina nada.
• Los archivos optimizados no aparecen: el original a tamaño completo solo estaba en la Cloud.
• En un álbum **Off**, elige antes un modo.
• En las Clouds distintas de OneDrive, cada Rescan consulta como máximo 25 archivos por álbum que no estén en la carpeta del álbum. Vuelve a pulsar Rescan para el resto.""",
        "en_hash": "966ae07aace959b6",
    },

    "album-file-sort": {
        "title": "Ordenar los archivos de un álbum",
        "body": """• **Name:** de la A a la Z.
• **Date:** primero los más nuevos.
• **Status:** fallidos, luego pendientes, luego optimizados, luego con copia.

La lista se abre cada vez ordenada por nombre. Ordenar no cambia nada de los archivos.""",
        "en_hash": "768559e84cdab251",
    },

    "album-file-pin": {
        "title": "Keep at full size",
        "body": """Una casilla junto a los archivos que **Restore** recuperó. Mientras esté seleccionada, GallerySync deja ese archivo en paz:
• En un álbum **Sync**, no se optimiza.
• En un álbum **Archive**, no se quita.
• En **Backup** u **Off**, no cambia nada, pero la elección se recuerda.

Restore la selecciona automáticamente. Deselecciónala para que el archivo vuelva a seguir el modo de su álbum.""",
        "en_hash": "3691f19075c93c85",
    },

    "album-camera-optimize": {
        "title": "Optimizar fotos y vídeos antiguos de tu carpeta de la cámara",
        "body": """Solo la **Carpeta de la cámara** que elegiste en Settings, en **Sync de la cámara** ([[settings-section-camera]]), tiene estos controles.

## Seleccionar archivos
• **Select Photos/Videos older than:** un número del **½** al **9** y una unidad — **hour**, **day**, **week**, **month**, **year** o **All**.
• **Photos** y **Videos:** qué tipos seleccionar.
• Desliza un archivo hacia la derecha para seleccionarlo, hacia la izquierda para deseleccionarlo. La tarjeta muestra cuántos hay seleccionados y el ahorro estimado.

## Botones
• **Sync now:** hace copia de lo que espera en la carpeta y luego optimiza solo los archivos seleccionados.
• **Rescan:** vuelve a comprobar la Cloud.

## Conviene saber
• Solo se pueden seleccionar los archivos que OneDrive ha confirmado a tamaño completo.
• No se optimiza nada hasta que pulsas **Sync now**. Las fotos nuevas se quedan como están.
• La carpeta de la cámara no tiene modo **Sync**, y los interruptores de optimización de Settings no la afectan.
• Si sales con las opciones cambiadas, se pregunta si quieres convertirlas en tus valores predeterminados.""",
        "en_hash": "19ad4bbf62950ae9",
    },

    "album-camera-select": {
        "title": "Seleccionar archivos en tu carpeta de la cámara",
        "body": """Muestra cuántos archivos hay seleccionados y el espacio estimado que se ahorra, o **Nothing selected**.

• **Photos**, **Videos** y la antigüedad seleccionan todo lo que coincide.
• Desliza hacia la derecha para seleccionar, hacia la izquierda para deseleccionar.
• **Sync now** optimiza solo los archivos seleccionados.

Un archivo en gris no se puede seleccionar: aún no está confirmado en OneDrive, o ya está optimizado. Consulta [[album-camera-optimize]].""",
        "en_hash": "ec8dfe5b2063e196",
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
