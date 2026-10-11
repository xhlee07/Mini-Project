package com.ctfly.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.time.LocalDate
import org.json.JSONObject

fun AppViewModel.openForm(type: String, initial: JSONObject = JSONObject()) {
    set("formType", type)
    set("formData", initial.toString())
    set("dialog", "form")
}

fun AppViewModel.person(id: String) =
    rows("people").find { it.text("id") == id }?.text("name") ?: id.take(8)

fun shareText(context: android.content.Context, title: String, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            },
            title,
        )
    )
}

@Composable
fun OperationsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val tab = vm.field("opsTab", "Sessions")
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Eyebrow("CREW HQ", Aqua)
            AppText("Keep the club moving.", style = MaterialTheme.typography.headlineMedium)
            AppText("Welcome back, ${vm.profile.text("name")}", color = Muted)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(
                    vm.rows("bookings").count { it.text("status") == "Active" }.toString(),
                    "Live sessions",
                    Aqua,
                    Modifier.weight(1f),
                )
                Stat(
                    vm.rows("orders")
                        .count {
                            it.optBoolean("paid") &&
                                it.text("status") in listOf("Pending", "Preparing")
                        }
                        .toString(),
                    "In the kitchen",
                    Orange,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            SurfaceCard {
                AppText("A scan. And they’re in.", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                AppText(
                    "Paid bookings only. Check-in starts the session within its reserved time.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = {
                        GmsBarcodeScanning.getClient(context)
                            .startScan()
                            .addOnSuccessListener { barcode ->
                                barcode.rawValue?.let {
                                    vm.set("scanToken", it)
                                    vm.set("dialog", "scan")
                                }
                            }
                            .addOnFailureListener {
                                vm.notify("Scanner unavailable. Use the entry code instead.")
                                vm.set("dialog", "scan")
                            }
                    },
                    enabled = !vm.busy,
                ) {
                    AppIcon(Icons.Outlined.QrCodeScanner, null)
                    Spacer(Modifier.width(10.dp))
                    AppText("Scan entry QR")
                }
                TextButton(onClick = { vm.set("dialog", "scan") }) {
                    AppText("Enter code manually")
                }
            }
        }
        item {
            FilterRow(listOf("Sessions", "Orders", "Entries", "Rewards"), tab) {
                vm.set("opsTab", it)
            }
        }
        when (tab) {
            "Sessions" ->
                items(
                    vm.rows("bookings").filter {
                        it.text("status") in listOf("Reserved", "Confirmed", "Active")
                    }
                ) { b ->
                    SurfaceCard {
                        AppText(
                            vm.rows("resources")
                                .find { it.text("id") == b.text("resource_id") }
                                ?.text("name") ?: "Space",
                            fontWeight = FontWeight.Bold,
                        )
                        AppText(
                            "Booking ${b.text("id").take(8)} · ${dateLabel(b.text("starts_at"))}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                        Chip(b.text("status"), Aqua)
                        AppText(
                            "Until ${dateLabel(b.text("ends_at"))} · ${money(b.optInt("total"))}",
                            fontSize = 12.sp,
                        )
                        if (!b.optBoolean("paid"))
                            TextButton(
                                onClick = {
                                    vm.set("paymentKind", "Booking")
                                    vm.set("paymentId", b.text("id"))
                                    vm.set("dialog", "pay")
                                }
                            ) {
                                AppText("Collect cash payment")
                            }
                        if (
                            b.optBoolean("paid") &&
                                b.text("status") in listOf("Confirmed", "Active")
                        )
                            TextButton(
                                onClick = {
                                    vm.set("paymentKind", "Extension30")
                                    vm.set("paymentId", b.text("id"))
                                    vm.set("dialog", "extendPaid")
                                }
                            ) {
                                AppText("Add time & collect cash")
                            }
                        if (b.text("status") == "Active")
                            TextButton(
                                onClick = {
                                    vm.set("endBooking", b.text("id"))
                                    vm.set("dialog", "end")
                                }
                            ) {
                                AppText("End session early")
                            }
                        if (vm.admin && b.text("status") == "Reserved")
                            TextButton(
                                onClick = {
                                    vm.openForm(
                                        "extend_booking",
                                        obj("id" to b.text("id"), "minutes" to "30"),
                                    )
                                }
                            ) {
                                AppText("Extend reservation")
                            }
                    }
                }
            "Orders" ->
                items(
                    vm.rows("orders").filter {
                        it.text("status") !in listOf("Delivered", "Cancelled")
                    }
                ) { o ->
                    SurfaceCard {
                        AppText("Order ${o.text("id").take(8)}", fontWeight = FontWeight.Bold)
                        Chip(o.text("status"), Orange)
                        if (
                            vm.rows("test_payments").any {
                                it.text("source_id") == o.text("id") &&
                                    it.text("status") == "Succeeded"
                            }
                        )
                            Chip("Test payment", Aqua)
                        vm.rows("order_items")
                            .filter { it.text("order_id") == o.text("id") }
                            .forEach { AppText("${it.optInt("qty")} × ${it.text("name")}") }
                        val b = vm.rows("bookings").find { it.text("id") == o.text("booking_id") }
                        val r =
                            vm.rows("resources").find { it.text("id") == b?.text("resource_id") }
                        AppText(
                            if (r == null) "Counter pickup" else "Deliver to ${r.text("name")}",
                            color = Muted,
                        )
                        if (!o.optBoolean("paid"))
                            TextButton(
                                onClick = {
                                    vm.set("paymentKind", "Order")
                                    vm.set("paymentId", o.text("id"))
                                    vm.set("dialog", "pay")
                                }
                            ) {
                                AppText("Collect ${money(o.optInt("total"))}")
                            }
                        else {
                            val next =
                                mapOf(
                                    "Pending" to "Preparing",
                                    "Preparing" to "Ready",
                                    "Ready" to "Delivered",
                                )[o.text("status")]
                            if (next != null)
                                Button(
                                    onClick = {
                                        vm.action(
                                            "order_status",
                                            obj("id" to o.text("id"), "status" to next),
                                        )
                                    },
                                    enabled = !vm.busy,
                                ) {
                                    AppText("Mark $next")
                                }
                        }
                    }
                }
            "Entries" ->
                items(vm.rows("registrations").filter { it.text("status") == "Registered" }) { r ->
                    SurfaceCard {
                        AppText(r.text("team"), fontWeight = FontWeight.Bold)
                        AppText(
                            vm.rows("events")
                                .find { it.text("id") == r.text("event_id") }
                                ?.text("name") ?: "Event",
                            color = Muted,
                        )
                        if (!r.optBoolean("paid"))
                            TextButton(
                                onClick = {
                                    vm.set("paymentKind", "Event")
                                    vm.set("paymentId", r.text("id"))
                                    vm.set("dialog", "pay")
                                }
                            ) {
                                AppText("Collect ${money(r.optInt("fee"))}")
                            }
                        else Chip("Paid", Aqua)
                    }
                }
            else ->
                items(vm.rows("redemptions").filter { it.text("status") == "Pending" }) { r ->
                    SurfaceCard {
                        AppText(r.text("name"), fontWeight = FontWeight.Bold)
                        AppText("Voucher ${r.text("id").take(8)}", color = Muted)
                        Button(
                            onClick = { vm.action("collect_reward", obj("id" to r.text("id"))) },
                            enabled = !vm.busy,
                        ) {
                            AppText("Mark collected")
                        }
                    }
                }
        }
    }
}

@Composable
fun CrewScreen(vm: AppViewModel) {
    val today = LocalDate.now(CafeZone).toString()
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Eyebrow("YOUR DAY, SORTED", Aqua)
            AppText("My shifts & missions.", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            SectionTitle("On the roster", action = "Request leave") {
                vm.openForm("leave", obj("day" to today))
            }
        }
        items(
            vm.rows("shifts")
                .filter { it.text("user_id") == vm.userId && it.text("status") == "Scheduled" }
                .sortedBy { it.text("starts_at") }
        ) { s ->
            SurfaceCard {
                AppText(dateLabel(s.text("starts_at")), fontWeight = FontWeight.Bold)
                AppText("Until ${dateLabel(s.text("ends_at"))}", color = Muted)
                val a = vm.rows("attendance").find { it.text("shift_id") == s.text("id") }
                if (a != null)
                    AppText(
                        "Late ${a.optInt("late_minutes")} min · Worked ${a.optInt("minutes")} min",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                Button(
                    onClick = { vm.action("clock", obj("id" to s.text("id"))) },
                    enabled = !vm.busy && (a == null || a.isNull("clock_out")),
                ) {
                    AppText(
                        if (a == null) "Clock in"
                        else if (a.isNull("clock_out")) "Clock out" else "Shift complete"
                    )
                }
            }
        }
        if (vm.rows("shifts").none { it.text("user_id") == vm.userId })
            item { EmptyState("No shifts assigned", "Your manager will add your schedule here.") }
        item { SectionTitle("Daily readiness", today) }
        item {
            SurfaceCard {
                listOf(
                        "Sanitize desks",
                        "Test headsets",
                        "Check network",
                        "Count cues & balls",
                        "Stock drinks",
                        "Closing safety check",
                    )
                    .forEach { item ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                vm.rows("checklist").any { it.text("item") == item },
                                onCheckedChange = { vm.action("checklist", obj("item" to item)) },
                                enabled = !vm.busy,
                            )
                            AppText(item, fontSize = 13.sp)
                        }
                    }
            }
        }
        item {
            SectionTitle("Missions", action = "Add mission") {
                vm.openForm("task", obj("day" to today, "user_id" to vm.userId))
            }
        }
        items(vm.rows("tasks")) { t ->
            SurfaceCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        t.optBoolean("done"),
                        onCheckedChange = { vm.action("task_done", obj("id" to t.text("id"))) },
                        enabled = !vm.busy,
                    )
                    Column {
                        AppText(t.text("title"), fontWeight = FontWeight.Bold)
                        AppText(
                            "${t.text("day")} · ${vm.person(t.text("user_id"))}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        item { SectionTitle("Leave requests") }
        items(vm.rows("leave_requests").filter { it.text("user_id") == vm.userId }) { l ->
            SurfaceCard {
                AppText(l.text("day"), fontWeight = FontWeight.Bold)
                AppText(l.text("reason"), color = Muted)
                Chip(l.text("status"), Aqua)
            }
        }
    }
}

@Composable
fun HandoverScreen(vm: AppViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Eyebrow("KEEP EVERYONE IN THE LOOP", Aqua)
                AppText("The shift journal.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.openForm("handover", obj("category" to "General")) }) {
                    AppText("Write a handover")
                }
            }
        }
        items(vm.rows("handovers")) { h ->
            SurfaceCard {
                Chip(h.text("category"), Aqua)
                AppText(h.text("title"), style = MaterialTheme.typography.titleLarge)
                AppText(h.text("note"), color = Muted)
                AppText(
                    "${vm.person(h.text("user_id"))} · ${dateLabel(h.text("created_at"))}",
                    fontSize = 11.sp,
                    color = Muted,
                )
                if (h.text("user_id") != vm.userId) {
                    val read =
                        vm.rows("handover_reads").any { it.text("handover_id") == h.text("id") }
                    TextButton(
                        onClick = { vm.action("ack", obj("id" to h.text("id"))) },
                        enabled = !read && !vm.busy,
                    ) {
                        AppText(if (read) "✓ Acknowledged" else "Acknowledge")
                    }
                }
            }
        }
        if (vm.rows("handovers").isEmpty())
            item { EmptyState("A clean slate", "Leave the next shift a helpful note.") }
    }
}

@Composable
fun EquipmentScreen(vm: AppViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Eyebrow("LOOK AFTER THE LOADOUT", Orange)
                AppText("Equipment care.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.openForm("ticket", obj("priority" to "Normal")) }) {
                    AppText("Report an issue")
                }
            }
        }
        items(vm.rows("tickets")) { t ->
            SurfaceCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t.text("priority"), Orange)
                    Chip(t.text("status"), Aqua)
                }
                AppText(t.text("asset"), fontSize = 12.sp, color = Muted)
                AppText(t.text("title"), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                if (t.text("assigned_user").isNotEmpty())
                    AppText(
                        "Assigned: ${vm.person(t.text("assigned_user"))}",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                if (t.text("resolution").isNotEmpty()) AppText(t.text("resolution"), color = Muted)
                when (t.text("status")) {
                    "Open" ->
                        TextButton(
                            onClick = { vm.action("claim", obj("id" to t.text("id"))) },
                            enabled = !vm.busy,
                        ) {
                            AppText("I’ll take this")
                        }
                    "In progress" ->
                        if (t.text("assigned_user") == vm.userId || vm.admin)
                            TextButton(
                                onClick = { vm.openForm("resolve", obj("id" to t.text("id"))) }
                            ) {
                                AppText("Resolve with note")
                            }
                    "Resolved" ->
                        if (
                            t.text("user_id") == vm.userId ||
                                t.text("assigned_user") == vm.userId ||
                                vm.admin
                        )
                            TextButton(
                                onClick = { vm.action("reopen", obj("id" to t.text("id"))) },
                                enabled = !vm.busy,
                            ) {
                                AppText("Reopen issue")
                            }
                }
            }
        }
        if (vm.rows("tickets").isEmpty())
            item { EmptyState("All clear", "Equipment issues will appear here.") }
    }
}

@Composable
fun ManagementScreen(vm: AppViewModel) {
    val tab = vm.field("manageTab", "Spaces")
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Eyebrow("MANAGER CONSOLE", Violet)
            AppText("Run a better club.", style = MaterialTheme.typography.headlineMedium)
            FilterRow(
                listOf("Spaces", "Inventory", "Events", "Staff", "Members", "Rewards", "Reports"),
                tab,
            ) {
                vm.set("manageTab", it)
            }
        }
        when (tab) {
            "Spaces" -> {
                item {
                    Button(
                        onClick = {
                            vm.openForm(
                                "save_resource",
                                obj(
                                    "kind" to "PC",
                                    "zone" to "Standard",
                                    "capacity" to "1",
                                    "status" to "Available",
                                    "rate" to "600",
                                ),
                            )
                        }
                    ) {
                        AppText("Add PC / lounge space")
                    }
                }
                items(vm.rows("resources")) { r ->
                    SurfaceCard {
                        AppText(r.text("name"), fontWeight = FontWeight.Bold)
                        AppText(
                            "${r.text("kind")} · ${money(r.optInt("rate"))}/hr · ${r.text("status")}",
                            color = Muted,
                        )
                        TextButton(onClick = { vm.openForm("save_resource", r) }) {
                            AppText("Edit space")
                        }
                    }
                }
            }
            "Inventory" -> {
                item {
                    Row {
                        Button(
                            onClick = {
                                vm.openForm(
                                    "save_product",
                                    obj(
                                        "category_id" to
                                            (vm.rows("categories").firstOrNull()?.text("id")
                                                ?: "1"),
                                        "threshold" to "5",
                                        "active" to "true",
                                        "image" to "iced_coffee",
                                    ),
                                )
                            }
                        ) {
                            AppText("Add product")
                        }
                        TextButton(onClick = { vm.openForm("save_category") }) {
                            AppText("Add category")
                        }
                    }
                }
                items(vm.rows("products")) { p ->
                    SurfaceCard {
                        AppText(p.text("name"), fontWeight = FontWeight.Bold)
                        AppText(
                            "${money(p.optInt("price"))} · ${p.optInt("stock")} in stock",
                            color = Muted,
                        )
                        if (p.optInt("stock") <= p.optInt("threshold")) Chip("Low stock", Orange)
                        Row {
                            TextButton(onClick = { vm.openForm("save_product", p) }) {
                                AppText("Edit")
                            }
                            TextButton(
                                onClick = {
                                    vm.openForm(
                                        "restock",
                                        obj(
                                            "id" to p.text("id"),
                                            "quantity" to "10",
                                            "reason" to "Delivery",
                                        ),
                                    )
                                }
                            ) {
                                AppText("Restock")
                            }
                        }
                    }
                }
                item { SectionTitle("Stock movements") }
                items(vm.rows("stock_records").take(30)) { s ->
                    AppText(
                        "${s.optInt("quantity")} · ${vm.rows("products").find{it.text("id")==s.text("product_id")}?.text("name")} · ${s.text("reason")}",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }
            "Events" -> {
                item {
                    Button(
                        onClick = {
                            vm.openForm(
                                "save_event",
                                obj("capacity" to "8", "fee" to "1500", "status" to "Open"),
                            )
                        }
                    ) {
                        AppText("Create tournament")
                    }
                }
                items(vm.rows("events")) { e ->
                    SurfaceCard {
                        AppText(e.text("name"), fontWeight = FontWeight.Bold)
                        AppText(
                            "${dateLabel(e.text("starts_at"))} · ${e.text("status")}",
                            color = Muted,
                        )
                        Row {
                            TextButton(onClick = { vm.openForm("save_event", e) }) {
                                AppText("Edit")
                            }
                            if (e.text("status") == "Open")
                                TextButton(
                                    onClick = {
                                        vm.action("bracket", obj("event_id" to e.text("id")))
                                    },
                                    enabled = !vm.busy,
                                ) {
                                    AppText("Generate bracket")
                                }
                        }
                        vm.rows("matches")
                            .filter { it.text("event_id") == e.text("id") }
                            .forEach { m ->
                                AppText(
                                    "R${m.optInt("round")} · ${m.text("team_a","TBD")} vs ${m.text("team_b","Bye / TBD")}",
                                    fontSize = 12.sp,
                                )
                                if (m.optBoolean("done"))
                                    AppText(
                                        "Winner: ${m.text("team_winner")}",
                                        color = Lime,
                                        fontSize = 12.sp,
                                    )
                                else if (m.text("a").isNotEmpty() && m.text("b").isNotEmpty())
                                    Row {
                                        listOf("a", "b").forEach { side ->
                                            TextButton(
                                                onClick = {
                                                    vm.set("winMatch", m.text("id"))
                                                    vm.set("winTeam", m.text(side))
                                                    vm.set("winName", m.text("team_$side"))
                                                    vm.set("dialog", "winner")
                                                }
                                            ) {
                                                AppText(
                                                    "${m.text("team_$side")} wins",
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }
                                    }
                            }
                    }
                }
            }
            "Staff" -> {
                item {
                    Row {
                        Button(onClick = { vm.openForm("schedule") }) { AppText("Assign shift") }
                        TextButton(onClick = { vm.openForm("weekly_roster") }) {
                            AppText("Weekly roster")
                        }
                        TextButton(
                            onClick = {
                                vm.openForm(
                                    "task",
                                    obj("day" to LocalDate.now(CafeZone).toString()),
                                )
                            }
                        ) {
                            AppText("Assign mission")
                        }
                    }
                }
                item {
                    AppText(
                        "Create staff accounts through Supabase Auth, then assign their role using the setup SQL. Public signup always creates a customer.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                items(vm.rows("people").filter { it.text("role") != "customer" }) { p ->
                    SurfaceCard {
                        AppText(p.text("name"), fontWeight = FontWeight.Bold)
                        Chip(p.text("role"), Aqua)
                    }
                }
                item { SectionTitle("Roster") }
                items(vm.rows("shifts")) { s ->
                    SurfaceCard {
                        AppText(vm.person(s.text("user_id")), fontWeight = FontWeight.Bold)
                        AppText(
                            "${dateLabel(s.text("starts_at"))} → ${dateLabel(s.text("ends_at"))}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                        Chip(s.text("status"))
                        if (s.text("status") == "Scheduled")
                            TextButton(
                                onClick = {
                                    vm.action("cancel_shift", obj("shift_id" to s.text("id")))
                                },
                                enabled = !vm.busy,
                            ) {
                                AppText("Cancel shift")
                            }
                    }
                }
                item { SectionTitle("Leave decisions") }
                items(vm.rows("leave_requests")) { l ->
                    SurfaceCard {
                        AppText(
                            "${vm.person(l.text("user_id"))} · ${l.text("day")}",
                            fontWeight = FontWeight.Bold,
                        )
                        AppText(l.text("reason"), color = Muted)
                        Chip(l.text("status"))
                        if (l.text("status") == "Pending")
                            Row {
                                listOf("Approved", "Declined").forEach { status ->
                                    TextButton(
                                        onClick = {
                                            vm.action(
                                                "leave_decision",
                                                obj("leave_id" to l.text("id"), "status" to status),
                                            )
                                        },
                                        enabled = !vm.busy,
                                    ) {
                                        AppText(status)
                                    }
                                }
                            }
                    }
                }
            }
            "Members" ->
                items(vm.rows("people").filter { it.text("role") == "customer" }) { p ->
                    SurfaceCard {
                        AppText(p.text("name"), fontWeight = FontWeight.Bold)
                        AppText(p.text("phone"), color = Muted)
                        TextButton(
                            onClick = {
                                vm.openForm(
                                    "member_profile",
                                    obj(
                                        "user_id" to p.text("id"),
                                        "name" to p.text("name"),
                                        "phone" to p.text("phone"),
                                    ),
                                )
                            }
                        ) {
                            AppText("Edit member")
                        }
                        TextButton(
                            onClick = {
                                vm.action(
                                    "member_status",
                                    obj(
                                        "user_id" to p.text("id"),
                                        "active" to !p.optBoolean("active"),
                                    ),
                                )
                            },
                            enabled = !vm.busy,
                        ) {
                            AppText(
                                if (p.optBoolean("active")) "Deactivate account"
                                else "Reactivate account"
                            )
                        }
                    }
                }
            "Rewards" -> {
                item {
                    Button(
                        onClick = {
                            vm.openForm(
                                "save_reward",
                                obj("stock" to "10", "points" to "100", "active" to "true"),
                            )
                        }
                    ) {
                        AppText("Create reward")
                    }
                }
                items(vm.rows("rewards")) { r ->
                    SurfaceCard {
                        AppText(r.text("name"), fontWeight = FontWeight.Bold)
                        AppText(
                            "${r.optInt("points")} points · ${r.optInt("stock")} left",
                            color = Muted,
                        )
                        TextButton(onClick = { vm.openForm("save_reward", r) }) {
                            AppText("Edit reward")
                        }
                    }
                }
            }
            else -> {
                val bills =
                    vm.rows("bills").filter {
                        it.text("status", "Paid") == "Paid" && it.text("environment") != "test"
                    }
                val totals =
                    listOf("Booking", "Order", "Event", "Extension").map { kind ->
                        kind to
                            bills.filter { it.text("kind") == kind }.sumOf { it.optInt("total") }
                    }
                item {
                    Stat(
                        money(bills.sumOf { it.optInt("total") }),
                        "Paid revenue · all time",
                        Lime,
                        Modifier.fillMaxWidth(),
                    )
                }
                item {
                    SurfaceCard {
                        AppText("Revenue mix", fontWeight = FontWeight.Bold)
                        totals.forEachIndexed { i, (kind, value) ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AppText(kind, color = Muted)
                                AppText(money(value))
                            }
                            LinearProgressIndicator(
                                progress = {
                                    if (totals.sumOf { it.second } == 0) 0f
                                    else value.toFloat() / totals.sumOf { it.second }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = listOf(Lime, Orange, Violet, Aqua)[i],
                            )
                        }
                    }
                }
                item {
                    SurfaceCard {
                        AppText("Operations", fontWeight = FontWeight.Bold)
                        AppText(
                            "${vm.rows("bookings").count{it.text("status")=="Completed"}} completed sessions"
                        )
                        AppText(
                            "${vm.rows("products").count{it.optInt("stock")<=it.optInt("threshold")}} low-stock products"
                        )
                        AppText(
                            "${vm.rows("attendance").sumOf{it.optInt("minutes")}} worked minutes · ${vm.rows("attendance").sumOf{it.optInt("late_minutes")}} late minutes"
                        )
                    }
                }
                item { SectionTitle("Recent bills") }
                items(vm.rows("bills").filter { it.text("environment") != "test" }.take(30)) { b ->
                    SurfaceCard {
                        AppText(
                            "${b.text("kind")} · ${money(b.optInt("total"))}",
                            fontWeight = FontWeight.Bold,
                        )
                        Chip(b.text("status", "Paid"))
                        if (
                            b.text("status", "Paid") == "Paid" &&
                                b.text("kind") != "Extension" &&
                                b.text("environment") != "test"
                        )
                            TextButton(
                                onClick = {
                                    vm.openForm("void_bill", obj("bill_id" to b.text("id")))
                                }
                            ) {
                                AppText("Void unfulfilled payment")
                            }
                    }
                }
                item { SectionTitle("Best sellers") }
                items(
                    vm.rows("order_items")
                        .filter { i ->
                            vm.rows("orders").any {
                                it.text("id") == i.text("order_id") &&
                                    it.optBoolean("paid") &&
                                    it.text("status") != "Cancelled" &&
                                    vm.rows("bills").none { bill ->
                                        bill.text("source_id") == it.text("id") &&
                                            bill.text("environment") == "test"
                                    }
                            }
                        }
                        .groupBy { it.text("name") }
                        .entries
                        .sortedByDescending { it.value.sumOf { it.optInt("qty") } }
                ) { entry ->
                    AppText(
                        "${entry.key} · ${entry.value.sumOf{it.optInt("qty")}} sold",
                        color = Muted,
                    )
                }
                item {
                    Button(
                        onClick = {
                            shareText(
                                context,
                                "CTFLY revenue report",
                                "Kind,Revenue (RM)\n" +
                                    totals.joinToString("\n") {
                                        "${it.first},${"%.2f".format(java.util.Locale.US,it.second/100.0)}"
                                    },
                            )
                        }
                    ) {
                        AppText("Share revenue report")
                    }
                }
            }
        }
    }
}
