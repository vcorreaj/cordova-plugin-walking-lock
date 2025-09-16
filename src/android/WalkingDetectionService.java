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
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

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
    
    // Detección por acelerómetro
    private static  float MOVEMENT_THRESHOLD = 2.5f; // Umbral de movimiento
    private static  int STEP_DETECTION_THRESHOLD = 3; // Pasos para activar
    private static final long MOVEMENT_TIMEOUT = 3000; // 3 segundos sin movimiento
    
    private static int movementCount = 0;
    private static int totalMovements = 0;
    private static boolean isMoving = false;
    private static long lastMovementTime = 0;
    private static long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 500; // Actualizar cada 500ms
    
    private static WalkingOverlayView overlayView;
    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent transitionPendingIntent;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    
    private static Handler stepsHandler;
    private float[] lastAcceleration = new float[3];
    private float[] currentAcceleration = new float[3];
    private long lastSensorTime = 0;
    private static boolean isManuallyUnlocked = false;
    private BroadcastReceiver manualUnlockReceiver;
 
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        activityRecognitionClient = ActivityRecognition.getClient(this);
        
        initializeAccelerometer();
        
        // Registrar receiver para desbloqueo manual
        manualUnlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("MANUAL_UNLOCK_ACTION".equals(intent.getAction())) {
                    Log.d(TAG, "Recibido broadcast de desbloqueo manual");
                    setManualUnlock(true);
                    isMoving = false;
                    movementCount = 0;
                }
            }
        };
        
        IntentFilter filter = new IntentFilter("MANUAL_UNLOCK_ACTION");
        registerReceiver(manualUnlockReceiver, filter);
    }
   public static void setSensitivity(float movementThreshold, int stepThreshold) {
    MOVEMENT_THRESHOLD = movementThreshold;
    STEP_DETECTION_THRESHOLD = stepThreshold;
    Log.d(TAG, "Sensibilidad actualizada - Umbral: " + MOVEMENT_THRESHOLD + ", Pasos: " + STEP_DETECTION_THRESHOLD);
}

public static float getMovementThreshold() {
    return MOVEMENT_THRESHOLD;
}

public static int getStepDetectionThreshold() {
    return STEP_DETECTION_THRESHOLD;
}
    public static void setManualUnlock(boolean unlocked) {
        isManuallyUnlocked = unlocked;
        Log.d(TAG, "Desbloqueo manual: " + (unlocked ? "ACTIVADO" : "DESACTIVADO"));
    }

    public static boolean isManuallyUnlocked() {
        return isManuallyUnlocked;
    }
    
    private void initializeAccelerometer() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                Log.d(TAG, "Acelerómetro registrado correctamente");
            } else {
                Log.e(TAG, "Acelerómetro no disponible en este dispositivo");
            }
        }
    }
    
    public static void setStepsHandler(Handler handler) {
        stepsHandler = handler;
    }
    
    private void sendMovementUpdate() {
        if (stepsHandler != null) {
            Message message = stepsHandler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putInt("movementCount", movementCount);
            bundle.putInt("totalMovements", totalMovements);
            bundle.putBoolean("isMoving", isMoving);
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
        return new MovementBinder();
    }
    
    public class MovementBinder extends android.os.Binder {
        public WalkingDetectionService getService() {
            return WalkingDetectionService.this;
        }
        
        public int getMovementCount() {
            return movementCount;
        }
        
        public int getTotalMovements() {
            return totalMovements;
        }
        
        public boolean isMoving() {
            return isMoving;
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
        
        // Desregistrar receiver
        if (manualUnlockReceiver != null) {
            unregisterReceiver(manualUnlockReceiver);
        }
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long currentTime = System.currentTimeMillis();
            
            // Si el usuario desbloqueó manualmente, no procesar detección de movimiento
            if (isManuallyUnlocked) {
                // Solo verificar si hay movimiento para reactivar el bloqueo
                System.arraycopy(currentAcceleration, 0, lastAcceleration, 0, 3);
                System.arraycopy(event.values, 0, currentAcceleration, 0, 3);
                
                float deltaX = Math.abs(currentAcceleration[0] - lastAcceleration[0]);
                float deltaY = Math.abs(currentAcceleration[1] - lastAcceleration[1]);
                float deltaZ = Math.abs(currentAcceleration[2] - lastAcceleration[2]);
                
                float totalDelta = deltaX + deltaY + deltaZ;
                
                // Si detectamos movimiento significativo, reactivar el bloqueo
                if (totalDelta > MOVEMENT_THRESHOLD && 
                    (currentTime - lastSensorTime) > 200) {
                    
                    movementCount++;
                    totalMovements++;
                    lastMovementTime = currentTime;
                    lastSensorTime = currentTime;
                    
                    Log.d(TAG, "Movimiento detectado durante desbloqueo manual: " + totalDelta);
                    
                    // Si se detecta movimiento suficiente, reactivar el bloqueo
                    if (movementCount >= STEP_DETECTION_THRESHOLD) {
                        Log.d(TAG, "¡Movimiento detectado! Reactivando bloqueo");
                        isManuallyUnlocked = false;
                        isMoving = true;
                        movementCount = 0; // Resetear contador
                        showOverlay(this);
                    }
                }
                
                // Enviar updates periódicamente
                if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
                    sendMovementUpdate();
                    updateNotification();
                    lastUpdateTime = currentTime;
                }
                
                return;
            }
            
            // Resto del código para cuando NO está desbloqueado manualmente
            System.arraycopy(currentAcceleration, 0, lastAcceleration, 0, 3);
            System.arraycopy(event.values, 0, currentAcceleration, 0, 3);
            
            // Calcular la diferencia de aceleración
            float deltaX = Math.abs(currentAcceleration[0] - lastAcceleration[0]);
            float deltaY = Math.abs(currentAcceleration[1] - lastAcceleration[1]);
            float deltaZ = Math.abs(currentAcceleration[2] - lastAcceleration[2]);
            
            float totalDelta = deltaX + deltaY + deltaZ;
            
            // Detectar movimiento significativo
            if (totalDelta > MOVEMENT_THRESHOLD && 
                (currentTime - lastSensorTime) > 200) {
                
                movementCount++;
                totalMovements++;
                lastMovementTime = currentTime;
                lastSensorTime = currentTime;
                
                Log.d(TAG, "Movimiento detectado: " + totalDelta + " - Count: " + movementCount);
                
                // Detectar si está caminando
                if (movementCount >= STEP_DETECTION_THRESHOLD && !isMoving) {
                    isMoving = true;
                    Log.d(TAG, "¡Caminando detectado! Mostrando overlay");
                    showOverlay(this);
                }
            }
            
            // Enviar updates periódicamente
            if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
                sendMovementUpdate();
                updateNotification();
                lastUpdateTime = currentTime;
            }
        }
    }
    
    private void updateNotification() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                Notification notification = createNotification();
                manager.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification: " + e.getMessage());
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
        
        String status = isManuallyUnlocked ? "Desbloqueado Manualmente" : (isMoving ? "Caminando" : "Detenido");
        String notificationText = String.format("Movimientos: %d | Estado: %s", 
            movementCount, status);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeWalk Activado")
            .setContentText(notificationText)
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
        // No mostrar overlay si está desbloqueado manualmente
        if (isManuallyUnlocked) {
            Log.d(TAG, "No mostrar overlay - desbloqueo manual activo");
            return;
        }
        
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
                
                // Si el usuario desbloqueó manualmente, ignorar transiciones
                if (isManuallyUnlocked) {
                    Log.d(TAG, "Ignorando transición - desbloqueo manual activo");
                    continue;
                }
                
                if (event.getActivityType() == DetectedActivity.WALKING || 
                    event.getActivityType() == DetectedActivity.ON_FOOT) {
                    
                    if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        Log.d(TAG, "Walking/On Foot detected - showing overlay");
                        showOverlay(context);
                    }
                }
            }
        }
    }
}