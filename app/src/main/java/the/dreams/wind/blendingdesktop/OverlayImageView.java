package the.dreams.wind.blendingdesktop;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.util.AttributeSet;
import android.widget.ImageView;

class OverlayImageView extends ImageView {
    private Paint mOverlayPaint;

    // ========================================== //
    // Lifecycle
    // ========================================== //
    {
        mOverlayPaint = new Paint();
        mOverlayPaint.setARGB(100, 255, 255, 255);
    }

    public OverlayImageView(Context context) {
        super(context);
    }

    public OverlayImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OverlayImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // ========================================== //
    // Accessors
    // ========================================== //

    void setOverlayColor(int color) {
        mOverlayPaint.setColor(color);
        invalidate();
    }

    void setOverlayPorterDuffMode(PorterDuff.Mode mode) {
        mOverlayPaint.setXfermode(new PorterDuffXfermode(mode));
        invalidate();
    }

    // ========================================== //
    // View
    // ========================================== //


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPaint(mOverlayPaint);
    }
}
