package com.limelight.ui;

import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public abstract class SrRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "GLRenderer";
    private int hTex;
    private SurfaceTexture mSTexture; // API >= 11
    private Surface surface;
    private volatile boolean requesrUpdateTex = false;

    private static final String vss =
            "attribute highp vec4 aPosition;\n"
                    + "attribute highp vec2 aTextureCoord;\n"
                    + "varying highp vec2 vTextureCoord;\n"
                    + "\n"
                    + "void main() {\n"
                    + "	gl_Position = aPosition;\n"
                    + "	vTextureCoord = aTextureCoord;\n"
                    + "}\n";
    public static final String fss =
            "#extension GL_OES_EGL_image_external : require\n"+
                    "precision mediump float;\n"+
                    "varying vec2 vTextureCoord;\n"+
                    "uniform samplerExternalOES uTexture;\n"+
                    "uniform vec2 mTextureSize;\n"+
                    "uniform float sharpLevel;\n"+
                    "uniform float uBrightness;\n" +
                    "uniform float uContrast;\n" +
                    "uniform float uSaturation;\n" +
                    "const vec3 Y_WEIGHTS = vec3(0.299, 0.587, 0.114); // Y通道的权重\n" +
                    "const vec3 U_WEIGHTS = vec3(-0.169736, -0.333264, 0.499960); // U通道的权重\n" +
                    "const vec3 V_WEIGHTS = vec3(0.499960, -0.418688, -0.081312); // V通道的权重\n"+
                    "const float LIMIT = 15.0/255.0;\n"+
                    "void main() {\n"+
                    "    vec4 texColor = texture2D(uTexture, vTextureCoord);\n"+
                    "    texColor.rgb += uBrightness;\n" +
                    "    texColor.rgb = (texColor.rgb - 0.5) * max(uContrast, 0.0) + 0.5;\n" +
                    "    float luminance = dot(texColor.rgb, vec3(0.299, 0.587, 0.114));\n" +
                    "    texColor.rgb = mix(vec3(luminance), texColor.rgb, uSaturation);\n" +
                    "\n"+
                    "    vec2 offsetX0 = vec2(1.0, 0.0) / mTextureSize;\n"+
                    "    vec2 offsetX1 = vec2(2.0, 0.0) / mTextureSize;\n"+
                    "    vec2 offsetY0 = vec2(0.0, 1.0) / mTextureSize;\n"+
                    "    vec2 offsetY1 = vec2(0.0, 2.0) / mTextureSize;\n"+
                    "    float cTempX0 = dot(texture2D(uTexture, vTextureCoord + offsetX0).rgb, Y_WEIGHTS);\n"+
                    "    float cTempX1 = dot(texture2D(uTexture, vTextureCoord + offsetX1).rgb, Y_WEIGHTS);\n"+
                    "    float cTempX2 = dot(texture2D(uTexture, vTextureCoord - offsetX1).rgb, Y_WEIGHTS);\n"+
                    "    float cTempX3 = dot(texture2D(uTexture, vTextureCoord - offsetX0).rgb, Y_WEIGHTS);\n"+
                    "    float cTemp = dot(texColor.rgb, Y_WEIGHTS);\n"+
                    "    float cTempY0 = dot(texture2D(uTexture, vTextureCoord + offsetY0).rgb, Y_WEIGHTS);\n"+
                    "    float cTempY1 = dot(texture2D(uTexture, vTextureCoord + offsetY1).rgb, Y_WEIGHTS);\n"+
                    "    float cTempY2 = dot(texture2D(uTexture, vTextureCoord - offsetY1).rgb, Y_WEIGHTS);\n"+
                    "    float cTempY3 = dot(texture2D(uTexture, vTextureCoord - offsetY0).rgb, Y_WEIGHTS);\n"+
                    "\n"+
                    "    float y = cTemp + min(LIMIT, max(-LIMIT, (cTemp-(cTempX0+cTempX3+cTempY0+cTempY3+(cTempX1+cTempX2+cTempY1+cTempY2)*2.0+cTemp*4.0)/16.0)*sharpLevel));\n"+
                    "    float u = dot(texColor.rgb, U_WEIGHTS);\n"+
                    "    float v = dot(texColor.rgb, V_WEIGHTS);\n"+
                    "\n"+
                    "    float R = y + v *  1.402;\n" +
                    "    float G = y - u * 0.3441 - v * 0.7141;\n" +
                    "    float B = y + u * 1.772;\n" +
                    "\n"+
                    "    gl_FragColor = vec4(R, G, B, 1.0);\n"+
                    "}\n";

    private static final float[] VERTICES = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f };

    private static final float[] TEXCOORD = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f };

    private final FloatBuffer pVertex;
    private final FloatBuffer pTexCoord;
    private int hProgram;
    int maPositionLoc;
    int maTextureCoordLoc;

    private int mSharpHandle;
    private int mTextureSizeHandle;
    private float mSharpLevel = 1.0f;
    private int mBrightnessHandle;
    private float mBrightnessValue = 0.0f;
    private int mContrastHandle;
    private float mContrastValue = 1.0f;
    private int mSaturationHandle;
    private float mSaturationValue = 1.0f;
    private FloatBuffer TEXTURE_SIZE;

    public SrRenderer() {
        pVertex = createFloatBuffer(VERTICES);
        pTexCoord = createFloatBuffer(TEXCOORD);
    }

    public static FloatBuffer createFloatBuffer(float[] coords) {
        FloatBuffer fb = ByteBuffer.allocateDirect(coords.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(coords);
        fb.flip();
        return fb;
    }

    public Surface getSurface() {
        return surface;
    }

    public void setSharpLevel(float sharpLevel) {
        mSharpLevel = sharpLevel;
    }

    public float getSharpLevel() {
        return mSharpLevel;
    }

    public static Point getSurfaceDefaultSize(Surface surface) {
        try {
            Method getDefaultSizeMethod = Surface.class.getDeclaredMethod("getDefaultSize");
            getDefaultSizeMethod.setAccessible(true);
            return (Point) getDefaultSizeMethod.invoke(surface);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int initTex() {
        final int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        return tex[0];
    }

    public static int loadShader(final String vss, final String fss) {
        int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vs, vss);
        GLES20.glCompileShader(vs);
        final int[] compiled = new int[1];
        GLES20.glGetShaderiv(vs, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Failed to compile vertex shader:" + GLES20.glGetShaderInfoLog(vs));
            GLES20.glDeleteShader(vs);
            vs = 0;
        }

        int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fs, fss);
        GLES20.glCompileShader(fs);
        GLES20.glGetShaderiv(fs, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.w(TAG, "Failed to compile fragment shader:" + GLES20.glGetShaderInfoLog(fs));
            GLES20.glDeleteShader(fs);
            fs = 0;
        }

        final int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program));
        }
        return program;
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        // This renderer required OES_EGL_image_external extension
        final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if (!extensions.contains("OES_EGL_image_external"))
            throw new RuntimeException("This system does not support OES_EGL_image_external.");

        hTex = initTex();
        mSTexture = new SurfaceTexture(hTex);
        mSTexture.setOnFrameAvailableListener(surfaceTexture -> requesrUpdateTex = true);
        surface = new Surface(mSTexture);

        GLES20.glClearColor(0.3f, 0.3f, 0.3f, 0.3f);

        hProgram = loadShader(vss, fss);
        GLES20.glUseProgram(hProgram);
        maPositionLoc = GLES20.glGetAttribLocation(hProgram, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(hProgram, "aTextureCoord");

        mSharpHandle = GLES20.glGetUniformLocation(hProgram, "sharpLevel");
        mTextureSizeHandle = GLES20.glGetUniformLocation(hProgram, "mTextureSize");
        mBrightnessHandle = GLES20.glGetUniformLocation(hProgram, "uBrightness");
        mContrastHandle = GLES20.glGetUniformLocation(hProgram, "uContrast");
        mSaturationHandle = GLES20.glGetUniformLocation(hProgram, "uSaturation");
    }


    @Override
    public void onSurfaceChanged(final GL10 unused, final int width, final int height) {
        if (width==0 || height==0)
            return;

        TEXTURE_SIZE = createFloatBuffer(new float[]{width,height});
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(final GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (requesrUpdateTex) {
            requesrUpdateTex = false;
            mSTexture.updateTexImage();
        }

        GLES20.glVertexAttribPointer(maPositionLoc, 2, GLES20.GL_FLOAT, false, 2 * Float.BYTES, pVertex);
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 2 * Float.BYTES, pTexCoord);
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);

        GLES20.glUseProgram(hProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,hTex);

        GLES20.glUniform1f(mSharpHandle, mSharpLevel);
        GLES20.glUniform2fv(mTextureSizeHandle, 1, TEXTURE_SIZE);
        GLES20.glUniform1f(mBrightnessHandle, mBrightnessValue);
        GLES20.glUniform1f(mContrastHandle, mContrastValue);
        GLES20.glUniform1f(mSaturationHandle, mSaturationValue);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glUseProgram(0);
    }

    public static void deleteTex(final int hTex) {
        final int[] tex = new int[] {hTex};
        GLES20.glDeleteTextures(1, tex, 0);
    }

    public synchronized void onSurfaceDestroyed() {
        if(surface!=null) {
            surface.release();
            surface = null;
        }

        if (mSTexture != null) {
            mSTexture.release();
            mSTexture = null;
        }

        deleteTex(hTex);
        if (hProgram >= 0) {
            GLES20.glDeleteProgram(hProgram);
            hProgram = -1;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        onSurfaceDestroyed();
        super.finalize();
    }
}