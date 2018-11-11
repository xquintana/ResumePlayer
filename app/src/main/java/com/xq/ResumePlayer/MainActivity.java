package com.xq.ResumePlayer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.Manifest;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_READWRITE_STORAGE = 0;

    private String  dataFileName = "ResumePlayer.txt"; // The file where the audio list is stored
    private List<Item> itemList = new ArrayList<>(); // The player's audio list
    private Item selectedItem; // Selected audio file
    private FileDialog addFileDialog; // Dialog used to add a new audio item to the list
    private Handler myHandler = new Handler(); // Used to update the progress of the current audio file
    private HeadphoneReceiver receiver; // Used to detect when the headphone is unplugged
    private boolean playing = false; // Indicates whether the app is currently playing audio
    private int stepTime = 5000; // Number of milliseconds to jump forward or backward

    // Controls
    private TextView txtElapsed, txtDuration, txtFileName;
    private Button   btPrevious, btStepBack, btPlay, btStepForward, btNext, btAdd, btRemove;
    private SeekBar  seekbar;
    private LinearLayout itemListLayout; // The audio list layout, containing a layout for each item
    private MediaPlayer  mediaPlayer;


    /** Represents an audio file, containing information about the path and its playing state. */
    private class Item {
        Item() {}
        Item(String file, String folder, long timestamp)
        {
            this.file = file;
            this.folder = folder;
            this.timestamp = timestamp;
            elapsed = 0;
            duration = getDuration(folder, file);
            exists = true;
        }
        // Serialized variables
        String folder;
        String file;
        int elapsed;
        int duration;
        long timestamp; // Used to sort items by the last time they were played
        // Non-serialized variables
        boolean exists;
        TextView txtFolder;
        TextView txtFile;
        TextView txtProgress;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        // Initialize controls
        itemListLayout = findViewById(R.id.audioList);
        btPrevious = findViewById(R.id.btPrevious);
        btStepBack = findViewById(R.id.btStepBack);
        btPlay = findViewById(R.id.btPlay);
        btStepForward = findViewById(R.id.btStepForward);
        btNext = findViewById(R.id.btNext);
        btAdd = findViewById(R.id.btAdd);
        btRemove = findViewById(R.id.btRemove);
        txtElapsed = findViewById(R.id.textElapsed);
        txtDuration = findViewById(R.id.textDuration);
        txtFileName = findViewById(R.id.textAudioName);
        seekbar = findViewById(R.id.seekBar);

        // Configure controls
        txtElapsed.setTextColor(Color.WHITE);
        txtDuration.setTextColor(Color.WHITE);
        txtFileName.setTextColor(Color.WHITE);
        seekbar.setClickable(false);

        // Handle the UI events
        setListeners();

        // Handle the headphone disconnection event
        receiver = new HeadphoneReceiver();
        IntentFilter receiverFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(receiver, receiverFilter);

        // Load data from file
        loadItemList();

        // Display audio list
        updateListView();

        // Check Read/Write permissions
        int permissionCheckRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int permissionCheckWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheckRead != PackageManager.PERMISSION_GRANTED || permissionCheckWrite != PackageManager.PERMISSION_GRANTED) {
            showControls(false);
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READWRITE_STORAGE);
        }
        else showControls(true);
    }

    /** Handles the events of the buttons and the seek bar */
    private void setListeners() {
        btPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!playing) play();
                else pause();
            }
        });
        btStepBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((selectedItem.elapsed - stepTime) > 0) {
                    mediaPlayer.seekTo(selectedItem.elapsed - stepTime);
                }
            }
        });
        btStepForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((selectedItem.elapsed + stepTime) <= selectedItem.duration) {
                    mediaPlayer.seekTo(selectedItem.elapsed + stepTime);
                }
            }
        });
        btNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectNextItem();
            }
        });
        btPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPreviousItem();
            }
        });
        btAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String initialPath;
                    if (selectedItem != null) initialPath = selectedItem.folder;
                    else initialPath = Environment.getExternalStorageDirectory().toString() + "/Music";
                    addFileDialog = new FileDialog(MainActivity.this, new File(initialPath));
                }
                catch (Exception e){
                    Toast.makeText(getApplicationContext(), "Cannot set root path", Toast.LENGTH_SHORT).show();
                    return;
                }
                addFileDialog.addFileListener(new FileDialog.FileSelectedListener() {
                    public void fileSelected(File obj) {
                        String fullPath = obj.toString();
                        String file = new File(fullPath).getName();
                        String folder = extractFolder(fullPath);
                        if (!isValidAudio(folder, file)) {
                            Toast.makeText(MainActivity.this, "This file cannot be played", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Item item = new Item(file, folder, getTimeStamp());
                        itemList.add(item);
                        selectItem(item);
                        updateListView();
                    }
                });
                addFileDialog.showDialog();
            }
        });
        btRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedItem == null) return;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Remove from list?")
                        .setMessage("File: " + selectedItem.file + "\r\n\r\nFolder: " + selectedItem.folder )
                        .setNegativeButton(android.R.string.no, null)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface arg0, int arg1) {
                                itemList.remove(selectedItem);
                                selectItem(itemList.isEmpty() ? null : itemList.get(0));
                                updateListView();
                            }
                        }).create().show();
            }
        });
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && progress > 0) {
                    mediaPlayer.seekTo(progress);
                    if (!playing) txtElapsed.setText(getFormattedTime(progress));
                }
            }
        });
    }

    /** Shows or hides the controls */
    private void showControls(boolean enable) {
        int value = enable ? View.VISIBLE : View.INVISIBLE;
        itemListLayout.setVisibility(value);
        btPrevious.setVisibility(value);
        btStepBack.setVisibility(value);
        btPlay.setVisibility(value);
        btStepForward.setVisibility(value);
        btNext.setVisibility(value);
        btAdd.setVisibility(value);
        btRemove.setVisibility(value);
        txtElapsed.setVisibility(value);
        txtDuration.setVisibility(value);
        txtFileName.setVisibility(value);
        seekbar.setVisibility(value);
    }

    /** Enables or disables the player buttons. */
    private void enablePlayerButtons(boolean enable){
        btPrevious.setEnabled(enable);
        btStepBack.setEnabled(enable);
        btPlay.setEnabled(enable);
        btStepForward.setEnabled(enable);
        btNext.setEnabled(enable);
        seekbar.setEnabled(enable);
        btPlay.setText(playing ? "||" : ">");
    }

    /** Loads the audio item list from storage */
    private void loadItemList() {
        itemList.clear();
        selectedItem = null;
        int numErrors = 0;

        File dataFile = new File(getFilesDir(), dataFileName);

        if (!checkFileExists(dataFile.getAbsolutePath())) {
            saveItemList(); // Create empty file
            return;
        }

        try {
            FileReader reader = new FileReader(dataFile);
            BufferedReader br = new BufferedReader(reader);
            String line, path;

            while ((line = br.readLine()) != null) {
                Item audioInfo = new Item();
                audioInfo.folder = line;

                // Read audio file name
                if ((line = br.readLine()) == null) break;
                audioInfo.file = line;

                // Read elapsed time
                if ((line = br.readLine()) == null) break;
                try {
                    audioInfo.elapsed = Integer.parseInt(line);
                } catch(NumberFormatException nfe) {
                    System.out.println("Could not parse " + nfe);
                }

                // Read duration
                if ((line = br.readLine()) == null) break;
                try {
                    audioInfo.duration = Integer.parseInt(line);
                } catch(NumberFormatException nfe) {
                    System.out.println("Could not parse " + nfe);
                }

                // Read timestamp (used for sorting)
                if ((line = br.readLine()) == null) break;
                try {
                    audioInfo.timestamp = Long.parseLong(line);
                } catch(NumberFormatException nfe) {
                    System.out.println("Could not parse " + nfe);
                }

                // Check that the file exists
                path = makeFullPath(audioInfo.folder, audioInfo.file);
                audioInfo.exists = checkFileExists(path);
                if (!audioInfo.exists) numErrors++;

                // Add the audio item to the player list
                itemList.add(audioInfo);
            }
            reader.close();
            selectItem(itemList.isEmpty() ? null : itemList.get(0));
            if (numErrors > 0) {
                Toast.makeText(MainActivity.this, String.valueOf(numErrors) + " file(s) not found", Toast.LENGTH_SHORT).show();
            }
        }
        catch(IOException e) {
            Toast.makeText(getApplicationContext(), "Cannot load file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Stores the audio item list */
    private void saveItemList(){
        File dataFile = new File(getFilesDir(), dataFileName);
        try {
            FileWriter writer = new FileWriter(dataFile, false);
            for (int i = 0; i < itemList.size(); i++) {
                Item audioInfo = itemList.get(i);
                writer.write(audioInfo.folder + "\r\n");
                writer.write(audioInfo.file + "\r\n");
                writer.write(String.valueOf(audioInfo.elapsed) + "\r\n");
                writer.write(String.valueOf(audioInfo.duration) + "\r\n");
                writer.write(String.valueOf(audioInfo.timestamp) + "\r\n");
            }
            writer.flush();
            writer.close();
        }
        catch(IOException e) {
            Toast.makeText(getApplicationContext(), "Cannot save file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Updates the audio item list, showing most recent first */
    private void updateListView(){
        if (itemListLayout == null) return;

        // Sort the audio items in order of access time (latest first)
        Collections.sort(itemList,
                new Comparator<Item>() {
                    public int compare(Item obj1, Item obj2) {
                        return Long.compare(obj2.timestamp, obj1.timestamp);
                    }
                });

        // Add the audio items to the list layout
        itemListLayout.removeAllViews();
        for(int i = 0; i < itemList.size(); i++)
            addItemToListView(itemList.get(i));

        // Update file
        saveItemList();
    }

    /** Adds a section in the item list view that shows information about an item */
    private void addItemToListView(Item item){
        // Create a layout to render the item's information
        LinearLayout itemLayout = new LinearLayout(MainActivity.this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setId(itemListLayout.getChildCount());

        // Add the line that separates items
        ImageView separator = new ImageView(MainActivity.this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(0, 10, 0, 10);
        separator.setLayoutParams(params);
        separator.setBackgroundColor(Color.LTGRAY);
        itemLayout.addView(separator);

        // Add item's folder
        TextView txtFolder = new TextView(MainActivity.this);
        txtFolder.setText(removeRootFolder(item.folder));
        itemLayout.addView(txtFolder);
        item.txtFolder = txtFolder;

        // Add item's file name
        TextView txtFile = new TextView(MainActivity.this);
        txtFile.setText(item.file);
        itemLayout.addView(txtFile);
        item.txtFile = txtFile;

        // Add item's play progress
        if (item.exists) {
            TextView txtProgress = new TextView(MainActivity.this);
            txtProgress.setText(getProgressInfo(item));
            itemLayout.addView(txtProgress);
            item.txtProgress = txtProgress;
        }

        // The selected item will show highlighted
        setItemAppearance(item, item == selectedItem);

        // When the layout is clicked, the associated item is selected
        itemLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { selectItem(itemList.get(v.getId())); }
        });

        // Add the item's layout to the list layout
        itemListLayout.addView(itemLayout);
    }

    /** Sets the color scheme of an item in the list */
    private void setItemAppearance(Item item, boolean highlight){
        int colorFolder, colorFile, colorTime;

        if (item == null) return;

        if (highlight) {
            if (item.exists) {
                colorFolder = Color.rgb(255, 145, 0);
                colorFile = Color.WHITE;
                colorTime = Color.rgb(128, 203, 196);
            }
            else colorFolder = colorFile = colorTime = Color.RED;
        }
        else {
            if (item.exists) {
                colorFolder = Color.rgb(128, 72, 0);
                colorFile = Color.GRAY;
                colorTime = Color.rgb(64, 102, 98);
            }
            else colorFolder = colorFile = colorTime = Color.rgb(128, 0, 0);
        }

        if (item.txtFolder != null) {
            item.txtFolder.setTypeface(null, Typeface.BOLD);
            item.txtFolder.setTextColor(colorFolder);
        }
        if (item.txtFile != null) {
            item.txtFile.setTextColor(colorFile);
        }
        if (item.txtProgress != null) {
            item.txtProgress.setTextSize(11);
            item.txtProgress.setTextColor(colorTime);
        }
    }

    /** Used to show something in case an item cannot be selected. */
    private void showDummyItemInfo(){
        txtFileName.setText(R.string.blank);
        txtElapsed.setText(getFormattedTime(0));
        txtDuration.setText(getFormattedTime(0));
        seekbar.setProgress(0);
        releasePlayer();
        enablePlayerButtons(false);
    }

    /** Sets the current audio item and initializes the Media Player.
     * Called when the user clicks on an item of the audio list. */
    private void selectItem(Item item){
        releasePlayer();
        enablePlayerButtons(false);

        // Remove highlight on current audio item
        setItemAppearance(selectedItem, false);

        // Set new audio item as the current one
        selectedItem = item;
        if (selectedItem == null) {
            showDummyItemInfo();
            return;
        }

        try {
            String fullPath = makeFullPath(selectedItem.folder, selectedItem.file);
            if (checkFileExists(fullPath)) {
                mediaPlayer = MediaPlayer.create(MainActivity.this, Uri.parse(fullPath));
                if (mediaPlayer != null) {
                    if (selectedItem.elapsed >= selectedItem.duration) {
                        selectedItem.elapsed = 0;
                    }
                    txtFileName.setText(selectedItem.file);
                    txtElapsed.setText(getFormattedTime(selectedItem.elapsed));
                    txtDuration.setText(getFormattedTime(selectedItem.duration));
                    mediaPlayer.seekTo(selectedItem.elapsed);
                    seekbar.setMax(selectedItem.duration);
                    seekbar.setProgress(selectedItem.elapsed);
                    enablePlayerButtons(true);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        public void onCompletion(MediaPlayer mp) {
                            selectNextItem();
                        }
                    });
                }
                else throw new Exception("Media Player failed to load file");
            }
            else
            {
                Toast.makeText(MainActivity.this, "File does not exist", Toast.LENGTH_SHORT).show();
                showDummyItemInfo();
                enablePlayerButtons(false);
                selectedItem.exists = false;
            }
            setItemAppearance(selectedItem, true);
        }
        catch(Exception e)
        {
            Toast.makeText(MainActivity.this, "Cannot select file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            showDummyItemInfo();
            enablePlayerButtons(false);
        }
    }

    /** Selects the next audio file found in the current folder */
    private void selectNextItem() {
        Item nextFile = getNextItem(selectedItem);
        if (nextFile != null) {
            replaceSelectedItem(nextFile);
        }
        else {
            selectedItem.elapsed = 0;
            selectItem(selectedItem);
            Toast.makeText(MainActivity.this, "No more files in folder", Toast.LENGTH_SHORT).show();
        }
    }

    /** Returns the audio file that follows the current audio in the same folder (null if not found) */
    private Item getNextItem(Item audioInfo){
        if (audioInfo == null) return null;
        File file[] = new File(audioInfo.folder).listFiles();
        boolean foundCurFile = false;
        for (File aFile : file) {
            if (foundCurFile && isValidAudio(audioInfo.folder,  aFile.getName())) {
                return new Item(aFile.getName(), audioInfo.folder, audioInfo.timestamp);
            }
            if (aFile.getName().equals(audioInfo.file)) foundCurFile = true;
        }
        return null; // The current audio was the last file in the current folder
    }

    /** Selects the previous audio file found in the current folder */
    private void selectPreviousItem() {
        Item prevFile = getPreviousItem(selectedItem);
        if (prevFile != null) {
            replaceSelectedItem(prevFile);
        }
        else {
            Toast.makeText(MainActivity.this, "Already the first file in folder", Toast.LENGTH_SHORT).show();
        }
    }

    /** Returns the audio file that precedes the current audio in the same folder (null if not found) */
    private Item getPreviousItem(Item item){
        if (item == null) return null;
        String prevFileName = null;
        File file[] = new File(item.folder).listFiles();
        for (File aFile : file) {
            if (aFile.getName().equals(item.file)) {
                if (prevFileName == null) return null;
                return new Item(prevFileName, item.folder, item.timestamp);
            }
            if (isValidAudio(item.folder,  aFile.getName())) prevFileName = aFile.getName();
        }
        return null;
    }

    /** Replaces the selected audio item with another item */
    private void replaceSelectedItem(Item item) {
        if (item != null) {
            boolean wasPlaying = playing;
            itemList.remove(selectedItem);
            itemList.add(item);
            selectItem(item);
            if (wasPlaying) play();
            else updateListView();
        }
        else {
            selectItem(null);
        }
    }

    /** Plays the selected audio item */
    private void play() {
        if (mediaPlayer == null || selectedItem == null) {
            Toast.makeText(getApplicationContext(), "Cannot play: player not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            mediaPlayer.start();
            selectedItem.timestamp = getTimeStamp();
            selectedItem.elapsed = mediaPlayer.getCurrentPosition();
            selectedItem.duration = mediaPlayer.getDuration();
            txtDuration.setText(getFormattedTime(selectedItem.duration));
            txtElapsed.setText(getFormattedTime(selectedItem.elapsed));
            myHandler.postDelayed(updateProgress, 100);
            playing = true;
            enablePlayerButtons(true);
            updateListView();
        }
        catch (Exception e){
            Toast.makeText(getApplicationContext(), "Cannot play:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Pauses the selected audio item.
     * It can also be called from class HeadphoneReceiver */
    void pause() {
        if (mediaPlayer == null || selectedItem == null) return;
        mediaPlayer.pause();
        playing = false;
        selectedItem.elapsed = mediaPlayer.getCurrentPosition();
        enablePlayerButtons(true);
        updateListView();
    }

    /** Releases the Media Player control and sets the app's playing state to false */
    private void releasePlayer(){
        if (mediaPlayer != null) {
            if (playing) mediaPlayer.pause();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playing = false;
    }

    /** Returns true if the input file is supported by the media player.
     *  Check https://developer.android.com/guide/topics/media/media-formats */
    private boolean isValidAudio(String folder, String fileName){
        return (getDuration(folder, fileName) > 0); // Check if duration can be retrieved
    }

    /** Returns the duration of an audio file */
    private int getDuration(String folder, String file) {
        Uri uri = Uri.parse(makeFullPath(folder, file));
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(getApplicationContext(), uri);
            String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Integer.parseInt(durationStr);
        }
        catch (Exception ex) {
            return -1;
        }
    }

    /** Returns a string with the progress details */
    private String getProgressInfo(Item item){
        int progress = (int)((double)item.elapsed / (double)item.duration * 100);
        return getFormattedTime(item.elapsed) + " / " +
                getFormattedTime(item.duration) +
                String.format(Locale.ROOT, "   (%d%%)", progress);
    }

    /** Converts a number of milliseconds into a string of format HH:MM:SS */
    private String getFormattedTime(int millis) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    /** Returns the current time in seconds */
    private long getTimeStamp(){
        return System.currentTimeMillis()/1000;
    }

    /** Returns true if a file exists */
    private boolean checkFileExists(String path) {
        return new File(path).exists();
    }

    /** Extracts the folder from a full path */
    private String extractFolder(String fullPath) {
        return fullPath.substring(0,fullPath.lastIndexOf("/"));
    }

    /** Returns a full path combining a folder and a file name */
    private String makeFullPath(String folder, String file) {
        return folder + "/" + file;
    }

    /** Returns a path without the root folder, if present.
     * Simplifies the paths of files placed in the internal storage.
     * Files placed in the external storage will show with its full path. */
    private String removeRootFolder(String path){
        return path.replace(Environment.getExternalStorageDirectory().toString(), "");
    }

    /** Updates the progress of the current audio file and the seek bar */
    private Runnable updateProgress = new Runnable() {
        public void run() {
            if (mediaPlayer != null && selectedItem != null) {
                selectedItem.elapsed = mediaPlayer.getCurrentPosition();
                txtElapsed.setText(getFormattedTime(selectedItem.elapsed));
                if (selectedItem.txtProgress != null) {
                    selectedItem.txtProgress.setText(getProgressInfo(selectedItem));
                }
                seekbar.setProgress(selectedItem.elapsed);
            }
            myHandler.postDelayed(this, 100);
        }
    };

    /** Stops playing when the user presses the back key.*/
    @Override
    public void onBackPressed(){
        if (playing) pause();
        unregisterReceiver(receiver);
        super.onBackPressed();
    }

    /** Enables the controls only if the Read/write permission is granted*/
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READWRITE_STORAGE) {
            if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                showControls(true);
            }
        }
    }
}

