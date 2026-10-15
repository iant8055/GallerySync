# GallerySync — Translation Glossary

Terms on this page **must not be translated**. They are app-specific labels, button
text, mode names, product names or Ian's wording conventions, and translating them
would make the Guide disagree with what the user sees on screen or on another
service.

## How to use this with an LLM translation pass

Paste this file in as a system/preamble before the Guide content, with a rule like:

> The following terms must be preserved **exactly as written**, including
> capitalisation, spelling (British `-ise`, not American `-ize`), and spacing.
> They stay in English inside sentences of any target language. Do not inflect,
> decline, or wrap them in the target language's quoting style — leave them
> untouched.

Platform / OS words (Section 7 below) are the opposite: they belong in the user's
own language and must be translated.

---

## 1. App name and product

- GallerySync
- Pro

## 2. The four tab names

- Albums
- Restore
- Archive
- Settings  *(the GallerySync tab, not Android's Settings app — see §7)*

## 3. The four album modes

- Off
- Backup
- Sync
- Archive

## 4. Core verbs and nouns of the app

- Backup *(noun and verb)*
- Restore
- Archive *(as the action)*
- Optimise / Optimised  *(UK spelling, deliberately — never "Optimize")*
- Rescan
- Verified / Verified in the Cloud
- Pairing  *(which Cloud a folder sends to)*
- Mode
- Album
- Cloud  *(capitalised, treated as a proper noun — Ian's convention)*
- Backup Plan  *(capitalised — Ian's convention)*

## 5. Button and screen labels

### Primary actions
- Sync now
- Keep at full size
- Check these files
- Decide later
- Remain in Trash  *[note 2026-10-11 02:40 CDT: not an on-screen label in the current app; kept on purpose as Cloud jargon that may not translate well (Ian, 11 Oct 2026)]*
- Back up to Cloud  *[note 2026-10-11 02:40 CDT: as above, kept on purpose as jargon (Ian, 11 Oct 2026)]*

### Navigation and chrome
- Next
- Back
- Cancel
- Finish
- Change
- Remove
- Refresh
- Connect
- Sign out

### Filters, selectors, state words
- All
- All Albums
- Photos
- Videos
- Automatic
- Manual
- Ask

### Settings-tab names
- General
- Camera folder
- Camera Sync
- Special settings for Camera
- Files in this album *[changed 2026-10-11 by the GallerySync main session: was "Files in this folder"; the app now says Album for its own groups and Folder only for Android or Cloud folders (Ian, 11 Oct 2026)]*
- Only show files older than
- How To Guide
- (?)   *(the help button symbol — leave as-is wherever it appears)*

## 6. Brand and product names

These are trademarks or product names and are not translated in any language.

- OneDrive
- Google Photos
- Google Drive
- Dropbox
- pCloud
- IDrive e2
- Backblaze B2
- Samsung Gallery
- Google
- Microsoft
- Android
- Play Store
- Wi-Fi

## 7. Translate these — they are OS or platform words

These are **not** GallerySync's own labels; the user sees them in their phone's
language, so the Guide should match. Translate them normally.

- Trash *(the Files app's bin)*
- Recycle Bin *(Samsung Gallery's bin)*
- gallery / gallery app  *(generic, lower-case)*
- phone, folder, file, photo, video  *(ordinary nouns)*
- Settings  *when the sentence clearly means Android's own Settings app, not
  GallerySync's Settings tab. If ambiguous, prefer the GallerySync reading and
  keep it English.*

## 8. Spelling notes

- British English throughout: **Optimise**, **Optimised**, **Optimisation**,
  not the American spellings. The code, the UI and the Guide all use `-ise`.
- "Check mark" (two words) for the drawn mark; never "tick".
- "Select" and "Deselect", not "tick"/"untick".

## 9. Verbs made from these nouns

An LLM will often try to translate the surrounding verb while leaving the noun
in English, which reads badly. Keep the whole phrase English when the verb is
part of the app's own vocabulary:

- "Back up to Cloud" — stays English in full
- "Sync now" — stays English in full
- "verified in the Cloud" — stays English in full
- "set to Archive" / "set to Sync" / "set to Backup" / "set to Off" — the
  verb "set to" may be translated, but the mode name after it stays English

Prose verbs built around these terms (*"GallerySync backs up your photos"*,
*"the app will restore the file"*) are ordinary language and should be
translated normally.

## 10. On-screen labels and messages referenced by the Guide

*Added 2026-10-11 02:40 CDT by the GallerySync main session (Claude), at Ian's request. Reason for every entry
marked [+2026-10-11 02:40 CDT]: the app is English-only, so when the Guide refers to an on-screen label or message it must stay
in English, exactly as the user sees it (Ian, 11 Oct 2026: "if the Guide is referencing an On Screen Label then
the reference should remain in English"). Found by matching the Guide's bold text against the app's English
strings, plus labels the Guide shortens (N stands for a number the app fills in). The same words used as
ordinary prose, not as a reference to the screen, are translated normally.*

**Amended 2026-10-14 (Ian):** the first-time setup wizard now translates alongside the Guide, and its (?) pop-ups
appear in the chosen language. So inside the Guide's **setup chapter**, a translated page quotes the wizard's
translated labels from `app/src/main/res/values-<loc>/strings.xml` — a Spanish reader sees a Spanish screen,
so the Spanish Guide must quote the Spanish buttons. Everywhere else in the Guide (Albums, Restore, Archive,
Settings and the rest), the labels in this section stay in English because the rest of the app's screens
stay English. The entries below remain the English references and are what the non-setup chapters quote;
setup-chapter quotes come from the wizard's own `strings.xml` for the target language.

**Rule of thumb:** in the Guide, on-screen labels are written in **bold**. A bold run that is a label or message
stays English. A bold run that is only emphasis (for example a heading-like phrase such as "Nothing is
permanently deleted.") is translated.

### Buttons and actions
- Add a folder [+2026-10-11 02:40 CDT]
- Archive now [+2026-10-11 02:40 CDT]
- Back up N to OneDrive [+2026-10-11 02:40 CDT]
- Browse... [+2026-10-12]  *(the OneDrive folder picker button in the Where new backups go dialog; added with the choose-backup-folder feature)*
- Bug Report [+2026-10-11 02:40 CDT]
- Cancel [+2026-10-11 02:40 CDT]
- Check all albums [+2026-10-11 02:40 CDT] *[changed 2026-10-11 by the GallerySync main session: was "Check all folders"; the app now says Album for its own groups and Folder only for Android or Cloud folders (Ian, 11 Oct 2026)]*
- Choose all [+2026-10-11 02:40 CDT]
- Clear [+2026-10-11 02:40 CDT]
- Close [+2026-10-11 02:40 CDT]
- Continue [+2026-10-11 02:40 CDT]
- Copy address [+2026-10-11 02:40 CDT]
- Copy details [+2026-10-11 02:40 CDT]
- Dismiss [+2026-10-11 02:40 CDT]
- Enter keys [+2026-10-11 02:40 CDT]
- Give search permission [+2026-10-11 02:40 CDT]
- Grant access [+2026-10-11 02:40 CDT]
- Keep [+2026-10-11 02:40 CDT]
- Keep all in OneDrive [+2026-10-11 02:40 CDT]
- Keep my edits [+2026-10-11 02:40 CDT]
- Leave [+2026-10-11 02:40 CDT]
- Leave all in the trash [+2026-10-11 02:40 CDT]
- Leave them there [+2026-10-11 02:40 CDT]
- New folder [+2026-10-11 02:40 CDT]
- Open in browser [+2026-10-11 02:40 CDT]
- Overwrite [+2026-10-11 02:40 CDT]
- Pause [+2026-10-11 02:40 CDT]
- Rate us on Play Store [+2026-10-11 02:40 CDT]
- Read more in the How To Guide [+2026-10-11 02:40 CDT]
- Refresh [+2026-10-11 02:40 CDT]
- Remove from OneDrive [+2026-10-11 02:40 CDT]
- Remove N from OneDrive [+2026-10-11 02:40 CDT]
- Reset to default [+2026-10-11 02:40 CDT]
- Restore them to the phone first [+2026-10-11 02:40 CDT]
- Resume [+2026-10-11 02:40 CDT]
- Retry [+2026-10-11 02:40 CDT]
- Right now [+2026-10-11 02:40 CDT]
- Select all [+2026-10-11 02:40 CDT]
- Show [+2026-10-11 02:40 CDT]
- Show all Albums [+2026-10-11 02:40 CDT]
- Show new Albums [+2026-10-11 02:40 CDT]
- Skip [+2026-10-11 02:40 CDT]
- Start 30-day free trial [+2026-10-11]  *(added during the 11 Oct Guide revision; the Settings button that starts the Pro trial, distinct from Unlock Pro which purchases)*
- Stop [+2026-10-11 02:40 CDT]
- Stop restoring [+2026-10-11 02:40 CDT]
- SYNC NOW [+2026-10-11 02:40 CDT]  *(the wizard's capitalised button; "Sync now" on the Albums tab is §5)*
- Try again [+2026-10-11 02:40 CDT]
- Unlock Pro [+2026-10-11 02:40 CDT]
- Up one folder [+2026-10-11 02:40 CDT]
- Use this folder [+2026-10-11 02:40 CDT]
- Yes [+2026-10-11 02:40 CDT]
- No [+2026-10-11 02:40 CDT]

### Settings rows and choices
- Appearance, System, Light, Dark [+2026-10-11 02:40 CDT]
- Use mobile data [+2026-10-11 02:40 CDT]
- Clouds [+2026-10-11 02:40 CDT]
- Backup folder [+2026-10-12]  *(the line under each connected Cloud in Settings → Backup, showing where that Cloud's backups go; has a Change button beside it)*
- Where each folder goes [+2026-10-11 02:40 CDT]
- Goes to [+2026-10-11 02:40 CDT]
- Folders to back up [+2026-10-11 02:40 CDT]
- Leave the Cloud copy [+2026-10-11 02:40 CDT]
- Ask me about the Cloud copy [+2026-10-11 02:40 CDT]
- Optimise photos, Optimise video [+2026-10-11 02:40 CDT]
- Older than [+2026-10-11 02:40 CDT]
- Straight away, 1 hour, 12 hours, 1 day, 1 week, 1 month, 1 year [+2026-10-11 02:40 CDT]  *(as on-screen choices)*
- Compression, Video compression, Video compression level [+2026-10-11 02:40 CDT]
- High, Medium, Low [+2026-10-11 02:40 CDT]  *(the compression buttons)*
- High compression, Medium compression, Low compression [+2026-10-11 02:40 CDT]
- Show empty folders [+2026-10-11 02:40 CDT]
- Camera folder, Other… [+2026-10-11 02:40 CDT]
- Select Photos/Videos older than [+2026-10-11 02:40 CDT]
- Notify when files are ready to archive [+2026-10-11 02:40 CDT]
- Help & Feedback [+2026-10-11 02:40 CDT]
- Privacy Policy, Delete Account Info, Contact Info [+2026-10-11 02:40 CDT]
- Language, Multi-Language Support Coming Soon [+2026-10-11 02:40 CDT]
- main (free) [+2026-10-11 02:40 CDT]
- Not connected [+2026-10-11 02:40 CDT]
- Endpoint, Region, Bucket name, Access key ID, Secret access key [+2026-10-11 02:40 CDT]  *(the key form's fields)*
- Sort by, Name, Date, Status [+2026-10-11 02:40 CDT]  *(the album file list's sort choices)*
- Delay [+2026-10-11 02:40 CDT]
- New [+2026-10-11 02:40 CDT]  *(the tag on a new album)*

### Headers, counts and states
- Album Mode Count [+2026-10-11 02:40 CDT]
- Albums to Restore [+2026-10-11 02:40 CDT] *[changed 2026-10-11 by the GallerySync main session: was "Folders to Restore"; the app now says Album for its own groups and Folder only for Android or Cloud folders (Ian, 11 Oct 2026)]*
- Files to Archive [+2026-10-11 02:40 CDT]
- Checking your Cloud [+2026-10-11 02:40 CDT]
- Pending [+2026-10-11 02:40 CDT]
- Syncing, Paused [+2026-10-11 02:40 CDT]
- Scanning... [+2026-10-11 02:40 CDT]
- Checking... [+2026-10-11 02:40 CDT]
- Working... [+2026-10-11 02:40 CDT]
- Batch [+2026-10-11 02:40 CDT]
- verified in OneDrive [+2026-10-11 02:40 CDT]
- sent to [+2026-10-11 02:40 CDT]  *(as in the album card's Cloud line)*
- Backs up to [+2026-10-12]  *(album file list header; names the Cloud this album is backed up to)*
- Also has files in [+2026-10-12]  *(album file list header; names other Clouds that also hold files from this album)*
- Where new backups go [+2026-10-12]  *(the dialog title shown when choosing a Cloud's backup folder)*
- to restore, to download, already on this phone, selected here [+2026-10-11 02:40 CDT]
- Available for download, Already on this phone [+2026-10-11 02:40 CDT]
- Not archiving [+2026-10-11 02:40 CDT]
- too recent for your filter [+2026-10-11 02:40 CDT]
- N files validated, All files validated, Nothing can be archived [+2026-10-11 02:40 CDT]
- Total estimated savings [+2026-10-11 02:40 CDT]
- Nothing selected [+2026-10-11 02:40 CDT]
- Nothing left to archive [+2026-10-11 02:40 CDT]
- Optimising photos, Optimising video [+2026-10-11 02:40 CDT]

### Messages (quoted from the screen)
- GallerySync needs access to your photos [+2026-10-11 02:40 CDT]
- Only some photos are shared [+2026-10-11 02:40 CDT]
- No permission to read media. [+2026-10-11 02:40 CDT]
- Wi-Fi only. / Syncs on Wi-Fi and mobile data. [+2026-10-11 02:40 CDT]
- Could not reach OneDrive [+2026-10-11 02:40 CDT]
- stopped: your Cloud is full. [+2026-10-11 02:40 CDT]
- Nothing in your Cloud is missing from this phone, so there is nothing to bring back. [+2026-10-11 02:40 CDT]
- Restored to full size / Back on this phone / Could not restore, your file is unchanged [+2026-10-11 02:40 CDT]
- Backing up... / Moving to Trash/Recycle Bin... / Removed from this phone. [+2026-10-11 02:40 CDT]  *(the on-screen message keeps "Trash/Recycle Bin" in English; §7 applies only to ordinary sentences)*
- Could not check your Cloud / Not in your Cloud / In your Cloud but the wrong size [+2026-10-11 02:40 CDT]
- Checking with your Cloud... [+2026-10-11 02:40 CDT]
- Nothing was removed. [+2026-10-11 02:40 CDT]
- Stop the backup? [+2026-10-11 02:40 CDT]
- Re-optimise existing videos? [+2026-10-11 02:40 CDT]
- This photo has been edited since it was backed up. Overwrite it with the original? [+2026-10-11 02:40 CDT]
- Please select your language to continue [+2026-10-14]  *(Welcome-screen prompt for the language picker added in sibling commit aaa11cc; the language names below it are shown in their own language and are not translated at all)*

## 11. Android's own words (not our concern)

*Added 2026-10-11 02:40 CDT. Reason: Ian, 11 Oct 2026: "if they are translated by Android then we do not have to
worry about them".* Labels on Android's own screens, such as **Allow all** and **Select photos** on the permission
screen, already appear in the phone's language. Translate them to match what the phone shows; they are not
glossary terms.

---

*Last reviewed: 2026-10-14, after Phase 0 of the translation pipeline (per-locale build) and the Welcome-screen language picker (sibling commit aaa11cc). Update this file whenever a new user-facing label is
added to the app, so the next translation sweep stays consistent with what is
on screen.*
