# Guide handoff: Settings → Restore → Show all backup folders (24 Nov 2026)

App change (Ian, 24 Nov 2026). Please add a topic and a (?) for the new switch, all 7 languages.

- **Restore lists only the OneDrive backup folder set in Settings** (Settings → Backup → Clouds → Backup folder). Before, it also listed earlier backup folders and `Samsung Gallery/DCIM`, always.
- **New switch: Settings → Restore → Show all backup folders**, off by default, under *Show empty folders*. On: Restore also lists earlier backup folders and `Samsung Gallery/DCIM`, for a file that is not in the current backup folder.
- Why: a second copy of GallerySync (in Samsung's Secure Folder) backing up to its own folder was showing the main gallery's albums too.
- What does **not** change: the check before uploading still looks in every folder, so nothing is sent twice whatever the switch says. Changing the backup folder still moves nothing.
- The other Clouds already list only their own Settings folder; the switch is about OneDrive.
- Restore-related topics that say Restore looks in `Samsung Gallery/DCIM` or in every earlier folder need the same correction (check restore-overview and the Change dialog topic, "GallerySync keeps looking in the old folder too": still true for uploads, no longer for Restore unless the switch is on).

App string: `settings_show_all_restore_folders` ("Show all backup folders"; translated in values-*/strings.xml). Once the topic exists, tell the app session the HelpTopic id so the row gets its (?).

Same procedure: en_hash, LAST_UPDATED plus all 7 LAST_UPDATED_BY_LOCALE, build_guide.py, ./gradlew test, stage by name **including the generated help_topics.xml**, delete this file in that commit.
