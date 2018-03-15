package com.softwarejoint.media.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.media.effect.Effect;
import android.media.effect.EffectContext;
import android.media.effect.EffectFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.softwarejoint.media.enums.ImageEffectType;
import com.softwarejoint.media.glutils.GLDrawer2D;
import com.softwarejoint.media.picker.MediaPickerOpts;
import com.softwarejoint.media.utils.BitmapUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

@SuppressWarnings("WeakerAccess")
public final class EffectGLView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final String TAG = "EffectGLView";

    private static final String[] EFFECTS = {
            EffectFactory.EFFECT_BLACKWHITE
    };

    private boolean mHasSurface;

    private boolean filtersEnabled;
    private boolean effectPreviewsVisible;
    private boolean imageLoaded = false;

    private int[] mTextures = new int[EFFECTS.length + 1];
    private EffectContext mEffectContext;
    private Effect mEffect;
    private TextureRenderer mTexRenderer = new TextureRenderer();
    private int mImageWidth;
    private int mImageHeight;
    private Bitmap origBitmap;

    private List<Effect> effects = new ArrayList<>();

    private final Queue<Runnable> mRunOnDraw = new LinkedList<>();

    private @ImageEffectType
    volatile int mCurrentEffect = ImageEffectType.NONE;

    private String origImagePath;

    public EffectGLView(final Context context) {
        this(context, null, 0);
    }

    public EffectGLView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EffectGLView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs);

        setZOrderOnTop(true);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8,8,8,8,16,0);
        getHolder().setFormat(PixelFormat.RGBA_8888);

        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        Arrays.fill(mTextures, 0);
    }

    public void init(String imagePath, MediaPickerOpts opts) {
        origImagePath = imagePath;
        filtersEnabled = opts.filtersEnabled;
        Log.d(TAG, "init: path: " + imagePath + " filter: " + filtersEnabled);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!effectPreviewsVisible) return super.dispatchTouchEvent(event);

        Log.d(TAG, "dispatchTouchEvent");

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                final int x = (int) event.getX();
                final int y = (int) event.getY();
                Log.d(TAG, "onTouched: x: " + x + " y: " + y);
                // queueEvent(() -> mRenderer.onTouched(x, y));
                break;
            case MotionEvent.ACTION_UP:
                break;
        }

        return true;
    }

    private void loadImage() {
        Log.d(TAG, "loadImage: mHasSurface: " + mHasSurface);
        Log.d(TAG, "loadImage: imageLoaded: " + imageLoaded);
        Log.d(TAG, "loadImage: imagePath: " + origImagePath);
        Log.d(TAG, "loadImage: width: " + getWidth());
        Log.d(TAG, "loadImage: height: " + getHeight());

        if (!mHasSurface || origImagePath == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        if (origBitmap == null) {
            final int imgSize = Math.min(getWidth(), getHeight());
            // Load input bitmap
            loadBitmap(origImagePath, imgSize);

            if (origBitmap != null) {
                mImageWidth = origBitmap.getWidth();
                mImageHeight = origBitmap.getHeight();
                mTexRenderer.updateTextureSize(mImageWidth, mImageHeight);

                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, origBitmap, 0);

                imageLoaded = true;
            }
        }

        if (imageLoaded) {
            requestRender();
        }
    }

    private void loadBitmap(String imagePath, int imgSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);
        options.inSampleSize = BitmapUtils.calculateInSampleSize(options, imgSize, imgSize);
        options.inJustDecodeBounds = false;
        origBitmap = BitmapFactory.decodeFile(imagePath, options);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadImage();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.d(TAG, "onMeasure: width: " + widthMeasureSpec + " height: " + heightMeasureSpec);

        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    public void toggleEffectPreviews(boolean enabled) {
        effectPreviewsVisible = enabled;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated: widht: " + getWidth() + " height: " + getHeight());

        mEffectContext = EffectContext.createWithCurrentGlContext();

        GLES20.glGenTextures(mTextures.length, mTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);

        GLToolbox.initTexParams();

        mTexRenderer.init();

        GLES20.glClearColor(0,0,0,0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        mHasSurface = true;
        queueEvent(this::loadImage);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "onSurfaceChanged: width: " + width + " height: " + height);

        if (mTexRenderer != null) {
            mTexRenderer.updateViewSize(width, height);
        }

        queueEvent(this::loadImage);
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed:");
        mHasSurface = false;
        queueEvent(this::onSurfaceDestroyed);
        super.surfaceDestroyed(holder);
    }

    private void onSurfaceDestroyed() {
        if (mTexRenderer != null) {
            mTexRenderer.tearDown();
        }

        if (mEffect != null) {
            mEffect.release();
        }

        for (Effect effect : effects) {
            effect.release();
        }

        for (int mTexture : mTextures) {
            if (mTexture > 0) {
                GLDrawer2D.deleteTex(mTexture);
            }
        }
    }

    public void onEffectSelected(@ImageEffectType int effectType) {
        runOnDraw(() -> {
            if (mEffect != null) {
                mEffect.release();
            }

            mEffect = null;

            mCurrentEffect = effectType;

            mEffect = createEffect();

            if (mCurrentEffect != ImageEffectType.NONE) {
                applyEffect();
            }
        });
    }

    protected void runOnDraw(final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.add(runnable);
        }
    }

    private void runAll(Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!imageLoaded) return;

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        runAll(mRunOnDraw);

        Log.d(TAG, "renderResult: mCurrentEffect: " + mCurrentEffect);

        if (mCurrentEffect == ImageEffectType.NONE) {
            // if no effect is chosen, just render the original bitmap
            mTexRenderer.renderTexture(mTextures[0]);
        } else {
            // render the result of applyEffect()
            mTexRenderer.renderTexture(mTextures[1]);
        }
    }

    private Effect createEffect() {
        EffectFactory effectFactory = mEffectContext.getFactory();

        Effect effect = null;

        switch (mCurrentEffect) {
            case ImageEffectType.NONE:
                break;
            case ImageEffectType.BLACK_WHITE:
                effect = effectFactory.createEffect(EffectFactory.EFFECT_BLACKWHITE);
                effect.setParameter("black", .1f);
                effect.setParameter("white", .7f);
                break;
        }

        return effect;
    }

    private void applyEffect() {
        Log.d(TAG, "applyEffect: mCurrentEffect: " + mCurrentEffect);
        mEffect.apply(mTextures[0], mImageWidth, mImageHeight, mTextures[mCurrentEffect + 1]);
    }
}