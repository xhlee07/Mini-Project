package com.ctfly.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AppViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val api = (application as CtflyApplication).gatewayFactory(application)
    var data by mutableStateOf(if (api.signedIn) api.cachedSnapshot else JSONObject())
        private set

    var busy by mutableStateOf(false)
        private set

    var loading by mutableStateOf(true)
        private set

    var message by mutableStateOf("")
        private set

    var validationError by mutableStateOf("")
        private set

    fun clearValidationError() {
        validationError = ""
    }

    fun invalid(text: String) {
        validationError = text
    }

    fun uploadEventPoster(bytes: ByteArray) = run {
        val url = api.uploadPoster(bytes)
        set("formData", JSONObject(field("formData", "{}")).put("poster_url", url).toString())
    }

    fun resetBookingDraft(id: String) {
        listOf("bookDate", "bookTime", "bookMinutes", "bookGuests").forEach {
            saved.remove<String>(it)
        }
        set("selectedResource", id)
    }

    var loadError by mutableStateOf("")
        private set

    var paymentQuote by mutableStateOf(JSONObject())
        private set

    var checkoutQuote by mutableStateOf(JSONObject())
        private set

    var checkoutError by mutableStateOf("")
        private set

    private var checkoutJob: Job? = null

    val testPaymentsEnabled
        get() = data.text("payment_mode") == BuildConfig.PAYMENT_MODE

    fun openCheckout(kind: String, id: String) {
        if (!testPaymentsEnabled) {
            notify("Test checkout setup is not available. Refresh and try again.")
            return
        }
        set("checkoutReturn", if (crew) "Transactions" else "Bookings")
        set("checkoutKind", kind)
        set("checkoutSource", id)
        set("checkoutPayment", "")
        set("checkoutResult", "")
        set("checkoutMethod", "TNG")
        set("checkoutPromoCode", "")
        set("checkoutAppliedCode", "")
        set("page", "Checkout")
        reloadCheckout()
    }

    fun resumeCheckout(payment: JSONObject) {
        set("checkoutReturn", if (crew) "Transactions" else "Bookings")
        set("checkoutKind", payment.text("kind"))
        set("checkoutSource", payment.text("source_id"))
        set("checkoutPayment", payment.text("id"))
        set("checkoutResult", payment.toString())
        set("page", "Checkout")
    }

    fun currentTestPayment(): JSONObject {
        val id = field("checkoutPayment")
        if (id.isBlank()) return JSONObject()
        return rows("test_payments").find { it.text("id") == id }
            ?: runCatching { JSONObject(field("checkoutResult")) }.getOrDefault(JSONObject())
    }

    fun canRefundTestPayment(payment: JSONObject): Boolean {
        if (payment.text("status") != "Succeeded") return false
        val id = payment.text("source_id")
        return when (payment.text("kind")) {
            "Order" ->
                rows("orders").any {
                    it.text("id") == id && it.text("status") == "Pending" && it.optBoolean("paid")
                }
            "Booking" ->
                rows("bookings").any {
                    it.text("id") == id &&
                        it.text("status") == "Confirmed" &&
                        it.optBoolean("paid") &&
                        runCatching {
                                java.time.Instant.parse(it.text("starts_at")).toEpochMilli() >
                                    nowMillis
                            }
                            .getOrDefault(false)
                }
            "Event" ->
                rows("registrations").any { r ->
                    r.text("id") == id &&
                        r.text("status") == "Registered" &&
                        r.optBoolean("paid") &&
                        rows("events").any { e ->
                            e.text("id") == r.text("event_id") &&
                                e.text("status") == "Open" &&
                                runCatching {
                                        java.time.Instant.parse(e.text("starts_at"))
                                            .toEpochMilli() > nowMillis
                                    }
                                    .getOrDefault(false)
                        }
                }
            else -> false
        }
    }

    fun reloadCheckout() {
        checkoutJob?.cancel()
        checkoutQuote = JSONObject()
        checkoutError = ""
        val kind = field("checkoutKind")
        val id = field("checkoutSource")
        val generation = accountGeneration
        checkoutJob =
            viewModelScope.launch {
                try {
                    val q =
                        api.rpc(
                            "ctfly_action",
                            obj(
                                "p_action" to "test_quote",
                                "p_data" to
                                    obj(
                                        "kind" to kind,
                                        "id" to id,
                                        "promo_code" to field("checkoutAppliedCode"),
                                    ),
                                "p_request" to UUID.randomUUID().toString(),
                            ),
                        )
                    require(q.text("provider") == "simulator" && q.text("environment") == "test") {
                        "Test checkout setup is not available. Refresh and try again."
                    }
                    if (
                        generation == accountGeneration &&
                            field("checkoutSource") == id &&
                            field("checkoutKind") == kind
                    )
                        checkoutQuote = q
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (generation == accountGeneration && field("checkoutSource") == id)
                        checkoutError = e.message ?: "Unable to load checkout."
                }
            }
    }

    private fun acceptTestResult(result: JSONObject) {
        result.optJSONObject("payment")?.let {
            result.optJSONObject("receipt")?.let { receipt -> it.put("receipt", receipt) }
            set("checkoutPayment", it.text("id"))
            set("checkoutResult", it.toString())
            val payments = JSONArray()
            rows("test_payments")
                .filter { p -> p.text("id") != it.text("id") }
                .forEach { p -> payments.put(p) }
            payments.put(it)
            data = JSONObject(data.toString()).put("test_payments", payments)
        }
    }

    fun startTestPayment() =
        action(
            "test_checkout",
            obj(
                "kind" to field("checkoutKind"),
                "id" to field("checkoutSource"),
                "method" to field("checkoutMethod", "TNG"),
                "expected_total" to checkoutQuote.optInt("total", -1),
                "promo_code" to field("checkoutAppliedCode"),
            ),
            onResult = ::acceptTestResult,
        )

    fun resolveTestPayment(outcome: String) =
        action(
            "test_resolve",
            obj("id" to field("checkoutPayment"), "outcome" to outcome),
            onResult = ::acceptTestResult,
        )

    fun refundTestPayment() =
        action(
            "test_refund",
            obj("id" to field("checkoutPayment")),
            onResult = ::acceptTestResult,
        ) {
            set("dialog", "")
        }

    var hasAccount by mutableStateOf(api.signedIn)
        private set

    var fieldVersion by mutableIntStateOf(0)
        private set

    // In-memory only: survives rotation via ViewModel, never written to saved-state bundles.
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var nowMillis by mutableLongStateOf(System.currentTimeMillis())
        private set

    val profile: JSONObject
        get() = data.optJSONObject("profile") ?: JSONObject()

    val role
        get() = profile.text("role")

    val crew
        get() = BuildConfig.STAFF_APP && role in listOf("staff", "admin")

    val admin
        get() = BuildConfig.STAFF_APP && role == "admin"

    private fun acceptsRole(actualRole: String?) =
        if (BuildConfig.STAFF_APP) actualRole in listOf("staff", "admin")
        else actualRole == "customer"

    private val wrongAppMessage
        get() =
            if (BuildConfig.STAFF_APP) "This account is a customer. Sign in with CTFLY Gaming Cafe."
            else "This account is staff or admin. Sign in with Staff CTFLY."

    val userId
        get() = profile.text("id")

    private var accountGeneration = 0
    private var refreshInFlight = false
    private var serverOffset = 0L
    private var quoteJob: Job? = null

    private fun acceptSnapshot(snapshot: JSONObject) {
        data = snapshot
        serverOffset =
            runCatching {
                    java.time.Instant.parse(snapshot.text("server_time")).toEpochMilli() -
                        System.currentTimeMillis()
                }
                .getOrDefault(0L)
        tick()
    }

    fun tick() {
        nowMillis = System.currentTimeMillis() + serverOffset
    }

    fun rows(key: String) = data.rows(key)

    fun field(key: String, default: String = ""): String {
        fieldVersion
        return saved[key] ?: default
    }

    fun set(key: String, value: String) {
        if (key !in listOf("pendingAction", "pendingBody", "pendingRequest")) validationError = ""
        saved[key] = value
        fieldVersion++
    }

    fun notify(text: String) {
        invalid(text)
    }

    fun dismissMessage() {
        message = ""
    }

    init {
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        if (!hasAccount) {
            data = JSONObject()
            loadError = ""
            loading = false
            return
        }
        if (refreshInFlight) return
        refreshInFlight = true
        val generation = accountGeneration
        viewModelScope.launch {
            if (!silent) loading = true
            try {
                val newData = api.rpc("ctfly_snapshot", authenticated = true)
                if (generation == accountGeneration) {
                    if (
                        hasAccount && !acceptsRole(newData.optJSONObject("profile")?.text("role"))
                    ) {
                        api.localLogout()
                        accountGeneration++
                        hasAccount = false
                        data = JSONObject()
                        set("explore", "false")
                        set("dialog", "")
                        set("authMode", "login")
                        message = wrongAppMessage
                        return@launch
                    }
                    acceptSnapshot(newData)
                    loadError = ""
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == accountGeneration) {
                    loadError = e.message ?: "Unable to connect. Check your internet connection."
                    if (!api.signedIn && hasAccount) {
                        hasAccount = false
                        data = JSONObject()
                    }
                }
            } finally {
                loading = false
                refreshInFlight = false
            }
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                invalid(
                    if (
                        e is ApiFailure &&
                            e.code in listOf("invalid_credentials", "invalid_login_credentials")
                    )
                        "Email or password is incorrect."
                    else e.message ?: "Something went wrong. Please try again."
                )
            } finally {
                busy = false
            }
        }
    }

    fun authenticate() = run {
        val email = field("email").trim()
        val mode = field("authMode", "login")
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            "Enter a valid email address."
        }
        when (mode) {
            "login" -> {
                require(password.isNotBlank()) { "Enter your password." }
                api.login(email, password)
                finishLogin()
            }
            "register" -> {
                require(!BuildConfig.STAFF_APP) {
                    "Staff accounts are provisioned by the administrator."
                }
                require(field("name").trim().length >= 2) { "Enter your name." }
                require(adult(field("dob"))) { "You must be 18 or older. Choose your birth date." }
                require(password.length >= 8 && password == confirmPassword) {
                    "Use at least 8 characters and match both passwords."
                }
                if (api.signup(email, password, field("name").trim(), field("dob"), field("phone")))
                    finishLogin()
                else {
                    set("authMode", "verify")
                    set("verifyType", "signup")
                    message = "Check your email for your verification code."
                }
            }
            "forgot" -> {
                api.recover(email)
                set("authMode", "verify")
                set("verifyType", "recovery")
                message = "If an account exists, a recovery email has been sent."
            }
            "verify" -> {
                require(field("code").matches(Regex("[0-9]{6,10}"))) {
                    "Enter the code from your email."
                }
                api.verify(email, field("code"), field("verifyType", "signup"))
                if (field("verifyType") == "recovery") {
                    set("authMode", "reset")
                    password = ""
                    confirmPassword = ""
                } else finishLogin()
            }
            "reset" -> {
                require(password.length >= 8 && password == confirmPassword) {
                    "Use at least 8 characters and match both passwords."
                }
                api.newPassword(password)
                finishLogin()
            }
        }
    }

    private suspend fun finishLogin() {
        val fresh = api.rpc("ctfly_snapshot")
        val actualRole = fresh.optJSONObject("profile")?.text("role")
        if (!acceptsRole(actualRole)) {
            api.localLogout()
            throw IllegalArgumentException(wrongAppMessage)
        }
        accountGeneration++
        acceptSnapshot(fresh)
        hasAccount = true
        loadError = ""
        password = ""
        confirmPassword = ""
        set("authMode", "login")
        set("explore", "false")
        set("page", if (actualRole in listOf("staff", "admin")) "Operations" else "Discover")
    }

    fun resend() = run {
        if (field("verifyType") == "recovery") api.recover(field("email"))
        else api.resend(field("email"))
        message = "A new code was requested. Check your inbox and spam folder."
    }

    fun quotePayment() {
        quoteJob?.cancel()
        paymentQuote = JSONObject()
        val kind = field("paymentKind")
        val id = field("paymentId")
        val generation = accountGeneration
        quoteJob =
            viewModelScope.launch {
                try {
                    val quote = api.rpc("ctfly_quote", obj("p_kind" to kind, "p_id" to id))
                    if (
                        generation == accountGeneration &&
                            field("paymentKind") == kind &&
                            field("paymentId") == id
                    )
                        paymentQuote = quote
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    message = e.message ?: "Unable to load the payment amount. Close and try again."
                }
            }
    }

    fun logout() = run {
        try {
            api.logout()
        } finally {
            accountGeneration++
            checkoutJob?.cancel()
            checkoutQuote = JSONObject()
            checkoutError = ""
            hasAccount = false
            data = JSONObject()
            password = ""
            confirmPassword = ""
            listOf(
                    "cart",
                    "page",
                    "dialog",
                    "explore",
                    "code",
                    "formData",
                    "pendingBody",
                    "pendingAction",
                    "pendingRequest",
                    "profileName",
                    "profilePhone",
                    "delivery",
                    "receipt",
                    "scanToken",
                    "selectedBooking",
                    "checkoutSource",
                    "checkoutKind",
                    "checkoutPayment",
                    "checkoutResult",
                )
                .forEach { saved.remove<String>(it) }
            fieldVersion++
            set("authMode", "login")
            refresh()
        }
    }

    fun action(
        name: String,
        body: JSONObject = JSONObject(),
        onResult: (JSONObject) -> Unit = {},
        onSuccess: () -> Unit = {},
    ) {
        if (!hasAccount) {
            set("explore", "false")
            message = "Sign in to continue."
            return
        }
        run {
            validationError = ""
            // Keep the request ID on uncertain transport failures. Retrying the same action is
            // safe.
            val signature = body.toString()
            val request =
                if (field("pendingAction") == name && field("pendingBody") == signature)
                    field("pendingRequest")
                else
                    UUID.randomUUID().toString().also {
                        set("pendingAction", name)
                        set("pendingBody", signature)
                        set("pendingRequest", it)
                    }
            val result =
                api.rpc(
                    "ctfly_action",
                    obj("p_action" to name, "p_data" to body, "p_request" to request),
                )
            set("pendingAction", "")
            set("pendingBody", "")
            set("pendingRequest", "")
            message = if (name.startsWith("test_")) "" else result.text("message", "Saved")
            onSuccess()
            onResult(result)
            try {
                acceptSnapshot(api.rpc("ctfly_snapshot"))
                loadError = ""
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                loadError = "Saved, but refresh failed. Tap refresh to reload."
            }
        }
    }

    fun cart(): Map<String, Int> =
        runCatching {
                JSONObject(field("cart", "{}").ifBlank { "{}" }).let { j ->
                    j.keys().asSequence().associateWith { j.optInt(it) }
                }
            }
            .getOrDefault(emptyMap())

    fun cartChange(id: String, delta: Int) {
        val map = cart().toMutableMap()
        val stock = rows("products").find { it.text("id") == id }?.optInt("stock") ?: 0
        val qty = ((map[id] ?: 0) + delta).coerceIn(0, minOf(20, stock))
        if (qty == 0) map.remove(id) else map[id] = qty
        set("cart", JSONObject(map as Map<*, *>).toString())
    }

    fun checkout() =
        action(
            "order",
            obj(
                "items" to
                    JSONArray().apply {
                        cart().forEach { (id, qty) -> put(obj("id" to id.toLong(), "qty" to qty)) }
                    },
                "booking_id" to field("delivery"),
            ),
            onResult = {
                if (testPaymentsEnabled && it.text("id").isNotBlank())
                    openCheckout("Order", it.text("id"))
            },
        ) {
            set("cart", "{}")
            set("dialog", "")
            set("page", "Bookings")
            set("bookingTab", "Orders")
        }
}
