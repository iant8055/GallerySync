TRANSLATIONS = {
    "_chapter": {
        "title": 'Configuração inicial',
        "intro": 'Uma configuração guiada roda na primeira vez que você abre o GallerySync.',
        "en_hash": "a04b336dd67e7d4e",
    },
    'setup-overview': {
        "title": 'Como funciona a configuração',
        "body": """**Próximo** avança, **Voltar** volta. Uma tela só deixa você continuar quando tem o que precisa.

## As etapas
1. Boas-vindas, um tour rápido e o que será configurado. Veja [[setup-welcome-tour]].
2. Escolha suas Nuvens e entre nelas. Veja [[setup-cloud]].
3. Permita o acesso às suas fotos e depois escolha as pastas. Veja [[setup-choose-folders]].
4. Escolha um Plano de Backup.
5. Configurações de otimização (somente nos planos 2 e 3).
6. Quando começar.
7. O primeiro backup: verificar a Nuvem, enviar e, por fim, otimizar se o seu plano incluir isso.

## Bom saber
• O primeiro backup pode levar horas. Ele continua se você sair do app.
• O app abre na tela de andamento até o primeiro backup terminar.
• As respostas da configuração valem só para o primeiro backup. Veja [[setup-and-settings]].""",
        "en_hash": "a398aa1dfcdb2e54",
    },
    'setup-welcome-tour': {
        "title": 'Boas-vindas, o tour e o que será configurado',
        "body": """• **Boas-vindas:** escolha seu idioma para continuar. Você pode mudar depois em Settings.
• **O tour:** cinco telas, uma para cada aba e uma para Ajuda.
• **O que vamos configurar:** entrar na sua Nuvem, permitir o acesso às fotos, escolher as pastas e permitir alterações nessas pastas.

O link na parte de baixo dessa tela abre este guia de configuração. É a única parte do guia disponível durante a configuração.""",
        "en_hash": "db32458beedb19eb",
    },
    'setup-cloud': {
        "title": 'Armazenamento na Nuvem: escolher serviços, entrar e escolher para onde vão os backups',
        "body": """Selecione as Nuvens que você usa e pressione **Próximo**. Nada vem selecionado no início.

## Sua primeira Nuvem é grátis
A primeira Nuvem que você seleciona vira a sua Nuvem grátis, mostrada como **main (free)** em Settings. Cada Nuvem adicional exige o **Pro**: grátis por 30 dias, depois uma compra única. Nada é cobrado automaticamente. Se o teste terminar, as Nuvens extras ficam pausadas e a sua Nuvem grátis continua funcionando.

Comece o teste com **Start 30-day free trial** em Settings, em Backup; ele aparece quando uma segunda Nuvem está conectada. **Unlock Pro** faz a compra.

## O que cada Nuvem pode fazer
• **OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 e Backblaze B2:** Backup, Sync, Archive e Restore.
• **Google Photos:** somente backup.

## Uma observação sobre duplicatas
O OneDrive é a única Nuvem cujo conteúdo é verificado antes de qualquer envio. As outras Nuvens podem receber uma segunda cópia de um arquivo que já está lá.

## Como entrar
Cada Nuvem tem o seu próprio cartão **Conectar**, numerado. O **Próximo** espera até que todas estejam conectadas.
• **OneDrive, Google Photos, Google Drive, Dropbox, pCloud:** entre na página do próprio provedor. Sua senha nunca é digitada no GallerySync. No Dropbox, permita leitura e escrita, ou o Restore não vai funcionar.
• **IDrive e2, Backblaze B2:** insira as chaves do console deles. Veja [[cloud-key-backblaze]] e [[cloud-key-idrive]].

## Pasta de backup
Depois que uma Nuvem é conectada, o cartão dela mostra a **Pasta de backup**, com **Alterar** para escolher outra (menos no Google Photos, que não tem pastas). O OneDrive começa em **Samsung Gallery / DCIM**, a pasta que a sincronização do OneDrive da própria Samsung usa, então as fotos que já foram sincronizadas lá não são enviadas duas vezes. As outras Nuvens começam em **GallerySync**.{{full: Veja [[dialog-destination]].}}""",
        "en_hash": "a90bbd0e1eecaa86",
    },
    'cloud-key-backblaze': {
        "title": 'Conectar o Backblaze B2: as chaves e onde encontrá-las',
        "body": """O GallerySync só escreve dentro da sua pasta de backup no bucket (**GallerySync**, a menos que você escolha outra), então um bucket que você já usa é seguro para isso.

## No Backblaze
1. **Um bucket privado:** use um que você já tenha, ou **Buckets → Create a Bucket**.
2. **O endpoint dele:** aparece nos detalhes do bucket, por exemplo **s3.us-east-005.backblazeb2.com**.
3. **Uma chave de aplicativo:** **Application Keys → Add a New Application Key**, só para esse bucket, **Read and Write**. Não a chave Master.
4. **Copie as duas metades:** o **keyID** e a **applicationKey**. A applicationKey aparece uma única vez.

## No GallerySync
• **Endpoint:** sem o https://.
• **Região:** deixe em branco.
• **Nome do bucket:** exatamente como no Backblaze.
• **ID da chave de acesso:** o keyID (começa com 005).
• **Chave de acesso secreta:** a applicationKey. Toque em **Mostrar** para conferir.

## Se as chaves forem rejeitadas
A mensagem diz o que conferir. Quase sempre é um erro de digitação: **O** e zero, **I** e **l**, **J** e **j**. Colar evita isso.""",
        "en_hash": "5b41f1e8fffbcd4e",
    },
    'cloud-key-idrive': {
        "title": 'Conectar o IDrive e2: as chaves e onde encontrá-las',
        "body": """O GallerySync só escreve dentro da sua pasta de backup no bucket (**GallerySync**, a menos que você escolha outra), então um bucket que você já usa é seguro para isso.

## No IDrive e2
1. **Um bucket privado.**
2. **O endpoint da região**, por exemplo **s3.us-west-1.idrivee2.com**. Não o endereço com o nome do bucket na frente.
3. **O nome da região**, por exemplo **us-west-1**.
4. **Uma chave de acesso:** um **ID da chave de acesso** e uma **chave de acesso secreta**. A chave secreta aparece uma única vez.

## No GallerySync
Insira o **Endpoint** (sem o https://), a **Região**, o **Nome do bucket**, o **ID da chave de acesso** e a **Chave de acesso secreta**. Toque em **Mostrar** para conferir a chave secreta.

## Se as chaves forem rejeitadas
A mensagem diz o que conferir. Quase sempre é um erro de digitação: **O** e zero, **I** e **l**. Colar evita isso.""",
        "en_hash": "18ca3e144c0c9aa9",
    },
    'setup-search-permission': {
        "title": 'Deixe o GallerySync procurar neste telefone',
        "body": """**Dar permissão de busca** abre a tela de permissão do Android. É aqui que você deixa o GallerySync ver as fotos e os vídeos do seu telefone.

## Bom saber
Escolha **Permitir tudo**. Se você escolher **Selecionar fotos**, só essas fotos poderão passar por backup.""",
        "en_hash": "9dfa956289f7ed75",
    },
    'setup-choose-folders': {
        "title": 'Escolha as pastas para o backup',
        "body": """Selecione as pastas para o backup e pressione **Próximo**. A maioria das fotos fica em **DCIM**.

## Para qual Nuvem vai cada pasta
Com mais de uma Nuvem, cada pasta mostra **Vai para**. Escolha uma Nuvem para cada pasta selecionada. Os arquivos já enviados nunca mudam de lugar; só os arquivos novos seguem uma mudança.{{full: Veja [[settings-destination]].}}

## O Android pergunta de novo para cada pasta
O Android mostra a própria tela de permissão de pasta. Se algo der errado:
• **Nenhum acesso foi dado:** **Tentar de novo** ou **Pular** a pasta.
• **Foi escolhida uma pasta dentro dela:** **Escolher … inteira** para corrigir, ou **Ficar só com …** para manter a escolha menor.
• **Foi escolhida uma pasta diferente:** tente de novo ou pule.

Nada fora das pastas que você escolheu é analisado ou alterado.""",
        "en_hash": "409ac88ad1359229",
    },
    'setup-backup-plan': {
        "title": 'Escolha seu Plano de Backup',
        "body": """O que fazer com as fotos e os vídeos que já estão no seu telefone. **Nenhum plano remove nada.**

• **1. Fazer backup de tudo o que ainda não está na Nuvem.** Nada muda no telefone. Vem selecionado quando você chega.
• **2. Fazer backup e depois otimizar todos os arquivos do telefone.** A maior economia de espaço.
• **3. Fazer backup e depois otimizar só os arquivos que este backup enviou.**
• **4. Verificar a Nuvem, mas não enviar nada.** Depois você escolhe o modo de cada álbum na aba Albums.

## Bom saber
Este primeiro backup acontece uma vez só e roda sem olhar o modo. Depois disso todo álbum fica em **Off**, o que está correto: a partir daí você escolhe o modo de cada álbum na aba Albums.{{full: Veja [[faq-albums-off-after-backup]].}}""",
        "en_hash": "80f55e21398db3f8",
    },
    'setup-optimise': {
        "title": 'Configurações de otimização durante a configuração',
        "body": """Só para os planos 2 e 3.

• **Otimizar fotos** e **Otimizar vídeos:** ligado ou desligado.
• **Nível de compressão de vídeo:** **Alta** (480p, o menor), **Média** (720p) ou **Baixa** (1080p).
• Cada um mostra uma economia estimada, com uma linha de **Economia total estimada**. Os resultados reais variam.""",
        "en_hash": "d3ff8b60c0328479",
    },
    'setup-ready': {
        "title": 'Pronto para o backup',
        "body": """Mostra quanto será enviado para backup e, se houver otimização, quanto espaço você vai economizar.

• **Agora mesmo:** começa na hora.
• **Atraso:** **1 h, 2 h, 4 h, 8 h, 12 h** ou **24 h**.

## Bom saber
• Um backup com atraso só começa com o telefone na tomada e pode começar um pouco depois do horário.
• O app fica na tela de andamento até o primeiro backup terminar.""",
        "en_hash": "90436741649f85c6",
    },
    'setup-progress': {
        "title": 'Andamento do backup',
        "body": """## Aguardando o início
Uma contagem regressiva roda até o backup começar, mesmo com o app fechado, desde que o telefone esteja na tomada. **SINCRONIZAR AGORA** começa na hora.

## Etapa 1: Verificando sua Nuvem
Cada arquivo é comparado com o que a sua Nuvem já tem, por nome e tamanho, com uma contagem em andamento. Nada é enviado. Em geral leva um ou dois minutos.

## Etapa 2: Enviando
O anel mostra o andamento geral. Abaixo dele: a Nuvem que está recebendo e a contagem dela. As outras Nuvens que estão esperando aparecem como **Pendentes**.

## Etapa 3: Otimizando
**Otimizando fotos** e depois **Otimizando vídeos**, se o seu plano incluir.

## Botões
• **Fechar:** fecha o app; o backup continua.
• **Concluir:** aparece quando tudo termina, com um resumo do que foi verificado, do que já estava na Nuvem e do que foi enviado.
• **Cancelar:** pergunta **Parar o backup?** Os arquivos já enviados continuam enviados; nada é apagado.""",
        "en_hash": "625986b0864a86c8",
    },
    'setup-and-settings': {
        "title": 'A configuração e Settings são separadas',
        "body": """A configuração e Settings guardam as próprias respostas. A configuração nunca lê de Settings, e Settings nunca lê da configuração. A lista de pastas da configuração começa vazia mesmo que Settings já liste pastas, e as respostas da configuração não aparecem em Settings.

A configuração nunca define o modo de um álbum. Só você define, na aba Albums.""",
        "en_hash": "06ce026c4915e1fd",
    },
}
