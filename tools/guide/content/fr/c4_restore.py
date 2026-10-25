TRANSLATIONS = {
    "_chapter": {
        "title": "L'onglet Restore",
        "intro": "Ramène des photos et vidéos depuis votre Cloud. Il ne fait que lire le Cloud ; rien n'y est modifié.",
        "en_hash": "e472722c1c5b626f",
    },
    "restore-overview": {
        "title": "Ce que Restore ramène",
        "body": """• **To restore :** un fichier optimisé sur le téléphone. Restaurer remet l'original en taille réelle à sa place.
• **To download :** un fichier qui n'est plus sur le téléphone. Le télécharger le remet dans son album.

Les fichiers restaurés reçoivent **Keep at full size**, afin de ne pas être optimisés ou archivés de nouveau tout de suite. Voir [[album-file-pin]].

## Quels Clouds
• **OneDrive :** chaque photo et vidéo de vos dossiers de sauvegarde, quelle que soit la personne qui les y a mises.
• **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2 :** les fichiers que GallerySync a envoyés depuis ce téléphone.
• **Google Photos :** on ne peut pas restaurer depuis ce Cloud.

## Où vont les fichiers
Dans le dossier d'où ils viennent. Les fichiers placés à la racine de **DCIM**, **Pictures** ou **Movies** retournent dans ce dossier. Si un dossier ne peut pas être écrit, les fichiers vont dans un dossier de **DCIM** nommé d'après l'album.

## Sans risque
Un fichier n'est remplacé qu'après l'arrivée complète du téléchargement et la vérification de sa taille. Si quoi que ce soit échoue, le fichier du téléphone reste tel quel.""",
        "en_hash": "1bc7e998be6fc106",
    },
    "restore-hero": {
        "title": "Albums to Restore et Files in this album",
        "body": """• **Liste d'albums :** **Albums to Restore**, avec le nombre d'albums qui ont quelque chose à ramener.
• **Dans un album :** flèche de retour, nom de l'album, et **Files in this album** avec le compte et ce que vous avez sélectionné.
• Faites glisser une carte vers la droite pour la sélectionner, vers la gauche pour la désélectionner ; touchez pour ouvrir. Cela fonctionne avec l'en-tête affiché ou masqué.
• **▲ Hide Details ▲** en bas de la carte la replie sur son titre, son nombre et son bouton principal ; **▼ Show Details ▼** ramène l'indication de balayage, **Refresh** / **Clear** et la ligne **Sort** / recherche du dossier. Les résultats et votre sélection restent visibles. L'onglet retient votre choix.

Un tiret signifie que la liste est encore en cours de chargement. **Checking your Cloud for more...** signifie qu'OneDrive est en cours de lecture. Un message rouge signifie qu'un dossier du Cloud n'a pas pu être joint.

La liste OneDrive est mémorisée, puis relue après dix minutes ou quand vous appuyez sur **Refresh**.""",
        "en_hash": "1099ec37091c62a1",
    },
    "restore-message-line": {
        "title": "Le message sous le nombre",
        "body": """• **Swipe right to select / left to deselect. Tap to open.** sur la liste d'albums.
• **Swipe right to select / left to deselect → Then press Restore.** dans un album.
• **Nothing in your Cloud is missing from this phone, so there is nothing to bring back.** quand la liste est vide.
• Après une restauration, combien de fichiers sont revenus et combien sont restés inchangés.""",
        "en_hash": "5b14979898ba9b25",
    },
    "restore-selected-summary": {
        "title": "N selected et l'espace à récupérer",
        "body": """Les fichiers sélectionnés dans tous les albums, et l'espace supplémentaire qu'ils occuperont sur votre téléphone. Assurez-vous d'avoir cet espace libre.""",
        "en_hash": "91898ed468f32d87",
    },
    "restore-buttons": {
        "title": "Refresh, Select all et Clear",
        "body": """• **Refresh** (liste d'albums) : relit la liste, OneDrive compris.
• **Select all** (dans un album) : sélectionne tous ses fichiers.
• **Clear :** retire toutes les sélections.

Grisés pendant qu'une restauration est en cours.""",
        "en_hash": "69730fbd93d70feb",
    },
    "restore-folders-list": {
        "title": "La liste d'albums",
        "body": """Chaque carte d'album affiche :
• Combien de fichiers peuvent revenir, et l'espace qu'il leur faut.
• Combien sont **to restore** et **to download**.
• Combien sont **already on this phone** (grisés à l'intérieur ; voir [[restore-greyed-files]]).
• Combien sont **selected here**, ou une coche quand tout l'album est sélectionné.

**Touchez** pour ouvrir. **Glissez vers la droite** pour sélectionner tout l'album, **vers la gauche** pour le désélectionner.""",
        "en_hash": "54ceae2583fe13a0",
    },
    "restore-files-list": {
        "title": "La liste de fichiers dans un album",
        "body": """Glissez vers la droite (ou touchez) pour sélectionner, vers la gauche pour désélectionner. Chaque fichier affiche l'une de ces mentions :
• **now · full size :** une copie optimisée, avec sa taille réduite sur le téléphone et la taille réelle dans le Cloud.
• **Available for download :** pas sur le téléphone.
• **Already on this phone :** grisé ; rien à faire.

Pendant le travail : **Working...** avec un pourcentage. Puis **Restored to full size**, **Back on this phone**, ou **Could not restore, your file is unchanged** avec la raison.""",
        "en_hash": "486e3275bedd2609",
    },
    "restore-greyed-files": {
        "title": "Pourquoi certains fichiers sont-ils grisés ?",
        "body": """Ils sont déjà sur le téléphone en taille réelle (même album, même nom, même taille), il n'y a donc rien à restaurer. Ils sont affichés pour que l'album corresponde à votre Cloud.

Un fichier de même nom mais de taille différente n'est pas proposé : ce peut être une modification, et Restore n'écrase jamais une modification.""",
        "en_hash": "02372472f6d53985",
    },
    "restore-action-bar": {
        "title": "Restore et Stop restoring",
        "body": """• **Restore :** ramène chaque fichier sélectionné, un par un. Apparaît dès que quelque chose est sélectionné.
• **Stop restoring :** arrête le lot. Les fichiers déjà restaurés restent restaurés.

Une barre de progression montre combien sont restaurés. Nécessite une connexion Internet et assez d'espace libre.""",
        "en_hash": "52b0a1302c2c28cb",
    },
    "restore-edited": {
        "title": "Une photo ou une vidéo que vous avez modifiée",
        "body": """Si vous avez modifié une copie optimisée et enregistré par-dessus, restaurer remplacerait votre modification ; l'application demande donc : **This photo has been edited since it was backed up. Overwrite it with the original?**

• **Keep my edits :** laisse le fichier tel quel.
• **Overwrite :** remet l'original ; votre modification est remplacée.

## Bon à savoir
La plupart des éditeurs enregistrent un nouveau fichier, qui n'est jamais touché. Un fichier modifié est sauvegardé comme un nouveau fichier avec **edited** et la date dans son nom ; l'original dans votre Cloud n'est jamais remplacé.""",
        "en_hash": "6292714bbfa760d3",
    },
}
