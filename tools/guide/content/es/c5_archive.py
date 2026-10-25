TRANSLATIONS = {
    "_chapter": {
        "title": "La pestaña Archive",
        "intro": "El único lugar donde los archivos salen de tu teléfono. Solo los álbumes que pones en Archive, comprobados antes con tu Cloud, y solo después de que pulses.",
        "en_hash": "99a3f217222adae4",
    },
    "archive-overview": {
        "title": "Cómo funciona Archive, paso a paso",
        "body": """**Archive necesita Android 11 o posterior.**

1. **Pon un álbum en Archive** en la pestaña Albums y confirma. Consulta [[dialog-archive-confirm]].
2. **Pulsa Check these files.** Se comprueba cada archivo con su Cloud. Los archivos que aún no están allí se envían primero.
3. **Mira el resultado:** una marca de verificación o una cruz roja en cada archivo, y cuánto espacio se liberará.
4. **Responde Yes.** Android pide mover los archivos confirmados a la papelera del teléfono. Consulta [[dialog-android-trash]].
5. **Vacía la papelera** cuando quieras. El espacio solo vuelve entonces.

## Qué significa "a salvo"
La Cloud debe confirmar, en ese mismo momento, que tiene el archivo con el mismo tamaño. Si no se puede consultar a la Cloud, no se quita nada. Para una foto optimizada, se comprueba el tamaño del original.

## Qué Clouds
OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 y Backblaze B2. Google Photos no.

## Conviene saber
• Archive necesita que estés presente: Android exige una pulsación. Consulta [[dialog-exit-warning]].
• Los archivos que se añadan más tarde a un álbum Archive también quedan cubiertos.
• Cuando sale el último archivo, el álbum desaparece de la lista y se olvida su modo. Si vuelve, empieza en Off.""",
        "en_hash": "767ff53f0ad97065",
    },

    "archive-hero": {
        "title": "Files to Archive",
        "body": """**Files to Archive**, con el número de archivos que se archivarán: en todos los álbumes Archive de la lista de álbumes, o en el álbum que abriste. Los archivos que conservaste en el teléfono no se cuentan.

Debajo: tus álbumes Archive, y un recordatorio de que los archivos archivados se pueden recuperar en la pestaña **Restore**.

Cada archivo se comprueba con tu Cloud antes de que algo salga del teléfono. Un archivo que todavía no esté en tu Cloud se envía primero y se verifica antes de contarse.

**▲ Hide Details ▲** al pie de la tarjeta la pliega hasta su título, número y botón principal; **▼ Show Details ▼** devuelve los nombres de álbum, la elección **Only show files older than**, la explicación y el aviso de **Restore**. **Check these files**, sus resultados y la pregunta Yes/No quedan visibles. La pestaña recuerda tu elección.""",
        "en_hash": "487804f5f7434e77",
    },

    "archive-folder-list": {
        "title": "Los álbumes Archive",
        "body": """Una tarjeta por cada álbum Archive: cuántos archivos esperan y su tamaño, además de los que se conservan, los retenidos por el filtro de antigüedad, los confirmados o los no confirmados.

• En la lista de álbumes, el botón dice **Check all albums** y comprueba todos los álbumes.
• Pulsa un álbum para abrirlo; **Check these files** comprueba entonces solo ese álbum.

Elegir un álbum no quita nada ni cambia ningún modo.""",
        "en_hash": "e1a713efaeea9168",
    },

    "archive-check-button": {
        "title": "Check these files y lo que informa",
        "body": """• **Check these files** (o **Check all albums**): inicia la comprobación.
• **Checking with your Cloud...** mientras se consulta a la Cloud.
• Progreso de **Batch** mientras se quitan los archivos.
• Cuántos archivos se quitaron y el espacio liberado, o **Nothing was removed.**

OneDrive se comprueba listando la carpeta de cada álbum en OneDrive (nombre y tamaño). Sobre Dropbox, Google Drive, pCloud, Backblaze B2 e IDrive e2 se pregunta por cada archivo.

"Liberar" significa después de vaciar la papelera.""",
        "en_hash": "651062d720e9e712",
    },

    "archive-age-filter": {
        "title": "Only show files older than…",
        "body": """Limita esta comprobación a los archivos que tengan al menos esa antigüedad: de **1 hour** a **1 year**, o **All**. Los archivos más nuevos aparecen en gris y se dejan para una comprobación posterior.

Empieza con tu valor predeterminado de Settings. Cambiarlo aquí dura solo esta visita.""",
        "en_hash": "3d5d116005b80474",
    },

    "archive-empty": {
        "title": "Cuando no hay nada que archivar",
        "body": """• **Ningún álbum está en Archive:** pon uno en la pestaña Albums.
• **Un cero:** todo lo de tus álbumes Archive ya ha salido del teléfono.
• **Esta versión de Android no tiene papelera de medios:** Archive necesita Android 11 o posterior.""",
        "en_hash": "ebf6a50a3d8d1d39",
    },

    "archive-file-list": {
        "title": "La lista de archivos y sus marcas",
        "body": """Una tarjeta por cada archivo de tus álbumes Archive.

## Conservar un archivo en el teléfono
Úsalo para un archivo que quieres conservar en el teléfono mientras el resto del álbum se archiva. Desliza hacia la **izquierda** para conservarlo: se pone en gris y dice **Not archiving**, y se recuerda. Desliza hacia la **derecha** para incluirlo de nuevo. Un archivo en gris como **too recent for your filter** está retenido por el filtro de antigüedad.

## Marcas
• **Indicador giratorio:** se está comprobando, enviando o quitando.
• **Marca de verificación verde:** confirmado en tu Cloud, o quitado.
• **Cruz roja:** no confirmado; se queda en el teléfono.

## Mensajes
**Backing up...**, **Moving to Trash/Recycle Bin...**, **Removed from this phone.**, o en rojo el motivo por el que un archivo se queda: **Could not check your Cloud**, **Not in your Cloud** o **In your Cloud but the wrong size**.""",
        "en_hash": "4ef90825cb796912",
    },

    "archive-prompt": {
        "title": "La pregunta: All files validated, o algunos validados",
        "body": """Después de la comprobación:
• **All files validated:** todos los archivos están confirmados.
• **N files validated:** solo esos están confirmados; el resto se queda en el teléfono.
• **Nothing can be archived:** ninguno fue confirmado. **Continue** cierra el mensaje.

**Yes** pide a Android que mueva los archivos confirmados a la papelera. **No** no quita nada. Un álbum grande puede necesitar varias confirmaciones de Android (2000 archivos cada una).""",
        "en_hash": "4155582f22850887",
    },

    "dialog-android-trash": {
        "title": "La confirmación propia de Android",
        "body": """Android te pide permiso para mover los archivos a la papelera. Su redacción varía según el teléfono.

• Android permite 2000 archivos por solicitud, así que un álbum grande se procesa por lotes.
• Si dices que no, no se quita nada.""",
        "en_hash": "ea86c62351435e6f",
    },

    "dialog-exit-warning": {
        "title": "Files ready to Archive (el aviso al salir)",
        "body": """Aparece si pulsas Atrás para salir mientras hay archivos confirmados esperando a archivarse.

• **Archive now:** abre la pestaña Archive.
• **Leave:** cierra la app. No se quita nada.

Cuenta solo los archivos de OneDrive: solo las copias de OneDrive se confirman con antelación. Las demás Clouds se comprueban cuando pulsas **Check these files**. El aviso no aparece si pulsas Inicio o cierras la app deslizándola.""",
        "en_hash": "973610b6e4444771",
    },

}
