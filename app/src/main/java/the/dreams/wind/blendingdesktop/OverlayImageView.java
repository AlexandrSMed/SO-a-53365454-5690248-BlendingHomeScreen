package the.dreams.wind.blendingdesktop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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

    void setOverlayColor(@SuppressWarnings("SameParameterValue") int color) {
        mOverlayPaint.setColor(color);
        invalidate();
    }

    void setOverlayPorterDuffMode(@SuppressWarnings("SameParameterValue") PorterDuff.Mode mode) {
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

    // For the pruposes of silencing lint warnings all custom views that has custom onTouchListener
    // should override the performClick method
    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
