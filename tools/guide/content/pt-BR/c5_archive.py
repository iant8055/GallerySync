TRANSLATIONS = {
    "_chapter": {
        "title": 'A aba Archive',
        "intro": 'O único lugar onde arquivos saem do seu telefone. Só álbuns que você colocou em Archive, conferidos antes com a sua Cloud, e só depois do seu toque.',
        "en_hash": "99a3f217222adae4",
    },
    'archive-overview': {
        "title": 'Como o Archive funciona, passo a passo',
        "body": """**O Archive exige Android 11 ou mais recente.**

1. **Coloque um álbum em Archive** na aba Albums e confirme. Veja [[dialog-archive-confirm]].
2. **Pressione Check these files.** Cada arquivo é conferido com a Cloud dele. Os arquivos que ainda não estão lá são enviados primeiro.
3. **Veja o resultado:** uma marca de verificação ou uma cruz vermelha em cada arquivo, e quanto espaço será liberado.
4. **Diga Yes.** O Android pede para mover os arquivos confirmados para a lixeira do telefone. Veja [[dialog-android-trash]].
5. **Esvazie a lixeira** quando estiver pronto. O espaço só volta nesse momento.

## O que significa "com segurança"
A Cloud precisa confirmar, naquele momento, que tem o arquivo com o mesmo tamanho. Se não for possível perguntar à Cloud, nada é removido. Para uma foto otimizada, confere-se o tamanho do original.

## Quais Clouds
OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 e Backblaze B2. Não o Google Photos.

## Bom saber
• O Archive precisa que você esteja presente: o Android exige um toque. Veja [[dialog-exit-warning]].
• Os arquivos adicionados depois a um álbum Archive também são cobertos.
• Quando o último arquivo sai, o álbum sai da lista e o modo dele é esquecido. Se ele voltar, começa em Off.""",
        "en_hash": "767ff53f0ad97065",
    },
    'archive-hero': {
        "title": 'Files to Archive',
        "body": """**Files to Archive**, com o número de arquivos que serão arquivados: em todos os álbuns Archive da lista de álbuns, ou no álbum que você abriu. Os arquivos que você manteve no telefone não são contados.

Embaixo: seus álbuns Archive e um lembrete de que os arquivos arquivados podem ser trazidos de volta na aba **Restore**.

Todo arquivo é conferido com sua Cloud antes de qualquer coisa sair do telefone. Um arquivo que ainda não esteja na sua Cloud é enviado primeiro e verificado antes de ser contado.

**▲ Hide Details ▲** no rodapé do cartão o dobra para o título, o número e o botão principal; **▼ Show Details ▼** traz de volta os nomes dos álbuns, a opção **Only show files older than**, a explicação e o aviso de **Restore**. **Check these files**, seus resultados e a pergunta Yes/No continuam visíveis. A aba lembra sua escolha.""",
        "en_hash": "487804f5f7434e77",
    },
    'archive-folder-list': {
        "title": 'Os álbuns Archive',
        "body": """Um cartão por álbum Archive: quantos arquivos estão esperando e o tamanho deles, mais os que foram mantidos, retidos pelo filtro de idade, confirmados ou não confirmados.

• Na lista de álbuns, o botão diz **Check all albums** e confere todos os álbuns.
• Toque em um álbum para abri-lo; **Check these files** então confere só esse álbum.

Escolher um álbum não remove nada e não muda nenhum modo.""",
        "en_hash": "e1a713efaeea9168",
    },
    'archive-check-button': {
        "title": 'Check these files e o que ele informa',
        "body": """• **Check these files** (ou **Check all albums**): inicia a conferência.
• **Checking with your Cloud...** enquanto a Cloud é consultada.
• O andamento de **Batch** enquanto os arquivos estão sendo removidos.
• Quantos arquivos foram removidos e o espaço liberado, ou **Nothing was removed.**

O OneDrive é conferido listando a pasta de cada álbum no OneDrive (nome e tamanho). O Dropbox, o Google Drive, o pCloud, o Backblaze B2 e o IDrive e2 são consultados sobre cada arquivo.

"Liberar" quer dizer depois que você esvaziar a lixeira.""",
        "en_hash": "651062d720e9e712",
    },
    'archive-age-filter': {
        "title": 'Only show files older than…',
        "body": """Limita esta conferência a arquivos com pelo menos essa idade: de **1 hour** a **1 year**, ou **All**. Os arquivos mais novos ficam em cinza e são deixados para uma conferência futura.

Ele começa no seu padrão de Settings. Mudá-lo aqui vale só para esta visita.""",
        "en_hash": "3d5d116005b80474",
    },
    'archive-empty': {
        "title": 'Quando não há nada para arquivar',
        "body": """• **Nenhum álbum está em Archive:** defina um na aba Albums.
• **Um zero:** tudo nos seus álbuns Archive já saiu do telefone.
• **Esta versão do Android não tem lixeira de mídia:** o Archive exige Android 11 ou mais recente.""",
        "en_hash": "ebf6a50a3d8d1d39",
    },
    'archive-file-list': {
        "title": 'A lista de arquivos e as marcas',
        "body": """Um cartão por arquivo nos seus álbuns Archive.

## Manter um arquivo no telefone
Use para um arquivo que você quer manter no telefone enquanto o resto do álbum é arquivado. Deslize para a **esquerda** para mantê-lo: ele fica em cinza e mostra **Not archiving**, e isso fica guardado. Deslize para a **direita** para incluí-lo de novo. Um arquivo em cinza com **too recent for your filter** está retido pelo filtro de idade.

## Marcas
• **Indicador de carregamento:** sendo conferido, enviado ou removido.
• **Marca de verificação verde:** confirmado na sua Cloud, ou removido.
• **Cruz vermelha:** não confirmado; ele fica no telefone.

## Mensagens
**Backing up...**, **Moving to Trash/Recycle Bin...**, **Removed from this phone.**, ou, em vermelho, por que um arquivo fica: **Could not check your Cloud**, **Not in your Cloud** ou **In your Cloud but the wrong size**.""",
        "en_hash": "4ef90825cb796912",
    },
    'archive-prompt': {
        "title": 'A pergunta: All files validated, ou só alguns validados',
        "body": """Depois da conferência:
• **All files validated:** todos os arquivos estão confirmados.
• **N files validated:** só esses estão confirmados; os demais ficam no telefone.
• **Nothing can be archived:** nenhum foi confirmado. **Continue** fecha a mensagem.

**Yes** pede ao Android para mover os arquivos confirmados para a lixeira. **No** não remove nada. Um álbum grande pode precisar de várias confirmações do Android (2.000 arquivos cada).""",
        "en_hash": "4155582f22850887",
    },
    'dialog-android-trash': {
        "title": 'A confirmação do próprio Android',
        "body": """O Android pede que você permita mover os arquivos para a lixeira. O texto varia conforme o telefone.

• O Android permite 2.000 arquivos por pedido, então um álbum grande é feito em lotes.
• Se você disser não, nada é removido.""",
        "en_hash": "ea86c62351435e6f",
    },
    'dialog-exit-warning': {
        "title": 'Files ready to Archive (o lembrete ao sair)',
        "body": """Aparece se você pressiona Voltar para sair enquanto há arquivos confirmados esperando para serem arquivados.

• **Archive now:** abre a aba Archive.
• **Leave:** fecha o app. Nada é removido.

Conta só os arquivos do OneDrive: só as cópias no OneDrive são confirmadas com antecedência. As outras Clouds são conferidas quando você pressiona **Check these files**. O lembrete não aparece se você pressiona Home ou fecha o app deslizando.""",
        "en_hash": "973610b6e4444771",
    },
}
