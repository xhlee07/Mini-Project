package com.ctfly.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant

@Composable
fun TestModeNotice() {
    SurfaceCard {
        AppText("TEST MODE · NO MONEY CHARGED", color = Lime, fontWeight = FontWeight.Bold)
        AppText(
            "TNG, online banking and cards are simulated. Do not enter real payment details.",
            color = Muted,
        )
    }
}

@Composable
fun TestPayButton(vm: AppViewModel, kind: String, id: String) {
    if (vm.testPaymentsEnabled && !vm.crew)
        Button(onClick = { vm.openCheckout(kind, id) }, enabled = !vm.busy) {
            AppText("Pay in test mode")
        }
}

@Composable
fun HoldCountdown(vm: AppViewModel, deadline: String) {
    val seconds =
        runCatching { (Instant.parse(deadline).toEpochMilli() - vm.nowMillis) / 1000 }
            .getOrDefault(0)
            .coerceAtLeast(0)
    AppText(
        tr("Checkout hold: %s").format("%02d:%02d".format(seconds / 60, seconds % 60)),
        color = if (seconds == 0L) Orange else Muted,
        localize = false,
    )
}

@Composable
fun CheckoutScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val pay = vm.currentTestPayment()
    val status = pay.text("status")
    val q = if (pay.text("id").isBlank()) vm.checkoutQuote else pay
    val expired =
        q.text("expires_at").isNotBlank() &&
            runCatching { Instant.parse(q.text("expires_at")).toEpochMilli() <= vm.nowMillis }
                .getOrDefault(true)
    LaunchedEffect(expired) { if (expired) vm.refresh(silent = true) }
    LaunchedEffect(vm.field("checkoutSource")) {
        if (pay.text("id").isBlank() && vm.checkoutQuote.length() == 0) vm.reloadCheckout()
    }
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            AppText("Test checkout", style = MaterialTheme.typography.titleLarge)
        }
        if (vm.message.isNotBlank()) {
            Surface(color = Orange.copy(alpha = .12f)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(vm.message, Modifier.weight(1f), color = Orange)
                    IconButton(onClick = { vm.dismissMessage() }) {
                        AppIcon(Icons.Outlined.Close, "Close")
                    }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f)
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .testTag("checkout-list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TestModeNotice() }
            if (vm.crew) item { AppText(pay.text("customer_name"), fontWeight = FontWeight.Bold) }
            if (q.length() > 0)
                item {
                    SurfaceCard {
                        Chip(
                            if (status.isBlank()) "Awaiting payment" else status,
                            if (status == "Succeeded") Lime else Orange,
                        )
                        q.rows("items").forEach {
                            AppText("${it.optInt("qty")} × ${it.text("name")}")
                        }
                        AmountLine("Subtotal", q.optInt("subtotal"))
                        AmountLine(
                            if (q.text("promo_code").isBlank()) "Member discount"
                            else "Promo discount",
                            -q.optInt("discount"),
                        )
                        HorizontalDivider()
                        AmountLine("Total", q.optInt("total"))
                        AppText(
                            tr("Demo points: %s").format(q.optInt("points").toString()),
                            color = Lime,
                            localize = false,
                        )
                        if (status.isBlank() || status in listOf("Pending", "Processing"))
                            HoldCountdown(vm, q.text("expires_at"))
                        if (pay.text("reference").isNotBlank())
                            AppText(pay.text("reference"), color = Muted, localize = false)
                    }
                }
            if (status.isBlank()) {
                if (!vm.crew)
                    item {
                        SurfaceCard {
                            Input(
                                "Promo code (optional)",
                                vm.field("checkoutPromoCode"),
                                { vm.set("checkoutPromoCode", it.uppercase()) },
                            )
                            OutlinedButton(
                                onClick = {
                                    vm.set(
                                        "checkoutAppliedCode",
                                        vm.field("checkoutPromoCode").trim(),
                                    )
                                    vm.reloadCheckout()
                                },
                                enabled = !vm.busy,
                            ) {
                                AppText("Apply / remove code")
                            }
                            if (q.text("promo_code").isNotBlank())
                                AppText(
                                    tr("Applied: {0}").replace("{0}", q.text("promo_code")),
                                    color = Lime,
                                    localize = false,
                                )
                        }
                    }

                if (vm.checkoutError.isNotBlank())
                    item {
                        SurfaceCard {
                            AppText(vm.checkoutError, color = Orange)
                            Button(onClick = { vm.reloadCheckout() }) { AppText("Reload checkout") }
                        }
                    }
                else if (q.length() == 0)
                    item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime) }
                else
                    item {
                        SurfaceCard {
                            AppText("Choose a test payment method", fontWeight = FontWeight.Bold)
                            listOf(
                                    "TNG" to "Touch ’n Go eWallet · test",
                                    "FPX" to "Online banking (FPX) · test",
                                    "Card" to "Debit / credit card · test",
                                )
                                .forEach { (key, label) ->
                                    OutlinedButton(
                                        onClick = { vm.set("checkoutMethod", key) },
                                        enabled = !vm.busy,
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .heightIn(min = 56.dp)
                                                .testTag("method-$key"),
                                    ) {
                                        if (vm.field("checkoutMethod", "TNG") == key)
                                            AppIcon(Icons.Outlined.CheckCircle, null)
                                        AppText(label, Modifier.padding(start = 8.dp))
                                    }
                                }
                            Button(
                                onClick = { vm.startTestPayment() },
                                enabled = !vm.busy && !expired,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .testTag("start-test-payment"),
                            ) {
                                AppText("Continue to test payment")
                            }
                            if (expired) AppText("Checkout hold expired", color = Orange)
                        }
                    }
            } else if (status in listOf("Pending", "Processing") && !vm.crew)
                item {
                    SurfaceCard {
                        AppText(
                            tr("%s simulator").format(pay.text("method")),
                            fontWeight = FontWeight.Bold,
                            localize = false,
                        )
                        AppText(
                            "Confirm a simulated payment. Your wallet, bank and card will not be charged.",
                            color = Muted,
                        )
                        if (status == "Processing")
                            AppText(
                                "Still processing in the simulator. Complete or cancel this demo below.",
                                color = Orange,
                            )
                        Button(
                            onClick = { vm.resolveTestPayment("Success") },
                            enabled = !vm.busy,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("confirm-test-payment"),
                        ) {
                            AppText("Confirm test payment")
                        }
                        var showDemo by remember { mutableStateOf(false) }
                        TextButton(onClick = { showDemo = !showDemo }) { AppText("Demo controls") }
                        if (showDemo) {
                            OutlinedButton(
                                onClick = { vm.resolveTestPayment("Failure") },
                                enabled = !vm.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AppText("Simulate failure")
                            }
                            OutlinedButton(
                                onClick = { vm.resolveTestPayment("Pending") },
                                enabled = !vm.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AppText("Simulate processing")
                            }
                        }
                        TextButton(
                            onClick = { vm.resolveTestPayment("Cancel") },
                            enabled = !vm.busy,
                        ) {
                            AppText("Cancel test payment")
                        }
                    }
                }
            else if (status == "Succeeded")
                item {
                    SurfaceCard {
                        AppIcon(Icons.Outlined.CheckCircle, null, tint = Lime)
                        AppText(
                            "Test payment successful",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        AppText(
                            "No money was charged. Your demo order is confirmed.",
                            color = Muted,
                        )
                        val receipt =
                            pay.optJSONObject("receipt")
                                ?: vm.rows("bills").find { it.text("id") == pay.text("bill_id") }
                        if (receipt != null)
                            Button(
                                onClick = {
                                    vm.set("receipt", receipt.toString())
                                    vm.set("dialog", "receipt")
                                }
                            ) {
                                AppText("View / share receipt")
                            }
                        if (vm.canRefundTestPayment(pay))
                            OutlinedButton(
                                onClick = { vm.set("dialog", "testRefund") },
                                enabled = !vm.busy,
                            ) {
                                AppText("Request test refund")
                            }
                    }
                }
            else
                item {
                    SurfaceCard {
                        AppText(
                            when (status) {
                                "Refunded" -> "Test refund completed"
                                "Failed" -> "Test payment failed"
                                "Expired" -> "Checkout hold expired"
                                else -> "Test payment cancelled"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        AppText("No real money moved.", color = Muted)
                        if (status in listOf("Failed", "Cancelled") && !vm.crew)
                            Button(
                                onClick = {
                                    vm.openCheckout(pay.text("kind"), pay.text("source_id"))
                                },
                                enabled = !vm.busy,
                            ) {
                                AppText("Retry test checkout")
                            }
                    }
                }
            item {
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime)
                TextButton(onClick = onBack) { AppText("Back to your plans") }
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, color = Muted)
        AppText(money(value), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TransactionsScreen(vm: AppViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppText("Test transactions", style = MaterialTheme.typography.headlineMedium)
                TestModeNotice()
            }
        }
        items(vm.rows("test_payments"), key = { it.text("id") }) { p ->
            SurfaceCard {
                Chip(p.text("status"), if (p.text("status") == "Succeeded") Lime else Orange)
                if (vm.crew) {
                    AppText(p.text("customer_name"), fontWeight = FontWeight.Bold)
                }
                AppText("${p.text("kind")} · ${money(p.optInt("total"))} · ${p.text("method")}")
                AppText(p.text("reference"), color = Muted, localize = false)
                AppText(dateLabel(p.text("created_at")), color = Muted)
                TextButton(onClick = { vm.resumeCheckout(p) }) {
                    AppText(
                        if (p.text("status") in listOf("Pending", "Processing") && !vm.crew)
                            "Resume test payment"
                        else "Payment details"
                    )
                }
            }
        }
        if (vm.rows("test_payments").isEmpty())
            item { EmptyState("No test transactions yet", "Test payments will appear here.") }
    }
}
