package com.xq.ResumePlayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Allows to browse and select an audio file from storage. */
class FileDialog {
    private static final String PARENT_DIR = "..";
    private final Activity activity; // The main activity
    private File currentPath; // Current browsing location
    private String[] fileList; // Files contained in the current location

    /** List of listeners to be notified when a file is selected */
    private ListenerList<FileSelectedListener> fileListenerList = new ListenerList<>();

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
        Dialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        String title = currentPath.getPath();

        if (selectStorage) { // Select a storage rather than a file
            title = "Select Storage";
        }

        builder.setTitle(title);
        builder.setItems(fileList, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
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
            }
        });

        dialog = builder.show();
        return dialog;
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
                mPath = mPath.substring(0, end);
                mainPaths.add(mPath);
            }
        }
        String[] paths = new String[mainPaths.size()];
        paths = mainPaths.toArray(paths);
        return paths;
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
                Collections.addAll(fileNames,children);
                Collections.sort(fileNames, new Comparator<String>() {
                    @Override
                    public int compare(String s1, String s2) {
                        return s1.compareToIgnoreCase(s2);
                    }
                });
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
        fileListenerList.fireEvent(new ListenerList.FireHandler<FileSelectedListener>() {
            public void fireEvent(FileSelectedListener listener) {
                listener.fileSelected(file);
            }
        });
    }
}

/** Used to send an event to the main activity when a file is selected */
class ListenerList<L> {
    private List<L> listenerList = new ArrayList<>();
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
