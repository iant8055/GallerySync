TRANSLATIONS = {
    "_chapter": {
        "title": "Fragen und Probleme",
        "intro": "Häufige Fragen und was meist dahintersteckt.",
        "en_hash": "01001cde06eb2f03",
    },
    "faq-sync-now-greyed": {
        "title": "Sync now ist ausgegraut",
        "body": """Es gibt nichts zu senden oder zu optimieren: Alles ist schon gesendet, oder kein Album steht auf Backup, Sync oder Archive. Prüfe die Modus-Summen in der Kopfzeile.""",
        "en_hash": "d76ec512e56c7203",
    },
    "faq-albums-off-after-backup": {
        "title": "Meine Alben stehen nach dem ersten Backup auf Off",
        "body": """Das ist richtig. Das erste Backup sendet alles unabhängig vom Modus, und die Einrichtung legt nie Modi fest. Wähle im Albums-Tab für jedes Album einen Modus.""",
        "en_hash": "39db6d0a503fb274",
    },
    "faq-not-checked-yet": {
        "title": "Ein Album sagt Not checked against OneDrive yet",
        "body": """OneDrive wurde noch nicht gefragt. Warte einen Moment oder drücke **Rescan**. **Could not reach OneDrive** bedeutet, dass die Verbindung fehlgeschlagen ist: Prüfe, ob du online und angemeldet bist, und drücke dann **Rescan**.

Alben in anderen Clouds zeigen stattdessen **sent to** und die jeweilige Cloud. Siehe [[albums-list]].""",
        "en_hash": "52048b6e4b7bcf97",
    },
    "faq-onedrive-full": {
        "title": "Das Backup wurde gestoppt, weil meine Cloud voll ist",
        "body": """**stopped: your Cloud is full.** Nichts geht verloren. Schaffe etwas Platz oder erweitere den Speicher, dann drücke **Sync now**.""",
        "en_hash": "70148dcb9cb4986c",
    },
    "faq-space-not-freed": {
        "title": "Ich habe ein Album archiviert, aber mein Telefon hat nicht mehr Platz",
        "body": """Archivierte Dateien liegen im Papierkorb des Telefons und belegen weiter Platz. Leere den Papierkorb, sobald du sicher bist, dass sie in deiner Cloud gut aufgehoben sind. Siehe [[where-deleted-files-go]].""",
        "en_hash": "2a38df823e61c58d",
    },
    "faq-album-vanished": {
        "title": "Ein Album ist aus meiner Galerie verschwunden",
        "body": """Wenn es auf **Archive** stand, liegen seine Dateien im Papierkorb des Telefons. Stelle sie aus dem Papierkorb wieder her oder nutze den Tab **Restore**. Es kommt als neues Album zurück, das auf Off steht.""",
        "en_hash": "94390b0205e85c29",
    },
    "faq-photo-looks-small": {
        "title": "Ein Foto sieht kleiner oder schlechter aus als vorher",
        "body": """Es wurde optimiert (achte auf das kleine Wolkensymbol). Das Original liegt in deiner Cloud: Nutze den Tab **Restore**.""",
        "en_hash": "4e1a6a3f710360db",
    },
    "faq-restore-empty": {
        "title": "Der Restore-Tab sagt, es gibt nichts zurückzuholen",
        "body": """Es wurde nichts optimiert oder archiviert, und (bei OneDrive) ist jedes Foto in deinen Backup-Ordnern schon auf dem Telefon. Das ist richtig.""",
        "en_hash": "e0720600e98ca0e2",
    },
    "faq-backup-stops-when-closed": {
        "title": "Backups scheinen zu stoppen, wenn ich die App schließe",
        "body": """Zurück oder Home ist in Ordnung. Wenn du die App aus den zuletzt verwendeten Apps wegwischst, kann die Hintergrundarbeit stoppen: Öffne die App wieder, und sie macht weiter, ohne etwas doppelt zu senden.""",
        "en_hash": "266c98c91b9f897f",
    },
    "faq-wrong-account": {
        "title": "Ich habe mich mit dem falschen Konto bei einer Cloud angemeldet",
        "body": """**Settings → Backup → Abmelden** neben dieser Cloud, wähle **Dort lassen**, dann melde dich mit dem richtigen Konto an. Bereits gesendete Dateien bleiben im ersten Konto. Siehe [[settings-account]].""",
        "en_hash": "a033d25dffca0535",
    },
    "faq-folder-waiting": {
        "title": "Ich habe mich von einer Cloud abgemeldet, und ihre Ordner senden nicht mehr",
        "body": """Das ist zu erwarten. Die Ordner warten, sicher auf deinem Telefon, bis du dich wieder anmeldest. Um sie anderswohin zu senden, ändere **Wohin jeder Ordner geht**. Siehe [[settings-destination]].""",
        "en_hash": "286529a95f8327e7",
    },
    "faq-keys-rejected": {
        "title": "IDrive e2 oder Backblaze B2 sagt, meine Schlüssel wurden abgelehnt",
        "body": """Meist ein Tippfehler. Prüfe den Endpunkt (ohne https://), die Region, den Bucket und beide Schlüssel; tippe beim Secret auf **Anzeigen**. Achte auf **O** statt Null, **I** statt **l**, **J** statt **j**. Einfügen vermeidet das. Siehe [[cloud-key-backblaze]] und [[cloud-key-idrive]].""",
        "en_hash": "e0b15236b548a066",
    },
    "faq-could-not-check": {
        "title": "Archive sagt, es konnte meine Cloud nicht prüfen",
        "body": """Die Cloud hat nicht geantwortet, also wurde nichts entfernt. Prüfe, ob du online und angemeldet bist, und drücke dann erneut **Check these files**.""",
        "en_hash": "96fcc47fc7eaf6d4",
    },
    "faq-reconnect-cloud": {
        "title": "Restore sagt, ich soll die Cloud erneut verbinden",
        "body": """Die Cloud hat das Lesen der Datei verweigert. Melde dich in Settings erneut an. Erlaube bei Dropbox das Lesen von Dateien; nutze bei IDrive e2 und Backblaze B2 einen Schlüssel, der den Bucket lesen kann. Die Datei auf deinem Telefon wurde nicht verändert.""",
        "en_hash": "23bf7817d447a418",
    },
    "albums-permission": {
        "title": "GallerySync kann meine Fotos nicht sehen",
        "body": """Eine Meldung oben im Albums-Tab bedeutet, dass GallerySync keinen vollen Zugriff auf deine Fotos hat:
• **GallerySync needs access to your photos:** Es wurde kein Zugriff erteilt, oder er wurde entzogen.
• **Only some photos are shared:** Der Zugriff wurde mit **Fotos auswählen** eingeschränkt, daher sind Backups unvollständig.

Drücke **Grant access** und wähle **Alle zulassen**. Erscheint keine Abfrage: **Einstellungen → Apps → GallerySync → Berechtigungen → Fotos und Videos** auf dem Telefon.""",
        "en_hash": "d1499b80f267c6f8",
    },
    "contact-and-privacy": {
        "title": "Kontakt, Datenschutz und Löschen deiner Daten",
        "body": """Ganz unten in Settings:
• **Kontakt:** die Adresse, mit einer Kopieren-Schaltfläche.
• **Datenschutzerklärung:** deine Dateien gehen nur an deine eigene Cloud; nichts wird erfasst.
• **Kontodaten löschen:** wie du dich abmeldest, den Zugriff entfernst und Daten löschst. Deine Cloud-Dateien bleiben.

Um ein Problem zu melden, nutze **Fehler melden** direkt darüber.""",
        "en_hash": "3e29c01351fc7319",
    },
}
