/*
 * Copyright (C) 2018 Artem Hluhovskyi
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

package com.taphold.camera.camerabutton;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.taphold.camera.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.taphold.camera.CameraPreviewFragment.DELAY_TIME;
import static com.taphold.camera.camerabutton.CameraButton.Action.CLICK;
import static com.taphold.camera.camerabutton.CameraButton.State.DEFAULT;
import static com.taphold.camera.camerabutton.CameraButton.State.EXPANDED;
import static com.taphold.camera.camerabutton.CameraButton.State.PRESSED;
import static com.taphold.camera.camerabutton.CameraButton.State.START_COLLAPSING;
import static com.taphold.camera.camerabutton.CameraButton.State.START_EXPANDING;
import static com.taphold.camera.camerabutton.TypedArrayHelper.getColor;
import static com.taphold.camera.camerabutton.TypedArrayHelper.getColors;
import static com.taphold.camera.camerabutton.TypedArrayHelper.getDimension;
import static com.taphold.camera.camerabutton.TypedArrayHelper.getDrawableResources;
import static com.taphold.camera.camerabutton.TypedArrayHelper.getInteger;

/**
 * Implementation notes:
 * <p>
 * No Java 8 features since library cannot be used when
 * it is included in project without targetCompatibility JAVA_8
 * <p>
 * Some fields/methods don't have private modifier. It allows to prevent generation
 * of additional synthetic methods for accessing such fields/methods from anonymous classes
 * <p>
 * {@link Action} and {@link Mode} uses {@link IntDef} to consume
 * less memory.
 * But {@link State} is declared as enum to allow write exhaustive expressions in Kotlin.
 */
@SuppressWarnings("unused")
public class CameraButton extends View {

    /**
     * Interface used to handle state changes events of the button
     *
     * @see #setOnStateChangeListener(OnStateChangeListener)
     */
    public interface OnStateChangeListener {

        /**
         * Invoked when state of the button is changed
         *
         * @param state new state of the button
         */
        void onStateChanged(@NonNull State state);
    }

    /**
     * Interface used to handle user actions which corresponds to make photo
     */
    public interface OnPhotoEventListener {

        /**
         * Invoked when user interaction with button is treated as "click":
         * - User simply clicks it
         * - User holds it less than {@link #getExpandDelay()}
         * - Button starts expanding but immediately released (holdDuration &lt; expandDelay + expandDuration)
         * - Every interaction when {@link #getMode()} is {@link Mode#PHOTO}
         */
        void onCameraClick();
    }

    /**
     * Interface used to handle user actions which corresponds to take video
     */
    public interface OnVideoEventListener {

        /**
         * Invoked when user interaction with button is treated as "start recording":
         * - User holds it longer than expandDelay + expandDuration
         * - User touch it when {@link #getMode()} is {@link Mode#VIDEO}
         */
        void onVideoStart();

        /**
         * Invoked when user interaction with button is treated as "stop recording"
         * - User release it when button is {@link State#EXPANDED}
         * - User clicks it when button is {@link State#EXPANDED}
         * and {@link #getCollapseAction()} is {@link Action#CLICK}
         */
        void onVideoFinish();

        /**
         * Invoked when video recording is interrupted
         */
        void onVideoCancel();

        void onVideoLongClick();
    }

    /**
     * Interface user to handle video recording progress changes
     */
    public interface OnProgressChangeListener {

        /**
         * Invoked on every tick of video recording where progress = 0f is beginning
         * and progress = 1f is finishing of recording
         *
         * @param progress new progress
         */
        void onProgressChanged(@FloatRange(from = 0, to = 1) float progress);
    }

    /**
     * Describes which type of actions should be handled by button
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Mode.ALL, Mode.PHOTO, Mode.VIDEO})
    public @interface Mode {

        /**
         * Describes mode which handles both photo and video type of actions
         */
        int ALL = 0;

        /**
         * Describes mode which handles only photo actions
         */
        int PHOTO = 1;

        /**
         * Describes mode which handles only video actions
         */
        int VIDEO = 2;
    }

    /**
     * Describes possible user action
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Action.RELEASE, Action.CLICK})
    public @interface Action {
        int RELEASE = 0;
        int CLICK = 1;
    }

    public static final float DEFAULT_GRADIENT_ROTATION_MULTIPLIER = 1.75f;
    public static final float NO_ICON = -1;

    static final String TAG = CameraButton.class.getSimpleName();

    private static final int DEFAULT_MODE = Mode.ALL;
    private static final int DEFAULT_COLLAPSE_ACTION = Action.RELEASE;

    private static final float START_ANGLE = -90f;
    private static final float SWEEP_ANGLE = 360f;
    private static final float TRANSLATION_SCALE_THRESHOLD = 0.4f;

    private final Paint mMainCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    //Sizes
    private int mDefaultWidth;
    private int mDefaultHeight;
    private int mMainCircleRadius;
    private int mMainCircleRadiusExpanded;
    private int mStrokeWidth;
    private int mProgressArcWidth;
    private int mIconSize;

    //Colors
    private int mMainCircleColor;
    private int mMainCircleColorPressed;
    private int mStrokeColor;
    private int mStrokeColorPressed;
    private int[] mProgressArcColors;

    //Durations
    private long mExpandDuration;
    private long mCollapseDuration;
    private long mExpandDelay;
    private long mVideoDuration;
    private long mIconScrollDuration;

    //Icons
    private Shader[] mIconShaders;
    private Matrix[] mIconMatrices;
    private Paint[] mIconPaints;
    float mIconPosition = NO_ICON;

    //Config
    private int mCurrentMode;
    private int mCollapseAction;
    private float mGradientRotationMultiplier;

    //Logic
    private State mCurrentState = DEFAULT;
    boolean mIsCanceling = false;
    float mExpandingFactor = 0f;
    float mProgressFactor = 0f;
    private RectF mProgressArcArea = null;

    private boolean mInvalidateGradient = true;
    private boolean mInvalidateConsistency = true;
    boolean mShouldCollapseOnNextClick = false;
    private boolean mShouldCheckConsistency = true;

    //Cancellable
    ValueAnimator mExpandAnimator = null;
    ValueAnimator mCollapseAnimator = null;
    ValueAnimator mProgressAnimator = null;
    ValueAnimator mIconScrollAnimator = null;
    Runnable mExpandMessage = null;

    //Listeners
    private OnStateChangeListener mStateListener;
    private OnPhotoEventListener mPhotoListener;
    private OnVideoEventListener mVideoListener;
    private OnProgressChangeListener mProgressListener;

    public CameraButton(Context context) {
        this(context, null);
    }

    public CameraButton(Context context,
                        @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraButton(Context context,
                        @Nullable AttributeSet attrs,
                        int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CameraButton(Context context,
                        @Nullable AttributeSet attrs,
                        int defStyleAttr,
                        int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context,
                      AttributeSet attrs,
                      int defStyleAttr,
                      int defStyleRes) {

        TypedArray array = context.obtainStyledAttributes(
                attrs, R.styleable.CameraButton, defStyleAttr, defStyleRes);

        mDefaultWidth = getResources().getDimensionPixelSize(R.dimen.cb_layout_width_default);
        mDefaultHeight = getResources().getDimensionPixelSize(R.dimen.cb_layout_height_default);

        mMainCircleRadius = getDimension(
                context, array,
                R.styleable.CameraButton_cb_main_circle_radius,
                R.dimen.cb_main_circle_radius_default);

        mMainCircleRadiusExpanded = getDimension(
                context, array,
                R.styleable.CameraButton_cb_main_circle_radius_expanded,
                R.dimen.cb_main_circle_radius_expanded_default);

        mStrokeWidth = getDimension(
                context, array,
                R.styleable.CameraButton_cb_stroke_width,
                R.dimen.cb_stroke_width_default);

        mProgressArcWidth = getDimension(
                context, array,
                R.styleable.CameraButton_cb_progress_arc_width,
                R.dimen.cb_progress_arc_width_default);

        mMainCircleColor = getColor(
                context, array,
                R.styleable.CameraButton_cb_main_circle_color,
                R.color.cb_main_circle_color_default);

        mMainCircleColorPressed = getColor(
                context, array,
                R.styleable.CameraButton_cb_main_circle_color_pressed,
                R.color.cb_main_circle_color_pressed_default);

        mStrokeColor = getColor(
                context, array,
                R.styleable.CameraButton_cb_stroke_color,
                R.color.cb_stroke_color_default);

        mStrokeColorPressed = getColor(
                context, array,
                R.styleable.CameraButton_cb_stroke_color_pressed,
                R.color.cb_stroke_color_pressed_default);

        mProgressArcColors = getColors(
                context, array,
                R.styleable.CameraButton_cb_progress_arc_colors,
                R.array.cb_progress_arc_colors_default);

        mExpandDuration = Constraints.checkDuration(
                getInteger(context, array,
                        R.styleable.CameraButton_cb_expand_duration,
                        R.integer.cb_expand_duration_default));

        mExpandDelay = Constraints.checkDuration(
                getInteger(context, array,
                        R.styleable.CameraButton_cb_expand_delay,
                        R.integer.cb_expand_delay_default));

        mCollapseDuration = Constraints.checkDuration(
                getInteger(context, array,
                        R.styleable.CameraButton_cb_collapse_duration,
                        R.integer.cb_collapse_duration_default));

        mVideoDuration = Constraints.checkDuration(
                getInteger(context, array,
                        R.styleable.CameraButton_cb_video_duration,
                        R.integer.cb_hold_duration_default));

        mIconSize = Constraints.checkDimension(
                getDimension(context, array,
                        R.styleable.CameraButton_cb_icon_size,
                        R.dimen.cb_icon_size_default));

        mIconScrollDuration = Constraints.checkDuration(
                getInteger(context, array,
                        R.styleable.CameraButton_cb_icon_scroll_duration,
                        R.integer.cb_icon_scroll_duration_default));

        mGradientRotationMultiplier = array.getFloat(
                R.styleable.CameraButton_cb_gradient_rotation_multiplier,
                DEFAULT_GRADIENT_ROTATION_MULTIPLIER);

        setIcons(getDrawableResources(
                context,
                array,
                R.styleable.CameraButton_cb_icons));

        mCurrentMode = array.getInteger(
                R.styleable.CameraButton_cb_mode,
                DEFAULT_MODE);

        mCollapseAction = array.getInteger(
                R.styleable.CameraButton_cb_collapse_action,
                DEFAULT_COLLAPSE_ACTION);

        array.recycle();

        mMainCirclePaint.setColor(mMainCircleColor);
        mStrokePaint.setColor(mStrokeColor);

        mProgressArcPaint.setStyle(Paint.Style.STROKE);
        mProgressArcPaint.setStrokeWidth(mProgressArcWidth);
        mProgressArcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public boolean performClick() {
        boolean result = super.performClick();
        if (mCollapseAction == CLICK &&
                (mCurrentState == START_EXPANDING || mCurrentState == EXPANDED)) {

            mCollapseAnimator = createCollapsingAnimator();
            mCollapseAnimator.start();
        }
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (isEnabled() && isTouched(event)) {
                    makePaintColorsHovered(true);

                    if (mShouldCollapseOnNextClick &&
                            (mCurrentState == START_EXPANDING || mCurrentState == EXPANDED)) {

                        return true;
                    }
                    postExpandingMessageIfNeeded();
                    invalidate();
                    dispatchStateChange(PRESSED);
                    return true;
                }
            }

            case MotionEvent.ACTION_MOVE: {
                if (mShouldCollapseOnNextClick) {
                    makePaintColorsHovered(isTouchedExpanded(event));
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                if (mCurrentState == START_EXPANDING || mCurrentState == EXPANDED) {
                    //Handling first release from button
                    if (mCollapseAction == CLICK && !mShouldCollapseOnNextClick) {
                        mShouldCollapseOnNextClick = true;
                        makePaintColorsHovered(false);
                        return true;
                    }

                    //Released outside of button area
                    if (mCollapseAction == CLICK && !isTouchedExpanded(event)) {
                        return true;
                    }

                    //Start collapsing
                    mCollapseAnimator = createCollapsingAnimator();
                    mCollapseAnimator.start();

                    makePaintColorsHovered(false);
                    invalidate();
                    return true;
                } else if (mCurrentState == PRESSED) {
                    removeCallbacks(mExpandMessage);
                    dispatchStateChange(DEFAULT);
                    performClick();

                    makePaintColorsHovered(false);
                    invalidate();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if button area is touched.
     * It operates with square to react on a little bigger part of the view.
     *
     * @param e - touch event with down action
     * @return is collapsed button area is touched
     */
    private boolean isTouched(MotionEvent e) {
        int radius = mMainCircleRadius + mStrokeWidth;
        return Math.abs(e.getX() - getWidth() / 2f) <= radius &&
                Math.abs(e.getY() - getHeight() / 2f) <= radius;
    }

    private boolean isTouchedExpanded(MotionEvent e) {
        int radius = mMainCircleRadius + mMainCircleRadiusExpanded;
        return Math.abs(e.getX() - getWidth() / 2f) <= radius &&
                Math.abs(e.getY() - getHeight() / 2f) <= radius;
    }

    /**
     * Post message about to start expanding to the handler in case if mode allows it.
     * If mode also allows to tap the button message will be send with
     * {@link #mExpandDelay} delay
     */
    private void postExpandingMessageIfNeeded() {
        if (isExpandable()) {
            mExpandMessage = new Runnable() {
                @Override
                public void run() {
                    mProgressFactor = 0f;
                    mExpandAnimator = createExpandingAnimator();
                    mExpandAnimator.start();
                }
            };

            //In case when mode doesn't allow hold - post message immediately
            //so button will start expanding right after a tap
            if (isPressable()) {
                postDelayed(mExpandMessage, mExpandDelay);
            } else {
                post(mExpandMessage);
            }
        }
    }

    ValueAnimator createExpandingAnimator() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mExpandingFactor = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                Log.v(TAG, "expandingAnimator, onAnimationStart");
                dispatchStateChange(START_EXPANDING);
                mVideoListener.onVideoLongClick();
                cancelProgressAnimatorIfNeeded();
                cancelCollapsingAnimatorIfNeeded();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Log.v(TAG, "expandingAnimator, onAnimationEnd");
                handler.postDelayed(myRunnable, DELAY_TIME);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                Log.v(TAG, "expandingAnimator, onAnimationCancel");
                animation.removeAllListeners();
            }
        });
        animator.setDuration(mExpandDuration);
        return animator;
    }

    Handler handler = new Handler();
    Runnable myRunnable = new Runnable() {
        public void run() {
            mProgressAnimator = createProgressAnimator();
            mProgressAnimator.start();
            dispatchStateChange(EXPANDED);
        }
    };

    void cancelExpandingAnimatorIfNeeded() {
        if (mExpandAnimator != null) {
            mExpandAnimator.cancel();
            mExpandAnimator = null;
        }
    }

    ValueAnimator createCollapsingAnimator() {
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mExpandingFactor = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                Log.v(TAG, "collapsingAnimator, onAnimationStart");
                mShouldCollapseOnNextClick = false;
                cancelExpandingAnimatorIfNeeded();
                cancelProgressAnimatorIfNeeded();
                makePaintColorsHovered(false);
                handler.removeCallbacks(myRunnable);
                dispatchStateChange(START_COLLAPSING);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Log.v(TAG, "collapsingAnimator, onAnimationEnd");
                mProgressFactor = 0f;
                dispatchStateChange(DEFAULT);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                Log.v(TAG, "collapsingAnimator, onAnimationCancel");
                animation.removeAllListeners();
            }
        });
        animator.setDuration(mCollapseDuration);
        return animator;
    }

    void cancelCollapsingAnimatorIfNeeded() {
        if (mCollapseAnimator != null) {
            mCollapseAnimator.cancel();
            mCollapseAnimator = null;
        }
    }

    ValueAnimator createProgressAnimator() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setInterpolator(Interpolators.getLinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mProgressFactor = (float) animation.getAnimatedValue();
                dispatchProgressChange(mProgressFactor);
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Log.v(TAG, "progressAnimator, onAnimationEnd");
                mCollapseAnimator = createCollapsingAnimator();
                mCollapseAnimator.start();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                Log.v(TAG, "progressAnimator, onAnimationCancel");
                animation.removeAllListeners();
            }
        });
        animator.setDuration(mVideoDuration);
        return animator;
    }

    void cancelProgressAnimatorIfNeeded() {
        if (mProgressAnimator != null) {
            mProgressAnimator.cancel();
            mProgressAnimator = null;
        }
    }

    /**
     * Changes colors of main circle and stroke paints according to passed flag
     *
     * @param hovered - indicates is user touches view or not
     */
    void makePaintColorsHovered(boolean hovered) {
        if (hovered) {
            mMainCirclePaint.setColor(mMainCircleColorPressed);
            mStrokePaint.setColor(mStrokeColorPressed);
        } else {
            mMainCirclePaint.setColor(mMainCircleColor);
            mStrokePaint.setColor(mStrokeColor);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int width;
        if (widthMode == MeasureSpec.AT_MOST || heightMode == MeasureSpec.UNSPECIFIED) {
            width = mDefaultWidth;
        } else {
            width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        }

        int height;
        if (heightMode == MeasureSpec.AT_MOST || heightMode == MeasureSpec.UNSPECIFIED) {
            height = mDefaultHeight;
        } else {
            height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        if (mShouldCheckConsistency && mInvalidateConsistency) {
            mInvalidateConsistency = false;
            validateConsistency(width, height);
        }

        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.min(centerX, centerY);

        if (mProgressArcArea == null) {
            mProgressArcArea = new RectF();
        }

        if (mInvalidateGradient) {
            mProgressArcPaint.setShader(createGradient(width, height));
            mInvalidateGradient = false;
        }

        float strokeCollapsedRadius = mMainCircleRadius + mStrokeWidth;
        float currentStrokeRadius = strokeCollapsedRadius - (strokeCollapsedRadius - radius) * mExpandingFactor;
        canvas.drawCircle(centerX, centerY, currentStrokeRadius, mStrokePaint);

        float currentArcWidth = mProgressArcWidth * Interpolators.interpolateArcWidth(mExpandingFactor);
        if (currentArcWidth > 0f) {
            mProgressArcPaint.setStrokeWidth(currentArcWidth);

            //Rotate whole canvas and reduce rotation from start angle of progress arc.
            //It allows to rotate gradient shader without rotating arc.
            canvas.save();
            float gradientRotation = SWEEP_ANGLE * mProgressFactor * mGradientRotationMultiplier;
            canvas.rotate(gradientRotation, centerX, centerY);
            invalidateProgressArcArea(centerX, centerY, currentStrokeRadius, currentArcWidth);
            canvas.drawArc(mProgressArcArea, START_ANGLE - gradientRotation, SWEEP_ANGLE * mProgressFactor, false, mProgressArcPaint);
            canvas.restore();
        }

        float mainCircleRadius = mMainCircleRadius - (mMainCircleRadius - mMainCircleRadiusExpanded) * mExpandingFactor;
        canvas.drawCircle(centerX, centerY, mainCircleRadius, mMainCirclePaint);

        drawIconsIfNeeded(canvas);
    }

    private void drawIconsIfNeeded(Canvas canvas) {
        if (mIconPosition == NO_ICON) {
            return;
        }

        int leftIndex = (int) mIconPosition;
        float leftProgress = mIconPosition - (int) mIconPosition;
        float rightProgress = 1 - leftProgress;

        drawIcon(canvas, leftIndex, leftProgress, true);

        if (leftIndex < mIconShaders.length - 1) {
            drawIcon(canvas, leftIndex + 1, rightProgress, false);
        }
    }

    /**
     * Since algorithm has differences between values calculation of right/left icons
     * we have to pass some flag for identify icon side.
     */
    private void drawIcon(Canvas canvas, int index, float progress, boolean isLeftIcon) {
        float centerX = canvas.getWidth() / 2f;
        float centerY = canvas.getHeight() / 2f;

        float iconWidth = calculateIconWidth(progress);
        float translation = calculateTranslation(progress);
        float scaleX = iconWidth / mIconSize;

        float matrixDx = isLeftIcon
                ? centerX - mIconSize / 2f - translation
                : centerX + mIconSize / 2f + translation - iconWidth;

        Matrix matrix = mIconMatrices[index];
        matrix.reset();
        matrix.setScale(scaleX, 1);
        matrix.postTranslate(matrixDx, centerY - mIconSize / 2f);

        mIconShaders[index].setLocalMatrix(matrix);

        Paint paint = mIconPaints[index];
        paint.setAlpha((int) (255 * (1 - progress)));

        float rectLeft = isLeftIcon
                ? centerX - mIconSize / 2f - translation
                : centerX + mIconSize / 2f + translation - iconWidth;

        float rectRight = isLeftIcon
                ? centerX - mIconSize / 2f - translation + iconWidth
                : centerX + mIconSize / 2f + translation;

        canvas.drawRect(
                rectLeft,
                centerY - mIconSize / 2f,
                rectRight,
                centerY + mIconSize / 2f,
                paint
        );
    }

    private float calculateTranslation(float progress) {
        float interpolated = progress <= TRANSLATION_SCALE_THRESHOLD
                ? progress / TRANSLATION_SCALE_THRESHOLD
                : 1f;

        return (mMainCircleRadius - mIconSize / 2f) * interpolated;
    }

    private float calculateIconWidth(float progress) {
        float interpolated = progress < TRANSLATION_SCALE_THRESHOLD
                ? 0
                : (progress - TRANSLATION_SCALE_THRESHOLD) / (1 - TRANSLATION_SCALE_THRESHOLD);

        return mIconSize - mIconSize * interpolated;
    }

    private void validateConsistency(int width, int height) {
        if (mMainCircleRadius > Math.min(width, height)) {
            throw new ConsistencyValidationException(
                    "MainCircleRadius can't be greater than width or height. " +
                            "MainCircleRadius=" + mMainCircleRadius + "px, width=" + width + "px, height=" + height + "px");
        }
        if (mMainCircleRadius + mStrokeWidth > Math.min(width, height)) {
            throw new ConsistencyValidationException(
                    "Sum of MainCircleRadius and StrokeWidth can't be greater than width or height. " +
                            "MainCircleRadius=" + mMainCircleRadius + "px, StrokeWidth=" + mStrokeWidth + "px, width=" + width + "px, height=" + height + "px");
        }
        if (mMainCircleRadiusExpanded > Math.min(width, height)) {
            throw new ConsistencyValidationException(
                    "MainCircleRadiusExpanded can't be greater than width or height. " +
                            "MainCircleRadiusExpanded=" + mMainCircleRadiusExpanded + "px, width=" + width + "px, height=" + height + "px");
        }
        if (mMainCircleRadiusExpanded + mProgressArcWidth > Math.min(width, height)) {
            throw new ConsistencyValidationException(
                    "Sum of MainCircleRadius and ProgressArcWidth can't be greater than width or height. " +
                            "MainCircleRadius=" + mMainCircleRadius + "px, ProgressArcWidth=" + mProgressArcWidth + "px, width=" + width + "px, height=" + height + "px");
        }
        if (mIconSize / Math.sqrt(2) > mMainCircleRadius) {
            throw new ConsistencyValidationException(
                    "Icon can't be inscribed in the main button area. " +
                            "MainCircleRadius=" + mMainCircleRadius + "px, IconSize=" + mIconSize + "px");
        }
    }

    private void invalidateProgressArcArea(int centerX, int centerY, float strokeRadius, float arcWidth) {
        float expandedAreaOffset = arcWidth / 2f;
        mProgressArcArea.top = centerY - strokeRadius + expandedAreaOffset;
        mProgressArcArea.left = centerX - strokeRadius + expandedAreaOffset;
        mProgressArcArea.bottom = centerY + strokeRadius - expandedAreaOffset;
        mProgressArcArea.right = centerX + strokeRadius - expandedAreaOffset;
    }

    /**
     * Creates gradient shader for progress arc
     *
     * @param width  - width of the canvas
     * @param height - height of the canvas
     * @return gradient shader
     */
    private Shader createGradient(int width, int height) {
        return new LinearGradient(0, 0, width, height,
                mProgressArcColors, null, Shader.TileMode.MIRROR);
    }

    private void invalidateIcons() {
        mIconMatrices = new Matrix[mIconShaders.length];
        mIconPaints = new Paint[mIconShaders.length];

        for (int i = 0; i < mIconShaders.length; i++) {
            Shader shader = mIconShaders[i];

            Matrix matrix = new Matrix();
            shader.setLocalMatrix(matrix);
            mIconMatrices[i] = matrix;

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(shader);

            mIconPaints[i] = paint;
        }
    }

    private void disposeIcons() {
        mIconPosition = NO_ICON;
        mIconShaders = null;
        mIconPaints = null;
        mIconMatrices = null;
    }

    private boolean isPressable() {
        return mCurrentMode == Mode.ALL || mCurrentMode == Mode.PHOTO;
    }

    private boolean isExpandable() {
        return mCurrentMode == Mode.ALL || mCurrentMode == Mode.VIDEO;
    }

    /**
     * Scrolls icons with animation to concrete position.
     * The position is float since it is valid to scroll to an intermediate position (e.g. 0.5f)
     *
     * @param position to which icons should be scrolled
     * @throws IllegalStateException if icons are not set
     * @throws IllegalStateException if position is greater than icons count
     */
    public void scrollIconsToPosition(@FloatRange(from = 0f) float position) {
        checkCanScroll(position);
        cancelScrollIfNeeded();

        float from = mIconPosition == NO_ICON ? 0 : mIconPosition;

        mIconScrollAnimator = ValueAnimator.ofFloat(from, position);
        mIconScrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mIconPosition = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        mIconScrollAnimator.setInterpolator(Interpolators.getDecelerateInterpolator());

        long duration = (long) (Math.abs(from - position) * mIconScrollDuration);
        mIconScrollAnimator.setDuration(duration);

        mIconScrollAnimator.start();
    }

    void cancelScrollIfNeeded() {
        if (mIconScrollAnimator != null) {
            mIconScrollAnimator.cancel();
            mIconScrollAnimator = null;
        }
    }

    /**
     * Immediately scrolls icons without animation to concrete position
     * The position is float since it is valid to set an intermediate position (e.g. 0.5f)
     *
     * @param position to which icons should be scrolled
     * @throws IllegalStateException if icons are not set
     * @throws IllegalStateException if position is greater than icons count
     */
    public void setIconsPosition(@FloatRange(from = 0f) float position) {
        checkCanScroll(position);
        cancelScrollIfNeeded();

        mIconPosition = position;
        invalidate();
    }

    private void checkCanScroll(float position) {
        if (mIconPosition == NO_ICON && mIconShaders == null) {
            throw new IllegalStateException(
                    "`setIcons` must be called before `scrollIconsToPosition`/`setIconsPosition`");
        }
        if (position < 0) {
            throw new IllegalStateException(
                    "`position` (" + position + ") must be positive number");
        }
        if (position > mIconShaders.length) {
            throw new IllegalStateException(
                    "`position` (" + position + ") can't be greater than icons count (" + mIconShaders.length + ")");
        }
    }

    /**
     * Cancels video recording with animation
     */
    public void cancel() {
        cancel(true);
    }

    /**
     * Cancels video recording.
     * In case if animated = false then START_COLLAPSING state won't be dispatched to listeners
     * <p>
     * Always calls {@link OnVideoEventListener#onVideoCancel()}
     *
     * @param animated indicates should canceling process be animated or not
     */
    public void cancel(boolean animated) {
        cancelProgressAnimatorIfNeeded();
        cancelExpandingAnimatorIfNeeded();
        mIsCanceling = true;
        if (animated) {
            mCollapseAnimator = createCollapsingAnimator();
            mCollapseAnimator.start();
        } else {
            dispatchStateChange(DEFAULT);
            mShouldCollapseOnNextClick = false;
            mExpandingFactor = 0f;
            mProgressFactor = 0f;
            invalidate();
        }
    }

    /**
     * Handle state changing. Notifies all listener except {@link #mProgressListener}
     * about corresponding events.
     *
     * @param state - new state of the button
     */
    void dispatchStateChange(State state) {
        Log.v(TAG, "dispatchStateChange " + mCurrentState + " -> " + state.name());

        if (mStateListener != null) {
            //In case if button isn't pressable (Mode#VIDEO) don't dispatch PRESSED state
            if (state != PRESSED || isPressable()) {
                mStateListener.onStateChanged(state);
            }
        }

        if (mVideoListener != null && isExpandable()) {
            if (state == EXPANDED) {
                mVideoListener.onVideoStart();
            } else if (mCurrentState == EXPANDED && state == START_COLLAPSING) {
                if (mIsCanceling) {
                    mVideoListener.onVideoCancel();
                    mIsCanceling = false;
                } else {
                    mVideoListener.onVideoFinish();
                }
            }
        }

        if (mPhotoListener != null && isPressable()) {
            if (mCurrentState == PRESSED && state == DEFAULT ||
                    mCurrentState == START_EXPANDING && state == START_COLLAPSING) {
                mPhotoListener.onCameraClick();
            }
        }

        mCurrentState = state;
    }

    /**
     * Handle progress changing. Notifies {@link #mProgressListener} only.
     *
     * @param progress - new progress value
     */
    void dispatchProgressChange(float progress) {
        if (mProgressListener != null) {
            mProgressListener.onProgressChanged(progress);
        }
    }

    /**
     * Sets an listener used to be notified about state changes
     *
     * @param listener new listener to observe state changes
     */
    public void setOnStateChangeListener(@Nullable OnStateChangeListener listener) {
        mStateListener = listener;
    }

    /**
     * Sets an listener used to be notified about photo-related events
     *
     * @param listener new listener to observe photo events
     */
    public void setOnPhotoEventListener(@Nullable OnPhotoEventListener listener) {
        mPhotoListener = listener;
    }

    /**
     * Sets an listener used to be notified about video-related events
     *
     * @param listener new listener to observe video events
     */
    public void setOnVideoEventListener(@Nullable OnVideoEventListener listener) {
        mVideoListener = listener;
    }

    /**
     * Sets an listener used to be notified about progress changes
     *
     * @param listener new listener to observe progress
     */
    public void setOnProgressChangeListener(@Nullable OnProgressChangeListener listener) {
        mProgressListener = listener;
    }

    /**
     * @return the radius of inner circle in pixels
     */
    @Px
    public int getMainCircleRadius() {
        return mMainCircleRadius;
    }

    /**
     * Sets an inner circle radius in pixels
     *
     * @param radius new radius in pixels
     */
    public void setMainCircleRadius(@Px int radius) {
        mMainCircleRadius = Constraints.checkDimension(radius);
        invalidate();
    }

    /**
     * @return the radius in pixels of inner circle when button is expanded
     */
    @Px
    public int getMainCircleRadiusExpanded() {
        return mMainCircleRadiusExpanded;
    }

    /**
     * Sets the radius of inner circle when button is expanded
     *
     * @param radius new radius in pixels
     */
    public void setMainCircleRadiusExpanded(@Px int radius) {
        mMainCircleRadiusExpanded = Constraints.checkDimension(radius);
        invalidate();
    }

    @Px
    public int getStrokeWidth() {
        return mStrokeWidth;
    }

    public void setStrokeWidth(@Px int width) {
        mStrokeWidth = Constraints.checkDimension(width);
        invalidate();
    }

    @Px
    public int getProgressArcWidth() {
        return mProgressArcWidth;
    }

    public void setProgressArcWidth(@Px int width) {
        mProgressArcWidth = Constraints.checkDimension(width);
        invalidate();
    }

    @ColorInt
    public int getMainCircleColor() {
        return mMainCircleColor;
    }

    public void setMainCircleColor(@ColorInt int color) {
        mMainCircleColor = color;
    }

    @ColorInt
    public int getMainCircleColorPressed() {
        return mMainCircleColorPressed;
    }

    public void setMainCircleColorPressed(@ColorInt int color) {
        mMainCircleColorPressed = color;
    }

    @ColorInt
    public int getStrokeColor() {
        return mStrokeColor;
    }

    public void setStrokeColor(@ColorInt int color) {
        mStrokeColor = color;
    }

    @ColorInt
    public int getStrokeColorPressed() {
        return mStrokeColorPressed;
    }

    public void setStrokeColorPressed(@ColorInt int color) {
        mStrokeColorPressed = color;
    }

    @ColorInt
    @NonNull
    public int[] getProgressArcColors() {
        return mProgressArcColors.clone();
    }

    public void setProgressArcColors(@ColorInt @NonNull int[] colors) {
        mProgressArcColors = Constraints.checkNonNull(colors).clone();
        mInvalidateGradient = true;
        invalidate();
    }

    @IntRange(from = 1)
    public long getExpandDuration() {
        return mExpandDuration;
    }

    public void setExpandDuration(@IntRange(from = 1) long duration) {
        mExpandDuration = Constraints.checkDuration(duration);
    }

    @IntRange(from = 1)
    public long getCollapseDuration() {
        return mCollapseDuration;
    }

    public void setCollapseDuration(@IntRange(from = 1) long duration) {
        mCollapseDuration = Constraints.checkDuration(duration);
    }

    /**
     * @return hold delay after which button starts expanding
     */
    @IntRange(from = 1)
    public long getExpandDelay() {
        return mExpandDelay;
    }

    /**
     * Sets a hold delay after which button starts expanding
     * Used only in {@link Mode#ALL}
     *
     * @param delay duration is milliseconds
     */
    public void setExpandDelay(@IntRange(from = 1) long delay) {
        mExpandDelay = Constraints.checkDuration(delay);
    }

    /**
     * @return a duration after which button will automatically start collapsing
     */
    @IntRange(from = 1)
    public long getVideoDuration() {
        return mVideoDuration;
    }

    /**
     * Sets a duration after which button will automatically start collapsing
     *
     * @param duration new duration in milliseconds
     */
    public void setVideoDuration(@IntRange(from = 1) long duration) {
        mVideoDuration = Constraints.checkDuration(duration);
    }

    /**
     * @return how much times faster gradient rotates comparatively to progress
     */
    @FloatRange(from = 0, fromInclusive = false)
    public float getGradientRotationMultiplier() {
        return mGradientRotationMultiplier;
    }

    /**
     * Sets how much times faster gradient rotates comparatively
     * to progress of the progress arc
     * <p>
     * By default is 1.75.
     *
     * @param multiplier new value
     */
    public void setGradientRotationMultiplier(
            @FloatRange(from = 0, fromInclusive = false) float multiplier) {

        if (multiplier <= 0) {
            throw new IllegalStateException("Multiplier should be greater than 0");
        }
        mGradientRotationMultiplier = multiplier;
    }

    /**
     * @return current state of the button
     */
    @NonNull
    public State getState() {
        return mCurrentState;
    }

    @Mode
    public int getMode() {
        return mCurrentMode;
    }

    public void setMode(@Mode int mode) {
        mCurrentMode = mode;
    }

    /**
     * @return an action which is used to collapse the button
     */
    @Action
    public int getCollapseAction() {
        return mCollapseAction;
    }

    /**
     * Sets an action which will collapse the button
     * By default uses {@link Action#RELEASE}
     *
     * @param action new action flag
     */
    public void setCollapseAction(@Action int action) {
        mCollapseAction = action;
    }

    /**
     * @return whatever consistency of the button should be validated during the first `onDraw`
     */
    public boolean shouldCheckConsistency() {
        return mShouldCheckConsistency;
    }

    /**
     * Sets whatever consistency of the button should be validated during first `onDraw`
     *
     * @param checkConsistency new value
     */
    public void setShouldCheckConsistency(boolean checkConsistency) {
        mShouldCheckConsistency = checkConsistency;
    }

    /**
     * @return an size of the icons in pixels which are drawn inside the button
     */
    @Px
    public int getIconSize() {
        return mIconSize;
    }

    /**
     * Sets an size of the icons which are drawn inside the button
     *
     * @param iconSize new size in pixels
     */
    public void setIconSize(@Px int iconSize) {
        mIconSize = Constraints.checkDimension(iconSize);
    }

    /**
     * @return duration about how long one icon will be fully scrolled
     */
    public long getIconScrollDuration() {
        return mIconScrollDuration;
    }

    /**
     * Sets duration about how long one icon will be fully scrolled
     * <p>
     * If icons are about to be scrolled in intermediate position like 0.5f,
     * then duration of scroll will be pro-rata (like duration / 0.5f)
     *
     * @param duration new scroll duration
     */
    public void setIconScrollDuration(long duration) {
        mIconScrollDuration = Constraints.checkDuration(duration);
    }

    /**
     * Sets icons which are drawn inside the inner circle of the button.
     * In case if null or empty array is passed icons will be cleared.
     *
     * @param icons array of icon resources
     */
    public void setIcons(@Nullable @DrawableRes int[] icons) {
        Bitmap[] bitmaps = null;

        if (icons != null) {
            bitmaps = new Bitmap[icons.length];
            Resources resources = getResources();

            for (int i = 0; i < icons.length; i++) {
                int iconRes = icons[i];
                bitmaps[i] = BitmapFactory.decodeResource(resources, icons[i]);
            }
        }

        setIcons(bitmaps);
    }

    /**
     * Sets icons which are drawn inside the inner circle of the button.
     * In case if null or empty array is passed icons will be cleared.
     *
     * @param icons array of icon bitmaps
     */
    public void setIcons(@Nullable Bitmap[] icons) {
        if (icons == null || icons.length == 0) {
            disposeIcons();
            return;
        }

        BitmapShader[] shaders = new BitmapShader[icons.length];
        for (int i = 0; i < icons.length; i++) {
            Bitmap bitmap = Bitmap.createScaledBitmap(icons[i], mIconSize, mIconSize, false);
            shaders[i] = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }

        mIconShaders = shaders;
        invalidateIcons();
    }

    /**
     * Describes states in which button can be. Possible lifecycle:
     * <p>
     * Mode.PHOTO:
     * DEFAULT -&gt; PRESSED -&gt; DEFAULT
     * <p>
     * Mode.VIDEO:
     * DEFAULT -&gt; START_EXPANDING -&gt; EXPANDED -&gt; START_COLLAPSING -&gt; DEFAULT
     * DEFAULT -&gt; START_EXPANDING -&gt; EXPANDED -[cancel]-&gt; DEFAULT
     * <p>
     * Mode.ALL:
     * DEFAULT -&gt; PRESSED -&gt; DEFAULT
     * DEFAULT -&gt; START_EXPANDING -&gt; EXPANDED -&gt; START_COLLAPSING -&gt; DEFAULT
     * DEFAULT -&gt; START_EXPANDING -&gt; EXPANDED -[cancel]-&gt; DEFAULT
     */
    public enum State {

        /**
         * Describes state in which user doesn't interact with button somehow.
         */
        DEFAULT,

        /**
         * Describes state in which user presses and holds button for short period of time
         * or for long time in case if {@link #getMode()} is {@link Mode#PHOTO}
         */
        PRESSED,

        /**
         * Describes state in which button begins expanding.
         */
        START_EXPANDING,

        /**
         * Describes state in which button is expanded and progress of video recording is
         * visible for the user
         */
        EXPANDED,

        /**
         * Describes state in which buttons begins collapsing.
         */
        START_COLLAPSING
    }
}
