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
        "en_hash": "e3efef2f9212aaf3",
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
        "body": """• Touchez **Backup**, **Optimize**, **Archive** ou **Off** pour n'afficher que les albums de ce mode.
• Touchez **All Albums**, ou le même bouton à nouveau, pour tous les afficher.

Filtrer ne change que ce que vous voyez. Pour changer un mode, utilisez le bouton de la carte de l'album. Voir [[modes-in-depth]].""",
        "en_hash": "fce05e6ad448f530",
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
    "albums-optimized-line": {
        "title": "Optimized and Saved",
        "body": """Avec **Optimize** sélectionné : combien de fichiers des albums Optimize ont été optimisés, et l'espace économisé.

Les originaux restent dans le Cloud. **Restore** les ramène. Voir [[how-optimizing-works]].""",
        "en_hash": "280df5abf38d7971",
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
        "title": "Back Up Now, Rescan, Pause, Resume et Stop",
        "body": """## Quand rien ne s'exécute
• **Back Up Now :** sauvegarde maintenant. Si un des interrupteurs d'optimisation de Settings est activé avec son **Mode** sur **Manuel**, il optimise aussi ensuite (voir [[settings-optimize-mode]]). Grisé quand il n'y a rien à envoyer ni à optimiser.
• **Rescan :** regarde le téléphone et interroge à nouveau OneDrive.

## Pendant une sauvegarde
• La progression s'affiche comme **Backing up now** avec un pourcentage (en volume de données, pas en nombre de fichiers).
• **Pause :** suspend toutes les sauvegardes jusqu'à **Resume**.
• **Stop :** termine cette exécution. Les sauvegardes automatiques continuent.

## Bon à savoir
• **Back Up Now** n'attend pas le chargeur, mais suit toujours votre réglage Wi-Fi. Voir [[settings-mobile-data]].
• Il sauvegarde chaque album réglé sur **Backup**, **Optimize** ou **Archive**, mais n'optimise que les fichiers des albums **Optimize**.""",
        "en_hash": "7018ccad25f0b316",
    },
    "albums-status-line": {
        "title": "Progression de l'envoi et messages d'état",
        "body": """• **Pendant l'envoi** (dans la carte) : le nombre de fichiers, l'album, le fichier en cours d'envoi et les octets envoyés sur la taille totale du fichier. Tant que la taille du fichier n'est pas connue, un pourcentage s'affiche à la place. La barre de progression se trouve en dessous. **▲ Hide Details ▲** masque les deux.
• **Pendant la préparation** (sous la carte) : **Scanning...** ou **No permission to read media.**
• **Après une exécution** (sous la carte) : un résumé de ce qui s'applique : **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (nouvel essai plus tard), **still to go**, ou **stopped:** avec la raison.""",
        "en_hash": "5e5b683e646d7541",
    },
    "albums-list": {
        "title": "L'onglet Albums",
        "body": """## En-tête
• Touchez **Backup**, **Optimize**, **Archive** ou **Off** pour n'afficher que les albums de ce mode. Touchez **All Albums** pour tous les afficher à nouveau.
• La ligne sous les boutons donne le total par mode.
• **▲ Hide Details ▲** en bas de la carte masque la ligne des totaux par mode et, pendant qu'une exécution se déroule, aussi la ligne de téléversement et sa barre ; **▼ Show Details ▼** les ramène. **All Albums**, les quatre boutons de mode et **Back Up Now** / **Rescan** restent, et pendant qu'une exécution se déroule, **Backing up now…**, **Pause** et **Stop** restent aussi. L'onglet mémorise votre choix.

## Cartes d'album
• **Nom, nombre de fichiers et taille** de l'album sur votre téléphone.
• **Deuxième ligne** (seulement si nécessaire) : combien de fichiers sont optimisés, marqués *do not optimize*, en attente ou verrouillés.
• **Ligne du Cloud :** **N verified in <Cloud>** ou **N of M verified in <Cloud>** signifie que le Cloud a confirmé les fichiers à la bonne taille. OneDrive, Dropbox, Google Drive, pCloud, Backblaze B2 et IDrive e2 sont vérifiés à chaque ouverture de l'onglet et sur **Rescan**. **Could not reach <Cloud>** signifie que la dernière vérification a échoué. Avant la première vérification — et toujours pour Google Photos, qui ne peut pas être interrogé — la ligne affiche **N sent to <Cloud>**. La ligne est verte lorsque chaque Cloud qu'elle nomme a tout vérifié.
• **Ligne rouge** (seulement s'il y en a) : **N files not backed up** — fichiers partis de leur Cloud, en échec, ou dont le téléversement n'a pas pu se terminer. Chacun demande une décision ; ouvrez l'album et balayez. Voir [[album-resend]].
• **Ligne grise** (seulement s'il y en a) : **N files not backed up by choice** — fichiers sur lesquels vous avez balayé vers la gauche, plus les verrouillés. Ils sont respectés ; rien ne les utilise.
• **Bouton de mode** (côté droit) : touchez pour changer le mode de l'album. Archive demande d'abord confirmation. Si le Cloud de l'album ne peut pas ce mode, la carte le dit.
• **Touchez la carte** pour voir les fichiers de l'album. Voir [[album-detail]].""",
        "en_hash": "0b9328a4170e83e0",
    },
    "album-detail": {
        "title": "La liste des fichiers d'un album",
        "body": """S'ouvre quand vous touchez une carte d'album.

• **En-tête :** flèche de retour, nom de l'album, mode et nombre de fichiers, **Backs up to** (le Cloud et le dossier où vont les nouveaux fichiers, plus tout autre Cloud qui en détient déjà), les compteurs ([[album-file-status]]), **Sort by** ([[album-file-sort]]) et **Do not optimize** ([[album-file-pin]]).
• **▲ Hide Details ▲** en bas de l'en-tête le replie sur le nom de l'album, son mode et le nombre de fichiers ; **▼ Show Details ▼** ramène le reste. La page retient votre choix.
• **Fichiers :** une carte par fichier avec sa taille, sa date et ses marques. Un fichier optimisé affiche sa taille réduite sur le téléphone.
• **Camera folder** (si choisi dans Settings) : commandes d'optimisation supplémentaires. Voir [[album-camera-optimize]].

Pas de miniatures ni d'aperçus. **Appuyez longuement sur un fichier** pour l'ouvrir dans l'application photo ou vidéo de votre téléphone, ou dans son Cloud lorsqu'il n'est plus sur le téléphone. Voir [[settings-open-not-on-phone]].""",
        "en_hash": "52f8fd50a2be074a",
    },
    "album-file-status": {
        "title": "Marques et compteurs des fichiers dans un album",
        "body": """• **✓ backed up :** dans le Cloud, en taille réelle sur le téléphone.
• **✓ backed up · optimized :** dans le Cloud ; le téléphone a une copie plus petite.
• **✓ backed up · locked :** dans le Cloud, et vous avez balayé vers la gauche : jamais renvoyé, jamais optimisé, jamais archivé. Toujours vérifié : si sa copie dans le Cloud disparaît, l'album le signale.
• **⟳ pending :** attend l'exécution suivante.
• **do not back up :** vous avez balayé vers la gauche sur un fichier non sauvegardé. Jamais téléversé, jamais optimisé, jamais proposé pour Archive. Une copie déjà dans un Cloud y reste.
• **couldn't finish uploading · trying after the rest :** un téléversement interrompu deux fois sans progresser. Envoyé après tout le reste, pour qu'un fichier bloqué ne retarde pas la file.
• **not in <Cloud> :** envoyé auparavant, et le Cloud ne l'a plus.

Les fichiers dans les trois derniers états sont comptés en rouge sur la carte de l'album comme **N files not backed up**. Chacun demande une décision — voir [[album-resend]].

## Do not optimize
Une case à cocher à côté de chaque fichier qui pourrait encore être optimisé : fichiers que **Restore** a ramenés dans n'importe quel album, et chaque fichier à taille réelle dans un album **Optimize**. Tant que la case est cochée, GallerySync n'optimise jamais ce fichier. L'en-tête de l'album affiche **N marked do not optimize**. Restore coche les fichiers qu'il ramène.

## Compteurs de l'en-tête
Ajoutent **N not backed up by choice** quand un fichier de l'album est marqué *do not back up* ou *locked*.""",
        "en_hash": "94b7a8315189854c",
    },
    "album-resend": {
        "title": "Fichiers non sauvegardés",
        "body": """Les fichiers que cet album a envoyés et qui ne sont plus dans leur Cloud, les fichiers dont le téléversement n'a pas pu se terminer, et les fichiers en échec apparaissent ici. La carte de l'album les compte en rouge sous la forme **N files not backed up** — chacun demande une décision. **Rescan** dans l'onglet Albums interroge de nouveau les Clouds.

Chacun est grisé. Un balayage décide :

• **Balayez vers la droite :** sauvegardez-le.
  • Pour un fichier parti de son Cloud : il est envoyé tout de suite (le Cloud est interrogé d'abord).
  • Pour un fichier en échec après cinq essais : seul celui-ci est réessayé.
  • Pour un fichier dont le téléversement a été coupé : il revient dans la file principale.
  • Pour un fichier que vous aviez tenu hors de la sauvegarde : il revient dans la sauvegarde.
• **Balayez vers la gauche :** marquez-le **do not back up**. Jamais téléversé, jamais optimisé, jamais proposé pour Archive. Une copie déjà dans un Cloud y reste.

Rien n'est renvoyé tout seul. Ils ont peut-être été supprimés exprès dans le Cloud.

## Back up all N
Dans l'en-tête, quand un fichier attend une décision. Sauvegarde chaque fichier en attente de décision, sauf ceux marqués *do not back up* et ceux en *✓ backed up · locked*. Grisé quand il n'y a rien à faire.

## Bon à savoir
• Les fichiers optimisés n'apparaissent pas ici : l'original en taille réelle n'était que dans le Cloud.
• Dans un album **Off**, choisissez d'abord un mode.
• Pour les Clouds autres que OneDrive, chaque Rescan vérifie au plus 25 fichiers par album absents du dossier de l'album. Relancez Rescan pour le reste.""",
        "en_hash": "bab90f38c05917bd",
    },

    "album-file-sort": {
        "title": "Trier les fichiers d'un album",
        "body": """• **Name :** de A à Z.
• **Date :** les plus récents d'abord.
• **Status :** failed, puis pending, puis optimized, puis backed up.

La liste s'ouvre à chaque fois dans l'ordre des noms. Trier ne change rien aux fichiers.""",
        "en_hash": "768559e84cdab251",
    },
    "album-file-pin": {
        "title": "Do not optimize",
        "body": """Une case à cocher à côté de chaque fichier d'un album **Optimize** qui pourrait encore être réduit, et à côté des fichiers que **Restore** a ramenés dans n'importe quel album. Tant que la case est cochée, GallerySync n'optimise jamais ce fichier — il reste à taille réelle sur votre téléphone.

L'en-tête de l'album affiche **N marked do not optimize**. Restore coche chaque fichier qu'il ramène. Décochez pour que le fichier suive à nouveau le mode de son album.

## Ce n'est pas la même chose que verrouiller
Balayer vers la gauche sur un fichier sauvegardé le marque **locked** (voir [[album-file-status]]) : jamais renvoyé, jamais optimisé **et** jamais archivé, mais toujours vérifié. *Do not optimize* arrête seulement l'optimisation.""",
        "en_hash": "4d4e77d702609a93",
    },
    "album-camera-optimize": {
        "title": "Optimiser les anciennes photos et vidéos de votre Camera folder",
        "body": """Seul le **Dossier de l'appareil photo** que vous avez choisi dans Settings sous **Dossier de l'appareil photo** ([[settings-section-camera]]) a ces commandes.

## Sélectionner des fichiers
• **Select Photos/Videos older than :** un nombre de **½** à **9** et une unité — **hour**, **day**, **week**, **month**, **year** ou **All**.
• **Photos** et **Videos :** quels types sélectionner.
• Glissez un fichier vers la droite pour le sélectionner, vers la gauche pour le désélectionner. La carte indique combien sont sélectionnés et l'économie estimée.

## Boutons
• **Back Up Now :** sauvegarde tout ce qui attend dans le dossier, puis optimise uniquement les fichiers sélectionnés.
• **Rescan :** vérifie à nouveau le Cloud.

## Bon à savoir
• Seuls les fichiers qu'OneDrive a confirmés en taille réelle peuvent être sélectionnés.
• Rien n'est optimisé avant que vous appuyiez sur **Back Up Now**. Les nouvelles photos restent telles quelles.
• Le Camera folder n'a pas de mode **Optimize**, et les interrupteurs d'optimisation de Settings ne l'affectent pas.
• Quitter avec des choix modifiés demande si vous voulez en faire vos valeurs par défaut.""",
        "en_hash": "10e1e3ba1e9982e2",
    },
    "album-camera-select": {
        "title": "Sélectionner des fichiers dans votre Camera folder",
        "body": """Affiche combien de fichiers sont sélectionnés et l'espace estimé économisé, ou **Nothing selected**.

• **Photos**, **Videos** et l'ancienneté sélectionnent chaque fichier correspondant.
• Glissez vers la droite pour sélectionner, vers la gauche pour désélectionner.
• **Back Up Now** n'optimise que les fichiers sélectionnés.

Un fichier grisé ne peut pas être sélectionné : il n'est pas encore confirmé dans OneDrive, ou il est déjà optimisé. Voir [[album-camera-optimize]].""",
        "en_hash": "0bfa66dc8a4265bf",
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
