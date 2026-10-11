@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ctfly.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import org.json.JSONObject

@Composable
fun DialogFrame(title: String, vm: AppViewModel, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = { if (!vm.busy) vm.set("dialog", "") },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogHeight =
            minOf(
                740.dp,
                (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 100)
                    .coerceAtLeast(140)
                    .dp,
            )
        Surface(
            Modifier.widthIn(max = 600.dp)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(16.dp)
                .imePadding(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = Panel,
        ) {
            Column(Modifier.heightIn(max = dialogHeight)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.set("dialog", "") }, enabled = !vm.busy) {
                        AppIcon(Icons.Outlined.Close, "Close dialog")
                    }
                }
                if (vm.validationError.isNotBlank())
                    Surface(
                        modifier =
                            Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                        color = Orange.copy(alpha = .12f),
                    ) {
                        AppText(
                            vm.validationError,
                            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                            color = Orange,
                            fontSize = 13.sp,
                        )
                    }
                Column(
                    Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    content()
                    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun AppDialogs(vm: AppViewModel) {
    val dialog = vm.field("dialog")
    val context = LocalContext.current
    when (dialog) {
        "book" ->
            DialogFrame("Make it your time.", vm) {
                val r = vm.rows("resources").find { it.text("id") == vm.field("selectedResource") }
                if (r != null) {
                    AppText(r.text("name"), fontWeight = FontWeight.Bold)
                    AppText(r.text("description"), color = Muted)
                    val initial = run {
                        java.time.Instant.ofEpochMilli(vm.nowMillis)
                            .atZone(CafeZone)
                            .withSecond(0)
                            .withNano(0)
                    }
                    val date = vm.field("bookDate", initial.toLocalDate().toString())
                    val time = vm.field("bookTime", initial.toLocalTime().toString())
                    val durations = VenueDirectory.durations(r.text("kind"))
                    val defaultMinutes = 30
                    val mins =
                        vm.field("bookMinutes", defaultMinutes.toString())
                            .toIntOrNull()
                            .let { if (it in durations) it!! else defaultMinutes }
                            .toString()
                    val guests =
                        vm.field("bookGuests", vm.field("roomPlayers", "1"))
                            .toIntOrNull()
                            ?.coerceIn(1, r.optInt("capacity")) ?: 1
                    DateFields("Reservation date · Malaysia time", date, { vm.set("bookDate", it) })
                    TimeFields("Start time", time, { vm.set("bookTime", it) })
                    Select(
                        "Duration",
                        mins,
                        durations.map { it.toString() to "$it minutes" },
                        { vm.set("bookMinutes", it) },
                    )
                    Select(
                        "Players",
                        guests.toString(),
                        (1..r.optInt("capacity")).map { it.toString() to "$it player(s)" },
                        { vm.set("bookGuests", it) },
                    )
                    AppText(
                        money(bookingPrice(r.optInt("rate"), mins.toInt())),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Lime,
                    )
                    if (r.text("kind") == "VIPRoom")
                        AppText(
                            "Whole private room · ${r.optInt("capacity")} gaming PCs · $guests guest(s). The room price stays the same for fewer guests.",
                            color = Aqua,
                            fontSize = 12.sp,
                        )
                    AppText(
                        "Complete test checkout within 10 minutes. No real money will be charged. Arriving late does not extend your end time.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Button(
                        onClick = {
                            val error =
                                bookingInputError(
                                    date,
                                    time,
                                    mins.toInt(),
                                    guests,
                                    r.optInt("capacity"),
                                    vm.nowMillis,
                                )
                            if (error != null) {
                                vm.invalid(error)
                                return@Button
                            }
                            val start =
                                LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                                    .atZone(CafeZone)
                                    .toInstant()
                                    .toString()
                            vm.action(
                                "book",
                                obj(
                                    "resource_id" to r.optLong("id"),
                                    "starts_at" to start,
                                    "minutes" to mins.toInt(),
                                    "guests" to guests,
                                ),
                                onResult = {
                                    if (vm.testPaymentsEnabled && it.text("id").isNotBlank())
                                        vm.openCheckout("Booking", it.text("id"))
                                },
                            ) {
                                vm.set("dialog", "")
                                vm.set("page", "Bookings")
                                vm.set("bookingTab", "Spaces")
                            }
                        },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText("Confirm reservation")
                    }
                }
            }
        "cart" ->
            DialogFrame("Your pit stop order", vm) {
                val cart = vm.cart()
                var total = 0
                cart.forEach { (id, qty) ->
                    val p = vm.rows("products").find { it.text("id") == id }
                    if (p != null) {
                        total += p.optInt("price") * qty
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                AppText(p.text("name"), fontWeight = FontWeight.Bold)
                                AppText(money(p.optInt("price") * qty), color = Orange)
                            }
                            TextButton(onClick = { vm.cartChange(id, -1) }, enabled = !vm.busy) {
                                AppText("−")
                            }
                            AppText(qty.toString())
                            TextButton(onClick = { vm.cartChange(id, 1) }, enabled = !vm.busy) {
                                AppText("+")
                            }
                        }
                    }
                }
                if (cart.isEmpty())
                    EmptyState("Your bag is empty", "Add something tasty from Café.")
                else {
                    val options =
                        listOf("" to "Counter pickup") +
                            vm.rows("bookings")
                                .filter { it.text("status") == "Active" }
                                .map { b ->
                                    b.text("id") to
                                        (vm.rows("resources")
                                            .find { it.text("id") == b.text("resource_id") }
                                            ?.text("name") ?: "My active space")
                                }
                    Select("Delivery", vm.field("delivery"), options, { vm.set("delivery", it) })
                    AppText("Total ${money(total)}", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    AppText(
                        "Complete test checkout within 10 minutes. Unpaid orders will not be prepared.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Button(
                        onClick = { vm.checkout() },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText("Place order")
                    }
                }
            }
        "event" ->
            DialogFrame("Enter the tournament", vm) {
                val e = vm.rows("events").find { it.text("id") == vm.field("selectedEvent") }
                AppText(e?.text("name") ?: "Tournament", fontWeight = FontWeight.Bold)
                Input("Team / player name", vm.field("teamName"), { vm.set("teamName", it) })
                AppText(
                    "Entry fee payable at the counter. Registration closes when the bracket is generated.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = {
                        vm.action(
                            "register_event",
                            obj(
                                "event_id" to vm.field("selectedEvent"),
                                "team" to vm.field("teamName"),
                            ),
                            onResult = {
                                if (vm.testPaymentsEnabled && it.text("id").isNotBlank())
                                    vm.openCheckout("Event", it.text("id"))
                            },
                        ) {
                            vm.set("dialog", "")
                        }
                    },
                    enabled = !vm.busy && vm.field("teamName").isNotBlank(),
                ) {
                    AppText("Register team")
                }
            }
        "qr" ->
            DialogFrame("Your entry pass", vm) {
                val b = vm.rows("bookings").find { it.text("id") == vm.field("selectedBooking") }
                if (b != null && b.optBoolean("paid") && b.text("status") == "Confirmed") {
                    val token = "ctfly:${b.text("token")}"
                    val bitmap =
                        remember(token) {
                            val matrix =
                                QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 640, 640)
                            Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
                                .apply {
                                    setPixels(
                                        IntArray(640 * 640) { i ->
                                            if (matrix[i % 640, i / 640])
                                                android.graphics.Color.BLACK
                                            else android.graphics.Color.WHITE
                                        },
                                        0,
                                        640,
                                        0,
                                        0,
                                        640,
                                        640,
                                    )
                                }
                                .asImageBitmap()
                        }
                    Image(bitmap, "Entry QR code", Modifier.fillMaxWidth().aspectRatio(1f))
                    AppText(
                        "Show this to the crew when your session begins. This code can only be checked in once.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        AppText(token, fontSize = 11.sp)
                    }
                } else
                    AppText(
                        "This pass is no longer available. Refresh your bookings.",
                        color = Orange,
                    )
            }
        "scan" ->
            DialogFrame("Check in a player", vm) {
                Input("Entry code / scanned QR", vm.field("scanToken"), { vm.set("scanToken", it) })
                AppText(
                    "Payment, booking time and prior use are verified by the server.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = {
                        vm.action("check_in", obj("token" to vm.field("scanToken"))) {
                            vm.set("dialog", "")
                            vm.set("scanToken", "")
                        }
                    },
                    enabled = !vm.busy,
                ) {
                    AppText("Verify & check in")
                }
            }
        "profile" ->
            DialogFrame("Make it yours", vm) {
                Input(
                    "Name",
                    vm.field("profileName", vm.profile.text("name")),
                    { vm.set("profileName", it) },
                )
                Input(
                    "Phone",
                    vm.field("profilePhone", vm.profile.text("phone")),
                    { vm.set("profilePhone", it) },
                )
                Button(
                    onClick = {
                        vm.action(
                            "profile",
                            obj(
                                "name" to vm.field("profileName", vm.profile.text("name")),
                                "phone" to vm.field("profilePhone", vm.profile.text("phone")),
                            ),
                        ) {
                            vm.set("dialog", "")
                        }
                    },
                    enabled = !vm.busy,
                ) {
                    AppText("Save profile")
                }
            }
        "receipt" -> DialogFrame("Payment receipt", vm) { ReceiptContent(vm) }
        "testRefund" ->
            DialogFrame("Request test refund", vm) {
                AppText(
                    "This simulates a refund and releases an unused booking or order. No real money will move."
                )
                AppText(
                    "Services already started or prepared cannot be refunded here.",
                    color = Muted,
                )
                Button(
                    onClick = { vm.refundTestPayment() },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText("Confirm test refund")
                }
            }
        "form" -> EditorDialog(vm)
        "logout",
        "cancelBooking",
        "end",
        "winner",
        "pay",
        "extendPaid" ->
            DialogFrame(
                when (dialog) {
                    "logout" -> "See you next session?"
                    "cancelBooking" -> "Cancel this reservation?"
                    "end" -> "End this session?"
                    "winner" -> "Confirm match winner?"
                    "extendPaid" -> "Add time to this session"
                    else -> "Confirm cash received"
                },
                vm,
            ) {
                if (dialog == "pay" || dialog == "extendPaid") {
                    if (dialog == "extendPaid")
                        Select(
                            "Extra time",
                            vm.field("paymentKind"),
                            listOf("Extension30" to "30 minutes", "Extension60" to "60 minutes"),
                            { vm.set("paymentKind", it) },
                        )
                    LaunchedEffect(vm.field("paymentId"), vm.field("paymentKind")) {
                        vm.quotePayment()
                    }
                    if (
                        (vm.paymentQuote.text("id") == vm.field("paymentId") &&
                            vm.paymentQuote.text("kind") == vm.field("paymentKind"))
                    ) {
                        AppText(
                            money(vm.paymentQuote.optInt("total")),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Lime,
                        )
                        AppText(
                            "Subtotal ${money(vm.paymentQuote.optInt("subtotal"))} · Tier discount ${money(vm.paymentQuote.optInt("discount"))}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                AppText(
                    when (dialog) {
                        "logout" -> "Sign out of this account."
                        "cancelBooking" -> "Your unpaid space will be released."
                        "end" -> "The prepaid package charge remains unchanged."
                        "winner" -> "${vm.field("winName")} will advance. Results are final."
                        "extendPaid" ->
                            "Confirm after receiving cash. Extra time is added only if the space remains available; otherwise no payment is recorded."
                        else ->
                            "Confirm only after receiving cash at the counter. The server applies the member’s current tier discount. This records an actual counter payment."
                    },
                    color = Muted,
                )
                Button(
                    onClick = {
                        when (dialog) {
                            "logout" -> {
                                vm.set("dialog", "")
                                vm.logout()
                            }
                            "cancelBooking" ->
                                vm.action(
                                    "cancel_booking",
                                    obj("id" to vm.field("cancelBooking")),
                                ) {
                                    vm.set("dialog", "")
                                }
                            "end" ->
                                vm.action("end_session", obj("id" to vm.field("endBooking"))) {
                                    vm.set("dialog", "")
                                }
                            "winner" ->
                                vm.action(
                                    "winner",
                                    obj(
                                        "match_id" to vm.field("winMatch"),
                                        "winner" to vm.field("winTeam"),
                                    ),
                                ) {
                                    vm.set("dialog", "")
                                }
                            "extendPaid" ->
                                vm.action(
                                    "extend_paid",
                                    obj(
                                        "id" to vm.field("paymentId"),
                                        "minutes" to
                                            if (vm.field("paymentKind") == "Extension30") 30
                                            else 60,
                                        "expected_total" to vm.paymentQuote.optInt("total"),
                                    ),
                                ) {
                                    vm.set("dialog", "")
                                }
                            else ->
                                vm.action(
                                    "pay",
                                    obj(
                                        "kind" to vm.field("paymentKind"),
                                        "id" to vm.field("paymentId"),
                                        "expected_total" to vm.paymentQuote.optInt("total"),
                                    ),
                                ) {
                                    vm.set("dialog", "")
                                }
                        }
                    },
                    enabled =
                        !vm.busy &&
                            (dialog !in listOf("pay", "extendPaid") ||
                                (vm.paymentQuote.text("id") == vm.field("paymentId") &&
                                    vm.paymentQuote.text("kind") == vm.field("paymentKind"))),
                ) {
                    AppText("Confirm")
                }
            }
    }
}

@Composable
fun TimeFields(label: String, value: String, onChange: (String) -> Unit) {
    val t = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(18, 0))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Select(
            "$label · hour",
            t.hour.toString(),
            (0..23).map { it.toString() to "%02d".format(it) },
            { onChange(t.withHour(it.toInt()).toString()) },
            Modifier.weight(1f),
        )
        Select(
            "Minute",
            t.minute.toString(),
            (0..59).map { it.toString() to "%02d".format(it) },
            { onChange(t.withMinute(it.toInt()).toString()) },
            Modifier.weight(1f),
        )
    }
}

data class FormField(
    val key: String,
    val label: String,
    val kind: String = "text",
    val options: List<Pair<String, String>> = emptyList(),
    val default: String = "",
    val optional: Boolean = false,
)

fun choices(vararg values: String) = values.map { it to it }

@Composable
fun EditorDialog(vm: AppViewModel) {
    val type = vm.field("formType")
    val current = runCatching { JSONObject(vm.field("formData", "{}")) }.getOrDefault(JSONObject())
    val staff =
        vm.rows("people")
            .filter { it.text("role") in listOf("staff", "admin") }
            .map { it.text("id") to it.text("name") }
    val fields =
        when (type) {
            "save_resource" ->
                listOf(
                    FormField("name", "Space name"),
                    FormField(
                        "kind",
                        "Type",
                        "select",
                        VenueDirectory.kinds.map { it to VenueDirectory.label(it) },
                        "PC",
                    ),
                    FormField(
                        "zone",
                        "Zone",
                        "select",
                        choices(
                            "Standard",
                            "VIP",
                            "Solo",
                            "Duo",
                            "Squad",
                            "Social",
                            "Lounge",
                            "Simulator",
                        ),
                        "Standard",
                    ),
                    FormField("description", "Specifications / description"),
                    FormField("capacity", "Player capacity", "number", default = "1"),
                    FormField("rate", "Hourly rate (sen: RM 6 = 600)", "number"),
                    FormField(
                        "status",
                        "Availability",
                        "select",
                        choices("Available", "Maintenance", "Retired"),
                        "Available",
                    ),
                )
            "save_product" ->
                listOf(
                    FormField("name", "Product name"),
                    FormField(
                        "category_id",
                        "Category",
                        "select",
                        vm.rows("categories").map { it.text("id") to it.text("name") },
                    ),
                    FormField("price", "Price (sen: RM 6 = 600)", "number"),
                    FormField("threshold", "Low stock threshold", "number", default = "5"),
                    FormField(
                        "image",
                        "Product image",
                        "select",
                        choices(
                            "iced_coffee",
                            "iced_lemon_tea",
                            "energy_drink",
                            "mineral_water",
                            "chicken_burger",
                            "fries",
                            "cup_noodles",
                            "potato_chips",
                            "game_card",
                        ),
                        "iced_coffee",
                    ),
                    FormField(
                        "active",
                        "On sale",
                        "select",
                        listOf("true" to "Yes", "false" to "No"),
                        "true",
                    ),
                )
            "restock" ->
                listOf(
                    FormField("quantity", "Quantity", "number"),
                    FormField("reason", "Supplier / restock note"),
                )
            "save_category" -> listOf(FormField("name", "Category name"))
            "save_event" ->
                listOf(
                    FormField("name", "Competition name"),
                    FormField("game", "Game"),
                    FormField("description", "Details / rules", "multiline", optional = true),
                    FormField("starts_at", "Starts", "datetime"),
                    FormField("ends_at", "Ends", "datetime"),
                    FormField("registration_closes_at", "Registration closes", "datetime"),
                    FormField("fee_rm", "Entry fee (RM)", "money", default = "0.00"),
                    FormField("capacity", "Team slots (2–64)", "number", default = "8"),
                    FormField(
                        "prize_first",
                        "First prize",
                        "multiline",
                        default = "To be announced",
                    ),
                    FormField(
                        "prize_second",
                        "Second prize",
                        "multiline",
                        default = "To be announced",
                    ),
                    FormField(
                        "prize_third",
                        "Third prize",
                        "multiline",
                        default = "To be announced",
                    ),
                    FormField(
                        "prize_consolation",
                        "Consolation prize",
                        "multiline",
                        default = "No consolation prize",
                    ),
                    FormField(
                        "status",
                        "Status",
                        "select",
                        choices("Draft", "Open", "Cancelled"),
                        "Draft",
                    ),
                )
            "save_promotion" ->
                listOf(
                    FormField("code", "Promo code"),
                    FormField("title", "Promo title"),
                    FormField(
                        "scope",
                        "Applies to",
                        "select",
                        choices("All", "Booking", "Order", "Event"),
                        "Booking",
                    ),
                    FormField(
                        "benefit",
                        "Benefit",
                        "select",
                        choices("Percentage", "Fixed", "Free"),
                        "Percentage",
                    ),
                ) +
                    (when (current.text("benefit", "Percentage")) {
                        "Fixed" ->
                            listOf(
                                FormField("amount_rm", "Discount (RM)", "money", default = "5.00")
                            )
                        "Free" -> emptyList()
                        else ->
                            listOf(FormField("percent", "Discount (%)", "number", default = "10"))
                    }) +
                    listOf(
                        FormField(
                            "resource_kind",
                            "Booking activity",
                            "select",
                            choices(
                                "All",
                                "PC",
                                "VIPRoom",
                                "Console",
                                "Racing",
                                "Arcade",
                                "GoKart",
                                "Pool",
                                "Snooker",
                            ),
                            "All",
                        ),
                        FormField(
                            "max_minutes",
                            "Maximum minutes (0 = any)",
                            "number",
                            default = "0",
                        ),
                        FormField("starts_at", "Valid from", "datetime"),
                        FormField("ends_at", "Expires", "datetime"),
                        FormField("usage_limit", "Total uses", "number", default = "50"),
                        FormField("per_user_limit", "Uses per customer", "number", default = "1"),
                        FormField(
                            "target_user",
                            "Customer (optional)",
                            "select",
                            listOf("" to "Any customer") +
                                vm.rows("people")
                                    .filter { it.text("role") == "customer" }
                                    .map { it.text("id") to it.text("name") },
                            optional = true,
                        ),
                        FormField(
                            "active",
                            "Enabled",
                            "select",
                            listOf("true" to "Yes", "false" to "No"),
                            "true",
                        ),
                    )
            "save_reward" ->
                listOf(
                    FormField("name", "Reward name"),
                    FormField("points", "Points required", "number"),
                    FormField("stock", "Available quantity", "number"),
                    FormField(
                        "active",
                        "Available",
                        "select",
                        listOf("true" to "Yes", "false" to "No"),
                        "true",
                    ),
                )
            "void_bill" -> listOf(FormField("reason", "Reason / cash refund note", "multiline"))
            "member_profile" ->
                listOf(FormField("name", "Member name"), FormField("phone", "Phone number"))
            "weekly_roster" ->
                listOf(
                    FormField("user_id", "Staff member", "select", staff),
                    FormField("day", "Week begins", "date"),
                    FormField(
                        "pattern",
                        "Working days",
                        "select",
                        choices("Weekdays", "Every day", "Weekend"),
                        "Weekdays",
                    ),
                    FormField("start_time", "Start time", "time", default = "09:00"),
                    FormField("end_time", "End time", "time", default = "17:00"),
                )
            "schedule" ->
                listOf(
                    FormField("user_id", "Staff member", "select", staff),
                    FormField("starts_at", "Shift start", "datetime"),
                    FormField("ends_at", "Shift end", "datetime"),
                )
            "task" ->
                (if (vm.admin) listOf(FormField("user_id", "Assigned to", "select", staff))
                else emptyList()) +
                    listOf(FormField("title", "Mission"), FormField("day", "Due date", "date"))
            "leave" ->
                listOf(
                    FormField("day", "Leave date", "date"),
                    FormField("reason", "Reason", "multiline"),
                )
            "handover" ->
                listOf(
                    FormField(
                        "category",
                        "Category",
                        "select",
                        choices("Opening", "Closing", "Stock", "General"),
                        "General",
                    ),
                    FormField("title", "Title"),
                    FormField("note", "Handover details", "multiline"),
                )
            "ticket" ->
                listOf(
                    FormField("asset", "PC / equipment name"),
                    FormField("title", "What needs attention?", "multiline"),
                    FormField(
                        "priority",
                        "Priority",
                        "select",
                        choices("Low", "Normal", "Urgent"),
                        "Normal",
                    ),
                )
            "resolve" -> listOf(FormField("resolution", "What was fixed?", "multiline"))
            "extend_booking" ->
                listOf(
                    FormField(
                        "minutes",
                        "Extra time",
                        "select",
                        listOf("30" to "30 minutes", "60" to "60 minutes"),
                        "30",
                    )
                )
            else -> emptyList()
        }
    val initialTime = remember {
        ZonedDateTime.now(CafeZone).plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0)
    }
    fun default(f: FormField) =
        when (f.kind) {
            "date" -> LocalDate.now(CafeZone).toString()
            "datetime" ->
                (if (type == "save_promotion") {
                        if (f.key == "ends_at") initialTime.plusDays(30)
                        else java.time.Instant.ofEpochMilli(vm.nowMillis).atZone(CafeZone)
                    } else if (f.key == "ends_at") initialTime.plusHours(4) else initialTime)
                    .toInstant()
                    .toString()
            "select" -> f.default.ifBlank { f.options.firstOrNull()?.first.orEmpty() }
            else -> f.default
        }
    fun update(key: String, value: String) {
        vm.set("formData", JSONObject(current.toString()).put(key, value).toString())
    }
    DialogFrame(
        when (type) {
            "save_event" -> "Competition details"
            "save_promotion" -> "Promo code details"
            else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
        },
        vm,
    ) {
        if (type == "save_event") PosterPicker(vm, current.text("poster_url"))
        fields.forEach { f ->
            val value = current.text(f.key, default(f))
            when (f.kind) {
                "select" -> Select(f.label, value, f.options, { update(f.key, it) })
                "date" -> DateFields(f.label, value, { update(f.key, it) })
                "time" -> TimeFields(f.label, value, { update(f.key, it) })
                "datetime" -> {
                    val dt =
                        runCatching { ZonedDateTime.parse(value).withZoneSameInstant(CafeZone) }
                            .getOrDefault(initialTime)
                    DateFields(
                        "${f.label} · Malaysia time",
                        dt.toLocalDate().toString(),
                        {
                            update(
                                f.key,
                                LocalDateTime.of(LocalDate.parse(it), dt.toLocalTime())
                                    .atZone(CafeZone)
                                    .toInstant()
                                    .toString(),
                            )
                        },
                    )
                    TimeFields(
                        f.label,
                        dt.toLocalTime().toString(),
                        {
                            update(
                                f.key,
                                LocalDateTime.of(dt.toLocalDate(), LocalTime.parse(it))
                                    .atZone(CafeZone)
                                    .toInstant()
                                    .toString(),
                            )
                        },
                    )
                }
                else ->
                    Input(
                        f.label,
                        value,
                        { update(f.key, it) },
                        numeric = f.kind == "number",
                        decimal = f.kind == "money",
                        multiline = f.kind == "multiline",
                    )
            }
        }
        Button(
            onClick = {
                val payload = JSONObject(current.toString())
                fields.forEach { f -> if (!payload.has(f.key)) payload.put(f.key, default(f)) }
                val errors =
                    validateFormFields(
                        type,
                        fields.associate { it.key to payload.text(it.key) },
                        fields,
                    )
                if (errors.isNotEmpty()) vm.invalid(errors.joinToString("\n"))
                else {
                    if (type == "save_event")
                        payload.put("fee", moneyInputSen(payload.text("fee_rm")))
                    if (type == "save_promotion")
                        payload.put(
                            "amount",
                            when (payload.text("benefit")) {
                                "Free" -> 0
                                "Fixed" -> moneyInputSen(payload.text("amount_rm"))
                                else -> payload.text("percent").toInt()
                            },
                        )
                    vm.action(type, payload) {
                        vm.set("dialog", "")
                        vm.set("formData", "{}")
                    }
                }
            },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppText("Save")
        }
    }
}
