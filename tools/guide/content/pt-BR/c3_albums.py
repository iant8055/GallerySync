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
        "en_hash": "3c7f69542cac04f4",
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
        "body": """• Toque em **Backup**, **Sync**, **Archive** ou **Off** para mostrar só os álbuns nesse modo.
• Toque em **All Albums**, ou no mesmo botão de novo, para mostrar todos.

Filtrar só muda o que você vê. Para mudar um modo, use o botão no cartão do álbum. Veja [[modes-in-depth]].""",
        "en_hash": "d4865c5cf08d3857",
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
    'albums-optimised-line': {
        "title": 'Optimised and Saved',
        "body": """Com **Sync** selecionado: quantos arquivos dos álbuns em Sync foram otimizados e o espaço economizado.

Os originais continuam na Cloud. O **Restore** traz eles de volta. Veja [[how-optimising-works]].""",
        "en_hash": "1fbc295f2322e4d0",
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
        "title": 'Sync now, Rescan, Pause, Resume e Stop',
        "body": """## Quando nada está rodando
• **Sync now:** faz o backup agora. Se um botão Optimise em Settings estiver ligado com o **Mode** em **Manual**, ele também otimiza em seguida (veja [[settings-optimise-mode]]). Fica cinza quando não há nada para enviar ou otimizar.
• **Rescan:** olha o telefone e pergunta de novo ao OneDrive.

## Enquanto um backup roda
• O andamento aparece como **Syncing** com uma porcentagem (por volume de dados, não por número de arquivos).
• **Pause:** segura todos os backups até **Resume**.
• **Stop:** encerra esta execução. Os backups automáticos continuam.

## Bom saber
• **Sync now** não espera o carregador, mas continua seguindo a sua configuração de Wi-Fi. Veja [[settings-mobile-data]].
• Ele faz backup de todos os álbuns em **Backup**, **Sync** ou **Archive**, mas só otimiza arquivos em álbuns **Sync**.""",
        "en_hash": "846b4240970f2d4a",
    },
    'albums-status-line': {
        "title": 'A linha de status embaixo do cartão',
        "body": """• **Durante o envio:** o número de arquivos, o arquivo que está sendo enviado e o andamento dele.
• **Durante a preparação:** **Scanning...** ou **No permission to read media.**
• **Depois de uma execução:** um resumo do que se aplica: **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (nova tentativa mais tarde), **still to go** ou **stopped:** com o motivo.""",
        "en_hash": "78b2b8f3d6801853",
    },
    'albums-list': {
        "title": 'A aba Albums',
        "body": """## Cabeçalho
• Toque em **Backup**, **Sync**, **Archive** ou **Off** para mostrar só os álbuns nesse modo. Toque em **All Albums** para mostrar todos de novo.
• A linha embaixo dos botões dá o total de cada modo.

## Cartões de álbum
• **Nome, número de arquivos e tamanho** do álbum no seu telefone.
• **Segunda linha** (só quando necessária): quantos arquivos estão otimizados, mantidos em tamanho real, pendentes ou com falha.
• **Linha da Cloud:** **verified in OneDrive** significa que o OneDrive confirmou os arquivos no tamanho certo. Isso é verificado toda vez que você abre a aba. As outras Clouds mostram **sent to** essa Cloud.
• **Botão de modo** (no lado direito): toque para mudar o modo do álbum. O Archive pede que você confirme antes. Se a Cloud do álbum não consegue fazer esse modo, o cartão avisa.
• **Toque no cartão** para ver os arquivos do álbum. Veja [[album-detail]].""",
        "en_hash": "52297f12afc2a7eb",
    },
    'album-detail': {
        "title": 'A lista de arquivos de um álbum',
        "body": """Abre quando você toca em um cartão de álbum.

• **Cabeçalho:** seta de voltar, nome do álbum, modo e número de arquivos, **Backs up to** (a Cloud e a pasta para onde vão os arquivos novos, mais qualquer outra Cloud que já guarde alguns), as contagens ([[album-file-status]]), **Sort by** ([[album-file-sort]]) e **Keep at full size** ([[album-file-pin]]).
• **Arquivos:** um cartão por arquivo com tamanho, data e marcas. Um arquivo otimizado mostra o tamanho menor que tem no telefone.
• **Camera folder** (se escolhida em Settings): controles extras de otimização. Veja [[album-camera-optimise]].

Sem miniaturas nem pré-visualizações. Use o seu app de galeria para ver as fotos.""",
        "en_hash": "c5a7c5d01cd7947b",
    },
    'album-file-status': {
        "title": 'Marcas e contagens de arquivos em um álbum',
        "body": """• **✓ backed up:** na Cloud, em tamanho real no telefone.
• **✓ backed up · optimised:** na Cloud; o telefone tem uma cópia menor.
• **⟳ pending:** esperando para ser enviado.
• **✗ failed:** não pôde ser enviado depois de várias tentativas.

## Retry failed
**Retry** coloca os arquivos com falha de volta na fila e inicia um backup. Não remove nada.""",
        "en_hash": "a1305dbfcdea48b5",
    },
    'album-file-sort': {
        "title": 'Ordenar os arquivos de um álbum',
        "body": """• **Name:** de A a Z.
• **Date:** do mais novo para o mais antigo. Um arquivo restaurado tem a data em que foi restaurado.
• **Status:** com falha, depois pendente, depois otimizado, depois com backup.

A lista sempre abre em ordem de nome. Ordenar não muda nada nos arquivos.""",
        "en_hash": "030835fb143a638a",
    },
    'album-file-pin': {
        "title": 'Keep at full size',
        "body": """Uma caixa de seleção ao lado dos arquivos que o **Restore** trouxe de volta. Enquanto estiver selecionada, o GallerySync deixa aquele arquivo em paz:
• Em um álbum **Sync**, ele não é otimizado.
• Em um álbum **Archive**, ele não é removido.
• Em **Backup** ou **Off**, nada muda, mas a escolha fica guardada.

O Restore a seleciona automaticamente. Desmarque para que o arquivo volte a seguir o modo do álbum.""",
        "en_hash": "34b89b8b5829a956",
    },
    'album-camera-optimise': {
        "title": 'Otimizar fotos e vídeos mais antigos na sua Camera folder',
        "body": """Só a **Camera folder** que você escolheu em Settings, em **Camera Sync** ([[settings-section-camera]]), tem estes controles.

## Selecionar arquivos
• **Select Photos/Videos older than:** de **1 day** a **1 year**, ou **All**.
• **Photos** e **Videos:** quais tipos selecionar.
• Deslize um arquivo para a direita para selecionar e para a esquerda para desmarcar. O cartão mostra quantos estão selecionados e a economia estimada.

## Botões
• **Sync now:** faz o backup de tudo o que está esperando na pasta e depois otimiza só os arquivos selecionados.
• **Rescan:** confere a Cloud de novo.

## Bom saber
• Só os arquivos que o OneDrive confirmou em tamanho real podem ser selecionados.
• Nada é otimizado até você pressionar **Sync now**. As fotos novas ficam como estão.
• A Camera folder não tem o modo **Sync**, e os botões Optimise em Settings não afetam essa pasta.
• Sair com escolhas alteradas pergunta se você quer torná-las seus padrões.""",
        "en_hash": "f06a5a7ce89e99c4",
    },
    'album-camera-select': {
        "title": 'Selecionar arquivos na sua Camera folder',
        "body": """Mostra quantos arquivos estão selecionados e o espaço estimado que será economizado, ou **Nothing selected**.

• **Photos**, **Videos** e a idade selecionam todos os arquivos que combinam.
• Deslize para a direita para selecionar e para a esquerda para desmarcar.
• **Sync now** otimiza só os arquivos selecionados.

Um arquivo em cinza não pode ser selecionado: ele ainda não foi confirmado no OneDrive ou já está otimizado. Veja [[album-camera-optimise]].""",
        "en_hash": "9904773f638bae7b",
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
