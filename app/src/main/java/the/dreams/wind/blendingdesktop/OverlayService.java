package the.dreams.wind.blendingdesktop;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import java.util.Objects;
import java.util.Random;

import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

public class OverlayService extends Service implements ScreenshotMaker.Callback,
        View.OnTouchListener {
    final static String INTENT_KEY_SCREEN_CAST_DATA = "INTENT_KEY_SCREEN_CAST_DATA";
    final static String INTENT_ACTION_START_OVERLAY = "INTENT_ACTION_START_OVERLAY";
    private final static String INTENT_ACTION_STOP = "INTENT_ACTION_STOP";

    private final static int NOTIFICATION_ID = 0xFFFF;
    private final static String NOTIFICATION_CHANNEL_ID = "overlay_notification_channel_id";
    private final static int RESTART_DELAY = 1024;
    private final static int IDLE_TIMEOUT = (int) (1024 * 1.5f);
    private final static int FADE_IN_OUT_DURATION = 512;
    private final Random mRandom = new Random();

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
        final int smallIconRes = R.drawable.ic_notification_foreground_service;
        final Bitmap largeIcon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_launcher_round);
        Notification mNotification = makeNotificationBuilder()
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.notification_ticker))
                .setSmallIcon(smallIconRes)
                .setLargeIcon(largeIcon)
                .addAction(makeStopServiceAction())
                .build();
        startForeground(NOTIFICATION_ID, mNotification);
    }

    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
        if (INTENT_ACTION_START_OVERLAY.equals(intent.getAction())) {
            addOverlay();
            Intent screenCastData = intent.getParcelableExtra(INTENT_KEY_SCREEN_CAST_DATA);
            mScreenshotMaker = new ScreenshotMaker(this, screenCastData);
            mScreenshotMaker.takeScreenshot(this);
        } else if (INTENT_ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWindowManager.removeView(mImageView);
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
    public boolean onTouch(@NonNull View view, @NonNull MotionEvent event) {
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

    private Notification.Action makeStopServiceAction() {
        Intent stopService = new Intent(getApplicationContext(), OverlayService.class);
        stopService.setAction(INTENT_ACTION_STOP);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, stopService, 0);
        CharSequence actionTitle = getText(R.string.notification_action_stop);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Icon icon = Icon.createWithResource(this, R.mipmap.ic_launcher);
            return new Notification.Action.Builder(icon, actionTitle, pendingIntent).build();
        } else {
            //noinspection deprecation
            return new Notification.Action(R.mipmap.ic_launcher, actionTitle, pendingIntent);
        }
    }

    private void restartIdleState() {
        if (mIdleHandler == null) {
            mIdleHandler = new Handler();
        }
        if (mIdleRunnable == null) {
            mIdleRunnable = () -> mScreenshotMaker.takeScreenshot(OverlayService.this);
        }
        mIdleHandler.removeCallbacks(mIdleRunnable);
        if (mImageView.getAlpha() > 0) {
            fadeOverlay(false, new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mIdleHandler.postDelayed(mIdleRunnable, IDLE_TIMEOUT);
                }
            });
        } else {
            mIdleHandler.postDelayed(mIdleRunnable, IDLE_TIMEOUT);
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
            mRestartRunnable = () -> {
                Context appContext = getApplicationContext();
                Intent mainActivityIntent = new Intent(appContext, MainActivity.class);
                mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(mainActivityIntent);
            };
        }

        // There is a need to wait for a short wile until screen rotation is finished
        mRestartHandler.removeCallbacks(mRestartRunnable);
        mRestartHandler.postDelayed(mRestartRunnable, RESTART_DELAY);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void makeNotificationChannel() {
        CharSequence name = getString(R.string.notification_channel_name);
        String description = getString(R.string.notification_channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
    }

    private Notification.Builder makeNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            makeNotificationChannel();
            return new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
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

        mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mImageView.setPadding(0,0,0,0);

        mImageView.setAlpha(0f);
        mImageView.setOnTouchListener(this);
        mImageView.setVisibility(View.INVISIBLE);
        mWindowManager.addView(mImageView, mOverlayLayoutParams);
    }

    private final static PorterDuff.Mode[] sPorterDuffEffectiveModes = new PorterDuff.Mode[] {
            PorterDuff.Mode.MULTIPLY, PorterDuff.Mode.ADD, PorterDuff.Mode.XOR, PorterDuff.Mode.SRC
    };

    private void fadeOverlay(boolean in, Animator.AnimatorListener animatorListener) {
        if (mOverlayAnimator != null) {
            mOverlayAnimator.cancel();
        }
        if (in) {
            final int color = mRandom.nextInt(0xFFFFFF + 1);
            // alpha is between 30 and 70 % (from 76 to 178)
            final int alpha = mRandom.nextInt(103) + 76 << 24;
            final int argbColor = alpha | color;
            mImageView.setOverlayColor(argbColor);
            final int nextPorterDuffIndex = mRandom.nextInt(sPorterDuffEffectiveModes.length);
            mImageView.setOverlayPorterDuffMode(sPorterDuffEffectiveModes[nextPorterDuffIndex]);
        }
        mOverlayAnimator = mImageView.animate().alpha(in ? 1.0f : 0f)
                .setDuration(FADE_IN_OUT_DURATION)
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
