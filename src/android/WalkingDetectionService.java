package com.yourcompany.walkinglock;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.ActivityTransitionRequest;

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class WalkingDetectionService extends Service {
    private static final String TAG = "WalkingDetectionService";
    private static final String CHANNEL_ID = "WalkingLockChannel";
    private static final int NOTIFICATION_ID = 123;
    
    private static WalkingOverlayView overlayView;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startActivityRecognition();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        hideOverlay(this);
    }
    
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Walking Detection Service",
                NotificationManager.IMPORTANCE_LOW
            );
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
    
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Walking Detection")
            .setContentText("Monitoring walking activity")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void startActivityRecognition() {
        List<ActivityTransition> transitions = new ArrayList<>();
        
        // Detectar cuando empieza a caminar
        transitions.add(new ActivityTransition.Builder()
            .setActivityType(DetectedActivity.WALKING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build());
        
        // Detectar cuando deja de caminar
        transitions.add(new ActivityTransition.Builder()
            .setActivityType(DetectedActivity.WALKING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            .build());
        
        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
        
        Task<Void> task = ActivityRecognition.getClient(this)
            .requestActivityTransitionUpdates(request, new WalkingActivityTransitionReceiver());
        
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(TAG, "Activity transition updates registered successfully");
            }
        });
    }
    
    public static void showOverlay(Context context) {
        if (overlayView == null) {
            overlayView = new WalkingOverlayView(context);
        }
        overlayView.show();
    }
    
    public static void hideOverlay(Context context) {
        if (overlayView != null) {
            overlayView.hide();
            overlayView = null;
        }
    }
    
    private class WalkingActivityTransitionReceiver extends com.google.android.gms.location.ActivityTransitionReceiver {
        @Override
        public void onActivityTransition(ActivityTransitionEvent event) {
            if (event.getActivityType() == DetectedActivity.WALKING) {
                if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    // Usuario empezó a caminar
                    Log.d(TAG, "Walking detected - showing overlay");
                    showOverlay(WalkingDetectionService.this);
                } else if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                    // Usuario dejó de caminar
                    Log.d(TAG, "Walking stopped - hiding overlay");
                    hideOverlay(WalkingDetectionService.this);
                }
            }
        }
    }
}