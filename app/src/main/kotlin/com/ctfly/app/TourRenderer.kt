package com.ctfly.app

import android.content.Context
import android.graphics.*
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class TourRenderer(
    private val context: Context,
    initial: TourPose,
    private val onPose: (TourPose) -> Unit,
    private val onStatus: (String) -> Unit,
    private val preferModernShaders: Boolean = true,
    initialRace: KartState? = null,
    private val onRace: (KartState?) -> Unit = {},
    initialDoors: Int = 0,
    private val onDoors: (Int) -> Unit = {},
    initialBest: Float = 0f,
) : GLSurfaceView.Renderer {
    internal val usesModernShaders: Boolean
        get() = modernShaders

    @Volatile
    internal var renderedSize: Pair<Int, Int> = 0 to 0
        private set

    @Volatile var strafe = 0f
    @Volatile var forward = 0f
    @Volatile var sprint = false
    @Volatile var throttle = 0f
    @Volatile var brake = 0f
    @Volatile var reverse = 0f
    @Volatile var drift = false
    @Volatile var experiencePaused = false
    @Volatile
    var race: KartState? = initialRace.takeIf { initial.area == TourArea.OUTDOOR }
        private set

    @Volatile
    var doorMask = initialDoors and WashroomDoors.validMask
        private set

    val jump = TourJump()
    private var movingBuffer = 0
    private var movingCount = 0
    private var kartOffset = 0
    private var kartCount = 0
    private val doorAngles =
        FloatArray(WashroomDoors.doors.size) { if (doorMask and (1 shl it) != 0) 90f else 0f }
    private val model = FloatArray(16)
    private var collisionMask = -1
    private var doorCollision = emptyList<TourObstacle>()
    private var kartBest = max(initialBest, initialRace?.best ?: 0f)
    @Volatile
    var pose = initial
        private set

    private var loaded: TourMesh? = null
    private val world: TourMesh
        get() = loaded ?: TourMesh(context, pose.area).also { loaded = it }

    private var viewportWidth = 1
    private var viewportHeight = 1
    private val geometry
        get() = world.geometry

    private val vertexCount
        get() = world.floatCount / 12

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private var program = 0
    private var buffer = 0
    private var texture = 0
    private var materialTexture = 0
    private var secondaryTexture = 0
    private var skylineTexture = 0
    private var screenTexture = 0
    private var shadowSize = 1024
    @Volatile private var previous = 0L
    private var reported = 0L
    private var ready = false
    private var firstFrame = true
    private var uMatrix = 0
    private var uCamera = 0
    private val lightMatrix = FloatArray(16)
    private var shadowTexture = 0
    private var shadowAvailable = false
    private var attributes = emptyList<Triple<Int, Int, Int>>()
    private var maxLamps = 12
    private var maxOccluders = 32
    private var skyProgram = 0
    private var skyBuffer = 0
    private var modernShaders = false
    private var postProgram = 0
    private var postFramebuffer = 0
    private var postTexture = 0
    private var postDepth = 0
    private var postReady = false
    private var fieldOfView = 68f
    private val skyTriangle =
        ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
            position(0)
        }

    private val look = TourLook(initial.yaw, initial.pitch)

    fun look(yaw: Float, pitch: Float) {
        if (race == null) look.add(yaw, pitch)
    }

    fun resetFrameClock() {
        previous = 0L
    }

    fun go(stop: TourStop) {
        race?.let { onRace(it) }
        race = null
        onRace(null)
        jump.reset()
        strafe = 0f
        forward = 0f
        val switched = pose.area != stop.pose.area
        pose = stop.pose
        look.reset(pose.yaw, pose.pitch)
        onPose(pose)
        if (switched) {
            onStatus("loading")
            ready = false
            loaded = null
            glDeleteBuffers(1, intArrayOf(buffer), 0)
            glDeleteBuffers(1, intArrayOf(movingBuffer), 0)
            movingBuffer = 0
            movingCount = 0
            glDeleteTextures(
                6,
                intArrayOf(
                    texture,
                    shadowTexture,
                    materialTexture,
                    secondaryTexture,
                    skylineTexture,
                    screenTexture,
                ),
                0,
            )
            glDeleteProgram(program)
            glDeleteProgram(skyProgram)
            glDeleteBuffers(1, intArrayOf(skyBuffer), 0)
            glDeleteProgram(postProgram)
            releasePostSurface()
            onSurfaceCreated(null, null)
            onSurfaceChanged(null, viewportWidth, viewportHeight)
        }
    }

    fun startRace() {
        if (pose.area != TourArea.OUTDOOR || !ready) return
        race = KartState(best = kartBest)
        jump.reset()
        pose = TourPose(race!!.x, race!!.z, race!!.yaw, -.17f, TourArea.OUTDOOR)
        onRace(race)
        onPose(pose)
    }

    fun exitRace() {
        go(TourPlan.entry(TourArea.OUTDOOR))
    }

    fun toggleDoor(index: Int) {
        if (WashroomDoors.nearest(pose) != index) return
        if (!WashroomDoors.canToggle(index, pose)) return
        doorMask = doorMask xor (1 shl index)
        onDoors(doorMask)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ready = false
        // Handles belong to the previous EGL context; do not delete recycled new IDs.
        renderedSize = 0 to 0
        postFramebuffer = 0
        postTexture = 0
        postDepth = 0
        postReady = false
        movingBuffer = 0
        movingCount = 0
        collisionMask = -1
        shadowAvailable = false
        try {
            val extensions = glGetString(GL_EXTENSIONS).orEmpty().split(' ').toSet()
            modernShaders =
                preferModernShaders && glGetString(GL_VERSION).orEmpty().contains("OpenGL ES 3")
            val gradients =
                modernShaders ||
                    "GL_EXT_shader_texture_lod" in extensions &&
                        "GL_OES_standard_derivatives" in extensions
            val sampling =
                if (gradients)
                    """
                #extension GL_EXT_shader_texture_lod : enable
                #extension GL_OES_standard_derivatives : enable
                #define DETAIL_NORMALS
                #define SAMPLE(s,uv,dx,dy) texture2DGradEXT(s,uv,dx,dy)
                #define DX(uv) dFdx(uv)
                #define DY(uv) dFdy(uv)
                #define FW(uv) fwidth(uv)
            """
                        .trimIndent()
                else
                    """
                #define SAMPLE(s,uv,dx,dy) texture2D(s,uv)
                #define DX(uv) vec2(0.)
                #define DY(uv) vec2(0.)
                #define FW(uv) vec2(.004)
            """
                        .trimIndent()
            val textureLimit = IntArray(1)
            glGetIntegerv(GL_MAX_TEXTURE_SIZE, textureLimit, 0)
            shadowSize = min(2048, textureLimit[0])
            val limit = IntArray(1)
            glGetIntegerv(GL_MAX_FRAGMENT_UNIFORM_VECTORS, limit, 0)
            maxLamps = min(14, max(1, limit[0] - 15))
            maxOccluders = max(1, min(32, limit[0] - maxLamps - 14))
            // Saved cameras from an older floor plan must not reopen inside new furniture.
            if (!TourPlan.canStand(pose.x, pose.z, world.obstacles, pose.area))
                pose = TourPlan.entry(pose.area).pose
            look.reset(pose.yaw, pose.pitch)
            val vs =
                shader(
                    GL_VERTEX_SHADER,
                    """
                uniform mat4 uMatrix; uniform mat4 uLightMatrix; uniform mat4 uModel;
                attribute vec3 aPosition; attribute vec3 aNormal; attribute vec3 aColor;
                attribute vec2 aUv; attribute float aGlow;
                varying vec3 vPosition; varying vec3 vNormal; varying vec3 vColor; varying vec3 vLocalPosition;
                varying vec2 vUv; varying float vGlow; varying vec4 vShadow;
                void main(){vec4 world=uModel*vec4(aPosition,1.0);vShadow=uLightMatrix*world;vPosition=world.xyz;vLocalPosition=aPosition;vNormal=mat3(uModel)*aNormal;vColor=aColor;vUv=aUv;vGlow=aGlow;gl_Position=uMatrix*world;}
            """
                        .trimIndent(),
                )
            val fs =
                shader(
                    GL_FRAGMENT_SHADER,
                    sampling +
                        "\n" +
                        """
                precision highp float;
                uniform sampler2D uAtlas; uniform sampler2D uShadow;
                uniform sampler2D uMaterials; uniform sampler2D uSecondary; uniform sampler2D uSkyline; uniform sampler2D uScreens;
                uniform float uShadowTexel;
                uniform float uHasShadow; uniform vec3 uCamera; uniform float uArea;
                uniform vec4 uLamps[12];uniform int uLampCount;
                uniform vec4 uOccluders[32]; uniform int uOccluderCount;
                varying vec3 vPosition; varying vec3 vNormal; varying vec3 vColor; varying vec3 vLocalPosition;
                varying vec2 vUv; varying float vGlow; varying vec4 vShadow;
                float visibility(vec3 n) {
                    vec3 p=vShadow.xyz/vShadow.w*.5+.5;
                    if(abs(n.y)<.5 || uHasShadow<.5 || p.x<0. || p.x>1. || p.y<0. || p.y>1.) return 1.;
                    float sum=0.; float bias=max(.0008,.0014*(1.-dot(n,normalize(vec3(4.,20.,9.)))));
                    for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) {
                        vec2 rg=texture2D(uShadow,p.xy+vec2(float(x),float(y))*uShadowTexel).rg;
                        sum+=step(p.z-bias,dot(rg,vec2(1.,1./255.)));
                    }
                    return sum/9.;
                }
                vec3 lamp(vec3 at,vec3 color,vec3 n,vec3 eye,float shine) {
                    vec3 delta=at-vPosition; float dist=length(delta); vec3 l=delta/dist;
                    float ndl=max(dot(n,l),0.);
                    float spec=0.;
                    if(shine>.18) spec=pow(max(dot(n,normalize(l+eye)),0.),80.)*shine;
                    return color*(ndl+spec)/(1.+.18*dist*dist);
                }
                vec3 grainNormal(vec3 n, float h) {
                    #ifdef DETAIL_NORMALS
                    vec3 px=dFdx(vPosition),py=dFdy(vPosition);
                    vec3 rx=cross(py,n),ry=cross(n,px);
                    float det=dot(px,rx);
                    vec3 gradient=sign(det)*(dFdx(h)*rx+dFdy(h)*ry);
                    return normalize(max(abs(det),.000001)*n-gradient);
                    #else
                    return n;
                    #endif
                }
                float landNoise(vec2 p) {
                    vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
                    float a=fract(sin(dot(i,vec2(127.1,311.7)))*43758.5453);
                    float b=fract(sin(dot(i+vec2(1.,0.),vec2(127.1,311.7)))*43758.5453);
                    float c=fract(sin(dot(i+vec2(0.,1.),vec2(127.1,311.7)))*43758.5453);
                    float d=fract(sin(dot(i+vec2(1.),vec2(127.1,311.7)))*43758.5453);
                    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
                }
                void main(){
                    vec3 n=normalize(vNormal); vec3 eye=normalize(uCamera-vPosition);
                    float tile=floor(vUv.x*8.)+8.*floor(vUv.y*8.);
                    vec2 plane=abs(n.y)>.65 ? vPosition.xz : (abs(n.x)>.65 ? vPosition.zy : vPosition.xy);
                    // Derive LOD from unwrapped world coordinates, never across fract() seams.
                    if(tile==35.) plane=vLocalPosition.xy;
                    float scale=tile==35. ? .34 : (tile==1. ? .5 : (tile==2. ? .8 : (tile==14. ? 2. : (tile==15. ? 1.5 : (tile==23. ? .8 : 1.)))));
                    vec2 continuous=plane*scale;
                    vec2 dx=DX(continuous)*.48,dy=DY(continuous)*.48;
                    vec2 uv=(fract(continuous)*.96+.02)*.5;
                    vec3 tex=texture2D(uAtlas,vUv).rgb;
                    if(tile==3.) tex=texture2D(uScreens,vec2((vUv.x*8.-3.-.01)/.98,(vUv.y*8.-.01)/.98)).rgb;
                    if(tile==1.) tex=SAMPLE(uMaterials,uv,dx,dy).rgb;
                    if(tile==2. || tile==35.) tex=SAMPLE(uMaterials,uv+vec2(.5,0.),dx,dy).rgb;
                    if(tile==14.) tex=mix(vec3(.90),SAMPLE(uMaterials,uv+vec2(0.,.5),dx,dy).rgb,.24);
                    if(tile==15.) tex=mix(vec3(.70),SAMPLE(uMaterials,uv+vec2(.5,.5),dx,dy).rgb,.35);
                    if(tile==11.) tex=mix(vec3(.42),SAMPLE(uSecondary,uv,dx,dy).rgb,.3);
                    if(tile==21.) tex=mix(vec3(.82),SAMPLE(uSecondary,uv+vec2(.5,0.),dx,dy).rgb,.18);
                    if(tile==22.) tex=mix(vec3(.26),SAMPLE(uSecondary,uv+vec2(0.,.5),dx,dy).rgb,.2);
                    if(tile==23.) tex=mix(vec3(.46,.48,.51),SAMPLE(uSecondary,uv+vec2(.5,.5),dx,dy).rgb,.2);
                    if(tile==34.) {
                        float patches=landNoise(vPosition.xz*.11);
                        float blades=landNoise(vPosition.xz*7.);
                        float fade=1.-smoothstep(.06,.3,length(FW(vPosition.xz)));
                        tex=mix(vec3(.66,.69,.55),vec3(.89,.92,.74),patches);
                        tex*=1.+(blades-.5)*.17*fade;
                    }
                    vec2 windowUV=vec2(vPosition.z/10.,clamp(1.-(vPosition.y-.84)/2.4,0.,1.));
                    vec2 wx=DX(windowUV),wy=DY(windowUV);
                    if(tile==12.) tex=SAMPLE(uSkyline,vec2(fract(windowUV.x),windowUV.y),wx,wy).rgb;
                    if(tile==1. && n.y>.8 && vPosition.y<.05) {
                        vec2 grid=vPosition.xz/vec2(1.2,.6);
                        vec2 footprint=max(FW(grid),vec2(.001));
                        vec2 border=min(fract(grid),1.-fract(grid));
                        vec2 joint=1.-smoothstep(vec2(.0015),vec2(.0015)+footprint,border);
                        float coverage=min(.22,.003/max(footprint.x,footprint.y));
                        tex*=1.-max(joint.x,joint.y)*coverage;
                    }
                    if(tile==32.) {
                        vec2 weave=plane*110.; vec2 footprint=FW(weave);
                        float detail=sin(weave.x*3.14159)*sin(weave.y*3.14159);
                        float patternFade=1.-smoothstep(.3,1.,max(footprint.x,footprint.y));
                        tex=vec3(.57+.10*detail*patternFade);
                    }
                    // Asphalt repeats over hundreds of metres: height derivatives at repeat
                    // seams can turn it into a visible grid. Keep its filtered albedo only.
                    if(tile==1. || tile==2. || tile==35. || tile==15. || tile==22. || tile==23.)
                        n=grainNormal(n,dot(tex,vec3(.299,.587,.114))*.018);
                    vec3 base=pow(tex*vColor,vec3(2.2));
                    float shiny=tile==32. ? .42 : (tile==21. ? .48 : (tile==1. ? .09 : ((tile==2. || tile==15.) ? .12 : .025)));
                    float shadow=visibility(n);
                    float key=max(dot(n,normalize(vec3(4.,20.,9.))),0.);
                    vec3 light=vec3(.17,.18,.20)+vec3(.32,.34,.37)*key*(.35+.65*shadow);
                    if(uArea>2.5) light=vec3(.26,.30,.34)+vec3(1.2,1.12,1.)*key*(.3+.7*shadow);
                    for(int i=0;i<12;i++) if(i<uLampCount) {
                        vec4 lampData=uLamps[i];
                        vec3 tint=lampData.w>.5 ? vec3(.85,.92,1.) : vec3(1.,.96,.89);
                        light+=lamp(vec3(lampData.x,2.78,lampData.y),tint*lampData.z,n,eye,shiny);
                    }
                    float blue=1./(1.+.18*pow(abs(vPosition.x),2.));
                    if(uArea<2.5) light+=vec3(.012,.023,.045)*blue;
                    vec3 result=base*light;
                    if(vPosition.y<.05 && n.y>.8) {
                        float contact=1.;
                        for(int i=0;i<32;i++) {
                            if(i<uOccluderCount) {
                                vec4 o=uOccluders[i];
                                float d=length(max(abs(vPosition.xz-o.xy)-o.zw*.5,vec2(0.)));
                                contact=min(contact,mix(.38,1.,smoothstep(0.,.55,d)));
                            }
                        }
                        result*=contact;
                    }
                    // View-dependent highlights on bare metal, attenuated inside cast shadows.
                    vec3 halfDir=normalize(normalize(vec3(4.,20.,9.))+eye);
                    if(shiny>.18) {
                    // GGX microfacet highlight with Schlick Fresnel; material-specific roughness.
                    float rough=tile==21. ? .28 : (tile==32. ? .32 : .57);
                    float a=rough*rough,a2=a*a;
                    float nh=max(dot(n,halfDir),0.),nv=max(dot(n,eye),.03),nl=max(dot(n,normalize(vec3(4.,20.,9.))),.03);
                    float denom=nh*nh*(a2-1.)+1.;
                    float d=a2/(3.14159*denom*denom);
                    float k=(rough+1.)*(rough+1.)/8.;
                    float g=(nv/(nv*(1.-k)+k))*(nl/(nl*(1.-k)+k));
                    vec3 f0=tile==21. ? mix(vec3(.04),base,.65) : vec3(.04);
                    vec3 fresnel=f0+(1.-f0)*pow(1.-max(dot(halfDir,eye),0.),5.);
                    result+=d*g*fresnel/(4.*nv*nl)*nl*shadow*(uArea>2.5 ? 1. : .48);
                    vec3 reflected=reflect(-eye,n);
                    vec3 environment=mix(vec3(.10,.085,.067),vec3(.22,.28,.33),smoothstep(-.2,.8,reflected.y));
                    vec2 reflectionUV=vec2(fract(atan(reflected.x,-reflected.z)/6.2831853+.5),clamp(.67-reflected.y*.85,.012,.988));
                    vec3 reflectedScene=texture2D(uSkyline,reflectionUV,rough*5.).rgb;
                    environment=mix(environment,pow(reflectedScene,vec3(2.2))*.55,.45);
                    result+=environment*shiny*pow(1.-max(dot(n,eye),0.),3.);
                    }
                    result=mix(result,base*2.3,clamp(vGlow,0.,1.));
                    result=pow(clamp((result*(2.51*result+.03))/(result*(2.43*result+.59)+.14),0.,1.),vec3(1./2.2));
                    float fog=clamp(length(vPosition-uCamera)/(uArea>2.5 ? 700. : 150.),0.,.18);
                    gl_FragColor=vec4(mix(result,uArea>2.5 ? vec3(.67,.76,.79) : vec3(.06,.07,.09),fog),1.);
                }
            """
                            .trimIndent()
                            .replace("uOccluders[32]", "uOccluders[$maxOccluders]")
                            .replace("i<32", "i<$maxOccluders")
                            .replace("uLamps[12]", "uLamps[$maxLamps]")
                            .replace("i<12", "i<$maxLamps"),
                )
            program = glCreateProgram()
            glAttachShader(program, vs)
            glAttachShader(program, fs)
            glLinkProgram(program)
            val linked = IntArray(1)
            glGetProgramiv(program, GL_LINK_STATUS, linked, 0)
            check(linked[0] != 0) { glGetProgramInfoLog(program) }
            glDeleteShader(vs)
            glDeleteShader(fs)
            glUseProgram(program)
            uMatrix = glGetUniformLocation(program, "uMatrix")
            uCamera = glGetUniformLocation(program, "uCamera")
            glUniform1f(glGetUniformLocation(program, "uArea"), pose.area.ordinal.toFloat())
            val lamps =
                when (pose.area) {
                    TourArea.BILLIARDS ->
                        listOf(-9f, -3f, 3f, 9f).flatMap { x ->
                            listOf(-10f, -3f, 4f).map { z -> floatArrayOf(x, z, 5.6f, 0f) }
                        }
                    TourArea.GAMING ->
                        PrivateRoomPlan.rooms.map { floatArrayOf(it.x, it.z, 4.5f, 0f) } +
                            listOf(0f, 7.5f, 15f).map { z -> floatArrayOf(0f, z, 9f, 1f) }
                    TourArea.LOUNGE ->
                        listOf(
                            floatArrayOf(-7.5f, 8f, 4f, 1f),
                            floatArrayOf(-7.5f, 2f, 4f, 1f),
                            floatArrayOf(-7.5f, -4f, 4f, 1f),
                            floatArrayOf(-7.5f, -10f, 4f, 1f),
                            floatArrayOf(6.5f, -8f, 4.8f, 1f),
                            floatArrayOf(6.5f, 9f, 5.8f, 0f),
                            floatArrayOf(0f, 12f, 4f, 0f),
                            floatArrayOf(14.5f, -9f, 5f, 0f),
                            floatArrayOf(14.5f, -3f, 5f, 0f),
                            floatArrayOf(14.5f, 3f, 5f, 0f),
                            floatArrayOf(14.5f, 9f, 5f, 0f),
                        )
                    TourArea.OUTDOOR ->
                        listOf(floatArrayOf(-10f, 22f, 2f, 0f), floatArrayOf(10f, 22f, 2f, 0f))
                }
            val room = WashroomDoors.rooms.first { it.area == pose.area }
            val roomLamps =
                listOf(
                    floatArrayOf(room.left + 2f, room.rowZ + 1f, 2.2f, 0f),
                    floatArrayOf(room.left + 5.8f, room.rowZ - 1f, 2.2f, 0f),
                )
            val litLamps = roomLamps + lamps
            val lampData = FloatArray(maxLamps * 4)
            litLamps.take(maxLamps).forEachIndexed { i, lamp -> lamp.copyInto(lampData, i * 4) }
            glUniform4fv(glGetUniformLocation(program, "uLamps[0]"), maxLamps, lampData, 0)
            glUniform1i(glGetUniformLocation(program, "uLampCount"), min(litLamps.size, maxLamps))
            val occluders =
                world.obstacles
                    .filter { it.w < 8f && it.d < 5f }
                    .sortedByDescending { it.w * it.d }
                    .take(if (limit[0] < 24) 0 else maxOccluders)
            val occlusion = FloatArray(maxOccluders * 4)
            occluders.forEachIndexed { i, o ->
                occlusion[i * 4] = o.x
                occlusion[i * 4 + 1] = o.z
                occlusion[i * 4 + 2] = o.w
                occlusion[i * 4 + 3] = o.d
            }
            glUniform4fv(glGetUniformLocation(program, "uOccluders[0]"), maxOccluders, occlusion, 0)
            glUniform1i(glGetUniformLocation(program, "uOccluderCount"), occluders.size)
            val ids = IntArray(1)
            glGenBuffers(1, ids, 0)
            buffer = ids[0]
            glBindBuffer(GL_ARRAY_BUFFER, buffer)
            geometry.position(0)
            glBufferData(GL_ARRAY_BUFFER, world.floatCount * 4, geometry, GL_STATIC_DRAW)
            run {
                val small = TourWorld(pose.area, false)
                small.cubicleDoor()
                movingCount = small.vertices.size / 12
                kartOffset = movingCount
                if (pose.area == TourArea.OUTDOOR) small.kart(0f, 0f, 0, false)
                kartCount = small.vertices.size / 12 - kartOffset
                val data =
                    ByteBuffer.allocateDirect(small.vertices.size * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                data.put(small.vertices.data, 0, small.vertices.size).position(0)
                glGenBuffers(1, ids, 0)
                movingBuffer = ids[0]
                glBindBuffer(GL_ARRAY_BUFFER, movingBuffer)
                glBufferData(GL_ARRAY_BUFFER, small.vertices.size * 4, data, GL_STATIC_DRAW)
                glBindBuffer(GL_ARRAY_BUFFER, buffer)
            }
            attributes =
                listOf(
                        Triple("aPosition", 3, 0),
                        Triple("aNormal", 3, 12),
                        Triple("aColor", 3, 24),
                        Triple("aUv", 2, 36),
                        Triple("aGlow", 1, 44),
                    )
                    .map { Triple(glGetAttribLocation(program, it.first), it.second, it.third) }
            createShadows()
            glUseProgram(program)
            glUniformMatrix4fv(
                glGetUniformLocation(program, "uLightMatrix"),
                1,
                false,
                lightMatrix,
                0,
            )
            glUniform1i(glGetUniformLocation(program, "uShadow"), 1)
            glUniform1f(
                glGetUniformLocation(program, "uHasShadow"),
                if (shadowAvailable) 1f else 0f,
            )
            glUniform1f(glGetUniformLocation(program, "uShadowTexel"), 1f / shadowSize)
            glGenTextures(1, ids, 0)
            texture = ids[0]
            glBindTexture(GL_TEXTURE_2D, texture)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            val atlas = atlas()
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, atlas, 0)
            glGenerateMipmap(GL_TEXTURE_2D)
            atlas.recycle()
            glUniform1i(glGetUniformLocation(program, "uAtlas"), 0)
            enableAnisotropy(extensions)
            materialTexture = uploadTexture(R.drawable.tour_materials_clean, 2048, extensions)
            secondaryTexture = uploadTexture(R.drawable.campus_materials, 2048, extensions)
            skylineTexture = uploadTexture(R.drawable.campus_skyline, 2048, extensions)
            screenTexture = createLauncherTexture(extensions)
            glUniform1i(glGetUniformLocation(program, "uMaterials"), 2)
            glUniform1i(glGetUniformLocation(program, "uSecondary"), 3)
            glUniform1i(glGetUniformLocation(program, "uSkyline"), 4)
            glUniform1i(glGetUniformLocation(program, "uScreens"), 5)
            createSky()
            createPostProgram()
            val samples = IntArray(1)
            glGetIntegerv(GL_SAMPLES, samples, 0)
            android.util.Log.i(
                "CTFLYTour",
                "${pose.area.name}: continuous texture gradients=$gradients, MSAA=${samples[0]}, shadow=$shadowSize",
            )
            check(glGetError() == GL_NO_ERROR) { "Graphics texture setup failed" }
            glEnable(GL_DEPTH_TEST)
            glDisable(GL_CULL_FACE)
            glClearColor(
                if (pose.area == TourArea.OUTDOOR) .20f else .012f,
                if (pose.area == TourArea.OUTDOOR) .27f else .018f,
                if (pose.area == TourArea.OUTDOOR) .36f else .028f,
                1f,
            )
            ready = true
            firstFrame = true
            previous = 0
        } catch (e: Exception) {
            android.util.Log.e("CTFLYTour", "Cannot initialize ${pose.area.name}", e)
            onStatus("3D could not start on this device. Return and try again.")
        }
    }

    private fun createSky() {
        val ids = IntArray(1)
        glGenBuffers(1, ids, 0)
        skyBuffer = ids[0]
        glBindBuffer(GL_ARRAY_BUFFER, skyBuffer)
        skyTriangle.position(0)
        glBufferData(GL_ARRAY_BUFFER, 24, skyTriangle, GL_STATIC_DRAW)
        val vs =
            shader(
                GL_VERTEX_SHADER,
                "attribute vec2 aSky; varying vec2 vScreen; void main(){vScreen=aSky;gl_Position=vec4(aSky,.99,1.);}",
            )
        val fs =
            shader(
                GL_FRAGMENT_SHADER,
                """
            precision highp float;
            uniform sampler2D uSkyline; uniform vec4 uView;
            varying vec2 vScreen;
            void main(){
                vec3 ray=normalize(vec3(vScreen.x*uView.z*uView.w,vScreen.y*uView.w,-1.));
                float cp=cos(uView.y),sp=sin(uView.y);
                ray=vec3(ray.x,ray.y*cp-ray.z*sp,ray.y*sp+ray.z*cp);
                float cy=cos(uView.x),sy=sin(uView.x);
                ray=vec3(ray.x*cy-ray.z*sy,ray.y,ray.x*sy+ray.z*cy);
                float longitude=atan(ray.x,-ray.z)/6.2831853;
                float u=fract(longitude*3.+.5);
                float v=clamp(.67-ray.y*.85,.012,.988);
                // Open daytime sky over a ground plane; no high-rise photo horizon.
                vec3 sky=mix(vec3(.78,.84,.85),vec3(.31,.55,.76),smoothstep(-.02,.7,ray.y));
                gl_FragColor=vec4(sky,1.);
            }
        """
                    .trimIndent(),
            )
        skyProgram = glCreateProgram()
        glAttachShader(skyProgram, vs)
        glAttachShader(skyProgram, fs)
        glLinkProgram(skyProgram)
        val linked = IntArray(1)
        glGetProgramiv(skyProgram, GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { glGetProgramInfoLog(skyProgram) }
        glDeleteShader(vs)
        glDeleteShader(fs)
    }

    private fun drawSky(p: TourPose) {
        glUseProgram(skyProgram)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, skylineTexture)
        glUniform1i(glGetUniformLocation(skyProgram, "uSkyline"), 0)
        glUniform4f(
            glGetUniformLocation(skyProgram, "uView"),
            p.yaw,
            p.pitch,
            viewportWidth.toFloat() / max(1, viewportHeight),
            tan(fieldOfView * PI.toFloat() / 360),
        )
        glBindBuffer(GL_ARRAY_BUFFER, skyBuffer)
        val a = glGetAttribLocation(skyProgram, "aSky")
        glEnableVertexAttribArray(a)
        glVertexAttribPointer(a, 2, GL_FLOAT, false, 8, 0)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glDisableVertexAttribArray(a)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
    }

    /** Bake a filtered 16-bit depth map once per GL context; no per-frame shadow pass. */
    private fun createShadows() {
        val lp = FloatArray(16)
        val lv = FloatArray(16)
        val span = max(pose.area.halfWidth, pose.area.halfDepth) * 1.5f
        Matrix.orthoM(
            lp,
            0,
            -span,
            span,
            -span,
            span,
            if (pose.area == TourArea.OUTDOOR) -10f else 1f,
            120f,
        )
        Matrix.setLookAtM(lv, 0, 4f, 36f, 9f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(lightMatrix, 0, lp, 0, lv, 0)
        val ids = IntArray(1)
        glGenTextures(1, ids, 0)
        shadowTexture = ids[0]
        glBindTexture(GL_TEXTURE_2D, shadowTexture)
        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            shadowSize,
            shadowSize,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            null,
        )
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glGenFramebuffers(1, ids, 0)
        val fbo = ids[0]
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            shadowTexture,
            0,
        )
        glGenRenderbuffers(1, ids, 0)
        val depth = ids[0]
        glBindRenderbuffer(GL_RENDERBUFFER, depth)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, shadowSize, shadowSize)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth)
        shadowAvailable = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE
        if (shadowAvailable) {
            val vs =
                shader(
                    GL_VERTEX_SHADER,
                    "uniform mat4 uLight; attribute vec3 aPosition; varying float height; void main(){height=aPosition.y;gl_Position=uLight*vec4(aPosition,1.);}",
                )
            val fs =
                shader(
                    GL_FRAGMENT_SHADER,
                    "precision highp float; varying float height; void main(){if(height>3.12 || height<.04) discard; vec2 enc=fract(vec2(1.,255.)*gl_FragCoord.z); enc.x-=enc.y/255.;gl_FragColor=vec4(enc,0.,1.);}",
                )
            val prog = glCreateProgram()
            glAttachShader(prog, vs)
            glAttachShader(prog, fs)
            glLinkProgram(prog)
            val linked = IntArray(1)
            glGetProgramiv(prog, GL_LINK_STATUS, linked, 0)
            check(linked[0] != 0) { glGetProgramInfoLog(prog) }
            glUseProgram(prog)
            glUniformMatrix4fv(glGetUniformLocation(prog, "uLight"), 1, false, lightMatrix, 0)
            glViewport(0, 0, shadowSize, shadowSize)
            // Dithering corrupts packed depth channels and produces striped self-shadowing.
            glDisable(GL_DITHER)
            glEnable(GL_DEPTH_TEST)
            glDisable(GL_CULL_FACE)
            glClearColor(1f, 0f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            val a = glGetAttribLocation(prog, "aPosition")
            glEnableVertexAttribArray(a)
            glVertexAttribPointer(a, 3, GL_FLOAT, false, 48, 0)
            glDrawArrays(GL_TRIANGLES, 0, vertexCount)
            glDisableVertexAttribArray(a)
            glDeleteProgram(prog)
            glDeleteShader(vs)
            glDeleteShader(fs)
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        glDeleteRenderbuffers(1, intArrayOf(depth), 0)
    }

    private fun shader(kind: Int, source: String): Int {
        val id = glCreateShader(kind)
        val code =
            if (modernShaders) {
                val body =
                    source
                        .lineSequence()
                        .filterNot { it.trimStart().startsWith("#extension") }
                        .joinToString("\n")
                        .replace(Regex("\\battribute\\b"), "in")
                        .replace(
                            Regex("\\bvarying\\b"),
                            if (kind == GL_VERTEX_SHADER) "out" else "in",
                        )
                        .replace("texture2DGradEXT", "textureGrad")
                        .replace(Regex("\\btexture2D\\b"), "texture")
                        .replace("gl_FragColor", "fragColor")
                "#version 300 es\n" +
                    (if (kind == GL_FRAGMENT_SHADER) "out highp vec4 fragColor;\n" else "") +
                    body
            } else source
        glShaderSource(id, code)
        glCompileShader(id)
        val status = IntArray(1)
        glGetShaderiv(id, GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { glGetShaderInfoLog(id) }
        return id
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        firstFrame = true
        onStatus("loading")
        viewportWidth = width
        viewportHeight = height
        android.util.Log.i("CTFLYTour", "${pose.area.name}: render surface ${width}x$height")
        fieldOfView = if (width > height) 68f else 78f
        glViewport(0, 0, width, height)
        Matrix.perspectiveM(
            projection,
            0,
            if (width > height) 68f else 78f,
            width.toFloat() / max(1, height),
            .07f,
            if (pose.area == TourArea.OUTDOOR) 650f else 120f,
        )
        createPostSurface(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!ready) return
        val now = System.nanoTime()
        val dt = if (previous == 0L || experiencePaused) 0f else (now - previous) / 1e9f
        previous = now
        val driving = race
        if (driving != null) {
            val advanced = KartPhysics.step(driving, strafe, throttle, brake, false, dt, reverse)
            race = advanced
            // Publish short drift transitions before a slow GPU frame can hide the HUD change.
            if (advanced.drifting != driving.drifting) onRace(advanced)
            if (advanced.best > 0f) kartBest = advanced.best
            pose = TourPose(advanced.x, advanced.z, advanced.yaw, -.17f, TourArea.OUTDOOR)
        } else {
            look.step(dt)
            pose = pose.copy(yaw = look.yaw, pitch = look.pitch)
            var movingDoors = false
            for (door in WashroomDoors.inArea(pose.area)) {
                val target = if (doorMask and (1 shl door.index) != 0) 90f else 0f
                val old = doorAngles[door.index]
                doorAngles[door.index] = old + (target - old).coerceIn(-dt * 300f, dt * 300f)
                if (old != target) movingDoors = true
            }
            if (collisionMask != doorMask || movingDoors) {
                collisionMask = doorMask
                doorCollision =
                    world.obstacles +
                        WashroomDoors.inArea(pose.area).map {
                            val angle = doorAngles[it.index]
                            if (angle > 0f && angle < 90f) it.sweep
                            else it.obstacle(doorMask and (1 shl it.index) != 0)
                        }
            }
            pose =
                TourPlan.move(pose, strafe, forward, dt, doorCollision, if (sprint) 7.2f else 4.5f)
            jump.step(dt)
        }
        val p = pose
        val eye = if (driving != null) 2.8f else 1.65f + jump.height
        val cameraX = if (driving != null) p.x - sin(p.yaw) * 5f else p.x
        val cameraZ = if (driving != null) p.z + cos(p.yaw) * 5f else p.z
        Matrix.setLookAtM(
            view,
            0,
            cameraX,
            eye,
            cameraZ,
            if (driving != null) p.x + sin(p.yaw) * 5f else p.x + sin(p.yaw) * cos(p.pitch),
            if (driving != null) .65f else eye + sin(p.pitch),
            if (driving != null) p.z - cos(p.yaw) * 5f else p.z - cos(p.yaw) * cos(p.pitch),
            0f,
            1f,
            0f,
        )
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, if (postReady) postFramebuffer else 0)
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        if (p.area == TourArea.OUTDOOR) drawSky(p)
        glUseProgram(program)
        glUniformMatrix4fv(uMatrix, 1, false, vp, 0)
        Matrix.setIdentityM(model, 0)
        glUniformMatrix4fv(glGetUniformLocation(program, "uModel"), 1, false, model, 0)
        glUniform3f(uCamera, cameraX, eye, cameraZ)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, texture)
        glBindBuffer(GL_ARRAY_BUFFER, buffer)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, shadowTexture)
        for ((unit, id) in
            listOf(
                2 to materialTexture,
                3 to secondaryTexture,
                4 to skylineTexture,
                5 to screenTexture,
            )) {
            glActiveTexture(GL_TEXTURE0 + unit)
            glBindTexture(GL_TEXTURE_2D, id)
        }
        glActiveTexture(GL_TEXTURE0)
        for ((a, size, offset) in attributes) {
            glEnableVertexAttribArray(a)
            glVertexAttribPointer(a, size, GL_FLOAT, false, 48, offset)
        }
        glDrawArrays(GL_TRIANGLES, 0, vertexCount)
        if (movingBuffer != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, movingBuffer)
            for ((a, size, offset) in attributes) glVertexAttribPointer(
                a,
                size,
                GL_FLOAT,
                false,
                48,
                offset,
            )
            run {
                WashroomDoors.inArea(p.area).forEach { door ->
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, door.x, 0f, door.z)
                    Matrix.rotateM(model, 0, doorAngles[door.index], 0f, 1f, 0f)
                    glUniformMatrix4fv(glGetUniformLocation(program, "uModel"), 1, false, model, 0)
                    glDrawArrays(GL_TRIANGLES, 0, movingCount)
                }
            }
            if (driving != null) {
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, p.x, 0f, p.z)
                Matrix.rotateM(model, 0, -p.yaw * 180f / PI.toFloat(), 0f, 1f, 0f)
                glUniformMatrix4fv(glGetUniformLocation(program, "uModel"), 1, false, model, 0)
                glDrawArrays(GL_TRIANGLES, kartOffset, kartCount)
            }
        }
        if (postReady) drawPost()
        if (firstFrame) {
            glFinish()
            val error = glGetError()
            if (error != GL_NO_ERROR) {
                ready = false
                android.util.Log.e("CTFLYTour", "First frame failed: GL $error")
                onStatus("3D could not render on this device. Return and try again.")
                return
            }
            firstFrame = false
            renderedSize = viewportWidth to viewportHeight
            onStatus("ready")
        }
        if (now - reported > 250_000_000L) {
            reported = now
            onPose(p)
            onRace(race)
        }
        // Limit mobile GPU usage to approximately 30 FPS, and skip long pause deltas above.
        val remaining = 33_333_333L - (System.nanoTime() - now)
        if (remaining > 1_000_000L) Thread.sleep(remaining / 1_000_000L)
    }

    /** Edge-only FXAA: available even when the display offers no MSAA configuration. */
    private fun createPostProgram() {
        val vs =
            shader(
                GL_VERTEX_SHADER,
                "attribute vec2 aSky; varying vec2 vScreen; void main(){vScreen=aSky*.5+.5;gl_Position=vec4(aSky,0.,1.);}",
            )
        val fs =
            shader(
                GL_FRAGMENT_SHADER,
                """
            precision highp float;
            uniform sampler2D uScene; uniform vec2 uTexel; varying vec2 vScreen;
            void main(){
                vec2 uv=vScreen;
                vec3 c=texture2D(uScene,uv).rgb;
                vec3 nw=texture2D(uScene,uv+vec2(-1.,-1.)*uTexel).rgb;
                vec3 ne=texture2D(uScene,uv+vec2(1.,-1.)*uTexel).rgb;
                vec3 sw=texture2D(uScene,uv+vec2(-1.,1.)*uTexel).rgb;
                vec3 se=texture2D(uScene,uv+vec2(1.,1.)*uTexel).rgb;
                vec3 lum=vec3(.299,.587,.114);
                float lc=dot(c,lum),lnw=dot(nw,lum),lne=dot(ne,lum),lsw=dot(sw,lum),lse=dot(se,lum);
                float lo=min(lc,min(min(lnw,lne),min(lsw,lse))),hi=max(lc,max(max(lnw,lne),max(lsw,lse)));
                if(hi-lo<max(.035,hi*.12)){gl_FragColor=vec4(c,1.);return;}
                vec2 dir=vec2(-((lnw+lne)-(lsw+lse)),(lnw+lsw)-(lne+lse));
                float reduce=max((lnw+lne+lsw+lse)*.03125,.0078125);
                dir=clamp(dir/(min(abs(dir.x),abs(dir.y))+reduce),vec2(-6.),vec2(6.))*uTexel;
                vec3 a=.5*(texture2D(uScene,uv-dir/6.).rgb+texture2D(uScene,uv+dir/6.).rgb);
                vec3 b=a*.5+.25*(texture2D(uScene,uv-dir*.5).rgb+texture2D(uScene,uv+dir*.5).rgb);
                float lb=dot(b,lum);
                gl_FragColor=vec4(lb<lo || lb>hi ? a : b,1.);
            }
        """
                    .trimIndent(),
            )
        postProgram = glCreateProgram()
        glAttachShader(postProgram, vs)
        glAttachShader(postProgram, fs)
        glLinkProgram(postProgram)
        val linked = IntArray(1)
        glGetProgramiv(postProgram, GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { glGetProgramInfoLog(postProgram) }
        glDeleteShader(vs)
        glDeleteShader(fs)
    }

    private fun releasePostSurface() {
        postReady = false
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glDeleteFramebuffers(1, intArrayOf(postFramebuffer), 0)
        glDeleteTextures(1, intArrayOf(postTexture), 0)
        glDeleteRenderbuffers(1, intArrayOf(postDepth), 0)
        postFramebuffer = 0
        postTexture = 0
        postDepth = 0
    }

    private fun createPostSurface(width: Int, height: Int) {
        releasePostSurface()
        if (!ready) return
        val ids = IntArray(1)
        glGenTextures(1, ids, 0)
        postTexture = ids[0]
        glBindTexture(GL_TEXTURE_2D, postTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glGenRenderbuffers(1, ids, 0)
        postDepth = ids[0]
        glBindRenderbuffer(GL_RENDERBUFFER, postDepth)
        val depth24 =
            modernShaders || glGetString(GL_EXTENSIONS).orEmpty().contains("GL_OES_depth24")
        glRenderbufferStorage(
            GL_RENDERBUFFER,
            if (depth24) 0x81A6 else GL_DEPTH_COMPONENT16,
            width,
            height,
        )
        glGenFramebuffers(1, ids, 0)
        postFramebuffer = ids[0]
        glBindFramebuffer(GL_FRAMEBUFFER, postFramebuffer)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, postTexture, 0)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, postDepth)
        postReady =
            glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE &&
                glGetError() == GL_NO_ERROR
        if (!postReady) {
            // A low-memory/older GPU still gets a usable direct render instead of a black screen.
            while (glGetError() != GL_NO_ERROR) {}
            releasePostSurface()
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        android.util.Log.i(
            "CTFLYTour",
            "${pose.area.name}: edge antialiasing=$postReady, GLES3=$modernShaders",
        )
    }

    private fun drawPost() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glUseProgram(postProgram)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, postTexture)
        glUniform1i(glGetUniformLocation(postProgram, "uScene"), 0)
        glUniform2f(
            glGetUniformLocation(postProgram, "uTexel"),
            1f / viewportWidth,
            1f / viewportHeight,
        )
        // World and fullscreen attributes can share numeric locations; reset their enable state.
        for ((a, _, _) in attributes) glDisableVertexAttribArray(a)
        glBindBuffer(GL_ARRAY_BUFFER, skyBuffer)
        val a = glGetAttribLocation(postProgram, "aSky")
        glEnableVertexAttribArray(a)
        glVertexAttribPointer(a, 2, GL_FLOAT, false, 8, 0)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glDisableVertexAttribArray(a)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
    }

    /** One sharp, original widescreen launcher texture shared by every monitor. */
    private fun createLauncherTexture(extensions: Set<String>): Int {
        val bitmap = Bitmap.createBitmap(1024, 576, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(0xFF16262B.toInt())
        val artwork = BitmapFactory.decodeResource(context.resources, R.drawable.tournament_banner)
        canvas.drawBitmap(artwork, null, RectF(330f, 62f, 1024f, 436f), paint)
        paint.shader =
            LinearGradient(
                240f,
                0f,
                760f,
                0f,
                intArrayOf(0xFF16262B.toInt(), 0x0016262B),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 62f, 1024f, 436f, paint)
        paint.shader = null
        fun text(label: String, x: Float, y: Float, size: Float, color: Int = 0xFFE9F1F0.toInt()) {
            paint.color = color
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText(label, x, y, paint)
        }
        paint.color = 0xFFB9E674.toInt()
        canvas.drawCircle(34f, 33f, 13f, paint)
        text("CTFLY", 59f, 40f, 23f)
        text("Home", 230f, 40f, 20f)
        text("Library", 337f, 40f, 20f, 0xFF8FA9AF.toInt())
        text("Friends", 456f, 40f, 20f, 0xFF8FA9AF.toInt())
        text("19:00", 936f, 40f, 18f)
        text("READY WHEN YOU ARE", 32f, 178f, 17f, 0xFFB9E674.toInt())
        text("Your next", 32f, 226f, 40f)
        text("great game.", 32f, 273f, 40f)
        text("Play together. Stay a while.", 32f, 309f, 18f, 0xFF9AB2B8.toInt())
        paint.color = 0xFFB9E674.toInt()
        canvas.drawRoundRect(32f, 339f, 228f, 385f, 8f, 8f, paint)
        text("LET'S PLAY", 58f, 369f, 20f, 0xFF17262C.toInt())
        val titles = listOf("ARENA", "DRIVE", "CLUB", "DISCOVER")
        val colors =
            listOf(0xFF416976.toInt(), 0xFF477468.toInt(), 0xFF656383.toInt(), 0xFF886B4D.toInt())
        for (i in 0..3) {
            val x = 24f + i * 250f
            paint.color = colors[i]
            canvas.drawRoundRect(x, 433f, x + 230f, 556f, 9f, 9f, paint)
            paint.color = 0x337CF0DD
            canvas.drawCircle(x + 181f, 464f, 44f, paint)
            paint.color = 0x338CB6DC
            canvas.drawCircle(x + 168f, 499f, 30f, paint)
            text(titles[i], x + 16f, 518f, 22f)
            text(
                if (i == 0) "Join the queue"
                else if (i == 1) "One more lap"
                else if (i == 2) "Your favourites" else "Something new",
                x + 16f,
                542f,
                14f,
                0xFFC5D2D7.toInt(),
            )
        }
        artwork.recycle()
        val ids = IntArray(1)
        glGenTextures(1, ids, 0)
        glBindTexture(GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glGenerateMipmap(GL_TEXTURE_2D)
        enableAnisotropy(extensions)
        bitmap.recycle()
        return ids[0]
    }

    private fun atlas(): Bitmap {
        val maxTexture = IntArray(1)
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, maxTexture, 0)
        val width = min(2048, maxTexture[0])
        val bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / 2048f, width / 2048f)
        val secondary = BitmapFactory.decodeResource(context.resources, R.drawable.campus_materials)
        val materials = BitmapFactory.decodeResource(context.resources, R.drawable.tour_materials)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val random = kotlin.random.Random(47)
        fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
            paint.color = color
            canvas.drawRect(x, y, x + w, y + h, paint)
        }
        fun text(s: String, y: Float, size: Float, color: Int = Color.WHITE) {
            paint.color = color
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textScaleX = min(1f, 228f / max(1f, paint.measureText(s)))
            canvas.drawText(s, 128f, y, paint)
            paint.textScaleX = 1f
        }
        for (tile in 0..63) {
            canvas.save()
            canvas.translate((tile % 8) * 256f, (tile / 8) * 256f)
            canvas.clipRect(0, 0, 256, 256)
            rect(0f, 0f, 256f, 256f, Color.WHITE)
            when (tile) {
                32 -> {
                    rect(0f, 0f, 256f, 256f, 0xFF515960.toInt())
                    for (i in 0 until 32) for (j in 0 until 32) rect(
                        i * 8f,
                        j * 8f,
                        if ((i + j) % 2 == 0) 7f else 3f,
                        if ((i + j) % 2 == 0) 3f else 7f,
                        0xFF3D454C.toInt(),
                    )
                }
                33 -> {
                    val art =
                        BitmapFactory.decodeResource(context.resources, R.drawable.kart_circuit)
                    canvas.drawBitmap(art, null, RectF(0f, 0f, 256f, 220f), paint)
                    art.recycle()
                    rect(0f, 196f, 256f, 60f, 0xFF111B25.toInt())
                    text("SIM / CTFLY", 223f, 21f, 0xFFBED8CF.toInt())
                    text("READY TO DRIVE", 246f, 15f, 0xFF93A7B8.toInt())
                }
                34 -> {
                    paint.shader =
                        RadialGradient(
                            128f,
                            116f,
                            190f,
                            intArrayOf(0xFFE6F0F3.toInt(), 0xFF8CC4DE.toInt(), 0xFF294F81.toInt()),
                            null,
                            Shader.TileMode.CLAMP,
                        )
                    canvas.drawRect(0f, 0f, 256f, 256f, paint)
                    paint.shader = null
                    for (i in 0..24) {
                        paint.color = 0xFFE8F4F7.toInt()
                        canvas.drawCircle(
                            random.nextFloat() * 256,
                            random.nextFloat() * 256,
                            1.0f,
                            paint,
                        )
                    }
                }
                3 -> {
                    val artwork =
                        BitmapFactory.decodeResource(
                            context.resources,
                            R.drawable.tournament_banner,
                        )
                    canvas.drawBitmap(artwork, null, RectF(0f, 0f, 256f, 256f), paint)
                    artwork.recycle()
                }
                12,
                20 -> {
                    val skyline =
                        BitmapFactory.decodeResource(context.resources, R.drawable.campus_skyline)
                    canvas.drawBitmap(skyline, null, RectF(0f, 0f, 256f, 256f), paint)
                    skyline.recycle()
                }
                11,
                21,
                22,
                23 -> {
                    val q =
                        when (tile) {
                            11 -> 0
                            21 -> 1
                            22 -> 2
                            else -> 3
                        }
                    val half = secondary.width / 2
                    canvas.drawBitmap(
                        secondary,
                        Rect(q % 2 * half, q / 2 * half, (q % 2 + 1) * half, (q / 2 + 1) * half),
                        RectF(0f, 0f, 256f, 256f),
                        paint,
                    )
                }
                else ->
                    if (tile != 0) {
                        rect(0f, 0f, 256f, 256f, 0xFF17252D.toInt())
                        rect(10f, 12f, 236f, 3f, 0xFFC6F56B.toInt())
                        rect(10f, 241f, 236f, 3f, 0xFF7EDFCC.toInt())
                        val labels =
                            mapOf(
                                4 to "CTFLY",
                                5 to "VIP ROOM",
                                6 to "POOL CLUB",
                                7 to "WASHROOM",
                                8 to "CAFÉ",
                                9 to "LIFT",
                                10 to "CIRCUIT",
                                13 to "PC ARENA",
                                16 to "CONSOLE SUITES",
                                17 to "SIMULATOR LAB",
                                18 to "ARCADE",
                                19 to "SNOOKER",
                                20 to "EXIT",
                                36 to "TOILET",
                                37 to "SHOWER",
                            )
                        text(
                            labels[tile] ?: if (tile >= 24) "0${tile-23}" else "CTFLY",
                            150f,
                            if (tile >= 24 && tile !in labels) 120f else 86f,
                        )
                        text(
                            if (tile == 4) "GAMING CAFE" else "CTFLY  /  EXPLORE",
                            211f,
                            27f,
                            0xFF7EDFCC.toInt(),
                        )
                    }
            }
            val quadrant =
                when (tile) {
                    1 -> 0
                    2 -> 1
                    14 -> 2
                    15 -> 3
                    else -> -1
                }
            if (quadrant >= 0) {
                val half = materials.width / 2
                canvas.drawBitmap(
                    materials,
                    Rect(
                        (quadrant % 2) * half,
                        (quadrant / 2) * half,
                        (quadrant % 2 + 1) * half,
                        (quadrant / 2 + 1) * half,
                    ),
                    RectF(0f, 0f, 256f, 256f),
                    paint,
                )
                if (tile == 1 || tile == 11) {
                    paint.color = 0xFF8F8C85.toInt()
                    paint.strokeWidth = 1f
                    val step = if (tile == 11) 64 else 256
                    for (i in 0..256 step step) {
                        canvas.drawLine(i.toFloat(), 0f, i.toFloat(), 256f, paint)
                        canvas.drawLine(0f, i.toFloat(), 256f, i.toFloat(), paint)
                    }
                }
            }
            canvas.restore()
        }
        materials.recycle()
        secondary.recycle()
        return bitmap
    }

    private fun enableAnisotropy(extensions: Set<String>) {
        if ("GL_EXT_texture_filter_anisotropic" !in extensions) return
        val maximum = FloatArray(1)
        glGetFloatv(0x84FF, maximum, 0)
        glTexParameterf(GL_TEXTURE_2D, 0x84FE, min(8f, maximum[0]))
    }

    private fun uploadTexture(resource: Int, preferredSize: Int, extensions: Set<String>): Int {
        val maximum = IntArray(1)
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, maximum, 0)
        val size = min(preferredSize, maximum[0])
        val original = BitmapFactory.decodeResource(context.resources, resource)
        val scaled = Bitmap.createScaledBitmap(original, size, size, true)
        val ids = IntArray(1)
        glGenTextures(1, ids, 0)
        glBindTexture(GL_TEXTURE_2D, ids[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        enableAnisotropy(extensions)
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, scaled, 0)
        glGenerateMipmap(GL_TEXTURE_2D)
        if (scaled !== original) scaled.recycle()
        original.recycle()
        return ids[0]
    }
}
