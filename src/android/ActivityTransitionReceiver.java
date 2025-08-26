package com.yourcompany.walkinglock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ActivityTransitionReceiver extends BroadcastReceiver {
    private static final String TAG = "ActivityTransitionReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Activity transition received");
        WalkingDetectionService.handleActivityTransition(context, intent);
    }
}