# GallerySync — Changelog

Every change since version 26 (0.3.25), the last build the Closed Testing group had. The short notes Play shows to users
(500 characters per language) are in `release-notes/`; this file is the full record.

## Version 31 (0.3.30) — built 24 Oct 2026

**Large text on narrow screens** (seen on the Galaxy Z Fold 8's cover screen)
- The Archive tab's question: its Yes and No can always be scrolled into reach, and the age choice stays visible.
- Labels on buttons and filters shrink to fit on one line instead of being cut off ("Back", "Arch", "Checkin").
- Sizes keep the number and unit together ("936 MB" no longer splits across lines).
- The coloured card at the top of each tab lets its text grow only up to 1.3 times. Everything else still follows the
  phone's text size in full.

**Font**
- The app draws its text in its own font, Noto Sans, so it looks the same on every phone whatever the phone's font style.
- **Settings → Font** switches between **App font** and **Phone's font**. Text size follows the phone either way.

## Version 30 (0.3.29) — 20 Oct 2026, Closed Testing

The same code as version 29, under a new version number. Play would not add the version 29 bundle, which is in production
review, to the Closed Testing track. It gets the Welcome screen fix to the testers and gives Google's pre-launch tests a
build to run.

## Version 29 (0.3.28) — 20 Oct 2026, Production

**Welcome screen (testers could not get past it in version 28)**
- The one-line rolling language strip at the bottom of the Welcome screen is gone. Its first line was the prompt, which did
  nothing when tapped, and nothing showed that it had to be swiped.
- A small **Tap to continue** pill sits near the bottom of the Welcome picture, in every language.
- Tapping the picture opens a **language pop-up**: the heading, the seven languages in their own names, and **OK**.
- Tapping a language only selects it: the check moves, and the heading and button switch to that language at once, so a
  wrong tap is easy to see and undo. **OK** continues. Tapping outside the pop-up does nothing; Back leaves the app.

## Version 28 (0.3.27) — built 15–17 Oct 2026, not released (rejected in production review)

**Seven languages**
- Choose the language on the Welcome screen: English, Español, Português (Brasil), Français, Deutsch, 日本語, हिन्दी.
- The **setup wizard**, the **Settings tab**, the **(?) help cards** and the **How To Guide** follow that choice.
  The Albums, Restore and Archive tabs stay in English.
- Anything that names a screen or a mode elsewhere in the app stays in English in every language: tab names, the album
  modes Backup, Sync, Archive and Off, and the Archive and camera-folder filters.
- **Settings → Language** shows and changes the same choice. It now sits just below the How To Guide card, above Appearance.
- The How To Guide is published in all seven languages, with a language row on every page; "Read more" on a (?) card opens
  the guide in the chosen language at that topic.
- Every install carries all seven languages (Play's per-language download splitting is turned off), so a language chosen
  on the Welcome screen works even when it is not the phone's language.

**Pro and the free trial**
- The Pro unlock is shown as **$9.99** (one-time, no subscription) in the trial offer, the days-left line and the
  trial-ended line. The price charged is set in Play Console.
- **Trial reminders:** a notification 3 days before the free Pro trial ends, and another on the day it ends. Sent once each,
  only when Pro is not bought and a folder actually goes to a Cloud other than the free one. Nothing is changed or removed.
- The **Trial ended** line in Settings no longer names Google Photos for every Cloud.
- Purchase tested end to end with a real payment on a Play install: bought, verified against the licensing key,
  acknowledged, Pro unlocked, an extra Cloud backed up, Pro kept after a reinstall, and removed by a refund.

**Ratings**
- After the first backup is checked and finished, pressing **Finish** asks Google Play for its rating sheet, once. It waits
  until the New Albums pop-up is cleared. Play decides whether it actually appears; nothing depends on the answer.

**The How To Guide and pop-ups**
- The guide and the policy pages follow the app's **Appearance** setting (System, Light or Dark) instead of always showing
  their light colours.
- The Appearance (?) card says so.

**Setup wizard wording**
- The finish line reads "N file(s) checked … N file(s) sent in all", correct for any count.
- The start-time card says **"You have selected nothing to back up"** instead of "0 B" when everything chosen is already in
  the Cloud.
- The folder list's "15 folders, 2212 photos, 42 videos" line is translated (it was built from English words).
- Sizes use each language's decimal mark.
- The German video-quality label is shortened so it fits its button.

**Under the hood**
- Code cleanup after Closed Testing: the original ContentProvider and its two database tables are removed (database
  version 16, with a tested migration), along with test-only code and the old folder screen.
- Translation sources for the wizard and Settings live in `tools/wizard/`, generated into `values-<language>/strings.xml`.

## Version 27 (0.3.26) — 14 Oct 2026, built, not uploaded

- The Albums status line clears when an automatic backup ends, instead of staying on "Uploading … 100%".
- The finish summary says the check is OneDrive's and that the sent figure covers every Cloud.
- The folder screen says **Select** instead of Check.
