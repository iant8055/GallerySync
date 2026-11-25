TRANSLATIONS = {
    "_chapter": {
        "title": 'Perguntas e problemas',
        "intro": 'Perguntas comuns e o que costuma estar acontecendo.',
        "en_hash": "01001cde06eb2f03",
    },
    'faq-sync-now-greyed': {
        "title": 'Back Up Now está em cinza',
        "body": """Não há nada para enviar ou otimizar: tudo já foi enviado, ou nenhum álbum está em Backup, Optimize ou Archive. Confira os totais de modo no cabeçalho.""",
        "en_hash": "946c334e3023479a",
    },
    'faq-albums-off-after-backup': {
        "title": 'Meus álbuns dizem Off depois do primeiro backup',
        "body": """Isso está correto. O primeiro backup envia tudo sem olhar o modo, e a configuração nunca define modos. Escolha um modo para cada álbum na aba Albums.""",
        "en_hash": "39db6d0a503fb274",
    },
    'faq-not-checked-yet': {
        "title": 'Um álbum mostra sent to sua Cloud, não verified',
        "body": """A Cloud ainda não foi consultada, ou a última checagem não conseguiu alcançá-la. Espere um momento ou toque em **Rescan**. Se a linha mostrar **Could not reach <Cloud>**, confira se está online e conectado, e depois toque em **Rescan**.

O Google Photos não pode ser consultado, então seus álbuns sempre mostram **N sent to Google Photos**. Veja [[albums-list]].""",
        "en_hash": "4afbe8f4cd09a04f",
    },
    'faq-onedrive-full': {
        "title": 'O backup parou porque a minha Cloud está cheia',
        "body": """**stopped: your Cloud is full.** Nada se perde. Libere espaço ou adicione armazenamento e depois pressione **Back Up Now**.""",
        "en_hash": "1a41d4b658e94c8d",
    },
    'faq-space-not-freed': {
        "title": 'Arquivei um álbum, mas meu telefone não ganhou espaço',
        "body": """Os arquivos arquivados ficam na lixeira do telefone, ainda ocupando espaço. Esvazie a lixeira quando tiver certeza de que eles estão seguros na sua Cloud. Veja [[where-deleted-files-go]].""",
        "en_hash": "2a38df823e61c58d",
    },
    'faq-album-vanished': {
        "title": 'Um álbum sumiu da minha galeria',
        "body": """Se ele estava em **Archive**, os arquivos dele estão na lixeira do telefone. Restaure-os da lixeira ou use a aba **Restore**. Ele volta como um álbum novo em Off.""",
        "en_hash": "94390b0205e85c29",
    },
    'faq-photo-looks-small': {
        "title": 'Uma foto parece menor ou de qualidade pior que antes',
        "body": """Ela foi otimizada (procure o pequeno selo de nuvem). O original está na sua Cloud: use a aba **Restore**.""",
        "en_hash": "009dd053a72c34e0",
    },
    'faq-restore-empty': {
        "title": 'A aba Restore diz que não há nada para trazer de volta',
        "body": """Nada foi otimizado nem arquivado e (no OneDrive) todas as fotos das suas pastas de backup já estão no telefone. Isso está correto.""",
        "en_hash": "e91f44f270b19e04",
    },
    'faq-backup-stops-when-closed': {
        "title": 'Os backups parecem parar quando fecho o app',
        "body": """Voltar ou Home não tem problema. Fechar o app deslizando-o para fora dos apps recentes pode parar o trabalho em segundo plano: abra o app de novo e ele continua sem enviar nada duas vezes.""",
        "en_hash": "266c98c91b9f897f",
    },
    'faq-secure-folder': {
        "title": "O GallerySync está na Secure Folder da Samsung",
        "body": """A cópia do GallerySync dentro da Secure Folder é um app separado, com configurações, logins e fotos próprios. Com o ajuste de bateria padrão da Samsung, ele só envia enquanto está na tela — ao mudar de app, o envio pausa e continua quando você abre o app de novo.

No telefone, abra **Settings → Apps → GallerySync → Battery** e escolha **Unrestricted**. Esse único ajuste vale para as duas cópias, e a da Secure Folder passa a enviar em segundo plano, como a cópia principal.

Mande a cópia da Secure Folder para uma pasta própria no OneDrive, para que as duas cópias não enxerguem os arquivos uma da outra. Consulte [[settings-destination]].""",
        "en_hash": "b20664245b085caf",
    },
    'faq-wrong-account': {
        "title": 'Entrei em uma Cloud com a conta errada',
        "body": """**Settings → Backup → Sair** ao lado dessa Cloud, escolha **Deixar lá** e depois entre com a conta certa. Os arquivos já enviados ficam na primeira conta. Veja [[settings-account]].""",
        "en_hash": "a033d25dffca0535",
    },
    'faq-folder-waiting': {
        "title": 'Saí de uma Cloud e as pastas dela pararam de enviar',
        "body": """Isso é esperado. As pastas esperam, seguras no seu telefone, até você entrar de novo. Para enviá-las para outro lugar, mude **Where each folder goes**. Veja [[settings-destination]].""",
        "en_hash": "286529a95f8327e7",
    },
    'faq-keys-rejected': {
        "title": 'O IDrive e2 ou o Backblaze B2 diz que minhas chaves foram rejeitadas',
        "body": """Em geral é um erro de digitação. Confira o endpoint (sem o https://), a região, o bucket e as duas chaves; toque em **Mostrar** na chave secreta. Cuidado com **O** e zero, **I** e **l**, **J** e **j**. Colar evita isso. Veja [[cloud-key-backblaze]] e [[cloud-key-idrive]].""",
        "en_hash": "e0b15236b548a066",
    },
    'faq-could-not-check': {
        "title": 'O Archive diz que não conseguiu verificar a minha Cloud',
        "body": """A Cloud não respondeu, então nada foi removido. Confira se você está online e conectado e depois pressione **Check these files** de novo.""",
        "en_hash": "96fcc47fc7eaf6d4",
    },
    'faq-reconnect-cloud': {
        "title": 'O Restore pede para reconectar essa Cloud',
        "body": """A Cloud recusou deixar o arquivo ser lido. Entre de novo em Settings. No Dropbox, permita a leitura de arquivos; no IDrive e2 e no Backblaze B2, use uma chave que possa ler o bucket. O arquivo do seu telefone não foi alterado.""",
        "en_hash": "23bf7817d447a418",
    },
    'albums-permission': {
        "title": 'O GallerySync não consegue ver as minhas fotos',
        "body": """Uma mensagem no alto da aba Albums significa que o GallerySync não tem acesso total às suas fotos:
• **GallerySync needs access to your photos:** nenhum acesso foi dado, ou ele foi retirado.
• **Only some photos are shared:** o acesso foi limitado com **Selecionar fotos**, então os backups ficam incompletos.

Pressione **Grant access** e escolha **Permitir tudo**. Se nenhum pedido aparecer: **Configurações → Apps → GallerySync → Permissões → Fotos e vídeos** no telefone.""",
        "en_hash": "d1499b80f267c6f8",
    },
    'contact-and-privacy': {
        "title": 'Contato, privacidade e apagar os seus dados',
        "body": """No fim de Settings:
• **Contato:** o endereço, com um botão de copiar.
• **Política de privacidade:** seus arquivos vão só para a sua própria Cloud; nada é coletado.
• **Excluir dados da conta:** como sair, remover o acesso e limpar os dados. Os arquivos da sua Cloud ficam.

Para relatar um problema, use **Relatar um erro** logo acima deles.""",
        "en_hash": "3e29c01351fc7319",
    },
}
