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

        topic("albums-totals", "Total Album Count/Size and Album Mode Count", """
            ## What it is
            Two lines of figures under the buttons.
            • **Total Album Count/Size: 12 Albums · 3.4 GB.** How many albums are being shown, and the combined size of the files in them.
            • **Album Mode Count: 3 Backup · 2 Sync · 1 Archive · 6 Off.** How many albums you have set to each mode. It only appears when **All Albums** is selected, and it is a quick way to spot albums you have not decided about: those are the ones counted under **Off**.

            ## Where it comes from
            The size line comes from your phone's own media library, counting the files that are on the
            phone right now and only inside the folders you chose during setup. It follows the filter:
            with **Sync** selected it describes only your Sync albums.

            The mode line is a plain tally of the choices you made on the album cards below.

            ## Good to know
            It will not always match your gallery app. Files in your phone's Trash are not counted, files
            outside the chosen folders are not counted, and the small **Restored** album is left out
            because everything in it is already in OneDrive. See [[numbers-explained]].
        """, ui=True),

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
            • **Sync now** starts a backup. It is greyed out when there is nothing to send, meaning every file in your Backup, Sync and Archive albums is already sent.
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
            • **3 optimised · 2 pending.** A second line that appears only when it has something to say. Optimised counts files replaced by a smaller copy; pending counts files not yet sent to OneDrive. Pending is the number of files on the phone minus the number the app has recorded as sent.
            • **The OneDrive line.** The only line that describes OneDrive itself. Its four forms are listed below.
            • **All files Archived**, in place of the three lines above, on an Archive album that has nothing left on the phone. The mode still applies if new files arrive.
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
            Opens when you tap an album card. A **left arrow** at the top goes back. Beside it are the
            album's name and a line such as **Backup · 12 files**: its mode and how many files the app
            is tracking. Below are the counts ([[album-file-status]]) and one row for each file.

            A row shows the file's name, its size, and **video** if it is a video, with its status on
            the right.

            If the app has not handled any files in the album yet, it says **No files tracked yet**.

            ## Where it comes from
            The app's own record of the files it has handled in that album. It is a plain list of names
            and marks: no thumbnails, no preview. Looking at photos is the job of your gallery app.
        """, ui=True),

        topic("album-file-status", "File marks and counts in an album", """
            ## What it is
            Under the album's name, a row of counts, showing only the ones that are not zero:
            **backed up**, **optimised**, **pending** and **failed**. Each file then carries one mark:

            • **✓ backed up.** In OneDrive, and still full size on the phone.
            • **✓ backed up · optimised.** In OneDrive, and the phone holds a smaller copy. Both are true: a smaller copy is only ever made from a file already confirmed in OneDrive.
            • **✓ optimised.** Smaller on the phone.
            • **⟳ pending.** Waiting to be sent.
            • **✗ failed.** Could not be sent after several attempts. The app does not keep retrying a file forever.

            ## Where it comes from
            The app's own record of each file's progress. The OneDrive-checked answer is the last line of
            the album card on the main list.
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
            change it: nobody asks again about the mode itself, and files added to that folder later are
            covered by the same choice. Android will still show its own confirmation each time files are
            actually removed. See [[dialog-android-trash]].

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
