# Version 21 (0.3.20) -- release notes

Built 5 Oct 2026. Nothing in it has been seen on a device.

## Play Console "What's new" (439 characters; the limit is 500)

```
Faster first backup. A large library was re-checking the same folders on every batch; it now remembers what it has already looked at, turning hours of waiting into minutes.

Setup no longer ends itself while the first backup is still running, so the Finish screen appears as it should.

Clearer progress: the percentage and the file count on the backup card now agree.

Restore: Sort by and the search box are readable again in light mode.
```

## Longer note for testers

**Setup could end itself part-way through the first backup.** If that happened you were dropped into the
app with the backup still running, no progress card and no Finish button, and no way back to either. Seen
on a Galaxy Z Fold 8 with 3,200 files still queued. The cause was the app asking the wrong question about
its own progress -- one that counted only the albums you had selected, while a first backup deliberately
covers everything. With every album unset, as they are after a first backup, the answer came back "nothing
left to do". Only pressing **Finish** ends setup now, and a build check enforces it.

**If you have a large library, the speed change is the one you will feel.** On a real 8,642-file library the
app was spending 29 seconds of every batch re-listing the same folder in the Cloud -- the same 3,378 files
it had listed a moment earlier -- while resolving only four files. The run was on course for sixteen hours,
almost none of it moving data. It now remembers what each folder holds for ten minutes, so it asks once
instead of once per batch.

It will not reuse anything it is unsure of: a listing that failed, one that broke off part way, or one taken
when backups were going somewhere else. Each is thrown away and asked again, because the cost of being wrong
there is a file marked backed up without being sent.

**The backup card's numbers now agree with each other.** The ring and the count beneath it were drawn from
two different totals, so you could see "19%" above "10 of 8642", or 0% while files were plainly uploading.

**Restore, inside a folder, in light mode:** the Sort by control and the text you type into the search box
were dark green on a dark green band and effectively invisible. Dark mode was always fine, which is how it
got out.

## What would help most

- A **fresh install** on a big library: does setup stay until you press Finish, and does it get there?
- Does the first backup move at a sensible pace, and does the percentage track the count beneath it?
- Restore's search and Sort by, in **light** mode this time.
- Anything that looks like a file being marked backed up when it is not actually in your Cloud. That is the
  one failure the speed change could cause, and it is worth reporting immediately.

## How to send feedback

Through Google Play, from the GallerySync listing in the Play Store app. The details block in
Settings -> Help & Feedback -> Bug Report is what to paste in.
