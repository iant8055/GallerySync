from model import Chapter, topic

CHAPTER = Chapter(
    id="restore",
    title="The Restore tab",
    intro="Brings photos and videos back from your Cloud. It only reads from the Cloud; nothing there is changed.",
    topics=[
        topic("restore-overview", "What Restore brings back", """
            • **To restore:** an optimised file on the phone. Restoring puts the full-size original back in its place.
            • **To download:** a file no longer on the phone. Downloading puts it back in its album.

            Restored files get **Keep at full size**, so they are not optimised or archived again straight away. See [[album-file-pin]].

            ## Which Clouds
            • **OneDrive:** every photo and video in your backup folders, whoever put them there.
            • **Dropbox, Google Drive, pCloud, Backblaze B2, IDrive e2:** files GallerySync sent from this phone.
            • **Google Photos:** cannot be restored from.

            ## Where files go
            Back into the folder they came from. Files loose at the top of **DCIM**, **Pictures** or **Movies** go back into that folder. If a folder cannot be written, files go to a folder in **DCIM** named after the album.

            ## Safe to try
            A file is replaced only after the full download arrives and its size checks out. If anything fails, the phone's file is left as it was.
        """),

        topic("restore-hero", "Albums to Restore and Files in this album", """
            • **Album list:** **Albums to Restore**, with how many albums have something to bring back.
            • **Inside an album:** back arrow, album name, and **Files in this album** with the count and what you have selected.
            • Swipe a card right to select, left to deselect; tap to open. These work whether the header is shown or hidden.
            • **▲ Hide Details ▲** at the bottom of the card folds it to its title, number and main button; **▼ Show Details ▼** brings the swipe instruction, **Refresh** / **Clear** and the folder's **Sort** / search line back. Results and what you have selected stay visible. The tab remembers your choice.

            A dash means the list is still loading. **Checking your Cloud for more...** means OneDrive is being read. A red message means a Cloud folder could not be reached.

            The OneDrive list is remembered, and read again after ten minutes or when you press **Refresh**.
        """, ui=True),

        topic("restore-message-line", "The message under the number", """
            • **Swipe right to select / left to deselect. Tap to open.** on the album list.
            • **Swipe right to select / left to deselect → Then press Restore.** inside an album.
            • **Nothing in your Cloud is missing from this phone, so there is nothing to bring back.** when the list is empty.
            • After a restore, how many files came back and how many were left unchanged.
        """, ui=True),

        topic("restore-selected-summary", "N selected and how much to recover", """
            The files selected across all albums, and the extra space they will take on your phone. Make sure you have that space free.
        """, ui=True),

        topic("restore-buttons", "Refresh, Select all and Clear", """
            • **Refresh** (album list): reads the list again, including OneDrive.
            • **Select all** (inside an album): selects every file in it.
            • **Clear:** removes every selection.

            Greyed out while a restore is running.
        """, ui=True),

        topic("restore-folders-list", "The album list", """
            Each album card shows:
            • How many files can come back, and the space they need.
            • How many are **to restore** and **to download**.
            • How many are **already on this phone** (greyed inside; see [[restore-greyed-files]]).
            • How many are **selected here**, or a check mark when the whole album is selected.

            **Tap** to open. **Swipe right** to select the whole album, **left** to deselect.
        """),

        topic("restore-files-list", "The file list inside an album", """
            Swipe right (or tap) to select, left to deselect. Each file shows one of:
            • **now · full size:** an optimised copy, showing its smaller file size on the phone and the full file size in the Cloud.
            • **Available for download:** not on the phone.
            • **Already on this phone:** greyed; nothing to do.

            While working: **Working...** with a percentage. Then **Restored to full size**, **Back on this phone**, or **Could not restore, your file is unchanged** with the reason.
        """),

        topic("restore-greyed-files", "Why are some files greyed out?", """
            They are already on the phone at full size (same album, name and size), so there is nothing to restore. They are shown so the album matches your Cloud.

            A file with the same name but a different size is not offered: it may be an edit, and Restore never overwrites an edit.
        """, ui=True),

        topic("restore-action-bar", "Restore and Stop restoring", """
            • **Restore:** brings back every selected file, one at a time. Appears once something is selected.
            • **Stop restoring:** stops the batch. Files already restored stay restored.

            A progress bar shows how many are restored. Needs an internet connection and enough free space.
        """),

        topic("restore-edited", "A photo or video you edited", """
            If you edited an optimised copy and saved over it, restoring would replace your edit, so the app asks: **This photo has been edited since it was backed up. Overwrite it with the original?**

            • **Keep my edits:** leaves the file as it is.
            • **Overwrite:** puts the original back; your edit is replaced.

            ## Good to know
            Most editors save a new file, which is never affected. An edited file is backed up as a new file with **edited** and the date in its name; the original in your Cloud is never replaced.
        """),
    ],
)
