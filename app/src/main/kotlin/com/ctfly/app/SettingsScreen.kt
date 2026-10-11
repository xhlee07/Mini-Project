@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ctfly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

fun AppViewModel.openSettings() {
    set("settingsReturn", field("page", if (crew) "Operations" else "Discover"))
    set("page", "Settings")
}

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val preferences = LocalAppPreferences.current
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, tr("Back"))
            }
            AppText("Settings", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SurfaceCard {
                    SectionTitle("Language", "Changes immediately and stays on this device.")
                    listOf("en" to "English", "zh" to "简体中文", "ms" to "Bahasa Melayu").forEach {
                        (id, name) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("language-$id")
                                .selectable(
                                    preferences.language == id,
                                    onClick = { preferences.chooseLanguage(id) },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(preferences.language == id, onClick = null)
                            AppText(name, Modifier.padding(start = 12.dp), localize = false)
                        }
                    }
                }
            }
            item {
                SurfaceCard {
                    SectionTitle("Background color", "Choose a comfortable look for the app.")
                    AppPreferences.palettes.forEach { (id, palette) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .testTag("background-$id")
                                .selectable(
                                    preferences.background == id,
                                    onClick = { preferences.chooseBackground(id) },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(32.dp)
                                    .background(palette.background, RoundedCornerShape(10.dp))
                                    .border(1.dp, Muted, RoundedCornerShape(10.dp))
                            )
                            AppText(palette.name, Modifier.weight(1f).padding(start = 12.dp))
                            RadioButton(preferences.background == id, onClick = null)
                        }
                    }
                }
            }
            if (!BuildConfig.STAFF_APP) {
                item {
                    SurfaceCard {
                        SectionTitle("App feedback", "Tell us what works and what we can improve.")
                        if (!vm.hasAccount) {
                            AppText("Sign in to send feedback.", color = Muted)
                            Button(
                                onClick = {
                                    vm.set("explore", "false")
                                    vm.set("page", "Discover")
                                }
                            ) {
                                AppText("Sign in")
                            }
                        } else {
                            Select(
                                "Category",
                                vm.field("feedbackCategory", "Suggestion"),
                                listOf(
                                    "Issue" to "Issue",
                                    "Suggestion" to "Suggestion",
                                    "Other" to "Other",
                                ),
                                { vm.set("feedbackCategory", it) },
                            )
                            Select(
                                "App rating",
                                vm.field("feedbackRating", "5"),
                                (1..5).map { it.toString() to "$it / 5" },
                                { vm.set("feedbackRating", it) },
                            )
                            Input(
                                "Feedback title",
                                vm.field("feedbackTitle"),
                                { vm.set("feedbackTitle", it.take(120)) },
                            )
                            Input(
                                "Your feedback",
                                vm.field("feedbackBody"),
                                { vm.set("feedbackBody", it.take(2000)) },
                                multiline = true,
                            )
                            AppText(
                                "${vm.field("feedbackBody").length} / 2000",
                                color = Muted,
                                fontSize = 12.sp,
                                localize = false,
                            )
                            AppText(
                                "Your name and feedback will be visible to our staff.",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                            val valid =
                                vm.field("feedbackTitle").trim().length in 3..120 &&
                                    vm.field("feedbackBody").trim().length in 10..2000
                            Button(
                                onClick = {
                                    vm.action(
                                        "feedback",
                                        obj(
                                            "title" to vm.field("feedbackTitle").trim(),
                                            "body" to vm.field("feedbackBody").trim(),
                                            "category" to
                                                vm.field("feedbackCategory", "Suggestion"),
                                            "rating" to vm.field("feedbackRating", "5").toInt(),
                                        ),
                                    ) {
                                        vm.set("feedbackTitle", "")
                                        vm.set("feedbackBody", "")
                                    }
                                },
                                enabled = !vm.busy && valid,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .testTag("sendFeedback"),
                            ) {
                                if (vm.busy)
                                    CircularProgressIndicator(
                                        Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                else AppText("Send feedback")
                            }
                            if (!valid)
                                AppText(
                                    "Title: 3–120 characters. Feedback: 10–2000 characters.",
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                            if (vm.loadError.isNotBlank())
                                AppText(vm.loadError, color = Orange, fontSize = 12.sp)
                        }
                    }
                }
                if (vm.hasAccount && vm.rows("feedback").isNotEmpty()) {
                    item {
                        SectionTitle(
                            "My feedback",
                            "See whether the team has reviewed your feedback.",
                        )
                    }
                    items(vm.rows("feedback"), key = { it.text("id") }) { feedback ->
                        FeedbackCard(feedback)
                    }
                }
            } else
                item {
                    SurfaceCard {
                        SectionTitle(
                            "Customer feedback",
                            "Read feedback submitted by club members.",
                        )
                        Button(onClick = { vm.set("page", "Feedback") }) {
                            AppText("Open feedback inbox")
                        }
                    }
                }
        }
    }
}

@Composable
private fun FeedbackCard(feedback: JSONObject, vm: AppViewModel? = null) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                feedback.text("title"),
                Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                localize = false,
            )
            Chip(feedback.text("status"), if (feedback.text("status") == "Resolved") Aqua else Lime)
        }
        if (vm != null) AppText(feedback.text("customer_name"), color = Muted, localize = false)
        AppText(
            "${feedback.text("category")} · ${feedback.optInt("rating")} / 5",
            color = Muted,
            fontSize = 12.sp,
        )
        AppText(feedback.text("body"), localize = false)
        AppText(dateLabel(feedback.text("created_at")), color = Muted, fontSize = 12.sp)
        if (vm != null && feedback.text("status") != "Resolved")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (feedback.text("status") == "New")
                    OutlinedButton(
                        onClick = {
                            vm.action(
                                "feedback_status",
                                obj("id" to feedback.text("id"), "status" to "Reviewed"),
                            )
                        },
                        enabled = !vm.busy,
                        modifier = Modifier.testTag("reviewFeedback-${feedback.text("id")}"),
                    ) {
                        AppText("Mark reviewed")
                    }
                Button(
                    onClick = {
                        vm.action(
                            "feedback_status",
                            obj("id" to feedback.text("id"), "status" to "Resolved"),
                        )
                    },
                    enabled = !vm.busy,
                    modifier = Modifier.testTag("resolveFeedback-${feedback.text("id")}"),
                ) {
                    AppText("Mark resolved")
                }
            }
    }
}

@Composable
fun FeedbackInboxScreen(vm: AppViewModel) {
    val filter = vm.field("feedbackFilter", "All")
    val feedback = vm.rows("feedback").filter { filter == "All" || it.text("status") == filter }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SectionTitle("Customer feedback", "Suggestions and issues from the app.", "Refresh") {
                vm.refresh()
            }
            TextButton(onClick = { vm.set("page", "Operations") }) { AppText("Back to operations") }
        }
        item {
            FilterRow(listOf("All", "New", "Reviewed", "Resolved"), filter) {
                vm.set("feedbackFilter", it)
            }
        }
        items(feedback, key = { it.text("id") }) { FeedbackCard(it, vm) }
        if (feedback.isEmpty())
            item {
                EmptyState("No feedback here yet", "New submissions will appear after refresh.")
            }
    }
}
