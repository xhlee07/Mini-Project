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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AppViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val rawApi = (application as CtflyApplication).gatewayFactory(application)
    val staffSession =
        (application as CtflyApplication).staffSessionFactory(application).apply {
            restore(rawApi.accountId, rawApi.signedIn)
        }
    val staffBiometrics = StaffBiometrics(application)
    private val api = StaffSessionGateway(rawApi, staffSession)
    var offerFingerprint by mutableStateOf(false)
        private set

    var biometricRequest by mutableStateOf<StaffBiometricRequest?>(null)
        private set

    var staffDevices by mutableStateOf<List<JSONObject>>(emptyList())
        private set

    var staffDevicesLoading by mutableStateOf(false)
        private set

    var staffDevicesLoaded by mutableStateOf(false)
        private set

    var staffDevicesError by mutableStateOf("")
        private set

    val staffLocked
        get() = BuildConfig.STAFF_APP && staffSession.locked

    val staffResuming
        get() = BuildConfig.STAFF_APP && hasAccount && !staffSession.foreground

    val restoringAccount
        get() = hasAccount && role.isBlank()

    var data by
        mutableStateOf(if (api.signedIn && !staffLocked) api.cachedSnapshot else JSONObject())
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
    var checkoutLeaving by mutableStateOf(false)
        private set

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
        set("checkoutCancelRequest", "")
        message = ""
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

    /** Unpaid café rows are checkout holds, not orders that have been placed. */
    fun confirmedCafeOrders(): List<JSONObject> {
        val settled =
            rows("test_payments")
                .filter {
                    it.text("kind") == "Order" &&
                        it.text("status") in listOf("Succeeded", "Refunded")
                }
                .map { it.text("source_id") }
                .toSet() +
                rows("bills")
                    .filter {
                        it.text("kind") == "Order" &&
                            it.text("status", "Paid") in listOf("Paid", "Void")
                    }
                    .map { it.text("source_id") }
        return rows("orders").filter { it.optBoolean("paid") || it.text("id") in settled }
    }

    fun leaveCheckout() {
        if (checkoutLeaving || field("page") != "Checkout") return
        val generation = accountGeneration
        val source = field("checkoutSource")
        val kind = field("checkoutKind")
        checkoutLeaving = true
        viewModelScope.launch {
            // Let a confirm/retry finish first: cancelling a coroutine cannot undo a server
            // payment.
            while (busy && generation == accountGeneration) delay(25)
            if (generation != accountGeneration || field("page") != "Checkout") {
                checkoutLeaving = false
                return@launch
            }
            this@AppViewModel.run {
                try {
                    if (crew || kind != "Order" || source.isBlank()) {
                        finishCheckoutExit(false)
                    } else {
                        val payment = currentTestPayment()
                        val order = rows("orders").find { it.text("id") == source }
                        if (
                            order?.optBoolean("paid") == true ||
                                payment.text("status") in listOf("Succeeded", "Refunded")
                        ) {
                            finishCheckoutExit(false)
                        } else if (order?.text("status") == "Cancelled") {
                            finishCheckoutExit(true)
                        } else {
                            val request =
                                field("checkoutCancelRequest").ifBlank {
                                    UUID.randomUUID().toString().also {
                                        set("checkoutCancelRequest", it)
                                    }
                                }
                            try {
                                api.rpc(
                                    "ctfly_action",
                                    obj(
                                        "p_action" to "cancel_order",
                                        "p_data" to obj("id" to source),
                                        "p_request" to request,
                                    ),
                                )
                                if (generation != accountGeneration) return@run
                                finishCheckoutExit(true)
                                refresh(silent = true)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                // A concurrent payment or expiry may have won the source-row lock.
                                val snapshot =
                                    try {
                                        api.rpc("ctfly_snapshot")
                                    } catch (refreshError: Exception) {
                                        if (refreshError is CancellationException)
                                            throw refreshError
                                        throw IllegalStateException(
                                            "Unable to confirm cancellation. Check your connection and try leaving again.",
                                            e,
                                        )
                                    }
                                if (generation != accountGeneration) return@run
                                acceptSnapshot(snapshot)
                                val latest = rows("orders").find { it.text("id") == source }
                                when {
                                    latest?.optBoolean("paid") == true ||
                                        currentTestPayment().text("status") in
                                            listOf("Succeeded", "Refunded") ->
                                        finishCheckoutExit(false)
                                    latest?.text("status") == "Cancelled" ->
                                        finishCheckoutExit(true)
                                    else ->
                                        throw IllegalStateException(
                                            "Unable to confirm cancellation. Check your connection and try leaving again.",
                                            e,
                                        )
                                }
                            }
                        }
                    }
                } finally {
                    checkoutLeaving = false
                }
            }
        }
    }

    private fun finishCheckoutExit(cancelled: Boolean) {
        checkoutJob?.cancel()
        checkoutQuote = JSONObject()
        checkoutError = ""
        val cafe = !crew && field("checkoutKind") == "Order"
        val destination = if (cafe) "Bookings" else field("checkoutReturn", "Bookings")
        if (cafe) set("bookingTab", "Orders")
        if (cancelled) message = "Order cancelled. Place a new order when you're ready."
        listOf(
                "checkoutSource",
                "checkoutKind",
                "checkoutPayment",
                "checkoutResult",
                "checkoutCancelRequest",
            )
            .forEach { set(it, "") }
        set("page", destination)
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

    fun startTestPayment() {
        if (checkoutLeaving) return
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
    }

    fun resolveTestPayment(outcome: String) {
        if (checkoutLeaving) return
        if (outcome == "Cancel" && field("checkoutKind") == "Order") {
            leaveCheckout()
            return
        }
        action(
            "test_resolve",
            obj("id" to field("checkoutPayment"), "outcome" to outcome),
            onResult = ::acceptTestResult,
        )
    }

    fun refundTestPayment() =
        action(
            "test_refund",
            obj("id" to field("checkoutPayment")),
            onResult = ::acceptTestResult,
        ) {
            set("dialog", "")
        }

    var hasAccount by mutableStateOf(api.signedIn && !staffLocked)
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
    private var pushRegistration = ""
    private var staffLockHandled = false
    private var operationJob: Job? = null
    private var operationSequence = 0

    fun openOrderFromNotification(owner: String, order: String) {
        if (owner.isBlank() || order.isBlank()) return
        set("notificationOwner", owner)
        set("notificationOrder", order)
        if (userId.isNotBlank()) routePickupNotification()
    }

    private fun routePickupNotification() {
        val owner = field("notificationOwner")
        val id = field("notificationOrder")
        if (owner.isBlank() || id.isBlank() || userId.isBlank()) return
        set("notificationOwner", "")
        set("notificationOrder", "")
        if (owner != userId || role != "customer") return
        val order = rows("orders").find { it.text("id") == id }
        if (order?.text("status") == "Ready" && order.optBoolean("paid")) openPickup(order)
        else openCafeOrders()
    }

    fun syncPickupDevice(language: String) {
        if (
            BuildConfig.STAFF_APP ||
                !BuildConfig.PUSH_CONFIGURED ||
                !hasAccount ||
                role != "customer"
        )
            return
        val owner = userId
        val generation = accountGeneration
        viewModelScope.launch {
            try {
                val token = PickupNotifications.token(getApplication()) ?: return@launch
                val signature = "$owner:$language:$token"
                if (generation != accountGeneration || signature == pushRegistration) return@launch
                api.rpc(
                    "ctfly_action",
                    obj(
                        "p_action" to "register_push_device",
                        "p_request" to UUID.randomUUID().toString(),
                        "p_data" to
                            obj(
                                "installation_id" to
                                    PickupNotifications.installationId(getApplication()),
                                "token" to token,
                                "language" to language,
                            ),
                    ),
                )
                if (generation == accountGeneration) pushRegistration = signature
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // In-app pickup information remains available if push setup/network is unavailable.
            }
        }
    }

    private fun acceptSnapshot(snapshot: JSONObject) {
        data = snapshot
        PickupNotifications.acceptSnapshot(getApplication(), snapshot)
        routePickupNotification()
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
        checkStaffSession()
        staffSession.heartbeat()
    }

    fun checkStaffSession() {
        if (!BuildConfig.STAFF_APP || !rawApi.signedIn) return
        if (staffSession.check() && !staffLockHandled) {
            staffLockHandled = true
            accountGeneration++
            operationSequence++
            operationJob?.cancel()
            busy = false
            hasAccount = false
            data = JSONObject()
            offerFingerprint = false
            if (biometricRequest?.enabling == true) biometricRequest = null
            validationError = ""
            password = ""
            confirmPassword = ""
            loadError = ""
            message = ""
            saved.keys().forEach { saved.remove<Any?>(it) }
            fieldVersion++
            set("authMode", "login")
            quoteJob?.cancel()
            checkoutJob?.cancel()
            paymentQuote = JSONObject()
            checkoutQuote = JSONObject()
            checkoutError = ""
        }
        if (!staffSession.locked) staffLockHandled = false
    }

    fun staffForeground() {
        if (!BuildConfig.STAFF_APP) return
        checkStaffSession()
        staffSession.resume()
    }

    fun staffBackground() {
        if (BuildConfig.STAFF_APP) staffSession.background()
    }

    fun staffInteraction() {
        checkStaffSession()
        staffSession.interaction()
    }

    fun declineFingerprint() {
        staffBiometrics.decline(userId)
        offerFingerprint = false
        validationError = ""
    }

    fun disableFingerprint() {
        staffBiometrics.disable()
        biometricRequest = null
        fieldVersion++
    }

    fun showFingerprintSetup() {
        if (!hasAccount || staffLocked || busy) return
        set("deviceStaffName", "")
        offerFingerprint = true
    }

    private fun acceptStaffDevices(result: JSONObject) {
        staffDevices = result.rows("devices")
        staffDevicesLoaded = true
        staffDevicesError = ""
        if (
            staffBiometrics.enabled(userId) &&
                staffDevices.none { it.text("id") == staffBiometrics.deviceId }
        )
            disableFingerprint()
    }

    fun loadStaffDevices() {
        if (!BuildConfig.STAFF_APP || !hasAccount || staffLocked || staffDevicesLoading) return
        staffDevicesLoading = true
        val generation = accountGeneration
        viewModelScope.launch {
            try {
                val result = api.staffDevice(obj("operation" to "list"))
                if (generation == accountGeneration && hasAccount) acceptStaffDevices(result)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == accountGeneration)
                    staffDevicesError = e.message ?: "Unable to load registered devices."
            } finally {
                if (generation == accountGeneration) staffDevicesLoading = false
            }
        }
    }

    fun removeStaffDevice(id: String) = run {
        require(hasAccount && !staffLocked) { "Please sign in first." }
        val result = api.staffDevice(obj("operation" to "remove", "device_id" to id))
        if (id == staffBiometrics.deviceId) disableFingerprint()
        acceptStaffDevices(result)
    }

    fun requestFingerprint(enabling: Boolean = false) {
        if (busy || biometricRequest != null || !BuildConfig.STAFF_APP) return
        checkStaffSession()
        if (enabling && (!hasAccount || staffLocked)) return
        val owner = if (enabling) userId else staffBiometrics.owner
        if (
            !enabling &&
                field("email").isNotBlank() &&
                !field("email").trim().equals(staffBiometrics.email, ignoreCase = true)
        ) {
            invalid("For a different account, sign in with email and password.")
            return
        }
        if (!enabling && !staffBiometrics.enabled(owner)) {
            invalid(
                "Sign in with your email and password once to enable fingerprint on this phone."
            )
            return
        }
        val name = field("deviceStaffName").trim()
        if (enabling && name.length !in 2..60) {
            invalid("Enter a staff name between 2 and 60 characters.")
            return
        }
        val generation = accountGeneration
        run {
            val challenge =
                if (enabling) {
                    val result = api.staffDevice(obj("operation" to "list"))
                    acceptStaffDevices(result)
                    require(
                        staffDevices.size < 5 ||
                            staffDevices.any { it.text("id") == staffBiometrics.deviceId }
                    ) {
                        "Five devices are already registered. Remove a device before adding another."
                    }
                    JSONObject()
                } else
                    rawApi.staffDevice(
                        obj("operation" to "challenge", "device_id" to staffBiometrics.deviceId),
                        false,
                    )
            if (generation != accountGeneration) return@run
            try {
                biometricRequest =
                    staffBiometrics.begin(owner, enabling, name, challenge, generation)
                offerFingerprint = false
            } catch (_: Exception) {
                disableFingerprint()
                invalid(
                    "Fingerprint settings have changed or are unavailable. Use your staff account password."
                )
            }
        }
    }

    fun cancelFingerprint() {
        biometricRequest = null
    }

    fun completeFingerprint(signature: java.security.Signature?) {
        checkStaffSession()
        val request = biometricRequest ?: return
        biometricRequest = null
        if (
            request.generation != accountGeneration ||
                (request.enabling && (!hasAccount || staffLocked || userId != request.owner))
        )
            return
        val proof =
            try {
                check(signature != null)
                staffBiometrics.sign(request, signature)
            } catch (_: Exception) {
                disableFingerprint()
                invalid("Fingerprint could not be verified. Use your staff account password.")
                return
            }
        run {
            try {
                if (request.enabling) {
                    val email = rawApi.accountEmail
                    val result =
                        api.staffDevice(
                            obj(
                                "operation" to "register",
                                "device_id" to request.deviceId,
                                "staff_name" to request.staffName,
                                "device_name" to
                                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                                        .take(100),
                                "public_key" to request.publicKey,
                                "signature" to proof,
                            )
                        )
                    staffBiometrics.remember(request, email)
                    acceptStaffDevices(result)
                    set("deviceStaffName", "")
                } else {
                    rawApi.loginDevice(
                        obj(
                            "operation" to "login",
                            "device_id" to request.deviceId,
                            "challenge_id" to request.challengeId,
                            "signature" to proof,
                        )
                    )
                    if (rawApi.accountId != request.owner) {
                        rawApi.localLogout()
                        error("Staff account is unavailable.")
                    }
                    finishLogin(passwordVerified = false)
                    validationError = ""
                }
                fieldVersion++
            } catch (e: Exception) {
                if (e is ApiFailure && e.code == "device_unavailable") disableFingerprint()
                throw e
            }
        }
    }

    private fun staffPasswordVerified(owner: String) {
        staffSession.verified(owner)
        staffLockHandled = false
        if (!staffBiometrics.enabled(owner)) staffBiometrics.disable()
        offerFingerprint = staffBiometrics.available() && !staffBiometrics.offered(owner)
    }

    fun rows(key: String) = data.rows(key)

    suspend fun staffHistory(query: JSONObject): JSONObject {
        check(crew) { "Staff access required." }
        val owner = userId
        val transactions = query.text("p_module") == "Transactions"
        val arguments =
            JSONObject(query.toString()).apply {
                if (transactions) {
                    remove("p_module")
                    put("p_status", remove("p_category"))
                }
            }
        val result =
            api.rpc(
                if (transactions) "ctfly_staff_transactions" else "ctfly_staff_history",
                arguments,
            )
        if (owner != userId || !crew) throw CancellationException("Account changed")
        return result
    }

    suspend fun publishVirtualVisit(
        session: String,
        sequence: Long,
        pose: TourPose,
        kart: Boolean,
        moving: Boolean,
        leaving: Boolean = false,
    ) {
        check(!BuildConfig.STAFF_APP && hasAccount && role == "customer") {
            "Customer access required."
        }
        require(VirtualCctv.valid(pose)) { "Invalid virtual position." }
        val owner = userId
        api.rpc(
            "ctfly_tour_presence",
            obj(
                "p_session" to session,
                "p_sequence" to sequence,
                "p_area" to pose.area.name,
                "p_x" to pose.x,
                "p_z" to pose.z,
                "p_yaw" to VirtualCctv.normalizedYaw(pose.yaw),
                "p_mode" to if (kart) "kart" else "walk",
                "p_moving" to moving,
                "p_leaving" to leaving,
            ),
        )
        if (owner != userId) throw CancellationException("Account changed")
    }

    suspend fun virtualCameraStates(): Pair<List<VirtualCameraState>, Long> {
        val result = api.rpc("ctfly_tour_cameras")
        val states =
            result.rows("cameras").mapNotNull { row ->
                val index = row.optInt("camera_id", -1)
                val yaw = row.optDouble("yaw", Double.NaN).toFloat()
                val pitch = row.optDouble("pitch", Double.NaN).toFloat()
                if (index !in VirtualCctv.cameras.indices || !yaw.isFinite() || !pitch.isFinite())
                    null
                else
                    VirtualCameraState(
                        index,
                        yaw,
                        pitch,
                        row.optLong("remaining_ms", 0).coerceIn(0, 15_000),
                    )
            }
        return states to result.optLong("server_ms", 0)
    }

    suspend fun turnVirtualCamera(direction: VirtualCameraState) {
        check(crew && !staffLocked) { "Staff access required." }
        api.rpc(
            "ctfly_tour_turn_camera",
            obj(
                "p_camera" to direction.index,
                "p_yaw" to direction.yaw,
                "p_pitch" to direction.pitch,
                "p_hold_ms" to direction.remainingMillis,
            ),
        )
    }

    suspend fun virtualVisitors(): List<VirtualVisitor> {
        check(crew && !staffLocked) { "Staff access required." }
        val owner = userId
        val result = api.rpc("ctfly_tour_watch")
        if (owner != userId || !crew || staffLocked) throw CancellationException("Account changed")
        return result.rows("visitors").mapNotNull { row ->
            val area =
                TourArea.entries.firstOrNull { it.name == row.text("area") }
                    ?: return@mapNotNull null
            val pose =
                TourPose(
                    row.optDouble("x", Double.NaN).toFloat(),
                    row.optDouble("z", Double.NaN).toFloat(),
                    row.optDouble("yaw", Double.NaN).toFloat(),
                    area = area,
                )
            if (!VirtualCctv.valid(pose) || row.text("id").isBlank()) return@mapNotNull null
            VirtualVisitor(
                row.text("id"),
                pose,
                row.text("mode") == "kart",
                row.optBoolean("moving"),
                row.optLong("age_ms", VirtualCctv.TTL_MS).coerceAtLeast(0),
            )
        }
    }

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
        checkStaffSession()
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        checkStaffSession()
        if (staffLocked) {
            loading = false
            return
        }
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
        val operation = ++operationSequence
        operationJob =
            viewModelScope.launch {
                try {
                    block()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    checkStaffSession()
                    if (operation != operationSequence) return@launch
                    if (!api.signedIn && hasAccount) {
                        accountGeneration++
                        hasAccount = false
                        data = JSONObject()
                        staffSession.clear()
                        set("dialog", "")
                        set("authMode", "login")
                    }
                    invalid(
                        if (
                            e is ApiFailure &&
                                e.code in listOf("invalid_credentials", "invalid_login_credentials")
                        )
                            "Email or password is incorrect."
                        else e.message ?: "Something went wrong. Please try again."
                    )
                } finally {
                    if (operation == operationSequence) busy = false
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

    private suspend fun finishLogin(passwordVerified: Boolean = true) {
        val fresh = rawApi.rpc("ctfly_snapshot")
        val actualRole = fresh.optJSONObject("profile")?.text("role")
        if (!acceptsRole(actualRole)) {
            api.localLogout()
            throw IllegalArgumentException(wrongAppMessage)
        }
        accountGeneration++
        val owner = fresh.optJSONObject("profile")?.text("id").orEmpty()
        staffDevices = emptyList()
        staffDevicesLoaded = false
        staffDevicesLoading = false
        staffDevicesError = ""
        if (BuildConfig.STAFF_APP) {
            if (passwordVerified) staffPasswordVerified(owner)
            else {
                staffSession.verified(owner)
                staffLockHandled = false
                offerFingerprint = false
            }
        }
        acceptSnapshot(fresh)
        hasAccount = true
        loadError = ""
        password = ""
        confirmPassword = ""
        set("authMode", "login")
        if (BuildConfig.STAFF_APP) set("email", "")
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
            if (BuildConfig.PUSH_CONFIGURED && pushRegistration.isNotBlank()) {
                runCatching {
                    api.rpc(
                        "ctfly_action",
                        obj(
                            "p_action" to "unregister_push_device",
                            "p_request" to UUID.randomUUID().toString(),
                            "p_data" to
                                obj(
                                    "installation_id" to
                                        PickupNotifications.installationId(getApplication())
                                ),
                        ),
                    )
                }
            }
            api.logout()
        } finally {
            staffSession.clear()
            biometricRequest = null
            offerFingerprint = false
            staffDevices = emptyList()
            staffDevicesLoaded = false
            staffDevicesLoading = false
            staffDevicesError = ""
            pushRegistration = ""
            getApplication<Application>()
                .getSystemService(android.app.NotificationManager::class.java)
                .cancelAll()
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
                    "email",
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
                    "selectedOrder",
                    "pickupScanToken",
                    "pickupPreview",
                    "notificationOwner",
                    "notificationOrder",
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
            message =
                if (name.startsWith("test_") || name == "order") ""
                else result.text("message", "Saved")
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

    fun checkout() {
        if (!testPaymentsEnabled) {
            invalid("Checkout is unavailable. Refresh and try again.")
            return
        }
        action(
            "order",
            obj(
                "items" to
                    JSONArray().apply {
                        cart().forEach { (id, qty) -> put(obj("id" to id.toLong(), "qty" to qty)) }
                    },
                "booking_id" to "",
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
}
