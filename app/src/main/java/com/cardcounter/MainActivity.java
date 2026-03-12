package com.cardcounter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * 主Activity - 带无障碍服务自动识别
 */
public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private Switch switchService;
    private TextView tvStatus;
    private Button btnReset;
    private Button btnAccessibility;
    private TextView tvAccessibilityStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化
        ScreenCaptureManager.getInstance().init(this);
        CardRecognizer.getInstance().init();

        // 检查悬浮窗权限
        if (!hasOverlayPermission()) {
            showPermissionDialog();
        }

        initViews();
    }

    private void initViews() {
        switchService = findViewById(R.id.switch_service);
        tvStatus = findViewById(R.id.tv_status);
        btnReset = findViewById(R.id.btn_reset);
        btnAccessibility = findViewById(R.id.btn_accessibility);
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);

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

        // 无障碍服务按钮
        if (btnAccessibility != null) {
            updateAccessibilityButton();
            btnAccessibility.setOnClickListener(v -> {
                openAccessibilitySettings();
            });
        }

        updateServiceStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        if (btnAccessibility != null) {
            updateAccessibilityButton();
        }
    }

    private void updateAccessibilityButton() {
        boolean isEnabled = isAccessibilityServiceEnabled();
        if (btnAccessibility != null) {
            btnAccessibility.setText(isEnabled ? "✓ 无障碍服务已开启" : "开启无障碍服务");
            btnAccessibility.setEnabled(!isEnabled);
        }
        if (tvAccessibilityStatus != null) {
            tvAccessibilityStatus.setText(isEnabled ? "自动识别：已启用" : "自动识别：未启用");
            tvAccessibilityStatus.setTextColor(isEnabled ? 0xFF27AE60 : 0xFFE67E22);
        }
    }

    /**
     * 检查无障碍服务是否启用
     */
    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/.CardAccessibilityService";
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (enabledServices != null) {
            return enabledServices.contains(service);
        }
        return false;
    }

    /**
     * 打开无障碍服务设置页面
     */
    private void openAccessibilitySettings() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("开启无障碍服务")
                .setMessage("开启无障碍服务后，记牌器可以自动识别游戏中的牌面。\n\n1. 在设置中找到「记牌器」\n2. 开启无障碍服务开关")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
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
        }
    }

    private void startFloatService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            switchService.setChecked(false);
            return;
        }

        // 检查无障碍服务
        if (!isAccessibilityServiceEnabled()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("建议开启无障碍服务")
                    .setMessage("无障碍服务可以让记牌器自动识别打出的牌面，无需手动操作。\n\n是否现在开启？")
                    .setPositiveButton("去开启", (dialog, which) -> {
                        openAccessibilitySettings();
                        switchService.setChecked(false);
                    })
                    .setNegativeButton("稍后", (dialog, which) -> {
                        startServiceOnly();
                        Toast.makeText(this, "可以稍后在设置中开启", Toast.LENGTH_LONG).show();
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
