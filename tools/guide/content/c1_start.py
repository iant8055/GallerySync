from model import Chapter, topic

CHAPTER = Chapter(
    id="start",
    title="Start here",
    intro="If you read only one chapter, read this one. It says what Gallery Sync is for, what it "
          "promises, and how the app is laid out.",
    topics=[
        topic("what-is-gallery-sync", "What Gallery Sync is, and what it is not", """
            Gallery Sync keeps your photos and videos safe in **your own OneDrive**, and manages how much
            room they take up on your phone.

            It is **not a gallery**. Looking at photos, searching, editing and sharing are all done by
            the gallery app you already have (Samsung Gallery, Google Photos and so on), and Gallery
            Sync deliberately does not copy any of that. It works quietly behind the scenes and keeps
            every photo where your gallery already finds it, as an ordinary file.

            ## What it can do for you
            • **Back up** your photos and videos to OneDrive, so a lost or broken phone loses nothing.
            • **Make smaller copies** of photos and videos that are already safe in OneDrive, so the phone stops filling up. They stay in your gallery and still open normally. This is called "optimising".
            • **Take a whole album off the phone** once every file in it is safely in OneDrive. This is called "archiving".
            • **Bring things back** at full size whenever you want them.

            ## What it does not do
            It has no photo grid, no search and no editor. It has no server of its own, so your photos
            go from your phone straight to your own OneDrive account and nowhere else.
        """),

        topic("safety-promises", "The safety promises", """
            These hold everywhere in the app, without exception.

            • **Nothing is ever permanently deleted.** Anything the app removes goes to a bin you can recover it from: your phone's Trash or Recycle Bin, or OneDrive's recycle bin.
            • **The app never empties a bin.** Emptying is always your own action, in your gallery app or in OneDrive.
            • **Nothing leaves your gallery unless you chose that for that album.** Removing files from the phone only happens in an album you set to Archive, and you confirm that choice when you make it.
            • **"Safe" has one meaning.** Before anything is shrunk or removed on your phone, OneDrive must confirm it holds the file **and** report the same size as the copy on your phone. Nothing weaker counts.
            • **Out of the box, nothing happens.** A fresh install has no folders chosen and every album set to Off, so it cannot back up or change anything until you say so.
            • **Your OneDrive copies are left alone by default.** Deleting a photo on your phone does not delete its backup unless you switch on the option that asks you first, and even then you confirm a list before anything moves, and it goes to OneDrive's recycle bin.

            ## Good to know
            No backup is safe just because an app says so, and that includes this one. Every so often,
            open OneDrive and look at a few of your photos there. Do it before you empty any recycle
            bin, because that is the point where the last spare copy stops existing.
        """),

        topic("tabs-at-a-glance", "The four tabs", """
            The bar at the bottom of the screen has four tabs. Only the tab you are on shows its name;
            the other three show just their icon.

            • **Albums** (folder icon). Where you choose what happens to each album, and where you start or watch a backup. This is the tab you will use most.
            • **Restore** (cloud icon). Brings things back: full-size originals in place of smaller copies, and files that are in OneDrive but no longer on the phone.
            • **Archive** (cloud with a tick). Checks the files in your Archive albums against OneDrive, then removes them from the phone once you say yes.
            • **Settings** (cog). The things you set once and rarely change: appearance, mobile data, where backups go, which folders to watch, and how photos and videos are optimised.

            During first-time setup the bar is hidden and a guided tour walks you through the same four
            tabs. Look for the **(?)** buttons in the app: each opens a short explanation taken from this guide.
        """),

        topic("quick-start", "Quick start", """
            1. Finish first-time setup: allow the app to see your photos, choose the folders to look after, sign in to your Microsoft account, and pick a plan. See [[setup-overview]].
            2. Open the **Albums** tab. Every album starts as **Off**. Tap the pill on an album and choose **Backup**, the safe choice that only adds copies to OneDrive.
            3. Press **Sync now**, or just leave it: new photos are picked up automatically shortly after you take them, on Wi-Fi.
            4. Look at the last line of each album: it says how many of its files OneDrive has confirmed.
            5. Want space back? Set an album to **Sync** to shrink its photos, or **Archive** to take it off the phone once everything is safe.
            6. Need something back at full size? Open the **Restore** tab.

            The numbered steps are a summary. The chapters that follow explain every button and every line.
        """),

        topic("words-you-will-see", "Words you will see", """
            • **Album.** A folder of photos and videos on your phone, such as Camera or Screenshots. Gallery Sync only lists albums inside the folders you chose during setup.
            • **Mode.** What you want done with an album: **Off**, **Backup**, **Sync** or **Archive**. See [[modes-in-depth]].
            • **Backup.** Copying a file to your OneDrive. Your phone's copy is not touched.
            • **Optimise / optimised.** Replacing a photo or video on the phone with a smaller copy while the full-quality original stays safe in OneDrive. See [[how-optimising-works]].
            • **Archive.** Removing an album's files from the phone once they are confirmed in OneDrive. The files go to your phone's bin, not into thin air.
            • **Verified in OneDrive.** OneDrive was asked and confirmed it holds the file, at the same size as your phone's copy. See [[how-verification-works]].
            • **Trash / Recycle Bin.** The place removed files wait so you can recover them. Samsung Gallery calls it the Recycle Bin and the Files app calls it Trash. See [[where-deleted-files-go]].
            • **Restore.** Putting a full-size original back on the phone.
            • **Rescan.** Looking at the phone again, and asking OneDrive again, so the numbers are fresh.
        """),
    ],
)
