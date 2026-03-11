package com.cardcounter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 主Activity - 简洁版
 */
public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private Switch switchService;
    private TextView tvStatus;
    private Button btnReset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        updateServiceStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
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
