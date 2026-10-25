TRANSLATIONS = {
    "_chapter": {
        "title": "Der Archive-Tab",
        "intro": "Der einzige Ort, an dem Dateien dein Telefon verlassen. Nur Alben, die du auf Archive gestellt hast, zuvor mit deiner Cloud abgeglichen und erst nach deinem Tippen.",
        "en_hash": "99a3f217222adae4",
    },
    "archive-overview": {
        "title": "So funktioniert Archive, Schritt für Schritt",
        "body": """**Archive braucht Android 11 oder neuer.**

1. **Stelle ein Album auf Archive** im Albums-Tab und bestätige. Siehe [[dialog-archive-confirm]].
2. **Drücke Check these files.** Jede Datei wird mit ihrer Cloud abgeglichen. Dateien, die noch nicht dort sind, werden zuerst gesendet.
3. **Sieh dir das Ergebnis an:** ein Häkchen oder ein rotes Kreuz an jeder Datei und wie viel Platz frei wird.
4. **Sage Yes.** Android fragt, ob die bestätigten Dateien in den Papierkorb des Telefons verschoben werden sollen. Siehe [[dialog-android-trash]].
5. **Leere den Papierkorb**, wenn du bereit bist. Erst dann kommt der Platz zurück.

## Was „sicher“ bedeutet
Die Cloud muss genau in diesem Moment bestätigen, dass sie die Datei in derselben Größe hat. Wenn die Cloud nicht gefragt werden kann, wird nichts entfernt. Bei einem optimierten Foto wird die Größe des Originals geprüft.

## Welche Clouds
OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 und Backblaze B2. Nicht Google Photos.

## Gut zu wissen
• Archive braucht dich vor Ort: Android verlangt ein Tippen. Siehe [[dialog-exit-warning]].
• Dateien, die später zu einem Archive-Album hinzukommen, sind ebenfalls abgedeckt.
• Wenn die letzte Datei geht, verschwindet das Album aus der Liste, und sein Modus wird vergessen. Kommt es zurück, beginnt es bei Off.""",
        "en_hash": "767ff53f0ad97065",
    },
    "archive-hero": {
        "title": "Files to Archive",
        "body": """**Files to Archive**, mit der Zahl der Dateien, die archiviert werden: in jedem Archive-Album der Albumliste oder im geöffneten Album. Dateien, die du auf dem Telefon behalten hast, werden nicht gezählt.

Darunter: deine Archive-Alben und ein Hinweis, dass archivierte Dateien im Tab **Restore** zurückgeholt werden können.

Jede Datei wird mit deiner Cloud abgeglichen, bevor etwas das Telefon verlässt. Eine Datei, die noch nicht in deiner Cloud ist, wird zuerst gesendet und bestätigt, erst dann wird sie gezählt.

**▲ Hide Details ▲** am unteren Rand der Karte klappt sie auf Titel, Zahl und Hauptschaltfläche zusammen; **▼ Show Details ▼** holt die Albumnamen, die Auswahl **Only show files older than**, die Erklärung und den Hinweis auf **Restore** zurück. **Check these files**, die Ergebnisse und die Yes/No-Frage bleiben sichtbar. Der Tab merkt sich deine Wahl.""",
        "en_hash": "487804f5f7434e77",
    },
    "archive-folder-list": {
        "title": "Die Archive-Alben",
        "body": """Eine Karte pro Archive-Album: wie viele Dateien warten und ihre Größe, dazu gegebenenfalls behaltene, durch den Altersfilter zurückgehaltene, bestätigte oder nicht bestätigte.

• In der Albumliste heißt die Schaltfläche **Check all albums** und prüft jedes Album.
• Tippe auf ein Album, um es zu öffnen; **Check these files** prüft dann nur dieses Album.

Die Wahl eines Albums entfernt nichts und ändert keinen Modus.""",
        "en_hash": "e1a713efaeea9168",
    },
    "archive-check-button": {
        "title": "Check these files und was gemeldet wird",
        "body": """• **Check these files** (oder **Check all albums**): startet die Prüfung.
• **Checking with your Cloud...**, während die Cloud befragt wird.
• **Batch**-Fortschritt, während Dateien entfernt werden.
• Wie viele Dateien entfernt wurden und wie viel Platz frei wurde, oder **Nothing was removed.**

OneDrive wird geprüft, indem der Ordner jedes Albums in OneDrive aufgelistet wird (Name und Größe). Dropbox, Google Drive, pCloud, Backblaze B2 und IDrive e2 werden zu jeder einzelnen Datei befragt.

„Frei werden“ bedeutet: nachdem du den Papierkorb geleert hast.""",
        "en_hash": "651062d720e9e712",
    },
    "archive-age-filter": {
        "title": "Only show files older than…",
        "body": """Begrenzt diese Prüfung auf Dateien, die mindestens so alt sind: **1 hour** bis **1 year**, oder **All**. Neuere Dateien sind ausgegraut und bleiben für eine spätere Prüfung.

Er beginnt bei deinem Standard aus Settings. Eine Änderung hier gilt nur für diesen Besuch.""",
        "en_hash": "3d5d116005b80474",
    },
    "archive-empty": {
        "title": "Wenn nichts zu archivieren ist",
        "body": """• **Kein Album steht auf Archive:** stelle eines im Albums-Tab ein.
• **Eine Null:** alles in deinen Archive-Alben hat das Telefon schon verlassen.
• **This version of Android has no media trash:** Archive braucht Android 11 oder neuer.""",
        "en_hash": "ebf6a50a3d8d1d39",
    },
    "archive-file-list": {
        "title": "Die Dateiliste und ihre Markierungen",
        "body": """Eine Karte pro Datei in deinen Archive-Alben.

## Eine Datei auf dem Telefon behalten
Nutze das für eine Datei, die du auf dem Telefon behalten willst, während der Rest des Albums archiviert wird. Wische **nach links**, um sie zu behalten: Sie wird grau, zeigt **Not archiving** und wird gemerkt. Wische **nach rechts**, um sie wieder einzubeziehen. Eine Datei, die als **too recent for your filter** ausgegraut ist, wird vom Altersfilter zurückgehalten.

## Markierungen
• **Ladekreis:** wird geprüft, gesendet oder entfernt.
• **Grünes Häkchen:** in deiner Cloud bestätigt oder entfernt.
• **Rotes Kreuz:** nicht bestätigt; sie bleibt auf dem Telefon.

## Meldungen
**Backing up...**, **Moving to Trash/Recycle Bin...**, **Removed from this phone.**, oder in Rot, warum eine Datei bleibt: **Could not check your Cloud**, **Not in your Cloud** oder **In your Cloud but the wrong size**.""",
        "en_hash": "4ef90825cb796912",
    },
    "archive-prompt": {
        "title": "Die Frage: All files validated oder nur einige validated",
        "body": """Nach der Prüfung:
• **All files validated:** jede Datei ist bestätigt.
• **N files validated:** nur diese sind bestätigt; die übrigen bleiben auf dem Telefon.
• **Nothing can be archived:** keine wurde bestätigt. **Continue** schließt die Meldung.

**Yes** bittet Android, die bestätigten Dateien in den Papierkorb zu verschieben. **No** entfernt nichts. Ein großes Album braucht möglicherweise mehrere Android-Bestätigungen (je 2.000 Dateien).""",
        "en_hash": "4155582f22850887",
    },
    "dialog-android-trash": {
        "title": "Die eigene Bestätigung von Android",
        "body": """Android bittet dich zu erlauben, dass die Dateien in den Papierkorb verschoben werden. Der Wortlaut variiert je nach Telefon.

• Android erlaubt 2.000 Dateien pro Anfrage, daher wird ein großes Album in Stapeln erledigt.
• Wenn du Nein sagst, wird nichts entfernt.""",
        "en_hash": "ea86c62351435e6f",
    },
    "dialog-exit-warning": {
        "title": "Files ready to Archive (die Erinnerung beim Verlassen)",
        "body": """Erscheint, wenn du mit Zurück gehst, während Dateien bestätigt sind und auf das Archivieren warten.

• **Archive now:** öffnet den Archive-Tab.
• **Leave:** schließt die App. Nichts wird entfernt.

Zählt nur OneDrive-Dateien: Nur OneDrive-Kopien werden vorab bestätigt. Andere Clouds werden geprüft, wenn du **Check these files** drückst. Die Erinnerung erscheint nicht, wenn du Home drückst oder die App wegwischst.""",
        "en_hash": "973610b6e4444771",
    },
}
