package com.yourcompany.walkinglock;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.os.Build;

public class WalkingOverlayView {
    private Context context;
    private WindowManager windowManager;
    private View overlayView;
    
    public WalkingOverlayView(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }
    
    public void show() {
        if (overlayView != null) {
            return; // Ya está mostrado
        }
        
        overlayView = createOverlayView();
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.8f;
        
        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void hide() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            overlayView = null;
        }
    }
    
    private View createOverlayView() {
        TextView textView = new TextView(context);
        textView.setText("⚠️ Bloqueo activo: se ha detectado que estás caminando");
        textView.setTextSize(18);
        textView.setTextColor(0xFFFFFFFF);
        textView.setBackgroundColor(0xCCFF0000);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(20, 40, 20, 40);
        
        return textView;
    }
    
    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
    }
}