TRANSLATIONS = {
    "_chapter": {
        "title": "Comment ça marche, en termes simples",
        "intro": "Les idées derrière les boutons.",
        "en_hash": "a50822757f585a7f",
    },
    "modes-in-depth": {
        "title": "Les quatre modes",
        "body": """Chaque album a un seul mode, réglé avec le bouton de sa carte.

• **Off :** cet album n'est pas pris en charge : rien n'est envoyé ni modifié. Chaque album commence ici.
• **Backup :** copie vers votre Cloud ; le téléphone n'est jamais touché.
• **Sync :** sauvegarde, puis optimise si l'optimisation est activée dans Settings.
• **Archive :** sauvegarde, vérifie, puis déplace les fichiers vers la corbeille du téléphone après votre toucher. Le seul mode qui retire des fichiers.

## Quels Clouds
Backup fonctionne avec tous les Clouds. Sync et Archive demandent OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 ou Backblaze B2, pas Google Photos.

## Dossier de l'appareil photo
Si vous définissez un dossier de l'appareil photo dans Settings, il propose Off, Backup et Archive, avec ses propres commandes d'optimisation au lieu de Sync. Voir [[album-camera-optimise]].

## Lequel choisir
• Garder tel quel, mais protégé : **Backup**.
• Gagner de la place en continuant à regarder : **Sync**.
• Vieux albums que vous voulez retirer du téléphone : **Archive**.
• Dans le doute : **Off** ou **Backup**.

## Bon à savoir
• Vous seul réglez les modes. La seule exception est la fusion d'albums en double, qui règle **Off**. Voir [[album-merge-warning]].
• Passer à Archive demande une confirmation. En sortir arrête les retraits suivants.""",
        "en_hash": "43d293213ae2b8f7",
    },
    "how-verification-works": {
        "title": "Comment fonctionne la vérification",
        "body": """Avant que quoi que ce soit soit optimisé ou retiré, on demande au Cloud, sur le moment, s'il détient une bonne copie.

## Le test
• **OneDrive :** liste le dossier de l'album ; le fichier doit avoir le même nom et la même taille.
• **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2 :** interrogés sur le fichier exact ; il doit être présent, pas dans sa corbeille, à la même taille. Pour une photo optimisée, la taille de l'original.

## Quand
• Au début de la première sauvegarde, pour n'envoyer que les fichiers manquants.
• À chaque ouverture de l'onglet Albums ou chaque pression sur Rescan (OneDrive).
• Quand vous vérifiez des fichiers dans l'onglet Archive, et de nouveau juste avant le retrait.
• Juste avant l'optimisation d'un fichier.

## Trois réponses
• **Verified :** le Cloud l'a.
• **Pas là, ou mauvaise taille :** le fichier reste sur le téléphone.
• **Impossible de vérifier :** traité comme inconnu. Rien n'est retiré ni optimisé.

## Bon à savoir
Une vérification de taille ne peut pas détecter tous les types de dommages. Ouvrez votre Cloud de temps en temps et vérifiez quelques photos.""",
        "en_hash": "3a19869102cce663",
    },
    "how-optimising-works": {
        "title": "Comment fonctionne l'optimisation",
        "body": """Optimiser remplace la copie du téléphone par une copie plus petite, de sorte que le téléphone cesse de se remplir tout en gardant tout dans votre galerie.

## Photos
Réduites à environ 2048 pixels sur le grand côté, soit environ un dixième de la taille. Même nom, même emplacement, même date et même lieu, avec un petit badge de nuage.

## Vidéo
Réencodée en **480p** (Compression élevée), **720p** (Compression moyenne) ou **1080p** (Compression faible). Jamais coupée. Automatique attend le chargeur ; Manuel s'exécute avec **Sync now**.

## Règles
• Seulement dans les albums **Sync**.
• Seulement après que le Cloud a confirmé l'original à la même taille.
• Un fichier qui ne deviendrait pas plus petit est laissé tranquille.

## Récupérer l'original
Utilisez l'onglet **Restore**. Restaurez avant de modifier si vous voulez tout le détail.""",
        "en_hash": "1c7d13215d15f27e",
    },
    "where-deleted-files-go": {
        "title": "Où vont les fichiers retirés",
        "body": """Rien n'est supprimé définitivement.

## Sur votre téléphone
Archive déplace les fichiers vers la corbeille d'Android pendant environ 30 jours :
• Samsung : la **Corbeille** de Samsung Gallery.
• Autres téléphones : la **Corbeille** de l'application **Fichiers**.

Les fichiers dans la corbeille occupent toujours leur espace complet jusqu'à ce que vous la vidiez.

## Dans votre Cloud
Sous **Ask**, les copies que vous choisissez de retirer vont dans la **corbeille OneDrive**. Rien n'est jamais retiré des autres Clouds. L'application ne vide jamais aucune corbeille.""",
        "en_hash": "ab2cd9530770d041",
    },
    "background-work": {
        "title": "Quand les choses se passent",
        "body": """• **Sauvegarde :** peu après l'apparition de nouvelles photos, et toutes les six heures. Wi-Fi uniquement sauf si les données mobiles sont activées ; pas quand la batterie ou le stockage est faible. **Sync now** s'exécute immédiatement.
• **Optimising photos :** Automatique, dès que le Cloud confirme un fichier d'un album Sync. Manuel, avec **Sync now**.
• **Optimising video :** Automatique, après les sauvegardes, pendant la charge. Manuel, avec **Sync now**.
• **Fichiers supprimés :** sous **Ask**, vous êtes notifié peu après la suppression d'un fichier qui a une copie Cloud (pas quand les sauvegardes sont en pause).
• **Archivage :** seulement quand vous dites Yes dans l'onglet Archive.
• **Restauration :** seulement quand vous appuyez sur Restore.

## Fermer l'application
Retour ou Accueil, ça va : le travail continue. Balayer l'application hors des applications récentes peut arrêter le travail en arrière-plan jusqu'à ce que vous la rouvriez.""",
        "en_hash": "b52b93819f999605",
    },
    "where-your-data-lives": {
        "title": "Où vivent vos données",
        "body": """• **Photos et vidéos :** vont directement entre votre téléphone et votre propre Cloud, chiffrées. Il n'y a aucun serveur GallerySync.
• **Connexions et clés :** conservées chiffrées sur votre téléphone. Les mots de passe se saisissent sur la page du fournisseur, jamais dans l'application.
• **Les enregistrements et réglages de l'application :** stockage privé sur votre téléphone.
• **Rien n'est collecté :** ni analyse d'usage, ni rapport de plantage, ni publicité.

Détails : **Politique de confidentialité** et **Supprimer les données du compte** dans Settings.""",
        "en_hash": "989af3b026b90273",
    },
    "numbers-explained": {
        "title": "Pourquoi les chiffres peuvent différer de ceux de votre application de galerie",
        "body": """GallerySync ne compte que les fichiers présents sur le téléphone maintenant, dans les dossiers que vous avez choisis, pas dans la corbeille. Votre galerie peut compter autrement les éléments uniquement dans le cloud ou regrouper autrement les dossiers. Les deux ont raison ; ils comptent des choses différentes.""",
        "en_hash": "54fab5cd25e07a35",
    },
}
