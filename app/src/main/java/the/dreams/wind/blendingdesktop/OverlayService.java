package the.dreams.wind.blendingdesktop;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import java.util.Objects;

import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

public class OverlayService extends Service implements ScreenshotMaker.Callback,
        View.OnTouchListener {
    final static String INTENT_ACTION_START_OVERLAY = "INTENT_ACTION_START_OVERLAY";
    final static String INTENT_KEY_SCREEN_CAST_DATA = "INTENT_KEY_SCREEN_CAST_DATA";

    private final static int sNotificationId = 0xFFFF;
    private final static String sNotificationChannelId = "overlay_notification_channel_id";
    private final static int sRestartDelay = 1024;
    private final static int sIdleTimeout = (int) (1024 * 1.5f);
    private final static int sFadeInOutDuration = 512;

    private ScreenshotMaker mScreenshotMaker;
    private OverlayImageView mImageView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mOverlayLayoutParams;
    private Handler mRestartHandler;
    private Runnable mRestartRunnable;
    private Handler mIdleHandler;
    private Runnable mIdleRunnable;
    private ViewPropertyAnimator mOverlayAnimator;

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
        Objects.requireNonNull(projectionManager).createScreenCaptureIntent();
        Notification mNotification = makeNotificationBuilder()
                .setTicker(getText(R.string.notification_ticker))
                .build();
        startForeground(sNotificationId, mNotification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (INTENT_ACTION_START_OVERLAY.equals(intent.getAction())) {
            addOverlay();
            Intent screenCastData = intent.getParcelableExtra(INTENT_KEY_SCREEN_CAST_DATA);
            mScreenshotMaker = new ScreenshotMaker(this, screenCastData);
            mScreenshotMaker.takeScreenshot(this);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        restartOverlay();
        mScreenshotMaker.release();
    }

    // ========================================== //
    // ScreenshotMaker.Callback
    // ========================================== //

    @Override
    public void onScreenshotTaken(Bitmap bitmap) {
        mImageView.setVisibility(View.VISIBLE);
        showDesktopScreenshot(bitmap, mImageView);
        fadeOverlay(true, null);
    }

    // ========================================== //
    // View.OnTouchListener
    // ========================================== //

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        // Just for to silence lint warnings
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                if (event.getEventTime() - event.getDownTime() < 512) {
                    view.performClick();
                }
                break;
        }
        restartIdleState();
        return true;
    }

    // ========================================== //
    // Private
    // ========================================== //

    private void restartIdleState() {
        if (mIdleHandler == null) {
            mIdleHandler = new Handler();
        }
        if (mIdleRunnable == null) {
            mIdleRunnable = new Runnable() {
                @Override
                public void run() {
                    mScreenshotMaker.takeScreenshot(OverlayService.this);
                }
            };
        }
        mIdleHandler.removeCallbacks(mIdleRunnable);
        if (mImageView.getAlpha() > 0) {
            fadeOverlay(false, new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mIdleHandler.postDelayed(mIdleRunnable, sIdleTimeout);
                }
            });
        } else {
            mIdleHandler.postDelayed(mIdleRunnable, sIdleTimeout);
        }
    }

    private void restartOverlay() {
        // Halt all fading animations and hide the view
        if (mOverlayAnimator != null) {
            mOverlayAnimator.cancel();
        }
        if (mIdleHandler != null) {
            mIdleHandler.removeCallbacks(mIdleRunnable);
        }
        if (mImageView != null) {
            mImageView.setVisibility(View.INVISIBLE);
        }

        // Lazy init
        if (mRestartHandler == null) {
            mRestartHandler = new Handler();
        }
        if (mRestartRunnable == null) {
            mRestartRunnable = new Runnable() {
                @Override
                public void run() {
                    Context appContext = getApplicationContext();
                    Intent mainActivityIntent = new Intent(appContext, MainActivity.class);
                    mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    appContext.startActivity(mainActivityIntent);
                }
            };
        }

        // There is a need to wait for a short wile until screen rotation is finished
        mRestartHandler.removeCallbacks(mRestartRunnable);
        mRestartHandler.postDelayed(mRestartRunnable, sRestartDelay);
    }

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
            //noinspection deprecation
            return new Notification.Builder(this);
        }
    }

    private WindowManager.LayoutParams overlayLayoutParams() {
        final int overlayFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            // TYPE_SYSTEM_ALERT is the closest one to TYPE_APPLICATION_OVERLAY flag for
            // pre-O android OSes
            overlayFlag = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        final int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayFlag,
                flags,
                PixelFormat.TRANSLUCENT);
    }

    private void addOverlay() {
        if (mImageView != null && mImageView.isAttachedToWindow()) {
            mWindowManager.removeView(mImageView);
        }
        mImageView = new OverlayImageView(this);
        mImageView.setOverlayPorterDuffMode(PorterDuff.Mode.MULTIPLY);
        mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mImageView.setPadding(0,0,0,0);
        mImageView.setOverlayColor(0xAAF5961B);
        mImageView.setAlpha(0f);
        mImageView.setOnTouchListener(this);
        mImageView.setVisibility(View.INVISIBLE);
        mWindowManager.addView(mImageView, mOverlayLayoutParams);
    }

    private void fadeOverlay(boolean in, Animator.AnimatorListener animatorListener) {
        if (mOverlayAnimator != null) {
            mOverlayAnimator.cancel();
        }
        mOverlayAnimator = mImageView.animate().alpha(in ? 1.0f : 0f)
                .setDuration(sFadeInOutDuration)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(animatorListener);
    }

    private void showDesktopScreenshot(Bitmap screenshot, ImageView imageView) {
        // The goal is to position the bitmap such it is attached to top of the screen display, by
        // moving it under status bar and/or navigation buttons bar
        Rect displayFrame = new Rect();
        imageView.getWindowVisibleDisplayFrame(displayFrame);
        final int statusBarHeight = displayFrame.top;
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        Matrix imageMatrix = new Matrix();
        imageMatrix.setTranslate(-displayFrame.left, -statusBarHeight);
        imageView.setImageMatrix(imageMatrix);
        imageView.setImageBitmap(screenshot);
    }
}
