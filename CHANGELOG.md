# Changelog

## 1.7

Update checks the download before it opens the installer.

- The download starts at this repository's GitHub release. GitHub's file hosts are accepted after that release redirects there.
- The file must be package com.exoplanet.notch and signed with the upload key. Any other download is deleted. Update says the file is not a signed Notch update.

## 1.6

Match a frequency in the app, and set a quarter-octave width.

- Menu, then Match, plays a quiet sine or a narrow noise. The tone does not pass through the notches.
- Lower and higher play the octave beside the match. The frequency stays until you tap Keep.
- Writing a frequency into a notch sets that center and turns the notch on.
- Width still runs from 0.10 to 1.00 octave. The buttons step by 0.05, so 0.25 can be set. A value can also be typed.
- The tools that package and sign the APK use Bouncy Castle 1.86. That release fixes CVE-2026-71891, which still affects 1.85.

## 1.5

Playlists you can fill and edit, and a simpler player.

- New playlist takes a name, tracks, and albums before it is saved. Edit adds or removes tracks and albums.
- Each playlist has a menu: Play, Add to queue, Edit, Rename, and Delete.
- The same playlist can be added to the queue more than once.
- The player buttons are Play, Previous, and Next, then Add tracks, Add albums, and Queue.
- Play test noise and Open audio are removed.

## 1.4

Dependency updates for the reported library bugs.

- The player uses Guava 33.7.2-android. Media3 1.11.1 brought 33.3.1-android, which is covered by CVE-2026-102554.
- The tools that package and sign the APK use the patched releases: Bouncy Castle 1.85, jose4j 0.9.6, JDOM 2.0.6.1, Commons Lang 3.18.0, and HttpClient 4.5.14.

Playback is unchanged.

## 1.3

Playlists.

- Add several files. They play in the order you pick them.
- Add an album folder. Disc folders such as CD1 and CD2 play by disc, then track number, then file name.
- Mix files and albums in one queue. Tap a track to jump to it, drag the handle to reorder a file or a whole album, and swipe left to remove a row. Removing the track that is playing continues with the next track. Reordering leaves the current track playing.
- Repeat off, Repeat all, and Repeat one. Repeat off stops at the end of the queue. Load starts with Repeat off.
- Save, load, rename, and delete named playlists. Update playlist writes the current queue back. Resume offers to continue the last playlist after you leave the app.
- A moved or deleted file is marked Unavailable and skipped. A missing album track stays in the list instead of disappearing after a successful folder scan. Put the file back in that folder and load again to clear the flag. If the folder cannot be opened, the saved list is kept and unreadable tracks are flagged.

## 1.2

Menu with the listening guide, About, and Update. Update installs a newer GitHub release. Light and dark follow the phone.

## 1.1

Width is an octave fraction from 0.1 to 1.0. Settings are restored on the next start. Play and Pause sit with the other audio buttons.

## 1.0

First public release. Four notches on audio the app plays.
