TRANSLATIONS = {
    "_chapter": {
        "title": "Der Settings-Tab",
        "intro": "Eine scrollende Seite in grünen Bändern: General, Backup, Albums, Sync, Camera Sync, Restore, Archive und Help & Feedback. Jedes Band hat ein (?).",
        "en_hash": "fdc210cbde6172d8",
    },
    "settings-overview": {
        "title": "So ist der Settings-Tab aufgebaut",
        "body": """• Änderungen wirken sofort. Es gibt keine Schaltfläche zum Speichern.
• Nichts hier entfernt eine Datei von deinem Telefon. Das tut nur Archive.
• Settings und die Ersteinrichtung sind getrennt. Siehe [[setup-and-settings]].""",
        "en_hash": "523b8f790e8db56b",
    },
    "settings-section-general": {
        "title": "General",
        "body": """Der **How To Guide**, **Appearance** und **Use mobile data**. **Language** ist die letzte Zeile der Seite.""",
        "en_hash": "b8038bbcadfcd8b3",
    },
    "settings-how-to-guide-card": {
        "title": "Die Karte How To Guide",
        "body": """Öffnet diese Anleitung. **Read more in the How To Guide** in einem beliebigen **(?)** öffnet sie bei diesem Thema. Braucht eine Internetverbindung; wenn sie nicht lädt, nutze **Open in browser**.""",
        "en_hash": "a7dec55c7b2218a4",
    },
    "settings-language": {
        "title": "Language",
        "body": """Die Sprache, die du auf dem Willkommensbildschirm gewählt hast. Tippe darauf, um sie zu ändern. Der How To Guide folgt dieser Wahl; die Bildschirme der App sind vorerst auf Englisch.""",
        "en_hash": "e1597813fc836e98",
    },
    "settings-appearance": {
        "title": "Appearance",
        "body": """• **System** (Standard): folgt der Hell- oder Dunkel-Einstellung deines Telefons.
• **Light** oder **Dark**: immer dieses Aussehen.

Die Anleitung und die Richtlinienseiten folgen der Einstellung deines Telefons.""",
        "en_hash": "b2ab8ff47282bc06",
    },
    "settings-mobile-data": {
        "title": "Use mobile data",
        "body": """• **Off** (Standard): **Wi-Fi only.**
• **On:** **Syncs on Wi-Fi and mobile data.**

Gilt für automatische Backups und **Sync now**. Lass es aus, es sei denn, dein Datentarif ist unbegrenzt.""",
        "en_hash": "e8860c60b1961fee",
    },
    "settings-section-backup": {
        "title": "Backup",
        "body": """• **Clouds:** verbinde jede Cloud oder melde dich bei ihr ab. Siehe [[settings-account]].
• **Where each folder goes:** zu welcher Cloud jeder Ordner sendet. Siehe [[settings-destination]].

Eine Cloud ist als **main (free)** markiert: die erste Cloud, die du gewählt hast. Sie ist immer kostenlos. Andere Clouds brauchen **Pro**: 30 Tage kostenlos (**Start 30-day free trial**), danach ein einmaliger Kauf (**Unlock Pro**). Es wird nichts automatisch berechnet.""",
        "en_hash": "0db12d3ebcb633de",
    },
    "settings-account": {
        "title": "Deine Clouds und Sign out",
        "body": """Jede Cloud zeigt ihr Konto (oder **Not connected**) mit **Sign out**, **Connect** oder **Enter keys**.

## Backup folder
Unter jeder verbundenen Cloud (außer Google Photos): der Ordner, in den ihre Backups gehen, mit **Change**. Siehe [[dialog-destination]].

## Sign out
Zuerst eine Warnung: Backups in diese Cloud stoppen, nichts wird gelöscht. Wenn diese Cloud Dateien hält, die auf dem Telefon nicht in voller Größe sind (optimiert oder archiviert), fragt die App:
• **Restore them to the phone first:** holt sie alle zurück und meldet dich dann ab.
• **Leave them there:** meldet dich ab.
• **Cancel:** du bleibst angemeldet.

Ordner bleiben dieser Cloud zugeordnet und warten, bis du dich wieder anmeldest. Um sie anderswohin zu senden, ändere **Where each folder goes**.""",
        "en_hash": "788b2237bf1a173f",
    },
    "settings-destination": {
        "title": "Where each folder goes",
        "body": """Jeder Hauptordner (DCIM, Pictures, Movies), die Cloud, zu der er geht, und wo in dieser Cloud.

• Tippe auf die Cloud, um sie zu ändern. Das geht nur mit mehr als einer Cloud und mit Pro oder dem Test.
• Nur neue Dateien folgen einer Änderung. Bereits gesendete Dateien bleiben, wo sie sind.
• Ein Ordner, der ohne Pro einer zusätzlichen Cloud zugeordnet ist, sendet nichts und sagt das.""",
        "en_hash": "22adac22e50fbdf0",
    },
    "dialog-destination": {
        "title": "Where new backups go (der Change-Dialog)",
        "body": """Geöffnet durch **Change** neben dem **Backup folder** einer Cloud, in Settings oder auf ihrer Karte bei der Einrichtung.

• Gib den Ordnerpfad ein, oder nutze bei OneDrive **Browse...**.
• **Reset to default:** zurück zu **Samsung Gallery/DCIM** (OneDrive) oder **GallerySync** (andere Clouds).
• **Use this folder** speichert; **Cancel** ändert nichts.

Nur neue Dateien gehen in den neuen Ordner. Bereits gesicherte Dateien bleiben, wo sie sind. OneDrive prüft weiterhin frühere Ordner, daher wird nichts doppelt gesendet.""",
        "en_hash": "910e0378a5a8b208",
    },
    "dialog-folder-picker": {
        "title": "Choose a folder (die OneDrive-Auswahl)",
        "body": """Geöffnet durch **Browse...** bei OneDrive. Listet deine OneDrive-Ordner auf: Tippe zum Öffnen, **Up one folder** zum Zurückgehen, **Use this folder** zum Wählen, **New folder** zum Anlegen eines Ordners.""",
        "en_hash": "f45e3349352eea44",
    },
    "settings-section-albums": {
        "title": "Albums",
        "body": """Welche Ordner GallerySync beobachtet und was mit Cloud-Kopien geschieht, wenn du vom Telefon löschst. Neue Alben beginnen immer bei **Off**.""",
        "en_hash": "dad7459947f2d8e1",
    },
    "settings-folders": {
        "title": "Folders to back up",
        "body": """Die Ordner, in denen GallerySync nachsehen darf.

• **Add a folder:** öffnet die Ordnerauswahl von Android.
• **Remove:** wähle Ordner aus, drücke **Remove** und bestätige.

Nichts außerhalb dieser Ordner wird durchsucht oder verändert. Das Entfernen eines Ordners verliert nichts: Füge ihn wieder hinzu, und alles kommt zurück.""",
        "en_hash": "3789c04bb52baef5",
    },
    "dialog-remove-folder": {
        "title": "Stop watching this folder?",
        "body": """**Remove** hört auf, die ausgewählten Ordner zu beobachten; **Cancel** ändert nichts.

Nichts bereits Gesichertes geht verloren. Wenn du den Ordner wieder hinzufügst, kommen seine Alben und sein Verlauf zurück, ohne dass etwas doppelt gesendet wird.""",
        "en_hash": "5b84d0c4a6ec8a9a",
    },
    "settings-deletion": {
        "title": "Wenn du ein Foto oder Video von diesem Telefon löschst",
        "body": """• **Leave the Cloud copy** (Standard): Aus deiner Cloud wird nie etwas entfernt.
• **Ask me about the Cloud copy:** Wenn Dateien dein Telefon verlassen, fragt ein Fenster, was mit ihren Cloud-Kopien geschehen soll. Sind Benachrichtigungen erlaubt, wirst du kurz nach dem Löschen auch benachrichtigt. Nichts wird ohne dein Einverständnis entfernt. Siehe [[deleted-files-window]].

Vorerst nur OneDrive. Kopien in anderen Clouds werden nie entfernt.""",
        "en_hash": "5fce34ecb857a3f6",
    },
    "deleted-files-window": {
        "title": "Files deleted from your phone (das Fenster, das sich mit der App öffnet)",
        "body": """Öffnet sich vor dem Albums-Tab, wenn Dateien dein Telefon verlassen haben, bei **Ask**. Zu Beginn ist nichts ausgewählt.

## Fenster 1: eine Kopie ist in OneDrive
Wische nach rechts (oder tippe), um Dateien auszuwählen, deren OneDrive-Kopie entfernt werden soll.
• **Remove N from OneDrive:** bittet dich zu bestätigen und verschiebt diese Kopien dann in den OneDrive-Papierkorb. Nicht ausgewählte Dateien bleiben in OneDrive. Siehe [[dialog-remove-from-onedrive]].
• **Keep all in OneDrive.**
• **Decide later.**

## Fenster 2: keine Kopie gefunden
Diese Dateien liegen noch im Papierkorb deines Telefons.
• **Back up N to OneDrive:** sendet die ausgewählten Dateien aus dem Papierkorb. Nichts wird gelöscht.
• **Leave all in the trash.**
• **Decide later.**

## Gut zu wissen
• Unentschiedene Dateien bleiben auf der Liste, bis du entscheidest.
• Nicht aufgelistet: Dateien, die Archive entfernt hat, an Ort und Stelle bearbeitete Fotos und Dateien, die ohne Cloud-Kopie endgültig gelöscht wurden.
• Wenn mehr als die Hälfte deiner gesicherten Dateien scheinbar auf einmal verschwindet, wird nichts angeboten; es wird als fehlerhafter Scan behandelt und nicht als echtes Löschen.
• Zurück tut dasselbe wie **Decide later**.""",
        "en_hash": "4cf138e07d6f0844",
    },
    "dialog-remove-from-onedrive": {
        "title": "Remove files from OneDrive? (die Bestätigung)",
        "body": """Zeigt, wie viele Dateien es sind und wie groß sie sind. Sie gehen in den OneDrive-Papierkorb, den GallerySync nie leert.

**Remove from OneDrive** führt es aus; **Cancel** entscheidet nichts. Jede Datei, die auf das Telefon zurückgekommen ist, bleibt unberührt.""",
        "en_hash": "a5707f1ea8aad403",
    },
    "settings-section-sync": {
        "title": "Sync",
        "body": """Wie Fotos und Videos in **Sync**-Alben optimiert werden. Dateien ändern sich erst, nachdem die Cloud sie überprüft hat. Funktioniert mit OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 und Backblaze B2.""",
        "en_hash": "1147f4e959ac06bc",
    },
    "settings-optimise-photos": {
        "title": "Optimise photos",
        "body": """Standardmäßig **Off**. Wenn an, werden Fotos in Sync-Alben durch kleinere Kopien ersetzt: etwa 2048 Pixel an der langen Kante, ungefähr ein Zehntel der Größe. Sie bleiben in deiner Galerie mit einem kleinen Wolkensymbol. Die Originale bleiben in der Cloud.

Fotos, die schon klein genug sind, und Dateien mit **Keep at full size** bleiben unberührt. **Mode** legt fest, wann es läuft. Siehe [[settings-optimise-mode]].""",
        "en_hash": "dee027d401742ca5",
    },
    "settings-optimise-video": {
        "title": "Optimise video",
        "body": """Standardmäßig **Off**. Wenn an, erscheinen **Mode**, **Older than** und **Compression**.

Videos in Sync-Alben werden kleiner neu kodiert. Nichts wird je abgeschnitten. Die Originale bleiben in der Cloud.

## Welche Videos
In einem Sync-Album, verified in the Cloud, älter als deine Einstellung, in einem Ordner, auf den du Zugriff gegeben hast, und noch nicht optimiert.

## Wann
• **Automatic:** nach Backups, nur beim Laden.
• **Manual:** wenn du **Sync now** drückst.""",
        "en_hash": "09cf14026b288ef6",
    },
    "settings-optimise-mode": {
        "title": "Mode: Automatic or Manual",
        "body": """• **Automatic** (Standard): Dateien in Sync-Alben werden optimiert, sobald die Cloud sie bestätigt. Videos warten auf das Ladegerät.
• **Manual:** nichts geschieht, bis du im Albums-Tab **Sync now** drückst.

Fotos außerhalb der Ordner, auf die du Zugriff gegeben hast, brauchen die Bestätigung von Android, warten also, bis du die App öffnest oder **Sync now** drückst.""",
        "en_hash": "2d55959b2e538398",
    },
    "settings-video-age": {
        "title": "Older than (video)",
        "body": """Wie alt ein Video sein muss, bevor es optimiert wird: **Straight away**, **1 hour**, **12 hours**, **1 day** oder **1 week**. Das Backup wird nicht verzögert.

**Straight away** schließt Clips ein, die heute gedreht wurden; wenn du einen davon bearbeitest, beginnst du mit der kleineren Kopie, bis du sie wiederherstellst.""",
        "en_hash": "b69444389e56afe8",
    },
    "settings-video-quality": {
        "title": "Compression (video)",
        "body": """• **High compression, 480p:** kleinste Dateien, etwa 85 % gespart.
• **Medium compression, 720p:** etwa 75 % gespart.
• **Low compression, 1080p:** etwa 50 % gespart.

Typische Werte; die Ergebnisse variieren.

## Nachdem du sie verstärkt hast
Die App fragt **Re-optimise existing videos?** **Yes** lässt Videos, die zuvor zu klein zum Verkleinern waren, erneut versuchen. **No** lässt sie unberührt. Bereits optimierte Videos werden nicht angefasst.""",
        "en_hash": "b68338f9be222883",
    },
    "settings-section-restore": {
        "title": "Restore (Settings)",
        "body": """Ein Schalter für den Restore-Tab: **Show empty folders**.""",
        "en_hash": "58571cb29a96c3ed",
    },
    "settings-show-empty-folders": {
        "title": "Show empty folders",
        "body": """Standardmäßig **Off**. Wenn an, listet der Restore-Tab jeden OneDrive-Ordner auf, auch solche, bei denen nichts zurückzuholen ist.""",
        "en_hash": "44bada9612e80a0d",
    },
    "settings-section-camera": {
        "title": "Camera Sync (Settings)",
        "body": """**Special settings for Camera**, standardmäßig aus. Wenn an:
• **Camera folder:** der Ordner, in dem deine Kamera speichert. Wähle ihn aus der Liste oder **Other…**.
• **Select Photos/Videos older than**, **Photos**, **Videos:** Standardwerte für die Optimise-Steuerung dieses Ordners ([[album-camera-optimise]]).
• **Video compression:** für dort optimierte Videos.

Der Camera folder hat keinen **Sync**-Modus, und nichts wird optimiert, bis du darin **Sync now** drückst. Die Optimise-Schalter oben wirken sich nicht auf ihn aus.""",
        "en_hash": "d596a9c99a8de79d",
    },
    "settings-section-archive": {
        "title": "Archive (Settings)",
        "body": """• **Only show files older than:** wo der Filter des Archive-Tabs beginnt. Siehe [[settings-archive-default-age]].
• **Notify when files are ready to archive:** standardmäßig aus. Siehe [[settings-archive-notify]].""",
        "en_hash": "7b4561ddc010537d",
    },
    "settings-archive-default-age": {
        "title": "Only show files older than (Settings)",
        "body": """Wo der Altersfilter des Archive-Tabs beginnt: **1 hour**, **1 day**, **1 week**, **1 month**, **1 year** oder **All** (Standard). Eine Änderung im Archive-Tab gilt nur für diesen Besuch. Siehe [[archive-age-filter]].""",
        "en_hash": "215ff23fa51a5fca",
    },
    "settings-archive-notify": {
        "title": "Notify when files are ready to archive",
        "body": """Standardmäßig **Off**. Wenn an, wirst du einmal pro neuem Stapel archivierbarer Dateien benachrichtigt. Nur OneDrive-Dateien.

Der Albums-Tab und die Erinnerung beim Verlassen zeigen das ebenfalls, sodass du dich nie nur auf Benachrichtigungen verlassen musst. Wenn Benachrichtigungen blockiert sind, sagt der Schalter es.""",
        "en_hash": "a0b5e2290fe1eaae",
    },
    "settings-about-cards": {
        "title": "Help & Feedback und die Link-Karten unten",
        "body": """## Help & Feedback
• **Rate us on Play Store:** öffnet den Eintrag von GallerySync.
• **Bug Report:** wo du schreiben kannst, und **Copy details** für deine App-Version, dein Telefonmodell und deine Android-Version.

## Ganz unten
• **Privacy Policy.**
• **Delete Account Info:** wie du dich abmeldest, den Zugriff der App entfernst und ihre Daten löschst.
• **Contact Info:** die Adresse, mit **Copy address**.

Die Seiten werden aus dem Internet geladen. Wenn eine nicht lädt, nutze **Open in browser**.""",
        "en_hash": "8a454188917d719a",
    },
}
