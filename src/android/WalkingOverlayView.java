package com.yourcompany.walkinglock;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.MotionEvent;
import android.util.Log;

public class WalkingOverlayView {
    private Context context;
    private WindowManager windowManager;
    private View overlayView;
    private static boolean isLocked = true;
    
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
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.9f;
        params.x = 0;
        params.y = 0;
        
        try {
            windowManager.addView(overlayView, params);
            isLocked = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Añadir un callback para notificar cuando se desbloquea manualmente
public interface UnlockListener {
    void onManualUnlock();
}

private static UnlockListener unlockListener;

public static void setUnlockListener(UnlockListener listener) {
    unlockListener = listener;
}

// Modificar el método hide
public void hide() {
    if (overlayView != null) {
        try {
            windowManager.removeView(overlayView);
        } catch (Exception e) {
            e.printStackTrace();
        }
        overlayView = null;
        isLocked = false;
        
        // Notificar que fue desbloqueo manual
        if (unlockListener != null) {
            unlockListener.onManualUnlock();
        }
    }
}
    
    public static boolean isLocked() {
        return isLocked;
    }
    
    private View createOverlayView() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xCC000000);
        
        TextView textView = new TextView(context);
        textView.setText("🚷 SafeWalk Activado\n\n" +
                        "¡Estás caminando!\n" +
                        "Pantalla bloqueada por seguridad\n\n" +
                        "Presiona el botón para desbloquear");
        textView.setTextSize(20);
        textView.setTextColor(0xFFFFFFFF);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(40, 80, 40, 40);
        textView.setLineSpacing(1.5f, 1.5f);
        
        Button unlockButton = new Button(context);
        unlockButton.setText("DESBLOQUEAR");
        unlockButton.setTextSize(18);
        unlockButton.setPadding(60, 30, 60, 30);
        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("WalkingOverlayView", "Botón de desbloqueo presionado");
                hide();
            }
        });
        
        layout.addView(textView);
        layout.addView(unlockButton);
        
        // Bloquear eventos táctiles en toda la pantalla excepto en el botón
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true; // Bloquear todos los eventos táctiles
            }
        });
        
        return layout;
    }
    
    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
    }
    
}