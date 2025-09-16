package com.yourcompany.walkinglock;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.content.Context;
import android.provider.Settings;
import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.ConnectionResult;

public class WalkingLockPlugin extends CordovaPlugin {
    
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_CODE_ACTIVITY_RECOGNITION = 1002;
    private static final int REQUEST_CODE_BODY_SENSORS = 1003;
    
    private CallbackContext currentCallbackContext;
    private boolean waitingForOverlayPermission = false;
    
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.currentCallbackContext = callbackContext;
        
        if ("startTracking".equals(action)) {
            return startTracking(callbackContext);
        } else if ("stopTracking".equals(action)) {
            return stopTracking(callbackContext);
        } else if ("checkOverlayPermission".equals(action)) {
            return checkOverlayPermission(callbackContext);
        } else if ("requestOverlayPermission".equals(action)) {
            return requestOverlayPermission(callbackContext);
        } else if ("checkPermissions".equals(action)) {
            return checkPermissions(callbackContext);
        } else if ("isTracking".equals(action)) {
            return isTracking(callbackContext);
        }  
        else if ("getStepCount".equals(action)) {
            return getStepCount(callbackContext);
        } else if ("resetStepCount".equals(action)) {
            return resetStepCount(callbackContext);
        }else if ("getMovementData".equals(action)) {
            return getMovementData(callbackContext);
        } else if ("resetMovementCount".equals(action)) {
            return resetMovementCount(callbackContext);
        }else if ("manualUnlock".equals(action)) {
            return manualUnlock(callbackContext);
        }else if ("manualUnlock".equals(action)) {
            return manualUnlock(callbackContext);
        }
        else if ("isManuallyUnlocked".equals(action)) {
            return isManuallyUnlocked(callbackContext);
        }
        else if ("resetManualUnlock".equals(action)) {
            return resetManualUnlock(callbackContext);
        }
        
        return false;
    }
private boolean manualUnlock(CallbackContext callbackContext) {
    Context context = cordova.getActivity().getApplicationContext();
    WalkingDetectionService.setManualUnlock(true);
    WalkingDetectionService.hideOverlay(context);
    
    callbackContext.success("Manually unlocked");
    return true;
}

private boolean isManuallyUnlocked(CallbackContext callbackContext) {
    JSONObject result = new JSONObject();
    try {
        result.put("isManuallyUnlocked", WalkingDetectionService.isManuallyUnlocked());
    } catch (JSONException e) {
        callbackContext.error("Error creating response");
        return false;
    }
    callbackContext.success(result);
    return true;
}

private boolean resetManualUnlock(CallbackContext callbackContext) {
    WalkingDetectionService.setManualUnlock(false);
    callbackContext.success("Manual unlock reset");
    return true;
}
    private boolean startTracking(CallbackContext callbackContext) {
        // Verificar Google Play Services primero
        if (!isGooglePlayServicesAvailable()) {
            callbackContext.error("Google Play Services not available");
            return false;
        }
        
        if (!hasOverlayPermission()) {
            callbackContext.error("Overlay permission not granted");
            return false;
        }
        
        if (!hasActivityRecognitionPermission()) {
            requestActivityRecognitionPermission(callbackContext);
            return true;
        }
        
        if (!hasBodySensorsPermission()) {
            requestBodySensorsPermission(callbackContext);
            return true;
        }
        
        startDetectionService();
        callbackContext.success("Tracking started");
        return true;
    }
    
    private boolean stopTracking(CallbackContext callbackContext) {
        Context context = cordova.getActivity().getApplicationContext();
        Intent serviceIntent = new Intent(context, WalkingDetectionService.class);
        context.stopService(serviceIntent);
        
        WalkingDetectionService.hideOverlay(context);
        
        callbackContext.success("Tracking stopped");
        return true;
    }
    
    private boolean checkOverlayPermission(CallbackContext callbackContext) {
        JSONObject result = new JSONObject();
        try {
            result.put("hasPermission", hasOverlayPermission());
        } catch (JSONException e) {
            callbackContext.error("Error creating response");
            return false;
        }
        callbackContext.success(result);
        return true;
    }
    
    private boolean requestOverlayPermission(CallbackContext callbackContext) {
        if (hasOverlayPermission()) {
            JSONObject result = new JSONObject();
            try {
                result.put("granted", true);
            } catch (JSONException e) {
                callbackContext.error("Error creating response");
                return false;
            }
            callbackContext.success(result);
            return true;
        }
        
        waitingForOverlayPermission = true;
        this.currentCallbackContext = callbackContext;
        
        Intent intent = new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:" + cordova.getActivity().getPackageName())
        );
        
        cordova.startActivityForResult(this, intent, REQUEST_CODE_OVERLAY_PERMISSION);
        return true;
    }
    
    private boolean checkPermissions(CallbackContext callbackContext) {
        JSONObject result = new JSONObject();
        try {
            result.put("overlayPermission", hasOverlayPermission());
            result.put("activityRecognitionPermission", hasActivityRecognitionPermission());
            result.put("bodySensorsPermission", hasBodySensorsPermission());
            result.put("googlePlayServicesAvailable", isGooglePlayServicesAvailable());
        } catch (JSONException e) {
            callbackContext.error("Error creating response");
            return false;
        }
        callbackContext.success(result);
        return true;
    }
    
    private boolean isTracking(CallbackContext callbackContext) {
        JSONObject result = new JSONObject();
        try {
            result.put("isTracking", false);
        } catch (JSONException e) {
            callbackContext.error("Error creating response");
            return false;
        }
        callbackContext.success(result);
        return true;
    }
    
    private boolean hasOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(cordova.getActivity());
        }
        return true;
    }
    
    private boolean hasActivityRecognitionPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return cordova.getActivity().checkSelfPermission(
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    
    private boolean hasBodySensorsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
            return cordova.getActivity().checkSelfPermission(
                Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(cordova.getActivity());
        return resultCode == ConnectionResult.SUCCESS;
    }
    
    private void requestActivityRecognitionPermission(CallbackContext callbackContext) {
        this.currentCallbackContext = callbackContext;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            cordova.requestPermission(
                this, 
                REQUEST_CODE_ACTIVITY_RECOGNITION, 
                Manifest.permission.ACTIVITY_RECOGNITION
            );
        } else {
            startDetectionService();
            callbackContext.success("Tracking started");
        }
    }
    
    private void requestBodySensorsPermission(CallbackContext callbackContext) {
        this.currentCallbackContext = callbackContext;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
            cordova.requestPermission(
                this, 
                REQUEST_CODE_BODY_SENSORS, 
                Manifest.permission.BODY_SENSORS
            );
        } else {
            startDetectionService();
            callbackContext.success("Tracking started");
        }
    }
    
    private void startDetectionService() {
        Context context = cordova.getActivity().getApplicationContext();
        Intent serviceIntent = new Intent(context, WalkingDetectionService.class);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
    
    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode == REQUEST_CODE_ACTIVITY_RECOGNITION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Ahora verificar también permiso de sensores corporales
                if (hasBodySensorsPermission()) {
                    startDetectionService();
                    currentCallbackContext.success("Tracking started");
                } else {
                    requestBodySensorsPermission(currentCallbackContext);
                }
            } else {
                currentCallbackContext.error("Activity recognition permission denied");
            }
        } else if (requestCode == REQUEST_CODE_BODY_SENSORS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDetectionService();
                currentCallbackContext.success("Tracking started");
            } else {
                currentCallbackContext.error("Body sensors permission denied");
            }
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (waitingForOverlayPermission && currentCallbackContext != null) {
                JSONObject result = new JSONObject();
                try {
                    result.put("granted", hasOverlayPermission());
                } catch (JSONException e) {
                    currentCallbackContext.error("Error creating response");
                    return;
                }
                currentCallbackContext.success(result);
                waitingForOverlayPermission = false;
            }
        }
    }
    
        private boolean getStepCount(CallbackContext callbackContext) {
            JSONObject result = new JSONObject();
            try {
                // Aquí implementarías la lógica para obtener los pasos del servicio
                result.put("totalSteps", 0);
                result.put("sessionSteps", 0);
                result.put("isWalking", false);
            } catch (JSONException e) {
                callbackContext.error("Error creating response");
                return false;
            }
            callbackContext.success(result);
            return true;
        }

        private boolean resetStepCount(CallbackContext callbackContext) {
            // Aquí implementarías resetear el contador
            callbackContext.success("Step counter reset");
            return true;
        }
        private boolean getMovementData(CallbackContext callbackContext) {
            JSONObject result = new JSONObject();
            try {
                // Simular datos para demo o implementar conexión real con el servicio
                result.put("movementCount", 15);
                result.put("totalMovements", 150);
                result.put("isMoving", true);
                result.put("sensorAvailable", true);
            } catch (JSONException e) {
                callbackContext.error("Error creating response");
                return false;
            }
            callbackContext.success(result);
            return true;
        }

        private boolean resetMovementCount(CallbackContext callbackContext) {
            callbackContext.success("Movement counter reset");
            return true;
        }
       
}