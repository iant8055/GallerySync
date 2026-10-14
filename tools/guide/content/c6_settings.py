from model import Chapter, topic

CHAPTER = Chapter(
    id="settings",
    title="The Settings tab",
    intro="One scrolling page in green bands: General, Backup, Albums, Sync, Camera Sync, Restore, Archive and "
          "Help & Feedback. Each band has a (?).",
    topics=[
        topic("settings-overview", "How the Settings tab is laid out", """
            • Changes take effect straight away. There is no Save button.
            • Nothing here removes a file from your phone. Only Archive does that.
            • Settings and first-time setup are separate. See [[setup-and-settings]].
        """),

        topic("settings-section-general", "General", """
            The **How To Guide**, **Appearance** and **Use mobile data**. **Language** is the last line on the page.
        """, ui=True),

        topic("settings-how-to-guide-card", "The How To Guide card", """
            Opens this guide. **Read more in the How To Guide** in any **(?)** opens it at that topic. Needs an internet connection; if it will not load, use **Open in browser**.
        """),

        topic("settings-language", "Language", """
            The language you chose on the Welcome screen. Tap it to change. The How To Guide follows this choice; the app's screens are in English for now.
        """, ui=True),

        topic("settings-appearance", "Appearance", """
            • **System** (default): follows your phone's light or dark setting.
            • **Light** or **Dark**: always that look.

            The guide and policy pages follow your phone's setting.
        """, ui=True),

        topic("settings-mobile-data", "Use mobile data", """
            • **Off** (default): **Wi-Fi only.**
            • **On:** **Syncs on Wi-Fi and mobile data.**

            Covers automatic backups and **Sync now**. Leave it off unless your data plan is unlimited.
        """, ui=True),

        topic("settings-section-backup", "Backup", """
            • **Clouds:** connect or sign out of each Cloud. See [[settings-account]].
            • **Where each folder goes:** which Cloud each folder sends to. See [[settings-destination]].

            One Cloud is marked **main (free)**: the first Cloud you selected. It is always free. Other Clouds need **Pro**: free for 30 days (**Start 30-day free trial**), then a one-time purchase (**Unlock Pro**). Nothing is charged automatically.
        """, ui=True),

        topic("settings-account", "Your Clouds and Sign out", """
            Each Cloud shows its account (or **Not connected**) with **Sign out**, **Connect** or **Enter keys**.

            ## Backup folder
            Under each connected Cloud (except Google Photos): the folder its backups go to, with **Change**. See [[dialog-destination]].

            ## Sign out
            A warning first: backups to that Cloud stop, nothing is deleted. If that Cloud holds files that are not full size on the phone (optimised or archived), it asks:
            • **Restore them to the phone first:** brings them all back, then signs out.
            • **Leave them there:** signs out.
            • **Cancel:** stays signed in.

            Folders stay paired with that Cloud and wait until you sign in again. To send them elsewhere, change **Where each folder goes**.
        """, ui=True),

        topic("settings-destination", "Where each folder goes", """
            Each top-level folder (DCIM, Pictures, Movies), the Cloud it goes to, and where in that Cloud.

            • Tap the Cloud to change it. It can only be changed with more than one Cloud and Pro or the trial.
            • Only new files follow a change. Files already sent stay where they are.
            • A folder paired with an extra Cloud without Pro sends nothing, and says so.
        """, ui=True),

        topic("dialog-destination", "Where new backups go (the Change dialog)", """
            Opened by **Change** beside a Cloud's **Backup folder**, in Settings or on its card during setup.

            • Type the folder path, or for OneDrive use **Browse...**.
            • **Reset to default:** back to **Samsung Gallery/DCIM** (OneDrive) or **GallerySync** (other Clouds).
            • **Use this folder** saves; **Cancel** changes nothing.

            Only new files go to the new folder. Files already backed up stay where they are. OneDrive keeps checking earlier folders, so nothing is sent twice.
        """, ui=True),

        topic("dialog-folder-picker", "Choose a folder (the OneDrive picker)", """
            Opened by **Browse...** for OneDrive. Lists your OneDrive folders: tap to open, **Up one folder** to go back, **Use this folder** to choose, **New folder** to create one.
        """, ui=True),

        topic("settings-section-albums", "Albums", """
            Which folders GallerySync watches, and what happens to Cloud copies when you delete from the phone. New albums always start at **Off**.
        """, ui=True),

        topic("settings-folders", "Folders to back up", """
            The folders GallerySync may look in.

            • **Add a folder:** opens Android's folder picker.
            • **Remove:** select folders, press **Remove**, and confirm.

            Nothing outside these folders is scanned or changed. Removing a folder loses nothing: add it back and everything returns.
        """, ui=True),

        topic("dialog-remove-folder", "Stop watching this folder?", """
            **Remove** stops watching the selected folders; **Cancel** changes nothing.

            Nothing already backed up is lost. Adding the folder back restores its albums and history, with nothing sent twice.
        """, ui=True),

        topic("settings-deletion", "When you delete a photo or video from this phone", """
            • **Leave the Cloud copy** (default): nothing is ever removed from your Cloud.
            • **Ask me about the Cloud copy:** when files leave your phone, a window asks what to do with their Cloud copies. If notifications are allowed, you are also told shortly after deleting. Nothing is removed without your say-so. See [[deleted-files-window]].

            OneDrive only for now. Copies in other Clouds are never removed.
        """, ui=True),

        topic("deleted-files-window", "Files deleted from your phone (the window that opens with the app)", """
            Opens before the Albums tab when files have left your phone, under **Ask**. Nothing is selected to start with.

            ## Window 1: a copy is in OneDrive
            Swipe right (or tap) to select files whose OneDrive copy should go.
            • **Remove N from OneDrive:** asks you to confirm, then moves those copies to the OneDrive recycle bin. Unselected files stay in OneDrive. See [[dialog-remove-from-onedrive]].
            • **Keep all in OneDrive.**
            • **Decide later.**

            ## Window 2: no copy found
            These files are still in your phone's trash.
            • **Back up N to OneDrive:** sends the selected files from the trash. Nothing is deleted.
            • **Leave all in the trash.**
            • **Decide later.**

            ## Good to know
            • Undecided files stay on the list until you decide.
            • Not listed: files Archive removed, photos edited in place, and files deleted outright with no Cloud copy.
            • If more than half of your backed-up files seem to vanish at once, nothing is offered; it is treated as a faulty scan rather than real deletions.
            • Back does the same as **Decide later**.
        """, ui=True),

        topic("dialog-remove-from-onedrive", "Remove files from OneDrive? (the confirmation)", """
            Shows how many files and their size. They go to the OneDrive recycle bin, which GallerySync never empties.

            **Remove from OneDrive** goes ahead; **Cancel** decides nothing. Any file that has come back to the phone is left alone.
        """, ui=True),

        topic("settings-section-sync", "Sync", """
            How photos and videos in **Sync** albums are optimised. Files change only after the Cloud has verified them. Works with OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 and Backblaze B2.
        """, ui=True),

        topic("settings-optimise-photos", "Optimise photos", """
            **Off** by default. When on, photos in Sync albums are replaced with smaller copies: about 2048 pixels on the long edge, roughly a tenth of the size. They stay in your gallery with a small cloud badge. Originals stay in the Cloud.

            Photos already small enough, and files set to **Keep at full size**, are left alone. **Mode** sets when it runs. See [[settings-optimise-mode]].
        """, ui=True),

        topic("settings-optimise-video", "Optimise video", """
            **Off** by default. When on, shows **Mode**, **Older than** and **Compression**.

            Videos in Sync albums are re-encoded smaller. Nothing is ever cut. Originals stay in the Cloud.

            ## Which videos
            In a Sync album, verified in the Cloud, older than your setting, in a folder you gave access to, and not already optimised.

            ## When
            • **Automatic:** after backups, only while charging.
            • **Manual:** when you press **Sync now**.
        """, ui=True),

        topic("settings-optimise-mode", "Mode: Automatic or Manual", """
            • **Automatic** (default): files in Sync albums are optimised once the Cloud confirms them. Video waits for the charger.
            • **Manual:** nothing happens until you press **Sync now** on the Albums tab.

            Photos outside the folders you gave access to need Android's confirmation, so they wait until you open the app or press **Sync now**.
        """, ui=True),

        topic("settings-video-age", "Older than (video)", """
            How old a video must be before it is optimised: **Straight away**, **1 hour**, **12 hours**, **1 day** or **1 week**. Backup is not delayed.

            **Straight away** includes clips shot today; editing one starts from the smaller copy until you restore it.
        """, ui=True),

        topic("settings-video-quality", "Compression (video)", """
            • **High compression, 480p:** smallest files, about 85% saved.
            • **Medium compression, 720p:** about 75% saved.
            • **Low compression, 1080p:** about 50% saved.

            Typical figures; results vary.

            ## After you make it stronger
            The app asks **Re-optimise existing videos?** **Yes** lets videos that were too small to shrink before be tried again. **No** leaves them. Already optimised videos are not touched.
        """, ui=True),

        topic("settings-section-restore", "Restore (Settings)", """
            One switch for the Restore tab: **Show empty folders**.
        """, ui=True),

        topic("settings-show-empty-folders", "Show empty folders", """
            **Off** by default. When on, the Restore tab lists every OneDrive folder, including ones with nothing to bring back.
        """, ui=True),

        topic("settings-section-camera", "Camera Sync (Settings)", """
            **Special settings for Camera**, off by default. When on:
            • **Camera folder:** the folder your camera saves to. Choose it from the list, or **Other…**.
            • **Select Photos/Videos older than**, **Photos**, **Videos:** defaults for that folder's optimise controls ([[album-camera-optimise]]).
            • **Video compression:** for videos optimised there.

            The Camera folder has no **Sync** mode, and nothing is optimised until you press **Sync now** in it. The Optimise switches above do not affect it.
        """, ui=True),

        topic("settings-section-archive", "Archive (Settings)", """
            • **Only show files older than:** where the Archive tab's filter starts. See [[settings-archive-default-age]].
            • **Notify when files are ready to archive:** off by default. See [[settings-archive-notify]].
        """, ui=True),

        topic("settings-archive-default-age", "Only show files older than (Settings)", """
            Where the Archive tab's age filter starts: **1 hour**, **1 day**, **1 week**, **1 month**, **1 year** or **All** (default). Changing it on the Archive tab lasts for that visit only. See [[archive-age-filter]].
        """, ui=True),

        topic("settings-archive-notify", "Notify when files are ready to archive", """
            **Off** by default. When on, you are notified once per new batch of files ready to archive. OneDrive files only.

            The Albums tab and the leaving reminder show this too, so you are never relying on notifications alone. If notifications are blocked, the switch says so.
        """, ui=True),

        topic("settings-about-cards", "Help & Feedback, and the link cards at the bottom", """
            ## Help & Feedback
            • **Rate us on Play Store:** opens the GallerySync listing.
            • **Bug Report:** where to write, and **Copy details** for your app version, phone model and Android version.

            ## At the bottom
            • **Privacy Policy.**
            • **Delete Account Info:** how to sign out, remove the app's access and clear its data.
            • **Contact Info:** the address, with **Copy address**.

            Pages load from the internet. If one will not load, use **Open in browser**.
        """),
    ],
)
