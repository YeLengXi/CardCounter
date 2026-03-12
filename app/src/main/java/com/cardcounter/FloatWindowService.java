package com.cardcounter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * 悬浮窗服务 - 带自动识别功能
 */
public class FloatWindowService extends Service {

    private static final String TAG = "FloatWindowService";
    private static final String CHANNEL_ID = "CardCounterChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int AUTO_CAPTURE_INTERVAL = 2000; // 每2秒识别一次

    private static FloatWindowService instance;
    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams params;

    private LinearLayout cardsContainer;

    // 自动识别相关
    private boolean autoCaptureEnabled = false;
    private Handler autoCaptureHandler;
    private Runnable autoCaptureRunnable;
    private String lastRecognizedText = "";
    private Bitmap lastCaptureBitmap = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        try {
            createNotificationChannel();
            Notification notification = createNotification();
            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification);
            } else {
                startForeground(NOTIFICATION_ID, new Notification());
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground error", e);
        }

        // 延迟创建悬浮窗
        new Handler().postDelayed(this::createFloatWindowSafe, 100);

        // 初始化自动识别
        initAutoCapture();
    }

    private void createFloatWindowSafe() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            createFloatWindow();
        } catch (Exception e) {
            Log.e(TAG, "createFloatWindow error", e);
            Toast.makeText(this, "悬浮窗创建失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutoCapture();

        if (windowManager != null && floatView != null) {
            windowManager.removeView(floatView);
        }
        instance = null;

        // 释放截屏资源
        ScreenCaptureManager.getInstance().release();
    }

    public static FloatWindowService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * 启用自动识别
     */
    public void enableAutoCapture() {
        if (ScreenCaptureManager.getInstance().hasPermission()) {
            autoCaptureEnabled = true;
            startAutoCapture();
            Log.d(TAG, "自动识别已启用");
        }
    }

    /**
     * 禁用自动识别
     */
    public void disableAutoCapture() {
        autoCaptureEnabled = false;
        stopAutoCapture();
        Log.d(TAG, "自动识别已禁用");
    }

    private void initAutoCapture() {
        autoCaptureHandler = new Handler(Looper.getMainLooper());
        autoCaptureRunnable = new Runnable() {
            @Override
            public void run() {
                if (autoCaptureEnabled) {
                    performAutoCapture();
                    autoCaptureHandler.postDelayed(this, AUTO_CAPTURE_INTERVAL);
                }
            }
        };
    }

    private void startAutoCapture() {
        if (autoCaptureHandler != null && autoCaptureRunnable != null) {
            autoCaptureHandler.post(autoCaptureRunnable);
        }
    }

    private void stopAutoCapture() {
        if (autoCaptureHandler != null && autoCaptureRunnable != null) {
            autoCaptureHandler.removeCallbacks(autoCaptureRunnable);
        }
    }

    private void performAutoCapture() {
        if (!ScreenCaptureManager.getInstance().hasPermission()) {
            return;
        }

        ScreenCaptureManager.getInstance().startCapture(this, new ScreenCaptureManager.ScreenCaptureCallback() {
            @Override
            public void onCaptureReady(Bitmap bitmap) {
                if (bitmap != null) {
                    lastCaptureBitmap = bitmap;
                    recognizeCards(bitmap);
                }
            }

            @Override
            public void onCaptureError(String error) {
                Log.e(TAG, "截图失败: " + error);
            }
        });
    }

    private void recognizeCards(Bitmap bitmap) {
        CardRecognizer.getInstance().recognizeCards(bitmap, new CardRecognizer.CardRecognitionCallback() {
            @Override
            public void onCardsRecognized(Map<String, Integer> playedCards) {
                updateCardsFromRecognition(playedCards);
            }

            @Override
            public void onRecognitionError(String error) {
                Log.e(TAG, "识别失败: " + error);
            }
        });
    }

    private void updateCardsFromRecognition(Map<String, Integer> playedCards) {
        if (playedCards == null || playedCards.isEmpty()) {
            return;
        }

        // 计算需要扣除的牌数
        CardDataManager dataManager = CardDataManager.getInstance();

        for (Map.Entry<String, Integer> entry : playedCards.entrySet()) {
            String card = entry.getKey();
            int recognizedCount = entry.getValue();

            // 获取当前剩余数量
            int currentCount = dataManager.getCardCount(card);
            int initialCount = dataManager.getInitialCount(card);
            int actualPlayedCount = initialCount - currentCount;

            // 如果识别出的数量大于已出牌数，说明有新牌被打出
            if (recognizedCount > actualPlayedCount) {
                int newPlayedCount = recognizedCount - actualPlayedCount;
                for (int i = 0; i < newPlayedCount; i++) {
                    dataManager.removeCard(card);
                }
                Log.d(TAG, "自动扣除: " + card + " x" + newPlayedCount);
            }
        }

        // 更新界面
        updateAllCards();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "记牌器",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("记牌器悬浮窗服务");

                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                Log.e(TAG, "createNotificationChannel error", e);
            }
        }
    }

    private Notification createNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("记牌器运行中")
                        .setContentText(autoCaptureEnabled ? "自动识别已启用" : "点击数字减少")
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .setOngoing(true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent notificationIntent = new Intent(this, MainActivity.class);
                    notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                            this, 0, notificationIntent,
                            android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.setContentIntent(pendingIntent);
                }

                return builder.build();
            }
        } catch (Exception e) {
            Log.e(TAG, "createNotification error", e);
        }
        return null;
    }

    private void createFloatWindow() {
        try {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            floatView = inflater.inflate(R.layout.float_window, null);

            // 获取保存的位置
            SharedPreferences prefs = getSharedPreferences("float_window", Context.MODE_PRIVATE);
            int savedX = prefs.getInt("x", -1);
            int savedY = prefs.getInt("y", 100);

            // 设置布局参数
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.START;
            if (savedX != -1) {
                params.x = savedX;
            } else {
                params.x = getScreenWidth() - 300;
            }
            params.y = savedY;

            initFloatViews();

            // 设置拖拽监听
            setupDragListener();

            // 添加到窗口
            windowManager.addView(floatView, params);

            // 如果有截屏权限，自动启用识别
            if (ScreenCaptureManager.getInstance().hasPermission()) {
                enableAutoCapture();
            }

        } catch (Exception e) {
            Log.e(TAG, "addView error", e);
            Toast.makeText(this, "悬浮窗添加失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initFloatViews() {
        cardsContainer = floatView.findViewById(R.id.cards_container);

        // 关闭按钮
        View btnClose = floatView.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                stopSelf();
            });
        }

        // 创建所有牌的按钮
        String[] cards = {"大王", "小王", "2", "A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3"};

        for (String card : cards) {
            View cardView = createCardView(card);
            cardsContainer.addView(cardView);
        }
    }

    private View createCardView(final String cardName) {
        View view = LayoutInflater.from(this).inflate(R.layout.card_item, null);

        TextView tvName = view.findViewById(R.id.tv_card_name);
        TextView tvCount = view.findViewById(R.id.tv_card_count);

        if (tvName != null) {
            tvName.setText(cardName);
        }

        updateCardDisplay(view, cardName, CardDataManager.getInstance().getCardCount(cardName));

        // 点击减少
        view.setOnClickListener(v -> {
            CardDataManager.getInstance().removeCard(cardName);
            updateCardDisplay(view, cardName, CardDataManager.getInstance().getCardCount(cardName));
        });

        // 长按增加
        view.setOnLongClickListener(v -> {
            CardDataManager.getInstance().addCard(cardName);
            updateCardDisplay(view, cardName, CardDataManager.getInstance().getCardCount(cardName));
            return true;
        });

        return view;
    }

    private void updateCardDisplay(View view, String cardName, int count) {
        TextView tvCount = view.findViewById(R.id.tv_card_count);
        TextView tvName = view.findViewById(R.id.tv_card_name);

        if (tvCount != null) {
            tvCount.setText(String.valueOf(count));
        }

        // 根据数量设置颜色
        int bgColor;
        int countColor;
        if (count == 0) {
            bgColor = 0xFFECF0F1;
            countColor = 0xFFBDC3C7;
        } else if (count == 1 && CardDataManager.getInstance().getInitialCount(cardName) > 1) {
            bgColor = 0xFFFFF59D;
            countColor = 0xFFE74C3C;
        } else if (cardName.equals("大王")) {
            bgColor = 0xFFFFE5E5;
            countColor = 0xFFE74C3C;
        } else if (cardName.equals("小王")) {
            bgColor = 0xFFE5E5FF;
            countColor = 0xFF3498DB;
        } else if (cardName.equals("2") || cardName.equals("A")) {
            bgColor = 0xFFFFF5E5;
            countColor = 0xFFE67E22;
        } else {
            bgColor = 0xFFFFFFFF;
            countColor = 0xFF27AE60;
        }

        view.setBackgroundColor(bgColor);
        if (tvCount != null) {
            tvCount.setTextColor(countColor);
        }
        if (tvName != null) {
            tvName.setTextColor(countColor);
        }
    }

    /**
     * 更新所有牌
     */
    public void updateAllCards() {
        if (cardsContainer == null) return;

        for (int i = 0; i < cardsContainer.getChildCount(); i++) {
            View child = cardsContainer.getChildAt(i);
            TextView tvName = child.findViewById(R.id.tv_card_name);
            if (tvName != null) {
                String cardName = tvName.getText().toString();
                updateCardDisplay(child, cardName, CardDataManager.getInstance().getCardCount(cardName));
            }
        }
    }

    /**
     * 设置拖拽监听
     */
    private void setupDragListener() {
        if (floatView == null) return;

        floatView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;

                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;

                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;

                            // 限制在屏幕范围内
                            int screenWidth = getScreenWidth();
                            int windowHeight = getWindowHeight();
                            int viewWidth = floatView.getWidth();
                            int viewHeight = floatView.getHeight();

                            if (params.x < 0) params.x = 0;
                            if (params.x + viewWidth > screenWidth) params.x = screenWidth - viewWidth;
                            if (params.y < 0) params.y = 0;
                            if (params.y + viewHeight > windowHeight) params.y = windowHeight - viewHeight;

                            windowManager.updateViewLayout(floatView, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        savePosition(params.x, params.y);
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    private void savePosition(int x, int y) {
        SharedPreferences prefs = getSharedPreferences("float_window", Context.MODE_PRIVATE);
        prefs.edit().putInt("x", x).putInt("y", y).apply();
    }

    private int getScreenWidth() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            android.graphics.Point size = new android.graphics.Point();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                wm.getDefaultDisplay().getSize(size);
                return size.x;
            }
        }
        return 1080;
    }

    private int getWindowHeight() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            android.graphics.Point size = new android.graphics.Point();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                wm.getDefaultDisplay().getSize(size);
                return size.y;
            }
        }
        return 1920;
    }
}
