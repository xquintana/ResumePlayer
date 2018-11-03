# ResumePlayer

An audio player for Android that keeps track of the progress of a list of files so they can be resumed at any time.  
For example, you can listen to a radio podcast for a while, then an audiobook, and later on resume the podcast where you left it.

## Usage

Use the buttons '+' and '-' on the upper right corner to add or remove audio files from the player's list.  
The list shows the location, file name and progress for each audio file.  
Select a file by pressing on the list. The selected item is highlighted.  
The player will show the file name and the progress of the selected item.

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

## Supported audio formats
The supported audio formats are determined by the [Android platform's supported audio formats and codecs](https://developer.android.com/guide/topics/media/media-formats#audio-codecs).

For example, it can play MP3, MP4, WAV or Ogg but not WMA.

## Permissions
This app requires storage read/write permissions in order to browse for audio files and store the audio list.

## Build Environment
Developed in Android Studio 3.1.4 on Windows.  
Gradle version: 4.4.  
Min/Target Sdk Version: API 23 (Marshmallow)  

## Install
A debug and unsigned APK can be downloaded from [here](/release/ResumePlayer.apk).  
You will need to temporary check the option "Unknown Sources" in Android's security settings.  

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
