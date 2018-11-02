package com.xq.ResumePlayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Pauses the player when the headphone is unplugged */
public class HeadphoneReceiver extends BroadcastReceiver {

    private boolean headphoneConnected = false;

    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (action == null) return;

        if ((action.compareTo(Intent.ACTION_HEADSET_PLUG)) == 0) {  // Unplugged event
            if (intent.hasExtra("state")) {
                if (headphoneConnected && intent.getIntExtra("state", 0) == 0) {
                    headphoneConnected = false;
                    try {
                        // Stop playing
                        ((MainActivity)context).pause();
                    }
                    catch (ClassCastException e) {
                        Toast.makeText(context, "Cast Exception: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                    catch (Exception e) {
                        Toast.makeText(context, "Exception" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else if (!headphoneConnected && intent.getIntExtra("state", 0) == 1) {
                    headphoneConnected = true;
                }
            }
        }
    }
}