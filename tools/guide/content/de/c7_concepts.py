TRANSLATIONS = {
    "_chapter": {
        "title": "So funktioniert es, in einfachem Deutsch",
        "intro": "Die Ideen hinter den Schaltflächen.",
        "en_hash": "a50822757f585a7f",
    },
    "modes-in-depth": {
        "title": "Die vier Modi",
        "body": """Jedes Album hat einen Modus, festgelegt mit der Schaltfläche auf seiner Karte.

• **Off:** dieses Album wird nicht betreut: Nichts wird gesendet oder geändert. Jedes Album beginnt hier.
• **Backup:** kopiert in deine Cloud; das Telefon wird nie angefasst.
• **Sync:** sichert, dann wird optimiert, wenn das Optimieren in Settings an ist.
• **Archive:** sichert, überprüft und verschiebt die Dateien dann nach deinem Tippen in den Papierkorb des Telefons. Der einzige Modus, der Dateien entfernt.

## Welche Clouds
Backup funktioniert mit jeder Cloud. Sync und Archive brauchen OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 oder Backblaze B2, nicht Google Photos.

## Camera folder
Wenn du in Settings einen Camera folder festlegst, bietet er Off, Backup und Archive, mit eigener Optimise-Steuerung statt Sync. Siehe [[album-camera-optimise]].

## Was du wählen solltest
• So lassen, aber geschützt: **Backup**.
• Platz sparen, aber weiter ansehen: **Sync**.
• Alte Alben, die vom Telefon runter sollen: **Archive**.
• Unsicher: **Off** oder **Backup**.

## Gut zu wissen
• Nur du legst Modi fest. Die einzige Ausnahme ist das Zusammenführen doppelter Alben, das **Off** setzt. Siehe [[album-merge-warning]].
• Der Wechsel zu Archive fragt nach Bestätigung. Der Wechsel weg davon stoppt weitere Entfernungen.""",
        "en_hash": "43d293213ae2b8f7",
    },
    "how-verification-works": {
        "title": "So funktioniert die Überprüfung",
        "body": """Bevor etwas optimiert oder entfernt wird, wird die Cloud genau in diesem Moment gefragt, ob sie eine gute Kopie hat.

## Der Test
• **OneDrive:** listet den Albumordner auf; die Datei muss denselben Namen und dieselbe Größe haben.
• **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2:** zur genauen Datei befragt; sie muss vorhanden sein, nicht in ihrem Papierkorb liegen und dieselbe Größe haben. Bei einem optimierten Foto zählt die Größe des Originals.

## Wann
• Zu Beginn des ersten Backups, damit nur fehlende Dateien gesendet werden.
• Jedes Mal, wenn du den Albums-Tab öffnest oder Rescan drückst (OneDrive).
• Wenn du im Archive-Tab Dateien prüfst, und noch einmal kurz vor dem Entfernen.
• Kurz bevor eine Datei optimiert wird.

## Drei Antworten
• **Verified:** die Cloud hat sie.
• **Nicht vorhanden oder falsche Größe:** die Datei bleibt auf dem Telefon.
• **Konnte nicht geprüft werden:** gilt als unbekannt. Nichts wird entfernt oder optimiert.

## Gut zu wissen
Eine Größenprüfung kann nicht jede Art von Schaden erkennen. Öffne ab und zu deine Cloud und sieh dir ein paar Fotos an.""",
        "en_hash": "3a19869102cce663",
    },
    "how-optimising-works": {
        "title": "So funktioniert das Optimieren",
        "body": """Das Optimieren tauscht die Kopie auf dem Telefon gegen eine kleinere aus, sodass das Telefon nicht mehr volläuft, während alles in deiner Galerie bleibt.

## Fotos
Auf etwa 2048 Pixel an der langen Kante verkleinert, ungefähr ein Zehntel der Größe. Gleicher Name, Ort, Datum und Standort, mit einem kleinen Wolkensymbol.

## Video
Neu kodiert mit **480p** (High compression), **720p** (Medium) oder **1080p** (Low). Nie abgeschnitten. Automatic wartet auf das Ladegerät; Manual läuft bei **Sync now**.

## Regeln
• Nur in **Sync**-Alben.
• Nur nachdem die Cloud das Original in derselben Größe bestätigt hat.
• Eine Datei, die nicht kleiner würde, bleibt unberührt.

## Das Original zurückholen
Nutze den Tab **Restore**. Stelle vor dem Bearbeiten wieder her, wenn du alle Details willst.""",
        "en_hash": "1c7d13215d15f27e",
    },
    "where-deleted-files-go": {
        "title": "Wohin entfernte Dateien gehen",
        "body": """Nichts wird dauerhaft gelöscht.

## Auf deinem Telefon
Archive verschiebt Dateien für etwa 30 Tage in den Papierkorb von Android:
• Samsung: der **Papierkorb von Samsung Gallery**.
• Andere Telefone: der **Papierkorb** der **Files**-App.

Dateien im Papierkorb belegen weiter ihren vollen Platz, bis du ihn leerst.

## In deiner Cloud
Bei **Ask** gehen Kopien, deren Entfernung du wählst, in den **OneDrive-Papierkorb**. Aus anderen Clouds wird nie etwas entfernt. Die App leert nie einen Papierkorb.""",
        "en_hash": "ab2cd9530770d041",
    },
    "background-work": {
        "title": "Wann was passiert",
        "body": """• **Sichern:** kurz nachdem neue Fotos erscheinen, und alle sechs Stunden. Nur über Wi-Fi, außer mobile Daten sind an; nicht bei niedrigem Akkustand oder wenig Speicher. **Sync now** läuft sofort.
• **Optimising photos:** Automatic, sobald die Cloud eine Datei in einem Sync-Album bestätigt. Manual, bei **Sync now**.
• **Optimising video:** Automatic, nach Backups, beim Laden. Manual, bei **Sync now**.
• **Gelöschte Dateien:** bei **Ask** wirst du kurz nach dem Löschen einer Datei mit Cloud-Kopie benachrichtigt (nicht, solange Backups pausiert sind).
• **Archivieren:** nur, wenn du im Archive-Tab Yes sagst.
• **Wiederherstellen:** nur, wenn du Restore drückst.

## Die App schließen
Zurück oder Home ist in Ordnung: Die Arbeit läuft weiter. Wenn du die App aus den zuletzt verwendeten Apps wegwischst, kann die Hintergrundarbeit stoppen, bis du sie wieder öffnest.""",
        "en_hash": "b52b93819f999605",
    },
    "where-your-data-lives": {
        "title": "Wo deine Daten liegen",
        "body": """• **Fotos und Videos:** gehen direkt zwischen deinem Telefon und deiner eigenen Cloud, verschlüsselt. Es gibt keine GallerySync-Server.
• **Anmeldungen und Schlüssel:** werden verschlüsselt auf deinem Telefon gespeichert. Passwörter werden auf der Seite des Anbieters eingegeben, nie in der App.
• **Die Aufzeichnungen und Einstellungen der App:** privater Speicher auf deinem Telefon.
• **Nichts wird erfasst:** keine Analysen, keine Absturzberichte, keine Werbung.

Details: **Privacy Policy** und **Delete Account Info** in Settings.""",
        "en_hash": "989af3b026b90273",
    },
    "numbers-explained": {
        "title": "Warum die Zahlen von denen deiner Galerie-App abweichen können",
        "body": """GallerySync zählt nur Dateien, die jetzt auf dem Telefon sind, in den Ordnern, die du gewählt hast, nicht im Papierkorb. Deine Galerie zählt vielleicht reine Cloud-Elemente oder gruppiert Ordner anders. Beides stimmt; es werden unterschiedliche Dinge gezählt.""",
        "en_hash": "54fab5cd25e07a35",
    },
}
