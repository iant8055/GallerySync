# Version 20 (0.3.19) — release notes

Built 5 Oct 2026. Closed testing track.

## Play Console "What's new" (455 characters; the limit is 500)

```
Restore: sort the files in a folder by Name, Newest or Largest, and tap the magnifying glass to search by name.

Albums: open an album and each file now says which Cloud it was backed up to, with the size moved up beside the file name.

Settings: a new Help & Feedback section. Rate us on Google Play, or open Bug Report for the address to write to and the version and handset details worth including.

Please tell us if anything looks wrong in dark mode.
```

## Longer note for testers

Three changes, all of them things you can see.

**Restore, inside a folder.** The header row that said *Files in this folder* now holds two
controls: **Sort by**, with Name, Newest and Largest, and a **magnifying glass** that opens a
search box. Search matches the file name as you type. Files you can actually restore stay at the
top whichever sort you pick — the sort orders them within that group rather than mixing greyed-out
files back in. Closing the search, or leaving the folder, clears what you typed.

**Albums, inside an album.** Each file used to read `name` then `size - video - date - marks`.
It now reads:

```
20170312_1223353.jpg              2 MB
28 Sep 2026 - backed up - OneDrive
```

The Cloud is named only once the file is really there. A file still waiting or failed shows no
Cloud, because until it lands it has a destination rather than a place.

**Settings, a new Help & Feedback section**, between Archive and the legal links. *Rate us on
Play Store* opens the listing. *Bug Report* opens a page with the address to write to and a block
giving the app version, your phone and its Android release — copy it into whatever you send, since
it is usually the difference between a fix and a guess.

## What would help most

- **Dark mode.** The Restore search box is the app's only text input and it has no colours of its
  own on purpose. If it is hard to read, or invisible, that is the thing to report first.
- Whether a long file name plus its size still fits on one line on your phone.
- Whether the Cloud named on a file matches where you actually sent that folder.

## How to send feedback

Through Google Play, from the GallerySync listing in the Play Store app — that reaches the
developer directly and is the channel for the testing period. The details block in
Settings -> Help & Feedback -> Bug Report is what to paste in.
