TRANSLATIONS = {
    "_chapter": {
        "title": "L'onglet Settings",
        "intro": "Une page défilante en bandes vertes : General, Backup, Albums, Sync, Camera Sync, Restore, Archive et Help & Feedback. Chaque bande a un (?).",
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
        "title": "General",
        "body": """Le **How To Guide**, **Appearance** et **Use mobile data**. **Language** est la dernière ligne de la page.""",
        "en_hash": "b8038bbcadfcd8b3",
    },
    "settings-how-to-guide-card": {
        "title": "La carte How To Guide",
        "body": """Ouvre ce guide. **Read more in the How To Guide** dans n'importe quel **(?)** l'ouvre à ce sujet. Nécessite une connexion Internet ; s'il ne se charge pas, utilisez **Open in browser**.""",
        "en_hash": "a7dec55c7b2218a4",
    },
    "settings-language": {
        "title": "Language",
        "body": """La langue que vous avez choisie sur l'écran Bienvenue. Touchez-la pour la changer. Le How To Guide suit ce choix ; les écrans de l'application sont pour l'instant en anglais.""",
        "en_hash": "e1597813fc836e98",
    },
    "settings-appearance": {
        "title": "Appearance",
        "body": """• **System** (par défaut) : suit le réglage clair ou sombre de votre téléphone.
• **Light** ou **Dark** : toujours cet aspect.

Le guide et les pages de politique suivent le réglage de votre téléphone.""",
        "en_hash": "b2ab8ff47282bc06",
    },
    "settings-mobile-data": {
        "title": "Use mobile data",
        "body": """• **Off** (par défaut) : **Wi-Fi only.**
• **On :** **Syncs on Wi-Fi and mobile data.**

Concerne les sauvegardes automatiques et **Sync now**. Laissez-le désactivé sauf si votre forfait de données est illimité.""",
        "en_hash": "e8860c60b1961fee",
    },
    "settings-section-backup": {
        "title": "Backup",
        "body": """• **Clouds :** connectez ou déconnectez chaque Cloud. Voir [[settings-account]].
• **Where each folder goes :** quel Cloud reçoit chaque dossier. Voir [[settings-destination]].

Un Cloud est marqué **main (free)** : le premier Cloud que vous avez sélectionné. Il est toujours gratuit. Les autres Clouds demandent **Pro** : gratuit pendant 30 jours (**Start 30-day free trial**), puis un achat unique (**Unlock Pro**). Rien n'est facturé automatiquement.""",
        "en_hash": "0db12d3ebcb633de",
    },
    "settings-account": {
        "title": "Vos Clouds et Sign out",
        "body": """Chaque Cloud affiche son compte (ou **Not connected**) avec **Sign out**, **Connect** ou **Enter keys**.

## Backup folder
Sous chaque Cloud connecté (sauf Google Photos) : le dossier où vont ses sauvegardes, avec **Change**. Voir [[dialog-destination]].

## Sign out
Un avertissement d'abord : les sauvegardes vers ce Cloud s'arrêtent, rien n'est supprimé. Si ce Cloud détient des fichiers qui ne sont pas en taille réelle sur le téléphone (optimisés ou archivés), il demande :
• **Restore them to the phone first :** les ramène tous, puis vous déconnecte.
• **Leave them there :** vous déconnecte.
• **Cancel :** reste connecté.

Les dossiers restent associés à ce Cloud et attendent que vous vous reconnectiez. Pour les envoyer ailleurs, changez **Where each folder goes**.""",
        "en_hash": "788b2237bf1a173f",
    },
    "settings-destination": {
        "title": "Where each folder goes",
        "body": """Chaque dossier de premier niveau (DCIM, Pictures, Movies), le Cloud où il va, et l'endroit dans ce Cloud.

• Touchez le Cloud pour le changer. C'est possible seulement avec plus d'un Cloud et avec Pro ou l'essai.
• Seuls les nouveaux fichiers suivent un changement. Les fichiers déjà envoyés restent où ils sont.
• Un dossier associé à un Cloud supplémentaire sans Pro n'envoie rien, et le dit.""",
        "en_hash": "22adac22e50fbdf0",
    },
    "dialog-destination": {
        "title": "Where new backups go (la boîte de dialogue Change)",
        "body": """Ouverte par **Change** à côté du **Backup folder** d'un Cloud, dans Settings ou sur sa carte pendant la configuration.

• Saisissez le chemin du dossier, ou pour OneDrive utilisez **Browse...**.
• **Reset to default :** retour à **Samsung Gallery/DCIM** (OneDrive) ou **GallerySync** (autres Clouds).
• **Use this folder** enregistre ; **Cancel** ne change rien.

Seuls les nouveaux fichiers vont dans le nouveau dossier. Les fichiers déjà sauvegardés restent où ils sont. OneDrive continue de vérifier les dossiers précédents, donc rien n'est envoyé deux fois.""",
        "en_hash": "910e0378a5a8b208",
    },
    "dialog-folder-picker": {
        "title": "Choose a folder (le sélecteur OneDrive)",
        "body": """Ouvert par **Browse...** pour OneDrive. Liste vos dossiers OneDrive : touchez pour ouvrir, **Up one folder** pour remonter, **Use this folder** pour choisir, **New folder** pour en créer un.""",
        "en_hash": "f45e3349352eea44",
    },
    "settings-section-albums": {
        "title": "Albums",
        "body": """Quels dossiers GallerySync surveille, et ce qui arrive aux copies Cloud quand vous supprimez depuis le téléphone. Les nouveaux albums commencent toujours sur **Off**.""",
        "en_hash": "dad7459947f2d8e1",
    },
    "settings-folders": {
        "title": "Folders to back up",
        "body": """Les dossiers dans lesquels GallerySync peut regarder.

• **Add a folder :** ouvre le sélecteur de dossier d'Android.
• **Remove :** sélectionnez des dossiers, appuyez sur **Remove**, et confirmez.

Rien en dehors de ces dossiers n'est analysé ni modifié. Retirer un dossier ne fait rien perdre : remettez-le et tout revient.""",
        "en_hash": "3789c04bb52baef5",
    },
    "dialog-remove-folder": {
        "title": "Arrêter de surveiller ce dossier ?",
        "body": """**Remove** arrête de surveiller les dossiers sélectionnés ; **Cancel** ne change rien.

Rien de ce qui est déjà sauvegardé n'est perdu. Remettre le dossier restaure ses albums et son historique, sans rien envoyer deux fois.""",
        "en_hash": "5b84d0c4a6ec8a9a",
    },
    "settings-deletion": {
        "title": "Quand vous supprimez une photo ou une vidéo de ce téléphone",
        "body": """• **Leave the Cloud copy** (par défaut) : rien n'est jamais retiré de votre Cloud.
• **Ask me about the Cloud copy :** quand des fichiers quittent votre téléphone, une fenêtre demande quoi faire de leurs copies Cloud. Si les notifications sont autorisées, vous êtes aussi prévenu peu après la suppression. Rien n'est retiré sans votre accord. Voir [[deleted-files-window]].

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
• Si plus de la moitié de vos fichiers sauvegardés semblent disparaître d'un coup, rien n'est proposé ; c'est traité comme une analyse défectueuse plutôt que comme de vraies suppressions.
• Retour fait la même chose que **Decide later**.""",
        "en_hash": "4cf138e07d6f0844",
    },
    "dialog-remove-from-onedrive": {
        "title": "Retirer des fichiers d'OneDrive ? (la confirmation)",
        "body": """Affiche combien de fichiers et leur taille. Ils vont dans la corbeille OneDrive, que GallerySync ne vide jamais.

**Remove from OneDrive** poursuit ; **Cancel** ne décide rien. Tout fichier revenu sur le téléphone est laissé tranquille.""",
        "en_hash": "a5707f1ea8aad403",
    },
    "settings-section-sync": {
        "title": "Sync",
        "body": """Comment les photos et vidéos des albums **Sync** sont optimisées. Les fichiers ne changent qu'après vérification par le Cloud. Fonctionne avec OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 et Backblaze B2.""",
        "en_hash": "1147f4e959ac06bc",
    },
    "settings-optimise-photos": {
        "title": "Optimise photos",
        "body": """**Off** par défaut. Quand c'est activé, les photos des albums Sync sont remplacées par des copies plus petites : environ 2048 pixels sur le grand côté, soit environ un dixième de la taille. Elles restent dans votre galerie avec un petit badge de nuage. Les originaux restent dans le Cloud.

Les photos déjà assez petites, et les fichiers réglés sur **Keep at full size**, sont laissés tranquilles. **Mode** définit quand cela s'exécute. Voir [[settings-optimise-mode]].""",
        "en_hash": "dee027d401742ca5",
    },
    "settings-optimise-video": {
        "title": "Optimise video",
        "body": """**Off** par défaut. Quand c'est activé, affiche **Mode**, **Older than** et **Compression**.

Les vidéos des albums Sync sont réencodées en plus petit. Rien n'est jamais coupé. Les originaux restent dans le Cloud.

## Quelles vidéos
Dans un album Sync, verified in the Cloud, plus anciennes que votre réglage, dans un dossier auquel vous avez donné accès, et pas déjà optimisées.

## Quand
• **Automatic :** après les sauvegardes, uniquement pendant la charge.
• **Manual :** quand vous appuyez sur **Sync now**.""",
        "en_hash": "09cf14026b288ef6",
    },
    "settings-optimise-mode": {
        "title": "Mode : Automatic ou Manual",
        "body": """• **Automatic** (par défaut) : les fichiers des albums Sync sont optimisés dès que le Cloud les confirme. La vidéo attend le chargeur.
• **Manual :** rien ne se passe tant que vous n'appuyez pas sur **Sync now** dans l'onglet Albums.

Les photos en dehors des dossiers auxquels vous avez donné accès demandent la confirmation d'Android ; elles attendent donc que vous ouvriez l'application ou appuyiez sur **Sync now**.""",
        "en_hash": "2d55959b2e538398",
    },
    "settings-video-age": {
        "title": "Older than (vidéo)",
        "body": """Quel âge une vidéo doit avoir avant d'être optimisée : **Straight away**, **1 hour**, **12 hours**, **1 day** ou **1 week**. La sauvegarde n'est pas retardée.

**Straight away** inclut les clips filmés aujourd'hui ; en modifier un repart de la copie plus petite jusqu'à ce que vous la restauriez.""",
        "en_hash": "b69444389e56afe8",
    },
    "settings-video-quality": {
        "title": "Compression (vidéo)",
        "body": """• **High compression, 480p :** les fichiers les plus petits, environ 85 % d'économie.
• **Medium compression, 720p :** environ 75 % d'économie.
• **Low compression, 1080p :** environ 50 % d'économie.

Chiffres typiques ; les résultats varient.

## Après un renforcement
L'application demande **Re-optimise existing videos?** **Yes** permet de retenter les vidéos qui étaient trop petites pour être réduites auparavant. **No** les laisse. Les vidéos déjà optimisées ne sont pas touchées.""",
        "en_hash": "b68338f9be222883",
    },
    "settings-section-restore": {
        "title": "Restore (Settings)",
        "body": """Un interrupteur pour l'onglet Restore : **Show empty folders**.""",
        "en_hash": "58571cb29a96c3ed",
    },
    "settings-show-empty-folders": {
        "title": "Show empty folders",
        "body": """**Off** par défaut. Quand c'est activé, l'onglet Restore liste chaque dossier OneDrive, y compris ceux qui n'ont rien à ramener.""",
        "en_hash": "44bada9612e80a0d",
    },
    "settings-section-camera": {
        "title": "Camera Sync (Settings)",
        "body": """**Special settings for Camera**, désactivé par défaut. Quand c'est activé :
• **Camera folder :** le dossier où votre appareil photo enregistre. Choisissez-le dans la liste, ou **Other…**.
• **Select Photos/Videos older than**, **Photos**, **Videos :** valeurs par défaut des commandes d'optimisation de ce dossier ([[album-camera-optimise]]).
• **Video compression :** pour les vidéos optimisées là.

Le Camera folder n'a pas de mode **Sync**, et rien n'y est optimisé tant que vous n'appuyez pas sur **Sync now** dedans. Les interrupteurs Optimise ci-dessus ne l'affectent pas.""",
        "en_hash": "d596a9c99a8de79d",
    },
    "settings-section-archive": {
        "title": "Archive (Settings)",
        "body": """• **Only show files older than :** là où commence le filtre de l'onglet Archive. Voir [[settings-archive-default-age]].
• **Notify when files are ready to archive :** désactivé par défaut. Voir [[settings-archive-notify]].""",
        "en_hash": "7b4561ddc010537d",
    },
    "settings-archive-default-age": {
        "title": "Only show files older than (Settings)",
        "body": """Là où commence le filtre d'ancienneté de l'onglet Archive : **1 hour**, **1 day**, **1 week**, **1 month**, **1 year** ou **All** (par défaut). Le modifier dans l'onglet Archive ne vaut que pour cette visite. Voir [[archive-age-filter]].""",
        "en_hash": "215ff23fa51a5fca",
    },
    "settings-archive-notify": {
        "title": "Notify when files are ready to archive",
        "body": """**Off** par défaut. Quand c'est activé, vous êtes notifié une fois par nouveau lot de fichiers prêts à être archivés. Fichiers OneDrive uniquement.

L'onglet Albums et le rappel de départ l'indiquent aussi, donc vous ne dépendez jamais des seules notifications. Si les notifications sont bloquées, l'interrupteur le dit.""",
        "en_hash": "a0b5e2290fe1eaae",
    },
    "settings-about-cards": {
        "title": "Help & Feedback, et les cartes de liens en bas",
        "body": """## Help & Feedback
• **Rate us on Play Store :** ouvre la fiche de GallerySync.
• **Bug Report :** où écrire, et **Copy details** pour la version de l'application, le modèle du téléphone et la version d'Android.

## En bas
• **Privacy Policy.**
• **Delete Account Info :** comment se déconnecter, retirer l'accès de l'application et effacer ses données.
• **Contact Info :** l'adresse, avec **Copy address**.

Les pages se chargent depuis Internet. Si l'une ne se charge pas, utilisez **Open in browser**.""",
        "en_hash": "8a454188917d719a",
    },
}
