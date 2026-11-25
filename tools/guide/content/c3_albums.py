from model import Chapter, topic

CHAPTER = Chapter(
    id="albums",
    title="The Albums tab",
    intro="Where you choose each album's mode and run backups.",
    topics=[
        topic("albums-overview", "How the Albums tab is laid out", """
            • **Green header:** mode buttons, totals, and the backup controls.
            • **Status line:** shown while something is running or just finished.
            • **Album list:** one card per album, A to Z. Two columns on a wide screen.

            Each time you open the tab, it looks at the phone and asks OneDrive again (**Rescan** reads **Checking...**). Other Clouds are asked about a file just before it is optimized or removed.

            Looking, filtering and scrolling never change a file. Only choosing a mode does.
        """),

        topic("albums-new", "New albums", """
            When a new album appears, a pop-up says **1 new Album is waiting for you to choose a mode**.
            • **Show new Albums:** lists only the new ones. **Show all Albums** brings the rest back.
            • **Dismiss:** closes the message.

            New albums carry a **New** tag and always start at **Off**. A new album can never start as Archive.
        """),


        topic("albums-filter", "All Albums and the four mode buttons", """
            • Tap **Backup**, **Optimize**, **Archive** or **Off** to show only the albums in that mode.
            • Tap **All Albums**, or the same button again, to show them all.

            Filtering only changes what you see. To change a mode, use the button on the album's card. See [[modes-in-depth]].
        """),

        topic("albums-totals", "Album Mode Count", """
            **Album Mode Count:** the total for each mode. Shown with **All Albums** selected. Albums under **Off** are the ones you have not decided about.
        """),

        topic("albums-all-backed-up", "Everything here is backed up", """
            Shown when every album in view has files and none are waiting to be sent. It comes from the app's own record; the Cloud's own answer is the last line of each album card.
        """, ui=True),

        topic("albums-optimized-line", "Optimized and Saved", """
            With **Optimize** selected: how many files in Optimize albums have been optimized, and the space saved.

            The originals stay in the Cloud. **Restore** brings them back. See [[how-optimizing-works]].
        """, ui=True),

        topic("albums-archive-lines", "Files archived and Scheduled to leave this phone", """
            With **Archive** selected:
            • **N files archived · X in your Cloud:** files that have left the phone, and the space their copies take in the Cloud.
            • **N Scheduled to leave this phone:** files still on the phone in Archive albums, not counting files you chose to keep. **Nothing left to archive** when there are none.

            Nothing is removed here. Removal happens on the Archive tab. See [[archive-overview]].
        """, ui=True),

        topic("albums-run-controls", "Back Up Now, Rescan, Pause, Resume and Stop", """
            ## When nothing is running
            • **Back Up Now:** backs up now. If an Optimize switch in Settings is on with its **Mode** set to **Manual**, it then optimizes too (see [[settings-optimize-mode]]). Greyed out when there is nothing to send or optimize.
            • **Rescan:** looks at the phone and asks OneDrive again.

            ## While a backup runs
            • Progress shows as **Backing up now** with a percentage (by data, not file count).
            • **Pause:** holds all backups until **Resume**.
            • **Stop:** ends this run. Automatic backups carry on.

            ## Good to know
            • **Back Up Now** does not wait for the charger, but still follows your Wi-Fi setting. See [[settings-mobile-data]].
            • It backs up every album set to **Backup**, **Optimize** or **Archive**, but only ever optimizes files in **Optimize** albums.
        """, ui=True),

        topic("albums-status-line", "Upload progress and status messages", """
            • **While uploading** (inside the card): the file count, the album, the file being sent and the bytes sent of its total size. A percentage shows in place of the bytes when the file's size isn't known yet. The progress bar sits under the line. **▲ Hide Details ▲** hides them both.
            • **While preparing** (below the card): **Scanning...** or **No permission to read media.**
            • **After a run** (below the card): a summary of what applies: **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (tried again later), **still to go**, or **stopped:** with the reason.
        """, ui=True),

        topic("albums-list", "The Albums Tab", """
            ## Header
            • Tap **Backup**, **Optimize**, **Archive** or **Off** to show only the albums in that mode. Tap **All Albums** to show them all again.
            • The line under the buttons gives the total for each mode.
            • **▲ Hide Details ▲** at the bottom of the card hides the mode total line and, while a run is going, the upload line and its bar; **▼ Show Details ▼** brings them back. **All Albums**, the four mode buttons and **Back Up Now** / **Rescan** stay, and while a run is going **Backing up now…**, **Pause** and **Stop** stay too. The tab remembers your choice.

            ## Album cards
            • **Name, file count and size** of the album on your phone.
            • **Second line** (only when needed): how many files are optimized, marked *do not optimize*, pending, or locked.
            • **Cloud line:** **N verified in <Cloud>**, or **N of M verified in <Cloud>**, means the Cloud confirmed the files at the right size. OneDrive, Dropbox, Google Drive, pCloud, Backblaze B2 and IDrive e2 are each checked when you open the tab and on **Rescan**. **Could not reach <Cloud>** means the last check failed. Before the first check — and always for Google Photos, which cannot be asked — the line reads **N sent to <Cloud>**. The line is green when every Cloud on it has verified everything.
            • **Red line** (only when there are any): **N files not backed up** — files gone from their Cloud, failed, or whose upload couldn't finish. Each needs a decision; open the album and swipe. See [[album-resend]].
            • **Grey line** (only when there are any): **N files not backed up by choice** — files you swiped left on, plus any locked ones. These are honoured; nothing uses them.
            • **Mode button** (right side): tap to change the album's mode. Archive asks you to confirm first. If the album's Cloud cannot do that mode, the card says so.
            • **Tap the card** to see the album's files. See [[album-detail]].
        """, ui=True),

        topic("album-detail", "An album's file list", """
            Opens when you tap an album card.

            • **Header:** back arrow, album name, mode and file count, **Backs up to** (the Cloud and folder new files go to, plus any other Cloud already holding some), the counts ([[album-file-status]]), **Sort by** ([[album-file-sort]]) and **Do not optimize** ([[album-file-pin]]).
            • **▲ Hide Details ▲** at the bottom of the header folds it to the album's name, mode and file count; **▼ Show Details ▼** brings the rest back. The page remembers your choice.
            • **Files:** one card per file with its size, date and marks. An optimized file shows its smaller size on the phone.
            • **Camera folder** (if chosen in Settings): extra optimize controls. See [[album-camera-optimize]].

            No thumbnails or previews. **Long-press a file** to open it in your phone's photo or video app, or in its Cloud if it is no longer on the phone. See [[settings-open-not-on-phone]].
        """, ui=True),

        topic("album-file-status", "File marks and counts in an album", """
            • **✓ backed up:** in the Cloud, full size on the phone.
            • **✓ backed up · optimized:** in the Cloud; the phone has a smaller copy.
            • **✓ backed up · locked:** in the Cloud, and you swiped left: never sent again, never optimized, never archived. Still checked: if its Cloud copy disappears, the album flags it.
            • **⟳ pending:** waiting to be sent in the next run.
            • **do not back up:** you swiped left on a file that was not backed up. Never uploaded, never optimized, never offered for Archive. A copy already in a Cloud stays there.
            • **couldn't finish uploading · trying after the rest:** an upload that was cut short twice without getting further. Sent after everything else, so one stuck file does not hold the queue.
            • **not in <Cloud>:** sent before, and the Cloud no longer holds it.

            Files in the last three states are counted in red on the album's card as **N files not backed up**. Each needs a decision — see [[album-resend]].

            ## Do not optimize
            A tick box beside every file that could still be optimized: files **Restore** brought back in any album, and every full-size file in an **Optimize** album. While ticked, GallerySync never optimizes that file. The album's header shows **N marked do not optimize**. Restore ticks the files it brings back.

            ## Header counts
            Add **N not backed up by choice** when any file in the album is marked *do not back up* or locked.
        """, ui=True),

        topic("album-resend", "Files not backed up", """
            Files this album sent that are no longer in their Cloud, files whose upload couldn't finish, and failed files all show here. The album's card counts them in red as **N files not backed up** — each needs a decision. **Rescan** on the Albums tab asks the Clouds again.

            Each is greyed. One swipe decides it:

            • **Swipe right:** back it up.
              • On a file gone from its Cloud: sends it straight away (the Cloud is asked first).
              • On a file that failed five times: tries that one again.
              • On a file whose upload was cut short: puts it back in the main queue.
              • On a file you kept out of the backup: puts it back in.
            • **Swipe left:** mark **do not back up**. Never uploaded, never optimized, never offered for Archive. Any copy already in a Cloud stays there.

            Nothing is sent again on its own. They may have been deleted in the Cloud on purpose.

            ## Back up all N
            In the header, when any file is waiting on a decision. Backs up every file flagged for one, except those marked *do not back up* and those locked at *✓ backed up · locked*. Greyed when there is nothing to do.

            ## Good to know
            • Optimized files are not listed here: the full-size original was only in the Cloud.
            • In an **Off** album, set a mode first.
            • For Clouds other than OneDrive, each Rescan looks up at most 25 files per album that are not in the album's folder. Rescan again for the rest.
        """, ui=True),

        topic("album-file-sort", "Sorting an album's files", """
            • **Name:** A to Z.
            • **Date:** newest first.
            • **Status:** failed, then pending, then optimized, then backed up.

            The list opens in name order each time. Sorting changes nothing about the files.
        """),

        topic("album-file-pin", "Do not optimize", """
            A tick box beside every file in an **Optimize** album that could still be shrunk, and beside files **Restore** brought back in any album. While ticked, GallerySync never optimizes that file — it stays at full size on your phone.

            The album header shows **N marked do not optimize**. Restore ticks every file it brings back. Untick to let the file follow its album's mode again.

            ## This is not the same as locking
            Swiping a backed-up file left marks it **locked** (see [[album-file-status]]): never sent again, never optimized **and** never archived, but still checked. *Do not optimize* only stops optimization.
        """, ui=True),

        topic("album-camera-optimize", "Optimize older photos and videos in your Camera folder", """
            Only the **Camera folder** you chose in Settings under **Camera Folder** ([[settings-section-camera]]) has these controls.

            ## Selecting files
            • **Select Photos/Videos older than:** a number from **½** to **9** and a unit — **hour**, **day**, **week**, **month**, **year** or **All**.
            • **Photos** and **Videos:** which kinds to select.
            • Swipe a file right to select, left to deselect. The card shows how many are selected and the estimated saving.

            ## Buttons
            • **Back Up Now:** backs up anything waiting in the folder, then optimizes the selected files only.
            • **Rescan:** checks the Cloud again.

            ## Good to know
            • Only files OneDrive has confirmed at full size can be selected.
            • Nothing is optimized until you press **Back Up Now**. New photos stay as they are.
            • The Camera folder has no **Optimize** mode, and the Optimize switches in Settings do not affect it.
            • Leaving with changed choices asks whether to make them your defaults.
        """, ui=True),

        topic("album-camera-select", "Selecting files in your Camera folder", """
            Shows how many files are selected and the estimated space saved, or **Nothing selected**.

            • **Photos**, **Videos** and the age select every match.
            • Swipe right to select, left to deselect.
            • **Back Up Now** optimizes only the selected files.

            A greyed file cannot be selected: it is not yet confirmed in OneDrive, or is already optimized. See [[album-camera-optimize]].
        """, ui=True),

        topic("album-merge-warning", "Duplicate album names detected", """
            A single folder can end up recorded under two spellings that differ only in capital letters. GallerySync spots this, merges them into one album, and sets its mode to **Off** so you can choose fresh. **Dismiss** closes the card.

            This is the only time the app sets a mode, and it can only set **Off**.
        """, ui=True),

        topic("dialog-archive-confirm", "Archive this album?", """
            Shown when you choose **Archive**:
            • Archive checks all files are in the Cloud.
            • Archived files move to your phone's Trash/Recycle Bin.
            • Empty the bin to free the space.

            **Archive** confirms; **Cancel** changes nothing.

            ## Good to know
            You confirm once. Files added to the album later are covered too, until the album is empty. Android asks once per batch of up to 2,000 files. See [[dialog-android-trash]].
        """, ui=True),
    ],
)
