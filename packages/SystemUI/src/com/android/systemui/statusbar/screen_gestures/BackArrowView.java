package com.android.systemui.statusbar.screen_gestures;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.util.AttributeSet;
import com.android.systemui.R;

import android.util.Log;
import android.view.View;

public class BackArrowView extends View {

    private static final String TAG = "BackArrowView";

    private int posX = -1;
    private int posY = -1;

    private int vectorSize = 0;
    private int contentsPaddingLeft = 0;
    private int contentsPaddingTop = 0;

    private Path topArch;
    private Path bottomArch;
    private Rect backgroundRect;

    private Paint eraser;
    private Paint painter;

    private Drawable arrowDrawable;

    private boolean isReversed = false;
    private boolean useBlackArrow = false;

    private boolean animating = false;

    public String name = "";

    public BackArrowView(Context context) {
        this(context, null);
    }

    public BackArrowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BackArrowView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BackArrowView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.BackArrowView, defStyleAttr, defStyleRes);
        isReversed = typedArray.getBoolean(R.styleable.BackArrowView_reversed, false);
        useBlackArrow = typedArray.getBoolean(R.styleable.BackArrowView_black_arrow, false);
        typedArray.recycle();

        init();
    }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        eraser = new Paint();
        eraser.setStyle(Paint.Style.FILL);
        eraser.setAntiAlias(true);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        painter = new Paint();
        painter.setColor(Color.BLACK);
        painter.setAntiAlias(true);
        painter.setStyle(Paint.Style.FILL);

        setUseBlackArrow(useBlackArrow);

        contentsPaddingLeft = vectorSize;
        contentsPaddingTop = vectorSize / 2;

        topArch = new Path();
        bottomArch = new Path();
        backgroundRect = new Rect();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isReversed) canvas.scale(-1, 1, getWidth()/2, getHeight()/2);
        super.onDraw(canvas);

        canvas.drawColor(Color.TRANSPARENT);

        if (posX < (-getWidth() + contentsPaddingLeft) || posY < 0) return;

        canvas.drawRect(backgroundRect, painter);
        canvas.drawPath(topArch, eraser);
        canvas.drawPath(bottomArch, eraser);

        arrowDrawable.draw(canvas);
    }

    public void onTouchStarted(int posX, int posY) {
        Log.d(TAG, name + " onTouchStarted: ");
        animating = true;

        reset();

        this.posX = posX;
        this.posY = posY;
    }

    public void onTouchMoved(int x, int y) {
        if (!animating) return;

        this.posX = x;
        this.posY = y;

        // Reverse the X position, to make the 'mirrored' view work
        if (isReversed) this.posX = -posX + getWidth();

        if (posX < (-getWidth() + contentsPaddingLeft)) posX = -getWidth() + contentsPaddingLeft;
        if (posY < contentsPaddingTop) posY = contentsPaddingTop;

        Log.d(TAG, name + " onTouchMoved: X: " + this.posX +", Y: "+this.posY);

        topArch = new Path();
        topArch.moveTo(posX, posY);
        topArch.addArc(posX - getWidth(),
                -1, // Needed to hide small line at the top
                getWidth(),
                posY,
                0,
                180);

        bottomArch = new Path();
        bottomArch.moveTo(posX, posY);

        bottomArch.addArc(posX - getWidth(),
                posY - 10,
                getWidth(),
                getHeight() + posY,
                180,
                270);

        backgroundRect = new Rect( (10 + posX + getWidth() - contentsPaddingLeft) / 2,
                posY / 2,
                getWidth(),
                posY + getHeight()/2);

        int arrowStartX = posX;
        if (posX < 0) arrowStartX = 0; // Don't show the arrow icon outside the drawing rect

        int leftArrowPos = arrowStartX; // Offset the arrow to center it on X position
        arrowDrawable.setBounds(leftArrowPos,
                posY - vectorSize / 2,
                leftArrowPos + vectorSize,
                posY + vectorSize / 2);

        invalidate();
    }

    public void onTouchEnded() {
        Log.d(TAG, name + "onTouchEnded: ");
        animating = false;

        reset();
    }

    private void reset() {
        this.posX = -1;
        this.posY = -1;

        topArch = new Path();
        bottomArch = new Path();
        backgroundRect = new Rect();
        arrowDrawable.setBounds(0, 0, 0, 0);

        invalidate();
    }

    public boolean isReversed() {
        return isReversed;
    }

    public void setReversed(boolean reversed) {
        isReversed = reversed;
    }

    public boolean usesBlackArrow() {
        return useBlackArrow;
    }

    public void setUseBlackArrow(boolean useBlackArrow) {
        this.useBlackArrow = useBlackArrow;

        float density = Resources.getSystem().getDisplayMetrics().density;
        int iconId = useBlackArrow ? R.drawable.ic_back_arrow_black : R.drawable.ic_back_arrow;
        arrowDrawable = VectorDrawableCompat.create(getResources(), iconId, getContext().getTheme());
        vectorSize = (int) (32f * density);
        arrowDrawable.setBounds(0, 0, vectorSize, vectorSize);
    }
}