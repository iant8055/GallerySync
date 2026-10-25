TRANSLATIONS = {
    "_chapter": {
        "title": "Configuración inicial",
        "intro": "Una configuración guiada se ejecuta la primera vez que abres GallerySync.",
        "en_hash": "a04b336dd67e7d4e",
    },
    "setup-overview": {
        "title": "Cómo funciona la configuración",
        "body": """**Siguiente** avanza, **Atrás** vuelve. Una pantalla te deja continuar solo cuando tiene lo que necesita.

## Los pasos
1. Bienvenida, un breve recorrido y lo que se va a configurar. Consulta [[setup-welcome-tour]].
2. Elige tus Nubes e inicia sesión. Consulta [[setup-cloud]].
3. Permite el acceso a tus fotos y luego elige carpetas. Consulta [[setup-choose-folders]].
4. Elige un Plan de copia.
5. Configuración de optimización (solo los planes 2 y 3).
6. Cuándo empezar.
7. La primera copia: revisar la Nube, subir y luego optimizar si tu plan lo incluye.

## Conviene saber
• La primera copia puede tardar horas. Continúa aunque salgas de la app.
• La app se abre en la pantalla de progreso hasta que termina la primera copia.
• Las respuestas de la configuración se aplican solo a la primera copia. Consulta [[setup-and-settings]].""",
        "en_hash": "a398aa1dfcdb2e54",
    },

    "setup-welcome-tour": {
        "title": "Bienvenida, el recorrido y lo que se va a configurar",
        "body": """• **Bienvenida:** toca la imagen (**Toca para continuar**), elige tu idioma en la ventana emergente y pulsa **Aceptar**. Puedes cambiarlo más tarde en Settings.
• **El recorrido:** cinco pantallas, una por cada pestaña y una para Ayuda.
• **Lo que vamos a configurar:** inicia sesión en tu Nube, permite el acceso a las fotos, elige carpetas y permite cambios en esas carpetas.

El enlace de la parte inferior de esa pantalla abre esta guía de configuración. Es la única parte de la guía disponible durante la configuración.""",
        "en_hash": "17ccaeacdf9f0d32",
    },

    "setup-cloud": {
        "title": "Almacenamiento en la Nube: elegir servicios, iniciar sesión y elegir dónde van las copias",
        "body": """Selecciona las Nubes que usas y pulsa **Siguiente**. Al principio no hay ninguna seleccionada.

## Tu primera Nube es gratis
La primera Nube que seleccionas se convierte en tu Nube gratuita, que aparece como **main (free)** en Settings. Cada Nube adicional necesita **Pro**: gratis durante 30 días y después una compra única. No se cobra nada automáticamente. Si termina la prueba, las Nubes adicionales se pausan y tu Nube gratuita sigue funcionando.

Inicia la prueba con **Start 30-day free trial** en Settings, en Backup; aparece cuando hay una segunda Nube conectada. **Unlock Pro** la compra.

## Qué puede hacer cada Nube
• **OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 y Backblaze B2:** Backup, Sync, Archive y Restore.
• **Google Photos:** solo Backup.

## Una nota sobre duplicados
OneDrive es la única Nube cuyo contenido se comprueba antes de enviar nada. Otras Nubes pueden recibir una segunda copia de un archivo que ya está allí.

## Iniciar sesión
Cada Nube tiene su propia tarjeta numerada **Conectar**. **Siguiente** espera hasta que todas estén conectadas.
• **OneDrive, Google Photos, Google Drive, Dropbox, pCloud:** inicia sesión en la página del propio proveedor. Tu contraseña nunca se introduce en GallerySync. En Dropbox, permite lectura y escritura, o Restore no funcionará.
• **IDrive e2, Backblaze B2:** introduce las claves de su consola. Consulta [[cloud-key-backblaze]] y [[cloud-key-idrive]].

## Carpeta de copia
Cuando una Nube está conectada, su tarjeta muestra su **Carpeta de copia**, con **Cambiar** para elegir otra (no en Google Photos, que no tiene carpetas). OneDrive empieza en **Samsung Gallery / DCIM**, la carpeta que usa la propia sincronización de OneDrive de Samsung, así que las fotos ya sincronizadas allí no se envían dos veces. Las demás Nubes empiezan en **GallerySync**.{{full: Consulta [[dialog-destination]].}}""",
        "en_hash": "a90bbd0e1eecaa86",
    },

    "cloud-key-backblaze": {
        "title": "Conectar Backblaze B2: las claves y dónde encontrarlas",
        "body": """GallerySync solo escribe dentro de su carpeta de copia en tu bucket (**GallerySync** salvo que elijas otra), así que puedes usar un bucket que ya tengas sin riesgo.

## En Backblaze
1. **Un bucket privado:** usa uno que ya tengas, o **Buckets → Create a Bucket**.
2. **Su endpoint:** aparece en los detalles del bucket, por ejemplo **s3.us-east-005.backblazeb2.com**.
3. **Una clave de aplicación:** **Application Keys → Add a New Application Key**, solo para ese bucket, con **Read and Write**. No la Master key.
4. **Copia las dos mitades:** el **keyID** y la **applicationKey**. La applicationKey se muestra una sola vez.

## En GallerySync
• **Endpoint:** sin https://.
• **Región:** déjala en blanco.
• **Nombre del bucket:** exactamente como en Backblaze.
• **ID de clave de acceso:** el keyID (empieza por 005).
• **Clave de acceso secreta:** la applicationKey. Pulsa **Mostrar** para comprobarla.

## Si se rechazan las claves
El mensaje indica qué revisar. Casi siempre es una errata: **O** frente a cero, **I** frente a **l**, **J** frente a **j**. Pegar evita este problema.""",
        "en_hash": "5b41f1e8fffbcd4e",
    },

    "cloud-key-idrive": {
        "title": "Conectar IDrive e2: las claves y dónde encontrarlas",
        "body": """GallerySync solo escribe dentro de su carpeta de copia en tu bucket (**GallerySync** salvo que elijas otra), así que puedes usar un bucket que ya tengas sin riesgo.

## En IDrive e2
1. **Un bucket privado.**
2. **El endpoint de la región**, por ejemplo **s3.us-west-1.idrivee2.com**. No la dirección con el nombre del bucket delante.
3. **El nombre de la región**, por ejemplo **us-west-1**.
4. **Una clave de acceso:** un **access key ID** y una **secret access key**. El secreto se muestra una sola vez.

## En GallerySync
Introduce el **Endpoint** (sin https://), la **Región**, el **Nombre del bucket**, el **ID de clave de acceso** y la **Clave de acceso secreta**. Pulsa **Mostrar** para comprobar el secreto.

## Si se rechazan las claves
El mensaje indica qué revisar. Casi siempre es una errata: **O** frente a cero, **I** frente a **l**. Pegar evita este problema.""",
        "en_hash": "18ca3e144c0c9aa9",
    },

    "setup-search-permission": {
        "title": "Deja que GallerySync busque en este teléfono",
        "body": """**Dar permiso de búsqueda** abre la pantalla de permisos de Android. Aquí permites que GallerySync vea las fotos y vídeos de tu teléfono.

## Conviene saber
Elige **Permitir todo**. Si eliges **Seleccionar fotos**, solo se podrá hacer copia de esas fotos.""",
        "en_hash": "9dfa956289f7ed75",
    },

    "setup-choose-folders": {
        "title": "Elige las carpetas que copiar",
        "body": """Selecciona las carpetas de las que hacer copia y pulsa **Siguiente**. La mayoría de las fotos están en **DCIM**.

## A qué Nube va cada carpeta
Con más de una Nube, cada carpeta muestra **Va a**. Elige una Nube para cada carpeta seleccionada. Los archivos ya enviados nunca se mueven; solo los archivos nuevos siguen un cambio.{{full: Consulta [[settings-destination]].}}

## Android vuelve a preguntar por cada carpeta
Android muestra su propia pantalla de permiso de carpeta. Si algo sale mal:
• **No se dio acceso:** **Reintentar** o **Omitir** la carpeta.
• **Se eligió una carpeta dentro de ella:** **Elegir todo** para corregirlo, o **Quedarme solo con** la opción más pequeña.
• **Se eligió una carpeta distinta:** reinténtalo u omítela.

Nada fuera de las carpetas que elegiste se analiza ni se modifica.""",
        "en_hash": "409ac88ad1359229",
    },

    "setup-backup-plan": {
        "title": "Elige tu Plan de copia",
        "body": """Qué hacer con las fotos y vídeos que ya están en tu teléfono. **Ningún plan elimina nada.**

• **1. Hacer copia de todo lo que aún no esté en la Nube.** Nada cambia en el teléfono. Seleccionado al llegar.
• **2. Hacer copia y luego optimizar todos los archivos del teléfono.** El máximo ahorro de espacio.
• **3. Hacer copia y luego optimizar solo los archivos que envió esta copia.**
• **4. Revisar la Nube pero no enviar nada.** Después eliges el modo de cada álbum en la pestaña Albums.

## Conviene saber
Esta primera copia es única y se hace sin tener en cuenta el modo. Después, todos los álbumes quedan en **Off**, lo cual es correcto: a partir de ahí eliges tú el modo de cada álbum en la pestaña Albums.{{full: Consulta [[faq-albums-off-after-backup]].}}""",
        "en_hash": "80f55e21398db3f8",
    },

    "setup-optimise": {
        "title": "Configuración de optimización durante la instalación",
        "body": """Solo para los planes 2 y 3.

• **Optimizar fotos** y **Optimizar vídeo:** activado o desactivado.
• **Nivel de compresión de vídeo:** **Alta** (480p, la más pequeña), **Media** (720p) o **Baja** (1080p).
• Cada una muestra un ahorro estimado, con una línea de **Ahorro total estimado**. Los resultados reales varían.""",
        "en_hash": "d3ff8b60c0328479",
    },

    "setup-ready": {
        "title": "Listo para la copia",
        "body": """Muestra cuánto se copiará y, si se optimiza, cuánto espacio ahorrarás.

• **Ahora mismo:** empieza de inmediato.
• **Retraso:** **1 h, 2 h, 4 h, 8 h, 12 h** o **24 h**.

## Conviene saber
• Una copia con retraso empieza solo mientras el teléfono está enchufado, y puede empezar un poco tarde.
• La app se queda en la pantalla de progreso hasta que termina la primera copia.""",
        "en_hash": "90436741649f85c6",
    },

    "setup-progress": {
        "title": "Progreso de la copia",
        "body": """## Esperando para empezar
Una cuenta atrás corre hasta que empieza la copia, incluso con la app cerrada, siempre que el teléfono esté enchufado. **SINCRONIZAR AHORA** la inicia de inmediato.

## Paso 1: Revisando tu Nube
Cada archivo se compara con lo que ya tiene tu Nube, por nombre y tamaño, con un recuento en curso. No se envía nada. Normalmente tarda uno o dos minutos.

## Paso 2: Subida
El anillo muestra el progreso total. Debajo: la Nube a la que se envía y su recuento. Las demás Nubes en espera aparecen como **Pendientes en** esa Nube.

## Paso 3: Optimizando
**Optimizando fotos**, luego **Optimizando vídeo**, si tu plan los incluye.

## Botones
• **Cerrar:** cierra la app; la copia continúa.
• **Finalizar:** aparece cuando todo está hecho, con un resumen de lo que se revisó, lo que ya estaba en la Nube y lo que se envió.
• **Cancelar:** pregunta **¿Detener la copia de seguridad?** Los archivos ya enviados se quedan enviados; no se elimina nada.""",
        "en_hash": "625986b0864a86c8",
    },

    "setup-and-settings": {
        "title": "La configuración y Settings son independientes",
        "body": """La configuración y Settings guardan cada una sus propias respuestas. La configuración nunca lee de Settings, y Settings nunca lee de la configuración. La lista de carpetas de la configuración empieza vacía aunque Settings ya enumere carpetas, y las respuestas de la configuración no aparecen en Settings.

La configuración nunca establece el modo de un álbum. Solo lo haces tú, en la pestaña Albums.""",
        "en_hash": "06ce026c4915e1fd",
    },

}
