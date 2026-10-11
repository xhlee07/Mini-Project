package com.ctfly.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

private tailrec fun android.content.Context.tourActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.tourActivity()
        else -> null
    }

private fun TourPose.saved() = "$x,$z,$yaw,$pitch,${area.name}"

private fun restoredPose(value: String): TourPose =
    runCatching {
            val parts = value.split(',')
            val v = parts.take(4).map { it.toFloat() }
            require(v.size == 4 && v.all { it.isFinite() })
            val area = TourArea.parse(parts.getOrNull(4))
            require(abs(v[0]) < area.halfWidth && abs(v[1]) < area.halfDepth)
            TourPose(v[0], v[1], v[2], v[3].coerceIn(-1.1f, 1.1f), area)
        }
        .getOrDefault(TourPose())

@Composable
fun TourScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = context.tourActivity()
    DisposableEffect(activity, vm) {
        if (activity != null) {
            if (vm.field("tourReturnOrientation").isBlank())
                vm.set("tourReturnOrientation", activity.requestedOrientation.toString())
            activity.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation =
                    vm.field("tourReturnOrientation").toIntOrNull()
                        ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                vm.set("tourReturnOrientation", "")
            }
        }
    }
    if (
        androidx.compose.ui.platform.LocalConfiguration.current.orientation !=
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        Box(
            Modifier.fillMaxSize().background(Ink).safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                AppText("Opening the venue…")
            }
        }
        return
    }
    val density = LocalDensity.current.density
    val racePrefs = remember {
        context.getSharedPreferences("ctfly_tour", android.content.Context.MODE_PRIVATE)
    }
    var pose by remember { mutableStateOf(restoredPose(vm.field("tourPose"))) }
    var status by remember { mutableStateOf("loading") }
    var map by rememberSaveable { mutableStateOf(vm.field("tourRace").isNotBlank()) }
    var help by rememberSaveable { mutableStateOf(false) }
    var lift by rememberSaveable { mutableStateOf(false) }
    var graphics by rememberSaveable { mutableStateOf(false) }
    var destinations by rememberSaveable { mutableStateOf(false) }
    var immersiveOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var restartConfirmation by rememberSaveable { mutableStateOf(false) }
    var sprint by rememberSaveable { mutableStateOf(false) }
    var race by remember {
        mutableStateOf(
            KartState.restore(vm.field("tourRace"))?.takeIf { pose.area == TourArea.OUTDOOR }
        )
    }
    var doors by remember {
        mutableIntStateOf(vm.field("tourDoors").toIntOrNull()?.and(WashroomDoors.validMask) ?: 0)
    }
    val quality = TourQuality.parse(vm.field("tourQuality"))
    val surface = remember {
        TourSurface(
            context,
            pose,
            { p ->
                pose = p
                vm.set("tourPose", p.saved())
            },
            { status = it },
            quality,
            initialRace = race,
            onRace = { r ->
                if (r != null && race == null) map = true
                race = r
                vm.set("tourRace", r?.saved().orEmpty())
                if (r != null && r.best > 0f) vm.set("tourKartBest", r.best.toString())
                if (r != null && r.best > 0f) {
                    val best = racePrefs.getFloat("kart_best", 0f)
                    if (best == 0f || r.best < best)
                        racePrefs.edit().putFloat("kart_best", r.best).apply()
                }
            },
            initialDoors = doors,
            onDoors = { d ->
                doors = d
                vm.set("tourDoors", d.toString())
            },
            initialBest =
                vm.field("tourKartBest").toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
                    ?: racePrefs.getFloat("kart_best", 0f),
        )
    }
    SideEffect {
        surface.tour.sprint = sprint
        surface.tour.experiencePaused =
            graphics || help || lift || destinations || restartConfirmation
        if (graphics || help || lift || destinations || restartConfirmation) surface.stopInput()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(surface, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surface.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    surface.stopInput()
                    vm.set("tourPose", surface.tour.pose.saved())
                    vm.set("tourRace", surface.tour.race?.saved().orEmpty())
                    surface.onPause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) surface.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            surface.detachCallbacks()
            surface.stopInput()
            vm.set("tourPose", surface.tour.pose.saved())
            vm.set("tourRace", surface.tour.race?.saved().orEmpty())
            surface.onPause()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        val compactLandscape = maxWidth > maxHeight * 1.8f && maxHeight < 500.dp
        val phoneLayout = maxHeight < 500.dp
        val immersive = phoneLayout || (immersiveOverride ?: compactLandscape)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.set("page", "Discover") }) {
                    AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Leave 3D tour")
                }
                Column(Modifier.weight(1f)) {
                    AppText(
                        "CTFLY / THE CAMPUS",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = if (phoneLayout) 14.sp else TextUnit.Unspecified,
                        lineHeight = if (phoneLayout) 20.sp else TextUnit.Unspecified,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        "${pose.area.floor} · ${pose.area.label} · ${if(race==null)"First-person tour" else "Kart time trial"}",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = if (phoneLayout) 14.sp else TextUnit.Unspecified,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (phoneLayout && race != null)
                    TourRaceActions(surface, { restartConfirmation = true })
                IconButton(onClick = { map = !map }) {
                    AppIcon(Icons.Outlined.Map, "Toggle floor map", tint = if (map) Lime else Muted)
                }
                IconButton(onClick = { graphics = true }) {
                    AppIcon(Icons.Outlined.HighQuality, "Tour picture quality")
                }
                if (!phoneLayout || race == null)
                    IconButton(
                        onClick = {
                            if (phoneLayout) destinations = true else immersiveOverride = !immersive
                        }
                    ) {
                        AppIcon(
                            if (phoneLayout) Icons.Outlined.Place
                            else if (immersive) Icons.Outlined.FullscreenExit
                            else Icons.Outlined.Fullscreen,
                            if (phoneLayout || immersive) "Show tour destinations"
                            else "Expand 3D view",
                        )
                    }
                IconButton(onClick = { help = true }) {
                    AppIcon(Icons.Outlined.HelpOutline, "Tour controls")
                }
            }
            if (race != null && !phoneLayout)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        "Brake + steer = drift",
                        Modifier.weight(1f),
                        fontSize = 11.sp,
                        color = Muted,
                    )
                    TourRaceActions(surface, { restartConfirmation = true })
                }
            if (!immersive && race == null)
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TourArea.entries.forEach { area ->
                        FilterChip(
                            selected = pose.area == area,
                            onClick = {
                                surface.stopInput()
                                surface.queueEvent { surface.tour.go(TourPlan.entry(area)) }
                            },
                            enabled = status == "ready",
                            label = { AppText("${area.floor} ${area.label}", fontSize = 12.sp) },
                        )
                    }
                }
            if (!immersive && race == null)
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TourPlan.stops
                        .filter { it.pose.area == pose.area }
                        .forEach { stop ->
                            AssistChip(
                                onClick = {
                                    surface.stopInput()
                                    surface.queueEvent { surface.tour.go(stop) }
                                },
                                label = { AppText(stop.name) },
                                enabled = status == "ready",
                            )
                        }
                }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val compactMap = maxWidth > maxHeight
                val pedalHeight =
                    if (phoneLayout) (maxHeight * 0.40f).coerceIn(80.dp, 92.dp) else 132.dp
                val tightHud =
                    phoneLayout && (maxHeight < 220.dp || LocalDensity.current.fontScale > 1.3f)

                AndroidView(
                    factory = { surface },
                    modifier = Modifier.fillMaxSize().testTag("tour3d"),
                )
                // Fingers own their input regions; another thumb staying down cannot block a new
                // swipe.
                Box(
                    Modifier.fillMaxSize().testTag("tourLook").pointerInput(surface, density) {
                        awaitPointerEventScope {
                            var active: PointerId? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                if (active == null)
                                    event.changes
                                        .firstOrNull { it.changedToDown() }
                                        ?.let {
                                            active = it.id
                                            it.consume()
                                        }
                                val finger = event.changes.firstOrNull { it.id == active }
                                if (finger != null) {
                                    if (!finger.pressed) active = null
                                    else {
                                        val delta = finger.positionChange()
                                        if (delta != Offset.Zero) {
                                            surface.addLook(
                                                delta.x / density * 0.006f,
                                                -delta.y / density * 0.006f,
                                            )
                                            finger.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
                if (status == "ready") {
                    val nearest =
                        TourPlan.stops
                            .filter { it.pose.area == pose.area }
                            .minBy { (it.pose.x - pose.x).pow(2) + (it.pose.z - pose.z).pow(2) }
                    val room =
                        if (pose.area == TourArea.GAMING)
                            PrivateRoomPlan.rooms.firstOrNull {
                                kotlin.math.abs(pose.x - it.x) < 3.7f &&
                                    kotlin.math.abs(pose.z - it.z) < 3.2f
                            }
                        else null
                    val placeName =
                        room?.name
                            ?: when {
                                pose.area == TourArea.LOUNGE && pose.x > 11.7f -> "VIP simulator"
                                pose.area == TourArea.LOUNGE &&
                                    pose.x in 3f..10f &&
                                    pose.z in -10f..1f -> "Simulator lab"
                                else -> nearest.name
                            }
                    Surface(
                        Modifier.align(Alignment.TopStart)
                            .padding(8.dp)
                            .widthIn(max = if (phoneLayout) 208.dp else 240.dp)
                            .testTag("tourHud"),
                        color = Ink.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            Modifier.padding(
                                horizontal = if (phoneLayout) 8.dp else 12.dp,
                                vertical = if (phoneLayout) 4.dp else 12.dp,
                            )
                        ) {
                            val kart = race
                            if (kart == null) {
                                AppText(
                                    placeName,
                                    color = Lime,
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                AppText(
                                    "Walk freely • Drag to look around",
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                AppText(
                                    "${(kart.speed*3.6f).roundToInt()} km/h · Lap ${kart.laps+1}",
                                    fontSize = 16.sp,
                                    lineHeight = 20.sp,
                                    maxLines = 1,
                                )
                                if (!tightHud)
                                    AppText(
                                        "Time ${raceTime(kart.lapTime)} · Best ${if(kart.best==0f)"—" else raceTime(kart.best)}",
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = Muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                AppText(
                                    if (kart.countdown > 0)
                                        "Ready · ${ceil(kart.countdown).toInt()}"
                                    else if (kart.drifting) "DRIFT"
                                    else if (tightHud)
                                        "Time ${raceTime(kart.lapTime)} · Best ${if(kart.best==0f)"—" else raceTime(kart.best)}"
                                    else
                                        "${KartTrack.length.roundToInt()} m circuit · ${kart.checkpoint-1}/8 gates",
                                    color = Lime,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (map)
                        Canvas(
                            Modifier.align(
                                    if (phoneLayout) {
                                        if (race != null) Alignment.TopEnd else Alignment.TopCenter
                                    } else if (compactMap && race != null) Alignment.TopCenter
                                    else Alignment.TopEnd
                                )
                                .offset(
                                    x =
                                        if (!phoneLayout && compactMap && race != null) (-46).dp
                                        else 0.dp
                                )
                                .padding(
                                    top =
                                        if (phoneLayout) {
                                            if (race == null) 68.dp else 8.dp
                                        } else if (race == null) 76.dp
                                        else if (compactMap) 12.dp else 74.dp,
                                    end = 12.dp,
                                )
                                .size(
                                    if (phoneLayout) 76.dp else if (compactMap) 92.dp else 116.dp,
                                    if (phoneLayout) 56.dp else if (compactMap) 76.dp else 154.dp,
                                )
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ink.copy(alpha = .9f))
                                .semantics { contentDescription = "Venue floor map" }
                        ) {
                            val area = pose.area
                            val sx = size.width / (area.halfWidth * 2 + 2)
                            val sz = size.height / (area.halfDepth * 2 + 2)
                            fun point(x: Float, z: Float) =
                                Offset((x + area.halfWidth + 1) * sx, (z + area.halfDepth + 1) * sz)
                            drawRect(
                                Muted,
                                point(-area.halfWidth, -area.halfDepth),
                                Size(area.halfWidth * 2 * sx, area.halfDepth * 2 * sz),
                                style = Stroke(2f),
                            )
                            if (area == TourArea.GAMING)
                                PrivateRoomPlan.rooms.forEach { room ->
                                    drawRect(
                                        Violet.copy(alpha = .45f),
                                        point(room.x - 3.5f, room.z - 2.8f),
                                        Size(sx * 7f, sz * 5.6f),
                                        style = Stroke(1.5f),
                                    )
                                }
                            if (area == TourArea.BILLIARDS)
                                for (x in listOf(-9f, -3f, 3f, 9f)) for (z in
                                    listOf(-10f, -3f, 4f)) {
                                    drawRect(
                                        Aqua.copy(alpha = .6f),
                                        point(x - 1f, z - 1.5f),
                                        Size(sx * 2, sz * 3),
                                    )
                                }
                            if (area == TourArea.OUTDOOR) {
                                KartTrack.points.forEachIndexed { i, p ->
                                    val next = KartTrack.points[(i + 1) % KartTrack.points.size]
                                    drawLine(
                                        Color.White.copy(alpha = .65f),
                                        point(p.x, p.z),
                                        point(next.x, next.z),
                                        3f,
                                    )
                                }
                                drawCircle(
                                    Lime,
                                    3f,
                                    point(KartTrack.points[0].x, KartTrack.points[0].z),
                                )
                                race?.let { kart ->
                                    val target = KartTrack.gate(kart.checkpoint)
                                    drawCircle(
                                        Aqua,
                                        5f,
                                        point(target.x, target.z),
                                        style = Stroke(2f),
                                    )
                                }
                            }
                            WashroomDoors.rooms
                                .first { it.area == area }
                                .let { room ->
                                    drawRect(
                                        Aqua.copy(alpha = .65f),
                                        point(room.left, room.back),
                                        Size(7f * sx, (room.front - room.back) * sz),
                                        style = Stroke(1.5f),
                                    )
                                    for (i in 1..4) drawLine(
                                        Aqua.copy(alpha = .5f),
                                        point(room.left + i * 1.4f, room.back),
                                        point(room.left + i * 1.4f, room.rowZ),
                                        1f,
                                    )
                                }
                            for (stop in TourPlan.stops.filter { it.pose.area == area }) drawCircle(
                                Muted,
                                3f,
                                point(stop.pose.x, stop.pose.z),
                            )
                            drawCircle(Lime, 5f, point(pose.x, pose.z))
                            drawLine(
                                Lime,
                                point(pose.x, pose.z),
                                point(pose.x + sin(pose.yaw) * 2, pose.z - cos(pose.yaw) * 2),
                                3f,
                            )
                        }
                    if (race == null)
                        Canvas(Modifier.align(Alignment.Center).size(5.dp)) {
                            drawCircle(Color.White.copy(alpha = .55f))
                        }
                    MoveJoystick(
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 10.dp),
                        diameter =
                            if (phoneLayout) (maxHeight - 100.dp).coerceIn(88.dp, 112.dp)
                            else 144.dp,
                    ) { x, y ->
                        surface.tour.strafe = x
                        surface.tour.forward = if (race == null) y else 0f
                    }
                    if (race != null) {
                        Row(
                            Modifier.align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .testTag("kartPedals"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            TourPedal(
                                "REVERSE",
                                "kartReverse",
                                Aqua,
                                if (phoneLayout) 56.dp else 76.dp,
                                if (phoneLayout) 72.dp else 116.dp,
                                true,
                            ) {
                                surface.tour.reverse = if (it) 1f else 0f
                            }
                            TourPedal(
                                "BRAKE",
                                "kartBrake",
                                Color(0xFFEEB19C),
                                if (phoneLayout) 84.dp else 112.dp,
                                pedalHeight,
                            ) {
                                surface.tour.brake = if (it) 1f else 0f
                            }
                            TourPedal(
                                "GAS",
                                "kartGas",
                                Lime,
                                if (phoneLayout) 84.dp else 112.dp,
                                pedalHeight,
                            ) {
                                surface.tour.throttle = if (it) 1f else 0f
                            }
                        }
                    } else {
                        // Only movement controls live at thumb level; venue actions stay above.
                        Row(
                            Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            TourRunButton(sprint, if (phoneLayout) 64.dp else 72.dp) {
                                sprint = !sprint
                            }
                            TourJumpButton(if (phoneLayout) 84.dp else 96.dp) {
                                surface.queueEvent { surface.tour.jump.trigger() }
                            }
                        }
                        Column(
                            Modifier.align(Alignment.TopEnd)
                                .padding(8.dp)
                                .testTag("tourVenueActions"),
                            horizontalAlignment = Alignment.End,
                        ) {
                            val door = WashroomDoors.nearest(pose)
                            val connector = TourPlan.connector(pose.area)
                            val nearLift = hypot(pose.x - connector.x, pose.z - connector.z) < 2.4f
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (door != null) {
                                    val open = doors and (1 shl door) != 0
                                    FilledTonalButton(
                                        onClick = {
                                            surface.queueEvent { surface.tour.toggleDoor(door) }
                                        },
                                        enabled = WashroomDoors.canToggle(door, pose),
                                        colors =
                                            ButtonDefaults.filledTonalButtonColors(
                                                disabledContainerColor = Color(0xFF283031),
                                                disabledContentColor = Color(0xFFB1BABA),
                                            ),
                                        modifier =
                                            Modifier.heightIn(min = 48.dp).testTag("cubicleDoor"),
                                    ) {
                                        AppText(
                                            if (open) "Close ${WashroomDoors.doors[door].label}"
                                            else "Open ${WashroomDoors.doors[door].label}",
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                if (
                                    pose.area == TourArea.OUTDOOR &&
                                        hypot(pose.x, pose.z - 101f) < 22f
                                ) {
                                    Button(
                                        onClick = {
                                            surface.stopInput()
                                            surface.queueEvent { surface.tour.startRace() }
                                        },
                                        modifier =
                                            Modifier.heightIn(min = 48.dp).testTag("kartStart"),
                                    ) {
                                        AppText("Take a kart 🏁", fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        surface.stopInput()
                                        lift = true
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("tourFloor"),
                                ) {
                                    AppIcon(Icons.Outlined.Elevator, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    AppText(
                                        if (pose.area == TourArea.OUTDOOR) "Ground exit"
                                        else if (nearLift) "Take lift" else "Change floor",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                            if (openDoorBlocked(door, pose))
                                AppText(
                                    "Step clear of the door to use it",
                                    Modifier.background(Ink.copy(alpha = 0.85f)).padding(8.dp),
                                    fontSize = 10.sp,
                                )
                        }
                    }
                } else
                    Surface(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        color = Panel,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (status == "loading") CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            AppText(if (status == "loading") "Opening the venue…" else status)
                        }
                    }
            }
        }
    }
    if (destinations)
        AlertDialog(
            onDismissRequest = { destinations = false },
            title = { AppText("Show tour destinations") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TourArea.entries.forEach { area ->
                        OutlinedButton(
                            onClick = {
                                destinations = false
                                surface.stopInput()
                                surface.queueEvent { surface.tour.go(TourPlan.entry(area)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppText("${area.floor} ${area.label}")
                        }
                        if (area == pose.area)
                            TourPlan.stops
                                .filter { it.pose.area == area }
                                .forEach { stop ->
                                    TextButton(
                                        onClick = {
                                            destinations = false
                                            surface.stopInput()
                                            surface.queueEvent { surface.tour.go(stop) }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        AppText(stop.name)
                                    }
                                }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { destinations = false }) { AppText("Done") } },
        )
    if (restartConfirmation)
        AlertDialog(
            onDismissRequest = { restartConfirmation = false },
            title = { AppText("Restart this lap?") },
            text = { AppText("Your current run will reset. Your best lap stays saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        restartConfirmation = false
                        surface.stopInput()
                        surface.queueEvent { surface.tour.startRace() }
                    },
                    modifier = Modifier.testTag("kartRestartConfirm"),
                ) {
                    AppText("Restart")
                }
            },
            dismissButton = {
                TextButton(onClick = { restartConfirmation = false }) { AppText("Keep driving") }
            },
        )
    if (graphics)
        AlertDialog(
            onDismissRequest = { graphics = false },
            title = { AppText("Picture quality") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        "Ultra keeps the scene sharp. Choose Balanced or Smooth if moving feels slow.",
                        color = Muted,
                    )
                    TourQuality.entries.forEach { option ->
                        OutlinedButton(
                            onClick = {
                                vm.set("tourQuality", option.name)
                                surface.setQuality(option)
                                graphics = false
                            },
                            modifier = Modifier.fillMaxWidth().testTag("tourQuality-${option.name}"),
                        ) {
                            AppText(
                                if (option == quality) "${option.label} · Selected"
                                else option.label
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { graphics = false }) { AppText("Done") } },
        )
    if (lift)
        AlertDialog(
            onDismissRequest = { lift = false },
            title = { AppText("Where next?") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TourArea.entries.forEach { area ->
                        OutlinedButton(
                            onClick = {
                                lift = false
                                surface.queueEvent { surface.tour.go(TourPlan.entry(area)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                AppText("${area.floor} · ${area.label}")
                                AppText(area.summary, fontSize = 11.sp, color = Muted)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { lift = false }) { AppText("Keep exploring") } },
        )
    if (help)
        AlertDialog(
            onDismissRequest = { help = false },
            title = { AppText("Make yourself at home") },
            text = {
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    AppText(
                        "Joystick: walk. Drag: look. Run switches to a faster pace; Jump lifts you off the floor.\n\nL1: connected esports desks. L2: billiards. G: social lounge and kart circuit. Every floor has a washroom with private toilets and showers. Find Washroom in the destination menu. Approach each door to open or close it; step out of its swing before closing.\n\nG kart paddock: find Take a kart. Joystick steers; hold GAS or BRAKE; BRAKE alone slows down; BRAKE + steering at speed drifts. Hold REVERSE to recover from a rail. Large pedals support two-thumb play. Follow the circuit through all eight gates to complete a timed lap. No skills or power-ups. Restart resets the run; Exit kart returns to the paddock.\n\nThe tour and time trial work offline. Reservations are made in Play."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { help = false }) { AppText("Let's explore") } },
        )
}

private fun openDoorBlocked(index: Int?, pose: TourPose) =
    index != null && !WashroomDoors.canToggle(index, pose)

private fun raceTime(seconds: Float): String {
    val ticks = (seconds * 100).toInt()
    return "%d:%02d.%02d".format(ticks / 6000, ticks / 100 % 60, ticks % 100)
}

@Composable
private fun TourRaceActions(surface: TourSurface, restart: () -> Unit) {
    TextButton(
        onClick = {
            surface.stopInput()
            restart()
        },
        modifier = Modifier.heightIn(min = 48.dp).testTag("kartRestart"),
    ) {
        AppText("Restart", fontSize = 11.sp, maxLines = 1)
    }
    TextButton(
        onClick = {
            surface.stopInput()
            surface.queueEvent { surface.tour.exitRace() }
        },
        modifier = Modifier.heightIn(min = 48.dp).testTag("kartExit"),
    ) {
        AppText("Exit kart", fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun TourJumpButton(diameter: androidx.compose.ui.unit.Dp, jump: () -> Unit) {
    val label = tr("JUMP")
    Surface(
        onClick = jump,
        modifier =
            Modifier.size(diameter).testTag("tourJump").semantics { contentDescription = label },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.58f),
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val arrow =
                Path().apply {
                    moveTo(w * 0.5f, h * 0.25f)
                    lineTo(w * 0.28f, h * 0.50f)
                    lineTo(w * 0.41f, h * 0.50f)
                    lineTo(w * 0.41f, h * 0.70f)
                    lineTo(w * 0.59f, h * 0.70f)
                    lineTo(w * 0.59f, h * 0.50f)
                    lineTo(w * 0.72f, h * 0.50f)
                    close()
                }
            drawPath(arrow, Color.White)
            drawPath(arrow, Color(0xFFBBBBBB), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun TourRunButton(
    running: Boolean,
    diameter: androidx.compose.ui.unit.Dp,
    toggle: () -> Unit,
) {
    val label = tr(if (running) "Running" else "Run")
    Surface(
        onClick = toggle,
        modifier =
            Modifier.size(diameter).testTag("tourSprint").semantics { contentDescription = label },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.58f),
        border = BorderStroke(1.5.dp, if (running) Lime else Color.White.copy(alpha = 0.6f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(
                Icons.Outlined.DirectionsRun,
                null,
                Modifier.size(26.dp),
                tint = if (running) Lime else Color.White,
            )
            AppText(
                if (running) "Running" else "Run",
                color = if (running) Lime else Color.White,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** A hold action with release/cancellation handling supports steering + gas + drift together. */
@Composable
private fun TourPedal(
    label: String,
    tag: String,
    tint: Color,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    reverse: Boolean = false,
    action: (Boolean) -> Unit,
) {
    var held by remember { mutableStateOf(false) }
    val ink = Ink
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(Unit) { onDispose { currentAction(false) } }
    Surface(
        modifier =
            Modifier.size(width, height)
                .testTag(tag)
                .semantics { contentDescription = label }
                .pointerInput(Unit) {
                    try {
                        awaitPointerEventScope {
                            var active: PointerId? = null
                            while (true) {
                                val event = awaitPointerEvent()
                                if (active == null)
                                    event.changes
                                        .firstOrNull { it.changedToDown() }
                                        ?.let {
                                            active = it.id
                                            held = true
                                            currentAction(true)
                                            it.consume()
                                        }
                                event.changes
                                    .firstOrNull { it.id == active }
                                    ?.let {
                                        if (!it.pressed) {
                                            active = null
                                            held = false
                                            currentAction(false)
                                        } else it.consume()
                                    }
                            }
                        }
                    } finally {
                        held = false
                        currentAction(false)
                    }
                },
        shape = RoundedCornerShape(16.dp),
        color = if (held) tint else Ink.copy(alpha = .9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .7f)),
    ) {
        Column(
            Modifier.padding(horizontal = if (reverse) 6.dp else 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (reverse)
                AppIcon(
                    Icons.Outlined.ArrowDownward,
                    null,
                    Modifier.weight(1f).fillMaxWidth().padding(4.dp),
                    tint = if (held) ink else Color.White,
                )
            else
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val color = if (held) ink else Color.White
                    val w = size.width * .65f
                    val h = size.height * .90f
                    val left = (size.width - w) / 2
                    val top = (size.height - h) / 2
                    drawRoundRect(
                        color,
                        Offset(left, top),
                        Size(w, h),
                        androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    )
                    for (i in 1..4) {
                        val yy = top + h * i / 5
                        drawLine(
                            if (held) tint else ink,
                            Offset(left + w * .14f, yy),
                            Offset(left + w * .86f, yy),
                            3.dp.toPx(),
                            androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }
                }
            AppText(
                if (reverse) "REV" else label,
                color = if (held) Ink else tint,
                fontSize = if (reverse) 10.sp else 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun MoveJoystick(
    modifier: Modifier,
    diameter: androidx.compose.ui.unit.Dp = 144.dp,
    onMove: (Float, Float) -> Unit,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    val ink = Ink
    val currentMove by rememberUpdatedState(onMove)
    Canvas(
        modifier
            .size(diameter)
            .testTag("tourJoystick")
            .semantics { contentDescription = "Movement joystick" }
            .pointerInput(diameter) {
                val radius = size.width * .34f
                fun update(p: Offset) {
                    val d = p - Offset(size.width / 2f, size.height / 2f)
                    offset = if (d.getDistance() > radius) d * (radius / d.getDistance()) else d
                    currentMove(offset.x / radius, -offset.y / radius)
                }
                try {
                    awaitPointerEventScope {
                        var active: PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            if (active == null)
                                event.changes
                                    .firstOrNull { it.changedToDown() }
                                    ?.let {
                                        active = it.id
                                        update(it.position)
                                        it.consume()
                                    }
                            event.changes
                                .firstOrNull { it.id == active }
                                ?.let { finger ->
                                    if (!finger.pressed) {
                                        active = null
                                        offset = Offset.Zero
                                        currentMove(0f, 0f)
                                    } else {
                                        update(finger.position)
                                        finger.consume()
                                    }
                                }
                        }
                    }
                } finally {
                    offset = Offset.Zero
                    currentMove(0f, 0f)
                }
            }
    ) {
        drawCircle(ink.copy(alpha = .72f))
        drawCircle(Color.White.copy(alpha = .2f), style = Stroke(2.dp.toPx()))
        drawLine(
            Color.White.copy(alpha = .12f),
            Offset(center.x, 12f),
            Offset(center.x, size.height - 12f),
            2f,
        )
        drawLine(
            Color.White.copy(alpha = .12f),
            Offset(12f, center.y),
            Offset(size.width - 12f, center.y),
            2f,
        )
        drawCircle(Lime.copy(alpha = .92f), size.width * .21f, center + offset)
        drawCircle(
            ink.copy(alpha = .25f),
            size.width * .10f,
            center + offset,
            style = Stroke(2.dp.toPx()),
        )
    }
}
