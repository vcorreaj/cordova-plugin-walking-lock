package com.yourcompany.walkinglock;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.util.Log;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class WalkingDetectionService extends Service implements SensorEventListener {
    private static final String TAG = "WalkingDetectionService";
    private static final String CHANNEL_ID = "WalkingLockChannel";
    private static final int NOTIFICATION_ID = 123;
    private static final int TRANSITION_REQUEST_CODE = 100;
    private static final int STEP_THRESHOLD = 5; // Solo 5 pasos para activar
    private static final int TIME_THRESHOLD = 3000; // 3 segundos de caminata
    
    private static WalkingOverlayView overlayView;
    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent transitionPendingIntent;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private int stepCount = 0;
    private long lastStepTime = 0;
    private boolean isWalkingDetected = false;
    private long walkingStartTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        activityRecognitionClient = ActivityRecognition.getClient(this);
        
        // Inicializar sensor de pasos para detección más rápida
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (stepSensor != null) {
                sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.w(TAG, "Step counter sensor not available");
            }
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Notification notification = createNotification();
            startForeground(NOTIFICATION_ID, notification);
            
            startActivityRecognition();
            
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage());
            return START_NOT_STICKY;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopActivityRecognition();
        hideOverlay(this);
        
        // Liberar sensor
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
    
    // SensorEventListener methods
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            long currentTime = System.currentTimeMillis();
            
            // Detectar si son pasos consecutivos (caminando)
            if (currentTime - lastStepTime < 1000) { // Menos de 1 segundo entre pasos
                stepCount++;
                
                if (stepCount >= STEP_THRESHOLD && !isWalkingDetected) {
                    // Usuario está caminando
                    isWalkingDetected = true;
                    walkingStartTime = currentTime;
                    Log.d(TAG, "Walking detected by step counter - showing overlay");
                    showOverlay(this);
                }
            } else {
                // Resetear contador si pasó mucho tiempo entre pasos
                stepCount = 0;
                
                // Si ya estaba caminando y pasó el tiempo threshold, ocultar overlay
                if (isWalkingDetected && currentTime - walkingStartTime > TIME_THRESHOLD) {
                    isWalkingDetected = false;
                    Log.d(TAG, "Walking stopped - hiding overlay");
                    hideOverlay(this);
                }
            }
            
            lastStepTime = currentTime;
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No necesario
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
        Intent intent = new Intent(this, cordova.getActivity().getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeWalk Activado")
            .setContentText("Monitoreando actividad de caminata")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private void startActivityRecognition() {
        try {
            List<ActivityTransition> transitions = new ArrayList<>();
            
            transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
            
            transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());
            
            // Añadir detección de movimiento también
            transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.ON_FOOT)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
            
            ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
            
            Intent intent = new Intent(this, ActivityTransitionReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            transitionPendingIntent = PendingIntent.getBroadcast(this, TRANSITION_REQUEST_CODE, intent, flags);
            
            Task<Void> task = activityRecognitionClient.requestActivityTransitionUpdates(request, transitionPendingIntent);
            
            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d(TAG, "Activity transition updates registered successfully");
                }
            });
            
            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(TAG, "Error registering activity transition updates: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception in startActivityRecognition: " + e.getMessage());
        }
    }
    
    private void stopActivityRecognition() {
        if (transitionPendingIntent != null) {
            activityRecognitionClient.removeActivityTransitionUpdates(transitionPendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Activity transition updates removed successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error removing activity transition updates: " + e.getMessage());
                    }
                });
        }
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
    
    public static void handleActivityTransition(Context context, Intent intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            
            for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                Log.d(TAG, "Activity: " + event.getActivityType() + ", Transition: " + event.getTransitionType());
                
                if (event.getActivityType() == DetectedActivity.WALKING || 
                    event.getActivityType() == DetectedActivity.ON_FOOT) {
                    
                    if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        Log.d(TAG, "Walking/On Foot detected - showing overlay");
                        showOverlay(context);
                    } else if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                        Log.d(TAG, "Walking/On Foot stopped - hiding overlay");
                        hideOverlay(context);
                    }
                }
            }
        }
    }
}