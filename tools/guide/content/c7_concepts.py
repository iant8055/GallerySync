from model import Chapter, topic

CHAPTER = Chapter(
    id="concepts",
    title="How it works, in plain English",
    intro="Short explanations of the ideas behind the buttons. The chapters above link here when a "
          "word needs more than a line.",
    topics=[
        topic("modes-in-depth", "The four modes", """
            Every album has exactly one mode. You set it with the coloured pill on the album's card, on
            the Albums tab. The cloud copy is identical in the three modes that upload; what differs is
            what happens to the file **on your phone**.

            • **Off.** Nothing is sent and nothing on the phone is touched. This is where every album starts. It means "I have not decided, or I do not want this album looked after".
            • **Backup.** Copies the album to OneDrive and leaves the phone alone, always. The safe choice, and the one to pick when you are unsure.
            • **Sync.** Backs up, and also makes the album eligible for optimising: photos can be replaced by smaller copies that stay in your gallery. Optimising itself is controlled in Settings, under Sync, and is off until you switch it on.
            • **Archive.** Backs up, verifies, and then takes the files off the phone into its Trash/Recycle Bin, after your tap. The album leaves your gallery. The only mode that removes files. It is a standing instruction while the album holds files: files added to it later are covered too. Once Archive has emptied the album, the album leaves the list and its mode is forgotten.

            ## The Camera album has no Sync
            Camera offers **Off**, **Backup** and **Archive**. Sync would make a new photo smaller the
            moment it is backed up, so Camera has a separate control instead: choose an age and optimise
            only what is already old. See [[album-camera-optimise]].

            ## Only you set a mode
            Nothing sets an album's mode for you: not first-time setup, not the optimise settings, and not
            a backup. The one exception is the merge of duplicate album names, which can only ever set
            **Off**. See [[album-merge-warning]].

            ## Which one should I pick?
            • Camera, and albums you want protected but kept as they are: **Backup**.
            • Albums that fill the phone, where you want space back but still want to see the photos: **Sync**. For Camera, use **Backup** and optimise the old photos from its file list ([[album-camera-optimise]]).
            • Old albums you rarely open and want off the phone: **Archive**.
            • Not sure yet: leave it **Off**, or use **Backup**. You can change a mode at any time.

            ## Changing your mind
            Switching between Off, Backup and Sync is instant and removes nothing. Switching **to**
            Archive asks you to confirm. Switching **away from** Archive stops any further removals; files
            already in the bin stay there until you restore or empty them.
        """),

        topic("how-verification-works", "How verification works", """
            Before the app shrinks or removes anything on your phone, it makes sure a good copy exists in
            OneDrive. That check is the guarantee, and nothing skips it.

            ## The test
            OneDrive is asked for the list of files in the album's folder. A file on your phone is
            **verified** only if OneDrive lists a file with the **same name** and reports the **same
            size**. Anything less, such as missing, or present at a different size, is not verified.

            ## When it is done
            • On the Albums tab, every time you open it or press Rescan, for the OneDrive line of each album.
            • On the Archive tab, when you press Check these files, and the files not found are sent first.

            ## Three different answers
            • **Verified.** OneDrive has it.
            • **Not there, or the wrong size.** The file stays on your phone, and the app says so.
            • **Could not check.** The internet failed or OneDrive did not answer. That is not the same as "missing", and nothing is removed on the strength of an unanswered question.

            ## What it cannot promise
            A size check cannot catch every kind of damage. So keep an eye on your own backups: open
            OneDrive now and then and open a few photos, and do it before you empty any bin. For
            anything you could not bear to lose, keep a copy somewhere this app cannot reach.
        """),

        topic("how-optimising-works", "How optimising works", """
            Optimising swaps a file on your phone for a smaller copy of itself. The point is to let the
            phone stop filling up while everything stays in your gallery.

            ## Photos
            • The copy is reduced to about **2048 pixels on the long edge**, roughly a tenth of the size.
            • It keeps its name and place, so your gallery shows it as usual, and it keeps information such as date and location.
            • It carries a small **cloud badge** so you can tell it has been optimised.
            • The **full-quality original stays in OneDrive**, untouched.
            • A photo that would not get smaller is left as it is.

            ## Video
            • A clip is re-encoded at a lower resolution: **480p** (High, the most shrinking), **720p** (Medium) or **1080p** (Low). Nothing is ever cut, so a clip is never shortened.
            • It stays in your gallery under its own name.
            • It happens in the background, a few clips at a time. Automatic waits for the phone to be charging; Manual starts when you press **Sync now**. First-time setup also optimises video once, if you chose a plan that includes it.
            • Only clips old enough to pass the age you set are touched, and a clip that would not get smaller is left as it is and not offered again.

            ## The rules it always follows
            • Only files inside albums set to **Sync**.
            • Only files OneDrive has confirmed, at the same size.
            • Never anything permanently deleted: the original is in OneDrive.

            ## Getting the original back
            Open the **Restore** tab, choose the folder or files, and press **Restore**. The original
            replaces the smaller copy in the same place. Editing an optimised photo starts from the smaller
            copy, so restore first if you want the full detail.
        """),

        topic("where-deleted-files-go", "Where removed files go", """
            Gallery Sync never permanently deletes anything. Everything it removes goes somewhere you can
            recover it from.

            ## On your phone
            When Archive removes files they go to Android's media trash. The file is renamed in place
            and hidden from your gallery's normal view, keeps its size, and stays for a limited time,
            usually around 30 days, which Android decides. It shows up in your gallery's bin:

            • On Samsung phones, **Samsung Gallery's Recycle Bin**.
            • On other phones, the **Trash** in the Files app (and often in the gallery app too).

            Open that bin to put files back.

            ## The space is not freed at once
            A file in the bin still takes its full space. Room only returns when you **empty the bin
            yourself**, or when the time runs out. The app never empties it, and every "frees X" message
            means "frees X once the bin is emptied".

            ## In OneDrive
            If you use the option to be asked about OneDrive copies after deleting on your phone, files
            you approve go to your **OneDrive recycle bin**, where you can restore them. The app never
            empties that either.
        """),

        topic("background-work", "When things happen", """
            ## Backing up
            Runs by itself once albums are set to Backup, Sync or Archive:
            • A short while after new photos or videos appear.
            • A safety check about every six hours.
            • Only on Wi-Fi, unless **Use mobile data** is on, and only when the battery and storage are not low.

            **Sync now** starts a run straight away, whether or not the phone is charging.

            ## Optimising photos
            **Automatic** happens as soon as a file reaches a Sync album, or an album is switched to Sync:
            after the file has been sent and confirmed in OneDrive, and in the background. **Manual**
            happens when you press **Sync now** on the Albums tab. Photos outside the folders you gave
            access to need Android's confirmation, so they wait until you open the app or press **Sync now**.

            ## Optimising video
            **Automatic** starts after a backup run has finished everything, when an album is switched to
            Sync, and when you change the video settings, and it waits for the phone to be charging.
            **Manual** starts when you press **Sync now**, straight away. It runs in the background and
            you can close the app.

            ## Archiving
            Only when you say Yes on the Archive tab. Android requires a tap, so this cannot run while
            you are away.

            ## Restoring
            Only when you press Restore.

            ## Closing the app
            Backing up carries on after you leave the app with the back gesture or the Home button. Swiping
            the app away from the recent-apps list is different: Android may then stop its background
            jobs until you next open it. If you are waiting for a large backup, open the app again after
            swiping it away.
        """),

        topic("where-your-data-lives", "Where your data lives", """
            • **Your photos and videos** go from your phone directly to your own OneDrive account, over an encrypted connection. Gallery Sync has no server, so they never pass through anything belonging to the developer.
            • **Your Microsoft sign-in** is kept in encrypted storage on your phone. You type your password on Microsoft's own page, never into the app.
            • **The app's own record** of what it has sent, its settings and your folder choices are kept in private storage on your phone, not readable by other apps.
            • **The developer collects nothing:** no analytics, no crash reporting, no advertising.

            The full details are in the **Privacy Policy** card at the bottom of Settings. To sign out,
            remove the app's access to your Microsoft account, or clear its data, use the **Delete Account
            Info** card.
        """),

        topic("numbers-explained", "Why the numbers may differ from your gallery app", """
            The counts in Gallery Sync answer a narrower question than your gallery does. They count:

            • Only files **on the phone right now**.
            • Only inside the **folders you chose**.
            • Not files in your phone's **Trash**.
            • Not the small **Restored** album, whose files are already in OneDrive.

            Your gallery app may also show cloud-only items, or group folders differently. Neither
            number is wrong; they are counting different things. Each figure in Gallery Sync says what it
            counts, and the **(?)** beside it says where it comes from.
        """),
    ],
)
