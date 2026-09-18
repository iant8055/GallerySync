from model import Chapter, topic

CHAPTER = Chapter(
    id="help",
    title="Questions and problems",
    intro="Things people ask, and what is usually going on.",
    topics=[
        topic("faq-sync-now-greyed", "Sync now is greyed out", """
            It is greyed out when there is nothing to send. That means either every file in your Backup,
            Sync and Archive albums is already sent, or no album is set to any of those modes yet.

            Check the mode counts in the green card. If they are all under **Off**, choose a mode for
            the albums you want looked after. If a backup is already running, the left side of the card
            shows its progress instead of a button.
        """),

        topic("faq-albums-off-after-backup", "My albums say Off after the first backup", """
            This is correct. The first backup sends everything in the folders you chose, whatever each
            album's mode is. Setup never sets an album's mode, so afterwards albums still read **Off**.

            From then on, only albums you switch to Backup, Sync or Archive are looked after. Choose a
            mode for each album on the Albums tab.
        """),

        topic("faq-not-checked-yet", "An album says Not checked against OneDrive yet", """
            The app has not asked OneDrive about that album yet. It asks when you open the Albums tab or
            press **Rescan**, so give it a moment, and check you are signed in and connected. If it says
            **Could not reach OneDrive when this was last checked**, the connection failed, so try
            **Rescan** again.
        """),

        topic("faq-onedrive-full", "The backup stopped because OneDrive is full", """
            The status line says **stopped: OneDrive is full**. Nothing is lost: files already sent are
            safe and the rest are waiting. Free some room in OneDrive, or add storage with Microsoft,
            then press **Sync now**.
        """),

        topic("faq-space-not-freed", "I archived an album but my phone has no more space", """
            Archived files are in your phone's Trash or Recycle Bin, and they take their full space until
            the bin is emptied. Open the bin in your gallery app (or the Files app) and empty it. Do that
            once you are happy the files are safe in OneDrive; the app never empties it for you.
            See [[where-deleted-files-go]].
        """),

        topic("faq-album-vanished", "An album has disappeared from my gallery", """
            If it was set to **Archive**, that is what Archive does: its files were moved to your phone's
            bin once OneDrive was confirmed to hold them. They are not gone. Open the bin to put them
            back, or use the **Restore** tab to download them again from OneDrive.

            The folder itself is not deleted, and the album's mode still stands. If you do not want the
            album archived any more, change its mode on the Albums tab.
        """),

        topic("faq-photo-looks-small", "A photo looks smaller or lower quality than before", """
            It has probably been optimised: replaced by a smaller copy, marked with a small cloud
            badge. The full-quality original is safe in OneDrive. Open the **Restore** tab, find the
            folder, and restore it to put the original back in place.
        """),

        topic("faq-restore-empty", "The Restore tab says there is nothing to bring back", """
            Restore only lists things Gallery Sync has changed on this phone: photos and videos it has
            replaced with smaller copies, and files it backed up that are no longer in their folder. If
            you have not optimised or archived anything, the list is empty, and that is right. For
            anything else in your OneDrive, open the OneDrive app.
        """),

        topic("faq-backup-stops-when-closed", "Backups seem to stop when I close the app", """
            Leaving with the back gesture or the Home button is fine: backing up carries on. Swiping
            the app away from the recent-apps list is different, because Android can then stop the app's
            background jobs until you next open it. If you swiped it away, open the app again and the
            backup picks up where it left off, without sending anything twice.
        """),

        topic("faq-wrong-account", "I signed in with the wrong Microsoft account", """
            Go to **Settings**, **Backup**, and press **Sign out**, then sign in with the right account.
            Files already sent to the first account stay there. If you want to withdraw the app's access
            from that account, follow the Delete Account Info page.
        """),

        topic("faq-only-some-photos", "The app says only some photos are shared", """
            Android gave Gallery Sync access to only the photos you picked, so the albums are incomplete.
            Press **Grant access** on the Albums tab and choose **Allow all**. If Android does not show
            the prompt, allow Photos and videos for Gallery Sync in your phone's Settings under Apps.
        """),

        topic("contact-and-privacy", "Contact, privacy and deleting your data", """
            All three are cards at the bottom of the Settings tab.

            • **Contact Info** shows the address to write to, with a button that copies it.
            • **Privacy Policy** says what the app does with your data. In short, it sends your files to your own OneDrive and nowhere else, and the developer collects nothing.
            • **Delete Account Info** explains how to sign out, remove the app's access from your Microsoft account, and clear its data from your phone. Your files in OneDrive stay where they are.
        """),
    ],
)
