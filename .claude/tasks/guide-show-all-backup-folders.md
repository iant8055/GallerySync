# Guide handoff: what Restore lists (Ian, 24-25 Nov 2026)

**There is no switch.** *Show all backup folders* was built on 24 Nov and removed on 25 Nov before it shipped; no topic for it.

The rule now, for the Restore topics (check restore-overview and dialog-destination, and anything else that says Restore looks in `Samsung Gallery/DCIM` or in every earlier folder):

- **Restore lists the OneDrive backup folder set in Settings** (Settings → Backup → Clouds → Backup folder), and only that one, for files GallerySync did not send.
- **Files this copy of GallerySync backed up or optimized are always offered**, wherever they are in OneDrive, including a folder it used before the backup folder was changed. That keeps Restore able to put an original back in place of its optimized copy.
- **Anything else in an older folder: use the OneDrive app.** (It downloads a copy; it does not replace an optimized one.)
- Why: a second copy of GallerySync (in Samsung's Secure Folder) backing up to its own folder was showing the main gallery's albums.
- Unchanged: the check before uploading still looks in every earlier folder, so nothing is sent twice. The Change dialog's "GallerySync keeps looking in the old folder too" stays true for uploads.
- The other Clouds already listed only their own Settings folder.

No new HelpTopic. Same procedure: en_hash, LAST_UPDATED plus all 7 LAST_UPDATED_BY_LOCALE, build_guide.py, ./gradlew test, stage by name including the generated help_topics.xml, delete this file in that commit.
