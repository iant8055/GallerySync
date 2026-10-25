TRANSLATIONS = {
    "_chapter": {
        "title": "Der Albums-Tab",
        "intro": "Hier wählst du den Modus jedes Albums und startest Backups.",
        "en_hash": "7c39f69f07321c58",
    },
    "albums-overview": {
        "title": "So ist der Albums-Tab aufgebaut",
        "body": """• **Grüne Kopfzeile:** Modus-Schaltflächen, Summen und die Backup-Steuerung.
• **Statuszeile:** wird angezeigt, während etwas läuft oder gerade fertig geworden ist.
• **Albumliste:** eine Karte pro Album, von A bis Z. Zwei Spalten auf einem breiten Bildschirm.

Jedes Mal, wenn du den Tab öffnest, sieht er sich das Telefon an und fragt OneDrive erneut (**Rescan** zeigt dann **Checking...**). Andere Clouds werden zu einer Datei befragt, kurz bevor sie optimiert oder entfernt wird.

Ansehen, Filtern und Scrollen verändern nie eine Datei. Nur die Wahl eines Modus tut das.""",
        "en_hash": "3c7f69542cac04f4",
    },
    "albums-new": {
        "title": "Neue Alben",
        "body": """Wenn ein neues Album erscheint, sagt ein Pop-up **1 new Album is waiting for you to choose a mode**.
• **Show new Albums:** zeigt nur die neuen. **Show all Albums** bringt die übrigen zurück.
• **Dismiss:** schließt die Meldung.

Neue Alben tragen die Markierung **New** und beginnen immer bei **Off**. Ein neues Album kann nie als Archive beginnen.""",
        "en_hash": "8f8d4b55c34a1c4d",
    },
    "albums-filter": {
        "title": "All Albums und die vier Modus-Schaltflächen",
        "body": """• Tippe auf **Backup**, **Sync**, **Archive** oder **Off**, um nur die Alben in diesem Modus zu zeigen.
• Tippe auf **All Albums** oder noch einmal auf dieselbe Schaltfläche, um alle zu zeigen.

Filtern ändert nur, was du siehst. Um einen Modus zu ändern, nutze die Schaltfläche auf der Karte des Albums. Siehe [[modes-in-depth]].""",
        "en_hash": "d4865c5cf08d3857",
    },
    "albums-totals": {
        "title": "Album Mode Count",
        "body": """**Album Mode Count:** die Summe für jeden Modus. Wird angezeigt, wenn **All Albums** ausgewählt ist. Alben unter **Off** sind die, über die du noch nicht entschieden hast.""",
        "en_hash": "61afcd38a3bee22c",
    },
    "albums-all-backed-up": {
        "title": "Alles hier ist gesichert",
        "body": """Wird angezeigt, wenn jedes Album in der Ansicht Dateien hat und keine auf das Senden wartet. Es stammt aus der eigenen Aufzeichnung der App; die Antwort der Cloud selbst steht in der letzten Zeile jeder Albumkarte.""",
        "en_hash": "79b43044d6005ada",
    },
    "albums-optimised-line": {
        "title": "Optimised and Saved",
        "body": """Bei ausgewähltem **Sync**: wie viele Dateien in Sync-Alben optimiert wurden und wie viel Platz gespart wurde.

Die Originale bleiben in der Cloud. **Restore** bringt sie zurück. Siehe [[how-optimising-works]].""",
        "en_hash": "1fbc295f2322e4d0",
    },
    "albums-archive-lines": {
        "title": "Files archived and Scheduled to leave this phone",
        "body": """Bei ausgewähltem **Archive**:
• **N files archived · X in your Cloud:** Dateien, die das Telefon verlassen haben, und der Platz, den ihre Kopien in der Cloud belegen.
• **N Scheduled to leave this phone:** Dateien, die noch auf dem Telefon in Archive-Alben liegen, ohne die Dateien, die du behalten wolltest. **Nothing left to archive**, wenn es keine gibt.

Hier wird nichts entfernt. Das Entfernen geschieht im Archive-Tab. Siehe [[archive-overview]].""",
        "en_hash": "00b01adb4a7cf037",
    },
    "albums-run-controls": {
        "title": "Sync now, Rescan, Pause, Resume and Stop",
        "body": """## Wenn nichts läuft
• **Sync now:** sichert jetzt. Ist in Settings ein Optimierungsschalter an und sein **Modus** auf **Manuell** gestellt, wird danach auch optimiert (siehe [[settings-optimise-mode]]). Ausgegraut, wenn nichts zu senden oder zu optimieren ist.
• **Rescan:** sieht sich das Telefon an und fragt OneDrive erneut.

## Während ein Backup läuft
• Der Fortschritt erscheint als **Syncing** mit einem Prozentwert (nach Datenmenge, nicht nach Dateizahl).
• **Pause:** hält alle Backups an, bis du **Resume** drückst.
• **Stop:** beendet diesen Durchlauf. Automatische Backups laufen weiter.

## Gut zu wissen
• **Sync now** wartet nicht auf das Ladegerät, beachtet aber weiterhin deine Einstellung „Mobile Daten verwenden“. Siehe [[settings-mobile-data]].
• Es sichert jedes Album, das auf **Backup**, **Sync** oder **Archive** steht, optimiert aber nur Dateien in **Sync**-Alben.""",
        "en_hash": "846b4240970f2d4a",
    },
    "albums-status-line": {
        "title": "Die Statuszeile unter der Karte",
        "body": """• **Beim Hochladen:** die Dateizahl, die gerade gesendete Datei und ihr Fortschritt.
• **Bei der Vorbereitung:** **Scanning...** oder **No permission to read media.**
• **Nach einem Durchlauf:** eine Zusammenfassung dessen, was zutrifft: **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (später erneut versucht), **still to go** oder **stopped:** mit dem Grund.""",
        "en_hash": "78b2b8f3d6801853",
    },
    "albums-list": {
        "title": "Der Albums-Tab",
        "body": """## Kopfzeile
• Tippe auf **Backup**, **Sync**, **Archive** oder **Off**, um nur die Alben in diesem Modus zu zeigen. Tippe auf **All Albums**, um wieder alle zu zeigen.
• Die Zeile unter den Schaltflächen nennt die Summe für jeden Modus.
• **▲ Hide Details ▲** am unteren Rand der Karte blendet die Zeile mit der Modus-Summe aus; **▼ Show Details ▼** holt sie zurück. **All Albums**, die vier Modus-Schaltflächen sowie **Sync now** / **Rescan** bleiben. Der Tab merkt sich deine Wahl.

## Albumkarten
• **Name, Dateizahl und Größe** des Albums auf deinem Telefon.
• **Zweite Zeile** (nur bei Bedarf): wie viele Dateien optimiert, in voller Größe behalten, ausstehend oder fehlgeschlagen sind.
• **Cloud-Zeile:** **verified in OneDrive** bedeutet, dass OneDrive die Dateien in der richtigen Größe bestätigt hat. Das wird bei jedem Öffnen des Tabs geprüft. Andere Clouds zeigen **sent to** und die jeweilige Cloud.
• **Modus-Schaltfläche** (rechte Seite): tippen, um den Modus des Albums zu ändern. Archive fragt zuerst nach Bestätigung. Wenn die Cloud des Albums diesen Modus nicht kann, sagt die Karte das.
• **Tippe auf die Karte**, um die Dateien des Albums zu sehen. Siehe [[album-detail]].""",
        "en_hash": "6f8771afe616ceed",
    },
    "album-detail": {
        "title": "Die Dateiliste eines Albums",
        "body": """Öffnet sich, wenn du auf eine Albumkarte tippst.

• **Kopfzeile:** Zurück-Pfeil, Albumname, Modus und Dateizahl, **Backs up to** (die Cloud und der Ordner, in die neue Dateien gehen, plus jede andere Cloud, die schon einige hält), die Zahlen ([[album-file-status]]), **Sort by** ([[album-file-sort]]) und **Keep at full size** ([[album-file-pin]]).
• **▲ Hide Details ▲** am unteren Rand der Kopfzeile klappt sie auf Albumname, Modus und Dateizahl zusammen; **▼ Show Details ▼** holt den Rest zurück. Die Seite merkt sich deine Wahl.
• **Dateien:** eine Karte pro Datei mit Größe, Datum und Markierungen. Eine optimierte Datei zeigt ihre kleinere Größe auf dem Telefon.
• **Kameraordner** (falls in Settings gewählt): zusätzliche Optimise-Steuerung. Siehe [[album-camera-optimise]].

Keine Miniaturansichten oder Vorschauen. Nutze deine Galerie-App, um Fotos anzusehen.""",
        "en_hash": "47ed510631cea71a",
    },
    "album-file-status": {
        "title": "Dateimarkierungen und Zahlen in einem Album",
        "body": """• **✓ backed up:** in der Cloud, auf dem Telefon in voller Größe.
• **✓ backed up · optimised:** in der Cloud; das Telefon hat eine kleinere Kopie.
• **⟳ pending:** wartet auf das Senden.
• **✗ failed:** konnte nach mehreren Versuchen nicht gesendet werden.

## Fehlgeschlagene erneut versuchen
**Retry** stellt fehlgeschlagene Dateien wieder in die Warteschlange und startet ein Backup. Es entfernt nichts.""",
        "en_hash": "a1305dbfcdea48b5",
    },
    "album-file-sort": {
        "title": "Die Dateien eines Albums sortieren",
        "body": """• **Name:** A bis Z.
• **Date:** neueste zuerst. Eine wiederhergestellte Datei trägt das Datum ihrer Wiederherstellung.
• **Status:** fehlgeschlagen, dann ausstehend, dann optimiert, dann gesichert.

Die Liste öffnet sich jedes Mal in Namensreihenfolge. Sortieren ändert nichts an den Dateien.""",
        "en_hash": "030835fb143a638a",
    },
    "album-file-pin": {
        "title": "Keep at full size",
        "body": """Ein Kontrollkästchen neben Dateien, die **Restore** zurückgebracht hat. Solange es ausgewählt ist, lässt GallerySync diese Datei in Ruhe:
• In einem **Sync**-Album wird sie nicht optimiert.
• In einem **Archive**-Album wird sie nicht entfernt.
• Bei **Backup** oder **Off** ändert sich nichts, aber die Wahl wird gemerkt.

Restore wählt es automatisch aus. Hebe die Auswahl auf, damit die Datei wieder dem Modus ihres Albums folgt.""",
        "en_hash": "34b89b8b5829a956",
    },
    "album-camera-optimise": {
        "title": "Ältere Fotos und Videos in deinem Kameraordner optimieren",
        "body": """Nur der **Kameraordner**, den du in Settings unter **Kamera-Sync** gewählt hast ([[settings-section-camera]]), hat diese Steuerung.

## Dateien auswählen
• **Select Photos/Videos older than:** **1 day** bis **1 year**, oder **All**.
• **Photos** und **Videos:** welche Arten ausgewählt werden.
• Wische eine Datei nach rechts, um sie auszuwählen, nach links, um die Auswahl aufzuheben. Die Karte zeigt, wie viele ausgewählt sind und die geschätzte Ersparnis.

## Schaltflächen
• **Sync now:** sichert alles, was im Ordner wartet, und optimiert dann nur die ausgewählten Dateien.
• **Rescan:** prüft die Cloud erneut.

## Gut zu wissen
• Nur Dateien, die OneDrive in voller Größe bestätigt hat, können ausgewählt werden.
• Nichts wird optimiert, bis du **Sync now** drückst. Neue Fotos bleiben, wie sie sind.
• Der Kameraordner hat keinen **Sync**-Modus, und die Optimierungsschalter in Settings wirken sich nicht auf ihn aus.
• Wenn du mit geänderten Auswahlen gehst, wirst du gefragt, ob sie deine Standardeinstellungen werden sollen.""",
        "en_hash": "f06a5a7ce89e99c4",
    },
    "album-camera-select": {
        "title": "Dateien in deinem Kameraordner auswählen",
        "body": """Zeigt, wie viele Dateien ausgewählt sind und wie viel Platz voraussichtlich gespart wird, oder **Nothing selected**.

• **Photos**, **Videos** und das Alter wählen jeden Treffer aus.
• Nach rechts wischen zum Auswählen, nach links zum Abwählen.
• **Sync now** optimiert nur die ausgewählten Dateien.

Eine ausgegraute Datei kann nicht ausgewählt werden: Sie ist noch nicht in OneDrive bestätigt oder schon optimiert. Siehe [[album-camera-optimise]].""",
        "en_hash": "9904773f638bae7b",
    },
    "album-merge-warning": {
        "title": "Doppelte Albumnamen erkannt",
        "body": """Ein einzelner Ordner kann unter zwei Schreibweisen erfasst werden, die sich nur in Groß- und Kleinschreibung unterscheiden. GallerySync erkennt das, führt sie zu einem Album zusammen und setzt dessen Modus auf **Off**, damit du neu wählen kannst. **Dismiss** schließt die Karte.

Das ist der einzige Fall, in dem die App einen Modus setzt, und es kann nur **Off** sein.""",
        "en_hash": "559da2287e55a253",
    },
    "dialog-archive-confirm": {
        "title": "Archive this album?",
        "body": """Wird angezeigt, wenn du **Archive** wählst:
• Archive prüft, ob alle Dateien in der Cloud sind.
• Archivierte Dateien wandern in den Papierkorb deines Telefons.
• Leere den Papierkorb, um den Platz freizugeben.

**Archive** bestätigt; **Cancel** ändert nichts.

## Gut zu wissen
Du bestätigst einmal. Dateien, die später zum Album hinzukommen, sind ebenfalls abgedeckt, bis das Album leer ist. Android fragt einmal pro Stapel von bis zu 2.000 Dateien. Siehe [[dialog-android-trash]].""",
        "en_hash": "ac36f7c88ce4bc76",
    },
}
