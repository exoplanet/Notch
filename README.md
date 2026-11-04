# Notch

Android player that removes chosen frequencies from audio it plays.

You can open one file, add several files, or add an album folder. Files and albums share one queue. Save that queue as a named playlist and load it later. The listening guide is under Menu, then Guide. The same text is in [GUIDE.md](GUIDE.md). Version history is in [CHANGELOG.md](CHANGELOG.md).

The source is public. The latest build is at https://github.com/exoplanet/Notch/releases/latest.

Pull requests are accepted from exoplanet only.

## Playlists

**Add files** adds the audio files you pick, and they play in that order. **Open audio** starts a fresh queue with one file. **Play test noise** starts a fresh queue with the built-in tone and repeats that tone.

**Add album** adds a folder, including one that is split into disc folders such as CD1 and CD2. Tracks play in this order:

1. Disc. The disc number stored in the file is used when the file has one. Otherwise a folder named CD1, CD 2, Disc 1, or the like is that disc. Numbered discs play before other folders. Files that sit in the album folder itself play after the disc folders.
2. Track number. A file with no track number plays after the numbered tracks on its disc.
3. File name. Numbers in the name are compared in order, so track 2 comes before track 10.

**Queue** shows single files and albums together. Tap a track to jump to it. Drag the ⋮⋮ handle to move a single file or a whole album. Swipe a row to the left to remove it. Removing the track that is playing continues with the next track.

**Repeat** cycles through Repeat off, Repeat all, and Repeat one. Off stops after the last track. All goes back to the first track. One keeps playing the current track. Load starts with Repeat off.

**Save as playlist**, on the queue screen, stores the queue on this phone. Menu, then **Playlists**, lists saved playlists, newest first. **Load** plays one from the start and checks the files again. **Rename** changes the name. **Delete** removes the saved playlist. The audio files stay where they are. **Update playlist** writes the current queue over the playlist you loaded. After you leave Notch, **Resume** offers to continue that playlist at the last position. **Dismiss** clears that offer.

If a file has been moved or deleted, its row says Unavailable and Notch skips it. The queue counts the unavailable tracks, and the player says “Some tracks are unavailable.” A missing track from an album stays in its place in the list. Put the file back in that folder and load the playlist again to play it. A moved file has a new location, so add it again with **Add files**. If the album folder itself cannot be opened, Notch keeps the saved track list and marks the tracks it cannot read. An empty folder reports “No audio files in that folder.”
