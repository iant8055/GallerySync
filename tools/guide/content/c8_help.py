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
            This line only appears on albums whose files go to OneDrive. The app has not asked OneDrive about that album yet. It asks when you open the Albums tab or
            press **Rescan**, so give it a moment, and check you are signed in and connected. If it says
            **Could not reach OneDrive when this was last checked**, the connection failed, so try
            **Rescan** again.

            An album whose files go to another cloud says something like **15 sent to Dropbox** instead. See [[albums-list]].
        """),

        topic("faq-onedrive-full", "The backup stopped because my cloud is full", """
            The status line says **stopped: your cloud is full**. Nothing is lost: files already sent are
            safe and the rest are waiting. Free some room in that cloud, or add storage with its provider,
            then press **Sync now**.
        """),

        topic("faq-space-not-freed", "I archived an album but my phone has no more space", """
            Archived files are in your phone's Trash or Recycle Bin, and they take their full space until
            the bin is emptied. Open the bin in your gallery app (or the Files app) and empty it. Do that
            once you are happy the files are safe in your cloud; the app never empties it for you.
            See [[where-deleted-files-go]].
        """),

        topic("faq-album-vanished", "An album has disappeared from my gallery", """
            If it was set to **Archive**, that is what Archive does: its files were moved to your phone's
            bin once your cloud was confirmed to hold them. They are not gone. Open the bin to put them
            back, or use the **Restore** tab to download them again from your cloud.

            The folder itself is not deleted, but the album has left the Albums tab and its mode has been
            forgotten, so nothing will archive it again. Open the **Restore** tab to bring it back: it
            returns as a new album, starting at Off.
        """),

        topic("faq-photo-looks-small", "A photo looks smaller or lower quality than before", """
            It has probably been optimised: replaced by a smaller copy, marked with a small cloud
            badge. The full-quality original is safe in your cloud. Open the **Restore** tab, find the
            folder, and restore it to put the original back in place.
        """),

        topic("faq-restore-empty", "The Restore tab says there is nothing to bring back", """
            Restore only lists things GallerySync has changed on this phone: photos and videos it has
            replaced with smaller copies, and files it backed up that are no longer in their folder. If
            you have not optimised or archived anything, the list is empty, and that is right. For
            anything else in your cloud, open that cloud's own app or website. If you use OneDrive, the tab also lists the photos and videos in your OneDrive backup folders that this phone does not have.
        """),

        topic("faq-backup-stops-when-closed", "Backups seem to stop when I close the app", """
            Leaving with the back gesture or the Home button is fine: backing up carries on. Swiping
            the app away from the recent-apps list is different, because Android can then stop the app's
            background jobs until you next open it. If you swiped it away, open the app again and the
            backup picks up where it left off, without sending anything twice.
        """),

        topic("faq-wrong-account", "I signed in to a cloud with the wrong account", """
            Go to **Settings**, **Backup**, and press **Sign out** beside that cloud, then sign in with the right account. See [[settings-account]]. It may ask what to do with files there: **Leave them there** is the usual answer.
            Files already sent to the first account stay there, and the folders paired with that cloud stay paired, so new files carry on to the right account once you sign in. If you want to withdraw the app's access
            from the first account, follow the Delete Account Info page.
        """),

        topic("faq-folder-waiting", "I signed out of a cloud and its folders stopped sending", """
            That is what signing out means. Signing out never moves a folder to another cloud: the folder stays paired with the cloud you signed out of, and its new files wait, safe on your phone, until you sign in to that cloud again. Nothing is lost and nothing is sent anywhere else.

            To send a folder to a different cloud instead, change it under **Where each folder goes** in Settings. See [[settings-destination]].
        """),

        topic("faq-keys-rejected", "IDrive e2 or Backblaze B2 says my keys were rejected", """
            The message names what to check, and it is nearly always a mistyped character. Check the endpoint (the address of the region, without https://), the region, the bucket name and the two keys. Tap **Show** on the secret and read it against the console. Watch for a capital **O** against a zero, a capital **I** against a lowercase **l**, and a capital **J** against a lowercase **j**. Pasting the keys avoids all of these. See [[cloud-key-backblaze]] and [[cloud-key-idrive]].
        """),

        topic("faq-could-not-check", "Archive says it could not check my cloud", """
            The app could not get an answer from the cloud, so it removed nothing: being unable to ask is not the same as an answer. Check you are online and still signed in to that cloud in Settings, then press **Check these files** again. A file stays on your phone until its cloud confirms it.
        """),

        topic("faq-reconnect-cloud", "Restore says to reconnect that cloud", """
            The cloud refused the request to read the file. Sign in to it again in Settings. For Dropbox, make sure the app's permissions include reading files as well as writing them. For IDrive e2 and Backblaze B2, make sure the key you made allows reading the bucket. The file on your phone was left exactly as it was.
        """),

        topic("faq-only-some-photos", "The app says only some photos are shared", """
            Android gave GallerySync access to only the photos you picked, so the albums are incomplete.
            Press **Grant access** on the Albums tab and choose **Allow all**. If Android does not show
            the prompt, allow Photos and videos for GallerySync in your phone's Settings under Apps.
        """),

        topic("contact-and-privacy", "Contact, privacy and deleting your data", """
            All three are cards at the bottom of the Settings tab.

            • **Contact Info** shows the address to write to, with a button that copies it.
            • **Privacy Policy** says what the app does with your data. In short, it sends your files to your own cloud accounts and nowhere else, and the developer collects nothing.
            • **Delete Account Info** explains how to sign out, remove the app's access from your cloud accounts, and clear its data from your phone. Your files in your cloud stay where they are.
        """),
    ],
)
