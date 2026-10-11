package com.ctfly.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class RevisionStateTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrument get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrument.targetContext.applicationContext as CtflyApplication
    private var scenario:ActivityScenario<MainActivity>?=null
    private lateinit var gateway:FixtureGateway
    private fun launch(offline:Boolean=false) {
        app.getSharedPreferences("ctfly_appearance",0).edit().clear().commit()
        val role=if(BuildConfig.STAFF_APP)"staff" else "customer"
        val fixture=JSONObject(instrument.context.assets.open("$role.json").bufferedReader().use{it.readText()}).put("server_time",Instant.now().toString())
        gateway=FixtureGateway(fixture,true)
        app.gatewayFactory={ if(offline) object:ClubGateway by gateway {
            override val cachedSnapshot get()=JSONObject(fixture.toString())
            override suspend fun rpc(name:String,body:JSONObject,authenticated:Boolean):JSONObject {throw java.io.IOException("Offline. Check your connection.")}
        } else gateway }
        scenario=ActivityScenario.launch(MainActivity::class.java);compose.waitForIdle()
    }
    private fun change(f:(AppViewModel)->Unit){scenario!!.onActivity{f(ViewModelProvider(it)[AppViewModel::class.java])};compose.waitForIdle()}
    private fun capture(name:String){compose.waitForIdle();Thread.sleep(800);val dir=File(app.getExternalFilesDir(null),"revision").apply{mkdirs()};instrument.uiAutomation.takeScreenshot().let{b->File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}};instrument.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/CTFLY-revision").close();Thread.sleep(100);instrument.uiAutomation.executeShellCommand("cp ${dir.absolutePath}/$name.png /sdcard/Download/CTFLY-revision/$name.png").close();Thread.sleep(100)}
    @After fun cleanup(){scenario?.close();app.gatewayFactory={CtflyApi(it)};instrument.uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()}
    @Test fun cachedLoginSurvivesOfflineColdStartAndExplicitLogout() {
        launch(offline=true)
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        scenario!!.onActivity{assertTrue(ViewModelProvider(it)[AppViewModel::class.java].hasAccount)}
        scenario!!.close();scenario=null;launch(offline=true)
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        change{it.logout()}
        compose.waitUntil(5000){!gateway.signedIn}
        compose.onNodeWithText(if(BuildConfig.STAFF_APP)"Your shift starts here." else "Good to see you.").assertIsDisplayed()
    }
    @Test fun currentTimeBookingResetsDraftAndErrorAppearsAboveFields() {
        Assume.assumeFalse(BuildConfig.STAFF_APP);launch()
        var resource=""
        change{vm->resource=vm.rows("resources").first{it.text("kind")=="PC"}.text("id");vm.set("bookTime","00:00");vm.set("bookMinutes","180");vm.resetBookingDraft(resource);vm.set("dialog","book")}
        compose.onNodeWithText("30 minutes").assertIsDisplayed()
        change{vm->assertTrue(vm.field("bookTime").isBlank());vm.set("bookDate","2020-01-01")}
        compose.onNodeWithText("Confirm reservation").performScrollTo().performClick()
        val error=compose.onNodeWithText("Reservation date is in the past. Choose today or a future date.")
        error.assertIsDisplayed();assertTrue(error.fetchSemanticsNode().boundsInRoot.top<compose.onNodeWithText("Confirm reservation").fetchSemanticsNode().boundsInRoot.top)
        capture("booking-error-in-front")
        change{vm->vm.resetBookingDraft(resource);vm.set("dialog","book")}
        compose.onNodeWithText("Confirm reservation").performScrollTo().performClick()
        compose.waitUntil(5000){gateway.lastBooking!=null}
        assertEquals(30,gateway.lastBooking!!.optInt("minutes"))
        val difference=java.time.Duration.between(Instant.parse(gateway.lastBooking!!.text("starts_at")),Instant.now()).seconds
        assertTrue("Default start is current minute",difference in -2..62)
    }
    @Test fun staffSpacingAndCompetitionEditorExposePrizesFeeAndPoster() {
        Assume.assumeTrue(BuildConfig.STAFF_APP);launch()
        change{it.set("page","Handover")}
        val title=compose.onNodeWithText("The shift journal.").fetchSemanticsNode().boundsInRoot
        val button=compose.onNodeWithText("Write a handover").fetchSemanticsNode().boundsInRoot
        assertTrue("Handover button has breathing room",button.top-title.bottom>8)
        capture("staff-handover-spacing")
        change{it.set("page","Equipment")}
        val equipment=compose.onNodeWithText("Equipment care.").fetchSemanticsNode().boundsInRoot
        val report=compose.onNodeWithText("Report an issue").fetchSemanticsNode().boundsInRoot
        assertTrue(report.top-equipment.bottom>8)
        change{it.set("page","Events")}
        compose.onNodeWithText("Create competition").performClick()
        compose.onNodeWithText("Default competition poster").assertIsDisplayed()
        compose.onNodeWithText("First prize").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Consolation prize").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.onNodeWithText("Competition name: this field is required.",substring=true).assertIsDisplayed()
        capture("competition-form-validation")
        change{it.set("dialog","")}
        compose.onNodeWithText("Promo codes").performClick();compose.onNodeWithText("Create promo code").performClick()
        compose.onNodeWithText("Promo code").assertIsDisplayed()
        compose.onNodeWithText("Total uses").performScrollTo().assertIsDisplayed()
        capture("staff-promo-code-editor")
    }
    @Test fun nativeReceiptPdfRendersUnicodeAndContainsFullSessionDetails() {
        launch()
        val bill=obj("id" to "demo-2026-001","kind" to "Booking","status" to "Paid","created_at" to "2026-10-11T02:35:00Z","environment" to "test","method" to "Test TNG","payment_reference" to "TEST-DEMO-RECEIPT","subtotal" to 1200,"discount" to 300,"total" to 900,"points" to 9,"details" to obj("customer_name" to "Student 李明","activity" to "Racing simulator","resource" to "VIP Racing Simulator 01","zone" to "VIP","starts_at" to "2026-10-12T04:00:00Z","ends_at" to "2026-10-12T05:30:00Z","duration_minutes" to 90,"players" to 1,"hourly_rate" to 800,"promo_code" to "SCHOOL25"))
        change{it.set("receipt",bill.toString());it.set("dialog","receipt")}
        compose.onNodeWithText("Duration: 90 minutes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Share PDF receipt").performScrollTo().assertIsDisplayed()
        val lines=receiptLines(bill);assertTrue(lines.any{it.contains("Student 李明")});assertTrue(lines.contains("TOTAL: RM 9.00"))
        val file=createReceiptPdf(app,bill);assertTrue(file.length()>1000)
        val dir=File(app.getExternalFilesDir(null),"revision").apply{mkdirs()};file.copyTo(File(dir,"receipt-native.pdf"),true)
        PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use{renderer->assertTrue(renderer.pageCount>=1);renderer.openPage(0).use{page->val b=Bitmap.createBitmap(page.width*3,page.height*3,Bitmap.Config.ARGB_8888);page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);File(dir,"receipt-native.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}}
        instrument.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/CTFLY-revision").close();Thread.sleep(100)
        instrument.uiAutomation.executeShellCommand("cp ${dir.absolutePath}/receipt-native.pdf /sdcard/Download/CTFLY-revision/receipt-native.pdf").close();Thread.sleep(100)
        instrument.uiAutomation.executeShellCommand("cp ${dir.absolutePath}/receipt-native.png /sdcard/Download/CTFLY-revision/receipt-native.png").close();Thread.sleep(100)
        capture("receipt-app-details")
    }
    @Test fun sessionVaultKeepsEncryptedSessionAcrossApiInstances() {
        val vault=SessionVault(app);vault.clear()
        val expected=obj("access_token" to "fixture-access","refresh_token" to "fixture-refresh","expires_at" to 1,"cached_snapshot" to obj("profile" to obj("name" to "Cached student","role" to "customer")))
        try {vault.save(expected);val stored=app.getSharedPreferences("ctfly_session",0).getString("session","")!!;assertFalse(stored.contains("fixture-refresh"));val api=CtflyApi(app);assertTrue(api.signedIn);assertEquals("Cached student",api.cachedSnapshot.getJSONObject("profile").text("name"));api.localLogout();assertFalse(CtflyApi(app).signedIn)}finally{vault.clear()}
    }
}
