TRANSLATIONS = {
    "_chapter": {
        "title": 'A aba Settings',
        "intro": 'Uma página rolável em faixas verdes: Geral, Backup, Albums, Sync, Sync da câmera, Restore, Archive e Ajuda e feedback. Cada faixa tem um (?).',
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
        "title": 'Geral',
        "body": """O **Guia de uso**, **Aparência** e **Usar dados móveis**. **Idioma** é a última linha da página.""",
        "en_hash": "b8038bbcadfcd8b3",
    },
    'settings-how-to-guide-card': {
        "title": 'O cartão Guia de uso',
        "body": """Abre este guia. **Saiba mais no Guia de uso** em qualquer **(?)** o abre naquele tópico. Precisa de conexão com a internet; se não carregar, use **Abrir no navegador**.""",
        "en_hash": "a7dec55c7b2218a4",
    },
    'settings-language': {
        "title": 'Idioma',
        "body": """O idioma que você escolheu na tela de Boas-vindas. Toque nele para mudar. O Guia de uso segue essa escolha; as telas do app ficam em inglês por enquanto.""",
        "en_hash": "e1597813fc836e98",
    },
    'settings-appearance': {
        "title": 'Aparência',
        "body": """• **Sistema** (padrão): segue a configuração clara ou escura do seu telefone.
• **Claro** ou **Escuro**: sempre essa aparência.

O guia e as páginas de política seguem a configuração do seu telefone.""",
        "en_hash": "b2ab8ff47282bc06",
    },
    'settings-mobile-data': {
        "title": 'Usar dados móveis',
        "body": """• **Off** (padrão): **Só Wi-Fi.**
• **On:** **Sincroniza no Wi-Fi e nos dados móveis.**

Vale para os backups automáticos e para o **Sync now**. Deixe desligado, a menos que seu plano de dados seja ilimitado.""",
        "en_hash": "e8860c60b1961fee",
    },
    'settings-section-backup': {
        "title": 'Backup',
        "body": """• **Nuvens:** conecte ou saia de cada Cloud. Veja [[settings-account]].
• **Para onde vai cada pasta:** para qual Cloud cada pasta envia. Veja [[settings-destination]].

Uma Cloud é marcada como **principal (grátis)**: a primeira Cloud que você selecionou. Ela é sempre grátis. As outras Clouds exigem o **Pro**: grátis por 30 dias (**Começar o teste grátis de 30 dias**), depois uma compra única (**Desbloquear o Pro**). Nada é cobrado automaticamente.""",
        "en_hash": "0db12d3ebcb633de",
    },
    'settings-account': {
        "title": 'Suas Clouds e Sair',
        "body": """Cada Cloud mostra a conta dela (ou **Não conectado**) com **Sair**, **Conectar** ou **Inserir chaves**.

## Pasta de backup
Embaixo de cada Cloud conectada (exceto o Google Photos): a pasta para onde vão os backups dela, com **Alterar**. Veja [[dialog-destination]].

## Sair
Primeiro vem um aviso: os backups para essa Cloud param, nada é apagado. Se essa Cloud guarda arquivos que não estão em tamanho real no telefone (otimizados ou arquivados), o app pergunta:
• **Restaurar no telefone primeiro:** traz todos de volta e depois sai.
• **Deixar lá:** sai.
• **Cancelar:** continua conectado.

As pastas continuam vinculadas a essa Cloud e esperam até você entrar de novo. Para enviá-las para outro lugar, mude **Para onde vai cada pasta**.""",
        "en_hash": "788b2237bf1a173f",
    },
    'settings-destination': {
        "title": 'Para onde vai cada pasta',
        "body": """Cada pasta de nível superior (DCIM, Pictures, Movies), a Cloud para onde ela vai e o lugar dentro dessa Cloud.

• Toque na Cloud para mudá-la. Só é possível mudar com mais de uma Cloud e com o Pro ou o teste.
• Só os arquivos novos seguem uma mudança. Os arquivos já enviados ficam onde estão.
• Uma pasta vinculada a uma Cloud extra sem o Pro não envia nada, e avisa isso.""",
        "en_hash": "22adac22e50fbdf0",
    },
    'dialog-destination': {
        "title": 'Para onde vão os novos backups (a caixa de diálogo Alterar)',
        "body": """Aberta por **Alterar** ao lado da **Pasta de backup** de uma Cloud, em Settings ou no cartão dela durante a configuração.

• Digite o caminho da pasta ou, no OneDrive, use **Procurar…**.
• **Voltar ao padrão:** volta para **Samsung Gallery/DCIM** (OneDrive) ou **GallerySync** (outras Clouds).
• **Usar esta pasta** salva; **Cancelar** não muda nada.

Só os arquivos novos vão para a pasta nova. Os arquivos que já têm backup ficam onde estão. O OneDrive continua olhando as pastas anteriores, então nada é enviado duas vezes.""",
        "en_hash": "910e0378a5a8b208",
    },
    'dialog-folder-picker': {
        "title": 'Escolha uma pasta (o seletor do OneDrive)',
        "body": """Aberto por **Procurar…** no OneDrive. Lista as suas pastas do OneDrive: toque para abrir, **Subir uma pasta** para voltar, **Usar esta pasta** para escolher, **Nova pasta** para criar uma.""",
        "en_hash": "f45e3349352eea44",
    },
    'settings-section-albums': {
        "title": 'Albums',
        "body": """Quais pastas o GallerySync observa e o que acontece com as cópias na Cloud quando você apaga algo do telefone. Os álbuns novos sempre começam em **Off**.""",
        "en_hash": "dad7459947f2d8e1",
    },
    'settings-folders': {
        "title": 'Pastas para backup',
        "body": """As pastas em que o GallerySync pode olhar.

• **Adicionar uma pasta:** abre o seletor de pastas do Android.
• **Remover:** selecione as pastas, pressione **Remover** e confirme.

Nada fora dessas pastas é analisado ou alterado. Remover uma pasta não perde nada: adicione-a de volta e tudo retorna.""",
        "en_hash": "3789c04bb52baef5",
    },
    'dialog-remove-folder': {
        "title": 'Parar de observar esta pasta?',
        "body": """**Remover** para de observar as pastas selecionadas; **Cancelar** não muda nada.

Nada que já tem backup é perdido. Adicionar a pasta de volta restaura os álbuns e o histórico dela, sem nada ser enviado duas vezes.""",
        "en_hash": "5b84d0c4a6ec8a9a",
    },
    'settings-deletion': {
        "title": 'Quando você apaga uma foto ou vídeo deste telefone',
        "body": """• **Deixar a cópia na Nuvem** (padrão): nada é removido da sua Cloud.
• **Perguntar sobre a cópia na Nuvem:** quando arquivos saem do seu telefone, uma janela pergunta o que fazer com as cópias deles na Cloud. Se as notificações estiverem permitidas, você também é avisado pouco depois de apagar. Nada é removido sem a sua decisão. Veja [[deleted-files-window]].

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
        "title": 'Otimizar fotos',
        "body": """**Off** por padrão. Quando ligado, as fotos em álbuns Sync são substituídas por cópias menores: cerca de 2048 pixels no lado maior, aproximadamente um décimo do tamanho. Elas continuam na sua galeria com um pequeno selo de nuvem. Os originais ficam na Cloud.

Fotos que já são pequenas o bastante e arquivos em **Keep at full size** são deixados em paz. **Modo** define quando roda. Veja [[settings-optimise-mode]].""",
        "en_hash": "dee027d401742ca5",
    },
    'settings-optimise-video': {
        "title": 'Otimizar vídeos',
        "body": """**Off** por padrão. Quando ligado, mostra **Modo**, **Com mais de** e **Compressão**.

Os vídeos em álbuns Sync são recodificados em um tamanho menor. Nada é cortado. Os originais ficam na Cloud.

## Quais vídeos
Em um álbum Sync, verified in the Cloud, mais antigo que a sua configuração, em uma pasta à qual você deu acesso e ainda não otimizado.

## Quando
• **Automático:** depois dos backups, somente com o aparelho carregando.
• **Manual:** quando você pressiona **Sync now**.""",
        "en_hash": "09cf14026b288ef6",
    },
    'settings-optimise-mode': {
        "title": 'Modo: Automático ou Manual',
        "body": """• **Automático** (padrão): os arquivos em álbuns Sync são otimizados assim que a Cloud os confirma. O vídeo espera o carregador.
• **Manual:** nada acontece até você pressionar **Sync now** na aba Albums.

Fotos fora das pastas às quais você deu acesso precisam da confirmação do Android, então esperam até você abrir o app ou pressionar **Sync now**.""",
        "en_hash": "2d55959b2e538398",
    },
    'settings-video-age': {
        "title": 'Com mais de (vídeo)',
        "body": """Quão antigo um vídeo precisa ser antes de ser otimizado: **Na hora**, **1 hora**, **12 horas**, **1 dia** ou **1 semana**. O backup não é atrasado.

**Na hora** inclui os clipes gravados hoje; editar um deles começa a partir da cópia menor, até você restaurá-lo.""",
        "en_hash": "b69444389e56afe8",
    },
    'settings-video-quality': {
        "title": 'Compressão (vídeo)',
        "body": """• **Compressão alta — 480p:** os menores arquivos, cerca de 85% de economia.
• **Compressão média — 720p:** cerca de 75% de economia.
• **Compressão baixa — 1080p:** cerca de 50% de economia.

Números típicos; os resultados variam.

## Depois de torná-la mais forte
O app pergunta **Otimizar de novo os vídeos existentes?** **Sim** permite que vídeos que eram pequenos demais para encolher antes sejam tentados de novo. **Não** os deixa como estão. Os vídeos já otimizados não são tocados.""",
        "en_hash": "b68338f9be222883",
    },
    'settings-section-restore': {
        "title": 'Restore (Settings)',
        "body": """Um botão para a aba Restore: **Mostrar pastas vazias**.""",
        "en_hash": "58571cb29a96c3ed",
    },
    'settings-show-empty-folders': {
        "title": 'Mostrar pastas vazias',
        "body": """**Off** por padrão. Quando ligado, a aba Restore lista todas as pastas do OneDrive, inclusive as que não têm nada para trazer de volta.""",
        "en_hash": "44bada9612e80a0d",
    },
    'settings-section-camera': {
        "title": 'Sync da câmera (Settings)',
        "body": """**Configurações especiais para a câmera**, desligado por padrão. Quando ligado:
• **Pasta da câmera:** a pasta em que a sua câmera salva. Escolha na lista, ou **Outra…**.
• **Select Photos/Videos older than**, **Photos**, **Videos:** padrões para os controles de otimização dessa pasta ([[album-camera-optimise]]).
• **Compressão de vídeo:** para os vídeos otimizados ali.

A Pasta da câmera não tem o modo **Sync**, e nada é otimizado até você pressionar **Sync now** nela. Os botões Optimise acima não afetam essa pasta.""",
        "en_hash": "d596a9c99a8de79d",
    },
    'settings-section-archive': {
        "title": 'Archive (Settings)',
        "body": """• **Only show files older than:** onde o filtro da aba Archive começa. Veja [[settings-archive-default-age]].
• **Avisar quando houver arquivos prontos para Archive:** desligado por padrão. Veja [[settings-archive-notify]].""",
        "en_hash": "7b4561ddc010537d",
    },
    'settings-archive-default-age': {
        "title": 'Only show files older than (Settings)',
        "body": """Onde o filtro de idade da aba Archive começa: **1 hour**, **1 day**, **1 week**, **1 month**, **1 year** ou **All** (padrão). Mudá-lo na aba Archive vale só para aquela visita. Veja [[archive-age-filter]].""",
        "en_hash": "215ff23fa51a5fca",
    },
    'settings-archive-notify': {
        "title": 'Avisar quando houver arquivos prontos para Archive',
        "body": """**Off** por padrão. Quando ligado, você é avisado uma vez por lote novo de arquivos prontos para arquivar. Somente arquivos do OneDrive.

A aba Albums e o lembrete ao sair também mostram isso, então você nunca depende só das notificações. Se as notificações estiverem bloqueadas, o botão avisa.""",
        "en_hash": "a0b5e2290fe1eaae",
    },
    'settings-about-cards': {
        "title": 'Ajuda e feedback e os cartões de link no fim',
        "body": """## Ajuda e feedback
• **Avalie na Play Store:** abre a página do GallerySync.
• **Relatar um erro:** onde escrever, e **Copiar detalhes** para a versão do app, o modelo do telefone e a versão do Android.

## No fim
• **Política de privacidade.**
• **Excluir dados da conta:** como sair, remover o acesso do app e limpar os dados dele.
• **Contato:** o endereço, com **Copiar endereço**.

As páginas carregam pela internet. Se alguma não carregar, use **Abrir no navegador**.""",
        "en_hash": "8a454188917d719a",
    },
}
