TRANSLATIONS = {
    "_chapter": {
        "title": "La pestaña Settings",
        "intro": "Una página con desplazamiento en bandas verdes: General, Backup, Albums, Sync, Sync de la cámara, Restore, Archive y Ayuda y comentarios. Cada banda tiene un (?).",
        "en_hash": "fdc210cbde6172d8",
    },
    "settings-overview": {
        "title": "Cómo está organizada la pestaña Settings",
        "body": """• Los cambios se aplican al instante. No hay botón de guardar.
• Nada de lo que hay aquí quita un archivo de tu teléfono. Solo Archive lo hace.
• Settings y la configuración inicial son independientes. Consulta [[setup-and-settings]].""",
        "en_hash": "523b8f790e8db56b",
    },

    "settings-section-general": {
        "title": "General",
        "body": """La **Guía de uso**, **Apariencia**, **Fuente** y **Usar datos móviles**. **Idioma** es la última línea de la página.""",
        "en_hash": "7c55bf50b318edb4",
    },

    "settings-how-to-guide-card": {
        "title": "La tarjeta Guía de uso",
        "body": """Abre esta guía. **Leer más en la Guía de uso** en cualquier **(?)** la abre en ese tema. Necesita conexión a internet; si no carga, usa **Abrir en el navegador**.""",
        "en_hash": "a7dec55c7b2218a4",
    },

    "settings-language": {
        "title": "Idioma",
        "body": """El idioma que elegiste en la pantalla de bienvenida. Púlsalo para cambiarlo. La Guía de uso sigue esta elección; las pantallas de la app están por ahora en inglés.""",
        "en_hash": "e1597813fc836e98",
    },

    "settings-appearance": {
        "title": "Apariencia",
        "body": """• **Sistema** (predeterminado): sigue el ajuste claro u oscuro de tu teléfono.
• **Claro** u **Oscuro**: siempre ese aspecto.

La guía y las páginas de política también siguen este ajuste.

## Fuente
• **De la app** (predeterminado): la app muestra el texto con su propia fuente para que se vea igual en todos los teléfonos.
• **Del teléfono:** usa el estilo de fuente propio del teléfono.

El tamaño del texto sigue el ajuste del teléfono en ambos casos.""",
        "en_hash": "a616649556d751e6",
    },

    "settings-mobile-data": {
        "title": "Usar datos móviles",
        "body": """• **Off** (predeterminado): **Solo wifi.**
• **On:** **Sincroniza con wifi y con datos móviles.**

Afecta a las copias automáticas y a **Sync now**. Déjalo desactivado salvo que tu plan de datos sea ilimitado.""",
        "en_hash": "e8860c60b1961fee",
    },

    "settings-section-backup": {
        "title": "Backup",
        "body": """• **Nubes:** conecta o cierra sesión en cada Cloud. Consulta [[settings-account]].
• **Adónde va cada carpeta:** a qué Cloud envía cada carpeta. Consulta [[settings-destination]].

Una Cloud está marcada como **principal (gratis)**: la primera que seleccionaste. Siempre es gratis. Las demás Clouds necesitan **Pro**: gratis durante 30 días (**Empezar prueba gratuita de 30 días**) y después una compra única (**Desbloquear Pro**). No se cobra nada automáticamente.""",
        "en_hash": "0db12d3ebcb633de",
    },

    "settings-account": {
        "title": "Tus Clouds y Cerrar sesión",
        "body": """Cada Cloud muestra su cuenta (o **No conectado**) con **Cerrar sesión**, **Conectar** o **Introducir claves**.

## Carpeta de copia
Bajo cada Cloud conectada (excepto Google Photos): la carpeta a la que van sus copias, con **Cambiar**. Consulta [[dialog-destination]].

## Cerrar sesión
Primero aparece un aviso: las copias a esa Cloud se detienen, no se elimina nada. Si esa Cloud tiene archivos que no están a tamaño completo en el teléfono (optimizados o archivados), pregunta:
• **Restaurarlos primero en el teléfono:** los recupera todos y luego cierra la sesión.
• **Dejarlos allí:** cierra la sesión.
• **Cancelar:** mantiene la sesión iniciada.

Las carpetas siguen emparejadas con esa Cloud y esperan hasta que vuelvas a iniciar sesión. Para enviarlas a otro sitio, cambia **Adónde va cada carpeta**.""",
        "en_hash": "788b2237bf1a173f",
    },

    "settings-destination": {
        "title": "Adónde va cada carpeta",
        "body": """Cada carpeta de primer nivel (DCIM, Pictures, Movies), la Cloud a la que va y en qué lugar de esa Cloud.

• Pulsa la Cloud para cambiarla. Solo se puede cambiar con más de una Cloud y con Pro o la prueba.
• Solo los archivos nuevos siguen un cambio. Los archivos ya enviados se quedan donde están.
• Una carpeta emparejada con una Cloud adicional sin Pro no envía nada, y lo indica.""",
        "en_hash": "22adac22e50fbdf0",
    },

    "dialog-destination": {
        "title": "Dónde van las copias nuevas (el cuadro de diálogo Cambiar)",
        "body": """Se abre con **Cambiar** junto a la **Carpeta de copia** de una Cloud, en Settings o en su tarjeta durante la configuración.

• Escribe la ruta de la carpeta o, en OneDrive, usa **Explorar…**.
• **Volver al predeterminado:** vuelve a **Samsung Gallery/DCIM** (OneDrive) o a **GallerySync** (otras Clouds).
• **Usar esta carpeta** guarda; **Cancelar** no cambia nada.

Solo los archivos nuevos van a la carpeta nueva. Los archivos que ya tienen copia se quedan donde están. OneDrive sigue comprobando las carpetas anteriores, así que no se envía nada dos veces.""",
        "en_hash": "910e0378a5a8b208",
    },

    "dialog-folder-picker": {
        "title": "Elige una carpeta (el selector de OneDrive)",
        "body": """Se abre con **Explorar…** en OneDrive. Lista tus carpetas de OneDrive: pulsa para abrir, **Subir una carpeta** para volver, **Usar esta carpeta** para elegir y **Carpeta nueva** para crear una.""",
        "en_hash": "f45e3349352eea44",
    },

    "settings-section-albums": {
        "title": "Albums",
        "body": """Qué carpetas vigila GallerySync, y qué ocurre con las copias de la Cloud cuando borras en el teléfono. Los álbumes nuevos siempre empiezan en **Off**.""",
        "en_hash": "dad7459947f2d8e1",
    },

    "settings-folders": {
        "title": "Carpetas para copiar",
        "body": """Las carpetas en las que GallerySync puede mirar.

• **Añadir una carpeta:** abre el selector de carpetas de Android.
• **Quitar:** selecciona carpetas, pulsa **Quitar** y confirma.

Nada fuera de estas carpetas se analiza ni se modifica. Quitar una carpeta no pierde nada: vuelve a añadirla y todo regresa.""",
        "en_hash": "3789c04bb52baef5",
    },

    "dialog-remove-folder": {
        "title": "¿Dejar de vigilar esta carpeta? (la confirmación)",
        "body": """**Quitar** deja de vigilar las carpetas seleccionadas; **Cancelar** no cambia nada.

Nada de lo que ya tiene copia se pierde. Al volver a añadir la carpeta se recuperan sus álbumes y su historial, sin enviar nada dos veces.""",
        "en_hash": "5b84d0c4a6ec8a9a",
    },

    "settings-deletion": {
        "title": "Cuando eliminas una foto o un vídeo de este teléfono",
        "body": """• **Dejar la copia en la Nube** (predeterminado): nunca se quita nada de tu Cloud.
• **Preguntarme por la copia en la Nube:** cuando los archivos salen de tu teléfono, una ventana pregunta qué hacer con sus copias en la Cloud. Si las notificaciones están permitidas, también se te avisa poco después de borrar. No se quita nada sin que tú lo decidas. Consulta [[deleted-files-window]].

Por ahora solo con OneDrive. Las copias en otras Clouds nunca se quitan.""",
        "en_hash": "5fce34ecb857a3f6",
    },

    "deleted-files-window": {
        "title": "Files deleted from your phone (la ventana que se abre con la app)",
        "body": """Se abre antes de la pestaña Albums cuando han salido archivos de tu teléfono, con **Ask**. Al principio no hay nada seleccionado.

## Ventana 1: hay una copia en OneDrive
Desliza hacia la derecha (o pulsa) para seleccionar los archivos cuya copia en OneDrive debe quitarse.
• **Remove N from OneDrive:** te pide confirmar y luego mueve esas copias a la papelera de reciclaje de OneDrive. Los archivos no seleccionados se quedan en OneDrive. Consulta [[dialog-remove-from-onedrive]].
• **Keep all in OneDrive.**
• **Decide later.**

## Ventana 2: no se encontró ninguna copia
Estos archivos siguen en la papelera de tu teléfono.
• **Back up N to OneDrive:** envía desde la papelera los archivos seleccionados. No se elimina nada.
• **Leave all in the trash.**
• **Decide later.**

## Conviene saber
• Los archivos sin decidir siguen en la lista hasta que decidas.
• No aparecen: los archivos que quitó Archive, las fotos editadas en el sitio y los archivos borrados definitivamente sin copia en la Cloud.
• Si parece que más de la mitad de tus archivos con copia desaparece de golpe, no se ofrece nada; se trata como un análisis defectuoso y no como borrados reales.
• Atrás hace lo mismo que **Decide later**.""",
        "en_hash": "4cf138e07d6f0844",
    },

    "dialog-remove-from-onedrive": {
        "title": "Remove files from OneDrive? (la confirmación)",
        "body": """Muestra cuántos archivos son y su tamaño. Van a la papelera de reciclaje de OneDrive, que GallerySync nunca vacía.

**Remove from OneDrive** sigue adelante; **Cancel** no decide nada. Cualquier archivo que haya vuelto al teléfono se deja como está.""",
        "en_hash": "a5707f1ea8aad403",
    },

    "settings-section-sync": {
        "title": "Sync",
        "body": """Cómo se optimizan las fotos y los vídeos de los álbumes **Sync**. Los archivos cambian solo después de que la Cloud los haya verificado. Funciona con OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 y Backblaze B2.""",
        "en_hash": "1147f4e959ac06bc",
    },

    "settings-optimise-photos": {
        "title": "Optimizar fotos",
        "body": """**Off** de forma predeterminada. Cuando está activado, las fotos de los álbumes Sync se reemplazan por copias más pequeñas: unos 2048 píxeles en el lado largo, aproximadamente una décima parte del tamaño. Siguen en tu galería con una pequeña insignia de nube. Los originales se quedan en la Cloud.

Las fotos que ya son lo bastante pequeñas, y los archivos en **Keep at full size**, se dejan como están. **Modo** define cuándo se ejecuta. Consulta [[settings-optimise-mode]].""",
        "en_hash": "dee027d401742ca5",
    },

    "settings-optimise-video": {
        "title": "Optimizar vídeo",
        "body": """**Off** de forma predeterminada. Cuando está activado, muestra **Modo**, **Con más de** y **Compresión**.

Los vídeos de los álbumes Sync se recodifican a un tamaño menor. Nunca se corta nada. Los originales se quedan en la Cloud.

## Qué vídeos
En un álbum Sync, verified in the Cloud, más antiguos que tu ajuste, en una carpeta a la que diste acceso y aún no optimizados.

## Cuándo
• **Automático:** después de las copias, solo mientras se carga el teléfono.
• **Manual:** cuando pulsas **Sync now**.""",
        "en_hash": "09cf14026b288ef6",
    },

    "settings-optimise-mode": {
        "title": "Modo: Automático o Manual",
        "body": """• **Automático** (predeterminado): los archivos de los álbumes Sync se optimizan en cuanto la Cloud los confirma. El vídeo espera al cargador.
• **Manual:** no ocurre nada hasta que pulsas **Sync now** en la pestaña Albums.

Las fotos fuera de las carpetas a las que diste acceso necesitan la confirmación de Android, así que esperan hasta que abras la app o pulses **Sync now**.""",
        "en_hash": "2d55959b2e538398",
    },

    "settings-video-age": {
        "title": "Con más de (vídeo)",
        "body": """Qué antigüedad debe tener un vídeo antes de optimizarse: **En seguida**, **1 hora**, **12 horas**, **1 día** o **1 semana**. La copia no se retrasa.

**En seguida** incluye los clips grabados hoy; si editas uno, la edición parte de la copia más pequeña hasta que lo restaures.""",
        "en_hash": "b69444389e56afe8",
    },

    "settings-video-quality": {
        "title": "Compresión (vídeo)",
        "body": """• **Compresión alta — 480p:** los archivos más pequeños, se ahorra alrededor del 85 %.
• **Compresión media — 720p:** alrededor del 75 %.
• **Compresión baja — 1080p:** alrededor del 50 %.

Cifras típicas; los resultados varían.

## Después de aumentar la compresión
La app pregunta **¿Volver a optimizar los vídeos existentes?** **Sí** permite volver a intentarlo con los vídeos que antes eran demasiado pequeños para reducirse. **No** los deja como están. Los vídeos ya optimizados no se tocan.""",
        "en_hash": "b68338f9be222883",
    },

    "settings-section-restore": {
        "title": "Restore (Settings)",
        "body": """Un interruptor para la pestaña Restore: **Mostrar carpetas vacías**.""",
        "en_hash": "58571cb29a96c3ed",
    },

    "settings-show-empty-folders": {
        "title": "Mostrar carpetas vacías",
        "body": """**Off** de forma predeterminada. Cuando está activado, la pestaña Restore lista todas las carpetas de OneDrive, incluidas las que no tienen nada que recuperar.""",
        "en_hash": "44bada9612e80a0d",
    },

    "settings-section-camera": {
        "title": "Sync de la cámara (Settings)",
        "body": """**Ajustes especiales para la cámara**, desactivado de forma predeterminada. Cuando está activado:
• **Carpeta de la cámara:** la carpeta donde guarda tu cámara. Elígela de la lista, o **Otra…**.
• **Select Photos/Videos older than**, **Photos**, **Videos:** valores predeterminados de los controles de optimización de esa carpeta ([[album-camera-optimise]]).
• **Compresión de vídeo:** para los vídeos que se optimizan allí.

La carpeta de la cámara no tiene modo **Sync**, y no se optimiza nada hasta que pulsas **Sync now** en ella. Los interruptores de optimización de arriba no la afectan.""",
        "en_hash": "d596a9c99a8de79d",
    },

    "settings-section-archive": {
        "title": "Archive (Settings)",
        "body": """• **Only show files older than:** dónde empieza el filtro de la pestaña Archive. Consulta [[settings-archive-default-age]].
• **Avisar cuando haya archivos listos para Archive:** desactivado de forma predeterminada. Consulta [[settings-archive-notify]].""",
        "en_hash": "7b4561ddc010537d",
    },

    "settings-archive-default-age": {
        "title": "Only show files older than (Settings)",
        "body": """Dónde empieza el filtro de antigüedad de la pestaña Archive: **1 hour**, **1 day**, **1 week**, **1 month**, **1 year** o **All** (predeterminado). Cambiarlo en la pestaña Archive dura solo esa visita. Consulta [[archive-age-filter]].""",
        "en_hash": "215ff23fa51a5fca",
    },

    "settings-archive-notify": {
        "title": "Avisar cuando haya archivos listos para Archive",
        "body": """**Off** de forma predeterminada. Cuando está activado, se te avisa una vez por cada lote nuevo de archivos listos para archivar. Solo archivos de OneDrive.

La pestaña Albums y el aviso al salir también lo muestran, así que nunca dependes solo de las notificaciones. Si las notificaciones están bloqueadas, el interruptor lo indica.""",
        "en_hash": "a0b5e2290fe1eaae",
    },

    "settings-about-cards": {
        "title": "Ayuda y comentarios y las tarjetas de enlaces de abajo",
        "body": """## Ayuda y comentarios
• **Valóranos en Play Store:** abre la ficha de GallerySync.
• **Informar de un error:** dónde escribir, y **Copiar detalles** para la versión de tu app, el modelo del teléfono y la versión de Android.

## Al final
• **Política de privacidad.**
• **Eliminar datos de la cuenta:** cómo cerrar sesión, quitar el acceso de la app y borrar sus datos.
• **Contacto:** la dirección, con **Copiar dirección**.

Las páginas se cargan desde internet. Si una no carga, usa **Abrir en el navegador**.""",
        "en_hash": "8a454188917d719a",
    },

}
