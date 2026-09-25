from model import Chapter, topic

CHAPTER = Chapter(
    id="setup",
    title="First-time setup",
    intro="The first time you open GallerySync, a short guided setup walks you through nine screens. "
          "Each one is explained here, including the messages you might meet along the way.",
    topics=[
        topic("setup-overview", "How the setup works", """
            Setup is a series of sreens. **Next** moves on and **Back** returns to the previous screen. You
            can only move on when a screen has what it needs: for example, at least one folder must be
            selected to backup and you must signed into your Cloud Account before the screen lets you continue.

            ## What you will be asked, in order
            1. A welcome screen, a short tour, and a summary of what's ahead. See [[setup-welcome-tour]].
            2. Which Cloud services you use, and signing in to each. See [[setup-cloud]].
            3. Permission to look for photos and videos on your phone, then which folders to back up and which Cloud each one goes to. See [[setup-choose-folders]].
            4. Your backup plan: one of four choices about the photos and videos already on the phone.
            5. Only for two of the plans: how much to optimise.
            6. When the first backup should start.
            7. The first backup itself, with progress.

            ## Good to know
            The first backup is the big one, because it sends everything that is not already in the Cloud.
            It could take hours. But you can monitor its progress.  If you leave the app while the backup is running, it will carry on in the background. 
            When you come back you can see the progress screen. If will always come to that Progress Screen until the first backup has finished and been checked.

            Setup answers apply to the first run only. See [[setup-and-settings]].
        """),

        topic("setup-welcome-tour", "Welcome, the tour, and what will be set up", """
            **The Welcome screen.** A picture that says "Welcome to GallerySync". Tap anywhere to continue, once it has been on screen a moment — a tap in the first instant, such as the one that opened the app, does not count, so the screen cannot vanish before it has actually been seen.

            **The tour.** Five short screens, one for each of the four tabs (Albums, Restore, Archive and
            Settings) and one for Help. Each points at its tab and says in a sentence what it is for. The
            Help screen explains that a **(?)** button gives a quick explanation of the line beside it,
            and rings the **How To Guide** screen at the top of Settings, which explains everything in
            one place.

            **What we'll set up.** A checklist of the four things ahead:
            • Choose your Cloud storage and sign in to it.
            • Give permission to search this phone for photos and videos.
            • Choose which of the folders it finds you want backed up, and where each one goes.
            • Give permission to change files in those folders.


            **The link to this explanation.** The **What we'll set up** screen ends with **For a more
            detailed breakdown of the set up process click here**. It opens this explanation on its own,
            as a page you can read at any point during setup. It is the only part of the guide you can
            open until setup is finished.
        """),

        topic("setup-cloud", "Cloud storage: choosing services, signing in and choosing where backups go", """
            This screen comes first, before your folders, so the next screens know which Clouds you can send to. It is one list of the Cloud services GallerySync works with: **OneDrive**, **Google Photos**, **Google Drive**, **Dropbox**, **pCloud**, **IDrive e2** and **Backblaze B2**. **Nothing is selected when you arrive.** Select the ones you use and press **Next**. A service only appears once the app is set up to connect to it.

            ## Your first Cloud is free
            The first Cloud you select is the free one. Each further Cloud is part of **Pro**: free for 30 days, then a one-time purchase, not a subscription. You start the trial yourself with a button, the terms are shown first, and nothing is ever charged automatically. If the trial ends and you do not buy it, uploads to the extra Clouds pause and your free Cloud carries on as normal.

            ## What each Cloud can do
            Selecting a Cloud can show an **About this Cloud** note listing what it cannot do.
            • **OneDrive, Google Drive, Dropbox, IDrive e2 and Backblaze B2** can Backup, Sync, Archive and Restore.
            • **Google Photos and pCloud** are **backup only** for now. Files sent there are not optimised, archived or restored by GallerySync, because the app cannot ask them, right then, whether a copy is safe.
            • **Outside OneDrive, the app cannot see what a Cloud already holds**, so a photo that is already there may be added a second time.

            ## Signing in, one Cloud at a time
            After **Next**, each Cloud you selected gets its own **Connect** card, headed **Cloud 1 of 3** and so on. Each one is finished before the next begins. **Back** returns to the previous card; there is no skip, so **Next** waits until every Cloud you selected is connected.
            • **OneDrive, Google Photos, Google Drive, Dropbox and pCloud** open the provider's own sign-in page. You type your password there, never into GallerySync. The app only receives a permission that lets it work with your files, and keeps it in encrypted storage on your phone. Dropbox asks for permission to read as well as write: allow both, or Restore will not work.
            • **IDrive e2 and Backblaze B2** ask for the endpoint, region, bucket and the access key pair you made in their console. The app checks them against the bucket before keeping them. See [[cloud-key-backblaze]] and [[cloud-key-idrive]].

            Once OneDrive is signed in, its card says **Signed in to OneDrive** and shows **Backup destination**: the OneDrive folder where new backups will go. The default is Samsung Gallery / DCIM, which matches where Samsung's own sync puts photos, so files already there are recognised and not sent twice. **Change** lets you pick another folder.{{full: See [[dialog-destination]].}}

            ## What it checks in the background
            If OneDrive is one of your Clouds, then once you have chosen your folders the app looks at what OneDrive already holds, so the later screens can tell you how much is left to send. The other Clouds are not checked this way.
        """),

        topic("cloud-key-backblaze", "Connecting Backblaze B2: the keys and where to find them", """
            **Backblaze B2** connects with a key you make in your own Backblaze account, not with a sign-in page. GallerySync only ever writes inside a folder called **GallerySync** in the bucket you choose. It never lists, changes or deletes anything else in the bucket, so an existing bucket is safe to use.

            ## What you need from Backblaze
            1. **A bucket.** Use one you already have, or make one under **B2 Cloud Storage, Buckets, Create a Bucket**. Keep it **Private**.
            2. **The bucket's endpoint.** Open the bucket in the list. Its details show an **Endpoint** such as **s3.us-east-005.backblazeb2.com**.
            3. **An application key for that bucket.** Under **Application Keys**, choose **Add a New Application Key**. Name it (for example **gallerysync**), choose your one bucket rather than **All**, and set the type of access to **Read and Write**. Do not use the Master Application Key: it can do everything in your account.
            4. **Both halves of the key.** Backblaze shows a **keyID** and an **applicationKey**. The applicationKey is shown once, so copy it before you leave the page. If you lose it, make a new key.

            ## What to enter in GallerySync
            • **Endpoint:** the address from the bucket, without https://.
            • **Region:** leave it blank. GallerySync reads it from the endpoint (the **us-east-005** part).
            • **Bucket name:** the bucket's exact name.
            • **Access key ID:** the **keyID**. Use all of it: it is long and starts with 005.
            • **Secret access key:** the **applicationKey**. Tap **Show** to check it before you press **Connect**.

            ## If it says the keys were rejected
            The message names what to check. In practice it is nearly always a typed character.
            • **Does not recognise that access key ID:** part of the keyID is missing or wrong.
            • **The secret key or the region does not match:** a letter in the applicationKey is wrong. Watch for a capital **O** against a zero, a capital **I** against a lowercase **l**, and a capital **J** against a lowercase **j**. Pasting the key avoids all of these.
            • **Not allowed to use that bucket:** the key was made for a different bucket.

            The key is kept in encrypted storage on your phone and is used only to send your files to this bucket. If Backblaze B2 is not your first Cloud, it needs Pro, free for 30 days.
        """),

        topic("cloud-key-idrive", "Connecting IDrive e2: the keys and where to find them", """
            **IDrive e2** connects with a key you make in your own e2 account, not with a sign-in page. GallerySync only ever writes inside a folder called **GallerySync** in the bucket you choose. It never lists, changes or deletes anything else in the bucket, so an existing bucket is safe to use.

            ## What you need from IDrive e2
            1. **A bucket.** Use one you already have, or create one in the e2 console. Keep it private.
            2. **The endpoint for that bucket's region.** This is the address of the region itself, for example **s3.us-west-1.idrivee2.com**. The console may also show an address for the bucket with the bucket's name in front. Do not use that one: GallerySync adds the bucket name itself.
            3. **The region name.** The console names the region of the bucket, for example **us-west-1**. Unlike Backblaze, IDrive's endpoint does not contain it, so GallerySync asks for it.
            4. **An access key.** Create one in the console's access keys section. It gives an **access key ID** and a **secret access key**. The secret is normally shown once, so copy it before you leave the page.

            IDrive changes its console from time to time, so the labels may differ a little from these. The four things above are what matter.

            ## What to enter in GallerySync
            • **Endpoint:** the address from the console, without https://.
            • **Region:** the region name from the console.
            • **Bucket name:** the bucket's exact name.
            • **Access key ID:** the access key ID.
            • **Secret access key:** the secret. Tap **Show** to check it before you press **Connect**.

            ## If it says the keys were rejected
            The message names what to check. In practice it is nearly always a typed character.
            • **Does not recognise that access key ID:** part of the ID is missing or wrong.
            • **The secret key or the region does not match:** a letter in the secret is wrong, or the region name is. Watch for a capital **O** against a zero, and a capital **I** against a lowercase **l**. Pasting the key avoids these.
            • **Not allowed to use that bucket:** the key does not cover that bucket.

            The key is kept in encrypted storage on your phone and is used only to send your files to this bucket. If IDrive e2 is not your first Cloud, it needs Pro, free for 30 days.
        """),

        topic("setup-search-permission", "Let GallerySync search this phone", """
            **Give search permission** opens Android's own permission screen. GallerySync needs it to
            find out where your photos and videos are stored.

            ## Why it asks first, on its own screen
            Android's permission wording cannot be changed by any app, so the screen explains why before
            you see it.

            ## Good to know
            On Android 14 and later you may be offered "Select photos" instead of "Allow all". If you
            share only some photos, GallerySync can only see those, so a backup would be incomplete.
            The Albums tab warns you when this is the case. To sync whole albums, allow access to all
            photos.
        """),

        topic("setup-choose-folders", "Choose folders to back up", """
            Once it has permission, GallerySync searches and shows the folders it found, each with a selection box. **Select the ones you want GallerySync to access.** DCIM is where most modern Android phones save the photos and videos. Press **Next** to move on.

            ## Which Cloud each folder goes to
            If more than one Cloud is connected, each folder you select shows a **Goes to** button, so you can send DCIM to OneDrive and Pictures to Google Photos, for example. It starts **blank**, so you have to choose: **Next** waits until every folder you selected has a Cloud, and the choice is highlighted once you have made it. This is chosen per folder (DCIM, Pictures, Movies), never per album inside it. If you connected only one Cloud you will not see the button, and everything goes there. Files already backed up never move if you change this later, only new files follow the change. You can change it any time in Settings.{{full: See [[settings-destination]].}}

            ## Then Android asks again, once per folder
            For each selected folder, Android shows its own folder permissions screen to double check this is what you want.  It is
            limited to the folders you chose, and you can withdraw it in Android's settings or GalleySync's Settings.

            ## If permission isn't given to the folder asked for
            A screen appears above the list saying what happened and what it means for the backup:
            • **No access was given.** Nothing in that folder can be backed up or optimised. Choose **Try again** or **Skip** that folder, which leaves it out of the backup.
            • **You chose a folder inside it** (for example DCIM/Camera instead of DCIM). Only that inner folder would be backed up and the rest of DCIM left out. Choose **Choose all** to try again, or **Keep** the narrower choice.
            • **You chose a different folder.** It is not used, because GallerySync needs access to the selected folder itself. Try again or skip.
            • **The choice cannot be used.** Same options.

            **Nothing is uploaded, scanned or changed in a folder you did not choose.**
        """),

        topic("setup-backup-plan", "Choose your backup plan", """
            You have four basic initial options about how to backup the photos and videos already on your phone. **None of the four options removes
            anything from your phone.**

            • **1. Check Cloud storage and back up everything that isn't already backed up.** Sends whatever isn't currently on your Cloud. Nothing on your phone changes and no space is freed.
            • **2. Everything in 1, plus optimise all files on the phone.** Also replaces photos and videos with optimized copies, for the most space saved. The originals stay in Cloud.
            • **3. Everything in 1, but only optimise newly backed-up files.** Optimized copies only for files this first backup sends, for a moderate saving. It shows how many files are not backed up yet, and on a phone already synced to Cloud that may be a small share of the library.
            • **4. Check Cloud storage but do not back up any new files.** No upload and no space saved. You choose individually which albums to back up or sync yourself, on the Albums tab.

            ## Good to know
            **Plan 1 is selected when you arrive**, so pressing Next without choosing means backing up
            everything that is not already in Cloud. Change it before you press Next if that is not
            what you want.

            Plans 2 and 3 use one-time optimisation during setup. Plan 4 uploads nothing, so setup ends and the app opens.

            None of the four plans sets an album's mode. After the first backup your albums still show
            **Off**, and that is correct: only you set modes, on the Albums tab.{{full: See [[faq-albums-off-after-backup]].}}
        """),

        topic("setup-optimise", "Optimisation settings during setup", """
            You only see this screen if you chose plan 2 or plan 3. It asks how much to shrink.

            • **Optimise photos** and **Optimise video** are on/off switches.
            • **Video optimisation level** appears when video is on: **High** (480p, the smallest), **Medium** (720p) or **Low** (1080p, the least shrinking).
            • Under each switch you are presented with estimated space saving for each option, and a **Total estimated savings** line adds them up.

            ## Where the estimates come from
            The app has just asked Cloud what it holds and has counted the photos and videos in your
            chosen folders. It multiplies the sizes by the typical saving for each setting. They are
            estimates, and real results may vary with what is in your files.
        """),


        topic("setup-ready", "Ready to back up", """
            Shows **how much you have selected to back up** and, if optimising is on, **how much space
            you will save on the phone**. Then it asks **Do you want to perform backup now?**

            • **Right now** starts straight away.
            • **Start in 1 hour** (or the delay you pick) starts later. Seven delay buttons appear: **3m, 1h, 2h, 4h, 8h, 12h and 24h**.

            ## Two notes on the screen
            • A delayed backup may start a little after the set time. Android batches background work for apps that have not been opened recently.
            • For the security of your data, the app is unavailable until a backup has been successfully completed and verified.

            **A delayed backup also needs the phone to be plugged in to start on its own.**
        """),

        topic("setup-progress", "Backup progress", """
            The last screen follows the first backup from start to finish.

            ## While it waits
            **Waiting to start** shows a countdown ("until backup starts"). The backup will start on its
            own, even if you close the app, as long as the phone is plugged in. **SYNC NOW** starts it
            immediately whatever the battery level.

            ## While it runs
            • **Scanning your library.** Getting ready.
            • **Starting upload.** The total is known and the first file is on its way.
            • **Uploading, with a percentage in the ring.** The ring and the large percentage show the whole backup, across every Cloud you use.
            • **Under the percentage** it names the Cloud being sent to right now (for example **Uploading Google Photos**) and, on the line below, how many are sent of how many there are for that Cloud.
            • **Below the ring**, each other Cloud that still has files waiting is listed as **Pending** with the same kind of count. When one Cloud finishes, the next one moves into the ring.
            • Some Clouds take longer than others. Google Photos, for example, only accepts about 27 uploads a minute, so a large library can take hours; the backup carries on in the background if you close the app.

            ## When uploading ends
            The screen then says the upload finished and was verified, and moves through
            **Optimising photos** and **Optimising video** with a count for each, when your plan
            includes them. Photos stay in your gallery and stay usable; nothing is cut from your videos.

            ## Finishing
            When everything is done the screen says the backup finished successfully and has been verified,
            and the button changes to **Finish**. While it is still running the button says **Close**,
            which closes the app while the backup carries on.

            ## Cancel
            **Cancel** (in place of Back) asks **Stop the backup?**. Stopping keeps everything already in the
            Cloud, resumes later where it left off without sending files twice, and deletes nothing.
            Photos already optimised stay optimised, with their originals in Cloud.
        """),

        topic("setup-and-settings", "Setup and Settings are separate", """
            What you answer during first-time setup and what you see in the **Settings** tab are two
            separate things. Neither reads or changes the other.

            • Setup collects its own answers and acts once, on files already on the phone.
            • A setup answer may leave nothing behind in Settings, and Settings may show something different from what a setup screen showed. That is normal.
            • Setup never sets an album's mode. Only you set modes, on the Albums tab.

            So if a screen in setup looks different from the matching row in Settings, nothing is wrong.
        """),
    ],
)
