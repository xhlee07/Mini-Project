package com.ctfly.app

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import javax.microedition.khronos.egl.*
import javax.microedition.khronos.opengles.GL10

/** Keeps GL content in the normal view hierarchy, including EMUI clipping and rotation. */
@android.annotation.SuppressLint("ViewConstructor")
class TourSurface(
    context: Context,
    initial: TourPose,
    onPose: (TourPose) -> Unit,
    private val onStatus: (String) -> Unit,
    quality: TourQuality = TourQuality.ULTRA,
    private val preferModern: Boolean = true,
    initialRace: KartState? = null,
    onRace: (KartState?) -> Unit = {},
    initialDoors: Int = 0,
    onDoors: (Int) -> Unit = {},
    initialBest: Float = 0f,
) : TextureView(context), TextureView.SurfaceTextureListener {
    @Volatile private var disposed = false
    @Volatile private var paused = true
    @Volatile private var quality = quality
    @Volatile private var requestedSize = 1 to 1
    @Volatile
    internal var renderBufferSize = 0 to 0
        private set

    @Volatile
    internal var presentedFrames = 0L
        private set

    private val renderThread = HandlerThread("CTFLY-Tour").apply { start() }
    private val handler = Handler(renderThread.looper)
    private val egl = EGLContext.getEGL() as EGL10
    private var display = EGL10.EGL_NO_DISPLAY
    private var glContext = EGL10.EGL_NO_CONTEXT
    private var window = EGL10.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private var nativeSurface: Surface? = null
    private var texture: SurfaceTexture? = null
    private var gl: GL10? = null
    private val pendingEvents = ArrayDeque<() -> Unit>()
    private val inputLock = Any()
    private var lookX = 0f
    private var lookY = 0f

    val tour =
        TourRenderer(
            context.applicationContext,
            initial,
            { p -> post { if (!disposed) onPose(p) } },
            { s -> post { if (!disposed) onStatus(s) } },
            preferModern,
            initialRace,
            { r -> post { if (!disposed) onRace(r) } },
            initialDoors,
            { d -> post { if (!disposed) onDoors(d) } },
            initialBest,
        )

    init {
        isOpaque = true
        surfaceTextureListener = this
        contentDescription = "First person CTFLY venue"
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        requestedSize = quality.size(width, height)
        handler.post {
            if (disposed) return@post
            try {
                releaseEgl()
                texture = surface
                display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
                check(display != EGL10.EGL_NO_DISPLAY && egl.eglInitialize(display, IntArray(2)))
                for (samples in listOf(4, 0)) for (depth in listOf(24, 16)) {
                    if (config == null) {
                        val configs = arrayOfNulls<EGLConfig>(1)
                        val count = IntArray(1)
                        val attributes =
                            intArrayOf(
                                EGL10.EGL_RED_SIZE,
                                8,
                                EGL10.EGL_GREEN_SIZE,
                                8,
                                EGL10.EGL_BLUE_SIZE,
                                8,
                                EGL10.EGL_DEPTH_SIZE,
                                depth,
                                EGL10.EGL_RENDERABLE_TYPE,
                                4,
                                EGL10.EGL_SURFACE_TYPE,
                                EGL10.EGL_WINDOW_BIT,
                                0x3032,
                                if (samples > 0) 1 else 0,
                                0x3031,
                                samples,
                                EGL10.EGL_NONE,
                            )
                        if (
                            egl.eglChooseConfig(display, attributes, configs, 1, count) &&
                                count[0] > 0
                        )
                            config = configs[0]
                    }
                }
                check(config != null) { "No compatible graphics configuration" }
                if (preferModern)
                    glContext =
                        egl.eglCreateContext(
                            display,
                            config,
                            EGL10.EGL_NO_CONTEXT,
                            intArrayOf(0x3098, 3, EGL10.EGL_NONE),
                        )
                if (glContext == EGL10.EGL_NO_CONTEXT) {
                    egl.eglGetError()
                    glContext =
                        egl.eglCreateContext(
                            display,
                            config,
                            EGL10.EGL_NO_CONTEXT,
                            intArrayOf(0x3098, 2, EGL10.EGL_NONE),
                        )
                }
                check(glContext != EGL10.EGL_NO_CONTEXT) { "Cannot create graphics context" }
                nativeSurface = Surface(surface)
                resizeWindow()
                gl = glContext.gl as GL10
                tour.onSurfaceCreated(gl, config)
                tour.onSurfaceChanged(gl, renderBufferSize.first, renderBufferSize.second)
                while (pendingEvents.isNotEmpty()) pendingEvents.removeFirst().invoke()
                scheduleFrame()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        requestedSize = quality.size(width, height)
        handler.post {
            if (!disposed && texture === surface && window != EGL10.EGL_NO_SURFACE) {
                try {
                    resizeWindow()
                    tour.onSurfaceChanged(gl, renderBufferSize.first, renderBufferSize.second)
                } catch (e: Exception) {
                    fail(e)
                }
            }
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // Release only after queued GL drawing has stopped, never concurrently on the UI thread.
        val queued =
            handler.post {
                if (texture === surface) releaseEgl()
                surface.release()
            }
        if (!queued) surface.release()
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        presentedFrames++
    }

    private fun resizeWindow() {
        val (w, h) = requestedSize
        texture?.setDefaultBufferSize(w, h)
        if (window != EGL10.EGL_NO_SURFACE) {
            egl.eglMakeCurrent(
                display,
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT,
            )
            egl.eglDestroySurface(display, window)
        }
        window =
            egl.eglCreateWindowSurface(display, config, nativeSurface, intArrayOf(EGL10.EGL_NONE))
        check(
            window != EGL10.EGL_NO_SURFACE && egl.eglMakeCurrent(display, window, window, glContext)
        ) {
            "Cannot resize graphics window"
        }
        val rw = IntArray(1)
        val rh = IntArray(1)
        check(
            egl.eglQuerySurface(display, window, EGL10.EGL_WIDTH, rw) &&
                egl.eglQuerySurface(display, window, EGL10.EGL_HEIGHT, rh)
        )
        renderBufferSize = rw[0] to rh[0]
    }

    private val drawFrame =
        object : Runnable {
            override fun run() {
                if (disposed || paused || window == EGL10.EGL_NO_SURFACE) return
                try {
                    if (renderBufferSize != requestedSize) {
                        resizeWindow()
                        tour.onSurfaceChanged(gl, renderBufferSize.first, renderBufferSize.second)
                    }
                    val delta =
                        synchronized(inputLock) {
                            (lookX to lookY).also {
                                lookX = 0f
                                lookY = 0f
                            }
                        }
                    tour.look(delta.first, delta.second)
                    tour.onDrawFrame(gl)
                    if (disposed || paused) return
                    check(egl.eglSwapBuffers(display, window)) {
                        "Graphics frame could not be presented"
                    }
                    handler.postDelayed(this, 1)
                } catch (e: Exception) {
                    fail(e)
                }
            }
        }

    private fun scheduleFrame() {
        handler.removeCallbacks(drawFrame)
        if (!paused && !disposed && window != EGL10.EGL_NO_SURFACE) handler.post(drawFrame)
    }

    fun queueEvent(action: () -> Unit) {
        if (disposed) return
        handler.post {
            if (!disposed) {
                try {
                    if (window == EGL10.EGL_NO_SURFACE) pendingEvents.add(action) else action()
                } catch (e: Exception) {
                    fail(e)
                }
            }
        }
    }

    fun addLook(yaw: Float, pitch: Float) {
        if (disposed || paused) return
        synchronized(inputLock) {
            lookX += yaw
            lookY += pitch
        }
    }

    fun setQuality(value: TourQuality) {
        quality = value
        requestedSize = quality.size(width, height)
        android.util.Log.i(
            "CTFLYTour",
            "Quality $value: target=$requestedSize buffer=$renderBufferSize paused=$paused disposed=$disposed",
        )
        handler.post { scheduleFrame() }
    }

    fun onResume() {
        paused = false
        tour.resetFrameClock()
        handler.post { scheduleFrame() }
    }

    fun onPause() {
        paused = true
        stopInput()
        handler.removeCallbacks(drawFrame)
    }

    fun stopInput() {
        tour.strafe = 0f
        tour.forward = 0f
        tour.throttle = 0f
        tour.brake = 0f
        tour.reverse = 0f
        tour.drift = false
        synchronized(inputLock) {
            lookX = 0f
            lookY = 0f
        }
    }

    fun detachCallbacks() {
        disposed = true
        handler.post {
            pendingEvents.clear()
            releaseEgl()
            renderThread.quitSafely()
        }
    }

    private fun fail(e: Exception) {
        android.util.Log.e("CTFLYTour", "Texture display failed", e)
        if (!disposed) post { if (!disposed) onStatus("3D display failed. Return and try again.") }
        handler.removeCallbacks(drawFrame)
        releaseEgl()
    }

    private fun releaseEgl() {
        handler.removeCallbacks(drawFrame)
        if (display != EGL10.EGL_NO_DISPLAY) {
            egl.eglMakeCurrent(
                display,
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT,
            )
            if (window != EGL10.EGL_NO_SURFACE) egl.eglDestroySurface(display, window)
            if (glContext != EGL10.EGL_NO_CONTEXT) egl.eglDestroyContext(display, glContext)
            egl.eglTerminate(display)
        }
        nativeSurface?.release()
        nativeSurface = null
        texture = null
        gl = null
        config = null
        window = EGL10.EGL_NO_SURFACE
        glContext = EGL10.EGL_NO_CONTEXT
        display = EGL10.EGL_NO_DISPLAY
        renderBufferSize = 0 to 0
    }
}
