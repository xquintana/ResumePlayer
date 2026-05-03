package com.xq.ResumePlayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_STORAGE_PERMISSION = 100;

    private final String dataFileName = "ResumePlayer.txt";
    private final List<Item> itemList = new ArrayList<>();
    private Item selectedItem;
    private FileDialog addFileDialog;
    private final Handler myHandler = new Handler(Looper.getMainLooper());
    private HeadphoneReceiver receiver;
    private boolean playing = false;
    private final int stepTime = 5000;

    private TextView txtElapsed, txtDuration, txtFileName;
    private Button   btPrevious, btStepBack, btPlay, btStepForward, btNext, btAdd, btRemove, btBoost;
    private SeekBar  seekbar;
    private LinearLayout itemListLayout;
    
    private PlaybackService playbackService;
    private boolean isBound = false;
    private boolean boostEnabled = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.LocalBinder binder = (PlaybackService.LocalBinder) service;
            playbackService = binder.getService();
            isBound = true;
            playbackService.setPlaybackCompletionListener(() -> myHandler.post(() -> selectNextItem()));
            syncWithService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            playbackService = null;
        }
    };

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
        String folder;
        String file;
        int elapsed;
        int duration;
        long timestamp;
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

        itemListLayout = findViewById(R.id.audioList);
        btPrevious = findViewById(R.id.btPrevious);
        btStepBack = findViewById(R.id.btStepBack);
        btPlay = findViewById(R.id.btPlay);
        btStepForward = findViewById(R.id.btStepForward);
        btNext = findViewById(R.id.btNext);
        btAdd = findViewById(R.id.btAdd);
        btRemove = findViewById(R.id.btRemove);
        btBoost = findViewById(R.id.btBoost);
        txtElapsed = findViewById(R.id.textElapsed);
        txtDuration = findViewById(R.id.textDuration);
        txtFileName = findViewById(R.id.textAudioName);
        seekbar = findViewById(R.id.seekBar);

        txtElapsed.setTextColor(Color.WHITE);
        txtDuration.setTextColor(Color.WHITE);
        txtFileName.setTextColor(Color.WHITE);
        seekbar.setClickable(false);

        setListeners();

        receiver = new HeadphoneReceiver();
        IntentFilter receiverFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        ContextCompat.registerReceiver(this, receiver, receiverFilter, ContextCompat.RECEIVER_EXPORTED);

        loadItemList();
        updateListView();

        checkPermissions();

        Intent intent = new Intent(this, PlaybackService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                try {
                    unregisterReceiver(receiver);
                } catch (Exception ignored) {}
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncWithService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isBound && playbackService != null && selectedItem != null) {
            selectedItem.elapsed = playbackService.getCurrentPosition();
            saveItemList();
        }
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        myHandler.removeCallbacks(updateProgress);
        super.onDestroy();
    }

    private void syncWithService() {
        if (playbackService != null) {
            String servicePath = playbackService.getCurrentPath();
            if (servicePath != null) {
                // If service is playing/prepared for something else, sync our selection
                if (selectedItem == null || !makeFullPath(selectedItem.folder, selectedItem.file).equals(servicePath)) {
                    for (Item item : itemList) {
                        if (makeFullPath(item.folder, item.file).equals(servicePath)) {
                            selectedItem = item;
                            break;
                        }
                    }
                }
            }

            if (playbackService.isPlaying()) {
                playing = true;
                if (selectedItem != null) {
                    txtFileName.setText(selectedItem.file);
                    txtDuration.setText(getFormattedTime(playbackService.getDuration()));
                    seekbar.setMax(playbackService.getDuration());
                }
                enablePlayerButtons(true);
                myHandler.removeCallbacks(updateProgress);
                myHandler.post(updateProgress);
                updateListView();
            } else if (selectedItem != null) {
                playing = false;
                enablePlayerButtons(true);
                updateUIFromSelectedItem();
                String fullPath = makeFullPath(selectedItem.folder, selectedItem.file);
                if (checkFileExists(fullPath) && servicePath == null) {
                    playbackService.prepare(fullPath, selectedItem.elapsed, boostEnabled);
                }
            }
        }
    }

    private void updateUIFromSelectedItem() {
        if (selectedItem == null) return;
        txtFileName.setText(selectedItem.file);
        txtElapsed.setText(getFormattedTime(selectedItem.elapsed));
        txtDuration.setText(getFormattedTime(selectedItem.duration));
        seekbar.setMax(selectedItem.duration);
        seekbar.setProgress(selectedItem.elapsed);
        updateListView();
    }

    private void checkPermissions() {
        if (Utils.isAndroid13orHigher()) {
            // Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                showControls(false);
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_STORAGE_PERMISSION);
            } else {
                showControls(true);
            }
        } else {
            // Android 12 and below (API 32 and below)
            // We only need READ_EXTERNAL_STORAGE to play files. Internal storage (getFilesDir) needs no permission.
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                showControls(false);
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            } else {
                showControls(true);
            }
        }
    }

    private void setListeners() {
        btPlay.setOnClickListener(v -> {
            if (!playing) play();
            else pause();
        });
        btStepBack.setOnClickListener(v -> {
            if (selectedItem != null && playbackService != null) {
                int currentPos = playbackService.getCurrentPosition();
                int target = Math.max(currentPos - stepTime, 0);
                playbackService.seekTo(target);
                selectedItem.elapsed = target;
                txtElapsed.setText(getFormattedTime(target));
                seekbar.setProgress(target);
            }
        });
        btStepForward.setOnClickListener(v -> {
            if (selectedItem != null && playbackService != null) {
                int currentPos = playbackService.getCurrentPosition();
                int target = Math.min(currentPos + stepTime, selectedItem.duration);
                playbackService.seekTo(target);
                selectedItem.elapsed = target;
                txtElapsed.setText(getFormattedTime(target));
                seekbar.setProgress(target);
            }
        });
        btNext.setOnClickListener(v -> selectNextItem());
        btPrevious.setOnClickListener(v -> selectPreviousItem());
        btAdd.setOnClickListener(v -> {
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
            addFileDialog.addFileListener(obj -> {
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
            });
            addFileDialog.showDialog();
        });
        btRemove.setOnClickListener(v -> {
            if (selectedItem == null) return;
            new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogTheme)
                    .setTitle(selectedItem.folder)
                    .setMessage(selectedItem.file + "\r\n\r\n" + "Remove from list?")
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                        itemList.remove(selectedItem);
                        selectItem(itemList.isEmpty() ? null : itemList.get(0));
                        updateListView();
                    }).create().show();
        });
        btBoost.setOnClickListener(v -> toggleBoost());
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && progress > 0 && playbackService != null) {
                    playbackService.seekTo(progress);
                    if (!playing) txtElapsed.setText(getFormattedTime(progress));
                }
            }
        });
    }

    private void toggleBoost() {
        boostEnabled = !boostEnabled;
        btBoost.setText(boostEnabled ? R.string.boost_on : R.string.boost_off);
        btBoost.setTextColor(boostEnabled ? Color.GREEN : Color.WHITE);
        if (playbackService != null) {
            playbackService.setBoost(boostEnabled);
        }
    }

    private void showControls(boolean enable) {
        int value = enable ? View.VISIBLE : View.GONE;
        itemListLayout.setVisibility(value);
        btPrevious.setVisibility(value);
        btStepBack.setVisibility(value);
        btPlay.setVisibility(value);
        btStepForward.setVisibility(value);
        btNext.setVisibility(value);
        btAdd.setVisibility(View.VISIBLE);
        btRemove.setVisibility(View.VISIBLE);
        btBoost.setVisibility(value);
        txtElapsed.setVisibility(value);
        txtDuration.setVisibility(value);
        txtFileName.setVisibility(value);
        seekbar.setVisibility(value);

        View controls = findViewById(R.id.controls);
        if (controls != null) controls.setVisibility(value);
        View playbackInfo = findViewById(R.id.playbackInfo);
        if (playbackInfo != null) playbackInfo.setVisibility(value);
    }

    private void enablePlayerButtons(boolean enable){
        btPrevious.setEnabled(enable);
        btStepBack.setEnabled(enable);
        btPlay.setEnabled(enable);
        btStepForward.setEnabled(enable);
        btNext.setEnabled(enable);
        seekbar.setEnabled(enable);
        btPlay.setText(playing ? "||" : ">");
    }

    private void loadItemList() {
        itemList.clear();
        selectedItem = null;
        int numErrors = 0;

        File dataFile = new File(getFilesDir(), dataFileName);

        if (!dataFile.exists()) {
            saveItemList();
            return;
        }

        try (FileReader reader = new FileReader(dataFile);
             BufferedReader br = new BufferedReader(reader)) {
            String line, path;

            while ((line = br.readLine()) != null) {
                Item audioInfo = new Item();
                audioInfo.folder = line;

                if ((line = br.readLine()) == null) break;
                audioInfo.file = line;

                if ((line = br.readLine()) == null) break;
                try {
                    audioInfo.elapsed = Integer.parseInt(line);
                } catch(NumberFormatException ignored) {}

                if ((line = br.readLine()) == null) break;
                try {
                    audioInfo.duration = Integer.parseInt(line);
                } catch(NumberFormatException ignored) {}

                if ((line = br.readLine()) == null) break;
                try {
                    audioInfo.timestamp = Long.parseLong(line);
                } catch(NumberFormatException ignored) {}

                path = makeFullPath(audioInfo.folder, audioInfo.file);
                
                // Check if the service saved a more recent position due to onTaskRemoved
                int savedPos = getSharedPreferences("ResumePlayerPrefs", Context.MODE_PRIVATE).getInt(path, -1);
                if (savedPos != -1) {
                    audioInfo.elapsed = savedPos;
                    getSharedPreferences("ResumePlayerPrefs", Context.MODE_PRIVATE).edit().remove(path).apply();
                }

                audioInfo.exists = checkFileExists(path);
                if (!audioInfo.exists) numErrors++;

                itemList.add(audioInfo);
            }
            selectItem(itemList.isEmpty() ? null : itemList.get(0));
            if (numErrors > 0) {
                Toast.makeText(MainActivity.this, numErrors + " file(s) not found", Toast.LENGTH_SHORT).show();
            }
        }
        catch(IOException e) {
            Toast.makeText(getApplicationContext(), "Cannot load file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveItemList(){
        File dataFile = new File(getFilesDir(), dataFileName);
        try (FileWriter writer = new FileWriter(dataFile, false)) {
            for (int i = 0; i < itemList.size(); i++) {
                Item audioInfo = itemList.get(i);
                writer.write(audioInfo.folder + "\r\n");
                writer.write(audioInfo.file + "\r\n");
                writer.write(audioInfo.elapsed + "\r\n");
                writer.write(audioInfo.duration + "\r\n");
                writer.write(audioInfo.timestamp + "\r\n");
            }
            writer.flush();
        }
        catch(IOException e) {
            Toast.makeText(getApplicationContext(), "Cannot save file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateListView(){
        if (itemListLayout == null) return;

        Collections.sort(itemList, (obj1, obj2) -> Long.compare(obj2.timestamp, obj1.timestamp));

        itemListLayout.removeAllViews();
        for(int i = 0; i < itemList.size(); i++)
            addItemToListView(itemList.get(i));

        saveItemList();
    }

    private void addItemToListView(Item item){
        LinearLayout itemLayout = new LinearLayout(MainActivity.this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setId(itemListLayout.getChildCount());

        ImageView separator = new ImageView(MainActivity.this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(0, 10, 0, 10);
        separator.setLayoutParams(params);
        separator.setBackgroundColor(Color.LTGRAY);
        itemLayout.addView(separator);

        TextView txtFolder = new TextView(MainActivity.this);
        txtFolder.setText(removeRootFolder(item.folder));
        itemLayout.addView(txtFolder);
        item.txtFolder = txtFolder;

        TextView txtFile = new TextView(MainActivity.this);
        txtFile.setText(item.file);
        itemLayout.addView(txtFile);
        item.txtFile = txtFile;

        if (item.exists) {
            TextView txtProgress = new TextView(MainActivity.this);
            txtProgress.setText(getProgressInfo(item));
            itemLayout.addView(txtProgress);
            item.txtProgress = txtProgress;
        }

        setItemAppearance(item, item == selectedItem);

        itemLayout.setOnClickListener(v -> selectItem(itemList.get(v.getId())));

        itemListLayout.addView(itemLayout);
    }

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

    private void showDummyItemInfo(){
        txtFileName.setText(R.string.blank);
        txtElapsed.setText(getFormattedTime(0));
        txtDuration.setText(getFormattedTime(0));
        seekbar.setProgress(0);
        playing = false;
        enablePlayerButtons(false);
    }

    private void selectItem(Item item){
        if (item != null && item == selectedItem && playing) {
            return;
        }

        playing = false;
        enablePlayerButtons(false);
        setItemAppearance(selectedItem, false);
        selectedItem = item;
        if (selectedItem == null) {
            showDummyItemInfo();
            return;
        }

        try {
            String fullPath = makeFullPath(selectedItem.folder, selectedItem.file);
            if (checkFileExists(fullPath)) {
                if (selectedItem.elapsed >= selectedItem.duration) {
                    selectedItem.elapsed = 0;
                }
                txtFileName.setText(selectedItem.file);
                txtElapsed.setText(getFormattedTime(selectedItem.elapsed));
                txtDuration.setText(getFormattedTime(selectedItem.duration));
                seekbar.setMax(selectedItem.duration);
                seekbar.setProgress(selectedItem.elapsed);
                enablePlayerButtons(true);

                if (playbackService != null) {
                    playbackService.prepare(fullPath, selectedItem.elapsed, boostEnabled);
                    int actualDuration = playbackService.getDuration();
                    if (actualDuration > 0) {
                        selectedItem.duration = actualDuration;
                        txtDuration.setText(getFormattedTime(selectedItem.duration));
                        seekbar.setMax(selectedItem.duration);
                    }
                }
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

    private Item getNextItem(Item audioInfo, boolean reverse){
        if (audioInfo == null) return null;
        File[] files = new File(audioInfo.folder).listFiles();
        boolean foundCurFile = false;
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                int res = f1.getName().compareToIgnoreCase(f2.getName());
                return reverse ? -res : res;
            });
            for (File file : files) {
                if (foundCurFile && isValidAudio(audioInfo.folder, file.getName())) {
                    return new Item(file.getName(), audioInfo.folder, audioInfo.timestamp);
                }
                if (file.getName().equals(audioInfo.file)) foundCurFile = true;
            }
        }
        return null;
    }

    private void selectNextItem() {
        Item nextFile = getNextItem(selectedItem, false);
        if (nextFile != null) {
            replaceSelectedItem(nextFile);
        }
        else {
            if (selectedItem != null) {
                selectedItem.elapsed = 0;
                playing = false;
                selectItem(selectedItem);
                updateListView();
            }
            Toast.makeText(MainActivity.this, "No more files in folder", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectPreviousItem() {
        Item prevFile = getNextItem(selectedItem, true);
        if (prevFile != null) {
            replaceSelectedItem(prevFile);
        }
        else {
            if (selectedItem != null) {
                selectedItem.elapsed = 0;
                playing = false;
                selectItem(selectedItem);
                updateListView();
            }
            Toast.makeText(MainActivity.this, "Already the first file in folder", Toast.LENGTH_SHORT).show();
        }
    }

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

    private void play() {
        if (selectedItem == null || playbackService == null) {
            Toast.makeText(getApplicationContext(), "Cannot play: player not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            playbackService.play(selectedItem.file);
            selectedItem.timestamp = getTimeStamp();
            selectedItem.duration = playbackService.getDuration();
            selectedItem.elapsed = Math.min(playbackService.getCurrentPosition(), selectedItem.duration);
            txtDuration.setText(getFormattedTime(selectedItem.duration));
            txtElapsed.setText(getFormattedTime(selectedItem.elapsed));
            myHandler.removeCallbacks(updateProgress);
            myHandler.postDelayed(updateProgress, 100);
            playing = true;
            enablePlayerButtons(true);
            updateListView();
        }
        catch (Exception e){
            Toast.makeText(getApplicationContext(), "Cannot play:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void pause() {
        if (playbackService == null || selectedItem == null) return;
        playbackService.pause();
        playing = false;
        selectedItem.elapsed = Math.min(playbackService.getCurrentPosition(), selectedItem.duration);
        enablePlayerButtons(true);
        updateListView();
    }

    private boolean isValidAudio(String folder, String fileName){
        return (getDuration(folder, fileName) > 0);
    }

    private int getDuration(String folder, String file) {
        Uri uri = Uri.parse(makeFullPath(folder, file));

        try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()) {
            mmr.setDataSource(getApplicationContext(), uri);
            String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return (durationStr != null) ? Integer.parseInt(durationStr) : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    private String getProgressInfo(Item item){
        int progress = (item.duration > 0) ? (int)((double)item.elapsed / (double)item.duration * 100) : 0;
        return getFormattedTime(item.elapsed) + " / " +
                getFormattedTime(item.duration) +
                String.format(Locale.ROOT, "   (%d%%)", progress);
    }

    private String getFormattedTime(int millis) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

    private long getTimeStamp(){
        return System.currentTimeMillis()/1000;
    }

    private boolean checkFileExists(String path) {
        return new File(path).exists();
    }

    private String extractFolder(String fullPath) {
        int index = fullPath.lastIndexOf("/");
        return (index != -1) ? fullPath.substring(0, index) : fullPath;
    }

    private String makeFullPath(String folder, String file) {
        return folder + "/" + file;
    }

    private String removeRootFolder(String path){
        return path.replace(Environment.getExternalStorageDirectory().toString(), "");
    }

    private final Runnable updateProgress = new Runnable() {
        public void run() {
            if (playbackService != null && selectedItem != null) {
                selectedItem.elapsed = Math.min(playbackService.getCurrentPosition(), selectedItem.duration);
                txtElapsed.setText(getFormattedTime(selectedItem.elapsed));
                if (selectedItem.txtProgress != null) {
                    selectedItem.txtProgress.setText(getProgressInfo(selectedItem));
                }
                seekbar.setProgress(selectedItem.elapsed);
            }
            if (playing) {
                myHandler.postDelayed(this, 100);
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) showControls(true);
            else {
                Toast.makeText(this, "Permission is required to list and play audio files", Toast.LENGTH_LONG).show();
            }
        }
    }
}
