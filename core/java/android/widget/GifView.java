/*
 * Copyright (C) 2014 ParanoidAndroid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.util.AttributeSet;

import java.io.InputStream;

import com.android.internal.R;

/**
 * Hide from public API
 * @hide
 */
public class GifView extends View {

    private static final int DEFAULT_DURATION = 1000; // 1 second

    private int mMovieResourceId = -1;
    private Movie mMovie;

    private long mMovieStart;
    private int mCurrentAnimationTime;

    private float mLeft;
    private float mTop;
    private float mScale;

    private int mMeasuredMovieWidth;
    private int mMeasuredMovieHeight;

    public GifView(Context context) {
        this(context, null);
    }

    public GifView(Context context, AttributeSet attrs) {
        this(context, attrs, R.styleable.CustomTheme_gifViewStyle);
    }

    public GifView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setViewAttributes(context, attrs, defStyle);
    }

    public void setGifResource(InputStream gifStream) {
        mMovieResourceId = 0;
        mMovie = Movie.decodeStream(gifStream);
        requestLayout();
    }

    private void setViewAttributes(Context context, AttributeSet attrs, int defStyle) {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        if (attrs != null) {
            final TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.GifView, defStyle,
                0); //TODO set a sane defualt

            mMovieResourceId = array.getResourceId(R.styleable.GifView_gif, -1);
            array.recycle();
        }

        if (mMovieResourceId != -1) {
            mMovie = Movie.decodeStream(getResources().openRawResource(mMovieResourceId));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        if (mMovie != null) {
            int movieWidth = mMovie.width();
            int movieHeight = mMovie.height();

            float scaleH = 1f;
            int measureModeWidth = MeasureSpec.getMode(widthMeasureSpec);

            if (measureModeWidth != MeasureSpec.UNSPECIFIED) {
                int maximumWidth = MeasureSpec.getSize(widthMeasureSpec);
                if (movieWidth > maximumWidth) {
                    scaleH = (float) movieWidth / (float) maximumWidth;
                }
            }

            float scaleW = 1f;
            int measureModeHeight = MeasureSpec.getMode(heightMeasureSpec);

            if (measureModeHeight != MeasureSpec.UNSPECIFIED) {
                int maximumHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (movieHeight > maximumHeight) {
                    scaleW = (float) movieHeight / (float) maximumHeight;
                }
            }

            mScale = 1f / Math.max(scaleH, scaleW);

            mMeasuredMovieWidth = (int) (movieWidth * mScale);
            mMeasuredMovieHeight = (int) (movieHeight * mScale);

            setMeasuredDimension(mMeasuredMovieWidth, mMeasuredMovieHeight);

        } else {
            setMeasuredDimension(getSuggestedMinimumWidth(), getSuggestedMinimumHeight());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mLeft = (getWidth() - mMeasuredMovieWidth) / 2f;
        mTop = (getHeight() - mMeasuredMovieHeight) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mMovie != null) {
            updateAnimationTime();
            drawMovieFrame(canvas);
            postInvalidateOnAnimation();
        }
    }

    private void updateAnimationTime() {
        long now = android.os.SystemClock.uptimeMillis();

        if (mMovieStart == 0) {
            mMovieStart = now;
        }

        int dur = mMovie.duration();

        if (dur == 0) {
            dur = DEFAULT_DURATION;
        }

        mCurrentAnimationTime = (int) ((now - mMovieStart) % dur);
    }

    private void drawMovieFrame(Canvas canvas) {
        mMovie.setTime(mCurrentAnimationTime);
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.scale(mScale, mScale);
        mMovie.draw(canvas, mLeft / mScale, mTop / mScale);
        canvas.restore();
    }
}
