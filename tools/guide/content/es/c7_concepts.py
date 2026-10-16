TRANSLATIONS = {
    "_chapter": {
        "title": "Cómo funciona, en lenguaje sencillo",
        "intro": "Las ideas que hay detrás de los botones.",
        "en_hash": "a50822757f585a7f",
    },
    "modes-in-depth": {
        "title": "Los cuatro modos",
        "body": """Cada álbum tiene un modo, que se elige con el botón de su tarjeta.

• **Off:** este álbum no se atiende: no se envía ni se cambia nada. Todos los álbumes empiezan aquí.
• **Backup:** copia en tu Cloud; el teléfono nunca se toca.
• **Sync:** hace la copia y luego optimiza, si la optimización está activada en Settings.
• **Archive:** hace la copia, verifica y luego mueve los archivos a la papelera del teléfono después de que pulses. Es el único modo que quita archivos.

## Qué Clouds
Backup funciona con todas las Clouds. Sync y Archive necesitan OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 o Backblaze B2, no Google Photos.

## Carpeta de la cámara
Si estableces una carpeta de la cámara en Settings, ofrece Off, Backup y Archive, con sus propios controles de optimización en lugar de Sync. Consulta [[album-camera-optimise]].

## Cuál elegir
• Dejarlo como está, pero protegido: **Backup**.
• Ahorrar espacio y seguir viéndolo: **Sync**.
• Álbumes antiguos que quieres sacar del teléfono: **Archive**.
• Si dudas: **Off** o **Backup**.

## Conviene saber
• Solo tú eliges los modos. La única excepción es la fusión de álbumes duplicados, que establece **Off**. Consulta [[album-merge-warning]].
• Cambiar a Archive te pide confirmar. Cambiar a otro modo detiene nuevas eliminaciones.""",
        "en_hash": "43d293213ae2b8f7",
    },

    "how-verification-works": {
        "title": "Cómo funciona la verificación",
        "body": """Antes de optimizar o quitar nada, se pregunta a la Cloud, en ese momento, si tiene una copia correcta.

## La prueba
• **OneDrive:** lista la carpeta del álbum; el archivo debe tener el mismo nombre y tamaño.
• **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2:** se pregunta por el archivo exacto; debe estar presente, no en su papelera, y con el mismo tamaño. Para una foto optimizada, el tamaño del original.

## Cuándo
• Al principio de la primera copia, para que solo se envíe lo que falta.
• Cada vez que abres la pestaña Albums o pulsas Rescan (OneDrive).
• Cuando compruebas archivos en la pestaña Archive, y otra vez justo antes de quitarlos.
• Justo antes de optimizar un archivo.

## Tres respuestas
• **Verified:** la Cloud lo tiene.
• **No está, o el tamaño no coincide:** el archivo se queda en el teléfono.
• **No se pudo comprobar:** se trata como desconocido. No se quita ni se optimiza nada.

## Conviene saber
Una comprobación de tamaño no puede detectar todos los tipos de daño. Abre tu Cloud de vez en cuando y revisa algunas fotos.""",
        "en_hash": "3a19869102cce663",
    },

    "how-optimising-works": {
        "title": "Cómo funciona la optimización",
        "body": """Optimizar cambia la copia del teléfono por otra más pequeña, para que el teléfono deje de llenarse mientras todo sigue en tu galería.

## Fotos
Se reducen a unos 2048 píxeles en el lado largo, aproximadamente una décima parte del tamaño. Mismo nombre, lugar, fecha y ubicación, con una pequeña insignia de nube.

## Vídeo
Se recodifica a **480p** (Compresión alta), **720p** (Compresión media) o **1080p** (Compresión baja). Nunca se corta. Automático espera al cargador; Manual se ejecuta con **Sync now**.

## Reglas
• Solo en álbumes **Sync**.
• Solo después de que la Cloud confirme el original con el mismo tamaño.
• Un archivo que no se haría más pequeño se deja como está.

## Recuperar el original
Usa la pestaña **Restore**. Restaura antes de editar si quieres todo el detalle.""",
        "en_hash": "1c7d13215d15f27e",
    },

    "where-deleted-files-go": {
        "title": "Adónde van los archivos quitados",
        "body": """Nada se elimina de forma permanente.

## En tu teléfono
Archive mueve los archivos a la papelera de Android durante unos 30 días:
• Samsung: la **Papelera de Samsung Gallery**.
• Otros teléfonos: la **Papelera** de la app **Files**.

Los archivos que están en la papelera siguen ocupando todo su espacio hasta que la vacías.

## En tu Cloud
Con **Ask**, las copias que eliges quitar van a la **papelera de reciclaje de OneDrive**. Nunca se quita nada de las demás Clouds. La app nunca vacía ninguna papelera.""",
        "en_hash": "ab2cd9530770d041",
    },

    "background-work": {
        "title": "Cuándo ocurre cada cosa",
        "body": """• **Copia:** poco después de que aparezcan fotos nuevas, y cada seis horas. Solo con Wi-Fi, a menos que los datos móviles estén activados; no con batería o almacenamiento bajos. **Sync now** se ejecuta al instante.
• **Optimising photos:** Automático, en cuanto la Cloud confirma un archivo en un álbum Sync. Manual, con **Sync now**.
• **Optimising video:** Automático, después de las copias, mientras se carga el teléfono. Manual, con **Sync now**.
• **Archivos borrados:** con **Ask**, se te avisa poco después de borrar un archivo que tiene copia en la Cloud (no mientras las copias están en pausa).
• **Archivar:** solo cuando respondes Yes en la pestaña Archive.
• **Restaurar:** solo cuando pulsas Restore.

## Cerrar la app
Salir con Atrás o Inicio no pasa nada: el trabajo continúa. Cerrar la app deslizándola desde las apps recientes puede detener el trabajo en segundo plano hasta que la abras de nuevo.""",
        "en_hash": "b52b93819f999605",
    },

    "where-your-data-lives": {
        "title": "Dónde viven tus datos",
        "body": """• **Fotos y vídeos:** van directamente entre tu teléfono y tu propia Cloud, cifrados. No hay servidores de GallerySync.
• **Inicios de sesión y claves:** se guardan cifrados en tu teléfono. Las contraseñas se escriben en la página del proveedor, nunca en la app.
• **Los registros y ajustes de la app:** almacenamiento privado en tu teléfono.
• **No se recopila nada:** ni analítica, ni informes de fallos, ni anuncios.

Detalles: **Política de privacidad** y **Eliminar datos de la cuenta** en Settings.""",
        "en_hash": "989af3b026b90273",
    },

    "numbers-explained": {
        "title": "Por qué las cifras pueden diferir de las de tu app de galería",
        "body": """GallerySync cuenta solo los archivos que hay ahora en el teléfono, en las carpetas que elegiste, no los de la papelera. Tu galería puede contar de otra manera los elementos que solo están en la nube o agrupar las carpetas de otra forma. Ambas tienen razón; cuentan cosas distintas.""",
        "en_hash": "54fab5cd25e07a35",
    },

}
