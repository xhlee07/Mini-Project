package com.ctfly.app

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FixtureGateway(private val fixture: JSONObject, override var signedIn: Boolean) :
    ClubGateway {
    @Volatile var lastBooking: JSONObject? = null
    @Volatile var lastFeedback: JSONObject? = null
    @Volatile var failFeedbackOnce = false
    val rpcRequests = mutableListOf<String>()
    val feedbackRequests = mutableListOf<String>()
    private val submittedFeedback = mutableMapOf<String, String>()
    val feedbackCount
        get() = fixture.rows("feedback").size

    override suspend fun rpc(name: String, body: JSONObject, authenticated: Boolean): JSONObject {
        rpcRequests.add(name)
        if (name == "ctfly_action" && body.text("p_action") == "feedback") {
            lastFeedback = JSONObject(body.getJSONObject("p_data").toString())
            val request = body.text("p_request")
            feedbackRequests.add(request)
            val id =
                submittedFeedback.getOrPut(request) {
                    val created = "fixture-feedback-${submittedFeedback.size}"
                    val row =
                        JSONObject(lastFeedback.toString()).apply {
                            put("id", created)
                            put("user_id", fixture.getJSONObject("profile").text("id"))
                            put("customer_name", fixture.getJSONObject("profile").text("name"))
                            put("created_at", java.time.Instant.now().toString())
                            put("status", "New")
                        }
                    fixture.optJSONArray("feedback")?.put(row)
                    created
                }
            if (failFeedbackOnce) {
                failFeedbackOnce = false
                throw java.io.IOException("Please retry feedback.")
            }
            return obj("message" to "Feedback sent. Thank you!", "id" to id)
        }
        if (name == "ctfly_action" && body.text("p_action") == "feedback_status") {
            val data = body.getJSONObject("p_data")
            fixture
                .rows("feedback")
                .find { it.text("id") == data.text("id") }
                ?.put("status", data.text("status"))
            return obj("message" to "Feedback status updated.")
        }
        if (name == "ctfly_action" && body.text("p_action") == "book") {
            lastBooking = JSONObject(body.getJSONObject("p_data").toString())
            return obj("message" to "Reserved", "id" to "fixture-booking")
        }
        if (name == "ctfly_quote") {
            kotlinx.coroutines.delay(if (body.text("p_kind") == "Extension30") 100 else 10)
            return obj(
                "id" to body.text("p_id"),
                "kind" to body.text("p_kind"),
                "subtotal" to 600,
                "discount" to 0,
                "total" to 600,
            )
        }
        return JSONObject(fixture.toString())
    }

    override suspend fun login(email: String, password: String) {
        signedIn = true
    }

    override suspend fun signup(
        email: String,
        password: String,
        name: String,
        dob: String,
        phone: String,
    ) = false

    override suspend fun recover(email: String) {}

    override suspend fun resend(email: String) {}

    override suspend fun verify(email: String, code: String, type: String) {
        signedIn = true
    }

    override suspend fun newPassword(password: String) {}

    override suspend fun logout() {
        signedIn = false
    }

    override fun localLogout() {
        signedIn = false
    }
}

class AdaptiveStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var latestGateway: FixtureGateway? = null
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val app
        get() = instrumentation.targetContext.applicationContext as CtflyApplication

    private fun launch(
        role: String = "customer",
        signedIn: Boolean = true,
        allowWrongRole: Boolean = false,
    ) {
        app.getSharedPreferences("ctfly_appearance", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        if (signedIn && !allowWrongRole)
            org.junit.Assume.assumeTrue(BuildConfig.STAFF_APP == (role != "customer"))
        val fixture =
            JSONObject(
                instrumentation.context.assets.open("$role.json").bufferedReader().use {
                    it.readText()
                }
            )
        val gateway = FixtureGateway(fixture, signedIn)
        latestGateway = gateway
        app.gatewayFactory = { gateway }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun change(action: (AppViewModel) -> Unit) {
        scenario!!.onActivity { action(ViewModelProvider(it)[AppViewModel::class.java]) }
        compose.waitForIdle()
    }

    private fun capture(name: String, settle: Boolean = true) {
        compose.waitForIdle()
        if (settle) Thread.sleep(1200)
        compose.waitForIdle()
        val directory = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { image ->
            File(directory, "$name.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        shell("mkdir -p /sdcard/Download/CTFLY-test")
        shell("cp ${directory.absolutePath}/$name.png /sdcard/Download/CTFLY-test/$name.png")
    }

    @After
    fun cleanup() {
        scenario?.close()
        app.gatewayFactory = { CtflyApi(it) }
        shell("wm size reset")
        shell("wm density reset")
        shell("settings put system font_scale 1.0")
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
        Thread.sleep(600)
    }

    @Test
    fun settingsPersistLanguagesColorsAndRetryFeedbackWithoutDuplicates() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("language-zh").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithTag("background-navy").performScrollTo().performClick()
        capture("settings-customer-chinese")
        scenario!!.recreate()
        compose.onNodeWithText("设置").assertIsDisplayed()
        val preferences = AppPreferences(app)
        assertEquals("zh", preferences.language)
        assertEquals("navy", preferences.background)
        compose.onNodeWithTag("language-ms").performScrollTo().performClick()
        compose.onNodeWithText("Tetapan").assertIsDisplayed()
        capture("settings-customer-malay")
        compose.onNodeWithTag("language-en").performScrollTo().performClick()
        compose.onNodeWithTag("sendFeedback").performScrollTo().assertIsNotEnabled()
        compose
            .onNodeWithText("Feedback title")
            .performScrollTo()
            .performTextInput("Settings are useful")
        compose
            .onNodeWithText("Your feedback")
            .performScrollTo()
            .performTextInput("I like choosing a background color and changing the language.")
        val countBefore = latestGateway!!.feedbackCount
        latestGateway!!.failFeedbackOnce = true
        compose.onNodeWithTag("sendFeedback").performScrollTo().performClick()
        compose.waitUntil(5000) {
            var done = false
            scenario!!.onActivity { done = !ViewModelProvider(it)[AppViewModel::class.java].busy }
            !latestGateway!!.failFeedbackOnce && done
        }
        compose.onNodeWithText("Please retry feedback.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose
            .onNodeWithText("Your feedback")
            .assertTextContains("I like choosing a background color and changing the language.")
        compose.onNodeWithTag("sendFeedback").performClick()
        compose.waitUntil(5000) { latestGateway!!.feedbackRequests.size == 2 }
        assertEquals(latestGateway!!.feedbackRequests[0], latestGateway!!.feedbackRequests[1])
        assertEquals(countBefore + 1, latestGateway!!.feedbackCount)
        compose.waitUntil(5000) {
            var empty = false
            scenario!!.onActivity {
                empty =
                    ViewModelProvider(it)[AppViewModel::class.java].field("feedbackBody").isEmpty()
            }
            empty
        }
        compose.onNodeWithTag("sendFeedback").assertIsNotEnabled()
        capture("settings-feedback-submitted")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun staffCanReadAndProcessCustomerFeedback() {
        org.junit.Assume.assumeTrue(BuildConfig.STAFF_APP)
        launch("staff")
        compose.onNodeWithContentDescription("Customer feedback").performClick()
        var row: JSONObject? = null
        scenario!!.onActivity {
            row =
                ViewModelProvider(it)[AppViewModel::class.java].rows("feedback").first { f ->
                    f.text("status") == "New"
                }
        }
        val id = row!!.text("id")
        compose.onNodeWithText(row!!.text("body")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reviewFeedback-$id").performScrollTo().performClick()
        compose.waitUntil(5000) {
            var status = ""
            scenario!!.onActivity {
                status =
                    ViewModelProvider(it)[AppViewModel::class.java].rows("feedback")
                        .first { f -> f.text("id") == id }
                        .text("status")
            }
            status == "Reviewed"
        }
        compose.onNodeWithTag("resolveFeedback-$id").performScrollTo().performClick()
        compose.waitUntil(5000) {
            var status = ""
            scenario!!.onActivity {
                status =
                    ViewModelProvider(it)[AppViewModel::class.java].rows("feedback")
                        .first { f -> f.text("id") == id }
                        .text("status")
            }
            status == "Resolved"
        }
        capture("settings-staff-feedback-inbox")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("language-zh").performClick()
        compose.onNodeWithTag("background-forest").performScrollTo().performClick()
        compose.onNodeWithText("打开反馈收件页").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("查看 App 建议和问题。").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("顾客反馈").assertIsDisplayed()
        capture("settings-staff-chinese-feedback")
    }

    @Test
    fun privateRoomPartySelectionAndSimulatorTiersUseCorrectBookingPayload() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch()
        change { it.set("page", "Play") }
        compose.onNodeWithText("VIP room").performScrollTo().performClick()
        compose.onNodeWithText("3 players").performScrollTo().performClick()
        compose.onNodeWithText("VIP Room Squad 01").assertIsDisplayed()
        compose.onAllNodesWithText("VIP Room Solo 01").assertCountEquals(0)
        capture("usability-vip-booking-catalog")
        compose.onAllNodesWithText("Reserve")[0].performClick()
        compose.onNodeWithText("3 player(s)").assertIsDisplayed()
        compose.onNodeWithText("RM 26.00").assertIsDisplayed()
        capture("usability-vip-booking-three")
        compose.onNodeWithText("Confirm reservation").performScrollTo().performClick()
        compose.waitUntil(5000) { latestGateway!!.lastBooking != null }
        assertEquals(3, latestGateway!!.lastBooking!!.optInt("guests"))
        assertEquals(30, latestGateway!!.lastBooking!!.optInt("minutes"))
        change { it.set("page", "Play") }
        compose.onNodeWithText("Racing simulator").performScrollTo().performClick()
        compose.onNodeWithText("VIP", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("VIP Racing Simulator 01").assertIsDisplayed()
        capture("usability-simulator-booking-vip")
        compose.onAllNodesWithText("Reserve")[0].performClick()
        compose.onNodeWithText("1 player(s)").assertIsDisplayed()
        compose.onNodeWithText("RM 17.50").assertIsDisplayed()
        compose.onNodeWithText("Confirm reservation").performScrollTo().performClick()
        compose.waitUntil(5000) { latestGateway!!.lastBooking!!.optInt("guests") == 1 }
        val id = latestGateway!!.lastBooking!!.optLong("resource_id")
        scenario!!.onActivity {
            val r =
                ViewModelProvider(it)[AppViewModel::class.java].rows("resources").first { r ->
                    r.optLong("id") == id
                }
            assertEquals("Racing", r.text("kind"))
            assertEquals("VIP", r.text("zone"))
        }
    }

    @Test
    fun tourExperienceHasJumpDoorsAndPlayableKart() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch()
        change {
            it.set("tourQuality", "BALANCED")
            it.set("page", "Tour")
        }
        fun ready() =
            compose.waitUntil(45000) {
                compose
                    .onAllNodesWithText("Walk freely • Drag to look around")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        fun surface(): TourSurface {
            var result: TourSurface? = null
            fun find(view: android.view.View): TourSurface? {
                if (view is TourSurface) return view
                if (view is android.view.ViewGroup)
                    for (i in 0 until view.childCount) find(view.getChildAt(i))?.let {
                        return it
                    }
                return null
            }
            scenario!!.onActivity { result = find(it.window.decorView) }
            return result!!
        }
        fun go(p: TourPose) {
            val s = surface()
            s.stopInput()
            s.queueEvent { s.tour.go(TourStop("Test view", p, "")) }
            compose.waitUntil(45000) {
                var synced = false
                scenario!!.onActivity {
                    val saved =
                        ViewModelProvider(it)[AppViewModel::class.java].field("tourPose").split(',')
                    synced =
                        saved.size >= 2 &&
                            kotlin.math.abs((saved[0].toFloatOrNull() ?: 999f) - p.x) < .01f &&
                            kotlin.math.abs((saved[1].toFloatOrNull() ?: 999f) - p.z) < .01f
                }
                synced &&
                    surface().tour.pose.area == p.area &&
                    compose
                        .onAllNodesWithText("Walk freely • Drag to look around")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
            }
        }
        ready()
        compose.onNodeWithTag("tourJump").performClick()
        compose.waitUntil(2000) { surface().tour.jump.height > .1f }
        compose.waitUntil(4000) { surface().tour.jump.height == 0f }
        compose.onNodeWithTag("tourSprint").performClick()
        compose.onNodeWithText("Running").assertIsDisplayed()
        go(TourPose(-3.2f, 17.0f, -.4f, -.24f))
        capture("experience-esports-desk")
        scenario!!.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        ready()
        capture("experience-esports-landscape")
        for (capacity in listOf(1, 2, 4)) {
            go(PrivateRoomPlan.rooms.first { it.capacity == capacity && it.x == 0f }.insidePose)
            capture("usability-vip-room-$capacity")
        }
        go(TourPose(6.4f, -2.4f, 0f, -.18f, TourArea.LOUNGE))
        capture("usability-simulator-standard")
        go(TourPose(13.2f, -.8f, .5f, -.20f, TourArea.LOUNGE))
        capture("usability-simulator-vip")
        go(TourPlan.stops.first { it.name == "Washroom" }.pose)
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("cubicleDoor").fetchSemanticsNodes().isNotEmpty()
        }
        capture("experience-washroom-closed")
        compose.onNodeWithTag("cubicleDoor").performClick()
        compose.waitUntil(4000) { surface().tour.doorMask != 0 }
        capture("experience-washroom-open")
        val mask = surface().tour.doorMask
        scenario!!.recreate()
        ready()
        assertEquals(mask, surface().tour.doorMask)
        compose.onNodeWithTag("cubicleDoor").performClick()
        compose.waitUntil(4000) { surface().tour.doorMask == 0 }
        go(TourPlan.entry(TourArea.OUTDOOR).pose)
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("kartStart").fetchSemanticsNodes().isNotEmpty()
        }
        capture("experience-ground-circuit")
        compose.onNodeWithTag("kartStart").performClick()
        compose.waitUntil(6000) { surface().tour.race?.countdown == 0f }
        val mapBounds =
            compose
                .onNodeWithContentDescription("Venue floor map")
                .fetchSemanticsNode()
                .boundsInRoot
        val gasBounds = compose.onNodeWithTag("kartGas").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Race map must not overlap gas",
            mapBounds.bottom <= gasBounds.top || mapBounds.right <= gasBounds.left,
        )
        val jumpMin = 80 * instrumentation.targetContext.resources.displayMetrics.density
        assertTrue("Large gas target", gasBounds.height >= jumpMin)
        val restartBounds = compose.onNodeWithTag("kartRestart").fetchSemanticsNode().boundsInRoot
        assertTrue("Restart belongs above pedals", restartBounds.bottom < gasBounds.top)
        compose.onNodeWithTag("kartGas").performTouchInput { down(center) }
        compose.waitUntil(6000) { (surface().tour.race?.speed ?: 0f) > 12f }
        compose.onNodeWithTag("kartGas").performTouchInput { up() }
        compose.onNodeWithTag("kartBrake").performTouchInput { down(2, center) }
        compose.onNodeWithTag("tourJoystick").performTouchInput {
            down(1, center)
            moveTo(1, center + androidx.compose.ui.geometry.Offset(40f, 0f), 150)
        }
        compose.waitUntil(3000) { surface().tour.race?.drifting == true }
        compose.waitUntil(3000) {
            compose.onAllNodesWithText("DRIFT").fetchSemanticsNodes().isNotEmpty()
        }
        capture("experience-kart-drift", false)
        compose.onNodeWithTag("tourJoystick").performTouchInput { up(1) }
        compose.onNodeWithTag("kartBrake").performTouchInput { up(2) }
        compose.onNodeWithTag("kartBrake").performTouchInput { down(center) }
        compose.waitUntil(5000) { (surface().tour.race?.speed ?: 99f) < .2f }
        compose.onNodeWithTag("kartBrake").performTouchInput { up() }
        compose.onNodeWithTag("kartReverse").performTouchInput { down(center) }
        compose.waitUntil(5000) { (surface().tour.race?.speed ?: 0f) < -2f }
        compose.onNodeWithTag("kartReverse").performTouchInput { up() }
        capture("usability-kart-reverse")
        compose.onNodeWithTag("kartBrake").performTouchInput { down(center) }
        compose.waitUntil(5000) { kotlin.math.abs(surface().tour.race?.speed ?: 99f) < .2f }
        compose.onNodeWithTag("kartBrake").performTouchInput { up() }
        val before = surface().tour.race!!
        scenario!!.recreate()
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("kartGas").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(before.checkpoint, surface().tour.race!!.checkpoint)
        assertEquals(before.laps, surface().tour.race!!.laps)
        assertEquals(0f, surface().tour.throttle, 0f)
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("kartGas").fetchSemanticsNodes().isNotEmpty()
        }
        capture("experience-kart-auto-landscape")
        compose.onNodeWithTag("kartRestart").performClick()
        compose.onNodeWithTag("kartRestartConfirm").performClick()
        compose.waitUntil(4000) { (surface().tour.race?.countdown ?: 0f) > 1f }
        compose.onNodeWithTag("kartExit").performClick()
        ready()
        assertNull(surface().tour.race)
        compose.onNodeWithContentDescription("Leave 3D tour").performClick()
    }

    @Test
    fun tourSupportsLookMovementAndRotation() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch()
        change { it.set("page", "Tour") }
        compose.waitUntil(20000) {
            compose
                .onAllNodesWithText("Walk freely • Drag to look around")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("tour3d").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(100f, 0f), 500)
        }
        var turned = ""
        compose.waitUntil(10000) {
            scenario!!.onActivity {
                turned = ViewModelProvider(it)[AppViewModel::class.java].field("tourPose")
            }
            turned.split(',').getOrNull(2)?.toFloatOrNull()?.let { it != 0f } == true
        }
        assertTrue(turned.split(',')[2].toFloat() != 0f)
        compose.onNodeWithTag("tourJoystick").performTouchInput {
            down(center)
            moveTo(center + androidx.compose.ui.geometry.Offset(0f, -45f), 100)
        }
        Thread.sleep(800)
        compose.onNodeWithTag("tourJoystick").performTouchInput { up() }
        Thread.sleep(400)
        scenario!!.onActivity {
            val moved = ViewModelProvider(it)[AppViewModel::class.java].field("tourPose").split(',')
            assertTrue(
                "Joystick must move the camera",
                moved[1].toFloat() < turned.split(',')[1].toFloat() - .03f,
            )
        }
        capture("tour-entrance")
        scenario!!.onActivity {
            turned = ViewModelProvider(it)[AppViewModel::class.java].field("tourPose")
        }
        scenario!!.recreate()
        compose.waitUntil(20000) {
            compose
                .onAllNodesWithText("Walk freely • Drag to look around")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        scenario!!.onActivity {
            val restored =
                ViewModelProvider(it)[AppViewModel::class.java].field("tourPose").split(',')
            assertEquals(turned.split(',')[2].toFloat(), restored[2].toFloat(), .1f)
        }
        scenario!!.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(20000) {
            compose
                .onAllNodesWithText("Walk freely • Drag to look around")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        capture("tour-landscape")
        if (
            compose
                .onAllNodesWithContentDescription("Show tour destinations")
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
            compose.onNodeWithContentDescription("Show tour destinations").performClick()
        fun switchFloor(area: TourArea) {
            if (
                compose
                    .onAllNodesWithText("${area.floor} ${area.label}", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isEmpty()
            )
                compose.onNodeWithContentDescription("Show tour destinations").performClick()
            compose
                .onNodeWithText("${area.floor} ${area.label}", useUnmergedTree = true)
                .performScrollTo()
                .performClick()
            compose.waitUntil(30000) {
                var current = ""
                scenario!!.onActivity {
                    current = ViewModelProvider(it)[AppViewModel::class.java].field("tourPose")
                }
                current.endsWith(area.name) &&
                    compose
                        .onAllNodesWithText("Walk freely • Drag to look around")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
            }
            Thread.sleep(1200)
        }
        switchFloor(TourArea.BILLIARDS)
        capture("tour-pool")
        compose.onNodeWithContentDescription("Tour picture quality").performClick()
        compose.onNodeWithTag("tourQuality-BALANCED").performScrollTo().performClick()
        scenario!!.recreate()
        compose.waitUntil(30000) {
            compose
                .onAllNodesWithText("Walk freely • Drag to look around")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        scenario!!.onActivity {
            assertTrue(
                ViewModelProvider(it)[AppViewModel::class.java].field("tourPose")
                    .endsWith("BILLIARDS")
            )
            assertEquals(
                "BALANCED",
                ViewModelProvider(it)[AppViewModel::class.java].field("tourQuality"),
            )
        }
        compose.onNodeWithContentDescription("Tour picture quality").performClick()
        compose.onNodeWithTag("tourQuality-ULTRA").performScrollTo().performClick()
        if (
            compose
                .onAllNodesWithContentDescription("Expand 3D view")
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
            compose.onNodeWithContentDescription("Expand 3D view").performClick()
        fun find(view: android.view.View): TourSurface? {
            if (view is TourSurface) return view
            if (view is android.view.ViewGroup)
                for (i in 0 until view.childCount) {
                    find(view.getChildAt(i))?.let {
                        return it
                    }
                }
            return null
        }
        compose.waitUntil(30000) {
            var drawn = false
            scenario!!.onActivity { activity ->
                val surface = requireNotNull(find(activity.window.decorView))
                val expected = TourQuality.ULTRA.size(surface.width, surface.height)
                drawn = surface.tour.renderedSize == expected
            }
            drawn &&
                compose
                    .onAllNodesWithText("Walk freely • Drag to look around")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
        scenario!!.onActivity { activity ->
            val surface = requireNotNull(find(activity.window.decorView))
            val expected = TourQuality.ULTRA.size(surface.width, surface.height)
            assertEquals(expected.first, surface.renderBufferSize.first)
            assertEquals(expected.second, surface.renderBufferSize.second)
            assertEquals(expected, surface.tour.renderedSize)
        }
        capture("tour-pool-ultra")
        compose.onNodeWithContentDescription("Show tour destinations").performClick()
        switchFloor(TourArea.OUTDOOR)
        capture("tour-outdoor")
        switchFloor(TourArea.GAMING)
        if (
            compose
                .onAllNodesWithText("VIP room", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
            compose.onNodeWithContentDescription("Show tour destinations").performClick()
        compose.onNodeWithText("VIP room", useUnmergedTree = true).performScrollTo().performClick()
        Thread.sleep(500)
        capture("tour-vip")
        switchFloor(TourArea.LOUNGE)
        capture("tour-social")
        if (
            compose
                .onAllNodesWithText("Washroom", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
            compose.onNodeWithContentDescription("Show tour destinations").performClick()
        compose.onNodeWithText("Washroom", useUnmergedTree = true).performScrollTo().performClick()
        Thread.sleep(500)
        capture("tour-washroom")
        compose.onNodeWithContentDescription("Leave 3D tour").performClick()
        compose.onNodeWithText("Discover").assertExists()
    }

    @Test
    fun expandedBookingGroupsShowFloorAndKartChoices() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch()
        change {
            it.set("page", "Play")
            it.set("playKind", "Pool")
        }
        compose.onNodeWithText("8 pool facilities · Choose your time").assertExists()
        capture("booking-pool-expanded")
        compose.onNodeWithText("Go-kart", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("6 go-kart facilities · Choose your time").assertExists()
        capture("booking-outdoor-expanded")
    }

    @Test
    fun tourCompatibilityShadersCanRenderAndSwitchFloor() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch()
        val status = java.util.concurrent.atomic.AtomicReference("loading")
        val version = java.util.concurrent.atomic.AtomicReference("")
        var surface: TourSurface? = null
        try {
            scenario!!.onActivity { activity ->
                surface =
                    TourSurface(
                        activity,
                        TourPose(),
                        {},
                        { status.set(it) },
                        TourQuality.BALANCED,
                        preferModern = false,
                    )
                activity.setContentView(surface)
                surface!!.onResume()
            }
            compose.waitUntil(30000) { status.get() == "ready" }
            scenario!!.onActivity {
                surface!!.queueEvent {
                    version.set(
                        android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION)
                            .orEmpty()
                    )
                    surface!!.tour.go(TourPlan.entry(TourArea.BILLIARDS))
                }
            }
            compose.waitUntil(30000) {
                version.get().isNotEmpty() &&
                    status.get() == "ready" &&
                    surface!!.tour.pose.area == TourArea.BILLIARDS
            }
            // SwiftShader may return GLES3 even when EGL requests GLES2. Force and verify the
            // GLSL ES 1.00 / compatibility sampling path without claiming older GPU coverage.
            assertFalse(surface!!.tour.usesModernShaders)
        } finally {
            scenario!!.onActivity {
                surface?.detachCallbacks()
                surface?.stopInput()
                surface?.onPause()
            }
        }
    }

    private fun tourSurface(): TourSurface {
        var result: TourSurface? = null
        fun find(view: android.view.View): TourSurface? {
            if (view is TourSurface) return view
            if (view is android.view.ViewGroup)
                for (i in 0 until view.childCount) find(view.getChildAt(i))?.let {
                    return it
                }
            return null
        }
        scenario!!.onActivity { result = find(it.window.decorView) }
        return requireNotNull(result)
    }

    @Test
    fun guestCannotBypassLoginWithSavedPreviewOrTourRoute() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch(signedIn = false)
        change {
            it.set("explore", "true")
            it.set("page", "Tour")
            it.refresh()
        }
        compose.onNodeWithText("Good to see you.").assertExists()
        compose.onNodeWithTag("tour3d").assertDoesNotExist()
        compose.onNodeWithText("Explore the club first").assertDoesNotExist()
        assertTrue(latestGateway!!.rpcRequests.isEmpty())
        scenario!!.recreate()
        compose.onNodeWithTag("tour3d").assertDoesNotExist()
        compose.onNodeWithText("New here? Join CTFLY").performScrollTo().performClick()
        compose.onNodeWithText("Create account").assertExists()
        capture("phone-login-required")
    }

    @Test
    fun birthDateFitsSmallPhoneWithLargeText() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 960x1920")
        shell("wm density 480")
        launch(signedIn = false)
        scenario!!.onActivity { activity ->
            activity.setContent {
                val original = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides
                        androidx.compose.ui.unit.Density(original.density, 1.6f)
                ) {
                    CtflyTheme {
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.fillMaxWidth().padding(24.dp)
                        ) {
                            DateFields("Date of birth · 18+", "2008-01-31", {}, true)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Day").assertIsDisplayed().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
        ) { action ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            action(layouts)
            assertEquals(1, layouts.single().lineCount)
        }
        compose.onNodeWithText("Year").assertIsDisplayed()
        compose.onNodeWithText("2008").assertIsDisplayed()
        capture("phone-date-large-text")
    }

    @Test
    fun phoneTourAutoRotatesAndSupportsRepeatedTwoThumbLook() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 1080x2340")
        shell("wm density 480")
        launch()
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitForIdle()
        change {
            it.set("tourQuality", "BALANCED")
            it.set("page", "Tour")
        }
        fun ready() =
            compose.waitUntil(45000) {
                var landscape = false
                scenario!!.onActivity {
                    landscape =
                        it.resources.configuration.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE
                }
                landscape &&
                    compose
                        .onAllNodesWithText("Walk freely • Drag to look around")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
            }
        ready()
        compose.onNodeWithText("CTFLY / THE CAMPUS").assertIsDisplayed()
        compose.onNodeWithTag("tourJump").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show tour destinations").assertIsDisplayed()
        compose.waitUntil(15000) { tourSurface().presentedFrames > 3 }
        scenario!!.onActivity {
            val image = requireNotNull(tourSurface().bitmap)
            for (fraction in listOf(0.2f, 0.8f)) {
                val row = (image.height * fraction).toInt()
                val colors = (1..9).map { x -> image.getPixel(image.width * x / 10, row) }.toSet()
                assertTrue("Scene must fill both upper and lower texture rows", colors.size > 2)
            }
            image.recycle()
        }
        val joystick = compose.onNodeWithTag("tourJoystick").fetchSemanticsNode().boundsInRoot
        val view = compose.onNodeWithTag("tour3d").fetchSemanticsNode().boundsInRoot
        val lookStart =
            androidx.compose.ui.geometry.Offset(
                view.left + view.width * 0.50f,
                view.top + view.height * 0.42f,
            )
        val lookEnd = lookStart + androidx.compose.ui.geometry.Offset(view.width * 0.22f, 0f)
        val heading = tourSurface().tour.pose.yaw
        compose.onRoot().performTouchInput {
            down(0, joystick.center)
            moveTo(0, joystick.center + androidx.compose.ui.geometry.Offset(0f, -60f), 100)
        }
        repeat(3) {
            compose.onRoot().performTouchInput {
                down(1, lookStart)
                moveTo(1, lookEnd, 250)
                up(1)
            }
            Thread.sleep(200)
        }
        compose.waitUntil(5000) { tourSurface().tour.pose.yaw > heading + 1f }
        assertTrue(
            "Joystick keeps walking during repeated look gestures",
            tourSurface().tour.forward > 0f,
        )
        compose.onRoot().performTouchInput {
            down(1, lookStart)
            up(0)
            down(0, joystick.center)
            moveTo(0, joystick.center + androidx.compose.ui.geometry.Offset(35f, 0f), 100)
        }
        compose.waitUntil(3000) { tourSurface().tour.strafe > 0f }
        compose.onRoot().performTouchInput {
            up(0)
            up(1)
        }
        compose.waitUntil(3000) {
            tourSurface().tour.strafe == 0f && tourSurface().tour.forward == 0f
        }
        capture("phone-tour-landscape-fixed")
        val headingAfter = tourSurface().tour.pose.yaw
        scenario!!.recreate()
        ready()
        assertEquals(headingAfter, tourSurface().tour.pose.yaw, 0.1f)
        compose.waitUntil(15000) { tourSurface().presentedFrames > 3 }
        compose.onNodeWithContentDescription("Tour picture quality").performClick()
        compose.onNodeWithTag("tourQuality-SMOOTH").performScrollTo().performClick()
        scenario!!.onActivity {
            assertEquals(
                "SMOOTH",
                ViewModelProvider(it)[AppViewModel::class.java].field("tourQuality"),
            )
            val surface = tourSurface()
            android.util.Log.i(
                "CTFLYTour",
                "Quality test: view=${surface.width}x${surface.height} buffer=${surface.renderBufferSize} drawn=${surface.tour.renderedSize}",
            )
        }
        compose.waitUntil(15000) {
            val surface = tourSurface()
            surface.tour.renderedSize == TourQuality.SMOOTH.size(surface.width, surface.height)
        }
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        ready()
        val frames = tourSurface().presentedFrames
        compose.waitUntil(10000) { tourSurface().presentedFrames > frames + 2 }
        compose.onNodeWithContentDescription("Leave 3D tour").performClick()
        compose.waitUntil(10000) {
            var portrait = false
            scenario!!.onActivity {
                portrait =
                    it.resources.configuration.orientation ==
                        android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            portrait
        }
        compose.onNodeWithText("Discover").assertExists()
    }

    @Test
    fun phoneWalkingAndRaceControlsFitWithLargeFont() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 1080x2340")
        shell("wm density 600")
        shell("settings put system font_scale 1.3")
        launch()
        change {
            it.set("tourQuality", "SMOOTH")
            it.set("page", "Tour")
        }
        fun ready() =
            compose.waitUntil(45000) {
                compose
                    .onAllNodesWithText("Walk freely • Drag to look around")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        ready()
        val surface = tourSurface()
        surface.queueEvent { surface.tour.go(TourPlan.entry(TourArea.OUTDOOR)) }
        compose.waitUntil(30000) {
            compose.onAllNodesWithTag("kartStart").fetchSemanticsNodes().isNotEmpty()
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        fun apart(a: String, b: String) {
            val aa = bounds(a)
            val bb = bounds(b)
            assertFalse("$a $aa must not overlap $b $bb", aa.overlaps(bb))
        }
        capture("phone-110-initial-layout")
        apart("tourHud", "tourJoystick")
        apart("tourVenueActions", "tourJump")
        apart("tourVenueActions", "tourSprint")
        assertTrue(bounds("kartStart").bottom < bounds("tourJump").top)
        assertTrue(bounds("tourFloor").bottom < bounds("tourJump").top)
        assertEquals(bounds("tourJump").width, bounds("tourJump").height, 1f)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        assertTrue("Jump is a large touch target", bounds("tourJump").width >= 80f * density)
        assertTrue("Joystick remains large", bounds("tourJoystick").width >= 88f * density)
        compose.onNodeWithTag("tourJoystick").performTouchInput { down(0, center) }
        repeat(3) { index ->
            compose.onNodeWithTag("tourJump").performTouchInput {
                down(1, center)
                up(1)
            }
            compose.waitUntil(3000) { tourSurface().tour.jump.height > 0.1f }
            compose.waitUntil(4000) { tourSurface().tour.jump.height == 0f }
            compose.onNodeWithTag("tourSprint").performTouchInput {
                down(1, center)
                up(1)
            }
            compose.waitUntil(2000) { tourSurface().tour.sprint == (index % 2 == 0) }
        }
        compose.onNodeWithTag("tourJoystick").performTouchInput { up(0) }
        compose.onNodeWithText("Running").assertIsDisplayed()
        compose.onNodeWithTag("tourFloor").performClick()
        compose.onNodeWithText("G · Kart circuit").performScrollTo().performClick()
        ready()
        compose.waitUntil(30000) {
            compose.onAllNodesWithTag("kartStart").fetchSemanticsNodes().isNotEmpty()
        }
        capture("phone-110-walking-controls")
        compose.onNodeWithTag("kartStart").performClick()
        compose.waitUntil(10000) { tourSurface().tour.race?.countdown == 0f }
        apart("tourHud", "tourJoystick")
        apart("kartPedals", "tourJoystick")
        val map =
            compose
                .onNodeWithContentDescription("Venue floor map")
                .fetchSemanticsNode()
                .boundsInRoot
        assertFalse(map.overlaps(bounds("kartGas")))
        compose.onNodeWithContentDescription("Toggle floor map").performClick()
        compose.onNodeWithContentDescription("Venue floor map").assertDoesNotExist()
        compose.onNodeWithContentDescription("Toggle floor map").performClick()
        compose.onNodeWithContentDescription("Venue floor map").assertIsDisplayed()
        assertTrue(bounds("kartRestart").bottom < bounds("kartGas").top)
        assertTrue(bounds("kartExit").bottom < bounds("kartGas").top)
        assertTrue(
            "Pedals occupy less than half the scene height",
            bounds("kartGas").height < bounds("tour3d").height * 0.5f,
        )
        compose.onNodeWithTag("tourJoystick").performTouchInput { down(0, center) }
        repeat(3) {
            compose.onNodeWithTag("kartGas").performTouchInput { down(1, center) }
            compose.waitUntil(2000) { tourSurface().tour.throttle == 1f }
            compose.onNodeWithTag("kartGas").performTouchInput { up(1) }
            compose.waitUntil(2000) { tourSurface().tour.throttle == 0f }
        }
        compose.onNodeWithTag("kartGas").performTouchInput { down(1, center) }
        compose.waitUntil(6000) { (tourSurface().tour.race?.speed ?: 0f) > 8f }
        compose.onNodeWithTag("kartGas").performTouchInput { up(1) }
        compose.onNodeWithTag("tourJoystick").performTouchInput { up(0) }
        capture("phone-110-race-controls")
        compose.onNodeWithTag("kartRestart").performClick()
        compose.onNodeWithText("Restart this lap?").assertIsDisplayed()
        compose.waitUntil(2000) { tourSurface().tour.experiencePaused }
        val paused = tourSurface().tour.race!!.elapsed
        Thread.sleep(800)
        assertEquals(paused, tourSurface().tour.race!!.elapsed, 0.1f)
        compose.onNodeWithText("Keep driving").performClick()
        compose.onNodeWithTag("kartExit").performClick()
        ready()
        compose.onNodeWithContentDescription("Tour controls").performClick()
        compose.onNodeWithText("Let's explore").performClick()
        compose.onNodeWithContentDescription("Leave 3D tour").performClick()
    }

    @Test
    fun everyFloorRestroomDoorsAndShowersWorkOnPhone() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 1080x2340")
        shell("wm density 480")
        launch()
        change {
            it.set("tourQuality", "BALANCED")
            it.set("page", "Tour")
        }
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("tourJump").fetchSemanticsNodes().isNotEmpty()
        }
        fun go(p: TourPose) {
            val surface = tourSurface()
            surface.stopInput()
            surface.queueEvent { surface.tour.go(TourStop("Test restroom", p, "")) }
            compose.waitUntil(45000) {
                val t = tourSurface().tour
                t.pose.area == p.area &&
                    kotlin.math.hypot(t.pose.x - p.x, t.pose.z - p.z) < .02f &&
                    compose.onAllNodesWithTag("tourJump").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
        }
        var expected = 0
        for (room in WashroomDoors.rooms) {
            go(room.outside)
            capture("restroom-111-${room.area.name.lowercase()}-entrance")
            for (door in WashroomDoors.inArea(room.area)) {
                go(TourPose(door.centreX, door.z + .4f, area = room.area))
                compose.waitUntil(5000) {
                    compose
                        .onAllNodesWithText("Open ${door.label}")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                compose.onNodeWithTag("cubicleDoor").assertIsEnabled().performClick()
                expected = expected or (1 shl door.index)
                compose.waitUntil(4000) { tourSurface().tour.doorMask == expected }
                // Door clears the opening, can close from inside, and rejects closing through a
                // person.
                Thread.sleep(500)
                go(TourPose(door.centreX, door.z, area = room.area))
                compose.waitUntil(5000) {
                    compose
                        .onAllNodesWithText("Close ${door.label}")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                compose.onNodeWithTag("cubicleDoor").assertIsNotEnabled()
                go(
                    TourPose(
                        door.centreX,
                        door.z - 1.48f,
                        yaw = Math.PI.toFloat(),
                        area = room.area,
                    )
                )
                compose.onNodeWithTag("cubicleDoor").assertIsEnabled().performClick()
                expected = expected xor (1 shl door.index)
                compose.waitUntil(4000) { tourSurface().tour.doorMask == expected }
                // Reopen for interior inspection and save every bit across floor changes.
                go(TourPose(door.centreX, door.z + .4f, area = room.area))
                compose.onNodeWithTag("cubicleDoor").performClick()
                expected = expected or (1 shl door.index)
                compose.waitUntil(4000) { tourSurface().tour.doorMask == expected }
            }
            go(
                room.washPose.copy(
                    x = room.right - .9f,
                    z = room.rowZ + .5f,
                    yaw = -.6f,
                    pitch = -.12f,
                )
            )
            capture("restroom-111-${room.area.name.lowercase()}-interior")
            val shower = WashroomDoors.inArea(room.area).first { it.label == "Shower 1" }
            go(TourPose(shower.centreX, shower.z - .05f, 0f, .12f, room.area))
            capture("restroom-111-${room.area.name.lowercase()}-shower")
            val toilet = WashroomDoors.inArea(room.area).first()
            go(TourPose(toilet.centreX, toilet.z - .35f, 0f, -.34f, room.area))
            capture("restroom-111-${room.area.name.lowercase()}-toilet")
        }
        assertEquals(WashroomDoors.validMask, expected)
        scenario!!.recreate()
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("tourJump").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(expected, tourSurface().tour.doorMask)
        go(TourPlan.entry(TourArea.OUTDOOR).pose)
        compose.onNodeWithTag("kartStart").performClick()
        compose.waitUntil(15000) { tourSurface().tour.race?.countdown == 0f }
        capture("restroom-111-track-clear")
    }

    @Test
    fun restroomFinalVisualsAndDisabledDoorContrast() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 1080x2340")
        shell("wm density 480")
        launch()
        change {
            it.set("tourQuality", "BALANCED")
            it.set("tourDoors", WashroomDoors.validMask.toString())
            it.set("page", "Tour")
        }
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("tourJump").fetchSemanticsNodes().isNotEmpty()
        }
        fun go(p: TourPose) {
            val surface = tourSurface()
            surface.stopInput()
            surface.queueEvent { surface.tour.go(TourStop("Restroom view", p, "")) }
            compose.waitUntil(45000) {
                val t = tourSurface().tour
                t.pose.area == p.area &&
                    kotlin.math.hypot(t.pose.x - p.x, t.pose.z - p.z) < .02f &&
                    compose.onAllNodesWithTag("tourJump").fetchSemanticsNodes().isNotEmpty()
            }
        }
        for (room in WashroomDoors.rooms) {
            go(
                room.washPose.copy(
                    x = room.right - .9f,
                    z = room.rowZ + .5f,
                    yaw = -.6f,
                    pitch = -.12f,
                )
            )
            capture("restroom-111-${room.area.name.lowercase()}-interior")
            val shower = WashroomDoors.inArea(room.area).first { it.label == "Shower 1" }
            go(TourPose(shower.centreX, shower.z - .05f, 0f, .12f, room.area))
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("Close Shower 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("cubicleDoor").assertIsNotEnabled()
            capture("restroom-111-${room.area.name.lowercase()}-shower")
            val toilet = WashroomDoors.inArea(room.area).first()
            go(TourPose(toilet.centreX, toilet.z - .35f, 0f, -.34f, room.area))
            capture("restroom-111-${room.area.name.lowercase()}-toilet")
        }
        val pavilion = WashroomDoors.rooms.first { it.area == TourArea.OUTDOOR }
        go(
            pavilion.outside.copy(
                x = pavilion.entranceX + 4f,
                z = pavilion.front + 5f,
                yaw = -.6f,
                pitch = -.08f,
            )
        )
        capture("restroom-111-pavilion-relocated")
        go(TourPlan.entry(TourArea.OUTDOOR).pose)
        compose.onNodeWithTag("kartStart").performClick()
        compose.waitUntil(15000) { tourSurface().tour.race?.countdown == 0f }
        capture("restroom-111-track-clear")
    }

    @Test
    fun phoneDefaultTourHasClearViewAndBottomControls() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 1080x2340")
        shell("wm density 480")
        launch()
        change {
            it.set("tourQuality", "SMOOTH")
            it.set("page", "Tour")
        }
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("tourJump").fetchSemanticsNodes().isNotEmpty()
        }
        val surface = tourSurface()
        surface.queueEvent { surface.tour.go(TourPlan.entry(TourArea.OUTDOOR)) }
        compose.waitUntil(30000) {
            compose.onAllNodesWithTag("kartStart").fetchSemanticsNodes().isNotEmpty()
        }
        capture("phone-110-walking-standard")
        compose.onNodeWithTag("kartStart").performClick()
        compose.waitUntil(10000) { tourSurface().tour.race?.countdown == 0f }
        val scene = compose.onNodeWithTag("tour3d").fetchSemanticsNode().boundsInRoot
        val pedals = compose.onNodeWithTag("kartPedals").fetchSemanticsNode().boundsInRoot
        assertTrue("Keep pedals to the right of the car", pedals.left > scene.center.x)
        val joystick = compose.onNodeWithTag("tourJoystick").fetchSemanticsNode().boundsInRoot
        val hud = compose.onNodeWithTag("tourHud").fetchSemanticsNode().boundsInRoot
        assertFalse("Speed display and steering must remain separate", hud.overlaps(joystick))
        capture("phone-110-race-standard")
    }

    @Test
    fun tabletTourControlsAndFloorDestinationsStayUsable() {
        org.junit.Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 1600x1000")
        shell("wm density 200")
        launch()
        change {
            it.set("tourQuality", "BALANCED")
            it.set("page", "Tour")
        }
        compose.waitUntil(45000) {
            compose.onAllNodesWithTag("tourJump").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNodeWithText("L2 Billiards", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.waitUntil(30000) {
            tourSurface().tour.pose.area == TourArea.BILLIARDS &&
                compose.onAllNodesWithTag("tourJump").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tourJump").performClick()
        compose.waitUntil(3000) { tourSurface().tour.jump.height > 0.1f }
        compose.onNodeWithContentDescription("Toggle floor map").performClick()
        val mapBounds =
            compose
                .onNodeWithContentDescription("Venue floor map")
                .fetchSemanticsNode()
                .boundsInRoot
        val actionBounds =
            compose.onNodeWithTag("tourVenueActions").fetchSemanticsNode().boundsInRoot
        assertFalse("Tablet map must not cover venue actions", mapBounds.overlaps(actionBounds))
        capture("tablet-110-tour-controls")
        compose.onNodeWithContentDescription("Expand 3D view").performClick()
        compose.onNodeWithContentDescription("Tour picture quality").performClick()
        compose.onNodeWithTag("tourQuality-BALANCED").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Leave 3D tour").performClick()
    }

    @Test
    fun standaloneLoginHasNoPortalSwitcher() {
        launch(signedIn = false)
        compose.onNodeWithText("Explore the club first").assertDoesNotExist()
        compose.onNodeWithText("Customer").assertDoesNotExist()
        compose.onNodeWithText("Staff / Admin").assertDoesNotExist()
        if (BuildConfig.STAFF_APP) {
            compose.onNodeWithText("New here? Join CTFLY").assertDoesNotExist()
            compose.onNodeWithText("Explore the club first").assertDoesNotExist()
            compose.onNodeWithText("Your shift starts here.").assertExists()
        } else {
            compose.onNodeWithText("New here? Join CTFLY").assertExists()
        }
        capture(
            if (BuildConfig.STAFF_APP) "staff-standalone-login" else "customer-standalone-login"
        )
    }

    @Test
    fun wrongRoleCannotRestoreSession() {
        launch(if (BuildConfig.STAFF_APP) "customer" else "staff", allowWrongRole = true)
        compose.waitUntil(5000) {
            var rejected = false
            scenario!!.onActivity {
                rejected = !ViewModelProvider(it)[AppViewModel::class.java].hasAccount
            }
            rejected
        }
        compose.onNodeWithText("Email address").assertExists()
        assertFalse(app.gatewayFactory(app).signedIn)
    }

    @Test
    fun wrongRoleCannotSignIn() {
        launch(if (BuildConfig.STAFF_APP) "customer" else "staff", signedIn = false)
        change {
            it.set("email", "wrong-role@example.test")
            it.password = "GoodPassword123"
            it.authenticate()
        }
        compose.waitUntil(5000) {
            var done = false
            scenario!!.onActivity { done = !ViewModelProvider(it)[AppViewModel::class.java].busy }
            done
        }
        scenario!!.onActivity {
            assertFalse(ViewModelProvider(it)[AppViewModel::class.java].hasAccount)
        }
        compose.onNodeWithText("Email address").assertExists()
    }

    @Test
    fun typedLoginAndPasswordSurviveRotation() {
        launch(signedIn = false)
        compose.onNodeWithText("Email address").performTextInput("player@example.test")
        compose.onNodeWithText("Password").performScrollTo().performTextInput("A_Strong_Password")
        scenario!!.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        scenario!!.onActivity {
            val vm = ViewModelProvider(it)[AppViewModel::class.java]
            assertEquals("player@example.test", vm.field("email"))
            assertEquals("A_Strong_Password", vm.password)
        }
        compose
            .onNodeWithText("Email address")
            .performScrollTo()
            .assertTextContains("player@example.test")
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) {
            app.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_PORTRAIT
        }
        compose.waitForIdle()
        capture("phone-login")
    }

    @Test
    fun shoppingAndBookingDraftSurviveRecreation() {
        launch()
        change {
            it.cartChange("1", 2)
            it.set("bookDate", "2030-01-01")
            it.set("bookTime", "19:45")
            it.set("selectedResource", "1")
            it.set("page", "Play")
            it.set("dialog", "book")
        }
        scenario!!.recreate()
        compose.waitForIdle()
        scenario!!.onActivity {
            val vm = ViewModelProvider(it)[AppViewModel::class.java]
            assertEquals(2, vm.cart()["1"])
            assertEquals("19:45", vm.field("bookTime"))
            assertEquals("book", vm.field("dialog"))
        }
        compose.onNodeWithText("Make it your time.").assertExists()
        capture("phone-booking")
    }

    @Test
    fun phoneAllCustomerScreensAndCafeSearch() {
        launch()
        capture("phone-discover")
        for (page in listOf("Play", "Café", "Events", "Club", "Bookings")) {
            change { it.set("page", page) }
            capture("phone-${page.replace("é","e").lowercase()}")
        }
        change { it.set("page", "Café") }
        compose.onNodeWithText("Search drinks & bites").performTextInput("tea")
        scenario!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Search drinks & bites").assertTextContains("tea")
        compose.onNodeWithText("Iced lemon tea").assertExists()
    }

    @Test
    fun staffSeesOnlyCrewPages() {
        launch("staff")
        compose.onNodeWithText("Keep the club moving.").assertExists()
        compose.onNodeWithText("Discover").assertDoesNotExist()
        compose.onNodeWithText("Café").assertDoesNotExist()
        for (page in listOf("Operations", "My shifts", "Handover", "Equipment")) {
            change { it.set("page", page) }
            capture("staff-${page.replace(" ","-")}")
        }
        change { it.set("page", "Café") }
        compose.onNodeWithText("Keep the club moving.").assertExists()
    }

    @Test
    fun tabletUsesRailAndSecondPane() {
        shell("wm size 1600x1000")
        shell("wm density 160")
        launch()
        scenario!!.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
        compose.onNodeWithText("YOUR NEXT SESSION").assertExists()
        capture("tablet-discover")
        change { it.set("page", "Café") }
        capture("tablet-cafe")
    }

    @Test
    fun smallPhoneWithLargeTextKeepsFormsUsable() {
        shell("wm size 640x1280")
        shell("wm density 320")
        shell("settings put system font_scale 1.3")
        try {
            launch(signedIn = false)
            compose
                .onNodeWithText("Email address")
                .performScrollTo()
                .performTextInput("small@example.test")
            compose.onNodeWithText("Password").performScrollTo().performTextInput("GoodPassword123")
            scenario!!.recreate()
            compose.waitForIdle()
            compose
                .onNodeWithText("Email address")
                .performScrollTo()
                .assertTextContains("small@example.test")
            capture("small-phone-large-text")
        } finally {
            shell("settings put system font_scale 1.0")
        }
    }

    @Test
    fun latestExtensionQuoteWinsWhenSelectionChanges() {
        launch("staff")
        change { vm ->
            vm.set("paymentId", "00000000-0000-0000-0000-000000000001")
            vm.set("paymentKind", "Extension30")
            vm.quotePayment()
            vm.set("paymentKind", "Extension60")
            vm.quotePayment()
        }
        compose.waitUntil(5000) {
            var done = false
            scenario!!.onActivity {
                done =
                    ViewModelProvider(it)[AppViewModel::class.java].paymentQuote.text("kind") ==
                        "Extension60"
            }
            done
        }
        scenario!!.onActivity {
            assertEquals(
                600,
                ViewModelProvider(it)[AppViewModel::class.java].paymentQuote.optInt("total"),
            )
        }
    }

    @Test
    fun managerPagesRenderWithData() {
        launch("admin")
        for (tab in
            listOf("Spaces", "Inventory", "Events", "Staff", "Members", "Rewards", "Reports")) {
            change {
                it.set("page", "Manage")
                it.set("manageTab", tab)
            }
            capture("manager-${tab.lowercase()}")
        }
    }
}
