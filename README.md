# ResumePlayer

An audio player for Android that keeps track of the progress of a list of files so they can be resumed at any time.  
For example, you can listen to a radio podcast for a while, then an audiobook, and later on resume the podcast where you left it.

## Usage

Use the button '+' to add audio files to the player's list.  
The list shows the location, file name and progress for each audio file.  
Select the file to play by pressing on the list. The selected item is highlighted.  
The player will show the file name and the last progress of the selected item.

Finally, press the button '>' to start playing.

![Screenshot](/screenshot.png)

### Controls

* **|<** Select previous audio file in the current folder.
* **<<** Jump 5 seconds backwards.
* **>** Play selected audio file.
* **||** Pause selected audio file.
* **>>** Jump 5 seconds forward.
* **>|** Select next audio file in the current folder.

The files in the list are sorted so the latest played file is shown first.  
If a file does not exist anymore or it cannot be played for some reason, it is shown in red.  
The player stops playing automatically if the headphones are unplugged.  

When adding a file, the browsing location starts at the same folder as the selected file.  
If the list is empty, the initial browsing location is the "Music" folder of the internal storage.
To delete a file, select it from the list and press the '-' button on the upper right corner.

## Supported audio formats
The supported audio formats are determined by the [Android platform's supported audio formats and codecs](https://developer.android.com/guide/topics/media/media-formats#audio-codecs).

For example, it can play MP3, MP4, WAV or Ogg but not WMA.

## Permissions
This app requires storage read/write permissions in order to browse for audio files and store the audio list.

## Build Environment
Developed with Android Studio 3.2.1 on Windows.  
Gradle version: 4.6.  
Min Sdk Version: API 23 (Android 6.0 / Marshmallow)
Target Sdk Version: API 26 (Android 8.0 / Oreo)

## Install
A debug and unsigned APK can be downloaded from [here](/release/ResumePlayer.apk).  
You will need to temporary check the option "Unknown Sources" in Android's security settings.  

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
