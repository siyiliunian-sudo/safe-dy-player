package com.xiazongji.littlestar;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/** ImageView that keeps the 4:5 card ratio used by the search grid. */
public final class AspectImageView extends ImageView {
    private static final float HEIGHT_RATIO = 1.26f;

    public AspectImageView(Context context) {
        super(context);
    }

    public AspectImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        if (width > 0) {
            setMeasuredDimension(width, Math.round(width * HEIGHT_RATIO));
        }
    }
}
