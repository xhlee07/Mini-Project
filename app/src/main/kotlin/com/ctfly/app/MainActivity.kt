package com.ctfly.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : FragmentActivity() {
    private val vm
        get() = ViewModelProvider(this)[AppViewModel::class.java]

    private lateinit var fingerprintPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reattach the callback after rotation; the system owns the ongoing biometric dialog.
        fingerprintPrompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        vm.completeFingerprint(result.cryptoObject?.signature)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        vm.cancelFingerprint()
                        if (
                            errorCode !in
                                listOf(
                                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                    BiometricPrompt.ERROR_USER_CANCELED,
                                    BiometricPrompt.ERROR_CANCELED,
                                )
                        )
                            vm.invalid(
                                "Fingerprint is unavailable. Use your staff account password."
                            )
                    }
                },
            )
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val state: AppViewModel = viewModel()
            val request = state.biometricRequest
            LaunchedEffect(request) {
                if (request == null) fingerprintPrompt.cancelAuthentication()
                if (request != null && !request.started) {
                    request.started = true
                    try {
                        fingerprintPrompt.authenticate(
                            BiometricPrompt.PromptInfo.Builder()
                                .setTitle(
                                    if (request.enabling) "Enable staff fingerprint sign-in"
                                    else "Sign in to Staff CTFLY"
                                )
                                .setSubtitle("Verify using this phone's enrolled biometrics")
                                .setAllowedAuthenticators(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                                )
                                .setNegativeButtonText("Use account password")
                                .build(),
                            BiometricPrompt.CryptoObject(request.signature),
                        )
                    } catch (_: Exception) {
                        state.cancelFingerprint()
                    }
                }
            }
            CtflyTheme { CtflyApp(state) }
        }
        handlePickupIntent(intent)
    }

    override fun onResume() {
        // Resolve access synchronously before the resumed window draws its first frame.
        vm.staffForeground()
        super.onResume()
        if (!BuildConfig.STAFF_APP && BuildConfig.PUSH_CONFIGURED) {
            ViewModelProvider(this)[AppViewModel::class.java].syncPickupDevice(
                AppPreferences(this).language
            )
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        vm.staffInteraction()
    }

    override fun onStop() {
        if (!isChangingConfigurations) vm.staffBackground()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePickupIntent(intent)
    }

    private fun handlePickupIntent(intent: Intent?) {
        if (BuildConfig.STAFF_APP) return
        val owner = intent?.getStringExtra("pickup_user").orEmpty()
        val order = intent?.getStringExtra("pickup_order").orEmpty()
        if (owner.isNotBlank() && order.isNotBlank()) {
            val vm = ViewModelProvider(this)[AppViewModel::class.java]
            vm.openOrderFromNotification(owner, order)
            vm.refresh(silent = true)
            intent?.removeExtra("pickup_user")
            intent?.removeExtra("pickup_order")
        }
    }
}
