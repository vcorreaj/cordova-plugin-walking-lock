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
        }
        
        return false;
    }
    
    private boolean startTracking(CallbackContext callbackContext) {
        if (!hasOverlayPermission()) {
            callbackContext.error("Overlay permission not granted");
            return false;
        }
        
        if (!hasActivityRecognitionPermission()) {
            requestActivityRecognitionPermission();
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
        // CORREGIDO: Usar JSONObject para boolean
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
            // CORREGIDO: Usar JSONObject para boolean
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
    
    private void requestActivityRecognitionPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            cordova.requestPermission(
                this, 
                REQUEST_CODE_ACTIVITY_RECOGNITION, 
                Manifest.permission.ACTIVITY_RECOGNITION
            );
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
                // CORREGIDO: Usar JSONObject para boolean
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