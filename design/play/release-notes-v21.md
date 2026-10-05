# Version 21 (0.3.20) -- release notes

Built 5 Oct 2026. Closed testing track. Nothing in it has been seen on a device.

## Play Console "What's new" (435 characters; the limit is 500)

```
Faster first backup. A large library was re-checking the same folders on every batch; it now remembers what it has already looked at, which turns hours of waiting into minutes.

Clearer progress. The percentage and the file count on the backup card were measuring different things, so the ring could sit at 0% while files were going up. They now agree.

Restore: the Sort by control and the search box are readable again in light mode.
```

## Longer note for testers

**If you have a large library, this is the one that matters.** On a real 8,642-file library the app was
spending 29 seconds of every batch re-listing the same folder in the Cloud -- the same 3,378 files it had
listed a moment earlier -- while resolving only four files. The run was going to take about sixteen hours,
almost none of it moving data. It now remembers what each folder holds for ten minutes, so it asks once
instead of once per batch.

It will not reuse anything it is not sure of: a listing that failed, a listing that broke off part way, or
one taken when backups were going somewhere else. Each of those is thrown away and asked again, because the
cost of being wrong there is a file marked backed up without being sent.

**The backup card's numbers now agree with each other.** The ring and the count beneath it were drawn from
two different totals, so you could see "19%" above "10 of 8642", or 0% while files were plainly uploading.

**Restore, inside a folder, in light mode:** the Sort by control and the text you type into the search box
were dark green on a dark green band and effectively invisible. Fixed. Dark mode was always fine, which is
how it got out.

## What would help most

- A first backup on a big library: does it move at a sensible pace, and does the card's percentage track the
  count beneath it?
- Restore's search and Sort by, in **light** mode this time.
- Anything that looks like a file being marked backed up when it is not actually in your Cloud. That is the
  one failure this change could cause, and it is the thing worth reporting immediately.

## How to send feedback

Through Google Play, from the GallerySync listing in the Play Store app. The details block in
Settings -> Help & Feedback -> Bug Report is what to paste in.
