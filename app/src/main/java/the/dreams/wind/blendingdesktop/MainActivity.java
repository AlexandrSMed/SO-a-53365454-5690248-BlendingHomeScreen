package the.dreams.wind.blendingdesktop;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.widget.Toast;

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case sPermissionRequestCode:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    // For the OS versions lower than M there should not be the case that it reached
                    // this part of code (they don't ask for this permission)
                    return;
                }
                if (Settings.canDrawOverlays(this)) {
                    requestScreenCapture();
                } else {
                    finishWithMessage(R.string.error_permission_overlay);
                }
                break;
            case sScreenRecordingRequestCode:
                if (resultCode == RESULT_OK) {
                    launchOverlay(data);
                } else {
                    finishWithMessage(R.string.error_permission_screen_capture);
                }
                break;
        }
    }

    // ========================================== //
    // Private
    // ========================================== //

    private void finishWithMessage(@StringRes int resourceId) {
        Toast.makeText(this, resourceId, Toast.LENGTH_LONG).show();
        finish();
    }

    private void requestScreenCapture() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = Objects.requireNonNull(mediaProjectionManager).createScreenCaptureIntent();
        startActivityForResult(intent, sScreenRecordingRequestCode);
    }

    private void launchOverlay(@NonNull Intent screenCastData) {
        Intent toOverlayService = new Intent(this, OverlayService.class);
        toOverlayService.setAction(OverlayService.INTENT_ACTION_START_OVERLAY);
        toOverlayService.putExtra(OverlayService.INTENT_KEY_SCREEN_CAST_DATA, screenCastData);
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
