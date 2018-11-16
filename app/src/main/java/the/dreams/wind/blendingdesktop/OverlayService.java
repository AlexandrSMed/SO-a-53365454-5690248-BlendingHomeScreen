package the.dreams.wind.blendingdesktop;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.text.BoringLayout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.Objects;

public class OverlayService extends Service {
    public final static String INTENT_KEY_SCREENCAST_DATA = "INTENT_KEY_SCREENCAST_DATA";

    private final static int sNotificationId = 0xFFFF;
    private final static String sNotificationChannelId = "overlay_notification_channel_id";

    private OverlayImageView mImageView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mOverlayLayoutParams;

    // ========================================== //
    // Lifecycle
    // ========================================== //

    public OverlayService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mOverlayLayoutParams = overlayLayoutParams();
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projectionManager.createScreenCaptureIntent();
        Notification mNotification = makeNotificationBuilder()
                .setTicker(getText(R.string.notification_ticker))
                .build();
        startForeground(sNotificationId, mNotification);
        addOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent screenCastData = intent.getParcelableExtra(INTENT_KEY_SCREENCAST_DATA);
        ScreenshotMaker screenshotMaker = new ScreenshotMaker(this, screenCastData);
        screenshotMaker.takeScreenshot(new ScreenshotMaker.Callback() {
            @Override
            public void onScreenshotTaken(Bitmap bitmap) {
                mImageView.setVisibility(View.VISIBLE);
                showDesktopScreenshot(bitmap, mImageView);
            }
        });
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // ========================================== //
    // Private
    // ========================================== //

    @TargetApi(Build.VERSION_CODES.O)
    private void makeNotificationChannel() {
        CharSequence name = getString(R.string.notification_channel_name);
        String description = getString(R.string.notification_channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(sNotificationChannelId, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
    }

    private Notification.Builder makeNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            makeNotificationChannel();
            return new Notification.Builder(this, sNotificationChannelId);
        } else {
            return new Notification.Builder(this);
        }
    }

    private WindowManager.LayoutParams overlayLayoutParams() {
        final int overlayFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayFlag = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }
        final int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayFlag,
                flags,
                PixelFormat.TRANSLUCENT);
    }

    private void addOverlay() {
        mImageView = new OverlayImageView(this);
        mImageView.setOverlayPorterDuffMode(PorterDuff.Mode.MULTIPLY);
        mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mImageView.setPadding(0,0,0,0);
        mImageView.setOverlayColor(0xAAf5961b);
        mImageView.setVisibility(View.INVISIBLE);
        mWindowManager.addView(mImageView, mOverlayLayoutParams);
    }

    private void showDesktopScreenshot(Bitmap screenshot, ImageView imageView) {
        // The goal is to position the bitmap such it is attached to top of the screen display, by
        // moving it under status bar
        Rect displayFrame = new Rect();
        imageView.getWindowVisibleDisplayFrame(displayFrame);
        final int statusBarHeight = displayFrame.top;
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        Matrix imageMatrix = new Matrix();
        imageMatrix.setTranslate(0, -statusBarHeight);
        imageView.setImageMatrix(imageMatrix);
        imageView.setImageBitmap(screenshot);
    }
}
