package com.cardcounter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.util.Map;

/**
 * 悬浮窗服务 - 增强保活能力
 */
public class FloatWindowService extends Service {

    private static final String TAG = "FloatWindow";
    private static final String CHANNEL_ID = "CardCounterChannel";
    private static final int NOTIFICATION_ID = 1001;

    private static FloatWindowService instance;
    private WindowManager windowManager;
    private View floatView;
    private View dragHandle;
    private WindowManager.LayoutParams params;

    private LinearLayout cardsContainer;
    private Handler uiHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        uiHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "FloatWindowService onCreate");

        // 立即启动前台服务
        createNotificationChannel();
        Notification notification = createNotification();
        try {
            startForeground(NOTIFICATION_ID, notification != null ? notification : new Notification());
            Log.d(TAG, "前台服务已启动");
        } catch (Exception e) {
            Log.e(TAG, "startForeground error", e);
        }

        // 延迟创建悬浮窗
        uiHandler.postDelayed(this::createFloatWindowSafe, 100);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        // 确保前台服务持续运行
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 需要明确声明前台服务类型
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }
        // START_STICKY: 服务被杀死后会自动重启
        // START_REDELIVER_INTENT: 如果服务在处理intent时被杀死，重启后会重新传递intent
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved - 应用被从最近任务中移除");
        // 重新启动服务，保持服务运行
        Intent restartIntent = new Intent(getApplicationContext(), FloatWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onLowMemory() {
        Log.d(TAG, "onLowMemory - 系统内存不足");
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        Log.d(TAG, "onTrimMemory - level: " + level);
        super.onTrimMemory(level);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy - 服务被销毁");
        super.onDestroy();

        try {
            if (windowManager != null && floatView != null) {
                windowManager.removeView(floatView);
            }
        } catch (Exception e) {
            Log.e(TAG, "removeView error", e);
        }

        // 尝试重启服务
        if (instance != null) {
            Intent restartIntent = new Intent(getApplicationContext(), FloatWindowService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent);
            } else {
                startService(restartIntent);
            }
        }

        instance = null;
    }

    public static FloatWindowService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * 处理无障碍服务识别到的牌面
     */
    public void onCardsRecognized(Map<String, Integer> playedCards) {
        if (playedCards == null || playedCards.isEmpty()) {
            return;
        }

        try {
            Log.d(TAG, "收到识别到的牌: " + playedCards);

            CardDataManager dataManager = CardDataManager.getInstance();

            for (Map.Entry<String, Integer> entry : playedCards.entrySet()) {
                String card = entry.getKey();
                int count = entry.getValue();

                for (int i = 0; i < count; i++) {
                    int currentCount = dataManager.getCardCount(card);
                    if (currentCount > 0) {
                        dataManager.removeCard(card);
                        Log.d(TAG, "扣减: " + card + " (剩余: " + dataManager.getCardCount(card) + ")");
                    }
                }
            }

            updateAllCards();

        } catch (Exception e) {
            Log.e(TAG, "onCardsRecognized 异常: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "记牌器",
                        NotificationManager.IMPORTANCE_HIGH  // 提高重要性
                );
                channel.setDescription("记牌器悬浮窗服务 - 保持运行以实现自动记牌");
                channel.setShowBadge(false);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

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
                boolean accessibilityEnabled = CardAccessibilityService.isEnabled();

                Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("🃏 记牌器运行中")
                        .setContentText(accessibilityEnabled ? "自动识别已启用" : "手动模式 - 点击牌面减1")
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .setOngoing(true)  // 不可清除
                        .setAutoCancel(false);

                // Android 13+ 需要设置通知权限
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

    private void createFloatWindowSafe() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            createFloatWindow();
        } catch (Exception e) {
            Log.e(TAG, "createFloatWindow error", e);
            showToast("悬浮窗创建失败: " + e.getMessage());
        }
    }

    private void createFloatWindow() {
        try {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            floatView = inflater.inflate(R.layout.float_window, null);

            dragHandle = floatView.findViewById(R.id.drag_handle);

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
                params.x = getScreenWidth() - 350;
            }
            params.y = savedY;

            initFloatViews();

            // 只在拖拽条上设置拖拽监听
            setupDragListener();

            // 添加到窗口
            windowManager.addView(floatView, params);

            // 设置无障碍服务回调
            setupAccessibilityCallback();

            Log.d(TAG, "悬浮窗创建成功");

        } catch (Exception e) {
            Log.e(TAG, "addView error", e);
            showToast("悬浮窗添加失败: " + e.getMessage());
        }
    }

    /**
     * 设置拖拽监听 - 只在拖拽条上响应
     */
    private void setupDragListener() {
        if (dragHandle == null) {
            Log.w(TAG, "拖拽条未找到");
            return;
        }

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                try {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float deltaX = event.getRawX() - initialTouchX;
                            float deltaY = event.getRawY() - initialTouchY;

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
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            savePosition(params.x, params.y);
                            return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "拖拽异常", e);
                }
                return false;
            }
        });

        Log.d(TAG, "拖拽监听已设置");
    }

    private void setupAccessibilityCallback() {
        try {
            if (CardAccessibilityService.isEnabled()) {
                CardAccessibilityService.getInstance().setCallback(playedCards -> {
                    uiHandler.post(() -> {
                        try {
                            onCardsRecognized(playedCards);
                        } catch (Exception e) {
                            Log.e(TAG, "回调处理异常: " + e.getMessage());
                        }
                    });
                });
                Log.d(TAG, "无障碍服务回调已设置");
            }
        } catch (Exception e) {
            Log.e(TAG, "设置回调异常: " + e.getMessage());
        }
    }

    private void initFloatViews() {
        cardsContainer = floatView.findViewById(R.id.cards_container);

        // 关闭按钮
        View btnClose = floatView.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                try {
                    stopSelf();
                } catch (Exception e) {
                    Log.e(TAG, "停止服务异常", e);
                }
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
            try {
                CardDataManager.getInstance().removeCard(cardName);
                updateCardDisplay(view, cardName, CardDataManager.getInstance().getCardCount(cardName));
            } catch (Exception e) {
                Log.e(TAG, "点击减牌异常", e);
            }
        });

        // 长按增加
        view.setOnLongClickListener(v -> {
            try {
                CardDataManager.getInstance().addCard(cardName);
                updateCardDisplay(view, cardName, CardDataManager.getInstance().getCardCount(cardName));
            } catch (Exception e) {
                Log.e(TAG, "长按加牌异常", e);
            }
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

    public void updateAllCards() {
        if (uiHandler == null || cardsContainer == null) return;

        uiHandler.post(() -> {
            try {
                for (int i = 0; i < cardsContainer.getChildCount(); i++) {
                    View child = cardsContainer.getChildAt(i);
                    TextView tvName = child.findViewById(R.id.tv_card_name);
                    if (tvName != null) {
                        String cardName = tvName.getText().toString();
                        updateCardDisplay(child, cardName, CardDataManager.getInstance().getCardCount(cardName));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "更新界面异常", e);
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

    private void showToast(final String message) {
        if (uiHandler != null) {
            uiHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        }
    }
}
