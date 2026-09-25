from model import Chapter, topic

CHAPTER = Chapter(
    id="start",
    title="Start here",
    intro="If you read only one chapter, read this one. It says what GallerySync is for, what it "
          "promises, and how the app is laid out.",
    topics=[
        topic("what-is-gallery-sync", "What GallerySync is, and what it is not", """
            GallerySync keeps your photos and videos safe in **your Cloud** and allows you to manage how much room they take up on your phone.

            It is **not** a gallery app. Looking at photos, searching, editing and sharing are all done by
            the gallery app you already have (Samsung Gallery, Google Photos and so on), and GallerySync
		deliberately does not copy any of that. It works quietly,  behind the scenes, keeping 
            every photo and video safe and secure.

            ## What it can do for you
            • **Back up** your photos and videos to the Cloud, so if you lose or break your phone your photos and videos are safe.
            • **Save Space** If your phone is filled up, GallerySync can help.  Once your photos and videos are safely backed up in the Cloud, you can set GallerySync to compact your pictures and/or videos to reduce the space they use. They stay in your gallery and still open normally. This is called "Optimising".
            • **Take a whole album off the phone.** Once every file in an album is confirmed safe in the Cloud, GallerySync can remove the copies on your phone (they go to your phone's trash) so the space comes back. You choose which albums, and you can omit any single file from it. This is called "Archiving".
            • **Bring things back** You don't have to open up a separate app to get your files.  With GallerySync you can pull your photo and video down from the Cloud whenever you want them. This is "Restoring".

            ## What GallerySync does not do
            We do not see your photos or videos.  We do not have any servers or external file storage. Your photos go from your phone straight to and from your own Cloud account and nowhere else.
        """),

        topic("safety-promises", "The safety promises", """
            These hold everywhere in the app, without exception.

            • **Nothing is ever permanently deleted.** Anything GallerySync removes goes to your phone's or the Cloud's Trash or Recycle Bin where it can be easily recovered.
            • **The app never empties a bin.** Emptying is always **your** own action, in the phone's gallery app or the Cloud's.
            • **Nothing leaves your gallery unless you chose that for that album/file.** Removing files from the phone only happens in an album you set to "Archive", and you confirm that choice when you make it.
            • **"Safe" has one meaning.** Before anything is optimised or moved off your phone, the Cloud must confirm it holds the file **and** report the same size as the copy on your phone. Nothing but 100 % verification will do.
            • **Out of the box, nothing happens.** A fresh install has no folders chosen and every album set to Off, so it cannot back up or change anything until you say so.   **You** are in full control of your photos and videos.
            • **Your Cloud copies are left alone by default.** Deleting a photo or video on your phone does not delete its backup by default. **You** switch on the option to ASK you first, and you still must confirm it before anything moves, and even then it goes to the Cloud's recycle bin.

            ## Good to know
            No backup is safe just because an app says so, and that includes this one. Every so often,
            open the Cloud and look at a few of your photos there. Do it before you empty any recycle
            bin, because that is the point where the last spare copy stops existing.
        """),

        topic("tabs-at-a-glance", "The Four Tabs Menu", """
            The bar at the bottom of the screen has four tabs. Only the tab you are on shows its name;
            the other three show just their icon.

            • **Albums** (folder icon). Where you choose what happens to each album, and where you start or watch a backup. This is the tab you will use most.
            • **Restore** (cloud icon). Brings things back: full-size originals in place of smaller copies, and files that are in the Cloud but no longer on the phone.
            • **Archive** (cloud with a tick). Checks the files in your Archive albums against the Cloud, then removes them from the phone once you say yes.
            • **Settings** (cog). The things you set once and rarely change: appearance, mobile data, which clouds are connected, which cloud each folder goes to, which folders to watch, and how photos and videos are optimised.

            During first-time setup the bar is hidden and a guided tour walks you through the same four
            tabs. Look for the **(?)** buttons in the app: each opens a short explanation taken from this
            guide. You can access the whole guide in **Settings**, under **General** or by clicking "Read More" in any of the pop-up explanations.
        """),

        topic("quick-start", "Quick start", """
            1. Finish the first-time setup: allow the app to see your photos, choose the folders to look after, sign in to your Cloud account, and pick a plan. See [[setup-overview]].
            2. Open the **Albums** tab. Every album starts as **Off**. Tap the button on an album and Select **Backup**, this as the name suggest copies your photos and videos to the Cloud, leaving the original on your phone.
            3. Press **Sync Now** if you want to manually sync your folders or close out of the tab.  New photos are picked up automatically shortly after you take them, on Wi-Fi or mobile data as well if you authorize it in Settings.
            4. Look at the last line of each album: it says how many of its files have been confirmed backed up to the Cloud.
            5. Need to free up some space on your phone? Set an album to **Sync** to optimise the photos and videos based on the configuration in Settings.  Setting the folder to **Archive** will move the photos/videos to the phone's Recycle Bin once it has been verified in the Cloud.
            6. Need something back at full size? Open the **Restore** tab.

            The numbered steps are a summary. The chapters that follow explain every button and every line.
        """),

        topic("words-you-will-see", "Words you will see", """
            • **Album.** A folder of photos and videos on your phone, such as Camera or Screenshots. GallerySync only lists albums inside the folders you chose.
            • **Mode.** What you want done with an album: **Off**, **Backup**, **Sync** or **Archive**. See [[modes-in-depth]].
            • **Cloud.** OneDrive, Google Photos, Google Drive, Dropbox, pCloud, IDrive e2 or Backblaze B2. You choose which ones you use. See [[setup-cloud]].
            • **Pairing.** Which cloud one of your folders (DCIM, Pictures and so on) sends to. See [[settings-destination]].
            • **Backup.** Copying a file to your Cloud. Your phone's copy is not touched.
            • **Optimise / optimised.** Replacing a photo or video on the phone with a smaller copy while the full-quality original stays safe in the Cloud. See [[how-optimising-works]].
            • **Archive.** Removing an album's files from the phone once they are confirmed in the Cloud. The files go to your phone's bin, not into thin air.
            • **Verified in the Cloud.** The cloud the file was sent to is asked, right then, and confirms it holds the file at the same size as your phone's copy. See [[how-verification-works]].
            • **Trash / Recycle Bin.** The place removed files wait so you can recover them. Samsung Gallery calls it the Recycle Bin and the Files app calls it Trash. See [[where-deleted-files-go]].
            • **Restore.** Putting a full-size original back on the phone.
            • **Rescan.** Looking at the phone again, and asking OneDrive again, so the numbers are fresh.
        """),
    ],
)
