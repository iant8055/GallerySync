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

            Each time you open the tab, it looks at the phone and asks OneDrive again (**Rescan** reads **Checking...**). Other Clouds are asked about a file just before it is optimised or removed.

            Looking, filtering and scrolling never change a file. Only choosing a mode does.
        """),

        topic("albums-new", "New albums", """
            When a new album appears, a pop-up says **1 new Album is waiting for you to choose a mode**.
            • **Show new Albums:** lists only the new ones. **Show all Albums** brings the rest back.
            • **Dismiss:** closes the message.

            New albums carry a **New** tag and always start at **Off**. A new album can never start as Archive.
        """),


        topic("albums-filter", "All Albums and the four mode buttons", """
            • Tap **Backup**, **Sync**, **Archive** or **Off** to show only the albums in that mode.
            • Tap **All Albums**, or the same button again, to show them all.

            Filtering only changes what you see. To change a mode, use the button on the album's card. See [[modes-in-depth]].
        """),

        topic("albums-totals", "Album Mode Count", """
            **Album Mode Count:** the total for each mode. Shown with **All Albums** selected. Albums under **Off** are the ones you have not decided about.
        """),

        topic("albums-all-backed-up", "Everything here is backed up", """
            Shown when every album in view has files and none are waiting to be sent. It comes from the app's own record; the Cloud's own answer is the last line of each album card.
        """, ui=True),

        topic("albums-optimised-line", "Optimised and Saved", """
            With **Sync** selected: how many files in Sync albums have been optimised, and the space saved.

            The originals stay in the Cloud. **Restore** brings them back. See [[how-optimising-works]].
        """, ui=True),

        topic("albums-archive-lines", "Files archived and Scheduled to leave this phone", """
            With **Archive** selected:
            • **N files archived · X in your Cloud:** files that have left the phone, and the space their copies take in the Cloud.
            • **N Scheduled to leave this phone:** files still on the phone in Archive albums, not counting files you chose to keep. **Nothing left to archive** when there are none.

            Nothing is removed here. Removal happens on the Archive tab. See [[archive-overview]].
        """, ui=True),

        topic("albums-run-controls", "Sync now, Rescan, Pause, Resume and Stop", """
            ## When nothing is running
            • **Sync now:** backs up now. If an Optimise switch in Settings is on with its **Mode** set to **Manual**, it then optimises too (see [[settings-optimise-mode]]). Greyed out when there is nothing to send or optimise.
            • **Rescan:** looks at the phone and asks OneDrive again.

            ## While a backup runs
            • Progress shows as **Syncing** with a percentage (by data, not file count).
            • **Pause:** holds all backups until **Resume**.
            • **Stop:** ends this run. Automatic backups carry on.

            ## Good to know
            • **Sync now** does not wait for the charger, but still follows your Wi-Fi setting. See [[settings-mobile-data]].
            • It backs up every album set to **Backup**, **Sync** or **Archive**, but only ever optimises files in **Sync** albums.
        """, ui=True),

        topic("albums-status-line", "The status line under the card", """
            • **While uploading:** the file count, the file being sent, and its progress.
            • **While preparing:** **Scanning...** or **No permission to read media.**
            • **After a run:** a summary of what applies: **uploaded**, **already in the Cloud**, **no longer on this phone**, **failed**, **waiting on the Cloud** (tried again later), **still to go**, or **stopped:** with the reason.
        """, ui=True),

        topic("albums-list", "The Albums Tab", """
            ## Header
            • Tap **Backup**, **Sync**, **Archive** or **Off** to show only the albums in that mode. Tap **All Albums** to show them all again.
            • The line under the buttons gives the total for each mode.
            • **▲ Hide Details ▲** at the bottom of the card folds it to its title, number and main button; **▼ Show Details ▼** brings the mode buttons and the mode total back. The tab remembers your choice.

            ## Album cards
            • **Name, file count and size** of the album on your phone.
            • **Second line** (only when needed): how many files are optimised, kept at full size, pending or failed.
            • **Cloud line:** **verified in OneDrive** means OneDrive confirmed the files at the right size. It is checked each time you open the tab. Other Clouds show **sent to** that Cloud.
            • **Mode button** (right side): tap to change the album's mode. Archive asks you to confirm first. If the album's Cloud cannot do that mode, the card says so.
            • **Tap the card** to see the album's files. See [[album-detail]].
        """, ui=True),

        topic("album-detail", "An album's file list", """
            Opens when you tap an album card.

            • **Header:** back arrow, album name, mode and file count, **Backs up to** (the Cloud and folder new files go to, plus any other Cloud already holding some), the counts ([[album-file-status]]), **Sort by** ([[album-file-sort]]) and **Keep at full size** ([[album-file-pin]]).
            • **▲ Hide Details ▲** at the bottom of the header folds it to the album's name, mode and file count; **▼ Show Details ▼** brings the rest back. The page remembers your choice.
            • **Files:** one card per file with its size, date and marks. An optimised file shows its smaller size on the phone.
            • **Camera folder** (if chosen in Settings): extra optimise controls. See [[album-camera-optimise]].

            No thumbnails or previews. Use your gallery app to look at photos.
        """, ui=True),

        topic("album-file-status", "File marks and counts in an album", """
            • **✓ backed up:** in the Cloud, full size on the phone.
            • **✓ backed up · optimised:** in the Cloud; the phone has a smaller copy.
            • **⟳ pending:** waiting to be sent.
            • **✗ failed:** could not be sent after several tries.

            ## Retry failed
            **Retry** puts failed files back in the queue and starts a backup. It removes nothing.
        """, ui=True),

        topic("album-file-sort", "Sorting an album's files", """
            • **Name:** A to Z.
            • **Date:** newest first. A restored file has the date it was restored.
            • **Status:** failed, then pending, then optimised, then backed up.

            The list opens in name order each time. Sorting changes nothing about the files.
        """),

        topic("album-file-pin", "Keep at full size", """
            A checkbox beside files that **Restore** brought back. While selected, GallerySync leaves that file alone:
            • In a **Sync** album, it is not optimised.
            • In an **Archive** album, it is not removed.
            • In **Backup** or **Off**, nothing changes, but the choice is remembered.

            Restore selects it automatically. Deselect it to let the file follow its album's mode again.
        """, ui=True),

        topic("album-camera-optimise", "Optimise older photos and videos in your Camera folder", """
            Only the **Camera folder** you chose in Settings under **Camera Sync** ([[settings-section-camera]]) has these controls.

            ## Selecting files
            • **Select Photos/Videos older than:** **1 day** to **1 year**, or **All**.
            • **Photos** and **Videos:** which kinds to select.
            • Swipe a file right to select, left to deselect. The card shows how many are selected and the estimated saving.

            ## Buttons
            • **Sync now:** backs up anything waiting in the folder, then optimises the selected files only.
            • **Rescan:** checks the Cloud again.

            ## Good to know
            • Only files OneDrive has confirmed at full size can be selected.
            • Nothing is optimised until you press **Sync now**. New photos stay as they are.
            • The Camera folder has no **Sync** mode, and the Optimise switches in Settings do not affect it.
            • Leaving with changed choices asks whether to make them your defaults.
        """, ui=True),

        topic("album-camera-select", "Selecting files in your Camera folder", """
            Shows how many files are selected and the estimated space saved, or **Nothing selected**.

            • **Photos**, **Videos** and the age select every match.
            • Swipe right to select, left to deselect.
            • **Sync now** optimises only the selected files.

            A greyed file cannot be selected: it is not yet confirmed in OneDrive, or is already optimised. See [[album-camera-optimise]].
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
