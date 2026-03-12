package com.cardcounter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 屏幕截图管理器
 * 用于截取屏幕内容供 OCR 识别
 */
public class ScreenCaptureManager {

    private static final int SCREEN_CAPTURE_REQUEST_CODE = 1002;
    private static ScreenCaptureManager instance;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ScreenCaptureCallback callback;
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);

    public interface ScreenCaptureCallback {
        void onCaptureReady(Bitmap bitmap);
        void onCaptureError(String error);
    }

    public static ScreenCaptureManager getInstance() {
        if (instance == null) {
            instance = new ScreenCaptureManager();
        }
        return instance;
    }

    public void init(Context context) {
        projectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    public void requestPermission(Activity activity) {
        if (projectionManager == null) {
            init(activity);
        }
        Intent intent = projectionManager.createScreenCaptureIntent();
        activity.startActivityForResult(intent, SCREEN_CAPTURE_REQUEST_CODE);
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (callback != null) {
                callback.onCaptureReady(null); // 权限获取成功
            }
        }
    }

    public void startCapture(Context context, ScreenCaptureCallback cb) {
        this.callback = cb;

        if (mediaProjection == null) {
            callback.onCaptureError("请先授予截屏权限");
            return;
        }

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, null
        );

        isCapturing.set(true);
        startReadingImage();
    }

    private void startReadingImage() {
        new Thread(() -> {
            while (isCapturing.get() && imageReader != null) {
                try {
                    Image image = imageReader.acquireLatestImage();
                    if (image != null) {
                        Bitmap bitmap = imageToBitmap(image);
                        image.close();

                        if (bitmap != null && callback != null) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                callback.onCaptureReady(bitmap);
                            });
                        }
                        break; // 只截取一次
                    }
                    Thread.sleep(100);
                } catch (Exception e) {
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            callback.onCaptureError(e.getMessage());
                        });
                    }
                    break;
                }
            }
        }).start();
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            Bitmap bitmap = Bitmap.createBitmap(
                    image.getWidth() + rowPadding / pixelStride,
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void stopCapture() {
        isCapturing.set(false);
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader = null;
        }
    }

    public boolean hasPermission() {
        return mediaProjection != null;
    }

    public void release() {
        stopCapture();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}
