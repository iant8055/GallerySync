from model import Chapter, topic

CHAPTER = Chapter(
    id="concepts",
    title="How it works, in plain English",
    intro="The ideas behind the buttons.",
    topics=[
        topic("modes-in-depth", "The four modes", """
            Each album has one mode, set with the button on its card.

            • **Off:** nothing happens. Every album starts here.
            • **Backup:** copies to your Cloud; the phone is never touched.
            • **Sync:** backs up, then optimises if optimising is on in Settings.
            • **Archive:** backs up, verifies, then moves the files to the phone's trash after your tap. The only mode that removes files.

            ## Which Clouds
            Backup works with every Cloud. Sync and Archive need OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 or Backblaze B2, not Google Photos.

            ## Camera folder
            If you set a Camera folder in Settings, it offers Off, Backup and Archive, with its own optimise controls instead of Sync. See [[album-camera-optimise]].

            ## Which to pick
            • Keep as is, but protected: **Backup**.
            • Save space but keep viewing: **Sync**.
            • Old albums you want off the phone: **Archive**.
            • Unsure: **Off** or **Backup**.

            ## Good to know
            • Only you set modes. The one exception is merging duplicate albums, which sets **Off**. See [[album-merge-warning]].
            • Switching to Archive asks you to confirm. Switching away stops further removals.
        """),

        topic("how-verification-works", "How verification works", """
            Before anything is optimised or removed, the Cloud is asked, right then, whether it holds a good copy.

            ## The test
            • **OneDrive:** lists the album folder; the file must have the same name and size.
            • **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2:** asked about the exact file; it must be present, not in its bin, at the same size. For an optimised photo, the original's size.

            ## When
            • At the start of the first backup, so only missing files are sent.
            • Each time you open the Albums tab or press Rescan (OneDrive).
            • When you check files on the Archive tab, and again just before removal.
            • Just before a file is optimised.

            ## Three answers
            • **Verified:** the Cloud has it.
            • **Not there, or wrong size:** the file stays on the phone.
            • **Could not check:** treated as unknown. Nothing is removed or optimised.

            ## Good to know
            A size check cannot catch every kind of damage. Open your Cloud now and then and check a few photos.
        """),

        topic("how-optimising-works", "How optimising works", """
            Optimising swaps the phone's copy for a smaller one, so the phone stops filling up while everything stays in your gallery.

            ## Photos
            Reduced to about 2048 pixels on the long edge, roughly a tenth of the size. Same name, place, date and location, with a small cloud badge.

            ## Video
            Re-encoded at **480p** (High compression), **720p** (Medium) or **1080p** (Low). Never cut. Automatic waits for the charger; Manual runs on **Sync now**.

            ## Rules
            • Only in **Sync** albums.
            • Only after the Cloud confirms the original at the same size.
            • A file that would not get smaller is left alone.

            ## Getting the original back
            Use the **Restore** tab. Restore before editing if you want full detail.
        """),

        topic("where-deleted-files-go", "Where removed files go", """
            Nothing is permanently deleted.

            ## On your phone
            Archive moves files to Android's trash for about 30 days:
            • Samsung: **Samsung Gallery's Recycle Bin**.
            • Other phones: the **Files** app's **Trash**.

            Files in the bin still take their full space until you empty it.

            ## In your Cloud
            Under **Ask**, copies you choose to remove go to the **OneDrive recycle bin**. Nothing is ever removed from other Clouds. The app never empties any bin.
        """),

        topic("background-work", "When things happen", """
            • **Backing up:** shortly after new photos appear, and every six hours. Wi-Fi only unless mobile data is on; not when battery or storage is low. **Sync now** runs straight away.
            • **Optimising photos:** Automatic, once the Cloud confirms a file in a Sync album. Manual, on **Sync now**.
            • **Optimising video:** Automatic, after backups, while charging. Manual, on **Sync now**.
            • **Deleted files:** under **Ask**, you are notified shortly after deleting a file that has a Cloud copy (not while backups are paused).
            • **Archiving:** only when you say Yes on the Archive tab.
            • **Restoring:** only when you press Restore.

            ## Closing the app
            Back or Home is fine: work carries on. Swiping the app away from recent apps can stop background work until you open it again.
        """),

        topic("where-your-data-lives", "Where your data lives", """
            • **Photos and videos:** go directly between your phone and your own Cloud, encrypted. There are no GallerySync servers.
            • **Sign-ins and keys:** kept encrypted on your phone. Passwords are typed on the provider's page, never in the app.
            • **The app's records and settings:** private storage on your phone.
            • **Nothing is collected:** no analytics, crash reporting or ads.

            Details: **Privacy Policy** and **Delete Account Info** in Settings.
        """),

        topic("numbers-explained", "Why the numbers may differ from your gallery app", """
            GallerySync counts only files on the phone now, in the folders you chose, not in the trash. Your gallery may count cloud-only items or group folders differently. Both are right; they count different things.
        """),
    ],
)
