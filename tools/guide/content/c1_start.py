from model import Chapter, topic

CHAPTER = Chapter(
    id="start",
    title="Start here",
    intro="What GallerySync is for, what it promises, and how the app is laid out.",
    topics=[
        topic("what-is-gallery-sync", "What GallerySync is, and what it is not", """
            GallerySync keeps your photos and videos safe in **your Cloud** and helps you control how much room they take on your phone.

            It is **not** a gallery app. Viewing, searching, editing and sharing stay in the gallery app you already use.

            ## What it does
            • **Backup:** copies your photos and videos to your Cloud.
            • **Optimise:** once a file is safely in the Cloud, swaps the phone's copy for a smaller one. It stays in your gallery.
            • **Archive:** once an album is safely in the Cloud, moves the phone's copies to the phone's trash to free space.
            • **Restore:** brings full-size files back from the Cloud.

            ## What it does not do
            There are no GallerySync servers. Your files go straight between your phone and your own Cloud account.
        """),

        topic("safety-promises", "The safety promises", """
            • **Nothing is permanently deleted.** Anything removed goes to a trash or recycle bin you can recover from.
            • **The app never empties a bin.** Only you do that.
            • **Nothing leaves your phone unless you chose Archive for that album**, and you confirm that choice.
            • **"Safe" means verified.** The Cloud must confirm it holds the file at the same size before anything is optimised or removed.
            • **Out of the box, nothing happens.** No folders are chosen and every album is Off until you decide.
            • **Your Cloud copies are left alone.** Deleting a photo on your phone never deletes its backup unless you choose **Ask** in Settings and confirm.

            ## Good to know
            Now and then, open your Cloud and look at a few photos, especially before you empty a bin.
        """),

        topic("tabs-at-a-glance", "The Four Tabs Menu", """
            The bar at the bottom has four tabs. The tab you are on shows its name.

            • **Albums** (folder): set each album's mode and run backups.
            • **Restore** (cloud with arrow): bring files back from your Cloud.
            • **Archive** (cloud with check mark): check Archive albums and remove them from the phone.
            • **Settings** (cog): Clouds, folders, optimising and other preferences.

            Tap any **(?)** for a short explanation. The full guide is the first card in **Settings**.
        """),

        topic("quick-start", "Quick start", """
            1. Finish setup: sign in to your Cloud, allow access to your photos, choose folders and a Backup Plan. See [[setup-overview]].
            2. On the **Albums** tab, tap an album's mode button and choose **Backup**.
            3. New photos are backed up automatically. Press **Sync now** to back up straight away.
            4. Check each album's last line: it shows how many files your Cloud has confirmed.
            5. Need space? Choose **Sync** to optimise an album, or **Archive** to move it off the phone.
            6. Need a file back at full size? Use the **Restore** tab.
        """),

        topic("words-you-will-see", "Words you will see", """
            • **Album:** a folder of photos and videos on your phone.
            • **Mode:** what happens to an album: **Off**, **Backup**, **Sync** or **Archive**. See [[modes-in-depth]].
            • **Cloud:** OneDrive, Google Photos, Google Drive, Dropbox, pCloud, IDrive e2 or Backblaze B2. See [[setup-cloud]].
            • **Pairing:** which Cloud a top-level folder (DCIM, Pictures) sends to. See [[settings-destination]].
            • **Optimise:** replace the phone's copy with a smaller one; the original stays in the Cloud. See [[how-optimising-works]].
            • **Verified:** the Cloud confirmed it holds the file at the same size. See [[how-verification-works]].
            • **Trash / Recycle Bin:** where removed files wait. See [[where-deleted-files-go]].
            • **Rescan:** look at the phone and the Cloud again for fresh numbers.
        """),
    ],
)
