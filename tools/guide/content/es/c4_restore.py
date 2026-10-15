TRANSLATIONS = {
    "_chapter": {
        "title": "La pestaña Restore",
        "intro": "Recupera fotos y vídeos de tu Cloud. Solo lee de la Cloud; nada allí se modifica.",
        "en_hash": "e472722c1c5b626f",
    },
    "restore-overview": {
        "title": "Qué recupera Restore",
        "body": """• **To restore:** un archivo optimizado en el teléfono. Restaurarlo pone en su lugar el original a tamaño completo.
• **To download:** un archivo que ya no está en el teléfono. Descargarlo lo devuelve a su álbum.

Los archivos restaurados reciben **Keep at full size**, así que no se vuelven a optimizar ni a archivar de inmediato. Consulta [[album-file-pin]].

## Qué Clouds
• **OneDrive:** todas las fotos y vídeos de tus carpetas de copia, los haya puesto quien los haya puesto.
• **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2:** los archivos que GallerySync envió desde este teléfono.
• **Google Photos:** no se puede restaurar desde ella.

## Adónde van los archivos
De vuelta a la carpeta de la que vinieron. Los archivos sueltos en el nivel superior de **DCIM**, **Pictures** o **Movies** vuelven a esa carpeta. Si no se puede escribir en una carpeta, los archivos van a una carpeta de **DCIM** con el nombre del álbum.

## Se puede probar sin riesgo
Un archivo se sustituye solo después de que la descarga llegue completa y su tamaño coincida. Si algo falla, el archivo del teléfono se queda como estaba.""",
        "en_hash": "1bc7e998be6fc106",
    },

    "restore-hero": {
        "title": "Albums to Restore y Files in this album",
        "body": """• **Lista de álbumes:** **Albums to Restore**, con cuántos álbumes tienen algo que recuperar.
• **Dentro de un álbum:** flecha atrás, nombre del álbum y **Files in this album** con el recuento y lo que has seleccionado.

Un guion significa que la lista aún se está cargando. **Checking your Cloud for more...** significa que se está leyendo OneDrive. Un mensaje en rojo significa que no se pudo acceder a una carpeta de la Cloud.

La lista de OneDrive se recuerda, y se vuelve a leer pasados diez minutos o cuando pulsas **Refresh**.""",
        "en_hash": "c073ea612bd19780",
    },

    "restore-message-line": {
        "title": "El mensaje bajo el número",
        "body": """• **Swipe right to select / left to deselect. Tap to open.** en la lista de álbumes.
• **Swipe right to select / left to deselect → Then press Restore.** dentro de un álbum.
• **Nothing in your Cloud is missing from this phone, so there is nothing to bring back.** cuando la lista está vacía.
• Después de una restauración, cuántos archivos volvieron y cuántos se dejaron sin cambios.""",
        "en_hash": "5b14979898ba9b25",
    },

    "restore-selected-summary": {
        "title": "N selected y cuánto hay que recuperar",
        "body": """Los archivos seleccionados en todos los álbumes, y el espacio adicional que ocuparán en tu teléfono. Asegúrate de tener ese espacio libre.""",
        "en_hash": "91898ed468f32d87",
    },

    "restore-buttons": {
        "title": "Refresh, Select all y Clear",
        "body": """• **Refresh** (lista de álbumes): vuelve a leer la lista, incluido OneDrive.
• **Select all** (dentro de un álbum): selecciona todos los archivos.
• **Clear:** quita todas las selecciones.

En gris mientras se ejecuta una restauración.""",
        "en_hash": "69730fbd93d70feb",
    },

    "restore-folders-list": {
        "title": "La lista de álbumes",
        "body": """Cada tarjeta de álbum muestra:
• Cuántos archivos pueden volver, y el espacio que necesitan.
• Cuántos están **to restore** y **to download**.
• Cuántos están **already on this phone** (en gris por dentro; consulta [[restore-greyed-files]]).
• Cuántos están **selected here**, o una marca de verificación cuando todo el álbum está seleccionado.

**Pulsa** para abrir. **Desliza hacia la derecha** para seleccionar todo el álbum, **hacia la izquierda** para deseleccionarlo.""",
        "en_hash": "54ceae2583fe13a0",
    },

    "restore-files-list": {
        "title": "La lista de archivos dentro de un álbum",
        "body": """Desliza hacia la derecha (o pulsa) para seleccionar, hacia la izquierda para deseleccionar. Cada archivo muestra una de estas indicaciones:
• **now · full size:** una copia optimizada, con su tamaño menor en el teléfono y el tamaño completo en la Cloud.
• **Available for download:** no está en el teléfono.
• **Already on this phone:** en gris; no hay nada que hacer.

Mientras trabaja: **Working...** con un porcentaje. Después, **Restored to full size**, **Back on this phone** o **Could not restore, your file is unchanged** con el motivo.""",
        "en_hash": "486e3275bedd2609",
    },

    "restore-greyed-files": {
        "title": "¿Por qué algunos archivos están en gris?",
        "body": """Ya están en el teléfono a tamaño completo (mismo álbum, nombre y tamaño), así que no hay nada que restaurar. Se muestran para que el álbum coincida con tu Cloud.

Un archivo con el mismo nombre pero distinto tamaño no se ofrece: puede ser una edición, y Restore nunca sobrescribe una edición.""",
        "en_hash": "02372472f6d53985",
    },

    "restore-action-bar": {
        "title": "Restore y Stop restoring",
        "body": """• **Restore:** recupera cada archivo seleccionado, uno a uno. Aparece cuando hay algo seleccionado.
• **Stop restoring:** detiene el lote. Los archivos ya restaurados se quedan restaurados.

Una barra de progreso muestra cuántos se han restaurado. Necesita conexión a internet y espacio libre suficiente.""",
        "en_hash": "52b0a1302c2c28cb",
    },

    "restore-edited": {
        "title": "Una foto o un vídeo que has editado",
        "body": """Si editaste una copia optimizada y guardaste sobre ella, restaurar reemplazaría tu edición, así que la app pregunta: **This photo has been edited since it was backed up. Overwrite it with the original?**

• **Keep my edits:** deja el archivo como está.
• **Overwrite:** pone el original; tu edición se reemplaza.

## Conviene saber
La mayoría de los editores guardan un archivo nuevo, que nunca se ve afectado. Un archivo editado se copia como archivo nuevo con **edited** y la fecha en su nombre; el original de tu Cloud nunca se reemplaza.""",
        "en_hash": "6292714bbfa760d3",
    },

}
