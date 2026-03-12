package com.cardcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机启动和应用保活接收器
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            Log.d(TAG, "收到广播: " + action);

            if (action == null) return;

            // 开机完成
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                Log.d(TAG, "开机完成，不自动启动服务（避免打扰）");
            }

            // 用户解锁手机
            if (Intent.ACTION_USER_PRESENT.equals(action)) {
                Log.d(TAG, "用户解锁手机");
                // 如果服务之前在运行，尝试恢复
                if (FloatWindowService.isRunning()) {
                    // 服务已在运行，无需处理
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "处理广播异常: " + e.getMessage());
        }
    }
}
