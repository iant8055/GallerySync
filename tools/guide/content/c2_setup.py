from model import Chapter, topic

CHAPTER = Chapter(
    id="setup",
    title="First-time setup",
    intro="A guided setup runs the first time you open GallerySync.",
    topics=[
        topic("setup-overview", "How the setup works", """
            **Next** moves on, **Back** goes back. A screen lets you continue only when it has what it needs.

            ## The steps
            1. Welcome, a short tour, and what will be set up. See [[setup-welcome-tour]].
            2. Choose your Clouds and sign in. See [[setup-cloud]].
            3. Allow access to your photos, then choose folders. See [[setup-choose-folders]].
            4. Choose a Backup Plan.
            5. Optimisation settings (plans 2 and 3 only).
            6. When to start.
            7. The first backup: check the Cloud, upload, then optimise if your plan includes it.

            ## Good to know
            • The first backup can take hours. It carries on if you leave the app.
            • The app opens on the progress screen until the first backup is finished.
            • Setup answers apply to the first backup only. See [[setup-and-settings]].
        """),

        topic("setup-welcome-tour", "Welcome, the tour, and what will be set up", """
            • **Welcome:** tap anywhere to continue.
            • **The tour:** five screens, one for each tab and one for Help.
            • **What we'll set up:** sign in to your Cloud, allow photo access, choose folders, and allow changes to those folders.

            The link at the bottom of that screen opens this setup guide. It is the only part of the guide available during setup.
        """),

        topic("setup-cloud", "Cloud storage: choosing services, signing in and choosing where backups go", """
            Select the Clouds you use and press **Next**. Nothing is selected to start with.

            ## Your first Cloud is free
            The first Cloud you select becomes your free Cloud, shown as **main (free)** in Settings. Each further Cloud needs **Pro**: free for 30 days, then a one-time purchase. Nothing is charged automatically. If the trial ends, extra Clouds pause and your free Cloud carries on.

            Start the trial with **Start 30-day free trial** in Settings, under Backup; it appears once a second Cloud is connected. **Unlock Pro** buys it.

            ## What each Cloud can do
            • **OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 and Backblaze B2:** Backup, Sync, Archive and Restore.
            • **Google Photos:** backup only.

            ## A note about duplicates
            OneDrive is the only Cloud whose contents are checked before anything is sent. Other Clouds may receive a second copy of a file that is already there.

            ## Signing in
            Each Cloud gets its own numbered **Connect** card. **Next** waits until all are connected.
            • **OneDrive, Google Photos, Google Drive, Dropbox, pCloud:** sign in on the provider's own page. Your password never goes into GallerySync. For Dropbox, allow read and write, or Restore will not work.
            • **IDrive e2, Backblaze B2:** enter the keys from their console. See [[cloud-key-backblaze]] and [[cloud-key-idrive]].

            ## Backup folder
            Once a Cloud is connected, its card shows its **Backup folder**, with **Change** to pick another (not Google Photos, which has no folders). OneDrive starts at **Samsung Gallery / DCIM**, the folder Samsung's own OneDrive sync uses, so photos already synced there are not sent twice. Other Clouds start at **GallerySync**.{{full: See [[dialog-destination]].}}
        """),

        topic("cloud-key-backblaze", "Connecting Backblaze B2: the keys and where to find them", """
            GallerySync only writes inside its backup folder in your bucket (**GallerySync** unless you choose another), so an existing bucket is safe to use.

            ## In Backblaze
            1. **A private bucket:** use one you have, or **Buckets → Create a Bucket**.
            2. **Its endpoint:** shown in the bucket's details, for example **s3.us-east-005.backblazeb2.com**.
            3. **An application key:** **Application Keys → Add a New Application Key**, for that one bucket, **Read and Write**. Not the Master key.
            4. **Copy both halves:** the **keyID** and the **applicationKey**. The applicationKey is shown only once.

            ## In GallerySync
            • **Endpoint:** without https://.
            • **Region:** leave blank.
            • **Bucket name:** exactly as in Backblaze.
            • **Access key ID:** the keyID (starts with 005).
            • **Secret access key:** the applicationKey. Tap **Show** to check it.

            ## If the keys are rejected
            The message says what to check. It is nearly always a typo: **O** vs zero, **I** vs **l**, **J** vs **j**. Pasting avoids this.
        """),

        topic("cloud-key-idrive", "Connecting IDrive e2: the keys and where to find them", """
            GallerySync only writes inside its backup folder in your bucket (**GallerySync** unless you choose another), so an existing bucket is safe to use.

            ## In IDrive e2
            1. **A private bucket.**
            2. **The region's endpoint**, for example **s3.us-west-1.idrivee2.com**. Not the address with the bucket name in front.
            3. **The region name**, for example **us-west-1**.
            4. **An access key:** an **access key ID** and **secret access key**. The secret is shown only once.

            ## In GallerySync
            Enter the **Endpoint** (without https://), **Region**, **Bucket name**, **Access key ID** and **Secret access key**. Tap **Show** to check the secret.

            ## If the keys are rejected
            The message says what to check. It is nearly always a typo: **O** vs zero, **I** vs **l**. Pasting avoids this.
        """),

        topic("setup-search-permission", "Let GallerySync search this phone", """
            **Give search permission** opens Android's permission screen. This is where you let GallerySync see the photos and videos on your phone.

            ## Good to know
            Choose **Allow all**. If you choose **Select photos**, only those photos can be backed up.
        """),

        topic("setup-choose-folders", "Choose folders to back up", """
            Select the folders to back up and press **Next**. Most photos are in **DCIM**.

            ## Which Cloud each folder goes to
            With more than one Cloud, each folder shows **Goes to**. Choose a Cloud for every selected folder. Files already sent never move; only new files follow a change.{{full: See [[settings-destination]].}}

            ## Android asks again for each folder
            Android shows its own folder permission screen. If it goes wrong:
            • **No access given:** **Try again** or **Skip** the folder.
            • **A folder inside it was chosen:** **Choose all** to fix, or **Keep** the smaller choice.
            • **A different folder was chosen:** try again or skip.

            Nothing outside your chosen folders is scanned or changed.
        """),

        topic("setup-backup-plan", "Choose your Backup Plan", """
            What to do with the photos and videos already on your phone. **No plan removes anything.**

            • **1. Back up everything not already in the Cloud.** Nothing on the phone changes. Selected when you arrive.
            • **2. Back up, then optimise every file on the phone.** The most space saved.
            • **3. Back up, then optimise only the files this backup sent.**
            • **4. Check the Cloud but send nothing.** You choose each album's mode afterwards on the Albums tab.

            ## Good to know
            This first backup is one-shot and runs regardless of mode. Afterwards every album is **Off**, which is correct: from here on you choose each album's mode yourself on the Albums tab.{{full: See [[faq-albums-off-after-backup]].}}
        """),

        topic("setup-optimise", "Optimisation settings during setup", """
            Only for plans 2 and 3.

            • **Optimise photos** and **Optimise video:** on or off.
            • **Video compression level:** **High** (480p, smallest), **Medium** (720p) or **Low** (1080p).
            • Each shows an estimated saving, with a **Total estimated savings** line. Real results vary.
        """),

        topic("setup-ready", "Ready to Back Up", """
            Shows how much will be backed up and, if optimising, how much space you will save.

            • **Right now:** starts straight away.
            • **Delay:** **1h, 2h, 4h, 8h, 12h** or **24h**.

            ## Good to know
            • A delayed backup starts only while the phone is plugged in, and may start a little late.
            • The app stays on the progress screen until the first backup is finished.
        """),

        topic("setup-progress", "Backup progress", """
            ## Waiting to start
            A countdown runs until the backup starts, even with the app closed, as long as the phone is plugged in. **SYNC NOW** starts it straight away.

            ## Step 1: Checking your Cloud
            Every file is compared with what your Cloud already holds, by name and size, with a running count. Nothing is sent. Usually a minute or two.

            ## Step 2: Uploading
            The ring shows overall progress. Below it: the Cloud being sent to and its count. Other Clouds waiting show as **Pending**.

            ## Step 3: Optimising
            **Optimising photos**, then **Optimising video**, if your plan includes them.

            ## Buttons
            • **Close:** closes the app; the backup carries on.
            • **Finish:** appears when everything is done, with a summary of what was checked, already in the Cloud, and sent.
            • **Cancel:** asks **Stop the backup?** Files already sent stay sent; nothing is deleted.
        """),

        topic("setup-and-settings", "Setup and Settings are separate", """
            Setup and Settings keep their own answers. Setup never reads from Settings, and Settings never reads from setup. Setup's folder list starts empty even if Settings already lists folders, and setup's answers do not appear in Settings.

            Setup never sets an album's mode. Only you do, on the Albums tab.
        """),
    ],
)
