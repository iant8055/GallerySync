from model import Chapter, topic

CHAPTER = Chapter(
    id="help",
    title="Questions and problems",
    intro="Common questions and what is usually going on.",
    topics=[
        topic("faq-sync-now-greyed", "Sync now is greyed out", """
            There is nothing to send or optimise: everything is already sent, or no album is set to Backup, Sync or Archive. Check the mode totals in the header.
        """),

        topic("faq-albums-off-after-backup", "My albums say Off after the first backup", """
            That is correct. The first backup sends everything regardless of mode, and setup never sets modes. Choose a mode for each album on the Albums tab.
        """),

        topic("faq-not-checked-yet", "An album says Not checked against OneDrive yet", """
            OneDrive has not been asked yet. Wait a moment or press **Rescan**. **Could not reach OneDrive** means the connection failed: check you are online and signed in, then **Rescan**.

            Albums on other Clouds show **sent to** that Cloud instead. See [[albums-list]].
        """),

        topic("faq-onedrive-full", "The backup stopped because my Cloud is full", """
            **stopped: your Cloud is full.** Nothing is lost. Free some room or add storage, then press **Sync now**.
        """),

        topic("faq-space-not-freed", "I archived an album but my phone has no more space", """
            Archived files sit in the phone's trash, still taking space. Empty the bin once you are happy they are safe in your Cloud. See [[where-deleted-files-go]].
        """),

        topic("faq-album-vanished", "An album has disappeared from my gallery", """
            If it was set to **Archive**, its files are in the phone's trash. Restore them from the bin, or use the **Restore** tab. It comes back as a new album set to Off.
        """),

        topic("faq-photo-looks-small", "A photo looks smaller or lower quality than before", """
            It has been optimised (look for the small cloud badge). The original is in your Cloud: use the **Restore** tab.
        """),

        topic("faq-restore-empty", "The Restore tab says there is nothing to bring back", """
            Nothing has been optimised or archived, and (for OneDrive) every photo in your backup folders is already on the phone. That is correct.
        """),

        topic("faq-backup-stops-when-closed", "Backups seem to stop when I close the app", """
            Back or Home is fine. Swiping the app away from recent apps can stop background work: open the app again and it carries on without sending anything twice.
        """),

        topic("faq-wrong-account", "I signed in to a Cloud with the wrong account", """
            **Settings → Backup → Sign out** beside that Cloud, choose **Leave them there**, then sign in with the right account. Files already sent stay in the first account. See [[settings-account]].
        """),

        topic("faq-folder-waiting", "I signed out of a Cloud and its folders stopped sending", """
            That is expected. The folders wait, safe on your phone, until you sign in again. To send them elsewhere, change **Where each folder goes**. See [[settings-destination]].
        """),

        topic("faq-keys-rejected", "IDrive e2 or Backblaze B2 says my keys were rejected", """
            Usually a typo. Check the endpoint (without https://), region, bucket and both keys; tap **Show** on the secret. Watch **O** vs zero, **I** vs **l**, **J** vs **j**. Pasting avoids this. See [[cloud-key-backblaze]] and [[cloud-key-idrive]].
        """),

        topic("faq-could-not-check", "Archive says it could not check my Cloud", """
            The Cloud did not answer, so nothing was removed. Check you are online and signed in, then press **Check these files** again.
        """),

        topic("faq-reconnect-cloud", "Restore says to reconnect that Cloud", """
            The Cloud refused to let the file be read. Sign in again in Settings. For Dropbox, allow reading files; for IDrive e2 and Backblaze B2, use a key that can read the bucket. Your phone's file was not changed.
        """),

        topic("faq-only-some-photos", "The app says only some photos are shared", """
            Press **Grant access** on the Albums tab and choose **Allow all**, or allow Photos and videos for GallerySync in your phone's Settings under Apps.
        """),

        topic("contact-and-privacy", "Contact, privacy and deleting your data", """
            At the bottom of Settings:
            • **Contact Info:** the address, with a copy button.
            • **Privacy Policy:** your files go only to your own Cloud; nothing is collected.
            • **Delete Account Info:** how to sign out, remove access and clear data. Your Cloud files stay.

            To report a problem, use **Bug Report** just above them.
        """),
    ],
)
