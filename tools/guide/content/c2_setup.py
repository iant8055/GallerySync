from model import Chapter, topic

CHAPTER = Chapter(
    id="setup",
    title="First-time setup",
    intro="The first time you open GallerySync, a short guided setup walks you through nine screens. "
          "Each one is explained here, including the messages you might meet along the way.",
    topics=[
        topic("setup-overview", "How the setup works", """
            Setup is a series of cards. **Next** moves on and **Back** returns to the previous card. You
            can only move on when a card has what it needs: for example, at least one folder must be
            ticked, and you must be signed in before the cloud card lets you continue.

            ## What you will be asked, in order
            1. A welcome card.
            2. A short tour of the four tabs.
            3. A summary of the five things being set up.
            4. Permission to look for photos and videos on your phone, then which folders to look after.
            5. Signing in to your Microsoft account, and where in OneDrive backups go.
            6. Your backup plan: one of four choices about the photos and videos already on the phone.
            7. Only for two of the plans: how much to optimise.
            8. When the first backup should start.
            9. The first backup itself, with progress.

            ## Good to know
            The first backup is the big one, because it sends everything that is not already in OneDrive.
            It can take hours. If you leave the app while it runs, it carries on in the background, and
            when you come back setup picks up at the progress card. If you chose a plan that backs up,
            the setup cards stay in front of the app until the first backup has finished and been checked.

            Setup answers apply to the first run only. See [[setup-and-settings]].
        """),

        topic("setup-welcome-tour", "Welcome, the tour, and what will be set up", """
            **The welcome card.** A picture that says "Welcome to GallerySync". Tap anywhere to continue.

            **The tour.** Five short cards, one for each of the four tabs (Albums, Restore, Archive and
            Settings) and one for Help. Each points at its tab and says in a sentence what it is for. The
            Help card explains that a **(?)** button gives a quick explanation of the line beside it,
            and rings the **How To Guide** card at the top of Settings, which explains everything in
            one place.

            **What we'll set up.** A checklist of the five things ahead:
            • Give permission to search this phone for photos and videos.
            • Choose which of the folders it finds you want backed up.
            • Give permission to change files in those folders.
            • Grant access to your cloud storage.
            • Select where backups go in OneDrive.

            The checklist is only a preview; nothing is changed by reading it.

            **The link to this explanation.** The **What we'll set up** card ends with **For a more
            detailed breakdown of the set up process click here**. It opens this explanation on its own,
            as a page you can read at any point during setup. It is the only part of the guide you can
            open until setup is finished.
        """),

        topic("setup-search-permission", "Let GallerySync search this phone", """
            **Give search permission** opens Android's own permission screen. GallerySync needs it to
            find out where your photos and videos are stored.

            ## Why it asks first, on its own card
            Android's permission wording cannot be changed by any app, so the card explains why before
            you see it.

            ## Good to know
            On Android 14 and later you may be offered "Select photos" instead of "Allow all". If you
            share only some photos, GallerySync can only see those, so a backup would be incomplete.
            The Albums tab warns you when this is the case. To sync whole albums, allow access to all
            photos.
        """),

        topic("setup-choose-folders", "Choose folders to back up", """
            Once it has permission, GallerySync searches and shows the folders it found, each with a
            tick box. **Tick the ones you want looked after.** DCIM is where most phones keep the camera
            roll, so it is what most people choose. Press **Next** to move on.

            ## Then Android asks again, once per folder
            For each ticked folder, Android shows its own folder picker so you can give GallerySync
            lasting access to that folder. This second permission is what allows the app to swap a
            photo for a smaller copy, or bring an original back, without asking you every time. It is
            limited to the folders you chose, and you can withdraw it in Android's settings.

            ## If the picker does not give the folder asked for
            A card appears above the list saying what happened and what it means for the backup:
            • **No access was given.** Nothing in that folder can be backed up or optimised. Choose **Try again** or **Skip** that folder, which leaves it out of the backup.
            • **You chose a folder inside it** (for example DCIM/Camera instead of DCIM). Only that inner folder would be backed up and the rest of DCIM left out. Choose **Choose all** to try again, or **Keep** the narrower choice.
            • **You chose a different folder.** It is not used, because GallerySync needs access to the ticked folder itself. Try again or skip.
            • **The choice cannot be used.** Same options.

            Nothing is uploaded, scanned or changed in a folder you did not choose.
        """),

        topic("setup-cloud", "Cloud storage: signing in and choosing where backups go", """
            **Sign in with Microsoft** opens Microsoft's own sign-in page. You type your password there,
            never into GallerySync. The app only receives a token that lets it work with your OneDrive
            files, and it keeps that token in encrypted storage on your phone.

            Once signed in, the card says **Signed in to OneDrive** and shows **Backup destination**: the
            OneDrive folder where new backups will go. By default this is Samsung Gallery / DCIM, which
            matches where Samsung's own sync puts photos, so files already there are recognised and not
            sent twice. **Change** lets you pick another folder.{{full: See [[dialog-destination]].}}

            ## What it checks in the background
            As soon as you are signed in, the app looks at what OneDrive already holds, so the next
            cards can tell you how much is left to send.
        """),

        topic("setup-backup-plan", "Choose your backup plan", """
            One choice about the photos and videos **already on your phone**. None of the four removes
            anything from your phone.

            • **1. Check cloud storage and back up everything that isn't already backed up.** Sends whatever OneDrive does not already have. Nothing on your phone changes and no space is freed.
            • **2. Everything in 1, plus optimise all files on the phone.** Also replaces photos and videos with smaller copies, for the most space saved. The originals stay in OneDrive.
            • **3. Everything in 1, but only optimise newly backed-up files.** Smaller copies only for files this first backup sends, for a moderate saving. It shows how many files are not backed up yet, and on a phone already synced to OneDrive that may be a small share of the library.
            • **4. Check cloud storage but do not back up any new files.** No upload and no space saved. You choose which albums to back up or sync yourself, on the Albums tab.

            ## Good to know
            **Plan 1 is selected when you arrive**, so pressing Next without choosing means backing up
            everything that is not already in OneDrive. Change it before you press Next if that is not
            what you want.

            Plans 2 and 3 use one-time optimising during setup. Plan 4 uploads nothing, so setup ends
            early and no first backup runs.

            None of the four plans sets an album's mode. After the first backup your albums still show
            **Off**, and that is correct: only you set modes, on the Albums tab.{{full: See [[faq-albums-off-after-backup]].}}
        """),

        topic("setup-optimise", "Optimisation settings during setup", """
            You only see this card if you chose plan 2 or plan 3. It asks how much to shrink.

            • **Optimise photos** and **Optimise video** are on/off switches.
            • **Video optimisation level** appears when video is on: **High** (480p, the smallest), **Medium** (720p) or **Low** (1080p, the least shrinking).
            • Under each switch the card estimates the saving, and a **Total estimated savings** line adds them up.

            ## Where the estimates come from
            The app has just asked OneDrive what it holds and has counted the photos and videos in your
            chosen folders. It multiplies the sizes by the typical saving for each setting. They are
            estimates, and real results vary with what is in your files.

            ## Messages you may see instead of a figure
            • **Checking OneDrive to work out what this would save.** Still counting.
            • **OneDrive could not be checked just now.** No estimate yet. The switches still apply to whatever the first backup sends.
            • **Could not check some of your albums.** The saving is a floor: the real figure is likely to be larger.
            • **No photos or videos were found in the folders you chose.** Nothing to optimise.
        """),

        topic("setup-ready", "Ready to back up", """
            Shows **how much you have selected to back up** and, if optimising is on, **how much space
            you will save on the phone**. Then it asks **Do you want to perform backup now?**

            • **Right now** starts straight away.
            • **Start in 1 hour** (or the delay you pick) starts later. Seven delay buttons appear: **3m, 1h, 2h, 4h, 8h, 12h and 24h**.

            ## Two notes on the card
            • A delayed backup may start a little after the set time. Android batches background work for apps that have not been opened recently.
            • For the security of your data, the app is unavailable until a backup has been successfully completed and verified.

            A delayed backup also needs the phone to be plugged in to start on its own.
        """),

        topic("setup-progress", "Backup progress", """
            The last card follows the first backup from start to finish.

            ## While it waits
            **Waiting to start** shows a countdown ("until backup starts"). The backup will start on its
            own, even if you close the app, as long as the phone is plugged in. **SYNC NOW** starts it
            immediately whatever the battery level.

            ## While it runs
            • **Scanning your library.** Getting ready.
            • **Starting upload.** The total is known and the first file is on its way.
            • **Uploading 5 of 155.** A ring and a count show progress.

            ## When uploading ends
            The card then says the upload finished and was verified, and moves through
            **Optimising photos** and **Optimising video** with a count for each, when your plan
            includes them. Photos stay in your gallery and stay usable; nothing is cut from your videos.

            ## Finishing
            When everything is done the card says the backup finished successfully and has been verified,
            and the button changes to **Finish**. While it is still running the button says **Close**,
            which closes the app while the backup carries on.

            ## Cancel
            **Cancel** (in place of Back) asks **Stop the backup?**. Stopping keeps everything already in
            OneDrive, resumes later where it left off without sending files twice, and deletes nothing.
            Photos already optimised stay optimised, with their originals in OneDrive.
        """),

        topic("setup-and-settings", "Setup and Settings are separate", """
            What you answer during first-time setup and what you see in the **Settings** tab are two
            separate things. Neither reads or changes the other.

            • Setup collects its own answers and acts once, on files already on the phone.
            • A setup answer may leave nothing behind in Settings, and Settings may show something different from what a setup card showed. That is normal.
            • Setup never sets an album's mode. Only you set modes, on the Albums tab.

            So if a card in setup looks different from the matching row in Settings, nothing is wrong.
        """),
    ],
)
