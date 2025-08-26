package com.yourcompany.walkinglock;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.MotionEvent;

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
            return;
        }
        
        overlayView = createOverlayView();
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_DIM_BEHIND |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // ¡IMPORTANTE! Bloquea todo táctil
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.9f; // Más oscuro
        params.x = 0;
        params.y = 0;
        
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
        textView.setText("🚷 SafeWalk Activado\n\n" +
                        "¡Estás caminando!\n" +
                        "Pantalla bloqueada por seguridad\n\n" +
                        "El bloqueo se desactivará automáticamente\n" +
                        "cuando dejes de caminar");
        textView.setTextSize(20);
        textView.setTextColor(0xFFFFFFFF);
        textView.setBackgroundColor(0xCC000000); // Fondo más oscuro
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(40, 80, 40, 80);
        textView.setLineSpacing(1.5f, 1.5f);
        
        // Hacer la vista completamente no táctil
        textView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Bloquear todos los eventos táctiles
                return true;
            }
        });
        
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