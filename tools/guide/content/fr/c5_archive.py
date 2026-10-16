TRANSLATIONS = {
    "_chapter": {
        "title": "L'onglet Archive",
        "intro": "Le seul endroit où des fichiers quittent votre téléphone. Uniquement les albums que vous réglez sur Archive, vérifiés d'abord auprès de votre Cloud, et seulement après votre toucher.",
        "en_hash": "99a3f217222adae4",
    },
    "archive-overview": {
        "title": "Comment fonctionne Archive, étape par étape",
        "body": """**Archive nécessite Android 11 ou plus récent.**

1. **Réglez un album sur Archive** dans l'onglet Albums et confirmez. Voir [[dialog-archive-confirm]].
2. **Appuyez sur Check these files.** Chaque fichier est vérifié auprès de son Cloud. Les fichiers qui n'y sont pas encore sont d'abord envoyés.
3. **Voyez le résultat :** une coche ou une croix rouge sur chaque fichier, et l'espace qui sera libéré.
4. **Dites Yes.** Android demande de déplacer les fichiers confirmés vers la corbeille du téléphone. Voir [[dialog-android-trash]].
5. **Videz la corbeille** quand vous êtes prêt. L'espace ne revient qu'à ce moment-là.

## Ce que « sûr » veut dire
Le Cloud doit confirmer, sur le moment, qu'il détient le fichier à la même taille. Si le Cloud ne peut pas être interrogé, rien n'est retiré. Pour une photo optimisée, c'est la taille de l'original qui est vérifiée.

## Quels Clouds
OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 et Backblaze B2. Pas Google Photos.

## Bon à savoir
• Archive exige votre présence : Android demande un toucher. Voir [[dialog-exit-warning]].
• Les fichiers ajoutés plus tard à un album Archive sont aussi couverts.
• Quand le dernier fichier part, l'album quitte la liste et son mode est oublié. S'il revient, il commence sur Off.""",
        "en_hash": "767ff53f0ad97065",
    },
    "archive-hero": {
        "title": "Files to Archive",
        "body": """**Files to Archive**, avec le nombre de fichiers qui seront archivés : dans chaque album Archive de la liste d'albums, ou dans l'album que vous avez ouvert. Les fichiers que vous avez gardés sur le téléphone ne sont pas comptés.

En dessous : vos albums Archive, et un rappel que les fichiers archivés peuvent être ramenés dans l'onglet **Restore**.""",
        "en_hash": "1b24c615ac7e7cb7",
    },
    "archive-folder-list": {
        "title": "Les albums Archive",
        "body": """Une carte par album Archive : combien de fichiers attendent et leur taille, plus tout ce qui est gardé, retenu par le filtre d'ancienneté, confirmé, ou non confirmé.

• Sur la liste d'albums, le bouton affiche **Check all albums** et vérifie chaque album.
• Touchez un album pour l'ouvrir ; **Check these files** ne vérifie alors que cet album.

Choisir un album ne retire rien et ne change aucun mode.""",
        "en_hash": "e1a713efaeea9168",
    },
    "archive-check-button": {
        "title": "Check these files, et ce qu'il indique",
        "body": """• **Check these files** (ou **Check all albums**) : lance la vérification.
• **Checking with your Cloud...** pendant que le Cloud est interrogé.
• La progression **Batch** pendant le retrait des fichiers.
• Combien de fichiers ont été retirés et l'espace libéré, ou **Nothing was removed.**

OneDrive est vérifié en listant le dossier de chaque album dans OneDrive (nom et taille). Dropbox, Google Drive, pCloud, Backblaze B2 et IDrive e2 sont interrogés pour chaque fichier.

« Libérer » s'entend une fois la corbeille vidée.""",
        "en_hash": "651062d720e9e712",
    },
    "archive-age-filter": {
        "title": "Only show files older than…",
        "body": """Limite cette vérification aux fichiers au moins aussi anciens : de **1 hour** à **1 year**, ou **All**. Les fichiers plus récents sont grisés et laissés pour une vérification ultérieure.

Il commence à votre valeur par défaut de Settings. Le modifier ici ne vaut que pour cette visite.""",
        "en_hash": "3d5d116005b80474",
    },
    "archive-empty": {
        "title": "Quand il n'y a rien à archiver",
        "body": """• **Aucun album n'est réglé sur Archive :** réglez-en un dans l'onglet Albums.
• **Un zéro :** tout ce qui est dans vos albums Archive a déjà quitté le téléphone.
• **This version of Android has no media trash:** Archive nécessite Android 11 ou plus récent.""",
        "en_hash": "ebf6a50a3d8d1d39",
    },
    "archive-file-list": {
        "title": "La liste de fichiers et ses marques",
        "body": """Une carte par fichier de vos albums Archive.

## Garder un fichier sur le téléphone
À utiliser pour un fichier que vous voulez garder sur le téléphone pendant que le reste de l'album est archivé. Glissez **vers la gauche** pour le garder : il se grise et affiche **Not archiving**, et c'est mémorisé. Glissez **vers la droite** pour le réinclure. Un fichier grisé avec **too recent for your filter** est retenu par le filtre d'ancienneté.

## Marques
• **Indicateur tournant :** en cours de vérification, d'envoi ou de retrait.
• **Coche verte :** confirmé dans votre Cloud, ou retiré.
• **Croix rouge :** non confirmé ; il reste sur le téléphone.

## Messages
**Backing up...**, **Moving to Trash/Recycle Bin...**, **Removed from this phone.**, ou, en rouge, pourquoi un fichier reste : **Could not check your Cloud**, **Not in your Cloud**, ou **In your Cloud but the wrong size**.""",
        "en_hash": "4ef90825cb796912",
    },
    "archive-prompt": {
        "title": "La question : All files validated, ou seulement certains fichiers",
        "body": """Après la vérification :
• **All files validated :** chaque fichier est confirmé.
• **N files validated :** seuls ceux-là sont confirmés ; les autres restent sur le téléphone.
• **Nothing can be archived :** aucun n'a été confirmé. **Continue** ferme le message.

**Yes** demande à Android de déplacer les fichiers confirmés vers la corbeille. **No** ne retire rien. Un grand album peut demander plusieurs confirmations Android (2 000 fichiers chacune).""",
        "en_hash": "4155582f22850887",
    },
    "dialog-android-trash": {
        "title": "La confirmation propre à Android",
        "body": """Android vous demande d'autoriser le déplacement des fichiers vers la corbeille. Sa formulation varie selon le téléphone.

• Android autorise 2 000 fichiers par demande, donc un grand album est traité par lots.
• Si vous refusez, rien n'est retiré.""",
        "en_hash": "ea86c62351435e6f",
    },
    "dialog-exit-warning": {
        "title": "Files ready to Archive (le rappel au départ)",
        "body": """Apparaît si vous appuyez sur Retour pour quitter alors que des fichiers sont confirmés et attendent d'être archivés.

• **Archive now :** ouvre l'onglet Archive.
• **Leave :** ferme l'application. Rien n'est retiré.

Ne compte que les fichiers OneDrive : seules les copies OneDrive sont confirmées à l'avance. Les autres Clouds sont vérifiés quand vous appuyez sur **Check these files**. Le rappel n'apparaît pas si vous appuyez sur Accueil ou si vous balayez l'application pour la fermer.""",
        "en_hash": "973610b6e4444771",
    },
}
