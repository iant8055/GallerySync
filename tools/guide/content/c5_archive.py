from model import Chapter, topic

CHAPTER = Chapter(
    id="archive",
    title="The Archive tab",
    intro="Archive is the one place where files leave your phone. It only ever handles albums you "
          "set to Archive, it checks every file against OneDrive first, and it ends with your tap.",
    topics=[
        topic("archive-overview", "How Archive works, step by step", """
            1. **You choose it, once, per album.** On the Albums tab, set an album's mode to **Archive** and confirm. This is your consent. See [[dialog-archive-confirm]].
            2. **You come here and press Check these files.** Every file in your Archive albums, except any you have chosen to keep on your phone, is checked against OneDrive. Anything not there yet is sent first and then checked.
            3. **The app tells you the result.** Each file gets a tick or a red cross, and a prompt says how many are confirmed and how much room will be freed.
            4. **You say Yes.** The app asks Android to move the confirmed files to your phone's Trash or Recycle Bin. Android shows its own confirmation. See [[dialog-android-trash]].
            5. **You empty the bin when you are ready.** Removed files keep taking their full space until you do, and the app never empties it for you.

            ## What "safely" means
            A file is only removed if OneDrive confirms it holds the file **and** reports the same size as
            your copy. A file OneDrive does not confirm stays on your phone, and the screen says why.

            ## What Archive cannot do by itself
            It cannot run while you are away. Android requires you to be present to approve removing
            files, so the app brings you back here instead of doing it silently. If you try to leave the
            app while files are checked and waiting, it reminds you. See [[dialog-exit-warning]].

            ## It is a standing instruction, until the album is empty
            An album set to Archive stays that way until you change it. Files that arrive in that album
            while it still holds files, from the camera, a download or a file manager, are covered by the
            same choice.

            When an Archive run takes the last file off the phone, the album is finished. It leaves the
            Albums tab and GallerySync forgets its mode. If you later bring the album back with
            **Restore**, or something creates the folder again, it is a new album and starts at Off,
            so it can never come back as Archive. That is what stops an album being
            archived, restored and archived again by itself.

            ## Availability
            Moving files to the bin needs Android 11 or newer. On older versions Archive is not offered,
            rather than deleting files with no way back.
        """),

        topic("archive-hero", "Files to Archive", """
            ## What it is
            The green card, laid out like the one on the Restore tab. On the left, **Files to** with
            **Archive** directly under it; in the right half, a number. Under them, the names of your
            Archive albums, then this sentence: **Every file below is checked against OneDrive first. Anything
            that is not there yet is backed up before it is verified.** At the foot of the card, a
            reminder: **To restore archived files or albums, check the Restore tab.**

            ## Where it comes from
            The number is a live count of the files on the phone, in albums set to Archive, that will be
            archived. Files you have swiped out of Archive are not counted. When you arrive it is worked
            out from the phone; it does not need the internet. The album names are the
            albums you set to Archive.
        """, ui=True),

        topic("archive-check-button", "Check these files, and what it reports", """
            ## What it is
            The button in the green card, and the messages that replace it as the process moves along:
            • **Check these files** starts the check.
            • **Checking against OneDrive...** with a small spinner while OneDrive is asked.
            • **Batch 1 of 3** while files are being removed. See [[dialog-android-trash]] for why there may be several.
            • **12 files removed from this phone, freeing 340 MB.** or **Nothing was removed.** when it finishes.

            ## Where it comes from
            The check asks OneDrive for the list of files in each album's folder and compares names and
            sizes with the files on your phone. The result messages report what actually happened.

            ## Good to know
            "Freeing 340 MB" is a promise about after the bin is emptied. A removed file still takes its
            space in the bin, so the room does not come back at the moment you tap.
        """, ui=True),

        topic("archive-age-filter", "Only show files older than…", """
            ## What it is
            A control above the file list: **Only show files older than**, with a choice from 1 hour up
            to 1 year, or **All**. It narrows what **Check these files** looks at this round, to files
            whose modified date is at least that old. A newer file is neither archived nor lost — it is
            simply left out of this check, shown greyed further down the list, and comes back into the
            check on its own once it is old enough, or the moment you widen the filter.

            The control stays visible even when the filter currently hides every file in an Archive
            album, so there is always a way back to a wider view.

            ## Where the starting choice comes from
            Settings → Archive has a default for this, which is what the filter is set to each time you
            open the tab fresh. Changing the filter here, on the Archive tab, only lasts for this visit
            and does not change that default.

            ## Good to know
            This is different from swiping a file out. Swiping is a choice about one file that
            GallerySync remembers until you swipe it back. The age filter is a choice about this round
            of checking, for every file at once, and remembers nothing about which files it held back.
        """, ui=True),

        topic("archive-empty", "When there is nothing to archive", """
            ## What it is
            Instead of a list, the card explains why:
            • **No album is set to Archive. Nothing here will remove anything from your phone.** With a hint that says to set an album to Archive on the Albums tab, and its files will be listed here for checking before anything leaves the phone.
            • **A zero, and no messages.** You have Archive albums but everything in them has already left the phone. The mode is finished for now and still stands.
            • **This version of Android has no media trash, so removing local copies is not offered here.** Your Android is older than version 11.

            ## Where it comes from
            From your album modes and the phone's Android version.
        """, ui=True),

        topic("archive-file-list", "The file list and its marks", """
            ## What it is
            One rounded card for each file in your Archive albums, in the same style as the file cards on the Restore tab: its name, then its size under it, then a mark on the right. A file you put into an Archive album later appears here too, before anything is archived.

            **Keeping a file on your phone.** Swipe right to select and left to deselect, as everywhere in the app. Swipe a card **left** to deselect it and it fades and reads **Not archiving**. It is left out of every check and every removal, and GallerySync remembers your choice, even after you close the app. Swipe it **right** to select it again and it returns to the list, ready to be archived. A green tick means a file has been checked against OneDrive in the current check, so a file you bring back has no tick until the files are checked again, and nothing is removed until they are. Swiping a card that is already that way does nothing, so a run of swipes cannot undo itself. You cannot swipe while a check or a removal is running. If you use a screen reader, each card offers **Deselect (keep on this phone)** or **Select (archive this file)** instead.

            An album with a file you have kept stays set to Archive, so files added to it later are still covered by the mode you set. If you would rather it stopped, change the album's mode on the Albums tab.

            **A file younger than your age filter** fades the same way and reads **too recent for your filter** instead of **Not archiving**. See [[archive-age-filter]]. It still swipes: swiping it left pins it permanently, which is a stronger version of the same thing.

            **The mark**
            • **A spinner.** Being checked, backed up or removed.
            • **A green tick.** Checked against OneDrive and confirmed there at the right size, or already removed.
            • **A red cross.** OneDrive did not confirm it, so it stays on your phone.
            • **Nothing.** Not looked at yet.

            **The words under the name**, in place of the size while something is happening:
            • **Backing up...** The file was not in OneDrive, so it is being sent.
            • **Moving to Trash/Recycle Bin...** Being removed.
            • **Removed from this phone.**
            • In red, why a file is staying: **Could not check OneDrive**, **Not in OneDrive**, or **In OneDrive but the wrong size**. Each ends "staying on your phone". "Could not check" and "is not there" are kept separate because they need different reactions. Being unable to ask is not the same as an answer.

            ## Where it comes from
            OneDrive's list of files for each album, compared with the phone. It asks once per album, not
            once per file.

            ## Good to know
            The number in the green card counts only the files that will be archived, not the ones you
            have kept. A kept file is the same setting as **Keep at full size** on the Albums tab, so
            it is counted there as well.
        """, ui=True),

        topic("archive-prompt", "The question: All files validated, or some validated", """
            ## What it is
            After the check, a card asks one question, in one of three forms:

            • **All files validated.** All files are confirmed in OneDrive. Archiving them moves the local copies to your phone's Trash/Recycle Bin, and frees the stated amount once you empty it.
            • **12 files validated.** Some files could not be confirmed and will stay on the phone. Archiving moves only the confirmed ones. The count and size describe only those.
            • **Nothing can be archived.** OneDrive did not confirm a single file, so they all stay. A single **Continue** button closes the message.

            If there are many files, it also says something like **Android can only ask about 2,000
            files at a time, so this will take 2 separate confirmations.**

            **Do you want to continue?**
            • **Yes** asks Android to move the confirmed files to the bin. Android then shows its own confirmation.
            • **No** puts the question away. Nothing has been removed, and you can check again later.

            ## Where the numbers come from
            The count is the files OneDrive just confirmed. The size is those files' size on your phone.
            It says "frees" that much once you empty the bin, because the bin keeps the bytes until then.
        """, ui=True),

        topic("dialog-android-trash", "Android's own confirmation", """
            ## What it is
            When you say Yes, Android puts up its own screen asking you to allow GallerySync to move the
            files to the trash. The wording and look belong to Android and your phone maker, and change
            between phones.

            ## Why it appears
            Android insists on a tap for this, and only lets an app that is open on screen ask. Gallery
            Sync does not add a second question of its own on top: the choice you made when you set the
            album to Archive is the consent, and this is Android's own safeguard.

            ## Why there can be more than one
            Android can only ask about 2,000 files at a time, so a large album needs a series of
            confirmations. The Archive tab shows **Batch 1 of 3** so you can see how far along it is.

            ## If you say no
            Nothing is removed. The files stay where they are.
        """),

        topic("dialog-exit-warning", "Files ready to Archive (the leaving reminder)", """
            ## What it is
            A message that pops up if you try to leave the app with the back gesture while files are
            checked, confirmed in OneDrive and waiting for your approval. It says how many files are
            **verified in OneDrive and ready to leave this phone**, and that they stay where they are
            until you approve the removal.

            • **Archive now** takes you to the Archive tab.
            • **Leave** closes the app. Nothing is removed.
            • Tapping outside the message keeps you where you were.

            ## Where it comes from
            The app's check of your Archive albums. The count is files in those albums that are
            confirmed in OneDrive and still on the phone.

            ## Good to know
            It is a reminder, not a guarantee. Android lets an app notice the back gesture and nothing
            else, so pressing Home or swiping the app away from the recent-apps list will not show it. The
            Archive tab is always there when you come back.
        """, ui=True),
    ],
)
