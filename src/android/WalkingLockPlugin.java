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
        
        return false;
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
            result.put("googlePlayServicesAvailable", isGooglePlayServicesAvailable());
        } catch (JSONException e) {
            callbackContext.error("Error creating response");
            return false;
        }
        callbackContext.success(result);
        return true;
    }
    
    private boolean isTracking(CallbackContext callbackContext) {
        // Este método podría implementarse para verificar si el servicio está corriendo
        JSONObject result = new JSONObject();
        try {
            result.put("isTracking", false); // Implementar lógica real si es necesario
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
            // Para versiones anteriores, iniciar directamente
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
                startDetectionService();
                if (currentCallbackContext != null) {
                    currentCallbackContext.success("Tracking started");
                }
            } else {
                if (currentCallbackContext != null) {
                    currentCallbackContext.error("Activity recognition permission denied");
                }
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
}