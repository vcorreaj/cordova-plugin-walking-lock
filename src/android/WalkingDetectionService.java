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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

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
    private static final int STEP_THRESHOLD = 5;
    private static final int TIME_THRESHOLD = 3000;
    
    // Contador de pasos
    private static int totalSteps = 0;
    private static int sessionSteps = 0;
    private static long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 1000; // 1 segundo
    
    private static WalkingOverlayView overlayView;
    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent transitionPendingIntent;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private Sensor stepDetectorSensor;
    private int stepCount = 0;
    private long lastStepTime = 0;
    private boolean isWalkingDetected = false;
    private long walkingStartTime = 0;
    
    // Handler para enviar updates a la actividad
    private static Handler stepsHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        activityRecognitionClient = ActivityRecognition.getClient(this);
        
        initializeStepCounter();
    }
    
    private void initializeStepCounter() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            // Intentar usar STEP_COUNTER (más preciso)
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (stepSensor != null) {
                sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
                Log.d(TAG, "Step counter sensor registered");
            } else {
                // Fallback: STEP_DETECTOR (menos preciso pero más compatible)
                stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
                if (stepDetectorSensor != null) {
                    sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
                    Log.d(TAG, "Step detector sensor registered");
                } else {
                    Log.w(TAG, "No step sensors available");
                }
            }
        }
    }
    
    public static void setStepsHandler(Handler handler) {
        stepsHandler = handler;
    }
    
    private void sendStepsUpdate() {
        if (stepsHandler != null) {
            Message message = stepsHandler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putInt("totalSteps", totalSteps);
            bundle.putInt("sessionSteps", sessionSteps);
            bundle.putBoolean("isWalking", isWalkingDetected);
            message.setData(bundle);
            stepsHandler.sendMessage(message);
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
        return new StepsBinder();
    }
    
    public class StepsBinder extends android.os.Binder {
        public WalkingDetectionService getService() {
            return WalkingDetectionService.this;
        }
        
        public int getTotalSteps() {
            return totalSteps;
        }
        
        public int getSessionSteps() {
            return sessionSteps;
        }
        
        public boolean isWalking() {
            return isWalkingDetected;
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopActivityRecognition();
        hideOverlay(this);
        
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = System.currentTimeMillis();
        
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            // STEP_COUNTER da el total acumulado desde el boot
            int stepsSinceBoot = (int) event.values[0];
            if (totalSteps == 0) {
                totalSteps = stepsSinceBoot;
            } else {
                int newSteps = stepsSinceBoot - totalSteps;
                if (newSteps > 0) {
                    sessionSteps += newSteps;
                    totalSteps = stepsSinceBoot;
                }
            }
        } 
        else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            // STEP_DETECTOR da 1.0 por cada paso
            if (event.values[0] == 1.0f) {
                totalSteps++;
                sessionSteps++;
                
                // Detección rápida de caminata
                if (currentTime - lastStepTime < 1000) {
                    stepCount++;
                    
                    if (stepCount >= STEP_THRESHOLD && !isWalkingDetected) {
                        isWalkingDetected = true;
                        walkingStartTime = currentTime;
                        Log.d(TAG, "Walking detected - showing overlay");
                        showOverlay(this);
                    }
                } else {
                    stepCount = 0;
                }
                
                lastStepTime = currentTime;
            }
        }
        
        // Enviar update cada segundo
        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            sendStepsUpdate();
            lastUpdateTime = currentTime;
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
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
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Mostrar pasos en la notificación
        String notificationText = String.format("Pasos: %d | Sesión: %d", totalSteps, sessionSteps);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeWalk Activado")
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    // Resto del código igual...
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