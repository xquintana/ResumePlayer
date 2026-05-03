package com.xq.ResumePlayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.io.IOException;

/**
 * Service responsible for background audio playback.
 */
public class PlaybackService extends Service {
    private static final String CHANNEL_ID = "PlaybackServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private MediaPlayer mediaPlayer;
    private LoudnessEnhancer loudnessEnhancer;
    private boolean boostEnabled = false;
    private final IBinder binder = new LocalBinder();
    private PlaybackCompletionListener completionListener;
    private HeadphoneReceiver headphoneReceiver;
    private String currentPath;
    private int lastKnownPosition = 0;

    public class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerHeadphoneReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (currentPath != null) {
            try {
                int position = isPlaying() ? getCurrentPosition() : lastKnownPosition;
                // Use commit() to ensure data is written before the process is killed
                getSharedPreferences("ResumePlayerPrefs", Context.MODE_PRIVATE)
                        .edit()
                        .putInt(currentPath, position)
                        .commit(); // Synchronous write is critical here
            } catch (Exception ignored) {}
        }
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setPlaybackCompletionListener(PlaybackCompletionListener listener) {
        this.completionListener = listener;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void prepare(String path, int position, boolean boost) {
        try {
            stopPlayback();
            currentPath = path;
            lastKnownPosition = position;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.seekTo(position);
            boostEnabled = boost;
            applyBoost();
            mediaPlayer.setOnCompletionListener(mp -> {
                if (completionListener != null) {
                    completionListener.onCompletion();
                }
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            });
        } catch (IOException e) {
            mediaPlayer = null;
            currentPath = null;
            lastKnownPosition = 0;
        }
    }

    public void play(String audioName) {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            startForeground(NOTIFICATION_ID, getNotification("Playing " + audioName));
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            lastKnownPosition = mediaPlayer.getCurrentPosition();
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        }
    }

    public void seekTo(int millis) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(millis);
            lastKnownPosition = millis;
        }
    }

    public int getCurrentPosition() {
        return (mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : lastKnownPosition;
    }

    public int getDuration() {
        return (mediaPlayer != null) ? mediaPlayer.getDuration() : 0;
    }

    public boolean isPlaying() {
        return (mediaPlayer != null) && mediaPlayer.isPlaying();
    }

    public void setBoost(boolean enabled) {
        boostEnabled = enabled;
        applyBoost();
    }

    private void applyBoost() {
        if (mediaPlayer != null) {
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                    loudnessEnhancer = null;
                }
                loudnessEnhancer = new LoudnessEnhancer(mediaPlayer.getAudioSessionId());
                loudnessEnhancer.setTargetGain(boostEnabled ? 2000 : 0);
                loudnessEnhancer.setEnabled(boostEnabled);
            } catch (Exception e) {
                Log.e("PlaybackService", "Failed to apply audio boost", e);
            }
        }
    }

    private void stopPlayback() {
        if (loudnessEnhancer != null) {
            loudnessEnhancer.release();
            loudnessEnhancer = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void registerHeadphoneReceiver() {
        headphoneReceiver = new HeadphoneReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headphoneReceiver, filter);
    }

    @Override
    public void onDestroy() {
        if (headphoneReceiver != null) {
            unregisterReceiver(headphoneReceiver);
        }
        stopPlayback();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Playback Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification getNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Resume Player")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private class HeadphoneReceiver extends BroadcastReceiver {
        private boolean headphoneConnected = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                int state = intent.getIntExtra("state", -1);
                if (state == 0) { // Unplugged
                    if (headphoneConnected) {
                        pause();
                    }
                    headphoneConnected = false;
                } else if (state == 1) { // Plugged
                    headphoneConnected = true;
                }
            }
        }
    }
}
