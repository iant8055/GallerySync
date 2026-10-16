TRANSLATIONS = {
    "_chapter": {
        "title": "L'onglet Settings",
        "intro": "Une page défilante en bandes vertes : Général, Backup, Albums, Sync, Sync de l'appareil photo, Restore, Archive et Aide et commentaires. Chaque bande a un (?).",
        "en_hash": "fdc210cbde6172d8",
    },
    "settings-overview": {
        "title": "Comment est organisé l'onglet Settings",
        "body": """• Les changements prennent effet immédiatement. Il n'y a pas de bouton Save.
• Rien ici ne retire un fichier de votre téléphone. Seul Archive le fait.
• Settings et la première configuration sont séparés. Voir [[setup-and-settings]].""",
        "en_hash": "523b8f790e8db56b",
    },
    "settings-section-general": {
        "title": "Général",
        "body": """Le **Mode d'emploi**, **Apparence** et **Utiliser les données mobiles**. **Langue** est la dernière ligne de la page.""",
        "en_hash": "b8038bbcadfcd8b3",
    },
    "settings-how-to-guide-card": {
        "title": "La carte Mode d'emploi",
        "body": """Ouvre ce guide. **En savoir plus dans le Mode d'emploi** dans n'importe quel **(?)** l'ouvre à ce sujet. Nécessite une connexion Internet ; s'il ne se charge pas, utilisez **Ouvrir dans le navigateur**.""",
        "en_hash": "a7dec55c7b2218a4",
    },
    "settings-language": {
        "title": "Langue",
        "body": """La langue que vous avez choisie sur l'écran Bienvenue. Touchez-la pour la changer. Le Mode d'emploi suit ce choix ; les écrans de l'application sont pour l'instant en anglais.""",
        "en_hash": "e1597813fc836e98",
    },
    "settings-appearance": {
        "title": "Apparence",
        "body": """• **Système** (par défaut) : suit le réglage clair ou sombre de votre téléphone.
• **Clair** ou **Sombre** : toujours cet aspect.

Le guide et les pages de politique suivent aussi ce réglage.""",
        "en_hash": "373e3bf77ff126af",
    },
    "settings-mobile-data": {
        "title": "Utiliser les données mobiles",
        "body": """• **Off** (par défaut) : **Wi-Fi uniquement.**
• **On :** **Synchronise en Wi-Fi et en données mobiles.**

Concerne les sauvegardes automatiques et **Sync now**. Laissez-le désactivé sauf si votre forfait de données est illimité.""",
        "en_hash": "e8860c60b1961fee",
    },
    "settings-section-backup": {
        "title": "Backup",
        "body": """• **Clouds :** connectez ou déconnectez chaque Cloud. Voir [[settings-account]].
• **Où va chaque dossier :** quel Cloud reçoit chaque dossier. Voir [[settings-destination]].

Un Cloud est marqué **principal (gratuit)** : le premier Cloud que vous avez sélectionné. Il est toujours gratuit. Les autres Clouds demandent **Pro** : gratuit pendant 30 jours (**Commencer l'essai gratuit de 30 jours**), puis un achat unique (**Débloquer Pro**). Rien n'est facturé automatiquement.""",
        "en_hash": "0db12d3ebcb633de",
    },
    "settings-account": {
        "title": "Vos Clouds et Se déconnecter",
        "body": """Chaque Cloud affiche son compte (ou **Non connecté**) avec **Se déconnecter**, **Connecter** ou **Saisir les clés**.

## Dossier de sauvegarde
Sous chaque Cloud connecté (sauf Google Photos) : le dossier où vont ses sauvegardes, avec **Modifier**. Voir [[dialog-destination]].

## Se déconnecter
Un avertissement d'abord : les sauvegardes vers ce Cloud s'arrêtent, rien n'est supprimé. Si ce Cloud détient des fichiers qui ne sont pas en taille réelle sur le téléphone (optimisés ou archivés), il demande :
• **Les restaurer d'abord sur le téléphone :** les ramène tous, puis vous déconnecte.
• **Les laisser là-bas :** vous déconnecte.
• **Annuler :** reste connecté.

Les dossiers restent associés à ce Cloud et attendent que vous vous reconnectiez. Pour les envoyer ailleurs, changez **Où va chaque dossier**.""",
        "en_hash": "788b2237bf1a173f",
    },
    "settings-destination": {
        "title": "Où va chaque dossier",
        "body": """Chaque dossier de premier niveau (DCIM, Pictures, Movies), le Cloud où il va, et l'endroit dans ce Cloud.

• Touchez le Cloud pour le changer. C'est possible seulement avec plus d'un Cloud et avec Pro ou l'essai.
• Seuls les nouveaux fichiers suivent un changement. Les fichiers déjà envoyés restent où ils sont.
• Un dossier associé à un Cloud supplémentaire sans Pro n'envoie rien, et le dit.""",
        "en_hash": "22adac22e50fbdf0",
    },
    "dialog-destination": {
        "title": "Où vont les nouvelles sauvegardes (la boîte de dialogue Modifier)",
        "body": """Ouverte par **Modifier** à côté du **Dossier de sauvegarde** d'un Cloud, dans Settings ou sur sa carte pendant la configuration.

• Saisissez le chemin du dossier, ou pour OneDrive utilisez **Parcourir…**.
• **Rétablir par défaut :** retour à **Samsung Gallery/DCIM** (OneDrive) ou **GallerySync** (autres Clouds).
• **Utiliser ce dossier** enregistre ; **Annuler** ne change rien.

Seuls les nouveaux fichiers vont dans le nouveau dossier. Les fichiers déjà sauvegardés restent où ils sont. OneDrive continue de vérifier les dossiers précédents, donc rien n'est envoyé deux fois.""",
        "en_hash": "910e0378a5a8b208",
    },
    "dialog-folder-picker": {
        "title": "Choisir un dossier (le sélecteur OneDrive)",
        "body": """Ouvert par **Parcourir…** pour OneDrive. Liste vos dossiers OneDrive : touchez pour ouvrir, **Dossier parent** pour remonter, **Utiliser ce dossier** pour choisir, **Nouveau dossier** pour en créer un.""",
        "en_hash": "f45e3349352eea44",
    },
    "settings-section-albums": {
        "title": "Albums",
        "body": """Quels dossiers GallerySync surveille, et ce qui arrive aux copies Cloud quand vous supprimez depuis le téléphone. Les nouveaux albums commencent toujours sur **Off**.""",
        "en_hash": "dad7459947f2d8e1",
    },
    "settings-folders": {
        "title": "Dossiers à sauvegarder",
        "body": """Les dossiers dans lesquels GallerySync peut regarder.

• **Ajouter un dossier :** ouvre le sélecteur de dossier d'Android.
• **Retirer :** sélectionnez des dossiers, appuyez sur **Retirer**, et confirmez.

Rien en dehors de ces dossiers n'est analysé ni modifié. Retirer un dossier ne fait rien perdre : remettez-le et tout revient.""",
        "en_hash": "3789c04bb52baef5",
    },
    "dialog-remove-folder": {
        "title": "Ne plus surveiller ce dossier ?",
        "body": """**Retirer** arrête de surveiller les dossiers sélectionnés ; **Annuler** ne change rien.

Rien de ce qui est déjà sauvegardé n'est perdu. Remettre le dossier restaure ses albums et son historique, sans rien envoyer deux fois.""",
        "en_hash": "5b84d0c4a6ec8a9a",
    },
    "settings-deletion": {
        "title": "Quand vous supprimez une photo ou une vidéo de ce téléphone",
        "body": """• **Laisser la copie dans le Cloud** (par défaut) : rien n'est jamais retiré de votre Cloud.
• **Me demander pour la copie dans le Cloud :** quand des fichiers quittent votre téléphone, une fenêtre demande quoi faire de leurs copies Cloud. Si les notifications sont autorisées, vous êtes aussi prévenu peu après la suppression. Rien n'est retiré sans votre accord. Voir [[deleted-files-window]].

OneDrive uniquement pour l'instant. Les copies dans les autres Clouds ne sont jamais retirées.""",
        "en_hash": "5fce34ecb857a3f6",
    },
    "deleted-files-window": {
        "title": "Fichiers supprimés de votre téléphone (la fenêtre qui s'ouvre avec l'application)",
        "body": """S'ouvre avant l'onglet Albums quand des fichiers ont quitté votre téléphone, sous **Ask**. Rien n'est sélectionné au départ.

## Fenêtre 1 : une copie est dans OneDrive
Glissez vers la droite (ou touchez) pour sélectionner les fichiers dont la copie OneDrive doit partir.
• **Remove N from OneDrive :** demande confirmation, puis déplace ces copies vers la corbeille OneDrive. Les fichiers non sélectionnés restent dans OneDrive. Voir [[dialog-remove-from-onedrive]].
• **Keep all in OneDrive.**
• **Decide later.**

## Fenêtre 2 : aucune copie trouvée
Ces fichiers sont encore dans la corbeille de votre téléphone.
• **Back up N to OneDrive :** envoie les fichiers sélectionnés depuis la corbeille. Rien n'est supprimé.
• **Leave all in the trash.**
• **Decide later.**

## Bon à savoir
• Les fichiers non décidés restent dans la liste jusqu'à ce que vous décidiez.
• Non listés : les fichiers que Archive a retirés, les photos modifiées sur place, et les fichiers supprimés définitivement sans copie Cloud.
• Si plus de la moitié de vos fichiers sauvegardés semblent disparaître d'un coup, rien n'est proposé ; c'est traité comme une analyse défectueuse plutôt que comme de vraies suppressions.
• Retour fait la même chose que **Decide later**.""",
        "en_hash": "4cf138e07d6f0844",
    },
    "dialog-remove-from-onedrive": {
        "title": "Retirer des fichiers d'OneDrive ? (la confirmation)",
        "body": """Affiche combien de fichiers et leur taille. Ils vont dans la corbeille OneDrive, que GallerySync ne vide jamais.

**Remove from OneDrive** poursuit ; **Cancel** ne décide rien. Tout fichier revenu sur le téléphone est laissé tranquille.""",
        "en_hash": "a5707f1ea8aad403",
    },
    "settings-section-sync": {
        "title": "Sync",
        "body": """Comment les photos et vidéos des albums **Sync** sont optimisées. Les fichiers ne changent qu'après vérification par le Cloud. Fonctionne avec OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 et Backblaze B2.""",
        "en_hash": "1147f4e959ac06bc",
    },
    "settings-optimise-photos": {
        "title": "Optimiser les photos",
        "body": """**Off** par défaut. Quand c'est activé, les photos des albums Sync sont remplacées par des copies plus petites : environ 2048 pixels sur le grand côté, soit environ un dixième de la taille. Elles restent dans votre galerie avec un petit badge de nuage. Les originaux restent dans le Cloud.

Les photos déjà assez petites, et les fichiers réglés sur **Keep at full size**, sont laissés tranquilles. **Mode** définit quand cela s'exécute. Voir [[settings-optimise-mode]].""",
        "en_hash": "dee027d401742ca5",
    },
    "settings-optimise-video": {
        "title": "Optimiser les vidéos",
        "body": """**Off** par défaut. Quand c'est activé, affiche **Mode**, **Plus de** et **Compression**.

Les vidéos des albums Sync sont réencodées en plus petit. Rien n'est jamais coupé. Les originaux restent dans le Cloud.

## Quelles vidéos
Dans un album Sync, verified in the Cloud, plus anciennes que votre réglage, dans un dossier auquel vous avez donné accès, et pas déjà optimisées.

## Quand
• **Automatique :** après les sauvegardes, uniquement pendant la charge.
• **Manuel :** quand vous appuyez sur **Sync now**.""",
        "en_hash": "09cf14026b288ef6",
    },
    "settings-optimise-mode": {
        "title": "Mode : Automatique ou Manuel",
        "body": """• **Automatique** (par défaut) : les fichiers des albums Sync sont optimisés dès que le Cloud les confirme. La vidéo attend le chargeur.
• **Manuel :** rien ne se passe tant que vous n'appuyez pas sur **Sync now** dans l'onglet Albums.

Les photos en dehors des dossiers auxquels vous avez donné accès demandent la confirmation d'Android ; elles attendent donc que vous ouvriez l'application ou appuyiez sur **Sync now**.""",
        "en_hash": "2d55959b2e538398",
    },
    "settings-video-age": {
        "title": "Plus de (vidéo)",
        "body": """Quel âge une vidéo doit avoir avant d'être optimisée : **Tout de suite**, **1 heure**, **12 heures**, **1 jour** ou **1 semaine**. La sauvegarde n'est pas retardée.

**Tout de suite** inclut les clips filmés aujourd'hui ; en modifier un repart de la copie plus petite jusqu'à ce que vous la restauriez.""",
        "en_hash": "b69444389e56afe8",
    },
    "settings-video-quality": {
        "title": "Compression (vidéo)",
        "body": """• **Compression élevée — 480p :** les fichiers les plus petits, environ 85 % d'économie.
• **Compression moyenne — 720p :** environ 75 % d'économie.
• **Compression faible — 1080p :** environ 50 % d'économie.

Chiffres typiques ; les résultats varient.

## Après un renforcement
L'application demande **Optimiser de nouveau les vidéos existantes ?** **Oui** permet de retenter les vidéos qui étaient trop petites pour être réduites auparavant. **Non** les laisse. Les vidéos déjà optimisées ne sont pas touchées.""",
        "en_hash": "b68338f9be222883",
    },
    "settings-section-restore": {
        "title": "Restore (Settings)",
        "body": """Un interrupteur pour l'onglet Restore : **Afficher les dossiers vides**.""",
        "en_hash": "58571cb29a96c3ed",
    },
    "settings-show-empty-folders": {
        "title": "Afficher les dossiers vides",
        "body": """**Off** par défaut. Quand c'est activé, l'onglet Restore liste chaque dossier OneDrive, y compris ceux qui n'ont rien à ramener.""",
        "en_hash": "44bada9612e80a0d",
    },
    "settings-section-camera": {
        "title": "Sync de l'appareil photo (Settings)",
        "body": """**Réglages spéciaux pour l'appareil photo**, désactivé par défaut. Quand c'est activé :
• **Dossier de l'appareil photo :** le dossier où votre appareil photo enregistre. Choisissez-le dans la liste, ou **Autre…**.
• **Select Photos/Videos older than**, **Photos**, **Videos :** valeurs par défaut des commandes d'optimisation de ce dossier ([[album-camera-optimise]]).
• **Compression vidéo :** pour les vidéos optimisées là.

Le dossier de l'appareil photo n'a pas de mode **Sync**, et rien n'y est optimisé tant que vous n'appuyez pas sur **Sync now** dedans. Les interrupteurs d'optimisation ci-dessus ne l'affectent pas.""",
        "en_hash": "d596a9c99a8de79d",
    },
    "settings-section-archive": {
        "title": "Archive (Settings)",
        "body": """• **Only show files older than :** là où commence le filtre de l'onglet Archive. Voir [[settings-archive-default-age]].
• **Prévenir quand des fichiers sont prêts pour Archive :** désactivé par défaut. Voir [[settings-archive-notify]].""",
        "en_hash": "7b4561ddc010537d",
    },
    "settings-archive-default-age": {
        "title": "Only show files older than (Settings)",
        "body": """Là où commence le filtre d'ancienneté de l'onglet Archive : **1 hour**, **1 day**, **1 week**, **1 month**, **1 year** ou **All** (par défaut). Le modifier dans l'onglet Archive ne vaut que pour cette visite. Voir [[archive-age-filter]].""",
        "en_hash": "215ff23fa51a5fca",
    },
    "settings-archive-notify": {
        "title": "Prévenir quand des fichiers sont prêts pour Archive",
        "body": """**Off** par défaut. Quand c'est activé, vous êtes notifié une fois par nouveau lot de fichiers prêts à être archivés. Fichiers OneDrive uniquement.

L'onglet Albums et le rappel de départ l'indiquent aussi, donc vous ne dépendez jamais des seules notifications. Si les notifications sont bloquées, l'interrupteur le dit.""",
        "en_hash": "a0b5e2290fe1eaae",
    },
    "settings-about-cards": {
        "title": "Aide et commentaires, et les cartes de liens en bas",
        "body": """## Aide et commentaires
• **Notez-nous sur le Play Store :** ouvre la fiche de GallerySync.
• **Signaler un bug :** où écrire, et **Copier les détails** pour la version de l'application, le modèle du téléphone et la version d'Android.

## En bas
• **Politique de confidentialité.**
• **Supprimer les données du compte :** comment se déconnecter, retirer l'accès de l'application et effacer ses données.
• **Contact :** l'adresse, avec **Copier l'adresse**.

Les pages se chargent depuis Internet. Si l'une ne se charge pas, utilisez **Ouvrir dans le navigateur**.""",
        "en_hash": "8a454188917d719a",
    },
}
