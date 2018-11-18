package the.dreams.wind.blendingdesktop;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.Objects;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

/**
 * Before using the class actions ensure that all required permissions are set
 * @see <a href="https://stackoverflow.com/a/34549690/5690248">Stack answer</a>
 */
class ScreenshotMaker extends MediaProjection.Callback
        implements ImageReader.OnImageAvailableListener {
    interface Callback {
        void onScreenshotTaken(Bitmap bitmap);
    }

    private final static String sVirtualDisplayName = "virtual_display";

    private final ImageReader mImageReader;
    private final MediaProjection mMediaProjection;
    private final int mScreenDpi;
    private VirtualDisplay mVirtualDisplay;
    private Callback mPendingCallback;

    // ========================================== //
    // Lifecycle
    // ========================================== //

    ScreenshotMaker(Context context, Intent screenCastData) {
        Context appContext = context.getApplicationContext();
        final MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) appContext.getSystemService(MEDIA_PROJECTION_SERVICE);
        mMediaProjection = Objects.requireNonNull(mediaProjectionManager)
                .getMediaProjection(Activity.RESULT_OK, screenCastData);
        mMediaProjection.registerCallback(this, null);

        final WindowManager windowManager =
                (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        final Display defaultDisplay = Objects.requireNonNull(windowManager).getDefaultDisplay();
        Point displaySize = new Point();
        defaultDisplay.getRealSize(displaySize);
        mImageReader = ImageReader.newInstance( displaySize.x, displaySize.y,
                PixelFormat.RGBA_8888, 1);
        mImageReader.setOnImageAvailableListener(this, null);

        final DisplayMetrics displayMetrics = new DisplayMetrics();
        defaultDisplay.getMetrics(displayMetrics);
        mScreenDpi = displayMetrics.densityDpi;
    }

    // ========================================== //
    // Actions
    // ========================================== //

    void takeScreenshot(final Callback callback) {
        prepareVirtualDisplay();
        mPendingCallback = callback;
    }

    void release() {
        mMediaProjection.stop();
        mMediaProjection.unregisterCallback(this);
    }

    // ========================================== //
    // MediaProjection.Callback
    // ========================================== //


    @Override
    public void onStop() {
        super.onStop();
        releaseVirtualDisplay();
    }

    // ========================================== //
    // ImageReader.OnImageAvailableListener
    // ========================================== //

    @Override
    public void onImageAvailable(ImageReader reader) {
        if (mPendingCallback == null) {
            return;
        }
        final Image image = reader.acquireLatestImage();
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        final Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();
        mPendingCallback.onScreenshotTaken(bitmap);
        mPendingCallback = null;
    }

    // ========================================== //
    // Private
    // ========================================== //

    private void prepareVirtualDisplay() {
        if (mVirtualDisplay != null) {
            return;
        }

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(sVirtualDisplayName,
                mImageReader.getWidth(), mImageReader.getHeight(), mScreenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mImageReader.getSurface(),
                new VirtualDisplay.Callback() {
                    @Override
                    public void onStopped() {
                        super.onStopped();
                        releaseVirtualDisplay();
                    }
                }, null);
    }

    private void releaseVirtualDisplay() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }
}
