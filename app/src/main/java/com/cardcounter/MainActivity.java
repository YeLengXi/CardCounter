package com.cardcounter;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
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

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private Switch switchService;
    private TextView tvStatus;
    private Button btnReset;
    private Button btnAccessibility;
    private TextView tvAccessibilityStatus;
    private Button btnDebug;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ScreenCaptureManager.getInstance().init(this);
        CardRecognizer.getInstance().init();

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
        btnDebug = findViewById(R.id.btn_debug);

        if (switchService != null) {
            switchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    startFloatService();
                } else {
                    stopFloatService();
                }
            });
        }

        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                CardDataManager.getInstance().reset();
                if (CardAccessibilityService.isEnabled()) {
                    CardAccessibilityService.getInstance().resetGame();
                }
                Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnAccessibility != null) {
            updateAccessibilityButton();
            btnAccessibility.setOnClickListener(v -> openAccessibilitySettings());
        }

        if (btnDebug != null) {
            btnDebug.setOnClickListener(v -> {
                if (CardAccessibilityService.isEnabled()) {
                    CardAccessibilityService.getInstance().dumpCurrentText();
                    Toast.makeText(this, "日志已输出", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                }
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
            btnAccessibility.setText(isEnabled ? "已开启" : "开启无障碍服务");
            btnAccessibility.setEnabled(!isEnabled);
        }
        if (tvAccessibilityStatus != null) {
            tvAccessibilityStatus.setText(isEnabled ? "自动识别：已启用" : "自动识别：未启用");
            tvAccessibilityStatus.setTextColor(isEnabled ? 0xFF27AE60 : 0xFFE67E22);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            String packageName = getPackageName();
            String serviceName = CardAccessibilityService.class.getName();

            List<AccessibilityServiceInfo> enabledServices = ((android.view.accessibility.AccessibilityManager)
                    getSystemService(Context.ACCESSIBILITY_SERVICE))
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

            for (AccessibilityServiceInfo service : enabledServices) {
                String id = service.getId();
                if (id != null && id.contains(packageName) && id.contains(serviceName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private void openAccessibilitySettings() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("开启无障碍服务")
                .setMessage("开启无障碍服务后，记牌器可以自动识别游戏中的牌面。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateServiceStatus() {
        boolean isRunning = FloatWindowService.isRunning();
        tvStatus.setText(isRunning ? "运行中" : "已停止");
        tvStatus.setTextColor(isRunning ? 0xFF27AE60 : 0xFF95A5A6);
        if (switchService != null) {
            switchService.setChecked(isRunning);
        }
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
                .setNegativeButton("退出", (dialog, which) -> finish())
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
            } else {
                updateServiceStatus();
            }
        }
    }

    private void startFloatService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            if (switchService != null) {
                switchService.setChecked(false);
            }
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("建议开启无障碍服务")
                    .setMessage("无障碍服务可以让记牌器自动识别打出的牌面")
                    .setPositiveButton("去开启", (dialog, which) -> {
                        openAccessibilitySettings();
                        if (switchService != null) {
                            switchService.setChecked(false);
                        }
                    })
                    .setNegativeButton("稍后", (dialog, which) -> {
                        startServiceOnly();
                    })
                    .setCancelable(false)
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
