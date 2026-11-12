TRANSLATIONS = {
    "_chapter": {
        "title": "Der Settings-Tab",
        "intro": "Eine scrollende Seite in grünen Bändern: Allgemein, Backup, Wohin jeder Ordner geht, Alben, Synchronisierung, Kamera-Sync, Wiederherstellen, Archiv und Hilfe und Feedback. Jedes Band hat ein (?).",
        "en_hash": "0295c3a8f1702248",
    },
    "settings-overview": {
        "title": "So ist der Settings-Tab aufgebaut",
        "body": """• Änderungen wirken sofort. Es gibt keine Schaltfläche zum Speichern.
• Nichts hier entfernt eine Datei von deinem Telefon. Das tut nur Archive.
• Settings und die Ersteinrichtung sind getrennt. Siehe [[setup-and-settings]].""",
        "en_hash": "523b8f790e8db56b",
    },
    "settings-section-general": {
        "title": "Allgemein",
        "body": """Die **Anleitung**, **Darstellung**, **Schriftart**, **Mobile Daten verwenden** und **Dateien nicht auf dem Telefon**. **Sprache** ist die letzte Zeile der Seite.""",
        "en_hash": "72502f5b1cbd1b1f",
    },
    "settings-how-to-guide-card": {
        "title": "Die Karte „Anleitung“",
        "body": """Öffnet diese Anleitung. **Mehr in der Anleitung** in einem beliebigen **(?)** öffnet sie bei diesem Thema. Braucht eine Internetverbindung; wenn sie nicht lädt, nutze **Im Browser öffnen**.""",
        "en_hash": "a7dec55c7b2218a4",
    },
    "settings-language": {
        "title": "Sprache",
        "body": """Die Sprache, die du auf dem Willkommensbildschirm gewählt hast. Tippe darauf, um sie zu ändern. Die Anleitung folgt dieser Wahl; die Bildschirme der App sind vorerst auf Englisch.""",
        "en_hash": "e1597813fc836e98",
    },
    "settings-appearance": {
        "title": "Darstellung",
        "body": """• **System** (Standard): folgt der Hell- oder Dunkel-Einstellung deines Telefons.
• **Hell** oder **Dunkel**: immer dieses Aussehen.

Die Anleitung und die Richtlinienseiten folgen ebenfalls dieser Einstellung.

## Schriftart
• **App** (Standard): die App stellt Text in ihrer eigenen Schriftart dar, sodass sie auf jedem Telefon gleich aussieht.
• **Telefon:** nutzt den eigenen Schriftstil des Telefons.

Die Schriftgröße folgt in beiden Fällen der Einstellung des Telefons.""",
        "en_hash": "a616649556d751e6",
    },
    "settings-mobile-data": {
        "title": "Mobile Daten verwenden",
        "body": """• **Aus** (Standard): **Nur WLAN.**
• **An:** **Synchronisiert über WLAN und mobile Daten.**

Gilt für automatische Backups und **Sync now**. Lass es aus, es sei denn, dein Datentarif ist unbegrenzt.""",
        "en_hash": "e8860c60b1961fee",
    },

    "settings-open-not-on-phone": {
        "title": "Dateien nicht auf dem Telefon",
        "body": """Was ein **langes Drücken** mit einer Datei macht, die nicht mehr auf deinem Telefon ist. Eine Datei, die auf dem Telefon ist, auch eine optimierte Kopie, wird immer vom Telefon aus geöffnet.

• **Öffnet in der Cloud-App oder im Browser** (Standard): öffnet die Datei in der App ihrer Cloud oder im Browser, wenn die App den Link nicht annimmt. Eine Cloud ohne Link verwendet die nächste Wahl.
• **Vorübergehend herunterladen:** kopiert die Datei in den Cache der App und öffnet sie. Die Kopie bleibt nur im Cache, kommt nie in deine Galerie und wird später gelöscht.

Der vorübergehende Download folgt **Mobile Daten verwenden** ([[settings-mobile-data]]). Bei Mobildaten fragt eine Datei über 750 MB vorher nach; im WLAN fragt sie nie. Dateien von Google Photos können nicht heruntergeladen werden: sie werden immer in Google Photos oder im Browser geöffnet.""",
        "en_hash": "1ac210a7b703cc21",
    },
    "settings-section-backup": {
        "title": "Backup",
        "body": """**Clouds:** verbinde jede Cloud oder melde dich bei ihr ab. Siehe [[settings-account]].

Eine Cloud ist als **Haupt-Cloud (kostenlos)** markiert: die erste Cloud, die du gewählt hast. Sie ist immer kostenlos. Andere Clouds brauchen **Pro**: 30 Tage kostenlos (**30 Tage kostenlos testen**), danach ein einmaliger Kauf (**Pro freischalten**). Es wird nichts automatisch berechnet. Sobald du Pro besitzt, verschwinden diese Zeilen, und **Pro freigeschaltet. Du kannst jede Cloud nutzen.** erscheint in der grünen **Backup**-Überschrift.""",
        "en_hash": "457d0ce467900c35",
    },
    "settings-account": {
        "title": "Deine Clouds und Abmelden",
        "body": """## Die Kopfzeile
Links **Clouds**, rechts **Backup-Ordner**. Darunter trennt eine helle Linie jede Cloud.

## Nicht verbunden
Name der Cloud und **Verbinden** oder **Schlüssel eingeben** stehen in derselben Zeile.

## Verbunden
Zwei Spalten:
• **Links:** Name der Cloud, Konto und **Abmelden**.
• **Rechts:** der Ordner, in den die Backups gehen, und **Ändern**. Google Photos hat hier keinen Ordner. Siehe [[dialog-destination]].

Die zwei Spalten gibt es nur in Settings. Die Cloud-Karten des Assistenten bleiben gestapelt.

## Abmelden
Zuerst eine Warnung: Backups in diese Cloud stoppen, nichts wird gelöscht. Wenn diese Cloud Dateien hält, die auf dem Telefon nicht in voller Größe sind (optimiert oder archiviert), fragt die App:
• **Erst auf das Telefon wiederherstellen:** holt sie alle zurück und meldet dich dann ab.
• **Dort lassen:** meldet dich ab.
• **Abbrechen:** du bleibst angemeldet.

Ordner bleiben dieser Cloud zugeordnet und warten, bis du dich wieder anmeldest. Um sie anderswohin zu senden, ändere **Wohin jeder Ordner geht**.""",
        "en_hash": "37be6bd6eea0ccf2",
    },
    "settings-destination": {
        "title": "Wohin jeder Ordner geht",
        "body": """Ein eigenes Band im Settings-Tab, zwischen **Backup** und **Alben**. Eine Zeile für jeden Ordner, den du unter **Ordner zum Sichern** hinzugefügt hast, auch wenn er leer ist (angezeigt als **Documents · 0 Dateien**).

• Jede Zeile nennt die Cloud, zu der er geht, und wo in dieser Cloud.
• Tippe auf die Cloud, um sie zu ändern. Das geht nur mit mehr als einer Cloud und mit Pro oder dem Test.
• Nur neue Dateien folgen einer Änderung. Bereits gesendete Dateien bleiben, wo sie sind.
• Ein Ordner, der ohne Pro einer zusätzlichen Cloud zugeordnet ist, sendet nichts und sagt das.
• Entfernst du einen Ordner unter **Ordner zum Sichern**, verschwindet seine Zeile hier sofort.

Eine Zeile sagt nur, wohin Dateien gehen würden. Es wird nichts hochgeladen, bevor das Album einen Modus hat. Siehe [[settings-folders]].""",
        "en_hash": "4ffe9112ae1b0294",
    },
    "dialog-destination": {
        "title": "Wohin neue Backups gehen (der Ändern-Dialog)",
        "body": """Geöffnet durch **Ändern** neben dem **Backup-Ordner** einer Cloud, in Settings oder auf ihrer Karte bei der Einrichtung.

• Gib den Ordnerpfad ein, oder nutze bei OneDrive **Durchsuchen…**.
• **Auf Standard zurücksetzen:** zurück zu **Samsung Gallery/DCIM** (OneDrive) oder **GallerySync** (andere Clouds).
• **Diesen Ordner verwenden** speichert; **Abbrechen** ändert nichts.

Nur neue Dateien gehen in den neuen Ordner. Bereits gesicherte Dateien bleiben, wo sie sind. OneDrive prüft weiterhin frühere Ordner, daher wird nichts doppelt gesendet.""",
        "en_hash": "910e0378a5a8b208",
    },
    "dialog-folder-picker": {
        "title": "Ordner wählen (die OneDrive-Auswahl)",
        "body": """Geöffnet durch **Durchsuchen…** bei OneDrive. Listet deine OneDrive-Ordner auf: Tippe zum Öffnen, **Einen Ordner höher** zum Zurückgehen, **Diesen Ordner verwenden** zum Wählen, **Neuer Ordner** zum Anlegen eines Ordners.""",
        "en_hash": "f45e3349352eea44",
    },
    "dialog-new-folder-cloud": {
        "title": "Wohin soll dieser Ordner gesichert werden? (nach Ordner hinzufügen)",
        "body": """Erscheint, nachdem **Ordner hinzufügen** unter **Ordner zum Sichern** den neuen Ordner neu eingelesen hat.

• **Wohin soll <Ordner> gesichert werden?** oben.
• Eine Zeile pro verbundener Cloud. Darunter zeigt **Geht nach <Cloud> / <Pfad>**, wohin die Dateien der ausgewählten Cloud gehen. Google Photos zeigt stattdessen **Geht in deine Google Photos-Mediathek** und hat kein **Ändern**.
• **Ändern** öffnet den Zieldialog für diese Cloud. Siehe [[dialog-destination]].
• **Alle Ordner, die an eine Cloud gehen, landen dort am selben Ort. Änderst du ihn, gehen nur neue Dateien dorthin.** Bereits gesendete Dateien bleiben, wo sie sind.
• **OK** setzt die Cloud. **Später entscheiden** und das Wegtippen ändern nichts, der Ordner bleibt bei der Haupt-Cloud und kann später auf seiner Zeile in [[settings-destination]] umgeleitet werden.

Nur in Settings. Der Assistent fragt dasselbe in seinem eigenen Schritt.""",
        "en_hash": "0034d417c7621eb5",
    },
    "settings-section-albums": {
        "title": "Alben",
        "body": """Welche Ordner GallerySync beobachtet und was mit Cloud-Kopien geschieht, wenn du vom Telefon löschst. Neue Alben beginnen immer bei **Off**.""",
        "en_hash": "dad7459947f2d8e1",
    },
    "settings-folders": {
        "title": "Ordner zum Sichern",
        "body": """Die Ordner, in denen GallerySync nachsehen darf.

• **Ordner hinzufügen:** öffnet die Ordnerauswahl von Android. Danach durchsucht GallerySync den neuen Ordner und fragt, wohin er gesichert werden soll. Siehe [[dialog-new-folder-cloud]].
• **Entfernen:** wähle Ordner aus, drücke **Entfernen** und bestätige.

Nichts außerhalb dieser Ordner wird durchsucht oder verändert. Das Entfernen eines Ordners verliert nichts: Füge ihn wieder hinzu, und alles kommt zurück.""",
        "en_hash": "32d6ed14d5bc023a",
    },
    "dialog-remove-folder": {
        "title": "Diesen Ordner nicht mehr beobachten?",
        "body": """**Entfernen** hört auf, die ausgewählten Ordner zu beobachten; **Abbrechen** ändert nichts.

Nichts bereits Gesichertes geht verloren. Wenn du den Ordner wieder hinzufügst, kommen seine Alben und sein Verlauf zurück, ohne dass etwas doppelt gesendet wird.""",
        "en_hash": "5b84d0c4a6ec8a9a",
    },
    "settings-deletion": {
        "title": "Wenn du ein Foto oder Video von diesem Telefon löschst",
        "body": """• **Kopie in der Cloud lassen** (Standard): Aus deiner Cloud wird nie etwas entfernt.
• **Mich zur Kopie in der Cloud fragen:** Wenn Dateien dein Telefon verlassen, fragt ein Fenster, was mit ihren Cloud-Kopien geschehen soll. Sind Benachrichtigungen erlaubt, wirst du kurz nach dem Löschen auch benachrichtigt. Nichts wird ohne dein Einverständnis entfernt. Siehe [[deleted-files-window]].

Vorerst nur OneDrive. Kopien in anderen Clouds werden nie entfernt.""",
        "en_hash": "5fce34ecb857a3f6",
    },
    "deleted-files-window": {
        "title": "Files deleted from your phone (das Fenster, das sich mit der App öffnet)",
        "body": """Öffnet sich vor dem Albums-Tab, wenn Dateien dein Telefon verlassen haben, bei **Mich zur Kopie in der Cloud fragen**. Zu Beginn ist nichts ausgewählt.

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
        "title": "Synchronisierung",
        "body": """Wie Fotos und Videos in **Sync**-Alben optimiert werden. Dateien ändern sich erst, nachdem die Cloud sie überprüft hat. Funktioniert mit OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 und Backblaze B2.""",
        "en_hash": "1147f4e959ac06bc",
    },
    "settings-optimise-photos": {
        "title": "Fotos optimieren",
        "body": """Standardmäßig **Aus**. Wenn an, werden Fotos in Sync-Alben durch kleinere Kopien ersetzt: etwa 2048 Pixel an der langen Kante, ungefähr ein Zehntel der Größe. Sie bleiben in deiner Galerie mit einem kleinen Wolkensymbol. Die Originale bleiben in der Cloud.

Fotos, die schon klein genug sind, und Dateien mit **Keep at full size** bleiben unberührt. **Modus** legt fest, wann es läuft. Siehe [[settings-optimise-mode]].""",
        "en_hash": "dee027d401742ca5",
    },
    "settings-optimise-video": {
        "title": "Videos optimieren",
        "body": """Standardmäßig **Aus**. Wenn an, erscheinen **Modus**, **Älter als** und **Kompression**.

Videos in Sync-Alben werden kleiner neu kodiert. Nichts wird je abgeschnitten. Die Originale bleiben in der Cloud.

## Welche Videos
In einem Sync-Album, verified in the Cloud, älter als deine Einstellung, in einem Ordner, auf den du Zugriff gegeben hast, und noch nicht optimiert.

## Wann
• **Automatisch:** nach Backups, nur beim Laden.
• **Manuell:** wenn du **Sync now** drückst.""",
        "en_hash": "09cf14026b288ef6",
    },
    "settings-optimise-mode": {
        "title": "Modus: Automatisch oder Manuell",
        "body": """• **Automatisch** (Standard): Dateien in Sync-Alben werden optimiert, sobald die Cloud sie bestätigt. Videos warten auf das Ladegerät.
• **Manuell:** nichts geschieht, bis du im Albums-Tab **Sync now** drückst.

Fotos außerhalb der Ordner, auf die du Zugriff gegeben hast, brauchen die Bestätigung von Android, warten also, bis du die App öffnest oder **Sync now** drückst.""",
        "en_hash": "2d55959b2e538398",
    },
    "settings-video-age": {
        "title": "Älter als (Video)",
        "body": """Wie alt ein Video sein muss, bevor es optimiert wird: **Sofort**, **1 Stunde**, **12 Stunden**, **1 Tag** oder **1 Woche**. Das Backup wird nicht verzögert.

**Sofort** schließt Clips ein, die heute gedreht wurden; wenn du einen davon bearbeitest, beginnst du mit der kleineren Kopie, bis du sie wiederherstellst.""",
        "en_hash": "b69444389e56afe8",
    },
    "settings-video-quality": {
        "title": "Kompression (Video)",
        "body": """• **Hohe Kompression — 480p:** kleinste Dateien, etwa 85 % gespart.
• **Mittlere Kompression — 720p:** etwa 75 % gespart.
• **Niedrige Kompression — 1080p:** etwa 50 % gespart.

Typische Werte; die Ergebnisse variieren.

## Nachdem du sie verstärkt hast
Die App fragt **Vorhandene Videos neu optimieren?** **Ja** lässt Videos, die zuvor zu klein zum Verkleinern waren, erneut versuchen. **Nein** lässt sie unberührt. Bereits optimierte Videos werden nicht angefasst.""",
        "en_hash": "b68338f9be222883",
    },
    "settings-section-restore": {
        "title": "Wiederherstellen (Settings)",
        "body": """Ein Schalter für den Restore-Tab: **Leere Ordner anzeigen**.""",
        "en_hash": "58571cb29a96c3ed",
    },
    "settings-show-empty-folders": {
        "title": "Leere Ordner anzeigen",
        "body": """Standardmäßig **Aus**. Wenn an, listet der Restore-Tab jeden OneDrive-Ordner auf, auch solche, bei denen nichts zurückzuholen ist.""",
        "en_hash": "44bada9612e80a0d",
    },
    "settings-section-camera": {
        "title": "Kamera-Sync (Settings)",
        "body": """**Spezielle Einstellungen für die Kamera**, standardmäßig aus. Wenn an:
• **Kameraordner:** der Ordner, in dem deine Kamera speichert. Wähle ihn aus der Liste oder **Anderer…**.
• **Select Photos/Videos older than**, **Photos**, **Videos:** Standardwerte für die Optimise-Steuerung dieses Ordners ([[album-camera-optimise]]).
• **Videokompression:** für dort optimierte Videos.

Der Kameraordner hat keinen **Sync**-Modus, und nichts wird optimiert, bis du darin **Sync now** drückst. Die Optimierungsschalter oben wirken sich nicht auf ihn aus.""",
        "en_hash": "d596a9c99a8de79d",
    },
    "settings-section-archive": {
        "title": "Archiv (Settings)",
        "body": """• **Only show files older than:** wo der Filter des Archive-Tabs beginnt. Siehe [[settings-archive-default-age]].
• **Benachrichtigen, wenn Dateien für Archive bereit sind:** standardmäßig aus. Siehe [[settings-archive-notify]].""",
        "en_hash": "7b4561ddc010537d",
    },
    "settings-archive-default-age": {
        "title": "Only show files older than (Settings)",
        "body": """Wo der Altersfilter des Archive-Tabs beginnt: eine Zahl von **1** bis **9** und eine Einheit — **hour**, **day**, **week**, **month**, **year** oder **All** (Standard). Eine Änderung im Archive-Tab gilt nur für diesen Besuch. Siehe [[archive-age-filter]].""",
        "en_hash": "fef50a67d4e7ea34",
    },
    "settings-archive-notify": {
        "title": "Benachrichtigen, wenn Dateien für Archive bereit sind",
        "body": """Standardmäßig **Aus**. Wenn an, wirst du einmal pro neuem Stapel archivierbarer Dateien benachrichtigt. Nur OneDrive-Dateien.

Der Albums-Tab und die Erinnerung beim Verlassen zeigen das ebenfalls, sodass du dich nie nur auf Benachrichtigungen verlassen musst. Wenn Benachrichtigungen blockiert sind, sagt der Schalter es.""",
        "en_hash": "a0b5e2290fe1eaae",
    },
    "settings-about-cards": {
        "title": "Hilfe und Feedback und die Link-Karten unten",
        "body": """## Hilfe und Feedback
• **Im Play Store bewerten:** öffnet den Eintrag von GallerySync.
• **Fehler melden:** wo du schreiben kannst, und **Details kopieren** für deine App-Version, dein Telefonmodell und deine Android-Version.

## Ganz unten
• **Datenschutzerklärung.**
• **Kontodaten löschen:** wie du dich abmeldest, den Zugriff der App entfernst und ihre Daten löschst.
• **Kontakt:** die Adresse, mit **Adresse kopieren**.

Die Seiten werden aus dem Internet geladen. Wenn eine nicht lädt, nutze **Im Browser öffnen**.""",
        "en_hash": "8a454188917d719a",
    },
}
