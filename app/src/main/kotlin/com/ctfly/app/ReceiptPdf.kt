package com.ctfly.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import org.json.JSONObject

fun receiptDate(value: String): String =
    runCatching {
            java.time.Instant.parse(value)
                .atZone(CafeZone)
                .format(
                    java.time.format.DateTimeFormatter.ofPattern(
                        "dd MMM yyyy · h:mm a",
                        java.util.Locale.ENGLISH,
                    )
                )
        }
        .getOrDefault(value)

fun receiptLines(b: JSONObject): List<String> = buildList {
    add("CTFLY GAMING CAFE")
    add("Malaysia · MYR")
    add("Tel: 0173707898")
    if (b.text("environment") == "test") {
        add("TEST RECEIPT - NO MONEY CHARGED")
        add("School project payment simulation")
    }
    val stamp =
        runCatching {
                java.time.Instant.parse(b.text("created_at"))
                    .atZone(CafeZone)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
            }
            .getOrDefault("")
    add("------------------------------------")
    add("RECEIPT DETAILS")
    add("Receipt no.: CT-$stamp-${b.text("id").uppercase(java.util.Locale.ROOT)}")
    add("Issued: ${receiptDate(b.text("created_at"))}")
    add("Status: ${b.text("status")}")
    val d = b.optJSONObject("details") ?: JSONObject()
    if (d.text("customer_name").isNotBlank()) add("Customer: ${d.text("customer_name")}")
    add("------------------------------------")
    add(if (b.text("kind") == "Order") "ORDER DETAILS" else "SESSION DETAILS")
    add("Activity: ${d.text("activity",b.text("kind"))}")
    listOf("Resource" to "resource", "Zone" to "zone", "Game" to "game", "Team" to "team")
        .forEach { (label, key) -> if (d.text(key).isNotBlank()) add("$label: ${d.text(key)}") }
    if (d.text("starts_at").isNotBlank()) add("Starts: ${receiptDate(d.text("starts_at"))}")
    if (d.text("ends_at").isNotBlank()) add("Ends: ${receiptDate(d.text("ends_at"))}")
    if (d.optInt("duration_minutes") > 0) add("Duration: ${d.optInt("duration_minutes")} minutes")
    if (d.optInt("players") > 0) add("Players: ${d.optInt("players")}")
    if (d.optInt("hourly_rate") > 0) add("Hourly rate: ${money(d.optInt("hourly_rate"))}")
    add("------------------------------------")
    add("CHARGES")
    d.rows("items").forEach {
        add(
            "${it.optInt("qty")} x ${it.text("name")}  ${money(it.optInt("line_total",it.optInt("qty")*it.optInt("price")))}"
        )
    }
    add("Subtotal: ${money(b.optInt("subtotal"))}")
    add("Discount: ${money(b.optInt("discount"))}")
    if (d.text("promo_code").isNotBlank()) add("Promo code: ${d.text("promo_code")}")
    add("TOTAL: ${money(b.optInt("total"))}")
    add("------------------------------------")
    add("PAYMENT DETAILS")
    add("Payment: ${b.text("method")}")
    if (b.text("payment_reference").isNotBlank()) add("Reference: ${b.text("payment_reference")}")
    add(
        "${if(b.text("status")=="Void")"Points reversed" else if(b.text("environment")=="test")"Demo points" else "Points earned"}: ${b.optInt("points")}"
    )
    if (b.text("status") == "Void") add("Void reason: ${b.text("void_reason")}")
    add("------------------------------------")
    add("All session times: Malaysia (UTC+8)")
    add("Keep this receipt for your booking.")
    add("Thank you for choosing CTFLY.")
}

/** A4 receipt using the same launcher logo and document layout as the email PDF. */
fun createReceiptPdf(context: Context, bill: JSONObject): File {
    val folder = File(context.cacheDir, "receipts").apply { mkdirs() }
    val id =
        bill.text("id").filter { it.isLetterOrDigit() || it == '-' }.take(64).ifBlank { "receipt" }
    val file = File(folder, "CTFLY-$id.pdf")
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val width = 595f
    val height = 842
    val margin = 36f
    val contentWidth = width - margin * 2
    val ink = Color.rgb(20, 29, 34)
    val muted = Color.rgb(109, 123, 129)
    val line = Color.rgb(220, 227, 230)
    val lime = Color.rgb(198, 245, 107)
    val soft = Color.rgb(245, 247, 248)
    var pageNumber = 1
    var page =
        document.startPage(PdfDocument.PageInfo.Builder(width.toInt(), height, pageNumber).create())
    var y = 163f
    fun configure(size: Float, strong: Boolean, color: Int = ink) {
        paint.textSize = size
        paint.color = color
        paint.typeface =
            Typeface.create("sans-serif", if (strong) Typeface.BOLD else Typeface.NORMAL)
    }
    fun measure(value: String, size: Float = 10f, strong: Boolean = false): Float {
        configure(size, strong)
        return paint.measureText(value)
    }
    fun text(
        value: String,
        x: Float,
        top: Float,
        size: Float = 10f,
        strong: Boolean = false,
        color: Int = ink,
    ) {
        configure(size, strong, color)
        page.canvas.drawText(value, x, top - paint.fontMetrics.ascent, paint)
    }
    fun box(x: Float, top: Float, w: Float, h: Float, color: Int = soft) {
        paint.color = color
        page.canvas.drawRect(x, top, x + w, top + h, paint)
    }
    fun rule(top: Float) {
        paint.color = line
        page.canvas.drawLine(margin, top, width - margin, top, paint)
    }
    fun wrap(value: String, max: Float, size: Float = 10f, strong: Boolean = false): List<String> {
        configure(size, strong)
        return value.split('\n').flatMap { original ->
            val result = mutableListOf<String>()
            var remaining = original
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, max, null).coerceAtLeast(1)
                result.add(remaining.take(count))
                remaining = remaining.drop(count)
            }
            result.ifEmpty { listOf("") }
        }
    }
    fun footer() {
        rule(786f)
        text("THANK YOU FOR PLAYING WITH US.", margin, 795f, 8f, true)
        text(
            "Malaysia (UTC+8) / MYR  |  Keep this receipt for your booking.",
            margin,
            810f,
            8f,
            color = muted,
        )
        val value = "Page $pageNumber"
        text(value, width - margin - measure(value, 8f), 810f, 8f, color = muted)
    }
    fun ensure(blockHeight: Float) {
        if (y + blockHeight <= 768f) return
        footer()
        document.finishPage(page)
        pageNumber++
        page =
            document.startPage(
                PdfDocument.PageInfo.Builder(width.toInt(), height, pageNumber).create()
            )
        page.canvas.drawColor(Color.WHITE)
        box(0f, 0f, width, 46f, ink)
        text("CTFLY", margin, 8f, 20f, true, lime)
        text("RECEIPT CONTINUED", margin, 61f, 10f, true)
        y = 87f
    }
    fun heading(value: String) {
        ensure(26f)
        text(value.uppercase(java.util.Locale.ROOT), margin, y, 9f, true, muted)
        y += 23f
    }
    fun row(label: String, value: String) {
        val parts = wrap(value, contentWidth - 116f)
        val rowHeight = maxOf(18f, parts.size * 13f + 5f)
        ensure(rowHeight)
        text(label, margin, y, 9f, color = muted)
        parts.forEachIndexed { i, part -> text(part, margin + 116f, y + i * 13f) }
        y += rowHeight
    }
    fun amount(label: String, value: String) {
        val parts = wrap(label, contentWidth - 112f)
        val rowHeight = parts.size * 13f + 7f
        ensure(rowHeight)
        parts.forEachIndexed { i, part -> text(part, margin, y + i * 13f) }
        text(value, width - margin - measure(value, strong = true), y, strong = true)
        y += rowHeight
    }
    try {
        page.canvas.drawColor(Color.WHITE)
        box(0f, 0f, width, 96f, ink)
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.receipt_logo)
        page.canvas.drawBitmap(logo, null, RectF(margin, 21f, margin + 52f, 73f), paint)
        text("CTFLY", margin + 70f, 27f, 25f, true, Color.WHITE)
        text("GAMING CAFE / MALAYSIA", margin + 71f, 59f, 9f, color = Color.rgb(186, 204, 209))
        text(
            "0173707898",
            width - margin - measure("0173707898", 11f),
            40f,
            11f,
            color = Color.WHITE,
        )
        val title =
            when (bill.text("kind")) {
                "Order" -> "Order receipt"
                "Event" -> "Event receipt"
                else -> "Booking receipt"
            }
        text(title, margin, 124f, 25f, true)
        val test = bill.text("environment") == "test"
        val badge =
            (if (test) "TEST / " else "") + bill.text("status").uppercase(java.util.Locale.ROOT)
        val badgeWidth = maxOf(66f, measure(badge, 9f, true) + 24f)
        box(
            width - margin - badgeWidth,
            131f,
            badgeWidth,
            24f,
            if (bill.text("status") == "Void") Color.rgb(255, 232, 219) else lime,
        )
        text(badge, width - margin - badgeWidth + 12f, 138f, 9f, true)
        if (test) {
            box(margin, y, contentWidth, 24f, Color.rgb(255, 245, 222))
            text(
                "TEST RECEIPT - NO MONEY CHARGED",
                margin + 12f,
                y + 7f,
                9f,
                true,
                Color.rgb(112, 83, 33),
            )
            y += 36f
        }
        val details = bill.optJSONObject("details") ?: JSONObject()
        row(
            "Receipt no.",
            receiptLines(bill).first { it.startsWith("Receipt no.") }.substringAfter(": "),
        )
        row("Issued", receiptDate(bill.text("created_at")))
        if (details.text("customer_name").isNotBlank())
            row("Customer", details.text("customer_name"))
        y += 12f
        rule(y)
        y += 17f
        heading(if (bill.text("kind") == "Order") "Order details" else "Session details")
        val fields =
            buildList<Pair<String, String>> {
                add("Activity" to details.text("activity", bill.text("kind")))
                if (details.text("resource").isNotBlank())
                    add("Space / item" to details.text("resource"))
                if (details.text("starts_at").isNotBlank())
                    add("Start" to receiptDate(details.text("starts_at")))
                if (details.text("ends_at").isNotBlank())
                    add("End" to receiptDate(details.text("ends_at")))
                if (details.optInt("duration_minutes") > 0)
                    add("Duration" to "${details.optInt("duration_minutes")} minutes")
                if (details.optInt("players") > 0)
                    add("Players" to details.optInt("players").toString())
                if (details.text("zone").isNotBlank()) add("Zone" to details.text("zone"))
                if (details.has("hourly_rate") && !details.isNull("hourly_rate"))
                    add("Hourly rate" to "${money(details.optInt("hourly_rate"))} / hour")
                if (details.text("game").isNotBlank()) add("Game" to details.text("game"))
                if (details.text("team").isNotBlank()) add("Team" to details.text("team"))
            }
        fields.chunked(2).forEach { pair ->
            val values = pair.map { wrap(it.second, (contentWidth - 42f) / 2, 11f, true) }
            val rowHeight = values.maxOf { it.size } * 14f + 25f
            ensure(rowHeight)
            box(margin, y, contentWidth, rowHeight)
            pair.forEachIndexed { i, field ->
                val x = margin + 14f + i * contentWidth / 2
                text(field.first.uppercase(java.util.Locale.ROOT), x, y + 7f, 8f, color = muted)
                values[i].forEachIndexed { j, part -> text(part, x, y + 20f + j * 14f, 11f, true) }
            }
            y += rowHeight
        }
        y += 17f
        heading("Charges")
        val items = details.rows("items")
        if (items.isEmpty())
            amount(details.text("activity", bill.text("kind")), money(bill.optInt("subtotal")))
        else
            items.forEach {
                amount(
                    "${it.optInt("qty")} x ${it.text("name")}",
                    money(it.optInt("line_total", it.optInt("qty") * it.optInt("price"))),
                )
            }
        rule(y)
        y += 10f
        amount("Subtotal", money(bill.optInt("subtotal")))
        val discount = bill.optInt("discount")
        val promo = details.text("promo_code")
        amount(
            "Discount" + if (promo.isNotBlank()) " / $promo" else "",
            (if (discount > 0) "- " else "") + money(discount),
        )
        ensure(62f)
        y += 5f
        box(margin, y, contentWidth, 46f, ink)
        text(
            if (bill.text("status") == "Void") "VOIDED TOTAL"
            else if (test) "TEST TOTAL" else "TOTAL PAID",
            margin + 15f,
            y + 17f,
            9f,
            true,
            Color.WHITE,
        )
        val total = money(bill.optInt("total"))
        text(total, width - margin - 15f - measure(total, 24f, true), y + 9f, 24f, true, lime)
        y += 64f
        heading("Payment details")
        row("Method", bill.text("method"))
        if (bill.text("payment_reference").isNotBlank())
            row("Reference", bill.text("payment_reference"))
        row(
            if (bill.text("status") == "Void") "Points reversed"
            else if (test) "Demo points" else "Points earned",
            bill.optInt("points").toString(),
        )
        if (bill.text("status") == "Void") row("Void reason", bill.text("void_reason"))
        footer()
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
    } finally {
        document.close()
    }
    return file
}

fun shareReceiptPdf(context: Context, b: JSONObject) {
    val file = createReceiptPdf(context, b)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "CTFLY receipt")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share PDF receipt",
        )
    )
}

fun openReceiptPdf(context: Context, b: JSONObject) {
    val file = createReceiptPdf(context, b)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    } catch (_: android.content.ActivityNotFoundException) {
        shareReceiptPdf(context, b)
    }
}

@Composable
fun ReceiptContent(vm: AppViewModel) {
    val context = LocalContext.current
    val initial = runCatching { JSONObject(vm.field("receipt")) }.getOrDefault(JSONObject())
    val b = vm.rows("bills").find { it.text("id") == initial.text("id") } ?: initial
    val d = b.optJSONObject("details") ?: JSONObject()
    val test = b.text("environment") == "test"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Chip(b.text("status"))
        if (test) Chip("Test payment", Muted)
    }
    SurfaceCard {
        AppText(
            d.text("activity", b.text("kind")),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            localize = false,
        )
        if (d.text("resource").isNotBlank())
            AppText(d.text("resource"), color = Muted, fontSize = 13.sp, localize = false)
        val start = d.text("starts_at", b.text("created_at"))
        val parsedStart =
            runCatching { java.time.Instant.parse(start).atZone(CafeZone) }.getOrNull()
        if (parsedStart != null) {
            ReceiptSummaryRow(
                "Date",
                parsedStart.format(
                    java.time.format.DateTimeFormatter.ofPattern(
                        "dd MMM yyyy",
                        java.util.Locale.ENGLISH,
                    )
                ),
            )
            val end =
                runCatching { java.time.Instant.parse(d.text("ends_at")).atZone(CafeZone) }
                    .getOrNull()
            val timeFormat =
                java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)
            val endText =
                end?.let {
                    if (it.toLocalDate() == parsedStart.toLocalDate()) it.format(timeFormat)
                    else
                        it.format(
                            java.time.format.DateTimeFormatter.ofPattern(
                                "dd MMM · h:mm a",
                                java.util.Locale.ENGLISH,
                            )
                        )
                }
            ReceiptSummaryRow(
                "Time",
                parsedStart.format(timeFormat) + (endText?.let { " - $it" } ?: ""),
            )
        }
        if (d.optInt("duration_minutes") > 0)
            ReceiptSummaryRow("Duration", tr("${d.optInt("duration_minutes")} minutes"))
        if (b.text("kind") == "Order")
            d.rows("items").forEach {
                ReceiptSummaryRow(
                    "${it.optInt("qty")} x ${it.text("name")}",
                    money(it.optInt("line_total", it.optInt("qty") * it.optInt("price"))),
                    localizeLabel = false,
                )
            }
        HorizontalDivider(color = Muted.copy(alpha = .2f))
        AppText(
            if (b.text("status") == "Void") "Voided total"
            else if (test) "Test total" else "Total paid",
            color = Muted,
            fontSize = 12.sp,
        )
        AppText(
            money(b.optInt("total")),
            color = Lime,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            localize = false,
        )
        if (test)
            AppText("Test payment. No real money was charged.", color = Muted, fontSize = 12.sp)
    }
    val job = vm.rows("receipt_emails").find { it.text("bill_id") == b.text("id") }
    val mailStatus = job?.text("status").orEmpty()
    AppText(
        when (mailStatus) {
            "Sent" -> "Full receipt sent to your registered email."
            "Sending" -> "Your full receipt is being emailed."
            "Queued" -> "Your full receipt is queued for email."
            "Unknown" -> "Email delivery is uncertain. Ask staff to check before sending again."
            "Failed" -> "Email could not be sent. You can retry below."
            else -> "Email receipt is unavailable. You can still view the full PDF."
        },
        color = if (mailStatus == "Sent") Lime else Muted,
        fontSize = 12.sp,
    )
    OutlinedButton(
        onClick = {
            runCatching { openReceiptPdf(context, b) }
                .onFailure { vm.invalid(it.message ?: "PDF could not be opened.") }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppText("View full receipt (PDF)")
    }
    TextButton(
        onClick = {
            runCatching { shareReceiptPdf(context, b) }
                .onFailure { vm.invalid(it.message ?: "PDF could not be shared.") }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppText("Share PDF receipt")
    }
    if (mailStatus in listOf("", "Failed", "NotConfigured"))
        TextButton(
            onClick = { vm.action("receipt_email", obj("id" to b.text("id"))) },
            enabled = !vm.busy,
        ) {
            AppText("Send to registered email")
        }
}

@Composable
private fun ReceiptSummaryRow(label: String, value: String, localizeLabel: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(
            label,
            Modifier.weight(.4f),
            color = Muted,
            fontSize = 13.sp,
            localize = localizeLabel,
        )
        AppText(
            value,
            Modifier.weight(.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            localize = false,
        )
    }
}
