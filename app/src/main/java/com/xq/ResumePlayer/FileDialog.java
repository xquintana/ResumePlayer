package com.xq.ResumePlayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Allows to browse and select an audio file from storage. */
class FileDialog {
    private static final String PARENT_DIR = "..";
    private final Activity activity; // The main activity
    private File currentPath; // Current browsing location
    private String[] fileList; // Files contained in the current location

    /** List of listeners to be notified when a file is selected */
    private final ListenerList<FileSelectedListener> fileListenerList = new ListenerList<>();

    /** true when the current browsing location is at the top level and we must select a
     * storage volume (internal/external) instead of a folder */
    private boolean selectStorage = false;

    /** Constructor */
    FileDialog(Activity activity, File path) {
        this.activity = activity;
        if (!path.exists()) path = Environment.getExternalStorageDirectory();
        fileList = getFileList(path);
    }

    /** Creates a dialog that browses the storage contents */
    private Dialog createFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AlertDialogTheme);
        String title = currentPath.getPath();

        if (selectStorage) { // Select a storage rather than a file
            title = "Select Storage";
        }

        builder.setTitle(title);

        // Use a custom ArrayAdapter to color folders
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                R.layout.row_file, fileList) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                String fileName = getItem(position);
                if (fileName != null) {
                    File file = getChosenFile(fileName);
                    if (file.isDirectory()) {
                        textView.setTextColor(Color.parseColor("#ff9100"));
                    } else {
                        textView.setTextColor(Color.WHITE);
                    }
                }
                return view;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            File chosenFile = getChosenFile(fileList[which]);
            if (!chosenFile.exists()) {
                Toast.makeText(activity.getApplicationContext(), "NOT EXISTS", Toast.LENGTH_SHORT).show();
            }
            else if (chosenFile.isDirectory()) {
                fileList = getFileList(chosenFile);
                dialog.cancel();
                dialog.dismiss();
                showDialog();
            } else fireFileSelectedEvent(chosenFile);
        });

        return builder.create();
    }

    /** Creates and displays the dialog */
    void showDialog() {
        createFileDialog().show();
    }

    /** Returns a list of internal and external storage volumes available */
    private String[] getStorageVolumes() {
        List<String> mainPaths = new ArrayList<>();
        File[] dirs = activity.getApplicationContext().getExternalFilesDirs(null);
        for (File dir : dirs) {
            // 'dir' can be null if the device contains an external SD card slot but no SD card is present.
            if (dir != null) {
                String mPath = dir.getAbsolutePath();
                int end = mPath.indexOf("/Android");
                if (end != -1) {
                    mPath = mPath.substring(0, end);
                    mainPaths.add(mPath);
                }
            }
        }
        String[] paths = new String[mainPaths.size()];
        paths = mainPaths.toArray(paths);
        return paths;
    }

    /** Returns true if the file name has a supported audio extension */
    private boolean isAudioFile(String name) {
        if (Utils.isAndroid13orHigher()) {
            return true; // Assume that in versions 13+, only audio files can be selected.
        }
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") ||
                lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".flac") ||
                lower.endsWith(".opus") || lower.endsWith(".wma");
    }

    /** Returns the list of files contained in the input path */
    private String[] getFileList(File path) {
        List<String> fileNames = new ArrayList<>();
        currentPath = path;
        selectStorage = false;

        if (path.exists()) {
            File parent = path.getParentFile();
            String[] children = path.list();

            if (children == null || (parent != null && parent.getAbsolutePath().equals("/"))) {
                // We must choose a storage volume instead of a file or folder
                selectStorage = true;
                Collections.addAll(fileNames, getStorageVolumes());
            }
            else {
                // Add the files and folders found in current path
                if (parent != null) fileNames.add(PARENT_DIR);

                List<String> dirs = new ArrayList<>();
                List<String> files = new ArrayList<>();

                for (String child : children) {
                    File file = new File(path, child);
                    if (file.isDirectory()) {
                        dirs.add(child);
                    } else if (isAudioFile(child)) {
                        files.add(child);
                    }
                }

                Collections.sort(dirs, String::compareToIgnoreCase);
                Collections.sort(files, String::compareToIgnoreCase);

                fileNames.addAll(dirs);
                fileNames.addAll(files);
            }
        }
        return fileNames.toArray(new String[]{});
    }

    /** Returns a File object associated to the input file name.
     * If it's a storage volume, returns the File object of the volume.
     * It it's a parent directory, returns the File object of the parent directory so the dialog
     * will list the parent's content.
     * Finally, assuming it's a regular file, returns a File object representing that file.
     */
    private File getChosenFile(String fileChosen) {
        if (selectStorage) return new File(fileChosen);
        else if (fileChosen.equals(PARENT_DIR)) return currentPath.getParentFile();
        else return new File(currentPath, fileChosen);
    }

    /** Interface used to notify that a file has been selected. Implemented by the main activity. */
    interface FileSelectedListener {
        void fileSelected(File file);
    }

    /** Subscribes the main activity to the event of selecting a file */
    void addFileListener(FileSelectedListener listener) {
        fileListenerList.add(listener);
    }

    /** Notifies the listeners that a file has been selected */
    private void fireFileSelectedEvent(final File file) {
        fileListenerList.fireEvent(listener -> listener.fileSelected(file));
    }
}

/** Used to send an event to the main activity when a file is selected */
class ListenerList<L> {
    private final List<L> listenerList = new ArrayList<>();
    interface FireHandler<L> {
        void fireEvent(L listener);
    }
    void add(L listener) {
        listenerList.add(listener);
    }
    void fireEvent(FireHandler<L> fireHandler) {
        List<L> copy = new ArrayList<>(listenerList);
        for (L l : copy) {
            fireHandler.fireEvent(l);
        }
    }
}
