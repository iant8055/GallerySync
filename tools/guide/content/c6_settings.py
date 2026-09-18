from model import Chapter, topic

CHAPTER = Chapter(
    id="settings",
    title="The Settings tab",
    intro="The things you set once and rarely change. The tab is a single scrolling page divided into "
          "green bands: General, Backup, Albums, Sync, Restore and Archive. The How To Guide is the "
          "first card in General, and the policy pages are at the bottom. Each band has a (?) beside "
          "its name.",
    topics=[
        topic("settings-overview", "How the Settings tab is laid out", """
            Each green band is a section heading, with a **(?)** that explains the whole section. Under
            it are that section's settings. Switches turn something on or off; boxes with the current
            value open a short list; buttons open a page or a dialog.

            ## Two things that are true of every setting
            • Changing one takes effect straight away. There is no Save button.
            • Nothing on this page removes a file from your phone. Removing files only happens through the Archive mode.

            ## Settings and first-time setup are separate
            Setup answered its own questions once. Settings does not read them back. See
            [[setup-and-settings]].
        """),

        topic("settings-section-general", "General", """
            ## What it is
            Settings that apply across the whole app: the **How To Guide** card, **Language**,
            **Appearance** and **Use mobile data**.

            ## Where it comes from
            These are stored on your phone and change how the app looks and when it uses your data plan.
        """, ui=True),

        topic("settings-how-to-guide-card", "The How To Guide card", """
            ## What it is
            The first card in **General**, above **Language**. It reads **How To Guide**, with a line
            under it saying it explains what every screen and every line means, and where its
            information comes from.

            ## What it does
            Tap it to open this guide inside the app. **Close** returns you to Settings. You reach the
            same guide from any **(?)** pop-up, by pressing **Read more in the How To Guide**, which
            opens it at the topic you were reading.

            ## Where it comes from
            The guide is a web page, shown from the internet, so it needs a connection. If it cannot be
            loaded you will see **This page could not be loaded**, with **Open in browser** to try it
            there.
        """),

        topic("settings-language", "Language", """
            ## What it is
            A line reading **Language** with **Multi-Language Support Coming Soon** under it. The app is
            available in English only at the moment, and there is nothing to choose yet.
        """, ui=True),

        topic("settings-appearance", "Appearance", """
            ## What it is
            A box showing **System**, **Light** or **Dark**. Tap it to choose.

            • **System** follows your phone's own light/dark setting, and is the starting choice.
            • **Light** and **Dark** always use that look, whatever the phone is set to.

            ## Where it comes from
            Your choice is stored on the phone and applies to every screen in the app.

            ## Good to know
            The guide, privacy and delete-account pages open in a viewer that follows your phone's
            light/dark setting, rather than this choice.
        """, ui=True),

        topic("settings-mobile-data", "Use mobile data", """
            ## What it is
            A switch. Off (the starting choice) says **Wi-Fi only.** On says **Syncs on Wi-Fi and mobile
            data.**

            ## What it controls
            Whether backups are allowed to run over your mobile data connection. It covers automatic
            backups and **Sync now**.

            ## Good to know
            Off is the safer choice: a first backup can be many gigabytes, which on a metered plan can
            be expensive. Leave it off unless your data is unlimited and your Wi-Fi is poor. Anything
            taken while away from Wi-Fi simply waits until the phone is back on it.
        """, ui=True),

        topic("settings-section-backup", "Backup", """
            ## What it is
            Your OneDrive account and the folder where backups are stored: the connected account with
            its **Sign out** button, and **Current folder location** with a **Change** button.

            ## Good to know
            Changing the folder does not affect files already backed up.
        """, ui=True),

        topic("settings-account", "Your account and Sign out", """
            ## What it is
            The Microsoft account the app is connected to, shown as the email address, with a **Sign out**
            button.

            ## Where it comes from
            Microsoft tells the app which account you signed in with. It is shown on your phone only and
            is not sent anywhere else.

            ## What Sign out does
            It removes the app's Microsoft sign-in from your phone. Nothing in your OneDrive is touched,
            and your files stay where they are. Backups stop until you sign in again, and the app will
            ask you to.

            To also cancel the app's access from Microsoft's side, follow the steps on the Delete Account
            Info page in Settings.
        """, ui=True),

        topic("settings-destination", "Current folder location", """
            ## What it is
            The OneDrive folder where new backups are placed, written like **OneDrive / Samsung Gallery/DCIM**,
            with a **Change** button.

            ## Where it comes from
            It starts as **Samsung Gallery/DCIM**, the same place Samsung's own sync used, which is how
            the app finds what is already there and avoids sending it twice. If you have changed it, your
            choice is stored on the phone.

            ## Changing it is safe
            Only new uploads move. The old folder stays in the search, so nothing already uploaded is
            lost and nothing is sent twice. See [[dialog-destination]].
        """, ui=True),

        topic("dialog-destination", "Where new backups go (the Change dialog)", """
            ## What it is
            A dialog opened by **Change**:

            • **Folder in OneDrive.** A box holding the folder path. Use a plain path like **Pictures/Backup**, with no slash at the start or end. An unusable path shows **That folder path cannot be used.**
            • **A note.** New photos and videos will go here. The files already backed up stay where they are, and Gallery Sync keeps looking in the old folder too, so nothing is uploaded twice. It shows the number of files already backed up when it knows it.
            • **Browse...** opens a picker so you can pick a folder from OneDrive. See [[dialog-folder-picker]].
            • **Reset to default.** Appears when the path is not the default. Puts back Samsung Gallery/DCIM.
            • **Use this folder** saves. **Cancel** closes without changing anything.

            ## Where it comes from
            The default and the count of files already backed up come from the app's own record on the phone.
        """, ui=True),

        topic("dialog-folder-picker", "Choose a folder (the OneDrive picker)", """
            ## What it is
            A list of the folders in your OneDrive, starting at the top (**OneDrive**).

            • Tap a folder to go into it. **Up one folder** goes back.
            • **Use this folder** picks the one you are looking at.
            • **New folder** asks for a **Folder name** and **Create**s it in OneDrive. If the name is already taken it says **Could not create that folder.**
            • **Show more** loads more folders when a long list has been cut short.
            • **No folders here.** The folder you are in has no folders inside it.
            • **Could not reach OneDrive.** with **Try again**, if the connection failed.

            ## Where it comes from
            OneDrive itself, live. It only lists folders, never your photos.
        """, ui=True),

        topic("settings-section-albums", "Albums", """
            ## What it is
            Which folders to watch, what happens to cloud copies when you delete from this phone, and the
            mode a brand new album starts with.
        """, ui=True),

        topic("settings-folders", "Folders to back up", """
            ## What it is
            The folders Gallery Sync is allowed to look in, each shown as a path such as **Internal storage
            / DCIM**, with a **Remove** button. **Add a folder** opens Android's folder picker.

            ## What it means
            Gallery Sync only looks in the folders you choose. Anything outside them is not scanned,
            listed or touched. Most people want DCIM, where the camera saves photos. A phone can report
            around ninety albums, almost all of them app caches and thumbnails, which is why the choice
            is yours.

            ## Messages
            • **No folders chosen yet. Nothing is backed up, scanned or changed until you pick one.**
            • **That folder cannot be used. Choose a folder rather than a whole drive.**

            ## Where it comes from
            The list is the folders you have given the app lasting access to through Android's folder
            picker, stored on the phone. **Internal storage** is the phone's own memory; an SD card shows its own name.

            ## Removing is safe
            Removing a folder only stops it being watched. Nothing already backed up is lost, and adding
            it again restores what you had set for its albums.
        """, ui=True),

        topic("settings-deletion", "When you delete a photo or video from this phone", """
            ## What it is
            Two choices, one selected at a time:

            • **Leave the OneDrive copy** (the starting choice). Nothing is ever removed from OneDrive. Deleting on your phone only frees space here.
            • **Ask me about the OneDrive copy.** Gallery Sync shows you what has gone from your phone and asks before removing anything from OneDrive. It never removes anything on its own.

            ## Why Leave is the default
            A copy left in OneDrive costs a little storage. A copy removed by mistake could cost you the
            photo, because the one on your phone is already gone.

            ## Good to know
            The app never treats a missing file as an instruction to delete. Even under **Ask**, nothing
            moves without you reading a list and confirming. See [[settings-deletion-review]].
        """, ui=True),

        topic("settings-deletion-wait", "Wait before asking", """
            ## What it is
            Appears only when you choose **Ask**. It sets how many days a file has to be missing from your
            phone before it is offered for removal from OneDrive: **1**, **7** (the starting choice),
            **30** or **90** days.

            ## Why there is a wait
            A file seen missing once is not proof it was deleted. A phone with its storage card out looks
            the same as a deleted photo for a while, and so does a gallery app that is reindexing. A
            week of continuous absence is much stronger evidence.

            ## Good to know
            A longer wait means space in OneDrive is reclaimed later. A shorter one makes it more likely
            that a temporary absence is offered as a deletion. Under **Leave**, this setting does nothing,
            so it is hidden.
        """, ui=True),

        topic("settings-deletion-review", "Gone from this phone", """
            ## What it is
            Appears under **Ask**. It lists what could be removed from OneDrive:

            • **Nothing to review. Files you delete will appear here once they have been gone for a while.** when the list is empty.
            • **12 files no longer on this phone, still in OneDrive, taking 340 MB.** followed by up to eight names, each shown as **name · album**, and a button **Remove these from OneDrive**. The list is capped because this is a review, not a file manager.
            • After a run, results: **N files removed from OneDrive.**, **N files were back on your phone, so their OneDrive copies were kept.** and **N files could not be removed. Nothing was lost; try again later.**

            ## Where it comes from
            The app compares its own record of files it sent with what is on the phone now. A file only
            appears once it has been missing for the waiting period.

            ## Good to know
            Files that came back are reported, not hidden. It shows the app checks rather than assumes.
            The button opens a confirmation: [[dialog-deletion-confirm]].
        """, ui=True),

        topic("dialog-deletion-confirm", "Remove files from OneDrive? (the confirmation)", """
            ## What it is
            A confirmation with the number and size: **Remove 12 files from OneDrive?** It says these
            files are not on your phone any more, and how much removing them frees in OneDrive.

            They go to the **OneDrive recycle bin**, where you can restore them yourself. Gallery Sync
            never empties that bin.

            **Remove from OneDrive** goes ahead. **Keep them** cancels, and it is worded that way so the
            safe choice says what it does.

            ## Where it comes from
            The number and size are the files in the list you just reviewed.
        """, ui=True),

        topic("settings-default-mode", "Default mode for new albums", """
            ## What it is
            A box showing **Off**, **Backup** or **Sync**. It is the mode a brand-new album starts with.
            Existing albums are not changed. The starting choice is **Off**, so nothing happens to a new
            album until you decide.

            ## Why Archive is not on the list
            Archive removes files from the phone. A default that did that would apply to albums you have
            never seen, and Archive must always be a choice you make about one specific album.

            ## Where it comes from
            It is used the first time the app sees a new album, for example after you add a folder or a
            new app creates its own album. It is stored on the phone.
        """, ui=True),

        topic("settings-section-sync", "Sync", """
            ## What it is
            How photos and videos are optimised to save space. Files are only changed after they are
            verified in OneDrive, and only inside albums you set to Sync.
        """, ui=True),

        topic("settings-optimise-photos", "Optimise photos", """
            ## What it is
            A switch. Off is the starting choice. On says **Photos in Sync albums are optimised for
            storage.** A **Mode** row appears under it. See [[settings-optimise-mode]].

            ## What optimising a photo does
            The photo on your phone is replaced with a smaller copy, reduced to about 2048 pixels on the
            long edge and roughly a tenth of the size. It stays in your gallery and opens normally in every
            app, and carries a small cloud badge so you can tell which ones are optimised. The full-quality
            original stays in OneDrive, untouched. Photos are optimised whatever their age.

            ## Only in Sync albums
            Backup and Archive albums never optimise, and Off does nothing. A photo is only ever
            optimised after OneDrive has confirmed it holds the original at the same size, and a
            photo that would not get smaller is left alone.

            See [[how-optimising-works]].
        """, ui=True),

        topic("settings-optimise-video", "Optimise video", """
            ## What it is
            A switch. Off is the starting choice. Turning it on reveals **Mode**, **Older than** and
            **Quality**, and a status line with a button underneath. See
            [[settings-optimise-video-status]].

            ## What it does
            Old video in Sync albums is replaced by a smaller copy on your phone. The full-quality
            original stays in OneDrive, and the Restore tab brings it back. Nothing is ever cut:
            shortening a clip is the one thing optimising never does, and the smaller copy is checked
            against the original's length before it replaces anything.

            ## Which clips it touches
            A clip has to pass every one of these:
            • It is in an album set to **Sync**.
            • OneDrive has confirmed it at the same size.
            • It is older than the age you chose.
            • It is in a folder you gave Gallery Sync access to. A clip anywhere else is left alone, and the status line says how many.
            • It has not been optimised already, and has not been found already small enough to leave.

            ## When it happens
            **Automatic** starts by itself, and only while the phone is charging, because re-encoding
            video is the heaviest thing the app does. It starts:
            • When a backup run finishes everything. That happens after every new photo and on the six-hourly check, which is also how a clip that has just grown older than your chosen age is noticed.
            • When you change Optimise video, Mode or Older than.

            **Manual** waits until you press the button. In either mode the button starts it straight
            away, without waiting for the charger.

            It works in the background, a few clips at a time, and you can close the app. Switching
            Optimise video off stops it after the clip in progress.

            See [[how-optimising-works]].
        """, ui=True),

        topic("settings-optimise-video-status", "The video status and button", """
            ## What it is
            Shown under the video settings once Optimise video is on. It says one of four things:

            • **12 clips can be optimised.** A smaller copy stays on the phone and the full-quality original stays in OneDrive. Followed by a button, **Optimise 1.4 GB of video**, which starts it now.
            • **No video is ready to optimise.** A clip has to be in a Sync album, backed up to OneDrive, and older than the age you set.
            • **Video optimising is waiting for the phone to charge.** An automatic run is queued and needs the charger. The button starts it now instead.
            • **Optimising video in the background. 9 clips left.** A run is working. The number falls as each clip is done.

            If some clips would qualify but are in a folder you have not given access to, a further line
            says so: **N clips are in folders you have not given access to, so they are left alone.**

            ## Where the numbers come from
            The app's own record of clips it has backed up, narrowed to those in Sync albums, older than
            your chosen age, still on the phone, and in a folder you gave access to. The size is what
            those clips take on your phone now.

            ## Good to know
            "Ready" counts clips the app can rewrite without asking you. A clip in a folder you did not
            grant is not counted, so the figure can be smaller than the number of videos you own.
        """, ui=True),

        topic("settings-optimise-mode", "Mode: Automatic or Manual", """
            ## What it is
            A box under **Optimise photos** (and another under **Optimise video**) showing
            **Automatic** or **Manual**.

            • **Automatic.** Photos are optimised on their own when you open the Albums or Settings tab, with no button to press. For folders you gave access to during setup this happens without a prompt. For anything outside them, Android asks you to confirm each batch. Video is optimised on its own in the background, while the phone is charging.
            • **Manual.** Nothing happens until you press the **Optimise** button shown further down. The list of what is ready to optimise stays up to date, so you can choose your own moment.

            ## Good to know
            Manual is not the same as switching optimising off. Automatic is the starting mode when you
            switch the feature on.
        """, ui=True),

        topic("settings-video-age", "Older than (video)", """
            ## What it is
            A box that sets how old a video must be before it can be optimised: **Straight away**, **1
            hour**, **12 hours**, **1 day** or **1 week**.

            ## What it covers
            Only the copy on this phone. A video is backed up to OneDrive straight away whatever this is
            set to. Age is worked out for each file separately, from when it was last modified.

            ## Good to know about "Straight away"
            It reaches clips shot today. They stay in your gallery and still play, but editing one starts
            from the smaller copy until you fetch the original back with Restore. When you choose it, a
            warning saying so appears under the setting.

            See [[settings-optimise-video]] for when it happens.
        """, ui=True),

        topic("settings-video-quality", "Quality (video)", """
            ## What it is
            A box that sets how much a video is shrunk. The label carries the outcome, so none of the
            three is a bare adjective:

            • **High, 480p.** The smallest files. Saves about 85 percent.
            • **Medium, 720p.** Saves about 75 percent.
            • **Low, 1080p.** The least shrinking. Saves about 50 percent.

            ## Where the figures come from
            They were measured on real clips and are typical, not promised. They depend on the content.
            The word "High" means the most shrinking, not the highest picture quality.

            ## Good to know
            Optimising re-encodes the clip at a lower resolution. Nothing is cut, and it stays in your
            gallery. The full-quality original stays in OneDrive.
        """, ui=True),

        topic("settings-optimise-status", "The optimise status and button", """
            ## What it is
            Shown under the switches once optimising is on. On Android 11 or newer:

            • **12 photos can be optimised.** A smaller copy stays on the phone and the full-size original stays in OneDrive. Followed by a button, **Optimise 480 MB of photos**, which does it now.
            • **Nothing to optimise yet.** A photo can only be optimised once it is verified in OneDrive, so set an album to Sync and run a sync first.
            • **Every eligible photo is already optimised.**

            During and after a run:
            • **Optimising...** while it works.
            • **Optimised 12 photos and freed 480 MB.** when done.
            • **Stopped after 5 photos. One could not be replaced (reason). Nothing after it was changed.** if something went wrong.
            • **Android wouldn't show the permission prompt, so no photos were changed.** Try again.

            On older Android: **Optimising photos needs Android 11 or newer.**

            Video has its own status and button, described in [[settings-optimise-video-status]]. Each
            kind only appears while its own switch is on.

            ## Where the numbers come from
            The app's own record of which photos are in Sync albums, are verified in OneDrive, and have
            not been optimised yet. The size is what those photos take on your phone now.

            ## Good to know
            Unlike Archive, optimising replaces the photo in place instead of moving it to a bin, so the
            space it frees is not held back waiting for a bin to be emptied.
        """, ui=True),

        topic("settings-section-restore", "Restore (Settings)", """
            ## What it is
            One switch that relates to the Restore tab: **Show empty folders**.
        """, ui=True),

        topic("settings-show-empty-folders", "Show empty folders", """
            ## What it is
            A switch. Off is the starting choice. The wording says: on lists every folder in your backup
            folders, including ones with nothing in them; off lists only folders that have something to
            bring back.

            ## Where things stand in this version
            The Restore tab currently lists only folders that have something to bring back, so this
            switch does not change what you see there yet. Leaving it off is right.
        """, ui=True),

        topic("settings-section-archive", "Archive (Settings)", """
            ## What it is
            A band that reads **Coming soon**. Archive has no settings of its own on this page: it is
            controlled album by album on the Albums tab, and carried out on the Archive tab.

            See [[archive-overview]].
        """, ui=True),

        topic("settings-about-cards", "The three link cards at the bottom", """
            Below the sections are three cards. Tap one to open its page inside the app.

            • **Privacy Policy.** How Gallery Sync handles your photos, videos and account.
            • **Delete Account Info.** How to sign out, remove the app's access to your Microsoft account, and clear its data.
            • **Contact Info.** Questions or problems? It shows the address, which you can select and copy with **Copy address**. It does not open a mail app.

            The How To Guide card is not down here: it is the first card in General. See
            [[settings-how-to-guide-card]].

            The Privacy Policy and Delete Account Info pages are shown from the internet, so they need
            a connection. If a page cannot be loaded you will see **This page could not be
            loaded**, with **Open in browser** to try it there. Close returns you to Settings.
        """),
    ],
)
