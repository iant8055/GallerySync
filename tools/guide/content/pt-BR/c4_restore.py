TRANSLATIONS = {
    "_chapter": {
        "title": 'A aba Restore',
        "intro": 'Traz fotos e vídeos de volta da sua Cloud. Ela só lê da Cloud; nada lá é alterado.',
        "en_hash": "e472722c1c5b626f",
    },
    'restore-overview': {
        "title": 'O que o Restore traz de volta',
        "body": """• **To restore:** um arquivo otimizado no telefone. Restaurar coloca o original em tamanho real de volta no lugar dele.
• **To download:** um arquivo que não está mais no telefone. Baixar coloca ele de volta no álbum dele.

Os arquivos restaurados recebem **Keep at full size**, para não serem otimizados nem arquivados de novo logo em seguida. Veja [[album-file-pin]].

## Quais Clouds
• **OneDrive:** todas as fotos e vídeos nas suas pastas de backup, não importa quem os colocou lá.
• **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2:** arquivos que o GallerySync enviou deste telefone.
• **Google Photos:** não é possível restaurar a partir dele.

## Para onde os arquivos vão
De volta para a pasta de onde vieram. Os arquivos soltos no topo de **DCIM**, **Pictures** ou **Movies** voltam para essa pasta. Se não for possível escrever em uma pasta, os arquivos vão para uma pasta em **DCIM** com o nome do álbum.

## Pode tentar sem medo
Um arquivo só é substituído depois que o download completo chega e o tamanho confere. Se algo falhar, o arquivo do telefone fica como estava.""",
        "en_hash": "1bc7e998be6fc106",
    },
    'restore-hero': {
        "title": 'Albums to Restore e Files in this album',
        "body": """• **Lista de álbuns:** **Albums to Restore**, com quantos álbuns têm algo para trazer de volta.
• **Dentro de um álbum:** seta de voltar, nome do álbum e **Files in this album** com a contagem e o que você selecionou.

Um traço significa que a lista ainda está carregando. **Checking your Cloud for more...** significa que o OneDrive está sendo lido. Uma mensagem em vermelho significa que não foi possível alcançar uma pasta da Cloud.

A lista do OneDrive fica guardada e é lida de novo depois de dez minutos ou quando você pressiona **Refresh**.""",
        "en_hash": "c073ea612bd19780",
    },
    'restore-message-line': {
        "title": 'A mensagem embaixo do número',
        "body": """• **Swipe right to select / left to deselect. Tap to open.** na lista de álbuns.
• **Swipe right to select / left to deselect → Then press Restore.** dentro de um álbum.
• **Nothing in your Cloud is missing from this phone, so there is nothing to bring back.** quando a lista está vazia.
• Depois de uma restauração, quantos arquivos voltaram e quantos foram deixados sem alteração.""",
        "en_hash": "5b14979898ba9b25",
    },
    'restore-selected-summary': {
        "title": 'N selected e quanto será recuperado',
        "body": """Os arquivos selecionados em todos os álbuns e o espaço extra que eles vão ocupar no seu telefone. Confira se você tem esse espaço livre.""",
        "en_hash": "91898ed468f32d87",
    },
    'restore-buttons': {
        "title": 'Refresh, Select all e Clear',
        "body": """• **Refresh** (lista de álbuns): lê a lista de novo, incluindo o OneDrive.
• **Select all** (dentro de um álbum): seleciona todos os arquivos dele.
• **Clear:** remove todas as seleções.

Ficam em cinza enquanto uma restauração está rodando.""",
        "en_hash": "69730fbd93d70feb",
    },
    'restore-folders-list': {
        "title": 'A lista de álbuns',
        "body": """Cada cartão de álbum mostra:
• Quantos arquivos podem voltar e o espaço de que precisam.
• Quantos estão **to restore** e **to download**.
• Quantos estão **already on this phone** (em cinza por dentro; veja [[restore-greyed-files]]).
• Quantos estão **selected here**, ou uma marca de verificação quando o álbum inteiro está selecionado.

**Toque** para abrir. **Deslize para a direita** para selecionar o álbum inteiro e **para a esquerda** para desmarcar.""",
        "en_hash": "54ceae2583fe13a0",
    },
    'restore-files-list': {
        "title": 'A lista de arquivos dentro de um álbum',
        "body": """Deslize para a direita (ou toque) para selecionar e para a esquerda para desmarcar. Cada arquivo mostra um destes:
• **now · full size:** uma cópia otimizada, mostrando o tamanho menor que ela tem no telefone e o tamanho completo do arquivo na Cloud.
• **Available for download:** não está no telefone.
• **Already on this phone:** em cinza; nada a fazer.

Enquanto trabalha: **Working...** com uma porcentagem. Depois **Restored to full size**, **Back on this phone** ou **Could not restore, your file is unchanged** com o motivo.""",
        "en_hash": "486e3275bedd2609",
    },
    'restore-greyed-files': {
        "title": 'Por que alguns arquivos estão em cinza?',
        "body": """Eles já estão no telefone em tamanho real (mesmo álbum, nome e tamanho), então não há nada a restaurar. São mostrados para que o álbum corresponda à sua Cloud.

Um arquivo com o mesmo nome, mas tamanho diferente, não é oferecido: pode ser uma edição, e o Restore nunca sobrescreve uma edição.""",
        "en_hash": "02372472f6d53985",
    },
    'restore-action-bar': {
        "title": 'Restore e Stop restoring',
        "body": """• **Restore:** traz de volta todos os arquivos selecionados, um de cada vez. Aparece quando algo está selecionado.
• **Stop restoring:** interrompe o lote. Os arquivos já restaurados continuam restaurados.

Uma barra de progresso mostra quantos foram restaurados. Precisa de conexão com a internet e de espaço livre suficiente.""",
        "en_hash": "52b0a1302c2c28cb",
    },
    'restore-edited': {
        "title": 'Uma foto ou vídeo que você editou',
        "body": """Se você editou uma cópia otimizada e salvou por cima, restaurar substituiria a sua edição, então o app pergunta: **This photo has been edited since it was backed up. Overwrite it with the original?**

• **Keep my edits:** deixa o arquivo como está.
• **Overwrite:** coloca o original de volta; a sua edição é substituída.

## Bom saber
A maioria dos editores salva um arquivo novo, que nunca é afetado. Um arquivo editado passa por backup como um arquivo novo com **edited** e a data no nome; o original na sua Cloud nunca é substituído.""",
        "en_hash": "6292714bbfa760d3",
    },
}
