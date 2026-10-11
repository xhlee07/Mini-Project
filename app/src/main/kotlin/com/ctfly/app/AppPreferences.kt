package com.ctfly.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

class AppPreferences(context: Context) {
    private val store = context.getSharedPreferences("ctfly_appearance", Context.MODE_PRIVATE)
    var language by
        mutableStateOf(
            store.getString("language", "en").orEmpty().let {
                if (it in listOf("en", "zh", "ms")) it else "en"
            }
        )
        private set

    var background by
        mutableStateOf(
            store.getString("background", "charcoal").orEmpty().let {
                if (it in palettes) it else "charcoal"
            }
        )
        private set

    fun chooseLanguage(value: String) {
        require(value in listOf("en", "zh", "ms"))
        language = value
        store.edit().putString("language", value).apply()
    }

    fun chooseBackground(value: String) {
        require(value in palettes)
        background = value
        store.edit().putString("background", value).apply()
    }

    data class Palette(val name: String, val background: Color, val panel: Color)

    companion object {
        val palettes =
            linkedMapOf(
                "charcoal" to Palette("Charcoal", Color(0xFF0B1013), Color(0xFF151D22)),
                "navy" to Palette("Midnight blue", Color(0xFF0B1731), Color(0xFF162848)),
                "forest" to Palette("Forest green", Color(0xFF0A241E), Color(0xFF153B30)),
                "plum" to Palette("Plum purple", Color(0xFF24132C), Color(0xFF3A2245)),
            )
    }
}

val LocalAppPreferences =
    staticCompositionLocalOf<AppPreferences> { error("Missing app preferences") }
val LocalAppLanguage = staticCompositionLocalOf { "en" }

@Composable fun tr(text: String): String = AppStrings.translate(text, LocalAppLanguage.current)

@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) {
    androidx.compose.material3.Icon(imageVector, contentDescription?.let { tr(it) }, modifier, tint)
}

@Composable
fun AppSnackbarHost(
    state: androidx.compose.material3.SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.SnackbarHost(state, modifier) { data ->
        androidx.compose.material3.Snackbar { AppText(data.visuals.message) }
    }
}

/** Translates presentation only: route IDs, action names and database values stay canonical. */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    localize: Boolean = true,
) {
    androidx.compose.material3.Text(
        if (localize) tr(text) else text,
        modifier,
        color,
        fontSize,
        fontStyle,
        fontWeight,
        fontFamily,
        letterSpacing,
        textDecoration,
        textAlign,
        lineHeight,
        overflow,
        softWrap,
        maxLines,
        minLines,
        style = style,
    )
}

object AppStrings {
    // Shared assets are packaged in both APKs. No translation service or network is used.
    private var entries: Map<String, Pair<String, String>> = emptyMap()
    private var lowercaseEntries: Map<String, Pair<String, String>> = emptyMap()
    private var templates: List<Triple<Regex, Pair<String, String>, Boolean>> = emptyList()

    @Synchronized
    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        context.assets.open("languages.tsv").bufferedReader().useLines { lines -> install(lines) }
    }

    internal fun install(lines: Sequence<String>) {
        entries =
            lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val fields = line.split('\t')
                    require(fields.size == 3) { "Invalid language entry: ${fields.first()}" }
                    fields[0].replace("\\n", "\n") to
                        (fields[1].replace("\\n", "\n") to fields[2].replace("\\n", "\n"))
                }
                .toMap()
        lowercaseEntries = entries.mapKeys { it.key.lowercase(java.util.Locale.ROOT) }
        templates =
            entries
                .filterKeys { it.contains("{0}") }
                .map { (key, value) ->
                    val regex =
                        key.split(Regex("\\{\\d+\\}")).joinToString("(.+?)") { Regex.escape(it) }
                    Triple(
                        Regex("^$regex$"),
                        value,
                        key !in
                            setOf(
                                "Hey, {0}.",
                                "Your team: {0}",
                                "Deliver to {0}",
                                "Winner: {0}",
                                "{0} wins",
                                "{0} will advance. Results are final.",
                            ),
                    )
                }
    }

    fun translate(text: String, language: String): String {
        if (language == "en") return text
        val direct = entries[text] ?: lowercaseEntries[text.lowercase(java.util.Locale.ROOT)]
        if (direct != null) return if (language == "zh") direct.first else direct.second
        for ((pattern, value, translateValues) in templates) pattern.matchEntire(text)?.let { match
            ->
            var translated = if (language == "zh") value.first else value.second
            match.groupValues.drop(1).forEachIndexed { index, part ->
                translated =
                    translated.replace(
                        "{$index}",
                        if (translateValues) translate(part, language) else part,
                    )
            }
            return translated
        }
        val templates =
            listOf(
                "Welcome back, " to ("欢迎回来，" to "Selamat kembali, "),
                "Welcome, " to ("欢迎，" to "Selamat datang, "),
                "Voucher " to ("兑换券 " to "Baucar "),
            )
        for ((prefix, translated) in templates) if (text.startsWith(prefix))
            return (if (language == "zh") translated.first else translated.second) +
                text.removePrefix(prefix)
        val units =
            listOf(
                Regex("^(\\d+) minutes$") to ("{0} 分钟" to "{0} minit"),
                Regex("^(\\d+) players?$") to ("{0} 人" to "{0} pemain"),
                Regex("^(\\d+) player\\(s\\)$") to ("{0} 人" to "{0} pemain"),
                Regex("^(\\d+) points$") to ("{0} 积分" to "{0} mata"),
                Regex("^(\\d+) pts$") to ("{0} 积分" to "{0} mata"),
                Regex("^(.+) member$") to ("{0}会员" to "Ahli {0}"),
            )
        for ((pattern, translated) in units) pattern.matchEntire(text)?.let { m ->
            return (if (language == "zh") translated.first else translated.second).replace(
                "{0}",
                translate(m.groupValues[1], language),
            )
        }
        // Compound status/category labels are localized without translating unknown names.
        if (text.contains(" · "))
            return text.split(" · ").joinToString(" · ") { translate(it, language) }
        Regex("^((?:L[12]|G|\\d+)) (.+)$").matchEntire(text)?.let { match ->
            val part = translate(match.groupValues[2], language)
            if (part != match.groupValues[2]) return "${match.groupValues[1]} $part"
        }
        if (text.contains('\n'))
            return text.split('\n').joinToString("\n") { translate(it, language) }
        return text
    }
}
