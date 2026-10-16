TRANSLATIONS = {
    "_chapter": {
        "title": "Questions et problèmes",
        "intro": "Questions courantes et ce qui se passe en général.",
        "en_hash": "01001cde06eb2f03",
    },
    "faq-sync-now-greyed": {
        "title": "Sync now est grisé",
        "body": """Il n'y a rien à envoyer ni à optimiser : tout est déjà envoyé, ou aucun album n'est réglé sur Backup, Sync ou Archive. Vérifiez les totaux de mode dans l'en-tête.""",
        "en_hash": "d76ec512e56c7203",
    },
    "faq-albums-off-after-backup": {
        "title": "Mes albums indiquent Off après la première sauvegarde",
        "body": """C'est normal. La première sauvegarde envoie tout quel que soit le mode, et la configuration ne règle jamais les modes. Choisissez un mode pour chaque album dans l'onglet Albums.""",
        "en_hash": "39db6d0a503fb274",
    },
    "faq-not-checked-yet": {
        "title": "Un album indique Not checked against OneDrive yet",
        "body": """OneDrive n'a pas encore été interrogé. Attendez un instant ou appuyez sur **Rescan**. **Could not reach OneDrive** signifie que la connexion a échoué : vérifiez que vous êtes en ligne et connecté, puis faites **Rescan**.

Les albums d'autres Clouds affichent plutôt **sent to** ce Cloud. Voir [[albums-list]].""",
        "en_hash": "52048b6e4b7bcf97",
    },
    "faq-onedrive-full": {
        "title": "La sauvegarde s'est arrêtée parce que mon Cloud est plein",
        "body": """**stopped: your Cloud is full.** Rien n'est perdu. Libérez de la place ou ajoutez du stockage, puis appuyez sur **Sync now**.""",
        "en_hash": "70148dcb9cb4986c",
    },
    "faq-space-not-freed": {
        "title": "J'ai archivé un album mais mon téléphone n'a pas plus de place",
        "body": """Les fichiers archivés sont dans la corbeille du téléphone, et occupent toujours de l'espace. Videz la corbeille quand vous êtes sûr qu'ils sont en sécurité dans votre Cloud. Voir [[where-deleted-files-go]].""",
        "en_hash": "2a38df823e61c58d",
    },
    "faq-album-vanished": {
        "title": "Un album a disparu de ma galerie",
        "body": """S'il était réglé sur **Archive**, ses fichiers sont dans la corbeille du téléphone. Restaurez-les depuis la corbeille, ou utilisez l'onglet **Restore**. Il revient comme un nouvel album réglé sur Off.""",
        "en_hash": "94390b0205e85c29",
    },
    "faq-photo-looks-small": {
        "title": "Une photo semble plus petite ou de moindre qualité qu'avant",
        "body": """Elle a été optimisée (cherchez le petit badge de nuage). L'original est dans votre Cloud : utilisez l'onglet **Restore**.""",
        "en_hash": "4e1a6a3f710360db",
    },
    "faq-restore-empty": {
        "title": "L'onglet Restore dit qu'il n'y a rien à ramener",
        "body": """Rien n'a été optimisé ni archivé, et (pour OneDrive) chaque photo de vos dossiers de sauvegarde est déjà sur le téléphone. C'est normal.""",
        "en_hash": "e0720600e98ca0e2",
    },
    "faq-backup-stops-when-closed": {
        "title": "Les sauvegardes semblent s'arrêter quand je ferme l'application",
        "body": """Retour ou Accueil, ça va. Balayer l'application hors des applications récentes peut arrêter le travail en arrière-plan : rouvrez l'application et il reprend sans rien envoyer deux fois.""",
        "en_hash": "266c98c91b9f897f",
    },
    "faq-wrong-account": {
        "title": "Je me suis connecté à un Cloud avec le mauvais compte",
        "body": """**Settings → Backup → Se déconnecter** à côté de ce Cloud, choisissez **Les laisser là-bas**, puis reconnectez-vous avec le bon compte. Les fichiers déjà envoyés restent dans le premier compte. Voir [[settings-account]].""",
        "en_hash": "a033d25dffca0535",
    },
    "faq-folder-waiting": {
        "title": "Je me suis déconnecté d'un Cloud et ses dossiers n'envoient plus",
        "body": """C'est normal. Les dossiers attendent, en sécurité sur votre téléphone, que vous vous reconnectiez. Pour les envoyer ailleurs, changez **Où va chaque dossier**. Voir [[settings-destination]].""",
        "en_hash": "286529a95f8327e7",
    },
    "faq-keys-rejected": {
        "title": "IDrive e2 ou Backblaze B2 dit que mes clés ont été rejetées",
        "body": """Généralement une faute de frappe. Vérifiez le point de terminaison (sans https://), la région, le bucket et les deux clés ; touchez **Afficher** sur le secret. Attention à **O** contre zéro, **I** contre **l**, **J** contre **j**. Le copier-coller évite cela. Voir [[cloud-key-backblaze]] et [[cloud-key-idrive]].""",
        "en_hash": "e0b15236b548a066",
    },
    "faq-could-not-check": {
        "title": "Archive dit qu'il n'a pas pu vérifier mon Cloud",
        "body": """Le Cloud n'a pas répondu, donc rien n'a été retiré. Vérifiez que vous êtes en ligne et connecté, puis appuyez de nouveau sur **Check these files**.""",
        "en_hash": "96fcc47fc7eaf6d4",
    },
    "faq-reconnect-cloud": {
        "title": "Restore dit de reconnecter ce Cloud",
        "body": """Le Cloud a refusé que le fichier soit lu. Reconnectez-vous dans Settings. Pour Dropbox, autorisez la lecture des fichiers ; pour IDrive e2 et Backblaze B2, utilisez une clé qui peut lire le bucket. Le fichier de votre téléphone n'a pas été modifié.""",
        "en_hash": "23bf7817d447a418",
    },
    "albums-permission": {
        "title": "GallerySync ne voit pas mes photos",
        "body": """Un message en haut de l'onglet Albums signifie que GallerySync n'a pas l'accès complet à vos photos :
• **GallerySync needs access to your photos :** aucun accès n'a été accordé, ou il a été retiré.
• **Only some photos are shared :** l'accès a été limité avec **Sélectionner des photos**, donc les sauvegardes sont incomplètes.

Appuyez sur **Grant access** et choisissez **Tout autoriser**. Si aucune invitation n'apparaît : dans les **Paramètres → Applications → GallerySync → Autorisations → Photos et vidéos** du téléphone.""",
        "en_hash": "d1499b80f267c6f8",
    },
    "contact-and-privacy": {
        "title": "Contact, confidentialité et suppression de vos données",
        "body": """En bas de Settings :
• **Contact :** l'adresse, avec un bouton de copie.
• **Politique de confidentialité :** vos fichiers vont uniquement vers votre propre Cloud ; rien n'est collecté.
• **Supprimer les données du compte :** comment se déconnecter, retirer l'accès et effacer les données. Vos fichiers Cloud restent.

Pour signaler un problème, utilisez **Signaler un bug** juste au-dessus.""",
        "en_hash": "3e29c01351fc7319",
    },
}
