package the.dreams.wind.blendingdesktop;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import java.util.Objects;

public class MainActivity extends Activity {
    private final static int sPermissionRequestCode = 0x0001;
    private final static int sScreenRecordingRequestCode = 0x0002;

    // ========================================== //
    // Lifecycle
    // ========================================== //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (requestPermissionIfNeeded()) {
            requestScreenCapture();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case sPermissionRequestCode:
                if (Settings.canDrawOverlays(this)) {
                    requestScreenCapture();
                } else {
                    // TODO: show error
                }
            case sScreenRecordingRequestCode:
                if (resultCode == RESULT_OK) {
                    launchOverlay(data);
                } else {
                    // TODO: show error
                }
        }

    }

    // ========================================== //
    // Private
    // ========================================== //

    private void requestScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = Objects.requireNonNull(mediaProjectionManager).createScreenCaptureIntent();
        startActivityForResult(intent, sScreenRecordingRequestCode);
    }

    private void launchOverlay(Intent screenCastData) {
        Intent toOverlayService = new Intent(this, OverlayService.class);
        toOverlayService.putExtra(OverlayService.INTENT_KEY_SCREENCAST_DATA, screenCastData);
        startService(toOverlayService);
        finish();
    }

    /**
     * @return true if explicit request is not needed. false otherwise
     */
    private Boolean requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (Settings.canDrawOverlays(this)) {
            return true;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, sPermissionRequestCode);
        return false;
    }
}
