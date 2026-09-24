from model import Chapter, topic

CHAPTER = Chapter(
    id="settings",
    title="The Settings tab",
    intro="The things you set once and rarely change. The tab is a single scrolling page divided into "
          "green bands: General, Backup, Albums, Sync, Restore and Archive. The How To Guide is the "
          "first card in General, the policy pages are near the bottom, and Language is the very last "
          "line. Each band has a (?) beside its name, and a thin line separates one setting from the "
          "next.",
    topics=[
        topic("settings-overview", "How the Settings tab is laid out", """
            Each green band is a section heading, with a **(?)** that explains the whole section. Under
            it are that section's settings, each set off from the next by a thin line. Switches turn
            something on or off; boxes with the current value open a short list; buttons open a page or
            a dialog.

            ## Two things that are true of every setting
            • Changing one takes effect straight away. There is no Save button.
            • Nothing on this page removes a file from your phone. Removing files only happens through the Archive mode.

            ## Settings and first-time setup are separate
            Setup answered its own questions once. Settings does not read them back. See
            [[setup-and-settings]].
        """),

        topic("settings-section-general", "General", """
            ## What it is
            Settings that apply across the whole app: the **How To Guide** card, **Appearance** and
            **Use mobile data**. **Language** is not here: it is the last line on the page.

            ## Where it comes from
            These are stored on your phone and change how the app looks and when it uses your data plan.
        """, ui=True),

        topic("settings-how-to-guide-card", "The How To Guide card", """
            ## What it is
            The first card in **General**. It reads **How To Guide**, with a line
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
            A line reading **Language** with **Multi-Language Support Coming Soon** under it, at the very
            bottom of the Settings tab, below the policy pages. The app is available in English only at
            the moment, and there is nothing to choose yet.
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
            Where your photos and videos are backed up to. For now that is OneDrive: its name with a box
            to the left of it, then the connected **Account** with its **Sign out** button, and
            **Current folder location** with a **Change** button.

            ## At least one backup location
            The box beside a location is there for switching it on or off, but the last one left on
            cannot be switched off: a backup that goes nowhere protects nothing. OneDrive is the only
            location there is, so its box stays ticked and greyed out, and you will see **At least one
            backup location must stay on.**

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
            • **A note.** New photos and videos will go here. The files already backed up stay where they are, and GallerySync keeps looking in the old folder too, so nothing is uploaded twice. It shows the number of files already backed up when it knows it.
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
            The mode a brand new album starts with, which folders to watch, and what happens to cloud
            copies when you delete from this phone, in that order and with a thin line between them.
        """, ui=True),

        topic("settings-folders", "Folders to back up", """
            ## What it is
            The folders GallerySync is allowed to look in, each shown as a path such as **Internal storage
            / DCIM**, with a box beside it. **Add a folder** opens Android's folder picker. To take
            folders off the list, tick the box beside each one and press **Remove**, which stays greyed
            out until something is ticked.

            ## What it means
            GallerySync only looks in the folders you choose. Anything outside them is not scanned,
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
            it again restores what you had set for its albums. **Remove** asks you to confirm first.
            See [[dialog-remove-folder]].
        """, ui=True),

        topic("dialog-remove-folder", "Stop watching this folder?", """
            ## What it is
            A confirmation that appears when you tick one or more folders and press **Remove** under
            **Folders to back up**. **Remove** confirms; **Cancel** leaves the list exactly as it was.

            ## Why it asks
            A folder can hold several albums, so one tap on a ticked box removes more than it looks
            like. This catches a stray tap before it takes effect.

            ## What actually happens
            GallerySync stops looking in the folder and hands back its permission. Nothing already
            backed up is lost: no ledger row and no album mode is deleted. Adding the same folder back
            brings everything back with its history intact, and nothing is re-uploaded.
        """, ui=True),

        topic("settings-deletion", "When you delete a photo or video from this phone", """
            ## What it is
            Two choices, one selected at a time:

            • **Leave the OneDrive copy** (the starting choice). Nothing is ever removed from OneDrive. Deleting on your phone only frees space here.
            • **Ask me about the OneDrive copy.** When you open the app after files have gone from your phone, a window lists them, whether or not the app ever backed them up, and asks what you want done: delete or keep their OneDrive copies, or back up the ones that never made it. It never removes anything on its own. See [[deleted-files-window]].

            ## Why Leave is the default
            A copy left in OneDrive costs a little storage. A copy removed by mistake could cost you the
            photo, because the one on your phone is already gone.

            ## Good to know
            The app never treats a missing file as an instruction to delete. Under **Ask** it asks you
            first, and nothing in OneDrive moves without your say-so.
        """, ui=True),

        topic("deleted-files-window", "Files deleted from your phone (the window that opens with the app)", """
            ## What it is
            A full-screen window that opens **before the Albums tab**, when files have left your phone
            since it last appeared and you have chosen **Ask** in Settings. It covers **every** file
            deleted from your phone, **whether or not GallerySync ever backed it up**. The green card at
            the top says **Files deleted from phone** and, in the right half, how many there are. It
            comes in up to two windows, one after the other, and each appears only if it has files.

            ## Window 1: a backup is in OneDrive
            These files were deleted from your phone but a copy is in OneDrive, **whether GallerySync put
            it there or not**. The question is what to do with the OneDrive copies.

            **Nothing is ticked to start with.** Swipe a card right (or tap it) to tick it, and left to untick it; a ticked card turns red and says
            **Will be removed from OneDrive**.
            • **Remove N from OneDrive** asks you to confirm first. See [[dialog-remove-from-onedrive]]. The ticked files' OneDrive copies go to the OneDrive recycle bin, and **the files you left unticked stay in OneDrive for good.**
            • **Keep all in OneDrive** leaves every copy alone, whatever is ticked.
            • **Decide later** moves on to the next window without deciding anything.

            ## Window 2: no backup can be found
            These files were deleted from your phone and **no copy could be found in OneDrive**. They are
            still in your phone's **trash** (Recycle Bin on some phones), where they stay for about a
            month. The question is what to do with the files themselves.

            **Nothing is ticked to start with.** Swipe a card right (or tap it) to tick it, and left to untick it; a ticked card says **Will be backed
            up to OneDrive**.
            • **Back up N to OneDrive** sends the ticked files to OneDrive from the trash. It only adds a copy: **nothing is deleted, and the files stay in the trash.** You can watch it count as it goes. **The files you left unticked stay in the trash.**
            • **Leave all in the trash** does nothing to any of them.
            • **Decide later** closes the window without deciding anything.

            A file whose bytes are no longer in the trash (you emptied it) can't be backed up. It is
            reported and dropped from the list.

            ## What is listed
            The older files as well as the newest: **a file you have not decided about stays on the list
            until you decide.** GallerySync looks for a file's copy in the OneDrive folder for its album,
            by **name and size**.

            Not listed: a file **Archive** took off the phone on purpose (its OneDrive copy is the one you
            asked to keep); a photo you **edited and saved over**, because a file of the same name is still
            in the same folder; a file you decided about, **until it comes back to the phone and is
            deleted again**; and a file that was deleted outright rather than to the trash and has no
            OneDrive copy, because there is nothing left to do about it.

            ## When it appears
            Only under **Ask**, only after first-time setup is finished, and **only when there are new
            files**: something has left the phone since the window was last shown. Opening the app with
            nothing new shows nothing. There is no waiting period. If a very large share of your library
            seems to have gone at once, it is treated as a scan going wrong rather than as deletions and
            nothing is offered. If OneDrive can't be reached, the files it could not check are left out
            and offered again next time.

            ## When it has finished
            It says what happened: how many OneDrive copies were moved to the recycle bin, how many were
            kept, how many files were backed up, how many were left in the trash, how many turned out to be
            back on your phone, and how many could not be done. **A file that could not be done is not lost
            and is not decided, so it stays on the list.**

            The back button does the same as **Decide later**. It never removes anything.

            ## Where it comes from
            The app's own record of the files it has seen on your phone, compared with what is on your
            phone when you open it. It looks when the app comes to the front, and at most once a minute.
        """, ui=True),

        topic("dialog-remove-from-onedrive", "Remove files from OneDrive? (the confirmation)", """
            ## What it is
            A confirmation with the number and size: **Remove 12 files from OneDrive?** It says the
            files are not on your phone any more.

            They go to the **OneDrive recycle bin**, where you can restore them yourself. GallerySync
            never empties that bin.

            **Remove from OneDrive** goes ahead. **Cancel** closes the confirmation and decides nothing:
            your ticks stay as they were.

            ## Where it comes from
            The number and size are the files you ticked in the window behind it. Just before anything is
            removed the app looks at your phone again, and any file that has come back, or whose name is
            back in its folder, is left alone.
        """, ui=True),

        topic("settings-section-sync", "Sync", """
            ## What it is
            How photos and videos are optimised to save space. Files are only changed after they are
            verified in OneDrive, and only inside albums you set to Sync.
        """, ui=True),

        topic("settings-optimise-photos", "Optimise photos", """
            ## What it is
            A switch. Off is the starting choice. On says **Photos in Sync albums are optimised for
            storage.** A **Mode** row appears under it, and it decides when this happens. See
            [[settings-optimise-mode]].

            ## What optimising a photo does
            The photo on your phone is replaced with a smaller copy, reduced to about 2048 pixels on the
            long edge and roughly a tenth of the size. It stays in your gallery and opens normally in every
            app, and carries a small cloud badge so you can tell which ones are optimised. The full-quality
            original stays in OneDrive, untouched. Photos are optimised whatever their age.

            ## Only in Sync albums
            Backup and Archive albums never optimise, and Off does nothing. A photo is only ever
            optimised after OneDrive has confirmed it holds the original at the same size, and a
            photo that would not get smaller is left alone. A photo you have set to **Keep at full
            size** is never touched.

            See [[how-optimising-works]].
        """, ui=True),

        topic("settings-optimise-video", "Optimise video", """
            ## What it is
            A switch. Off is the starting choice. Turning it on reveals **Mode**, **Older than** and
            **Quality**.

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
            • It is in a folder you gave GallerySync access to. A clip anywhere else is left alone.
            • It has not been optimised already, and has not been found already small enough to leave.

            ## When it happens
            **Automatic** starts by itself, and only while the phone is charging, because re-encoding
            video is the heaviest thing the app does. It starts:
            • When a backup run finishes everything. That happens after every new video, when you switch an album to Sync, and on the six-hourly check, which is also how a clip that has just grown older than your chosen age is noticed.
            • When you change Optimise video, Mode or Older than.

            **Manual** waits until you press **Sync now** on the Albums tab. It then starts straight
            away, without waiting for the charger. See [[albums-run-controls]].

            It works in the background, a few clips at a time, and you can close the app. Switching
            Optimise video off stops it after the clip in progress.

            See [[how-optimising-works]].
        """, ui=True),

        topic("settings-optimise-mode", "Mode: Automatic or Manual", """
            ## What it is
            A box under **Optimise photos** (and another under **Optimise video**) showing
            **Automatic** or **Manual**. It decides when optimising happens.

            • **Automatic.** As soon as a file reaches an album set to **Sync**, or an album is switched to Sync. The file is sent to OneDrive first and optimised the moment OneDrive has confirmed it. Photos are optimised in the background, with no prompt, for folders you gave access to during setup. Video waits for the phone to be charging.
            • **Manual.** Nothing happens until you press **Sync now** on the Albums tab. That sends whatever is waiting and then optimises. **Sync now** can be pressed even when nothing is left to send, if something is ready to optimise. See [[albums-run-controls]].

            ## Photos outside the folders you gave access to
            The app cannot change these on its own. Android asks you to confirm them, so they wait until
            you open the app (Automatic) or press **Sync now** (Manual), and the prompt then appears.

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

        topic("settings-section-restore", "Restore (Settings)", """
            ## What it is
            One switch that relates to the Restore tab: **Show empty folders**.
        """, ui=True),

        topic("settings-show-empty-folders", "Show empty folders", """
            ## What it is
            A switch. Off is the starting choice. The wording says: on lists every folder in your backup
            folders, including ones with nothing in them; off lists only folders that have something to
            bring back.

            ## What it changes
            The Restore tab lists the folders in your OneDrive backup folders that have something to
            bring back. Turn this on and it lists every folder, including those where every file is
            already on the phone. Off is right for most people.
        """, ui=True),

        topic("settings-section-archive", "Archive (Settings)", """
            ## What it is
            Two settings, added 22 Sept 2026:
            • **Only show files older than**, which sets where the Archive tab's own age filter starts each time you open it. See [[settings-archive-default-age]].
            • **Notify when files are ready to archive**, off by default. See [[settings-archive-notify]].

            Everything else about Archive is still controlled album by album on the Albums tab, and carried out on the Archive tab. See [[archive-overview]].
        """, ui=True),

        topic("settings-archive-default-age", "Only show files older than (Settings)", """
            ## What it is
            Where the Archive tab's **Only show files older than** filter starts each time you open the
            tab fresh: 1 hour, 1 day, 1 week, 1 month, 1 year, or **All**. See [[archive-age-filter]] for
            what the filter itself does.

            ## Good to know
            This is only a starting point. Changing the filter on the Archive tab itself, once you are
            there, lasts for that visit and does not change this default.

            The default is **All** out of the box, so nothing is held back until you choose otherwise —
            the same as before this setting existed.
        """, ui=True),

        topic("settings-archive-notify", "Notify when files are ready to archive", """
            ## What it is
            A switch, off by default. On, GallerySync asks Android for permission to post
            notifications, then lets you know when files in an Archive album have been confirmed in
            OneDrive and are ready to leave your phone.

            ## Why it is not the only way to find out
            The Albums tab already shows this, and a reminder appears if you try to leave the app with
            files waiting. Those need no permission and cannot be silently switched off. A notification
            can — Android may refuse it, or you can turn it off later in your phone's own Settings,
            outside this app entirely — so it is offered as an extra, never as the only way you would
            know.

            ## Good to know
            It tells you once per new batch of files becoming ready, not once per file and not again for
            a batch that is still sitting there unchanged. If your phone has already told Android not to
            notify for GallerySync, the switch says so underneath it, and turning it off and back on here
            asks again.
        """, ui=True),

        topic("settings-about-cards", "The three link cards at the bottom", """
            Below the sections are three cards. Tap one to open its page inside the app.

            • **Privacy Policy.** How GallerySync handles your photos, videos and account.
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
