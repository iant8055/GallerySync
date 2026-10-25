TRANSLATIONS = {
    "_chapter": {
        "title": "Première configuration",
        "intro": "Une configuration guidée s'exécute la première fois que vous ouvrez GallerySync.",
        "en_hash": "a04b336dd67e7d4e",
    },
    "setup-overview": {
        "title": "Comment fonctionne la configuration",
        "body": """**Suivant** avance, **Retour** revient en arrière. Un écran ne vous laisse continuer que lorsqu'il a ce dont il a besoin.

## Les étapes
1. Bienvenue, une courte visite, et ce qui va être configuré. Voir [[setup-welcome-tour]].
2. Choisissez vos Clouds et connectez-vous. Voir [[setup-cloud]].
3. Autorisez l'accès à vos photos, puis choisissez des dossiers. Voir [[setup-choose-folders]].
4. Choisissez un Plan de sauvegarde.
5. Réglages d'optimisation (plans 2 et 3 seulement).
6. Quand commencer.
7. La première sauvegarde : vérifier le Cloud, envoyer, puis optimiser si votre plan l'inclut.

## Bon à savoir
• La première sauvegarde peut prendre des heures. Elle continue si vous quittez l'application.
• L'application s'ouvre sur l'écran de progression tant que la première sauvegarde n'est pas terminée.
• Les réponses de la configuration ne valent que pour la première sauvegarde. Voir [[setup-and-settings]].""",
        "en_hash": "a398aa1dfcdb2e54",
    },
    "setup-welcome-tour": {
        "title": "Bienvenue, la visite, et ce que nous allons configurer",
        "body": """• **Bienvenue :** touchez l'image (**Touchez pour continuer**), choisissez votre langue dans la fenêtre contextuelle et appuyez sur **OK**. Vous pourrez la changer plus tard dans Settings.
• **La visite :** cinq écrans, un pour chaque onglet et un pour l'Aide.
• **Ce que nous allons configurer :** connectez-vous à votre Cloud, autorisez l'accès aux photos, choisissez des dossiers, et autorisez les modifications dans ces dossiers.

Le lien en bas de cet écran ouvre ce guide de configuration. C'est la seule partie du guide disponible pendant la configuration.""",
        "en_hash": "17ccaeacdf9f0d32",
    },
    "setup-cloud": {
        "title": "Stockage Cloud : choisir les services, se connecter et choisir où vont les sauvegardes",
        "body": """Sélectionnez les Clouds que vous utilisez et appuyez sur **Suivant**. Rien n'est sélectionné au départ.

## Votre premier Cloud est gratuit
Le premier Cloud que vous sélectionnez devient votre Cloud gratuit, affiché comme **principal (gratuit)** dans Settings. Chaque Cloud supplémentaire demande **Pro** : gratuit pendant 30 jours, puis un achat unique. Rien n'est facturé automatiquement. Si l'essai se termine, les Clouds supplémentaires se mettent en pause et votre Cloud gratuit continue.

Lancez l'essai avec **Commencer l'essai gratuit de 30 jours** dans Settings, sous Backup ; il apparaît dès qu'un deuxième Cloud est connecté. **Débloquer Pro** l'achète.

## Ce que chaque Cloud peut faire
• **OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 et Backblaze B2 :** Backup, Sync, Archive et Restore.
• **Google Photos :** Backup uniquement.

## À propos des doublons
OneDrive est le seul Cloud dont le contenu est vérifié avant tout envoi. Les autres Clouds peuvent recevoir une seconde copie d'un fichier qui s'y trouve déjà.

## Connexion
Chaque Cloud a sa propre carte numérotée, avec un bouton **Connecter**. **Suivant** attend que tous soient connectés.
• **OneDrive, Google Photos, Google Drive, Dropbox, pCloud :** connectez-vous sur la page du fournisseur lui-même. Votre mot de passe n'entre jamais dans GallerySync. Pour Dropbox, autorisez la lecture et l'écriture, sinon Restore ne fonctionnera pas.
• **IDrive e2, Backblaze B2 :** saisissez les clés de leur console. Voir [[cloud-key-backblaze]] et [[cloud-key-idrive]].

## Dossier de sauvegarde
Une fois un Cloud connecté, sa carte affiche son **Dossier de sauvegarde**, avec **Modifier** pour en choisir un autre (sauf Google Photos, qui n'a pas de dossiers). OneDrive commence à **Samsung Gallery / DCIM**, le dossier qu'utilise la synchronisation OneDrive de Samsung, de sorte que les photos déjà synchronisées là ne sont pas envoyées deux fois. Les autres Clouds commencent à **GallerySync**.{{full: Voir [[dialog-destination]].}}""",
        "en_hash": "a90bbd0e1eecaa86",
    },
    "cloud-key-backblaze": {
        "title": "Connecter Backblaze B2 : les clés et où les trouver",
        "body": """GallerySync n'écrit que dans son dossier de sauvegarde de votre bucket (**GallerySync** sauf si vous en choisissez un autre), donc un bucket existant peut être utilisé sans risque.

## Dans Backblaze
1. **Un bucket privé :** utilisez-en un que vous avez, ou **Buckets → Create a Bucket**.
2. **Son point de terminaison :** affiché dans les détails du bucket, par exemple **s3.us-east-005.backblazeb2.com**.
3. **Une clé d'application :** **Application Keys → Add a New Application Key**, pour ce seul bucket, **Read and Write**. Pas la clé Master.
4. **Copiez les deux moitiés :** le **keyID** et l'**applicationKey**. L'applicationKey n'est affichée qu'une seule fois.

## Dans GallerySync
• **Point de terminaison :** sans https://.
• **Région :** laissez vide.
• **Nom du bucket :** exactement comme dans Backblaze.
• **ID de clé d'accès :** le keyID (commence par 005).
• **Clé d'accès secrète :** l'applicationKey. Touchez **Afficher** pour la vérifier.

## Si les clés sont rejetées
Le message dit quoi vérifier. C'est presque toujours une faute de frappe : **O** contre zéro, **I** contre **l**, **J** contre **j**. Le copier-coller évite cela.""",
        "en_hash": "5b41f1e8fffbcd4e",
    },
    "cloud-key-idrive": {
        "title": "Connecter IDrive e2 : les clés et où les trouver",
        "body": """GallerySync n'écrit que dans son dossier de sauvegarde de votre bucket (**GallerySync** sauf si vous en choisissez un autre), donc un bucket existant peut être utilisé sans risque.

## Dans IDrive e2
1. **Un bucket privé.**
2. **Le point de terminaison de la région**, par exemple **s3.us-west-1.idrivee2.com**. Pas l'adresse avec le nom du bucket devant.
3. **Le nom de la région**, par exemple **us-west-1**.
4. **Une clé d'accès :** un **access key ID** et une **secret access key**. Le secret n'est affiché qu'une seule fois.

## Dans GallerySync
Saisissez le **Point de terminaison** (sans https://), la **Région**, le **Nom du bucket**, l'**ID de clé d'accès** et la **Clé d'accès secrète**. Touchez **Afficher** pour vérifier le secret.

## Si les clés sont rejetées
Le message dit quoi vérifier. C'est presque toujours une faute de frappe : **O** contre zéro, **I** contre **l**. Le copier-coller évite cela.""",
        "en_hash": "18ca3e144c0c9aa9",
    },
    "setup-search-permission": {
        "title": "Laisser GallerySync chercher sur ce téléphone",
        "body": """**Autoriser la recherche** ouvre l'écran d'autorisation d'Android. C'est là que vous permettez à GallerySync de voir les photos et vidéos de votre téléphone.

## Bon à savoir
Choisissez **Tout autoriser**. Si vous choisissez **Sélectionner des photos**, seules ces photos pourront être sauvegardées.""",
        "en_hash": "9dfa956289f7ed75",
    },
    "setup-choose-folders": {
        "title": "Choisir les dossiers à sauvegarder",
        "body": """Sélectionnez les dossiers à sauvegarder et appuyez sur **Suivant**. La plupart des photos sont dans **DCIM**.

## Vers quel Cloud va chaque dossier
Avec plusieurs Clouds, chaque dossier affiche **Va vers**. Choisissez un Cloud pour chaque dossier sélectionné. Les fichiers déjà envoyés ne bougent jamais ; seuls les nouveaux fichiers suivent un changement.{{full: Voir [[settings-destination]].}}

## Android redemande pour chaque dossier
Android affiche son propre écran d'autorisation de dossier. En cas de problème :
• **Aucun accès accordé :** **Réessayer** ou **Ignorer** le dossier.
• **Un dossier à l'intérieur a été choisi :** **Choisir tout** pour corriger, ou **Garder seulement** le choix plus étroit.
• **Un autre dossier a été choisi :** réessayez ou ignorez.

Rien en dehors de vos dossiers choisis n'est analysé ni modifié.""",
        "en_hash": "409ac88ad1359229",
    },
    "setup-backup-plan": {
        "title": "Choisissez votre Plan de sauvegarde",
        "body": """Ce qu'il faut faire des photos et vidéos déjà sur votre téléphone. **Aucun plan ne retire quoi que ce soit.**

• **1. Vérifier le stockage Cloud et sauvegarder tout ce qui ne l'est pas encore.** Rien ne change sur le téléphone. Sélectionné à votre arrivée.
• **2. Tout le n° 1, plus optimiser tous les fichiers du téléphone pour un gain de place maximal.** C'est le plus d'espace gagné.
• **3. Tout le n° 1, mais optimiser seulement les fichiers nouvellement sauvegardés, pour un gain de place modéré.**
• **4. Vérifier le stockage Cloud sans sauvegarder de nouveaux fichiers, sans gain de place.** Vous choisissez ensuite le mode de chaque album dans l'onglet Albums.

## Bon à savoir
Cette première sauvegarde est unique et s'exécute quel que soit le mode. Ensuite, chaque album est sur **Off**, ce qui est normal : à partir de là, vous choisissez vous-même le mode de chaque album dans l'onglet Albums.{{full: Voir [[faq-albums-off-after-backup]].}}""",
        "en_hash": "80f55e21398db3f8",
    },
    "setup-optimise": {
        "title": "Réglages d'optimisation pendant la configuration",
        "body": """Uniquement pour les plans 2 et 3.

• **Optimiser les photos** et **Optimiser les vidéos :** activé ou désactivé.
• **Niveau de compression vidéo :** **Élevée** (480p, la plus petite), **Moyenne** (720p) ou **Faible** (1080p).
• Chacun affiche un gain estimé, avec une ligne **Gain total estimé**. Les résultats réels varient.""",
        "en_hash": "d3ff8b60c0328479",
    },
    "setup-ready": {
        "title": "Prêt pour la sauvegarde",
        "body": """Affiche la quantité qui sera sauvegardée et, si vous optimisez, la place que vous allez gagner.

• **Tout de suite :** démarre immédiatement.
• **Délai :** **1 h, 2 h, 4 h, 8 h, 12 h** ou **24 h**.

## Bon à savoir
• Une sauvegarde différée ne démarre que si le téléphone est branché, et peut démarrer un peu en retard.
• L'application reste sur l'écran de progression jusqu'à la fin de la première sauvegarde.""",
        "en_hash": "90436741649f85c6",
    },
    "setup-progress": {
        "title": "Progression de la sauvegarde",
        "body": """## En attente de démarrage
Un compte à rebours tourne jusqu'au début de la sauvegarde, même application fermée, tant que le téléphone est branché. **SYNCHRONISER MAINTENANT** la lance immédiatement.

## Étape 1 : Vérification de votre Cloud
Chaque fichier est comparé à ce que votre Cloud détient déjà, par nom et par taille, avec un décompte en direct. Rien n'est envoyé. En général une minute ou deux.

## Étape 2 : Envoi
L'anneau montre la progression globale. En dessous : le Cloud vers lequel on envoie et son décompte. Les autres Clouds en attente apparaissent avec **En attente**.

## Étape 3 : Optimisation
**Optimisation des photos**, puis **Optimisation des vidéos**, si votre plan les inclut.

## Boutons
• **Fermer :** ferme l'application ; la sauvegarde continue.
• **Terminer :** apparaît quand tout est fini, avec un résumé de ce qui a été vérifié, déjà dans le Cloud, et envoyé.
• **Annuler :** demande **Arrêter la sauvegarde ?** Les fichiers déjà envoyés restent envoyés ; rien n'est supprimé.""",
        "en_hash": "625986b0864a86c8",
    },
    "setup-and-settings": {
        "title": "La configuration et Settings sont séparés",
        "body": """La configuration et Settings gardent chacun leurs propres réponses. La configuration ne lit jamais Settings, et Settings ne lit jamais la configuration. La liste de dossiers de la configuration commence vide même si Settings liste déjà des dossiers, et les réponses de la configuration n'apparaissent pas dans Settings.

La configuration ne définit jamais le mode d'un album. Vous seul le faites, dans l'onglet Albums.""",
        "en_hash": "06ce026c4915e1fd",
    },
}
