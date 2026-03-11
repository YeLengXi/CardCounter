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
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 悬浮窗服务
 * 可拖拽的顶部悬浮窗记牌器
 */
public class FloatWindowService extends Service {

    private static final String CHANNEL_ID = "CardCounterChannel";
    private static final int NOTIFICATION_ID = 1001;

    private static FloatWindowService instance;
    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams params;

    // 悬浮窗组件
    private TextView tvTotal;
    private TextView tvMini;
    private LinearLayout cardsContainer;

    // 状态
    private boolean isExpanded = true;

    // 拖拽相关
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    private long clickStartTime;
    private long lastClickTime = 0;

    public static FloatWindowService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        try {
            // 先创建通知
            createNotificationChannel();
            Notification notification = createNotification();

            if (notification != null) {
                startForeground(NOTIFICATION_ID, notification);
            } else {
                // 如果通知创建失败，使用默认通知
                startForeground(NOTIFICATION_ID, new Notification());
            }
        } catch (Exception e) {
            // 如果startForeground失败，继续创建悬浮窗
            android.util.Log.e("FloatWindowService", "startForeground error", e);
        }

        // 延迟创建悬浮窗，避免在服务启动时崩溃
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                createFloatWindowSafe();
            }
        }, 100);
    }

    private void createFloatWindowSafe() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            createFloatWindow();
        } catch (Exception e) {
            android.util.Log.e("FloatWindowService", "createFloatWindow error", e);
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
        if (windowManager != null && floatView != null) {
            windowManager.removeView(floatView);
        }
        instance = null;
    }

    /**
     * 创建通知渠道
     */
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
                android.util.Log.e("FloatWindowService", "createNotificationChannel error", e);
            }
        }
    }

    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("记牌器运行中")
                        .setContentText("悬浮窗已启动")
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .setOngoing(true);

                // Android 13+ 需要设置 PendingIntent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 点击通知返回主Activity
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
            android.util.Log.e("FloatWindowService", "createNotification error", e);
        }
        return null;
    }

    /**
     * 创建悬浮窗
     */
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
                params.x = getScreenWidth() - getWindowWidth();
            }
            params.y = savedY;

            // 初始化组件
            initFloatViews();

            // 设置拖拽监听
            setupDragListener();

            // 添加到窗口
            windowManager.addView(floatView, params);

        } catch (Exception e) {
            android.util.Log.e("FloatWindowService", "addView error", e);
            Toast.makeText(this, "悬浮窗添加失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 初始化悬浮窗组件
     */
    private void initFloatViews() {
        tvTotal = floatView.findViewById(R.id.tv_total);
        tvMini = floatView.findViewById(R.id.tv_mini);
        cardsContainer = floatView.findViewById(R.id.cards_container);

        // 更新总数
        if (tvTotal != null) {
            tvTotal.setText(String.valueOf(CardDataManager.getInstance().getTotalCount()));
        }
        if (tvMini != null) {
            tvMini.setText(String.valueOf(CardDataManager.getInstance().getTotalCount()));
        }

        // 展开/收起按钮
        View btnCollapse = floatView.findViewById(R.id.btn_collapse);
        if (btnCollapse != null) {
            btnCollapse.setOnClickListener(v -> {
                isExpanded = false;
                updateExpandState();
            });
        }

        View btnExpand = floatView.findViewById(R.id.btn_expand);
        if (btnExpand != null) {
            btnExpand.setOnClickListener(v -> {
                isExpanded = true;
                updateExpandState();
            });
        }

        // 创建所有牌的按钮
        createCardButtons();

        // 重置按钮
        View btnReset = floatView.findViewById(R.id.btn_reset);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                CardDataManager.getInstance().reset();
                updateAllCards();
            });
        }
    }

    /**
     * 创建牌的按钮
     */
    private void createCardButtons() {
        String[] cards = {"大王", "小王", "2", "A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3"};

        for (String card : cards) {
            View cardView = createCardView(card);
            cardsContainer.addView(cardView);
        }
    }

    /**
     * 创建单个牌的视图
     */
    private View createCardView(String cardName) {
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
            updateTotal();
            vibrate();
        });

        // 长按增加
        view.setOnLongClickListener(v -> {
            CardDataManager.getInstance().addCard(cardName);
            updateCardDisplay(view, cardName, CardDataManager.getInstance().getCardCount(cardName));
            updateTotal();
            return true;
        });

        return view;
    }

    /**
     * 更新单张牌的显示
     */
    private void updateCardDisplay(View view, String cardName, int count) {
        TextView tvName = view.findViewById(R.id.tv_card_name);
        TextView tvCount = view.findViewById(R.id.tv_card_count);

        if (tvCount != null) {
            tvCount.setText(String.valueOf(count));
        }

        int bgColor;
        if (count == 0) {
            bgColor = 0xFF95A5A6;
        } else if (count == 1 && CardDataManager.getInstance().getInitialCount(cardName) > 1) {
            bgColor = 0xFFFFD700;
        } else if (cardName.equals("大王")) {
            bgColor = 0xFFFFE5E5;
        } else if (cardName.equals("小王")) {
            bgColor = 0xFFE5E5FF;
        } else if (cardName.equals("2") || cardName.equals("A")) {
            bgColor = 0xFFFFF5E5;
        } else {
            bgColor = 0xFFF5F5F5;
        }
        view.setBackgroundColor(bgColor);
    }

    /**
     * 更新指定牌的视图
     */
    public void updateCardView(String cardName, int count) {
        if (cardsContainer == null) return;

        for (int i = 0; i < cardsContainer.getChildCount(); i++) {
            View child = cardsContainer.getChildAt(i);
            TextView tvName = child.findViewById(R.id.tv_card_name);
            if (tvName != null && tvName.getText().toString().equals(cardName)) {
                updateCardDisplay(child, cardName, count);
                break;
            }
        }
    }

    /**
     * 更新所有牌
     */
    public void updateAllCards() {
        String[] cards = {"大王", "小王", "2", "A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3"};
        for (String card : cards) {
            updateCardView(card, CardDataManager.getInstance().getCardCount(card));
        }
        updateTotal();
    }

    private void updateTotal() {
        int total = CardDataManager.getInstance().getTotalCount();
        if (tvTotal != null) {
            tvTotal.setText(String.valueOf(total));
        }
        if (tvMini != null) {
            tvMini.setText(String.valueOf(total));
        }
    }

    /**
     * 设置拖拽监听
     */
    private void setupDragListener() {
        if (floatView == null) return;

        floatView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        clickStartTime = System.currentTimeMillis();
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

                        long clickDuration = System.currentTimeMillis() - clickStartTime;
                        if (!isDragging && clickDuration < 300) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastClickTime < 300) {
                                toggleExpand();
                            }
                            lastClickTime = currentTime;
                        }

                        isDragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * 切换展开/收起状态
     */
    private void toggleExpand() {
        isExpanded = !isExpanded;
        updateExpandState();
    }

    /**
     * 更新展开/收起状态
     */
    private void updateExpandState() {
        View expandView = floatView.findViewById(R.id.expand_area);
        View miniView = floatView.findViewById(R.id.mini_area);

        if (expandView != null) {
            expandView.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
        if (miniView != null) {
            miniView.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * 震动反馈
     */
    private void vibrate() {
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && hasVibratePermission()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, 50));
                } else {
                    vibrator.vibrate(50);
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    private boolean hasVibratePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            return vibrator != null;
        }
        return true;
    }

    /**
     * 保存位置
     */
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
            } else {
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
            } else {
                wm.getDefaultDisplay().getSize(size);
                return size.y;
            }
        }
        return 1920;
    }

    private int getWindowWidth() {
        return 300;
    }
}
