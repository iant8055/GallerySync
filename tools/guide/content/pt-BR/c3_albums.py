TRANSLATIONS = {
    "_chapter": {
        "title": 'A aba Albums',
        "intro": 'Onde você escolhe o modo de cada álbum e executa os backups.',
        "en_hash": "7c39f69f07321c58",
    },
    'albums-overview': {
        "title": 'Como a aba Albums está organizada',
        "body": """• **Cabeçalho verde:** botões de modo, totais e os controles de backup.
• **Linha de status:** aparece enquanto algo está rodando ou acabou de terminar.
• **Lista de álbuns:** um cartão por álbum, de A a Z. Duas colunas em tela larga.

Toda vez que você abre a aba, ela olha o telefone e pergunta de novo ao OneDrive (**Rescan** mostra **Checking...**). As outras Clouds são consultadas sobre um arquivo pouco antes de ele ser otimizado ou removido.

Olhar, filtrar e rolar nunca alteram um arquivo. Só escolher um modo altera.""",
        "en_hash": "e3efef2f9212aaf3",
    },
    'albums-new': {
        "title": 'Álbuns novos',
        "body": """Quando aparece um álbum novo, um pop-up diz **1 new Album is waiting for you to choose a mode**.
• **Show new Albums:** lista só os novos. **Show all Albums** traz os outros de volta.
• **Dismiss:** fecha a mensagem.

Os álbuns novos levam uma etiqueta **New** e sempre começam em **Off**. Um álbum novo nunca pode começar como Archive.""",
        "en_hash": "8f8d4b55c34a1c4d",
    },
    'albums-filter': {
        "title": 'All Albums e os quatro botões de modo',
        "body": """• Toque em **Backup**, **Optimize**, **Archive** ou **Off** para mostrar só os álbuns nesse modo.
• Toque em **All Albums**, ou no mesmo botão de novo, para mostrar todos.

Filtrar só muda o que você vê. Para mudar um modo, use o botão no cartão do álbum. Veja [[modes-in-depth]].""",
        "en_hash": "fce05e6ad448f530",
    },
    'albums-totals': {
        "title": 'Album Mode Count',
        "body": """**Album Mode Count:** o total de cada modo. Aparece com **All Albums** selecionado. Os álbuns em **Off** são aqueles sobre os quais você ainda não decidiu.""",
        "en_hash": "61afcd38a3bee22c",
    },
    'albums-all-backed-up': {
        "title": 'Tudo aqui tem backup',
        "body": """Aparece quando todos os álbuns à vista têm arquivos e nenhum está esperando para ser enviado. Vem do registro do próprio app; a resposta da própria Cloud é a última linha de cada cartão de álbum.""",
        "en_hash": "79b43044d6005ada",
    },
    'albums-optimized-line': {
        "title": 'Optimized and Saved',
        "body": """Com **Optimize** selecionado: quantos arquivos dos álbuns em Optimize foram otimizados e o espaço economizado.

Os originais continuam na Cloud. O **Restore** traz eles de volta. Veja [[how-optimizing-works]].""",
        "en_hash": "280df5abf38d7971",
    },
    'albums-archive-lines': {
        "title": 'Files archived e Scheduled to leave this phone',
        "body": """Com **Archive** selecionado:
• **N files archived · X in your Cloud:** arquivos que saíram do telefone e o espaço que as cópias deles ocupam na Cloud.
• **N Scheduled to leave this phone:** arquivos que ainda estão no telefone em álbuns Archive, sem contar os arquivos que você escolheu manter. **Nothing left to archive** quando não há nenhum.

Nada é removido aqui. A remoção acontece na aba Archive. Veja [[archive-overview]].""",
        "en_hash": "00b01adb4a7cf037",
    },
    'albums-run-controls': {
        "title": 'Back Up Now, Rescan, Pause, Resume e Stop',
        "body": """## Quando nada está rodando
• **Back Up Now:** faz o backup agora. Se um botão Optimize em Settings estiver ligado com o **Modo** em **Manual**, ele também otimiza em seguida (veja [[settings-optimize-mode]]). Fica cinza quando não há nada para enviar ou otimizar.
• **Rescan:** olha o telefone e pergunta de novo ao OneDrive.

## Enquanto um backup roda
• O andamento aparece como **Backing up now** com uma porcentagem (por volume de dados, não por número de arquivos).
• **Pause:** segura todos os backups até **Resume**.
• **Stop:** encerra esta execução. Os backups automáticos continuam.

## Bom saber
• **Back Up Now** não espera o carregador, mas continua seguindo a sua configuração de Wi-Fi. Veja [[settings-mobile-data]].
• Ele faz backup de todos os álbuns em **Backup**, **Optimize** ou **Archive**, mas só otimiza arquivos em álbuns **Optimize**.""",
        "en_hash": "7018ccad25f0b316",
    },
    'albums-status-line': {
        "title": 'Andamento do envio e mensagens de status',
        "body": """• **Durante o envio** (dentro do cartão): o número de arquivos, o álbum, o arquivo que está sendo enviado e os bytes enviados em relação ao tamanho total do arquivo. Enquanto o tamanho do arquivo ainda não é conhecido, aparece um percentual no lugar. A barra de progresso fica embaixo da linha. **▲ Hide Details ▲** esconde as duas.
• **Durante a preparação** (embaixo do cartão): **Scanning...** ou **No permission to read media.**
• **Depois de uma execução** (embaixo do cartão): um resumo do que se aplica: **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (nova tentativa mais tarde), **still to go** ou **stopped:** com o motivo.""",
        "en_hash": "5e5b683e646d7541",
    },
    "albums-list": {
        "title": "A aba Albums",
        "body": """## Cabeçalho
• Toque em **Backup**, **Optimize**, **Archive** ou **Off** para mostrar só os álbuns nesse modo. Toque em **All Albums** para mostrar todos de novo.
• A linha abaixo dos botões dá o total por modo.
• **▲ Hide Details ▲** no fim do cartão esconde a linha do total por modo e, enquanto uma execução está rolando, também a linha do envio e sua barra; **▼ Show Details ▼** traz de volta. **All Albums**, os quatro botões de modo e **Back Up Now** / **Rescan** ficam, e enquanto uma execução está rolando, **Backing up now…**, **Pause** e **Stop** ficam também. A aba lembra sua escolha.

## Cartões de álbum
• **Nome, número de arquivos e tamanho** do álbum no seu telefone.
• **Segunda linha** (só quando for necessário): quantos arquivos estão otimizados, marcados como *do not optimize*, pendentes ou travados.
• **Linha da Cloud:** **N verified in <Cloud>** ou **N of M verified in <Cloud>** quer dizer que a Cloud confirmou os arquivos no tamanho certo. OneDrive, Dropbox, Google Drive, pCloud, Backblaze B2 e IDrive e2 são conferidos cada vez que você abre a aba e no **Rescan**. **Could not reach <Cloud>** quer dizer que a última conferência falhou. Antes da primeira conferência — e sempre para o Google Photos, que não pode ser consultado — a linha diz **N sent to <Cloud>**. A linha fica verde quando toda Cloud nela confirmou tudo.
• **Linha vermelha** (só se houver algum): **N files not backed up** — arquivos que saíram de sua Cloud, falharam, ou cujo envio não pôde terminar. Cada um precisa de uma decisão; abra o álbum e deslize. Veja [[album-resend]].
• **Linha cinza** (só se houver algum): **N files not backed up by choice** — arquivos nos quais você deslizou para a esquerda, mais os travados. Eles são respeitados; nada os usa.
• **Botão de modo** (lado direito): toque para mudar o modo do álbum. Archive pede confirmação primeiro. Se a Cloud do álbum não pode esse modo, o cartão diz.
• **Toque no cartão** para ver os arquivos do álbum. Veja [[album-detail]].""",
        "en_hash": "0b9328a4170e83e0",
    },
    'album-detail': {
        "title": 'A lista de arquivos de um álbum',
        "body": """Abre quando você toca em um cartão de álbum.

• **Cabeçalho:** seta de voltar, nome do álbum, modo e número de arquivos, **Backs up to** (a Cloud e a pasta para onde vão os arquivos novos, mais qualquer outra Cloud que já guarde alguns), as contagens ([[album-file-status]]), **Sort by** ([[album-file-sort]]) e **Do not optimize** ([[album-file-pin]]).
• **▲ Hide Details ▲** no rodapé do cabeçalho o dobra para o nome do álbum, o modo e o número de arquivos; **▼ Show Details ▼** traz o resto de volta. A página lembra sua escolha.
• **Arquivos:** um cartão por arquivo com tamanho, data e marcas. Um arquivo otimizado mostra o tamanho menor que tem no telefone.
• **Pasta da câmera** (se escolhida em Settings): controles extras de otimização. Veja [[album-camera-optimize]].

Sem miniaturas nem pré-visualizações. **Toque e segure um arquivo** para abri-lo no app de fotos ou vídeos do seu telefone, ou na sua Cloud se não estiver mais no telefone. Veja [[settings-open-not-on-phone]].""",
        "en_hash": "52f8fd50a2be074a",
    },
    "album-file-status": {
        "title": "Marcas e contagens de arquivos em um álbum",
        "body": """• **✓ backed up:** na Cloud, em tamanho total no telefone.
• **✓ backed up · optimized:** na Cloud; o telefone tem uma cópia menor.
• **✓ backed up · locked:** na Cloud, e você deslizou para a esquerda: nunca mais é enviado, nem otimizado, nem arquivado. Continua sendo conferido: se sua cópia na Cloud sumir, o álbum sinaliza.
• **⟳ pending:** aguarda a próxima execução.
• **do not back up:** você deslizou para a esquerda em um arquivo sem backup. Não é enviado, não é otimizado, não é oferecido para Archive. Uma cópia que já esteja em uma Cloud fica lá.
• **couldn't finish uploading · trying after the rest:** um envio cortado duas vezes sem avançar. Vai depois de tudo, para que um arquivo travado não segure a fila.
• **not in <Cloud>:** enviado antes, e a Cloud não tem mais.

Arquivos nos três últimos estados são contados em vermelho no cartão do álbum como **N files not backed up**. Cada um precisa de uma decisão — veja [[album-resend]].

## Do not optimize
Uma caixa de seleção ao lado de todo arquivo que ainda poderia ser otimizado: arquivos que **Restore** trouxe de volta em qualquer álbum, e todo arquivo em tamanho total em um álbum **Optimize**. Enquanto marcada, GallerySync nunca otimiza esse arquivo. O cabeçalho do álbum mostra **N marked do not optimize**. Restore marca os arquivos que traz.

## Contagens do cabeçalho
Acrescentam **N not backed up by choice** quando algum arquivo do álbum está marcado como *do not back up* ou *locked*.""",
        "en_hash": "94b7a8315189854c",
    },
    "album-resend": {
        "title": "Arquivos sem backup",
        "body": """Arquivos que este álbum enviou e que não estão mais na sua Cloud, arquivos cujo envio não pôde terminar, e arquivos que falharam aparecem aqui. O cartão do álbum os conta em vermelho como **N files not backed up** — cada um precisa de uma decisão. **Rescan** na aba Albums pergunta às Clouds de novo.

Cada um aparece esmaecido. Um deslize decide:

• **Deslize para a direita:** faça o backup.
  • Para um arquivo que saiu de sua Cloud: é enviado na hora (a Cloud é perguntada primeiro).
  • Para um arquivo que falhou cinco vezes: só esse é tentado de novo.
  • Para um arquivo cujo envio foi cortado: volta para a fila principal.
  • Para um arquivo que você manteve fora do backup: volta para o backup.
• **Deslize para a esquerda:** marque **do not back up**. Não é enviado, não é otimizado, não é oferecido para Archive. Uma cópia já em uma Cloud fica lá.

Nada é reenviado sozinho. Eles podem ter sido apagados na Cloud de propósito.

## Back up all N
No cabeçalho, quando algum arquivo está aguardando uma decisão. Faz backup de todo arquivo que precisa de uma decisão, menos os marcados *do not back up* e os que estão em *✓ backed up · locked*. Esmaecido quando não há nada a fazer.

## Bom saber
• Arquivos otimizados não aparecem aqui: o original em tamanho total só estava na Cloud.
• Em um álbum **Off**, escolha um modo primeiro.
• Nas Clouds que não são o OneDrive, cada Rescan consulta no máximo 25 arquivos por álbum que não estão na pasta do álbum. Use Rescan de novo para o resto.""",
        "en_hash": "bab90f38c05917bd",
    },

    'album-file-sort': {
        "title": 'Ordenar os arquivos de um álbum',
        "body": """• **Name:** de A a Z.
• **Date:** do mais novo para o mais antigo.
• **Status:** com falha, depois pendente, depois otimizado, depois com backup.

A lista sempre abre em ordem de nome. Ordenar não muda nada nos arquivos.""",
        "en_hash": "768559e84cdab251",
    },
    "album-file-pin": {
        "title": "Do not optimize",
        "body": """Uma caixa de seleção ao lado de todo arquivo em um álbum **Optimize** que ainda poderia ser reduzido, e ao lado dos arquivos que **Restore** trouxe de volta em qualquer álbum. Enquanto marcada, GallerySync nunca otimiza esse arquivo — ele fica em tamanho total no telefone.

O cabeçalho do álbum mostra **N marked do not optimize**. Restore marca cada arquivo que traz. Desmarque para que o arquivo volte a seguir o modo do seu álbum.

## Não é a mesma coisa que travar
Deslizar para a esquerda em um arquivo com backup o marca como **locked** (veja [[album-file-status]]): nunca mais é enviado, nunca otimizado **e** nunca arquivado, mas continua sendo conferido. *Do not optimize* só impede a otimização.""",
        "en_hash": "4d4e77d702609a93",
    },
    'album-camera-optimize': {
        "title": 'Otimizar fotos e vídeos mais antigos na sua Pasta da câmera',
        "body": """Só a **Pasta da câmera** que você escolheu em Settings, em **Pasta da câmera** ([[settings-section-camera]]), tem estes controles.

## Selecionar arquivos
• **Select Photos/Videos older than:** um número de **½** a **9** e uma unidade — **hour**, **day**, **week**, **month**, **year** ou **All**.
• **Photos** e **Videos:** quais tipos selecionar.
• Deslize um arquivo para a direita para selecionar e para a esquerda para desmarcar. O cartão mostra quantos estão selecionados e a economia estimada.

## Botões
• **Back Up Now:** faz o backup de tudo o que está esperando na pasta e depois otimiza só os arquivos selecionados.
• **Rescan:** confere a Cloud de novo.

## Bom saber
• Só os arquivos que o OneDrive confirmou em tamanho real podem ser selecionados.
• Nada é otimizado até você pressionar **Back Up Now**. As fotos novas ficam como estão.
• A Pasta da câmera não tem o modo **Optimize**, e os botões Optimize em Settings não afetam essa pasta.
• Sair com escolhas alteradas pergunta se você quer torná-las seus padrões.""",
        "en_hash": "10e1e3ba1e9982e2",
    },
    'album-camera-select': {
        "title": 'Selecionar arquivos na sua Pasta da câmera',
        "body": """Mostra quantos arquivos estão selecionados e o espaço estimado que será economizado, ou **Nothing selected**.

• **Photos**, **Videos** e a idade selecionam todos os arquivos que combinam.
• Deslize para a direita para selecionar e para a esquerda para desmarcar.
• **Back Up Now** otimiza só os arquivos selecionados.

Um arquivo em cinza não pode ser selecionado: ele ainda não foi confirmado no OneDrive ou já está otimizado. Veja [[album-camera-optimize]].""",
        "en_hash": "0bfa66dc8a4265bf",
    },
    'album-merge-warning': {
        "title": 'Duplicate album names detected',
        "body": """Uma única pasta pode acabar registrada com duas grafias que só diferem nas letras maiúsculas. O GallerySync percebe isso, junta as duas em um só álbum e define o modo dele como **Off** para você escolher de novo. **Dismiss** fecha o cartão.

Esta é a única vez em que o app define um modo, e ele só pode definir **Off**.""",
        "en_hash": "559da2287e55a253",
    },
    'dialog-archive-confirm': {
        "title": 'Archive this album?',
        "body": """Aparece quando você escolhe **Archive**:
• O Archive confere se todos os arquivos estão na Cloud.
• Os arquivos arquivados vão para a lixeira do seu telefone.
• Esvazie a lixeira para liberar o espaço.

**Archive** confirma; **Cancel** não muda nada.

## Bom saber
Você confirma uma vez só. Os arquivos adicionados ao álbum depois também são cobertos, até o álbum ficar vazio. O Android pergunta uma vez por lote de até 2.000 arquivos. Veja [[dialog-android-trash]].""",
        "en_hash": "ac36f7c88ce4bc76",
    },
}
