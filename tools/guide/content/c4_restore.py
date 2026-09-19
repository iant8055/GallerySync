from model import Chapter, topic

CHAPTER = Chapter(
    id="restore",
    title="The Restore tab",
    intro="Restore brings photos and videos back from OneDrive to the album they came from. It only "
          "ever reads from OneDrive and writes to your phone, so nothing in OneDrive is touched.",
    topics=[
        topic("restore-overview", "What Restore brings back", """
            Restore lists two kinds of file, side by side in the same folders:

            • **To restore.** A photo or video the app has replaced with a smaller copy, and which is still on the phone. Restoring puts the full-size original in its place, in the same album and under the same name.
            • **To download.** A file the app backed up that is no longer in its folder on the phone, most often because its album was archived. Downloading puts it back in the album it came from, under its own name.

            Every file Restore brings back is ticked **Keep at full size**, so it is not shrunk or
            archived again straight away. You can untick it in the album's file list. See
            [[album-file-pin]].

            ## What OneDrive holds, not only what this app sent
            Restore lists the photos and videos in your OneDrive backup folders, whoever put them there.
            It is not limited to what Gallery Sync uploaded, so an album you archived, a folder from
            another phone or one added from a computer can all be brought back. Each file goes back into
            the album with the same name as its OneDrive folder. It is still not a general file
            browser: only photos and videos, and only in your backup folders.

            A file the phone already has at full size is shown greyed out, so you can see why it is not
            on offer. See [[restore-greyed-files]].

            ## Folders first
            The tab always opens on a list of folders (albums), even if there is only one. Swipe a folder
            to take all of it, or tap it to open it and pick individual files.

            ## Safe to try
            A restore only replaces a smaller copy once the full download has arrived and its size has
            been checked. If anything goes wrong the file on your phone is left exactly as it was.
            Stopping partway costs nothing and you can run it again.
        """),

        topic("restore-hero", "Folders to Restore and Files in this folder", """
            ## What it is
            The green card at the top. Its heading and big number depend on where you are:
            • On the folder list: **Folders to** with **Restore** directly under it, in the left half, and in the right half how many folders have something to bring back.
            • Inside a folder: the card is laid out like an album's file list on the Albums tab. The return arrow and the folder's name, large and bold, are in the left half, and the number of files is centred in the right half with **Files in this folder** under it.

            A dash instead of a number means the list is still loading.

            ## Where it comes from
            First the app's own record of what it has optimised or backed up, compared with a look at
            what is on the phone right now. Then OneDrive itself, folder by folder: **Checking OneDrive
            for more...** appears under the card while that happens, and anything else OneDrive holds
            is added when it arrives. If a folder cannot be reached the card says so, in red, and the
            list may be incomplete.

            OneDrive is read once and remembered, including when the app is closed. Leaving the tab and
            coming back, or closing the app and opening it again, shows the list straight away, compared
            with what is on your phone now. If the remembered list is more than ten minutes old, OneDrive
            is read again behind it; you can also press **Refresh** at any time. Signing out forgets it.

            A folder appears when it has something to bring back. To list every OneDrive folder, even
            those with nothing to bring back, turn on **Show empty folders** in Settings.
        """, ui=True),

        topic("restore-message-line", "The message under the number", """
            ## What it is
            One line of text whose wording changes with the situation:
            • **Swipe right to select / left to deselect. Tap to open.** On the folder list.
            • **Tap a file to select it.** With **Then press Restore.** on the line under it. Inside a folder. Nothing moves until you press Restore.
            • **Nothing in OneDrive is missing from this phone, so there is nothing to bring back.** When the list is empty.
            • **A result after a restore**, such as **3 back to full quality · 2 back on this phone. 1 unchanged.** or **None recovered. 4 unchanged.** Unchanged means those files were left exactly as they were.

            ## Where it comes from
            The instructions are fixed text. The result line is worked out from what the restore just did.
        """, ui=True),

        topic("restore-selected-summary", "N selected and how much to recover", """
            ## What it is
            Appears under the big number once you have selected something: for example **6 selected · 1.2 GB
            to recover**.

            ## Where it comes from
            **Selected** is a count of the files you have chosen across all folders. **To recover** is the
            extra room the restored files will take on your phone: for each file, its full size in
            OneDrive minus what the phone holds now (nothing at all, for a file that has to be downloaded).

            ## Good to know
            Make sure the phone has that much free space before you press Restore.
        """, ui=True),

        topic("restore-buttons", "Refresh, Select all and Clear", """
            ## What it is
            Two buttons in the green card, each half the width:

            • **Refresh** (on the folder list) reads the list again. Use it after something changed while this tab was open, such as an optimise or archive run.
            • **Select all** (inside a folder) selects every file listed in that folder.
            • **Clear** removes every selection, in every folder. It is greyed out when nothing is selected, and it stays in place instead of disappearing so the card does not jump around.

            Both are greyed out while a restore is running.

            ## Where it comes from
            **Refresh** repeats both looks: the app's own record and the phone, then OneDrive, whatever
            the age of the last reading. Reading OneDrive needs the internet.
        """, ui=True),

        topic("restore-folders-list", "The folder list", """
            ## What it is
            One card per folder. Each card shows:
            • **The folder name.**
            • **12 files · 1.2 GB.** How many files in it can come back, and the extra room they would take.
            • **8 to restore · 4 to download.** How many are smaller copies that would be swapped for full size, and how many are missing from the phone and would be downloaded.
            • **3 already on this phone**, when some of the folder's files are here at full size. They are greyed out inside the folder. See [[restore-greyed-files]].
            • **3 selected here**, when only some of the folder is selected.
            • **A tick**, when the whole folder is selected. Otherwise an arrow showing that it opens.

            ## How to use it
            • **Tap** a card to open it.
            • **Swipe right** to select everything in the folder; **swipe left** to deselect it. Repeating a swipe changes nothing, so swiping across several folders cannot accidentally undo a choice you already made. The card slides aside as you pull and shows a tick (or a cross) behind it, ticks against your finger with a short buzz once you have pulled far enough to count, and springs back when you let go.

            Inside a folder, the green card at the top names the folder, and its return arrow takes you
            back to the list of folders.

            ## Where it comes from
            OneDrive's own listing of the folder, compared with what is on the phone now, together with
            the app's own record of what it has optimised.
        """),

        topic("restore-files-list", "The file list inside a folder", """
            ## What it is
            One card per file. **Tap** to select or unselect it; a selected file is highlighted with a tick.

            **The name**, then one of:
            • **1.2 MB now · 8.4 MB full size.** A smaller copy on the phone, and the size of the original in OneDrive. Restoring swaps one for the other.
            • **Available for download · 8.4 MB.** The file is not on the phone at all. Restoring brings it back at this size.
            • **Already on this phone · 8.4 MB.** Greyed out. The phone already has this file at full size, so there is nothing to do. See [[restore-greyed-files]].

            **While it works:** **Working... 45%** and a bar.

            **When it finishes:**
            • **Restored to full size.** The original replaced the smaller copy.
            • **Back on this phone.** The file was downloaded.
            • **Could not restore, your file is unchanged**, with the reason in brackets, in red. Nothing was lost.

            ## Where it comes from
            Names and sizes come from OneDrive's listing of the folder. The percentage comes from the
            download itself.
        """),

        topic("restore-greyed-files", "Why are some files greyed out?", """
            ## What it is
            Inside a folder, some files are faded and cannot be selected. They read **Already on this
            phone**, with their size. Above the list a line says **Greyed-out files are already on this
            phone.**

            ## Why they are shown at all
            OneDrive holds every file in the folder. Files the phone already has, at full size, need no
            restoring, but leaving them out would make the folder look shorter than it is in OneDrive
            and leave you wondering where they went. So they are listed, and greyed out.

            ## Where it comes from
            OneDrive's listing, compared with the photos and videos on the phone, by folder, name and
            size. A file counts as here only when all three match.

            ## Good to know
            A file of the same name but a different size is not offered and not greyed: it might be a
            photo you edited, and Restore never writes over an edit.
        """, ui=True),

        topic("restore-action-bar", "Restore and Stop restoring", """
            ## What it is
            A wide button along the bottom of the screen. It only appears once you have selected
            something, or while a restore is running.

            • **Restore** starts bringing back every selected file, one after another.
            • **Stop restoring** replaces it while the restore runs. It stops the batch, including the file in progress. Files already brought back stay back.

            ## What happens
            Each file is downloaded from your OneDrive, checked, and only then put in place. It needs an
            internet connection and enough free space. Your OneDrive is only read, never changed.
        """, ui=True),
    ],
)
