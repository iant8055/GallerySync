TRANSLATIONS = {
    "_chapter": {
        "title": "Ersteinrichtung",
        "intro": "Beim ersten Öffnen von GallerySync läuft eine geführte Einrichtung.",
        "en_hash": "a04b336dd67e7d4e",
    },
    "setup-overview": {
        "title": "So funktioniert die Einrichtung",
        "body": """**Weiter** geht vor, **Zurück** geht zurück. Ein Bildschirm lässt dich erst weitermachen, wenn er hat, was er braucht.

## Die Schritte
1. Willkommen, eine kurze Tour und was eingerichtet wird. Siehe [[setup-welcome-tour]].
2. Wähle deine Clouds und melde dich an. Siehe [[setup-cloud]].
3. Erlaube den Zugriff auf deine Fotos und wähle dann Ordner. Siehe [[setup-choose-folders]].
4. Wähle einen Backup-Plan.
5. Optimierungseinstellungen (nur Plan 2 und 3).
6. Wann es losgeht.
7. Das erste Backup: die Cloud prüfen, hochladen, dann optimieren, wenn dein Plan es vorsieht.

## Gut zu wissen
• Das erste Backup kann Stunden dauern. Es läuft weiter, wenn du die App verlässt.
• Die App öffnet sich auf dem Fortschrittsbildschirm, bis das erste Backup abgeschlossen ist.
• Antworten aus der Einrichtung gelten nur für das erste Backup. Siehe [[setup-and-settings]].""",
        "en_hash": "a398aa1dfcdb2e54",
    },
    "setup-welcome-tour": {
        "title": "Willkommen, die Tour und was eingerichtet wird",
        "body": """• **Willkommen bei GallerySync:** tippe auf das Bild (**Zum Fortfahren tippen**), wähle deine Sprache aus dem Pop-up und drücke **OK**. Du kannst sie später in Settings ändern.
• **Die Tour:** fünf Bildschirme, einer für jeden Tab und einer für die Hilfe.
• **Was wir einrichten:** bei deiner Cloud anmelden, den Zugriff auf Fotos erlauben, Ordner wählen und Änderungen an diesen Ordnern erlauben.

Der Link unten auf diesem Bildschirm öffnet diese Einrichtungsanleitung. Sie ist der einzige Teil der Anleitung, der während der Einrichtung verfügbar ist.""",
        "en_hash": "17ccaeacdf9f0d32",
    },
    "setup-cloud": {
        "title": "Cloud-Speicher: Dienste wählen, anmelden und festlegen, wohin Backups gehen",
        "body": """Wähle die Clouds aus, die du nutzt, und drücke **Weiter**. Zu Beginn ist nichts ausgewählt.

## Deine erste Cloud ist kostenlos
Die erste Cloud, die du auswählst, wird deine kostenlose Cloud, in Settings als **Haupt-Cloud (kostenlos)** angezeigt. Jede weitere Cloud gehört zu **Pro**: 30 Tage kostenlos, danach ein einmaliger Kauf. Es wird nichts automatisch berechnet. Endet der Test, pausieren zusätzliche Clouds, und deine kostenlose Cloud läuft weiter.

Starte den Test mit **30 Tage kostenlos testen** in Settings unter Backup; die Schaltfläche erscheint, sobald eine zweite Cloud verbunden ist. **Pro freischalten** kauft Pro.

## Was jede Cloud kann
• **OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 und Backblaze B2:** Backup, Sync, Archive und Restore.
• **Google Photos:** nur Backup.

## Ein Hinweis zu Duplikaten
OneDrive ist die einzige Cloud, deren Inhalt geprüft wird, bevor etwas gesendet wird. Andere Clouds können eine zweite Kopie einer Datei erhalten, die schon dort ist.

## Anmelden
Jede Cloud bekommt ihre eigene nummerierte Karte **Verbinden**. **Weiter** wartet, bis alle verbunden sind.
• **OneDrive, Google Photos, Google Drive, Dropbox, pCloud:** melde dich auf der eigenen Seite des Anbieters an. Dein Passwort gelangt nie in GallerySync. Erlaube bei Dropbox Lesen und Schreiben, sonst funktioniert Restore nicht.
• **IDrive e2, Backblaze B2:** gib die Schlüssel aus deren Konsole ein. Siehe [[cloud-key-backblaze]] und [[cloud-key-idrive]].

## Backup-Ordner
Sobald eine Cloud verbunden ist, zeigt ihre Karte den **Backup-Ordner**, mit **Ändern**, um einen anderen zu wählen (nicht bei Google Photos, das keine Ordner hat). OneDrive beginnt bei **Samsung Gallery / DCIM**, dem Ordner, den die eigene OneDrive-Synchronisierung von Samsung nutzt, sodass dort bereits synchronisierte Fotos nicht doppelt gesendet werden. Andere Clouds beginnen bei **GallerySync**.{{full: Siehe [[dialog-destination]].}}""",
        "en_hash": "a90bbd0e1eecaa86",
    },
    "cloud-key-backblaze": {
        "title": "Backblaze B2 verbinden: die Schlüssel und wo du sie findest",
        "body": """GallerySync schreibt nur in seinen Backup-Ordner in deinem Bucket (**GallerySync**, sofern du keinen anderen wählst), daher ist die Nutzung eines vorhandenen Buckets sicher.

## In Backblaze
1. **Ein privater Bucket:** nimm einen vorhandenen oder wähle **Buckets → Create a Bucket**.
2. **Sein Endpunkt:** steht in den Details des Buckets, zum Beispiel **s3.us-east-005.backblazeb2.com**.
3. **Ein Application Key:** **Application Keys → Add a New Application Key**, nur für diesen einen Bucket, **Read and Write**. Nicht der Master Key.
4. **Beide Hälften kopieren:** die **keyID** und der **applicationKey**. Der applicationKey wird nur einmal angezeigt.

## In GallerySync
• **Endpunkt:** ohne https://.
• **Region:** leer lassen.
• **Bucket-Name:** genau wie in Backblaze.
• **Zugriffsschlüssel-ID:** die keyID (beginnt mit 005).
• **Geheimer Zugriffsschlüssel:** der applicationKey. Tippe auf **Anzeigen**, um ihn zu prüfen.

## Wenn die Schlüssel abgelehnt werden
Die Meldung sagt, was zu prüfen ist. Fast immer ist es ein Tippfehler: **O** statt Null, **I** statt **l**, **J** statt **j**. Einfügen vermeidet das.""",
        "en_hash": "5b41f1e8fffbcd4e",
    },
    "cloud-key-idrive": {
        "title": "IDrive e2 verbinden: die Schlüssel und wo du sie findest",
        "body": """GallerySync schreibt nur in seinen Backup-Ordner in deinem Bucket (**GallerySync**, sofern du keinen anderen wählst), daher ist die Nutzung eines vorhandenen Buckets sicher.

## In IDrive e2
1. **Ein privater Bucket.**
2. **Der Endpunkt der Region**, zum Beispiel **s3.us-west-1.idrivee2.com**. Nicht die Adresse mit dem Bucket-Namen davor.
3. **Der Name der Region**, zum Beispiel **us-west-1**.
4. **Ein Zugriffsschlüssel:** eine **access key ID** und ein **secret access key**. Das Secret wird nur einmal angezeigt.

## In GallerySync
Gib **Endpunkt** (ohne https://), **Region**, **Bucket-Name**, **Zugriffsschlüssel-ID** und **Geheimer Zugriffsschlüssel** ein. Tippe auf **Anzeigen**, um das Secret zu prüfen.

## Wenn die Schlüssel abgelehnt werden
Die Meldung sagt, was zu prüfen ist. Fast immer ist es ein Tippfehler: **O** statt Null, **I** statt **l**. Einfügen vermeidet das.""",
        "en_hash": "18ca3e144c0c9aa9",
    },
    "setup-search-permission": {
        "title": "GallerySync dieses Telefon durchsuchen lassen",
        "body": """**Suche erlauben** öffnet den Berechtigungsbildschirm von Android. Hier erlaubst du GallerySync, die Fotos und Videos auf deinem Telefon zu sehen.

## Gut zu wissen
Wähle **Alle zulassen**. Wenn du **Fotos auswählen** wählst, können nur diese Fotos gesichert werden.""",
        "en_hash": "9dfa956289f7ed75",
    },
    "setup-choose-folders": {
        "title": "Ordner zum Sichern wählen",
        "body": """Wähle die Ordner aus, die gesichert werden sollen, und drücke **Weiter**. Die meisten Fotos liegen in **DCIM**.

## In welche Cloud jeder Ordner geht
Bei mehr als einer Cloud zeigt jeder Ordner **Geht nach**. Wähle für jeden ausgewählten Ordner eine Cloud. Bereits gesendete Dateien wandern nie; nur neue Dateien folgen einer Änderung.{{full: Siehe [[settings-destination]].}}

## Android fragt für jeden Ordner erneut
Android zeigt einen eigenen Bildschirm für die Ordnerberechtigung. Wenn etwas schiefgeht:
• **Kein Zugriff erteilt:** **Erneut versuchen** oder **[Ordner] überspringen**.
• **Ein Ordner darin wurde gewählt:** **Ganz [Ordner] wählen** zur Korrektur, oder **Nur [Ordner] behalten** für die kleinere Auswahl.
• **Ein anderer Ordner wurde gewählt:** erneut versuchen oder überspringen.

Nichts außerhalb deiner gewählten Ordner wird durchsucht oder verändert.""",
        "en_hash": "409ac88ad1359229",
    },
    "setup-backup-plan": {
        "title": "Wähle deinen Backup-Plan",
        "body": """Was mit den Fotos und Videos geschieht, die schon auf deinem Telefon sind. **Kein Plan entfernt etwas.**

• **1. Alles sichern, was noch nicht in der Cloud ist.** Auf dem Telefon ändert sich nichts. Beim Öffnen ausgewählt.
• **2. Sichern, dann jede Datei auf dem Telefon optimieren.** Die größte Platzersparnis.
• **3. Sichern, dann nur die Dateien optimieren, die dieses Backup gesendet hat.**
• **4. Die Cloud prüfen, aber nichts senden.** Den Modus jedes Albums wählst du danach im Albums-Tab.

## Gut zu wissen
Dieses erste Backup ist einmalig und läuft unabhängig vom Modus. Danach steht jedes Album auf **Off**, und das ist richtig: Ab jetzt wählst du den Modus jedes Albums selbst im Albums-Tab.{{full: Siehe [[faq-albums-off-after-backup]].}}""",
        "en_hash": "80f55e21398db3f8",
    },
    "setup-optimise": {
        "title": "Optimierungseinstellungen bei der Einrichtung",
        "body": """Nur für Plan 2 und 3.

• **Fotos optimieren** und **Videos optimieren:** an oder aus.
• **Videokompression:** **Hoch** (480p, am kleinsten), **Mittel** (720p) oder **Niedrig** (1080p).
• Jede zeigt eine geschätzte Ersparnis, mit einer Zeile **Geschätzte Gesamtersparnis**. Die tatsächlichen Ergebnisse variieren.""",
        "en_hash": "d3ff8b60c0328479",
    },
    "setup-ready": {
        "title": "Bereit für das Backup",
        "body": """Zeigt, wie viel gesichert wird und, wenn optimiert wird, wie viel Platz du sparst.

• **Sofort:** startet gleich.
• **Verzögerung:** **1 Std., 2 Std., 4 Std., 8 Std., 12 Std.** oder **24 Std.**

## Gut zu wissen
• Ein verzögertes Backup startet nur, während das Telefon am Ladegerät ist, und kann etwas verspätet beginnen.
• Die App bleibt auf dem Fortschrittsbildschirm, bis das erste Backup abgeschlossen ist.""",
        "en_hash": "90436741649f85c6",
    },
    "setup-progress": {
        "title": "Backup-Fortschritt",
        "body": """## Warten auf den Start
Ein Countdown läuft, bis das Backup startet, auch bei geschlossener App, solange das Telefon am Ladegerät ist. **JETZT SYNCHRONISIEREN** startet es sofort.

## Schritt 1: Deine Cloud wird geprüft
Jede Datei wird nach Name und Größe mit dem verglichen, was deine Cloud schon hat, mit laufender Zählung. Es wird nichts gesendet. Meist ein bis zwei Minuten.

## Schritt 2: Hochladen
Der Ring zeigt den Gesamtfortschritt. Darunter: die Cloud, an die gerade gesendet wird, und ihre Zahl. Andere wartende Clouds erscheinen als **Ausstehend**.

## Schritt 3: Optimieren
**Fotos werden optimiert**, dann **Videos werden optimiert**, wenn dein Plan sie einschließt.

## Schaltflächen
• **Schließen:** schließt die App; das Backup läuft weiter.
• **Fertig:** erscheint, wenn alles erledigt ist, mit einer Zusammenfassung dessen, was geprüft wurde, schon in der Cloud war und gesendet wurde.
• **Abbrechen:** fragt **Backup stoppen?** Bereits gesendete Dateien bleiben gesendet; nichts wird gelöscht.""",
        "en_hash": "625986b0864a86c8",
    },
    "setup-and-settings": {
        "title": "Einrichtung und Settings sind getrennt",
        "body": """Einrichtung und Settings haben jeweils ihre eigenen Antworten. Die Einrichtung liest nie aus Settings, und Settings liest nie aus der Einrichtung. Die Ordnerliste der Einrichtung beginnt leer, auch wenn Settings schon Ordner auflistet, und die Antworten der Einrichtung erscheinen nicht in Settings.

Die Einrichtung legt nie den Modus eines Albums fest. Das tust nur du, im Albums-Tab.""",
        "en_hash": "06ce026c4915e1fd",
    },
}
