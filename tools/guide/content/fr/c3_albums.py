TRANSLATIONS = {
    "_chapter": {
        "title": "L'onglet Albums",
        "intro": "L'endroit où vous choisissez le mode de chaque album et lancez les sauvegardes.",
        "en_hash": "7c39f69f07321c58",
    },
    "albums-overview": {
        "title": "Comment est organisé l'onglet Albums",
        "body": """• **En-tête vert :** boutons de mode, totaux et commandes de sauvegarde.
• **Ligne d'état :** affichée pendant qu'une action s'exécute ou vient de se terminer.
• **Liste d'albums :** une carte par album, de A à Z. Deux colonnes sur un grand écran.

À chaque ouverture de l'onglet, il regarde le téléphone et interroge à nouveau OneDrive (**Rescan** affiche **Checking...**). Les autres Clouds sont interrogés au sujet d'un fichier juste avant qu'il soit optimisé ou retiré.

Regarder, filtrer et faire défiler ne modifient jamais un fichier. Seul le choix d'un mode le fait.""",
        "en_hash": "3c7f69542cac04f4",
    },
    "albums-new": {
        "title": "Nouveaux albums",
        "body": """Quand un nouvel album apparaît, une fenêtre dit **1 new Album is waiting for you to choose a mode**.
• **Show new Albums :** n'affiche que les nouveaux. **Show all Albums** ramène les autres.
• **Dismiss :** ferme le message.

Les nouveaux albums portent une étiquette **New** et commencent toujours sur **Off**. Un nouvel album ne peut jamais commencer en Archive.""",
        "en_hash": "8f8d4b55c34a1c4d",
    },
    "albums-filter": {
        "title": "All Albums et les quatre boutons de mode",
        "body": """• Touchez **Backup**, **Sync**, **Archive** ou **Off** pour n'afficher que les albums de ce mode.
• Touchez **All Albums**, ou le même bouton à nouveau, pour tous les afficher.

Filtrer ne change que ce que vous voyez. Pour changer un mode, utilisez le bouton de la carte de l'album. Voir [[modes-in-depth]].""",
        "en_hash": "d4865c5cf08d3857",
    },
    "albums-totals": {
        "title": "Album Mode Count",
        "body": """**Album Mode Count :** le total pour chaque mode. Affiché quand **All Albums** est sélectionné. Les albums sous **Off** sont ceux sur lesquels vous n'avez pas encore décidé.""",
        "en_hash": "61afcd38a3bee22c",
    },
    "albums-all-backed-up": {
        "title": "Tout ici est sauvegardé",
        "body": """Affiché quand chaque album visible contient des fichiers et qu'aucun n'attend d'être envoyé. Cela vient du registre propre à l'application ; la réponse du Cloud lui-même est la dernière ligne de chaque carte d'album.""",
        "en_hash": "79b43044d6005ada",
    },
    "albums-optimised-line": {
        "title": "Optimised and Saved",
        "body": """Avec **Sync** sélectionné : combien de fichiers des albums Sync ont été optimisés, et l'espace économisé.

Les originaux restent dans le Cloud. **Restore** les ramène. Voir [[how-optimising-works]].""",
        "en_hash": "1fbc295f2322e4d0",
    },
    "albums-archive-lines": {
        "title": "Files archived et Scheduled to leave this phone",
        "body": """Avec **Archive** sélectionné :
• **N files archived · X in your Cloud :** les fichiers qui ont quitté le téléphone, et l'espace que leurs copies occupent dans le Cloud.
• **N Scheduled to leave this phone :** les fichiers encore sur le téléphone dans des albums Archive, sans compter ceux que vous avez choisi de garder. **Nothing left to archive** quand il n'y en a aucun.

Rien n'est retiré ici. Le retrait se fait dans l'onglet Archive. Voir [[archive-overview]].""",
        "en_hash": "00b01adb4a7cf037",
    },
    "albums-run-controls": {
        "title": "Sync now, Rescan, Pause, Resume et Stop",
        "body": """## Quand rien ne s'exécute
• **Sync now :** sauvegarde maintenant. Si un des interrupteurs d'optimisation de Settings est activé avec son **Mode** sur **Manuel**, il optimise aussi ensuite (voir [[settings-optimise-mode]]). Grisé quand il n'y a rien à envoyer ni à optimiser.
• **Rescan :** regarde le téléphone et interroge à nouveau OneDrive.

## Pendant une sauvegarde
• La progression s'affiche comme **Syncing** avec un pourcentage (en volume de données, pas en nombre de fichiers).
• **Pause :** suspend toutes les sauvegardes jusqu'à **Resume**.
• **Stop :** termine cette exécution. Les sauvegardes automatiques continuent.

## Bon à savoir
• **Sync now** n'attend pas le chargeur, mais suit toujours votre réglage Wi-Fi. Voir [[settings-mobile-data]].
• Il sauvegarde chaque album réglé sur **Backup**, **Sync** ou **Archive**, mais n'optimise que les fichiers des albums **Sync**.""",
        "en_hash": "846b4240970f2d4a",
    },
    "albums-status-line": {
        "title": "La ligne d'état sous la carte",
        "body": """• **Pendant l'envoi :** le nombre de fichiers, le fichier en cours d'envoi et sa progression.
• **Pendant la préparation :** **Scanning...** ou **No permission to read media.**
• **Après une exécution :** un résumé de ce qui s'applique : **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (nouvel essai plus tard), **still to go**, ou **stopped:** avec la raison.""",
        "en_hash": "78b2b8f3d6801853",
    },
    "albums-list": {
        "title": "L'onglet Albums",
        "body": """## En-tête
• Touchez **Backup**, **Sync**, **Archive** ou **Off** pour n'afficher que les albums de ce mode. Touchez **All Albums** pour tous les afficher à nouveau.
• La ligne sous les boutons donne le total pour chaque mode.
• **▲ Hide Details ▲** en bas de la carte masque la ligne du total de mode ; **▼ Show Details ▼** la ramène. **All Albums**, les quatre boutons de mode ainsi que **Sync now** / **Rescan** restent. L'onglet retient votre choix.

## Cartes d'album
• **Nom, nombre de fichiers et taille** de l'album sur votre téléphone.
• **Deuxième ligne** (seulement si nécessaire) : combien de fichiers sont optimisés, gardés en taille réelle, en attente ou en échec.
• **Ligne Cloud :** **verified in OneDrive** signifie qu'OneDrive a confirmé les fichiers à la bonne taille. C'est vérifié à chaque ouverture de l'onglet. Les autres Clouds affichent **sent to** ce Cloud.
• **Bouton de mode** (à droite) : touchez-le pour changer le mode de l'album. Archive demande d'abord une confirmation. Si le Cloud de l'album ne peut pas faire ce mode, la carte le dit.
• **Touchez la carte** pour voir les fichiers de l'album. Voir [[album-detail]].""",
        "en_hash": "6f8771afe616ceed",
    },
    "album-detail": {
        "title": "La liste des fichiers d'un album",
        "body": """S'ouvre quand vous touchez une carte d'album.

• **En-tête :** flèche de retour, nom de l'album, mode et nombre de fichiers, **Backs up to** (le Cloud et le dossier où vont les nouveaux fichiers, plus tout autre Cloud qui en détient déjà), les compteurs ([[album-file-status]]), **Sort by** ([[album-file-sort]]) et **Keep at full size** ([[album-file-pin]]).
• **▲ Hide Details ▲** en bas de l'en-tête le replie sur le nom de l'album, son mode et le nombre de fichiers ; **▼ Show Details ▼** ramène le reste. La page retient votre choix.
• **Fichiers :** une carte par fichier avec sa taille, sa date et ses marques. Un fichier optimisé affiche sa taille réduite sur le téléphone.
• **Camera folder** (si choisi dans Settings) : commandes d'optimisation supplémentaires. Voir [[album-camera-optimise]].

Pas de miniatures ni d'aperçus. Utilisez votre application de galerie pour regarder les photos.""",
        "en_hash": "47ed510631cea71a",
    },
    "album-file-status": {
        "title": "Marques et compteurs des fichiers d'un album",
        "body": """• **✓ backed up :** dans le Cloud, taille réelle sur le téléphone.
• **✓ backed up · optimised :** dans le Cloud ; le téléphone a une copie plus petite.
• **⟳ pending :** en attente d'envoi.
• **✗ failed :** n'a pas pu être envoyé après plusieurs essais.

## Retry failed
**Retry** remet les fichiers en échec dans la file et lance une sauvegarde. Cela ne retire rien.""",
        "en_hash": "a1305dbfcdea48b5",
    },
    "album-file-sort": {
        "title": "Trier les fichiers d'un album",
        "body": """• **Name :** de A à Z.
• **Date :** les plus récents d'abord. Un fichier restauré a la date de sa restauration.
• **Status :** failed, puis pending, puis optimised, puis backed up.

La liste s'ouvre à chaque fois dans l'ordre des noms. Trier ne change rien aux fichiers.""",
        "en_hash": "030835fb143a638a",
    },
    "album-file-pin": {
        "title": "Keep at full size",
        "body": """Une case à cocher à côté des fichiers que **Restore** a ramenés. Tant qu'elle est sélectionnée, GallerySync laisse ce fichier tranquille :
• Dans un album **Sync**, il n'est pas optimisé.
• Dans un album **Archive**, il n'est pas retiré.
• Dans **Backup** ou **Off**, rien ne change, mais le choix est mémorisé.

Restore la sélectionne automatiquement. Désélectionnez-la pour que le fichier suive à nouveau le mode de son album.""",
        "en_hash": "34b89b8b5829a956",
    },
    "album-camera-optimise": {
        "title": "Optimiser les anciennes photos et vidéos de votre Camera folder",
        "body": """Seul le **Dossier de l'appareil photo** que vous avez choisi dans Settings sous **Sync de l'appareil photo** ([[settings-section-camera]]) a ces commandes.

## Sélectionner des fichiers
• **Select Photos/Videos older than :** de **1 day** à **1 year**, ou **All**.
• **Photos** et **Videos :** quels types sélectionner.
• Glissez un fichier vers la droite pour le sélectionner, vers la gauche pour le désélectionner. La carte indique combien sont sélectionnés et l'économie estimée.

## Boutons
• **Sync now :** sauvegarde tout ce qui attend dans le dossier, puis optimise uniquement les fichiers sélectionnés.
• **Rescan :** vérifie à nouveau le Cloud.

## Bon à savoir
• Seuls les fichiers qu'OneDrive a confirmés en taille réelle peuvent être sélectionnés.
• Rien n'est optimisé avant que vous appuyiez sur **Sync now**. Les nouvelles photos restent telles quelles.
• Le Camera folder n'a pas de mode **Sync**, et les interrupteurs d'optimisation de Settings ne l'affectent pas.
• Quitter avec des choix modifiés demande si vous voulez en faire vos valeurs par défaut.""",
        "en_hash": "f06a5a7ce89e99c4",
    },
    "album-camera-select": {
        "title": "Sélectionner des fichiers dans votre Camera folder",
        "body": """Affiche combien de fichiers sont sélectionnés et l'espace estimé économisé, ou **Nothing selected**.

• **Photos**, **Videos** et l'ancienneté sélectionnent chaque fichier correspondant.
• Glissez vers la droite pour sélectionner, vers la gauche pour désélectionner.
• **Sync now** n'optimise que les fichiers sélectionnés.

Un fichier grisé ne peut pas être sélectionné : il n'est pas encore confirmé dans OneDrive, ou il est déjà optimisé. Voir [[album-camera-optimise]].""",
        "en_hash": "9904773f638bae7b",
    },
    "album-merge-warning": {
        "title": "Noms d'album en double détectés",
        "body": """Un même dossier peut se retrouver enregistré sous deux orthographes qui ne diffèrent que par les majuscules. GallerySync le repère, les fusionne en un seul album et règle son mode sur **Off** pour que vous puissiez choisir à nouveau. **Dismiss** ferme la carte.

C'est la seule fois où l'application définit un mode, et elle ne peut définir que **Off**.""",
        "en_hash": "559da2287e55a253",
    },
    "dialog-archive-confirm": {
        "title": "Passer cet album en Archive ?",
        "body": """Affiché quand vous choisissez **Archive** :
• Archive vérifie que tous les fichiers sont dans le Cloud.
• Les fichiers archivés vont dans la corbeille de votre téléphone.
• Videz la corbeille pour libérer l'espace.

**Archive** confirme ; **Cancel** ne change rien.

## Bon à savoir
Vous confirmez une seule fois. Les fichiers ajoutés plus tard à l'album sont aussi couverts, jusqu'à ce que l'album soit vide. Android demande une fois par lot de 2 000 fichiers au plus. Voir [[dialog-android-trash]].""",
        "en_hash": "ac36f7c88ce4bc76",
    },
}
