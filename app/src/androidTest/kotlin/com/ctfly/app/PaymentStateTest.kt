package com.ctfly.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.UUID
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/**
 * Offline UI fixture only; transaction/ownership rules are exercised against SQL in database.mjs.
 */
class PaymentFixtureGateway(
    val fixture: JSONObject,
    private val delegate: FixtureGateway = FixtureGateway(fixture, true),
) : ClubGateway by delegate {
    private val results = mutableMapOf<String, JSONObject>()
    val paymentRequests = mutableListOf<String>()
    var failAfterSuccessOnce = false

    override suspend fun rpc(name: String, body: JSONObject, authenticated: Boolean): JSONObject {
        if (name == "ctfly_snapshot") fixture.put("server_time", Instant.now().toString())
        if (name != "ctfly_action" || !body.text("p_action").startsWith("test_"))
            return delegate.rpc(name, body, authenticated)
        val request = body.text("p_request")
        paymentRequests.add(request)
        results[request]?.let {
            return JSONObject(it.toString())
        }
        val d = body.getJSONObject("p_data")
        val action = body.text("p_action")
        if(action=="test_quote" && d.text("promo_code")=="BAD") throw ApiFailure(400,"Promo code does not exist. Check the code.")
        android.util.Log.i("PaymentFixture", "action=$action data=$d")
        var response: JSONObject
        if (action in listOf("test_quote", "test_checkout")) {
            val source = fixture.rows("orders").first { it.text("id") == d.text("id") }
            val quote =
                obj(
                    "kind" to "Order",
                    "source_id" to source.text("id"),
                    "environment" to "test",
                    "provider" to "simulator",
                    "subtotal" to source.optInt("total"),
                    "discount" to 0,
                    "total" to source.optInt("total"),
                    "points" to source.optInt("total") / 100,
                    "expires_at" to Instant.now().plusSeconds(600).toString(),
                    "items" to
                        org.json
                            .JSONArray()
                            .put(
                                obj(
                                    "name" to "Iced coffee",
                                    "qty" to 1,
                                    "price" to source.optInt("total"),
                                )
                            ),
                )
            if (action == "test_quote") response = quote
            else {
                val pay =
                    JSONObject(quote.toString())
                        .put("id", UUID.randomUUID().toString())
                        .put("user_id", fixture.getJSONObject("profile").text("id"))
                        .put("method", d.text("method"))
                        .put("reference", "TEST-UI-${UUID.randomUUID()}")
                        .put("status", "Pending")
                        .put("created_at", Instant.now().toString())
                fixture.getJSONArray("test_payments").put(pay)
                response = obj("payment" to pay, "message" to "Test mode. No money was charged.")
            }
        } else {
            val pay = fixture.rows("test_payments").first { it.text("id") == d.text("id") }
            if (action == "test_refund") {
                pay.put("status", "Refunded")
                pay.optJSONObject("receipt")?.put("status", "Void")
            } else if (pay.text("status") in listOf("Pending", "Processing")) {
                pay.put(
                    "status",
                    when (d.text("outcome")) {
                        "Success" -> "Succeeded"
                        "Failure" -> "Failed"
                        "Cancel" -> "Cancelled"
                        else -> "Processing"
                    },
                )
                if (pay.text("status") == "Succeeded") {
                    val receipt =
                        obj(
                            "id" to UUID.randomUUID().toString(),
                            "kind" to "Order",
                            "source_id" to pay.text("source_id"),
                            "environment" to "test",
                            "payment_reference" to pay.text("reference"),
                            "total" to pay.optInt("total"),
                            "subtotal" to pay.optInt("subtotal"),
                            "discount" to 0,
                            "points" to pay.optInt("points"),
                            "status" to "Paid",
                            "method" to "Test ${pay.text("method")}",
                            "created_at" to Instant.now().toString(),
                        )
                    pay.put("bill_id", receipt.text("id")).put("receipt", receipt)
                    fixture.getJSONArray("bills").put(receipt)
                    fixture
                        .rows("orders")
                        .first { it.text("id") == pay.text("source_id") }
                        .put("paid", true)
                    fixture.getJSONObject("profile").put("test_points", pay.optInt("points"))
                }
            }
            response =
                obj(
                    "payment" to pay,
                    "receipt" to pay.optJSONObject("receipt"),
                    "message" to "Test mode. No money was charged.",
                )
        }
        results[request] = JSONObject(response.toString())
        android.util.Log.i("PaymentFixture", "response=$response")
        if (failAfterSuccessOnce && action == "test_resolve" && d.text("outcome") == "Success") {
            failAfterSuccessOnce = false
            throw java.io.IOException("Connection lost. Retry the test payment.")
        }
        return response
    }
}

class PaymentStateTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst
        get() = InstrumentationRegistry.getInstrumentation()

    private val app
        get() = inst.targetContext.applicationContext as CtflyApplication

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var gateway: PaymentFixtureGateway
    private lateinit var orderId: String

    private fun shell(cmd: String) {
        inst.uiAutomation.executeShellCommand(cmd).close()
        Thread.sleep(600)
    }

    private fun launch() {
        app.getSharedPreferences("ctfly_appearance", 0).edit().clear().commit()
        val role = if (BuildConfig.STAFF_APP) "staff" else "customer"
        val f =
            JSONObject(
                inst.context.assets.open("payment-$role.json").bufferedReader().use {
                    it.readText()
                }
            )
        orderId =
            f.rows("orders")
                .first { it.text("status") == "Pending" && !it.optBoolean("paid") }
                .text("id")
        gateway = PaymentFixtureGateway(f)
        app.gatewayFactory = { gateway }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    private fun change(block: (AppViewModel) -> Unit) {
        scenario!!.onActivity { block(ViewModelProvider(it)[AppViewModel::class.java]) }
        compose.waitForIdle()
    }

    private fun start(method: String = "TNG") {
        change { it.openCheckout("Order", orderId) }
        compose.waitUntil(5000) { gateway.paymentRequests.isNotEmpty() }
        compose.onNodeWithTag("checkout-list").performScrollToNode(hasTestTag("method-$method"))
        compose.onNodeWithTag("method-$method").performScrollTo().performClick()
        compose.onNodeWithTag("start-test-payment").performScrollTo()
        compose.onNodeWithTag("start-test-payment").performClick()
        try {
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("confirm-test-payment").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            capture("payment-start-failure")
            throw e
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(1000)
        compose.waitForIdle()
        val dir = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        inst.uiAutomation.takeScreenshot().let { bitmap ->
            File(dir, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        shell("mkdir -p /sdcard/Download/CTFLY-test")
        shell("cp ${dir.absolutePath}/$name.png /sdcard/Download/CTFLY-test/$name.png")
    }

    @After
    fun cleanup() {
        scenario?.close()
        app.gatewayFactory = { CtflyApi(it) }
        shell("wm size reset")
        shell("wm density reset")
        shell("settings put system font_scale 1.0")
    }

    @Test
    fun promoCodeCanBeCorrectedAfterQuoteError() {
        Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch()
        change {it.openCheckout("Order",orderId)}
        compose.onNodeWithText("Promo code (optional)").performScrollTo().performTextInput("BAD")
        compose.onNodeWithText("Apply / remove code").performClick()
        compose.onNodeWithText("Promo code does not exist. Check the code.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Promo code (optional)").performScrollTo().performTextClearance()
        compose.onNodeWithText("Apply / remove code").performClick()
        compose.onNodeWithTag("start-test-payment").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun smallPhoneCardPaymentSurvivesRecreationAndSharesClearlyMarkedReceipt() {
        Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 1080x1920")
        shell("wm density 480")
        shell("settings put system font_scale 1.3")
        launch()
        start("Card")
        capture("payment-phone-card-pending")
        scenario!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("confirm-test-payment").performScrollTo().performClick()
        compose.waitUntil(5000) {
            gateway.fixture.rows("test_payments").any {
                it.text("source_id") == orderId && it.text("status") == "Succeeded"
            }
        }
        compose.onNodeWithText("Test payment successful").performScrollTo().assertIsDisplayed()
        capture("payment-phone-success")
        compose.onNodeWithText("View / share receipt").performScrollTo().performClick()
        compose
            .onNodeWithText("TEST RECEIPT - NO MONEY CHARGED", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Share PDF receipt").performScrollTo().assertIsDisplayed()
        capture("payment-phone-test-receipt")
        assertEquals(0, gateway.fixture.getJSONObject("profile").optInt("points"))
        assertEquals(0, gateway.fixture.getJSONObject("profile").optInt("spent"))
        change { it.set("dialog", "") }
        compose.onNodeWithText("Request test refund").performScrollTo().performClick()
        compose.onNodeWithText("Confirm test refund").performScrollTo().performClick()
        compose.onNodeWithText("Test refund completed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun failureProcessingCancellationAndTransportRetryKeepSingleReceipt() {
        Assume.assumeFalse(BuildConfig.STAFF_APP)
        launch()
        start("FPX")
        compose.onNodeWithText("Demo controls").performScrollTo().performClick()
        compose.onNodeWithText("Simulate failure").performScrollTo().performClick()
        compose.onNodeWithText("Retry test checkout").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("start-test-payment").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("start-test-payment").performScrollTo().performClick()
        compose.onNodeWithText("Demo controls").performScrollTo().performClick()
        compose.onNodeWithText("Simulate processing").performScrollTo().performClick()
        compose
            .onNodeWithText(
                "Still processing in the simulator. Complete or cancel this demo below."
            )
            .performScrollTo()
            .assertIsDisplayed()
        capture("payment-processing")
        scenario!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Cancel test payment").performScrollTo().performClick()
        compose.onNodeWithText("Test payment cancelled").performScrollTo().assertIsDisplayed()
        start("TNG")
        gateway.failAfterSuccessOnce = true
        compose.onNodeWithTag("confirm-test-payment").performScrollTo().performClick()
        compose.waitUntil(5000) { !gateway.failAfterSuccessOnce }
        // Response loss after commit: retry uses the same persisted request ID.
        compose.onNodeWithTag("confirm-test-payment").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Test payment successful").fetchSemanticsNodes().isNotEmpty()
        }
        val requests = gateway.paymentRequests.takeLast(2)
        assertEquals(requests[0], requests[1])
        assertEquals(1, gateway.fixture.rows("bills").count { it.text("source_id") == orderId })
        capture("payment-tng-success")
    }

    @Test
    fun tabletCheckoutAndChineseLabelsFit() {
        Assume.assumeFalse(BuildConfig.STAFF_APP)
        shell("wm size 2304x1440")
        shell("wm density 240")
        launch()
        start("TNG")
        val preferences = AppPreferences(app)
        preferences.chooseLanguage("zh")
        scenario!!.recreate()
        change { it.set("page", "Settings") }
        change { it.set("page", "Checkout") }
        compose.onNodeWithText("测试模式 · 不会扣款").performScrollTo().assertIsDisplayed()
        capture("payment-tablet-chinese")
    }

    @Test
    fun staffSeesCustomerTestTransactionsWithoutCustomerPaymentControls() {
        Assume.assumeTrue(BuildConfig.STAFF_APP)
        launch()
        change { it.set("page", "Transactions") }
        compose.onNodeWithText("Test transactions").assertIsDisplayed()
        capture("payment-staff-transactions")
        change { vm ->
            vm.resumeCheckout(vm.rows("test_payments").first { it.text("status") == "Succeeded" })
        }
        compose.onNodeWithText("TEST MODE · NO MONEY CHARGED").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("confirm-test-payment").assertDoesNotExist()
        capture("payment-staff-details")
    }
}
