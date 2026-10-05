# Version 22 (0.3.21) -- release notes

Built 5 Oct 2026. Supersedes version 21, which was published but never installed from Play.

## Play Console "What's new" (451 characters; the limit is 500)

```
The setup wizard now stays until you press Finish. It could disappear the moment your first backup finished, taking the Finish screen with it.

Faster first backup: a large library was re-checking the same folders on every batch and now remembers what it has already looked at.

The New Albums notice waits until setup is done, and stops asking again every time you open the Albums tab.

Restore: Sort by and the search box are readable in light mode.
```

## Longer note for testers

**The wizard could end itself.** When your first backup finished, the app lifted an internal timer that had
nothing to do with setup -- and that same record was what told the app the wizard was over. So setup vanished
at the exact moment it should have shown you **Finish**, and you landed in the app with no idea whether the
backup had completed. Now only pressing Finish ends setup, and a build check enforces it.

Verified on two phones: a small library and a real 8,642-file one both reached **100% - Finish** and waited to
be pressed.

**Faster first backup.** A large library was spending 29 seconds of every batch re-listing the same Cloud
folder -- the same 3,378 files it had listed moments earlier. The same run now takes about sixteen minutes
where it was heading for sixteen hours. It will not reuse a listing it is unsure of: one that failed, one that
broke off part way, or one taken when backups were going somewhere else.

**The New Albums notice** no longer appears over the wizard during setup, and no longer asks again every time
you return to the Albums tab. Seeing it now counts as seen, however you close it. A folder that appears later
still asks.

**Settings** has a clear boundary between connecting your Clouds and choosing where each folder goes.

**Restore, inside a folder, in light mode:** the Sort by control and the search text were dark green on a dark
green band and effectively invisible.

## What would help most

- A **fresh install**: does setup stay until you press Finish, and does it get there?
- Does the backup card's percentage track the count beneath it? On a library where almost everything is already
  in your Cloud the total is still too large -- known, and being worked on next.
- Anything that looks like a file marked backed up when it is not actually in your Cloud. That is the one
  failure worth reporting immediately.

## How to send feedback

Through Google Play, from the GallerySync listing in the Play Store app. The details block in
Settings -> Help & Feedback -> Bug Report is what to paste in.
