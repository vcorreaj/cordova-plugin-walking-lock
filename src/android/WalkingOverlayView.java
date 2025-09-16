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
import android.util.TypedValue;
import android.content.Intent;

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
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.CENTER;
        params.dimAmount = 0.9f;
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
        // Crear layout principal
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(Gravity.CENTER);
        mainLayout.setBackgroundColor(0xCC000000);
        
        // TextView con el mensaje
        TextView textView = new TextView(context);
        textView.setText("🚷 SafeWalk Activado\n\n" +
                        "¡Estás caminando!\n" +
                        "Pantalla bloqueada por seguridad\n\n" +
                        "Presiona el botón para desbloquear temporalmente");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        textView.setTextColor(0xFFFFFFFF);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(40, 80, 40, 40);
        textView.setLineSpacing(1.5f, 1.5f);
        
        // Botón de desbloqueo
        Button unlockButton = new Button(context);
        unlockButton.setText("Desbloquear");
        unlockButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        unlockButton.setPadding(40, 20, 40, 20);
        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Desbloquear manualmente
                hide();
                
                // Notificar al servicio que se desbloqueó manualmente
                Intent unlockIntent = new Intent("MANUAL_UNLOCK_ACTION");
                context.sendBroadcast(unlockIntent);
            }
        });
        
        // Añadir vistas al layout
        mainLayout.addView(textView);
        mainLayout.addView(unlockButton);
        
        return mainLayout;
    }
    
    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
    }
}