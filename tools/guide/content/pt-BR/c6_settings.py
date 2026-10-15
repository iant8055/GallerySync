TRANSLATIONS = {
    "_chapter": {
        "title": 'A aba Settings',
        "intro": 'Uma página rolável em faixas verdes: General, Backup, Albums, Sync, Camera Sync, Restore, Archive e Help & Feedback. Cada faixa tem um (?).',
        "en_hash": "fdc210cbde6172d8",
    },
    'settings-overview': {
        "title": 'Como a aba Settings está organizada',
        "body": """• As mudanças valem na hora. Não existe botão Salvar.
• Nada aqui remove um arquivo do seu telefone. Só o Archive faz isso.
• Settings e a configuração inicial são separadas. Veja [[setup-and-settings]].""",
        "en_hash": "523b8f790e8db56b",
    },
    'settings-section-general': {
        "title": 'General',
        "body": """O **How To Guide**, **Appearance** e **Use mobile data**. **Language** é a última linha da página.""",
        "en_hash": "b8038bbcadfcd8b3",
    },
    'settings-how-to-guide-card': {
        "title": 'O cartão How To Guide',
        "body": """Abre este guia. **Read more in the How To Guide** em qualquer **(?)** o abre naquele tópico. Precisa de conexão com a internet; se não carregar, use **Open in browser**.""",
        "en_hash": "a7dec55c7b2218a4",
    },
    'settings-language': {
        "title": 'Language',
        "body": """O idioma que você escolheu na tela de Boas-vindas. Toque nele para mudar. O How To Guide segue essa escolha; as telas do app ficam em inglês por enquanto.""",
        "en_hash": "e1597813fc836e98",
    },
    'settings-appearance': {
        "title": 'Appearance',
        "body": """• **System** (padrão): segue a configuração clara ou escura do seu telefone.
• **Light** ou **Dark**: sempre essa aparência.

O guia e as páginas de política seguem a configuração do seu telefone.""",
        "en_hash": "b2ab8ff47282bc06",
    },
    'settings-mobile-data': {
        "title": 'Use mobile data',
        "body": """• **Off** (padrão): **Wi-Fi only.**
• **On:** **Syncs on Wi-Fi and mobile data.**

Vale para os backups automáticos e para o **Sync now**. Deixe desligado, a menos que seu plano de dados seja ilimitado.""",
        "en_hash": "e8860c60b1961fee",
    },
    'settings-section-backup': {
        "title": 'Backup',
        "body": """• **Clouds:** conecte ou saia de cada Cloud. Veja [[settings-account]].
• **Where each folder goes:** para qual Cloud cada pasta envia. Veja [[settings-destination]].

Uma Cloud é marcada como **main (free)**: a primeira Cloud que você selecionou. Ela é sempre grátis. As outras Clouds exigem o **Pro**: grátis por 30 dias (**Start 30-day free trial**), depois uma compra única (**Unlock Pro**). Nada é cobrado automaticamente.""",
        "en_hash": "0db12d3ebcb633de",
    },
    'settings-account': {
        "title": 'Suas Clouds e Sign out',
        "body": """Cada Cloud mostra a conta dela (ou **Not connected**) com **Sign out**, **Connect** ou **Enter keys**.

## Backup folder
Embaixo de cada Cloud conectada (exceto o Google Photos): a pasta para onde vão os backups dela, com **Change**. Veja [[dialog-destination]].

## Sign out
Primeiro vem um aviso: os backups para essa Cloud param, nada é apagado. Se essa Cloud guarda arquivos que não estão em tamanho real no telefone (otimizados ou arquivados), o app pergunta:
• **Restore them to the phone first:** traz todos de volta e depois sai.
• **Leave them there:** sai.
• **Cancel:** continua conectado.

As pastas continuam vinculadas a essa Cloud e esperam até você entrar de novo. Para enviá-las para outro lugar, mude **Where each folder goes**.""",
        "en_hash": "788b2237bf1a173f",
    },
    'settings-destination': {
        "title": 'Where each folder goes',
        "body": """Cada pasta de nível superior (DCIM, Pictures, Movies), a Cloud para onde ela vai e o lugar dentro dessa Cloud.

• Toque na Cloud para mudá-la. Só é possível mudar com mais de uma Cloud e com o Pro ou o teste.
• Só os arquivos novos seguem uma mudança. Os arquivos já enviados ficam onde estão.
• Uma pasta vinculada a uma Cloud extra sem o Pro não envia nada, e avisa isso.""",
        "en_hash": "22adac22e50fbdf0",
    },
    'dialog-destination': {
        "title": 'Where new backups go (a caixa de diálogo Change)',
        "body": """Aberta por **Change** ao lado do **Backup folder** de uma Cloud, em Settings ou no cartão dela durante a configuração.

• Digite o caminho da pasta ou, no OneDrive, use **Browse...**.
• **Reset to default:** volta para **Samsung Gallery/DCIM** (OneDrive) ou **GallerySync** (outras Clouds).
• **Use this folder** salva; **Cancel** não muda nada.

Só os arquivos novos vão para a pasta nova. Os arquivos que já têm backup ficam onde estão. O OneDrive continua olhando as pastas anteriores, então nada é enviado duas vezes.""",
        "en_hash": "910e0378a5a8b208",
    },
    'dialog-folder-picker': {
        "title": 'Choose a folder (o seletor do OneDrive)',
        "body": """Aberto por **Browse...** no OneDrive. Lista as suas pastas do OneDrive: toque para abrir, **Up one folder** para voltar, **Use this folder** para escolher, **New folder** para criar uma.""",
        "en_hash": "f45e3349352eea44",
    },
    'settings-section-albums': {
        "title": 'Albums',
        "body": """Quais pastas o GallerySync observa e o que acontece com as cópias na Cloud quando você apaga algo do telefone. Os álbuns novos sempre começam em **Off**.""",
        "en_hash": "dad7459947f2d8e1",
    },
    'settings-folders': {
        "title": 'Folders to back up',
        "body": """As pastas em que o GallerySync pode olhar.

• **Add a folder:** abre o seletor de pastas do Android.
• **Remove:** selecione as pastas, pressione **Remove** e confirme.

Nada fora dessas pastas é analisado ou alterado. Remover uma pasta não perde nada: adicione-a de volta e tudo retorna.""",
        "en_hash": "3789c04bb52baef5",
    },
    'dialog-remove-folder': {
        "title": 'Stop watching this folder?',
        "body": """**Remove** para de observar as pastas selecionadas; **Cancel** não muda nada.

Nada que já tem backup é perdido. Adicionar a pasta de volta restaura os álbuns e o histórico dela, sem nada ser enviado duas vezes.""",
        "en_hash": "5b84d0c4a6ec8a9a",
    },
    'settings-deletion': {
        "title": 'Quando você apaga uma foto ou vídeo deste telefone',
        "body": """• **Leave the Cloud copy** (padrão): nada é removido da sua Cloud.
• **Ask me about the Cloud copy:** quando arquivos saem do seu telefone, uma janela pergunta o que fazer com as cópias deles na Cloud. Se as notificações estiverem permitidas, você também é avisado pouco depois de apagar. Nada é removido sem a sua decisão. Veja [[deleted-files-window]].

Por enquanto, só o OneDrive. As cópias em outras Clouds nunca são removidas.""",
        "en_hash": "5fce34ecb857a3f6",
    },
    'deleted-files-window': {
        "title": 'Arquivos apagados do seu telefone (a janela que abre com o app)',
        "body": """Abre antes da aba Albums quando arquivos saíram do seu telefone, em **Ask**. Nada vem selecionado no início.

## Janela 1: há uma cópia no OneDrive
Deslize para a direita (ou toque) para selecionar os arquivos cuja cópia no OneDrive deve ser removida.
• **Remove N from OneDrive:** pede que você confirme e depois move essas cópias para a lixeira do OneDrive. Os arquivos não selecionados ficam no OneDrive. Veja [[dialog-remove-from-onedrive]].
• **Keep all in OneDrive.**
• **Decide later.**

## Janela 2: nenhuma cópia encontrada
Esses arquivos ainda estão na lixeira do seu telefone.
• **Back up N to OneDrive:** envia os arquivos selecionados a partir da lixeira. Nada é apagado.
• **Leave all in the trash.**
• **Decide later.**

## Bom saber
• Os arquivos sem decisão continuam na lista até você decidir.
• Não aparecem na lista: arquivos que o Archive removeu, fotos editadas no mesmo lugar e arquivos apagados de vez sem cópia na Cloud.
• Se mais da metade dos seus arquivos com backup parecer sumir de uma vez, nada é oferecido; isso é tratado como uma análise defeituosa e não como apagamentos de verdade.
• Voltar faz o mesmo que **Decide later**.""",
        "en_hash": "4cf138e07d6f0844",
    },
    'dialog-remove-from-onedrive': {
        "title": 'Remove files from OneDrive? (a confirmação)',
        "body": """Mostra quantos arquivos e o tamanho deles. Eles vão para a lixeira do OneDrive, que o GallerySync nunca esvazia.

**Remove from OneDrive** segue em frente; **Cancel** não decide nada. Qualquer arquivo que tenha voltado ao telefone é deixado em paz.""",
        "en_hash": "a5707f1ea8aad403",
    },
    'settings-section-sync': {
        "title": 'Sync',
        "body": """Como as fotos e os vídeos em álbuns **Sync** são otimizados. Os arquivos só mudam depois que a Cloud os verificou. Funciona com OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 e Backblaze B2.""",
        "en_hash": "1147f4e959ac06bc",
    },
    'settings-optimise-photos': {
        "title": 'Optimise photos',
        "body": """**Off** por padrão. Quando ligado, as fotos em álbuns Sync são substituídas por cópias menores: cerca de 2048 pixels no lado maior, aproximadamente um décimo do tamanho. Elas continuam na sua galeria com um pequeno selo de nuvem. Os originais ficam na Cloud.

Fotos que já são pequenas o bastante e arquivos em **Keep at full size** são deixados em paz. **Mode** define quando roda. Veja [[settings-optimise-mode]].""",
        "en_hash": "dee027d401742ca5",
    },
    'settings-optimise-video': {
        "title": 'Optimise video',
        "body": """**Off** por padrão. Quando ligado, mostra **Mode**, **Older than** e **Compression**.

Os vídeos em álbuns Sync são recodificados em um tamanho menor. Nada é cortado. Os originais ficam na Cloud.

## Quais vídeos
Em um álbum Sync, verified in the Cloud, mais antigo que a sua configuração, em uma pasta à qual você deu acesso e ainda não otimizado.

## Quando
• **Automatic:** depois dos backups, somente com o aparelho carregando.
• **Manual:** quando você pressiona **Sync now**.""",
        "en_hash": "09cf14026b288ef6",
    },
    'settings-optimise-mode': {
        "title": 'Mode: Automatic ou Manual',
        "body": """• **Automatic** (padrão): os arquivos em álbuns Sync são otimizados assim que a Cloud os confirma. O vídeo espera o carregador.
• **Manual:** nada acontece até você pressionar **Sync now** na aba Albums.

Fotos fora das pastas às quais você deu acesso precisam da confirmação do Android, então esperam até você abrir o app ou pressionar **Sync now**.""",
        "en_hash": "2d55959b2e538398",
    },
    'settings-video-age': {
        "title": 'Older than (vídeo)',
        "body": """Quão antigo um vídeo precisa ser antes de ser otimizado: **Straight away**, **1 hour**, **12 hours**, **1 day** ou **1 week**. O backup não é atrasado.

**Straight away** inclui os clipes gravados hoje; editar um deles começa a partir da cópia menor, até você restaurá-lo.""",
        "en_hash": "b69444389e56afe8",
    },
    'settings-video-quality': {
        "title": 'Compression (vídeo)',
        "body": """• **High compression, 480p:** os menores arquivos, cerca de 85% de economia.
• **Medium compression, 720p:** cerca de 75% de economia.
• **Low compression, 1080p:** cerca de 50% de economia.

Números típicos; os resultados variam.

## Depois de torná-la mais forte
O app pergunta **Re-optimise existing videos?** **Yes** permite que vídeos que eram pequenos demais para encolher antes sejam tentados de novo. **No** os deixa como estão. Os vídeos já otimizados não são tocados.""",
        "en_hash": "b68338f9be222883",
    },
    'settings-section-restore': {
        "title": 'Restore (Settings)',
        "body": """Um botão para a aba Restore: **Show empty folders**.""",
        "en_hash": "58571cb29a96c3ed",
    },
    'settings-show-empty-folders': {
        "title": 'Show empty folders',
        "body": """**Off** por padrão. Quando ligado, a aba Restore lista todas as pastas do OneDrive, inclusive as que não têm nada para trazer de volta.""",
        "en_hash": "44bada9612e80a0d",
    },
    'settings-section-camera': {
        "title": 'Camera Sync (Settings)',
        "body": """**Special settings for Camera**, desligado por padrão. Quando ligado:
• **Camera folder:** a pasta em que a sua câmera salva. Escolha na lista, ou **Other…**.
• **Select Photos/Videos older than**, **Photos**, **Videos:** padrões para os controles de otimização dessa pasta ([[album-camera-optimise]]).
• **Video compression:** para os vídeos otimizados ali.

A Camera folder não tem o modo **Sync**, e nada é otimizado até você pressionar **Sync now** nela. Os botões Optimise acima não afetam essa pasta.""",
        "en_hash": "d596a9c99a8de79d",
    },
    'settings-section-archive': {
        "title": 'Archive (Settings)',
        "body": """• **Only show files older than:** onde o filtro da aba Archive começa. Veja [[settings-archive-default-age]].
• **Notify when files are ready to archive:** desligado por padrão. Veja [[settings-archive-notify]].""",
        "en_hash": "7b4561ddc010537d",
    },
    'settings-archive-default-age': {
        "title": 'Only show files older than (Settings)',
        "body": """Onde o filtro de idade da aba Archive começa: **1 hour**, **1 day**, **1 week**, **1 month**, **1 year** ou **All** (padrão). Mudá-lo na aba Archive vale só para aquela visita. Veja [[archive-age-filter]].""",
        "en_hash": "215ff23fa51a5fca",
    },
    'settings-archive-notify': {
        "title": 'Notify when files are ready to archive',
        "body": """**Off** por padrão. Quando ligado, você é avisado uma vez por lote novo de arquivos prontos para arquivar. Somente arquivos do OneDrive.

A aba Albums e o lembrete ao sair também mostram isso, então você nunca depende só das notificações. Se as notificações estiverem bloqueadas, o botão avisa.""",
        "en_hash": "a0b5e2290fe1eaae",
    },
    'settings-about-cards': {
        "title": 'Help & Feedback e os cartões de link no fim',
        "body": """## Help & Feedback
• **Rate us on Play Store:** abre a página do GallerySync.
• **Bug Report:** onde escrever, e **Copy details** para a versão do app, o modelo do telefone e a versão do Android.

## No fim
• **Privacy Policy.**
• **Delete Account Info:** como sair, remover o acesso do app e limpar os dados dele.
• **Contact Info:** o endereço, com **Copy address**.

As páginas carregam pela internet. Se alguma não carregar, use **Open in browser**.""",
        "en_hash": "8a454188917d719a",
    },
}
