from model import Chapter, topic

CHAPTER = Chapter(
    id="archive",
    title="The Archive tab",
    intro="The only place files leave your phone. Only albums you set to Archive, checked against your Cloud first, and only after your tap.",
    topics=[
        topic("archive-overview", "How Archive works, step by step", """
            **Archive needs Android 11 or newer.**

            1. **Set an album to Archive** on the Albums tab and confirm. See [[dialog-archive-confirm]].
            2. **Press Check these files.** Every file is checked against its Cloud. Files not there yet are sent first.
            3. **See the result:** a check mark or red cross on each file, and how much space will be freed.
            4. **Say Yes.** Android asks to move the confirmed files to the phone's trash. See [[dialog-android-trash]].
            5. **Empty the bin** when you are ready. The space returns only then.

            ## What "safely" means
            The Cloud must confirm, right then, that it holds the file at the same size. If the Cloud cannot be asked, nothing is removed. For an optimised photo, the original's size is checked.

            ## Which Clouds
            OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 and Backblaze B2. Not Google Photos.

            ## Good to know
            • Archive needs you present: Android requires a tap. See [[dialog-exit-warning]].
            • Files added to an Archive album later are covered too.
            • When the last file leaves, the album leaves the list and its mode is forgotten. If it comes back, it starts at Off.
        """),

        topic("archive-hero", "Files to Archive", """
            **Files to Archive**, with the number of files that will be archived: in every Archive folder on the folder list, or in the folder you opened. Files you kept on the phone are not counted.

            Below it: your Archive albums, and a reminder that archived files can be brought back on the **Restore** tab.
        """, ui=True),

        topic("archive-folder-list", "The Archive folders", """
            One card per Archive folder: how many files are waiting and their size, plus any kept, held back by the age filter, confirmed, or not confirmed.

            • On the folder list, the button reads **Check all folders** and checks every folder.
            • Tap a folder to open it; **Check these files** then checks only that folder.

            Choosing a folder removes nothing and does not change any mode.
        """, ui=True),

        topic("archive-check-button", "Check these files, and what it reports", """
            • **Check these files** (or **Check all folders**): starts the check.
            • **Checking with your Cloud...** while the Cloud is asked.
            • **Batch** progress while files are being removed.
            • How many files were removed and the space freed, or **Nothing was removed.**

            OneDrive is checked by listing each album folder (name and size). Dropbox, Google Drive, pCloud, Backblaze B2 and IDrive e2 are asked about each file.

            "Freeing" means after you empty the bin.
        """, ui=True),

        topic("archive-age-filter", "Only show files older than…", """
            Limits this check to files at least that old: **1 hour** to **1 year**, or **All**. Newer files are greyed and left for a later check.

            It starts at your default from Settings. Changing it here lasts for this visit only.
        """, ui=True),

        topic("archive-empty", "When there is nothing to archive", """
            • **No album is set to Archive:** set one on the Albums tab.
            • **A zero:** everything in your Archive albums has already left the phone.
            • **This version of Android has no media trash:** Archive needs Android 11 or newer.
        """, ui=True),

        topic("archive-file-list", "The file list and its marks", """
            One card per file in your Archive albums.

            ## Keeping a file on the phone
            Use it for a file you want to keep on the phone while the rest of the album is archived. Swipe **left** to keep it: it greys out and reads **Not archiving**, and is remembered. Swipe **right** to include it again. A file greyed as **too recent for your filter** is held back by the age filter.

            ## Marks
            • **Spinner:** being checked, sent or removed.
            • **Green check mark:** confirmed in your Cloud, or removed.
            • **Red cross:** not confirmed; it stays on the phone.

            ## Messages
            **Backing up...**, **Moving to Trash/Recycle Bin...**, **Removed from this phone.**, or in red why a file stays: **Could not check your Cloud**, **Not in your Cloud**, or **In your Cloud but the wrong size**.
        """, ui=True),

        topic("archive-prompt", "The question: All files validated, or some validated", """
            After the check:
            • **All files validated:** every file is confirmed.
            • **N files validated:** only those are confirmed; the rest stay on the phone.
            • **Nothing can be archived:** none were confirmed. **Continue** closes the message.

            **Yes** asks Android to move the confirmed files to the bin. **No** removes nothing. A large album may need several Android confirmations (2,000 files each).
        """, ui=True),

        topic("dialog-android-trash", "Android's own confirmation", """
            Android asks you to allow moving the files to the trash. Its wording varies by phone.

            • Android allows 2,000 files per request, so a large album is done in batches.
            • If you say no, nothing is removed.
        """),

        topic("dialog-exit-warning", "Files ready to Archive (the leaving reminder)", """
            Appears if you press Back to leave while files are confirmed and waiting to be archived.

            • **Archive now:** opens the Archive tab.
            • **Leave:** closes the app. Nothing is removed.

            Counts OneDrive files only: only OneDrive copies are confirmed ahead of time. Other Clouds are checked when you press **Check these files**. The reminder does not appear if you press Home or swipe the app away.
        """, ui=True),
    ],
)
