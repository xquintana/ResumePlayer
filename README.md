# Resume Player

**Resume Player** is a simple Android audio player that remembers playback progress for each file, so you can resume listening at any time.  
For example, you can listen to a song, then an audiobook, and later return to the song right where you left off.

## Usage

Use the **+** button in the top-right corner to add audio files to the list.  
The list shows each file’s location, name, and playback progress.  
Select a file in the list to play it (the selected file will be highlighted), then press **>** to start playback.  

![Screenshot](/screenshot.png)

### Controls

*   **`+` / `-`** : Add or remove audio files from your list.  
*   **BOOST ON/OFF** : Toggle audio amplification.  
*   **`|<` / `>|`** : Skip to the previous or next file in the current folder.  
*   **`<<` / `>>`** : Jump 5 seconds backward or forward.  
*   **`>` / `||`** : Play or Pause the selected file.  

The most recently played audio file appears at the top of the list.  
If a file no longer exists or cannot be played, it is shown in red.  
The player stops automatically if the headphones are unplugged.  

When adding a file, browsing starts in the same folder as the selected file.  

## Permissions

This app requires access to your device’s storage to find and play audio files.

* On Android 13 and newer, the system will ask for access to audio files only.  
* On older Android versions, the system may ask for access to "photos and media files". This is a broader, older permission, but the app uses it only to read audio files.  

No personal data is collected or modified. Access is used strictly for browsing and playing audio files stored on your device.  

## App Details

* **App name / ID:** com.xq.ResumePlayer
* **Version:** 2.0
* **Minimum Android version:** Android 6.0 (Marshmallow) — API 23
* **Target Android version:** Android 13.0 (Tiramisu) — API 33
* **Build SDK:** Android 14.0 (Upside Down Cake) — API 34

## Install
A debug, unsigned APK can be downloaded from the [Releases section](./releases).   
You will need to temporarily enable the *Unknown Sources* option in Android's security settings.  

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.  
