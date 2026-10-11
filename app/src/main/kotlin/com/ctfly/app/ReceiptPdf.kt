package com.ctfly.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    if (b.text("environment") == "test") {
        add("TEST RECEIPT - NO MONEY CHARGED")
        add("School project payment simulation")
    }
    add("Receipt: ${b.text("id")}")
    add("Issued: ${receiptDate(b.text("created_at"))}")
    add("Status: ${b.text("status")}")
    val d = b.optJSONObject("details") ?: JSONObject()
    if (d.text("customer_name").isNotBlank()) add("Customer: ${d.text("customer_name")}")
    add("Purchase: ${d.text("activity",b.text("kind"))}")
    listOf("Resource" to "resource", "Zone" to "zone", "Game" to "game", "Team" to "team")
        .forEach { (label, key) -> if (d.text(key).isNotBlank()) add("$label: ${d.text(key)}") }
    if (d.text("starts_at").isNotBlank()) add("Starts: ${receiptDate(d.text("starts_at"))}")
    if (d.text("ends_at").isNotBlank()) add("Ends: ${receiptDate(d.text("ends_at"))}")
    if (d.optInt("duration_minutes") > 0) add("Duration: ${d.optInt("duration_minutes")} minutes")
    if (d.optInt("players") > 0) add("Players: ${d.optInt("players")}")
    if (d.optInt("hourly_rate") > 0) add("Hourly rate: ${money(d.optInt("hourly_rate"))}")
    add("------------------------------------")
    d.rows("items").forEach {
        add(
            "${it.optInt("qty")} x ${it.text("name")}  ${money(it.optInt("line_total",it.optInt("price")))}"
        )
    }
    add("Subtotal: ${money(b.optInt("subtotal"))}")
    add("Discount: ${money(b.optInt("discount"))}")
    if (d.text("promo_code").isNotBlank()) add("Promo code: ${d.text("promo_code")}")
    add("TOTAL: ${money(b.optInt("total"))}")
    add("Payment: ${b.text("method")}")
    if (b.text("payment_reference").isNotBlank()) add("Reference: ${b.text("payment_reference")}")
    add(
        "${if(b.text("environment")=="test")"Demo points" else "Points earned"}: ${b.optInt("points")}"
    )
    if (b.text("status") == "Void") add("Void reason: ${b.text("void_reason")}")
    add("------------------------------------")
    add("All session times: Malaysia (UTC+8)")
    add("Keep this receipt for your booking.")
    add("Thank you for choosing CTFLY.")
}

/** Native system fonts preserve customer names in Chinese and other configured languages. */
fun createReceiptPdf(context: Context, bill: JSONObject): File {
    val folder = File(context.cacheDir, "receipts").apply { mkdirs() }
    val id =
        bill.text("id").filter { it.isLetterOrDigit() || it == '-' }.take(64).ifBlank { "receipt" }
    val file = File(folder, "CTFLY-$id.pdf")
    val document = PdfDocument()
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    val lines = mutableListOf<String>()
    receiptLines(bill).forEach { text ->
        var remainder = text
        while (remainder.isNotEmpty()) {
            val count = paint.breakText(remainder, true, 276f, null).coerceAtLeast(1)
            lines.add(remainder.take(count))
            remainder = remainder.drop(count)
        }
    }
    try {
        lines.chunked(46).forEachIndexed { index, pageLines ->
            val page =
                document.startPage(
                    PdfDocument.PageInfo.Builder(
                            320,
                            Math.max(240, 72 + pageLines.size * 15),
                            index + 1,
                        )
                        .create()
                )
            page.canvas.drawColor(Color.WHITE)
            var y = 32f
            pageLines.forEachIndexed { i, text ->
                paint.typeface =
                    Typeface.create(
                        "sans-serif",
                        if (text.startsWith("TOTAL:") || (index == 0 && i == 0)) Typeface.BOLD
                        else Typeface.NORMAL,
                    )
                page.canvas.drawText(text, 22f, y, paint)
                y += 15
            }
            paint.textSize = 8f
            page.canvas.drawText("Page ${index+1}", 22f, y + 16, paint)
            paint.textSize = 10f
            document.finishPage(page)
        }
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
    Chip(b.text("status"))
    receiptLines(b).forEach { AppText(it, localize = false) }
    val job = vm.rows("receipt_emails").find { it.text("bill_id") == b.text("id") }
    val mailStatus = job?.text("status").orEmpty()
    AppText(
        when (mailStatus) {
            "Sent" -> "Receipt email sent to your registered email."
            "Sending" -> "Receipt email is being sent."
            "Queued" -> "Receipt email is queued."
            "Unknown" -> "Email delivery is uncertain. Ask staff to check before sending again."
            "Failed" -> "Email could not be sent. You can retry below."
            else -> "Email delivery needs the cafe's mail service configuration."
        },
        color = if (mailStatus == "Sent") Lime else Muted,
    )
    Button(
        onClick = {
            runCatching { openReceiptPdf(context, b) }
                .onFailure { vm.invalid(it.message ?: "PDF could not be opened.") }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppText("Open PDF receipt")
    }
    OutlinedButton(
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
