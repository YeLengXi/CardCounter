package com.cardcounter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 主Activity - 带自动识别功能
 */
public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_SCREEN_CAPTURE = 1002;

    private Switch switchService;
    private TextView tvStatus;
    private Button btnReset;
    private Button btnCapturePermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化截屏管理器
        ScreenCaptureManager.getInstance().init(this);
        CardRecognizer.getInstance().init();

        // 检查权限
        if (!hasOverlayPermission()) {
            showPermissionDialog();
        }

        initViews();
    }

    private void initViews() {
        switchService = findViewById(R.id.switch_service);
        tvStatus = findViewById(R.id.tv_status);
        btnReset = findViewById(R.id.btn_reset);
        btnCapturePermission = findViewById(R.id.btn_capture_permission);

        // 开关切换
        switchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startFloatService();
            } else {
                stopFloatService();
            }
        });

        // 重置按钮
        btnReset.setOnClickListener(v -> {
            CardDataManager.getInstance().reset();
            Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show();
        });

        // 截屏权限按钮
        if (btnCapturePermission != null) {
            updateCapturePermissionButton();
            btnCapturePermission.setOnClickListener(v -> {
                ScreenCaptureManager.getInstance().requestPermission(MainActivity.this);
            });
        }

        updateServiceStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        if (btnCapturePermission != null) {
            updateCapturePermissionButton();
        }
    }

    private void updateCapturePermissionButton() {
        boolean hasPermission = ScreenCaptureManager.getInstance().hasPermission();
        if (btnCapturePermission != null) {
            btnCapturePermission.setText(hasPermission ? "✓ 截屏权限已授予" : "授予截屏权限");
            btnCapturePermission.setEnabled(!hasPermission);
        }
    }

    private void updateServiceStatus() {
        boolean isRunning = FloatWindowService.isRunning();
        tvStatus.setText(isRunning ? "● 运行中" : "● 已停止");
        tvStatus.setTextColor(isRunning ? 0xFF27AE60 : 0xFF95A5A6);
        switchService.setChecked(isRunning);
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void showPermissionDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("需要悬浮窗权限")
                .setMessage("请开启悬浮窗权限以使用记牌器功能")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                })
                .setNegativeButton("退出", (dialog, which) -> {
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (!hasOverlayPermission()) {
                Toast.makeText(this, "未授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == REQUEST_SCREEN_CAPTURE) {
            // 处理截屏权限结果
            ScreenCaptureManager.getInstance().handleActivityResult(requestCode, resultCode, data);
            updateCapturePermissionButton();

            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "截屏权限已授予，自动识别功能已启用", Toast.LENGTH_SHORT).show();

                // 如果服务正在运行，通知其启用自动识别
                if (FloatWindowService.isRunning()) {
                    FloatWindowService service = FloatWindowService.getInstance();
                    if (service != null) {
                        service.enableAutoCapture();
                    }
                }
            }
        }
    }

    private void startFloatService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            switchService.setChecked(false);
            return;
        }

        // 检查是否有截屏权限
        if (!ScreenCaptureManager.getInstance().hasPermission()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("需要截屏权限")
                    .setMessage("自动识别牌面功能需要截屏权限，是否现在授予？")
                    .setPositiveButton("授予", (dialog, which) -> {
                        ScreenCaptureManager.getInstance().requestPermission(MainActivity.this);
                        switchService.setChecked(false);
                    })
                    .setNegativeButton("稍后", (dialog, which) -> {
                        // 不授予权限也启动服务，但无法自动识别
                        startServiceOnly();
                    })
                    .show();
            return;
        }

        startServiceOnly();
    }

    private void startServiceOnly() {
        Intent intent = new Intent(this, FloatWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateServiceStatus();
    }

    private void stopFloatService() {
        Intent intent = new Intent(this, FloatWindowService.class);
        stopService(intent);
        updateServiceStatus();
    }
}
