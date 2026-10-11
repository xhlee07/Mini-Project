package com.ctfly.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun EventPoster(url: String, modifier: Modifier = Modifier) {
    val bitmap by
        produceState<Bitmap?>(null, url) {
            if (
                url.startsWith(
                    BuildConfig.SUPABASE_URL + "/storage/v1/object/public/event-posters/"
                )
            )
                value =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                val c = URL(url).openConnection() as HttpURLConnection
                                try {
                                    c.connectTimeout = 8000
                                    c.readTimeout = 10000
                                    c.instanceFollowRedirects = false
                                    require(c.responseCode == 200)
                                    val bytes =
                                        c.inputStream.use { input ->
                                            ByteArrayOutputStream().use { out ->
                                                val buffer = ByteArray(8192)
                                                var read = input.read(buffer)
                                                while (read != -1) {
                                                    require(out.size() + read <= 4194304)
                                                    out.write(buffer, 0, read)
                                                    read = input.read(buffer)
                                                }
                                                out.toByteArray()
                                            }
                                        }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } finally {
                                    c.disconnect()
                                }
                            }
                            .getOrNull()
                    }
        }
    if (bitmap == null) Photo("tournament_banner", modifier)
    else
        Image(
            bitmap!!.asImageBitmap(),
            "Competition poster",
            modifier,
            contentScale = ContentScale.Crop,
        )
}

@Composable
fun PosterPicker(vm: AppViewModel, url: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        val bytes =
                            withContext(Dispatchers.IO) {
                                val bounds =
                                    BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                context.contentResolver.openInputStream(uri)?.use {
                                    BitmapFactory.decodeStream(it, null, bounds)
                                }
                                require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                                    "Image: choose a readable photo."
                                }
                                var sample = 1
                                while (
                                    maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200
                                ) sample *= 2
                                val bitmap =
                                    context.contentResolver.openInputStream(uri)?.use {
                                        BitmapFactory.decodeStream(
                                            it,
                                            null,
                                            BitmapFactory.Options().apply { inSampleSize = sample },
                                        )
                                    } ?: error("Image could not be opened.")
                                try {
                                    ByteArrayOutputStream().use {
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
                                        it.toByteArray()
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        vm.uploadEventPoster(bytes)
                    } catch (e: Exception) {
                        vm.invalid(e.message ?: "Image could not be uploaded.")
                    }
                }
        }
    EventPoster(url, Modifier.fillMaxWidth().height(160.dp))
    AppText(if (url.isBlank()) "Default competition poster" else "Uploaded poster")
    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !vm.busy) {
        AppText("Upload image")
    }
    if (url.isNotBlank())
        TextButton(
            onClick = {
                vm.set(
                    "formData",
                    JSONObject(vm.field("formData", "{}")).put("poster_url", "").toString(),
                )
            }
        ) {
            AppText("Use default poster")
        }
}

@Composable
fun EventInformation(e: JSONObject) {
    if (e.text("description").isNotBlank()) AppText(e.text("description"))
    AppText("${dateLabel(e.text("starts_at"))} → ${dateLabel(e.text("ends_at"))}", color = Muted)
    if (e.text("registration_closes_at").isNotBlank())
        AppText(
            "Registration closes: ${dateLabel(e.text("registration_closes_at"))}",
            color = Muted,
        )
    listOf(
            "First prize" to "prize_first",
            "Second prize" to "prize_second",
            "Third prize" to "prize_third",
            "Consolation prize" to "prize_consolation",
        )
        .forEach { (label, key) -> AppText("$label: ${e.text(key,"To be announced")}") }
}

@Composable
fun StaffEventsScreen(vm: AppViewModel) {
    val tab = vm.field("staffEventsTab", "Competitions")
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppText("Competitions & offers", style = MaterialTheme.typography.headlineMedium)
                FilterRow(listOf("Competitions", "Promo codes"), tab) {
                    vm.set("staffEventsTab", it)
                }
                Button(
                    onClick = {
                        vm.openForm(if (tab == "Competitions") "save_event" else "save_promotion")
                    }
                ) {
                    AppText(
                        if (tab == "Competitions") "Create competition" else "Create promo code"
                    )
                }
            }
        }
        if (tab == "Competitions") {
            items(vm.rows("events"), key = { it.text("id") }) { e ->
                SurfaceCard {
                    EventPoster(e.text("poster_url"), Modifier.fillMaxWidth().height(160.dp))
                    Chip(e.text("status"), Aqua)
                    AppText(e.text("name"), style = MaterialTheme.typography.titleLarge)
                    AppText(
                        "${e.text("game")} · ${money(e.optInt("fee"))} / team · ${e.optInt("capacity")} slots"
                    )
                    EventInformation(e)
                    if (e.text("status") !in listOf("Running", "Completed"))
                        OutlinedButton(
                            onClick = {
                                vm.openForm(
                                    "save_event",
                                    JSONObject(e.toString())
                                        .put(
                                            "fee_rm",
                                            "%.2f"
                                                .format(
                                                    java.util.Locale.US,
                                                    e.optInt("fee") / 100.0,
                                                ),
                                        ),
                                )
                            }
                        ) {
                            AppText("Edit competition")
                        }
                }
            }
            if (vm.rows("events").isEmpty())
                item {
                    EmptyState("No competitions yet", "Create a draft or publish your next event.")
                }
        } else {
            items(vm.rows("promotions"), key = { it.text("id") }) { p ->
                SurfaceCard {
                    AppText(
                        p.text("code"),
                        fontWeight = FontWeight.Bold,
                        color = Lime,
                        localize = false,
                    )
                    AppText(p.text("title"))
                    AppText(
                        "${p.text("scope")} · " +
                            when (p.text("benefit")) {
                                "Free" -> "Free"
                                "Fixed" -> money(p.optInt("amount"))
                                else -> "${p.optInt("amount")}% off"
                            }
                    )
                    AppText(
                        "${dateLabel(p.text("starts_at"))} → ${dateLabel(p.text("ends_at"))}",
                        color = Muted,
                    )
                    AppText(
                        "${p.optInt("used")} / ${p.optInt("usage_limit")} used · ${p.optInt("per_user_limit")} per customer"
                    )
                    if (p.text("target_user").isNotBlank())
                        AppText("Customer: ${vm.person(p.text("target_user"))}")
                    AppText(if (p.optBoolean("active")) "Enabled" else "Disabled", color = Muted)
                    OutlinedButton(
                        onClick = {
                            vm.openForm(
                                "save_promotion",
                                JSONObject(p.toString())
                                    .put("percent", p.optInt("amount"))
                                    .put(
                                        "amount_rm",
                                        "%.2f"
                                            .format(java.util.Locale.US, p.optInt("amount") / 100.0),
                                    ),
                            )
                        }
                    ) {
                        AppText("Edit promo code")
                    }
                }
            }
            if (vm.rows("promotions").isEmpty())
                item {
                    EmptyState(
                        "No promo codes yet",
                        "Create an offer and share its code with customers.",
                    )
                }
        }
    }
}
