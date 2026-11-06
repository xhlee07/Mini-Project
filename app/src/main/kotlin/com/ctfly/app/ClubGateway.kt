package com.ctfly.app

import android.app.Application
import android.content.Context
import org.json.JSONObject

/** Boundary between state/UI and the cloud; tests supply an isolated gateway. */
interface ClubGateway {
    val signedIn: Boolean
    val cachedSnapshot: JSONObject
        get() = JSONObject()

    val accountId: String
        get() = cachedSnapshot.optJSONObject("profile")?.text("id").orEmpty()

    val accountEmail: String
        get() = cachedSnapshot.optJSONObject("profile")?.text("email").orEmpty()

    suspend fun service(body: JSONObject): JSONObject =
        throw IllegalStateException("Receipt service is not available yet.")

    suspend fun uploadPoster(bytes: ByteArray): String =
        throw IllegalStateException("Image upload is not available yet.")

    suspend fun rpc(
        name: String,
        body: JSONObject = JSONObject(),
        authenticated: Boolean = true,
    ): JSONObject

    suspend fun login(email: String, password: String)

    suspend fun staffDevice(body: JSONObject, authenticated: Boolean = true): JSONObject =
        throw IllegalStateException("Device sign-in is not available yet.")

    suspend fun loginDevice(body: JSONObject) {
        throw IllegalStateException("Device sign-in is not available yet.")
    }

    suspend fun signup(
        email: String,
        password: String,
        name: String,
        dob: String,
        phone: String,
    ): Boolean

    suspend fun recover(email: String)

    suspend fun resend(email: String)

    suspend fun verify(email: String, code: String, type: String)

    suspend fun newPassword(password: String)

    suspend fun logout()

    fun localLogout()
}

class CtflyApplication : Application() {
    var gatewayFactory: (Context) -> ClubGateway = { CtflyApi(it) }
    var staffSessionFactory: (Context) -> StaffSession = { StaffSession(it) }

    override fun onCreate() {
        super.onCreate()
        PickupNotifications.createChannel(this)
    }
}
