TRANSLATIONS = {
    "_chapter": {
        "title": "Preguntas y problemas",
        "intro": "Preguntas habituales y qué suele estar pasando.",
        "en_hash": "01001cde06eb2f03",
    },
    "faq-sync-now-greyed": {
        "title": "Sync now está en gris",
        "body": """No hay nada que enviar ni optimizar: todo está ya enviado, o ningún álbum está en Backup, Sync o Archive. Mira los totales de modo en la cabecera.""",
        "en_hash": "d76ec512e56c7203",
    },

    "faq-albums-off-after-backup": {
        "title": "Mis álbumes dicen Off después de la primera copia",
        "body": """Es correcto. La primera copia envía todo sin tener en cuenta el modo, y la configuración nunca establece modos. Elige un modo para cada álbum en la pestaña Albums.""",
        "en_hash": "39db6d0a503fb274",
    },

    "faq-not-checked-yet": {
        "title": "Un álbum dice Not checked against OneDrive yet",
        "body": """Aún no se ha consultado a OneDrive. Espera un momento o pulsa **Rescan**. **Could not reach OneDrive** significa que falló la conexión: comprueba que estás en línea y con la sesión iniciada, y luego pulsa **Rescan**.

Los álbumes de otras Clouds muestran en su lugar **sent to** esa Cloud. Consulta [[albums-list]].""",
        "en_hash": "52048b6e4b7bcf97",
    },

    "faq-onedrive-full": {
        "title": "La copia se detuvo porque mi Cloud está llena",
        "body": """**stopped: your Cloud is full.** No se pierde nada. Libera algo de espacio o amplía el almacenamiento, y pulsa **Sync now**.""",
        "en_hash": "70148dcb9cb4986c",
    },

    "faq-space-not-freed": {
        "title": "Archivé un álbum pero mi teléfono no tiene más espacio",
        "body": """Los archivos archivados están en la papelera del teléfono, y siguen ocupando espacio. Vacía la papelera cuando estés seguro de que están a salvo en tu Cloud. Consulta [[where-deleted-files-go]].""",
        "en_hash": "2a38df823e61c58d",
    },

    "faq-album-vanished": {
        "title": "Un álbum ha desaparecido de mi galería",
        "body": """Si estaba en **Archive**, sus archivos están en la papelera del teléfono. Restáuralos desde la papelera, o usa la pestaña **Restore**. Vuelve como un álbum nuevo en Off.""",
        "en_hash": "94390b0205e85c29",
    },

    "faq-photo-looks-small": {
        "title": "Una foto se ve más pequeña o de menor calidad que antes",
        "body": """Se ha optimizado (busca la pequeña insignia de nube). El original está en tu Cloud: usa la pestaña **Restore**.""",
        "en_hash": "4e1a6a3f710360db",
    },

    "faq-restore-empty": {
        "title": "La pestaña Restore dice que no hay nada que recuperar",
        "body": """No se ha optimizado ni archivado nada y (en OneDrive) todas las fotos de tus carpetas de copia ya están en el teléfono. Es correcto.""",
        "en_hash": "e0720600e98ca0e2",
    },

    "faq-backup-stops-when-closed": {
        "title": "Las copias parecen detenerse cuando cierro la app",
        "body": """Salir con Atrás o Inicio no pasa nada. Cerrar la app deslizándola desde las apps recientes puede detener el trabajo en segundo plano: abre la app de nuevo y continúa sin enviar nada dos veces.""",
        "en_hash": "266c98c91b9f897f",
    },

    "faq-wrong-account": {
        "title": "Inicié sesión en una Cloud con la cuenta equivocada",
        "body": """**Settings → Backup → Cerrar sesión** junto a esa Cloud, elige **Dejarlos allí** y luego inicia sesión con la cuenta correcta. Los archivos ya enviados se quedan en la primera cuenta. Consulta [[settings-account]].""",
        "en_hash": "a033d25dffca0535",
    },

    "faq-folder-waiting": {
        "title": "Cerré sesión en una Cloud y sus carpetas dejaron de enviar",
        "body": """Es lo esperado. Las carpetas esperan, a salvo en tu teléfono, hasta que vuelvas a iniciar sesión. Para enviarlas a otro sitio, cambia **Adónde va cada carpeta**. Consulta [[settings-destination]].""",
        "en_hash": "286529a95f8327e7",
    },

    "faq-keys-rejected": {
        "title": "IDrive e2 o Backblaze B2 dice que mis claves fueron rechazadas",
        "body": """Normalmente es una errata. Revisa el endpoint (sin https://), la región, el bucket y las dos claves; pulsa **Mostrar** en el secreto. Ojo con **O** frente a cero, **I** frente a **l**, **J** frente a **j**. Pegar evita este problema. Consulta [[cloud-key-backblaze]] y [[cloud-key-idrive]].""",
        "en_hash": "e0b15236b548a066",
    },

    "faq-could-not-check": {
        "title": "Archive dice que no pudo comprobar mi Cloud",
        "body": """La Cloud no respondió, así que no se quitó nada. Comprueba que estás en línea y con la sesión iniciada, y pulsa **Check these files** otra vez.""",
        "en_hash": "96fcc47fc7eaf6d4",
    },

    "faq-reconnect-cloud": {
        "title": "Restore dice que vuelva a conectar esa Cloud",
        "body": """La Cloud se negó a dejar leer el archivo. Vuelve a iniciar sesión en Settings. En Dropbox, permite leer archivos; en IDrive e2 y Backblaze B2, usa una clave que pueda leer el bucket. El archivo de tu teléfono no se modificó.""",
        "en_hash": "23bf7817d447a418",
    },

    "albums-permission": {
        "title": "GallerySync no puede ver mis fotos",
        "body": """Un mensaje en la parte superior de la pestaña Albums significa que GallerySync no tiene acceso completo a tus fotos:
• **GallerySync needs access to your photos:** no se dio acceso, o se retiró.
• **Only some photos are shared:** el acceso se limitó con **Seleccionar fotos**, así que las copias están incompletas.

Pulsa **Grant access** y elige **Permitir todo**. Si no aparece ningún aviso: **Ajustes → Aplicaciones → GallerySync → Permisos → Fotos y vídeos** del teléfono.""",
        "en_hash": "d1499b80f267c6f8",
    },

    "contact-and-privacy": {
        "title": "Contacto, privacidad y cómo borrar tus datos",
        "body": """En la parte inferior de Settings:
• **Contacto:** la dirección, con un botón de copiar.
• **Política de privacidad:** tus archivos van solo a tu propia Cloud; no se recopila nada.
• **Eliminar datos de la cuenta:** cómo cerrar sesión, quitar el acceso y borrar los datos. Los archivos de tu Cloud se quedan.

Para informar de un problema, usa **Informar de un error**, justo encima.""",
        "en_hash": "3e29c01351fc7319",
    },

}
