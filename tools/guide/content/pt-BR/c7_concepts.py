TRANSLATIONS = {
    "_chapter": {
        "title": 'Como funciona, em português simples',
        "intro": 'As ideias por trás dos botões.',
        "en_hash": "a50822757f585a7f",
    },
    'modes-in-depth': {
        "title": 'Os quatro modos',
        "body": """Cada álbum tem um modo, definido com o botão no cartão dele.

• **Off:** este álbum não é cuidado: nada é enviado nem alterado. Todo álbum começa aqui.
• **Backup:** copia para a sua Cloud; o telefone nunca é tocado.
• **Sync:** faz o backup e depois otimiza, se a otimização estiver ligada em Settings.
• **Archive:** faz o backup, verifica e depois move os arquivos para a lixeira do telefone depois do seu toque. O único modo que remove arquivos.

## Quais Clouds
O Backup funciona com todas as Clouds. O Sync e o Archive exigem OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 ou Backblaze B2, não o Google Photos.

## Camera folder
Se você definir uma Camera folder em Settings, ela oferece Off, Backup e Archive, com controles de otimização próprios no lugar do Sync. Veja [[album-camera-optimise]].

## Qual escolher
• Manter como está, mas protegido: **Backup**.
• Economizar espaço e continuar vendo: **Sync**.
• Álbuns antigos que você quer tirar do telefone: **Archive**.
• Em dúvida: **Off** ou **Backup**.

## Bom saber
• Só você define os modos. A única exceção é juntar álbuns duplicados, que define **Off**. Veja [[album-merge-warning]].
• Mudar para Archive pede que você confirme. Mudar para outro modo interrompe novas remoções.""",
        "en_hash": "43d293213ae2b8f7",
    },
    'how-verification-works': {
        "title": 'Como a verificação funciona',
        "body": """Antes de qualquer coisa ser otimizada ou removida, a Cloud é consultada, naquele momento, sobre se tem uma boa cópia.

## O teste
• **OneDrive:** lista a pasta do álbum; o arquivo precisa ter o mesmo nome e tamanho.
• **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2:** consultados sobre o arquivo exato; ele precisa estar presente, fora da lixeira e com o mesmo tamanho. Para uma foto otimizada, o tamanho do original.

## Quando
• No início do primeiro backup, para que só os arquivos que faltam sejam enviados.
• Toda vez que você abre a aba Albums ou pressiona Rescan (OneDrive).
• Quando você confere os arquivos na aba Archive, e de novo logo antes da remoção.
• Logo antes de um arquivo ser otimizado.

## Três respostas
• **Verified:** a Cloud tem o arquivo.
• **Não está lá, ou o tamanho está errado:** o arquivo fica no telefone.
• **Não foi possível verificar:** tratado como desconhecido. Nada é removido nem otimizado.

## Bom saber
Uma checagem de tamanho não detecta todo tipo de dano. Abra a sua Cloud de vez em quando e confira algumas fotos.""",
        "en_hash": "3a19869102cce663",
    },
    'how-optimising-works': {
        "title": 'Como a otimização funciona',
        "body": """Otimizar troca a cópia do telefone por uma menor, para que o telefone pare de encher enquanto tudo continua na sua galeria.

## Fotos
Reduzidas a cerca de 2048 pixels no lado maior, aproximadamente um décimo do tamanho. Mesmo nome, lugar, data e local, com um pequeno selo de nuvem.

## Vídeo
Recodificado em **480p** (High compression), **720p** (Medium) ou **1080p** (Low). Nunca cortado. O Automatic espera o carregador; o Manual roda no **Sync now**.

## Regras
• Só em álbuns **Sync**.
• Só depois que a Cloud confirma o original com o mesmo tamanho.
• Um arquivo que não ficaria menor é deixado em paz.

## Como ter o original de volta
Use a aba **Restore**. Restaure antes de editar se quiser todos os detalhes.""",
        "en_hash": "1c7d13215d15f27e",
    },
    'where-deleted-files-go': {
        "title": 'Para onde vão os arquivos removidos',
        "body": """Nada é apagado permanentemente.

## No seu telefone
O Archive move os arquivos para a lixeira do Android por cerca de 30 dias:
• Samsung: a **Lixeira do Samsung Gallery**.
• Outros telefones: a **Lixeira** do app **Arquivos**.

Os arquivos na lixeira continuam ocupando todo o espaço deles até você esvaziá-la.

## Na sua Cloud
Em **Ask**, as cópias que você escolhe remover vão para a **lixeira do OneDrive**. Nada é removido das outras Clouds. O app nunca esvazia nenhuma lixeira.""",
        "en_hash": "ab2cd9530770d041",
    },
    'background-work': {
        "title": 'Quando as coisas acontecem',
        "body": """• **Fazer backup:** pouco depois de aparecerem fotos novas, e a cada seis horas. Só no Wi-Fi, a menos que os dados móveis estejam ligados; não quando a bateria ou o armazenamento estão baixos. O **Sync now** roda na hora.
• **Optimising photos:** no Automatic, assim que a Cloud confirma um arquivo em um álbum Sync. No Manual, no **Sync now**.
• **Optimising video:** no Automatic, depois dos backups, com o aparelho carregando. No Manual, no **Sync now**.
• **Arquivos apagados:** em **Ask**, você é avisado pouco depois de apagar um arquivo que tem uma cópia na Cloud (não enquanto os backups estão pausados).
• **Arquivar:** só quando você diz Yes na aba Archive.
• **Restaurar:** só quando você pressiona Restore.

## Fechar o app
Voltar ou Home não tem problema: o trabalho continua. Fechar o app deslizando-o para fora dos apps recentes pode parar o trabalho em segundo plano até você abri-lo de novo.""",
        "en_hash": "b52b93819f999605",
    },
    'where-your-data-lives': {
        "title": 'Onde ficam os seus dados',
        "body": """• **Fotos e vídeos:** vão direto entre o seu telefone e a sua própria Cloud, criptografados. Não existem servidores do GallerySync.
• **Logins e chaves:** ficam criptografados no seu telefone. As senhas são digitadas na página do provedor, nunca no app.
• **Os registros e as configurações do app:** armazenamento privado no seu telefone.
• **Nada é coletado:** sem análises, relatórios de falhas nem anúncios.

Detalhes: **Privacy Policy** e **Delete Account Info** em Settings.""",
        "en_hash": "989af3b026b90273",
    },
    'numbers-explained': {
        "title": 'Por que os números podem diferir do seu app de galeria',
        "body": """O GallerySync conta só os arquivos que estão agora no telefone, nas pastas que você escolheu, e não os da lixeira. Sua galeria pode contar itens só da nuvem ou agrupar pastas de outro jeito. Os dois estão certos; eles contam coisas diferentes.""",
        "en_hash": "54fab5cd25e07a35",
    },
}
