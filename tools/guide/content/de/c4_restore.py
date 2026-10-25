TRANSLATIONS = {
    "_chapter": {
        "title": "Der Restore-Tab",
        "intro": "Holt Fotos und Videos aus deiner Cloud zurück. Er liest nur aus der Cloud; dort wird nichts verändert.",
        "en_hash": "e472722c1c5b626f",
    },
    "restore-overview": {
        "title": "Was Restore zurückbringt",
        "body": """• **To restore:** eine optimierte Datei auf dem Telefon. Das Wiederherstellen setzt das Original in voller Größe an ihre Stelle.
• **To download:** eine Datei, die nicht mehr auf dem Telefon ist. Das Herunterladen legt sie zurück in ihr Album.

Wiederhergestellte Dateien erhalten **Keep at full size**, damit sie nicht gleich wieder optimiert oder archiviert werden. Siehe [[album-file-pin]].

## Welche Clouds
• **OneDrive:** jedes Foto und Video in deinen Backup-Ordnern, egal wer es dort abgelegt hat.
• **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2:** Dateien, die GallerySync von diesem Telefon gesendet hat.
• **Google Photos:** kann nicht wiederhergestellt werden.

## Wohin die Dateien gehen
Zurück in den Ordner, aus dem sie kamen. Dateien direkt oben in **DCIM**, **Pictures** oder **Movies** gehen zurück in diesen Ordner. Wenn in einen Ordner nicht geschrieben werden kann, kommen die Dateien in einen Ordner in **DCIM**, der nach dem Album benannt ist.

## Gefahrlos ausprobieren
Eine Datei wird erst ersetzt, nachdem der vollständige Download angekommen ist und seine Größe stimmt. Wenn etwas fehlschlägt, bleibt die Datei auf dem Telefon, wie sie war.""",
        "en_hash": "1bc7e998be6fc106",
    },
    "restore-hero": {
        "title": "Albums to Restore and Files in this album",
        "body": """• **Albumliste:** **Albums to Restore**, mit der Zahl der Alben, bei denen etwas zurückzuholen ist.
• **In einem Album:** Zurück-Pfeil, Albumname und **Files in this album** mit der Zahl und dem, was du ausgewählt hast.
• Wische eine Karte nach rechts, um sie auszuwählen, nach links, um die Auswahl aufzuheben; tippe, um sie zu öffnen. Das geht, egal ob die Kopfzeile sichtbar oder ausgeblendet ist.
• **▲ Hide Details ▲** am unteren Rand der Karte klappt sie auf Titel, Zahl und Hauptschaltfläche zusammen; **▼ Show Details ▼** holt den Wischhinweis, **Refresh** / **Clear** und die **Sort**- / Suchzeile des Ordners zurück. Ergebnisse und deine Auswahl bleiben sichtbar. Der Tab merkt sich deine Wahl.

Ein Strich bedeutet, dass die Liste noch lädt. **Checking your Cloud for more...** bedeutet, dass OneDrive gelesen wird. Eine rote Meldung bedeutet, dass ein Cloud-Ordner nicht erreicht werden konnte.

Die OneDrive-Liste wird gemerkt und nach zehn Minuten oder beim Drücken von **Refresh** erneut gelesen.""",
        "en_hash": "1099ec37091c62a1",
    },
    "restore-message-line": {
        "title": "Die Meldung unter der Zahl",
        "body": """• **Swipe right to select / left to deselect. Tap to open.** in der Albumliste.
• **Swipe right to select / left to deselect → Then press Restore.** in einem Album.
• **Nothing in your Cloud is missing from this phone, so there is nothing to bring back.** wenn die Liste leer ist.
• Nach einer Wiederherstellung, wie viele Dateien zurückgekommen sind und wie viele unverändert blieben.""",
        "en_hash": "5b14979898ba9b25",
    },
    "restore-selected-summary": {
        "title": "N ausgewählt und wie viel zurückgeholt wird",
        "body": """Die über alle Alben ausgewählten Dateien und der zusätzliche Platz, den sie auf deinem Telefon belegen werden. Achte darauf, dass dieser Platz frei ist.""",
        "en_hash": "91898ed468f32d87",
    },
    "restore-buttons": {
        "title": "Refresh, Select all and Clear",
        "body": """• **Refresh** (Albumliste): liest die Liste erneut, einschließlich OneDrive.
• **Select all** (in einem Album): wählt jede Datei darin aus.
• **Clear:** entfernt jede Auswahl.

Ausgegraut, solange eine Wiederherstellung läuft.""",
        "en_hash": "69730fbd93d70feb",
    },
    "restore-folders-list": {
        "title": "Die Albumliste",
        "body": """Jede Albumkarte zeigt:
• Wie viele Dateien zurückkommen können und den Platz, den sie brauchen.
• Wie viele **to restore** und **to download** sind.
• Wie viele **already on this phone** sind (innen ausgegraut; siehe [[restore-greyed-files]]).
• Wie viele **selected here** sind, oder ein Häkchen, wenn das ganze Album ausgewählt ist.

**Tippen** zum Öffnen. **Nach rechts wischen** wählt das ganze Album aus, **nach links** hebt die Auswahl auf.""",
        "en_hash": "54ceae2583fe13a0",
    },
    "restore-files-list": {
        "title": "Die Dateiliste in einem Album",
        "body": """Nach rechts wischen (oder tippen) zum Auswählen, nach links zum Abwählen. Jede Datei zeigt eines davon:
• **now · full size:** eine optimierte Kopie, die ihre kleinere Dateigröße auf dem Telefon und die volle Dateigröße in der Cloud zeigt.
• **Available for download:** nicht auf dem Telefon.
• **Already on this phone:** ausgegraut; nichts zu tun.

Während der Arbeit: **Working...** mit einem Prozentwert. Danach **Restored to full size**, **Back on this phone** oder **Could not restore, your file is unchanged** mit dem Grund.""",
        "en_hash": "486e3275bedd2609",
    },
    "restore-greyed-files": {
        "title": "Warum sind manche Dateien ausgegraut?",
        "body": """Sie sind schon in voller Größe auf dem Telefon (gleiches Album, gleicher Name, gleiche Größe), also gibt es nichts wiederherzustellen. Sie werden angezeigt, damit das Album zu deiner Cloud passt.

Eine Datei mit demselben Namen, aber anderer Größe wird nicht angeboten: Es kann eine Bearbeitung sein, und Restore überschreibt nie eine Bearbeitung.""",
        "en_hash": "02372472f6d53985",
    },
    "restore-action-bar": {
        "title": "Restore and Stop restoring",
        "body": """• **Restore:** holt jede ausgewählte Datei zurück, eine nach der anderen. Erscheint, sobald etwas ausgewählt ist.
• **Stop restoring:** stoppt den Stapel. Bereits wiederhergestellte Dateien bleiben wiederhergestellt.

Ein Fortschrittsbalken zeigt, wie viele wiederhergestellt sind. Braucht eine Internetverbindung und genug freien Speicherplatz.""",
        "en_hash": "52b0a1302c2c28cb",
    },
    "restore-edited": {
        "title": "Ein Foto oder Video, das du bearbeitet hast",
        "body": """Wenn du eine optimierte Kopie bearbeitet und darüber gespeichert hast, würde das Wiederherstellen deine Bearbeitung ersetzen, also fragt die App: **This photo has been edited since it was backed up. Overwrite it with the original?**

• **Keep my edits:** lässt die Datei, wie sie ist.
• **Overwrite:** legt das Original zurück; deine Bearbeitung wird ersetzt.

## Gut zu wissen
Die meisten Editoren speichern eine neue Datei, die nie betroffen ist. Eine bearbeitete Datei wird als neue Datei gesichert, mit **edited** und dem Datum im Namen; das Original in deiner Cloud wird nie ersetzt.""",
        "en_hash": "6292714bbfa760d3",
    },
}
