package com.domker.study.androidstudy;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

public class ZoomImageView extends ImageView implements OnScaleGestureListener, OnTouchListener, ViewTreeObserver.OnGlobalLayoutListener {

    public static final float SCALE_MAX = 4.0f; //缩放最大值
    private final int mTouchSlop; //移动距离
    private float initScale = 1.0f; //初始缩放比例
    private final float[] matrixValues = new float[9]; //用于存放Matrix的九个值
    private boolean once = true;
    private  ScaleGestureDetector mScaleGestureDetector = null; //缩放手势检测
    private final Matrix mScaleMatrix = new Matrix(); // 初始矩阵
    private boolean isCanDrag; //是否可以移动

    //用于判断图片大小是否超出屏幕，进而判断是否可以移动
    private boolean isCheckLeftAndRight;
    private boolean isCheckTopAndBottom;

    private int lastPointerCount; //上一次触摸点
    //上一次触摸点坐标均值
    private float mLastX;
    private float mLastY;

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setScaleType(ScaleType.MATRIX);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mScaleGestureDetector = new ScaleGestureDetector(context,this);
        this.setOnTouchListener(this);
    }

    //获取当前缩放比例
    public final float getScale(){
        mScaleMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float scale = getScale();
        float scaleFactor = detector.getScaleFactor();

        if (getDrawable() == null)
            return true;

        //缩放的范围控制
        if ((scale<SCALE_MAX && scaleFactor>1.0f)
                ||(scale>initScale && scaleFactor<1.0f))
        {

            if (scaleFactor * scale < initScale)
            {
                scaleFactor = initScale / scale;
            }
            if (scaleFactor * scale > SCALE_MAX)
            {
                scaleFactor = SCALE_MAX / scale;
            }

            //设置缩放中心与比例
            mScaleMatrix.postScale(scaleFactor, scaleFactor,
                    detector.getFocusX(), detector.getFocusX());
            checkBorderAndCenterWhenScale();
            setImageMatrix(mScaleMatrix);
        }
        return true;
    }

    //在缩放时，进行图片显示范围的控制
    private void checkBorderAndCenterWhenScale()
    {

        RectF rect = getMatrixRectF();
        float deltaX = 0;
        float deltaY = 0;

        int width = getWidth();
        int height = getHeight();

        // 如果宽或高大于屏幕，则控制范围
        if (rect.width() >= width)
        {
            if (rect.left > 0)
            {
                deltaX = -rect.left;
            }
            if (rect.right < width)
            {
                deltaX = width - rect.right;
            }
        }
        if (rect.height() >= height)
        {
            if (rect.top > 0)
            {
                deltaY = -rect.top;
            }
            if (rect.bottom < height)
            {
                deltaY = height - rect.bottom;
            }
        }
        // 如果宽或高小于屏幕，则让其居中
        if (rect.width() < width)
        {
            deltaX = width * 0.5f - rect.right + 0.5f * rect.width();
        }
        if (rect.height() < height)
        {
            deltaY = height * 0.5f - rect.bottom + 0.5f * rect.height();
        }
        mScaleMatrix.postTranslate(deltaX, deltaY);

    }

    //根据当前图片的Matrix获得图片的范围
    private RectF getMatrixRectF()
    {
        Matrix matrix = mScaleMatrix;
        RectF rect = new RectF();
        Drawable d = getDrawable();
        if (null != d)
        {
            rect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            matrix.mapRect(rect);
        }
        return rect;
    }


    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
        mScaleGestureDetector.onTouchEvent(event);

        float x = 0, y = 0;
        // 拿到触摸点的个数
        final int pointerCount = event.getPointerCount();
        // 得到多个触摸点的x与y均值
        for (int i = 0; i < pointerCount; i++)
        {
            x += event.getX(i);
            y += event.getY(i);
        }
        x = x / pointerCount;
        y = y / pointerCount;

        /**
         * 每当触摸点发生变化时，重置mLasX , mLastY
         */
        if (pointerCount != lastPointerCount)
        {
            isCanDrag = false;
            mLastX = x;
            mLastY = y;
        }


        lastPointerCount = pointerCount;
        RectF rectF = getMatrixRectF();

        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN: //处理与ViewPager冲突，使图片放大时可以移动而不会切换。
                if (rectF.width() > getWidth() || rectF.height() > getHeight())
                {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            case MotionEvent.ACTION_MOVE:
                float dx = x - mLastX;
                float dy = y - mLastY;

                if (!isCanDrag)
                {
                    isCanDrag = isCanDrag(dx, dy);
                }
                if (isCanDrag)
                {

                    if (getDrawable() != null)
                    {


                        // 如果宽度小于屏幕宽度，则禁止左右移动
                        isCheckLeftAndRight = isCheckTopAndBottom = true;
                        if (rectF.width() < getWidth())
                        {
                            dx = 0;
                            isCheckLeftAndRight = false;
                        }
                        // 如果高度小于屏幕高度，则禁止上下移动
                        if (rectF.height() < getHeight())
                        {
                            dy = 0;
                            isCheckTopAndBottom = false;
                        }
                        mScaleMatrix.postTranslate(dx, dy);
                        checkMatrixBounds();
                        setImageMatrix(mScaleMatrix);

                    }
                }
                mLastX = x;
                mLastY = y;
                if (rectF.width() > getWidth() || rectF.height() > getHeight())
                {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                //使得放大后的图片移动到边界时可以切换图片，处理与ViewPager冲突
                if (isCanDrag) {

                    if (getDrawable() != null) {
                        if (rectF.left == 0 && dx > 0) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }

                        if (rectF.right == getWidth() && dx < 0) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastPointerCount = 0;
                break;
        }



        return true;
    }

     //移动时的边界判断
    private void checkMatrixBounds()
    {
        RectF rect = getMatrixRectF();

        float deltaX = 0, deltaY = 0;
        final float viewWidth = getWidth();
        final float viewHeight = getHeight();
        // 判断移动或缩放后，图片显示是否超出屏幕边界
        if (rect.top > 0 && isCheckTopAndBottom)
        {
            deltaY = -rect.top;
        }
        if (rect.bottom < viewHeight && isCheckTopAndBottom)
        {
            deltaY = viewHeight - rect.bottom;
        }
        if (rect.left > 0 && isCheckLeftAndRight)
        {
            deltaX = -rect.left;
        }
        if (rect.right < viewWidth && isCheckLeftAndRight)
        {
            deltaX = viewWidth - rect.right;
        }
        mScaleMatrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 是否是推动行为
     *
     * @param dx
     * @param dy
     * @return
     */
    private boolean isCanDrag(float dx, float dy)
    {
        return Math.sqrt((dx * dx) + (dy * dy)) >= mTouchSlop;
    }

    @Override
    public void onGlobalLayout() {
        if(once)
        {
            Drawable d = getDrawable();
            if (d == null)
                return;
            //View显示出的宽高
            int width = getWidth();
            int height = getHeight();
            //图片的宽和高
            int dw = d.getIntrinsicWidth();
            int dh = d.getIntrinsicHeight();
            float scale = 1.0f;
            //若图片的宽或高超出view范围（即屏幕），则缩放至屏幕的宽/高
            if (dw > width && dh<= height){
                scale = width*1.0f/dw;
            }
            if (dh > height && dw<= width){
                scale = width*1.0f/dh;
            }
            //若图片的宽和高都超出view范围（即屏幕），按比例缩放到适应屏幕
            if (dw > width && dh > height){
                scale = Math.min(dw * 1.0f / width, dh * 1.0f / height);
            }
            initScale = scale;
            //图片移动至屏幕中心
            mScaleMatrix.postTranslate((width - dw) / 2, (height - dh) / 2);
            mScaleMatrix.postScale(scale, scale, getWidth() / 2, getHeight() / 2);
            setImageMatrix(mScaleMatrix);
            once = false;
        }

    }
}
