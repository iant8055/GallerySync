from model import Chapter, topic

CHAPTER = Chapter(
    id="albums",
    title="The Albums tab",
    intro="The tab you will use most. A green card at the top summarises your albums and holds the "
          "run controls; below it is one card per album, each with a coloured pill for choosing what "
          "happens to that album.",
    topics=[
        topic("albums-overview", "How the Albums tab is laid out", """
            From top to bottom:
            • **The green card.** A heading and four mode buttons that narrow the list, some figures about the albums you are looking at, and the controls that start, pause and stop a backup.
            • **A line of status** under the card, only while something is happening or just finished.
            • **The list of albums**, one card each, in alphabetical order. On a wide screen, such as an unfolded foldable, the list runs in two columns.

            ## What happens when you open the tab
            Every time you arrive, the app looks at the phone again and asks OneDrive again, so the figures
            are fresh rather than left over from the last time. You will see the **Rescan** button say
            **Checking OneDrive...** while it does.

            ## Nothing here changes a file by itself
            Choosing a mode is what tells the app what to do with an album. Scrolling, filtering and
            tapping an album to look at it change nothing.
        """),

        topic("albums-permission", "The permission message", """
            ## What it is
            A message at the top of the tab, in one of two forms:
            • **Gallery Sync needs access to your photos.** The app cannot see any albums yet. It reads the albums you choose so it can sync them to OneDrive, and it never deletes anything.
            • **Only some photos are shared.** Android is letting the app see just the photos you picked, so a sync would be incomplete. The albums it can see are still listed below.

            The **Grant access** button opens Android's permission screen.

            ## Where it comes from
            Your phone tells the app which level of access it has: none, some, or all. The message simply
            reports that.

            ## Good to know
            To sync whole albums, choose **Allow all** when Android asks. If Android does not show the
            prompt any more, open your phone's Settings, then Apps, then Gallery Sync, then Permissions,
            and allow Photos and videos there.
        """, ui=True),

        topic("albums-filter", "All Albums and the four mode buttons", """
            ## What it is
            • **All Albums** is the heading of the list, and it is also a button. Tap it to see every album. It is ringed when nothing is filtered. The **(?)** inside it explains the album cards below.
            • **Backup, Sync, Archive and Off** are four buttons, each in the colour of its mode. Tap one to show only the albums set to that mode; tap it again, or tap **All Albums**, to see everything again. The chosen button turns solid with a ring and the others turn to outlines.
            • **Tap to filter by mode** is a reminder that these are buttons and not just labels.

            If no album is in the mode you tapped, the list says **No album is in that mode.**

            ## Where it comes from
            The modes are the choices you made for each album. Filtering changes only what you see: the
            figures in the card underneath then describe just the albums being shown. It never changes
            an album's mode. To change a mode, use the pill on the album's own card.

            For what each mode does, see [[modes-in-depth]].
        """),

        topic("albums-totals", "Album Mode Count", """
            ## What it is
            A line of figures under the buttons: **Album Mode Count: 3 Backup · 2 Sync · 1 Archive · 6 Off.**
            How many albums you have set to each mode. It only appears when **All Albums** is selected,
            and it is a quick way to spot albums you have not decided about: those are the ones counted
            under **Off**.

            ## Where it comes from
            A plain tally of the choices you made on the album cards below.
        """),

        topic("albums-all-backed-up", "Everything here is backed up", """
            ## What it is
            A short line that appears when every album currently shown has at least one file and none
            of those files is waiting to be sent.

            ## Where it comes from
            The app compares the number of files in each shown album with its own record of which files
            it has already sent. The line is about the albums being shown, so it will not claim
            "everything" if an album set to Off, which never sends anything, still holds unsent files.
            With a filter such as Archive, where the albums may hold no files, the line is left out.

            ## Good to know
            This line comes from the app's own record. For OneDrive's own answer, look at the last line
            of each album card, which says how many files OneDrive has confirmed.
        """, ui=True),

        topic("albums-optimised-line", "Optimised and Saved", """
            ## What it is
            With **Sync** selected, a line such as **3 Optimised · 1.2 GB Saved**: how many photos and
            videos in your Sync albums have been replaced by smaller copies, and how much room that gave
            back.

            ## Where it comes from
            The app's own record. Each time it swaps a file for a smaller copy, it notes the size before
            and after. The total is the difference, added up across the shown albums.

            ## Good to know
            Optimised files stay in your gallery and open as normal. The full-quality original is in
            OneDrive, and the Restore tab brings it back. See [[how-optimising-works]].
        """, ui=True),

        topic("albums-archive-lines", "Files archived and Scheduled to leave this phone", """
            ## What it is
            With **Archive** selected, up to two lines:
            • **N files archived · X in OneDrive.** How many files from your Archive albums the app has sent to OneDrive, and how much room they take there. This counts files that have been sent, whether or not they have left the phone yet.
            • **N Scheduled to leave this phone.** Files still on the phone in Archive albums. Every one is due to be removed once OneDrive is confirmed to hold it. When there are none, the line says **Nothing left to archive**.

            ## Where it comes from
            The first line comes from the app's own record of what it has sent. The second is a live count
            of files in your Archive albums on the phone right now.

            ## Good to know
            Nothing leaves the phone from this screen. Removal happens on the Archive tab, after a check
            and your tap. See [[archive-overview]].
        """, ui=True),

        topic("albums-run-controls", "Sync now, Rescan, Pause, Resume and Stop", """
            ## What it is
            The buttons at the bottom of the green card. They change depending on what is happening.

            **When nothing is running**
            • **Sync now** starts a backup, and then optimises anything you set to Manual in Settings. It is greyed out when there is nothing to send and nothing waiting to be optimised, meaning every file in your Backup, Sync and Archive albums is already sent.
            • **Rescan** looks at the phone again and asks OneDrive again, so all the figures are fresh. While it works it says **Checking OneDrive...** and is disabled.

            **When a backup is running**
            • The left side reports instead of acting: **Syncing 37%**. Before the figures are known it just says **Syncing...**.
            • **Pause** holds everything, including automatic syncs, until you press **Resume**. While paused it reads **Paused at 37%**.
            • **Stop** ends this run and leaves automatic syncing switched on, so it carries on the next time a new photo appears.

            On a narrow screen, Pause, Resume and Stop become small icons with the same meaning.

            ## Where the percentage comes from
            It is the share of the data in your selected albums that has been sent during this run,
            counted in bytes, not files. Counting files jumps around when a few large videos are mixed
            with many small photos.

            ## Good to know
            **Sync now** goes ahead even when the phone is not charging, and it does not wait for the
            first-backup start time. It still respects your Wi-Fi setting. See [[settings-mobile-data]].
        """, ui=True),

        topic("albums-status-line", "The status line under the card", """
            ## What it is
            One line of text, and sometimes a thin progress bar, describing the current or last run.

            **While uploading:** **Uploading 3 of 12 · IMG_0420.jpg · 45%**. It names the file and how far
            through that file it is, because on a large video "3 of 12" alone looks stuck. The bar shows
            the same percentage.

            **While preparing:** **Scanning...** or **No permission to read media.**

            **After a run**, a summary made of the parts that apply, separated by commas:
            • **N uploaded**: sent this time.
            • **N already in OneDrive**: found there already, so not sent again.
            • **N no longer on this phone**: files the app remembered but that have since gone from the phone. This is not a failure.
            • **N failed**: could not be sent after several attempts.
            • **N waiting on OneDrive**: their album could not be listed this time. They are untouched and will be tried again.
            • **N still to go**: left for the next run.
            • **stopped: ...** with a reason: not signed in, OneDrive rejected the sign-in, OneDrive is full, lost connection, or no access to your photos.

            ## Where it comes from
            The backup itself reports it as it works.
        """, ui=True),

        topic("albums-list", "An album card", """
            Each album has one card. From top to bottom:

            • **Album name**, for example Camera.
            • **12 files · 340 MB.** How many files the album holds on the phone right now, and their total size. From your phone's media library, inside the chosen folders.
            • **3 optimised · 4 kept at full size · 2 pending.** A second line that appears only when it has something to say, and shows only the parts that are not zero. Optimised counts files replaced by a smaller copy. Kept at full size counts files Restore has put back and that are still ticked, which Gallery Sync leaves alone. Pending counts files not yet sent to OneDrive: the number of files on the phone minus the number the app has recorded as sent.
            • **The OneDrive line.** The only line that describes OneDrive itself. Its four forms are listed below.
            • **All files Archived**, in place of the three lines above, on an Archive album that has nothing left on the phone. This is uncommon now: when an Archive run empties an album, the album leaves this list and its mode is forgotten. See [[modes-in-depth]].
            • **The mode pill**, at the right, in the mode's colour, with a small arrow. Tap it and choose Off, Backup, Sync or Archive; the current mode is ringed. Choosing Archive first asks you to confirm. See [[dialog-archive-confirm]].

            Tap anywhere else on the card to see the album's files. See [[album-detail]].

            ## The four forms of the OneDrive line
            • **Not checked against OneDrive yet.** Nobody has asked yet. That is not the same as zero.
            • **Could not reach OneDrive when this was last checked.** The answer is unknown, not bad news.
            • **12 verified in OneDrive**, in green. OneDrive holds every file in the album at the right size.
            • **10 of 12 verified in OneDrive.** Some files are missing there, or are there at a different size.

            ## Where the OneDrive line comes from
            OneDrive is asked directly, each time you open the tab or press Rescan. A file counts as
            **verified** only if OneDrive has a file with the same name in that album's folder **and**
            reports the same size. It is deliberately not taken from the app's memory of what it once
            sent, because that memory cannot know if you have since deleted something in OneDrive.
        """, ui=True),

        topic("album-detail", "An album's file list", """
            ## What it is
            Opens when you tap an album card. It has the same green card at the top as the other tabs.
            In it, a **return arrow** at the left goes back, and beside it is the folder's name in bold.
            Under the name is a line such as **Backup · 12 files**: its mode and how many files the app
            is tracking. Then the counts ([[album-file-status]]) and, on one line, a **Sort by** box
            ([[album-file-sort]]) and, when some file has one, the **Keep at full size** heading over the
            tick boxes ([[album-file-pin]]).

            Below the card is one rounded card for each file, in the same style as the Restore tab's
            files. Each shows the file's name and, under it, one line with its size (and **video** if it is
            a video) followed by its marks. A file that Restore has put back also has a tick box at its
            right.

            If the app has not handled any files in the album yet, it says **No files tracked yet**.

            ## Where it comes from
            The app's own record of the files it has handled in that album. It is a plain list of names
            and marks: no thumbnails, no preview. Looking at photos is the job of your gallery app.
        """, ui=True),

        topic("album-file-status", "File marks and counts in an album", """
            ## What it is
            Under the album's name, a row of counts, showing only the ones that are not zero:
            **backed up**, **optimised**, **kept at full size**, **pending** and **failed**. Each file
            then carries its marks, after its size on the same line:

            • **✓ backed up.** In OneDrive, and still full size on the phone.
            • **✓ backed up · optimised.** In OneDrive, and the phone holds a smaller copy. Both are true: a smaller copy is only ever made from a file already confirmed in OneDrive.
            • **✓ optimised.** Smaller on the phone.
            • **⟳ pending.** Waiting to be sent.
            • **✗ failed.** Could not be sent after several attempts. The app does not keep retrying a file forever.

            ## Where it comes from
            The app's own record of each file's progress. The OneDrive-checked answer is the last line of
            the album card on the main list.
        """, ui=True),

        topic("album-file-sort", "Sorting an album's files", """
            ## What it is
            A **Sort by** box above the list of files, showing the order now in force: **Name**, **Date**
            or **Status**. Tap it and choose another to put the files in that order.

            • **Name.** A to Z, ignoring capital letters.
            • **Date.** Newest first, using the date your phone holds for each file. A file put back by Restore carries the date it was restored.
            • **Status.** The files that need attention come first: failed, then pending, then optimised, then backed up. Files in the same group are in name order.

            ## Good to know
            Sorting only changes how the list is shown. It changes nothing about the files, and the list
            opens in name order each time.
        """),

        topic("album-file-pin", "Keep at full size", """
            ## What it is
            A tick box at the end of a file in an album's list, under the heading **Keep at full size**.
            It appears only beside files that Restore has put back. A file Restore has not touched has no
            box. While the box is ticked, Gallery Sync leaves that one file exactly as it is, whatever
            mode the album is in.

            ## What a tick does
            • In a **Sync** album, the file is not replaced by a smaller copy.
            • In an **Archive** album, the file is not moved to the Trash. It stays on the phone, and it is not counted as scheduled to leave.
            • In a **Backup** or **Off** album it changes nothing today. The tick is remembered, in case you change the album's mode later.

            A tick can only make Gallery Sync do less. It never removes, shrinks or sends anything, so it
            needs no confirmation.

            ## Why Restore ticks them
            Restore ticks every file it brings back, so a file you have just restored is not shrunk or
            archived again straight away. Untick it whenever you like. From then on the file follows its
            album's mode again: in a Sync album it can be shrunk the next time optimising runs, and in an
            Archive album it can be offered for archiving again.

            A file you untick keeps its box while the list is open, so a slip can be put right. Once you
            leave the list, only files that are still ticked show a box.

            ## Where it comes from
            The tick is kept on your phone by Gallery Sync, for that one file. Ticking does not touch the
            file itself.
        """, ui=True),

        topic("album-merge-warning", "Duplicate album names detected", """
            ## What it is
            A red card at the top of the list. Some phones store a single folder under two spellings, for
            example **camera** and **Camera**, so the list would show two albums that are really one
            folder. When Gallery Sync finds this it joins them and tells you.

            The card shows a table with **Album Name 1**, **Album Name 2** and **Merged Album Name**,
            then three sentences: the albums have been merged to avoid confusion, the combined album's
            mode was switched to **Off**, and please choose a mode for it again. **Dismiss** removes the
            card.

            ## Where it comes from
            The app compares folder locations on the phone. Two names pointing at one location is the
            signal.

            ## Why the mode changes to Off
            Two spellings might have carried two different modes. Rather than guess, the app sets the
            merged album to **Off** and asks you. This is the one case where the app sets a mode itself,
            and it can only ever set **Off**, which cannot remove or change anything.
        """, ui=True),

        topic("dialog-archive-confirm", "Archive this album?", """
            ## What it is
            A confirmation that appears when you choose **Archive** from an album's mode pill. It says:
            • Archive will verify all files are uploaded to the cloud.
            • Archived files will be moved to your phone's Trash/Recycle Bin.
            • Please empty your Trash/Recycle Bin to free up storage.

            **Archive** confirms; **Cancel** leaves the album exactly as it was.

            ## Why it asks once
            Setting an album to Archive is you saying "take this album off the phone once it is safely in
            OneDrive". This is the moment that choice is confirmed. After that the mode stands until you
            change it or until Archive has taken every file off the phone: nobody asks again about the
            mode itself, and files added to the album while it still holds files are covered by the same
            choice. Android will still show its own confirmation each time files are actually removed.
            See [[dialog-android-trash]].

            ## What happens next
            After you confirm, the app takes you to the Archive tab, where you can watch the files being
            checked. Nothing is removed until they are confirmed in OneDrive at the same size.

            ## The bin is named differently on different phones
            Samsung Gallery calls it the Recycle Bin and the Files app calls it Trash. It is the same
            thing. Files stay there for a while and take up their full space until it is emptied, and
            the app never empties it for you.
        """, ui=True),
    ],
)
