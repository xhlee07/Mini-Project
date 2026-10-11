@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ctfly.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Ink: Color
    @Composable
    get() = AppPreferences.palettes.getValue(LocalAppPreferences.current.background).background
val Panel: Color
    @Composable
    get() = AppPreferences.palettes.getValue(LocalAppPreferences.current.background).panel
val Muted = Color(0xFF9DAEB8)
val Lime = Color(0xFFC6F56B)
val Violet = Color(0xFFB5A0FF)
val Aqua = Color(0xFF7EDFCC)
val Orange = Color(0xFFFFBA85)

@Composable
fun CtflyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember {
        AppStrings.load(context)
        AppPreferences(context)
    }
    CompositionLocalProvider(
        LocalAppPreferences provides preferences,
        LocalAppLanguage provides preferences.language,
    ) {
        MaterialTheme(
            colorScheme =
                darkColorScheme(
                    primary = if (BuildConfig.STAFF_APP) Aqua else Lime,
                    onPrimary = Ink,
                    secondary = Violet,
                    tertiary = Aqua,
                    background = Ink,
                    surface = Panel,
                    onSurface = Color(0xFFF4F7F8),
                    onSurfaceVariant = Muted,
                    outline = Color(0xFF39464D),
                ),
            typography =
                Typography(
                    headlineLarge =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 36.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    headlineMedium =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    titleLarge =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 21.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                ),
            content = { Surface(color = Ink, contentColor = Color(0xFFF4F7F8)) { content() } },
        )
    }
}

@Composable
fun Photo(name: String, modifier: Modifier = Modifier, description: String? = null) {
    if (name == "game_card") {
        Box(
            modifier.background(Brush.linearGradient(listOf(Color(0xFF243C54), Ink))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(
                    Icons.Outlined.SportsEsports,
                    null,
                    tint = Lime,
                    modifier = Modifier.size(38.dp),
                )
                AppText(
                    "GAME CREDIT",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White,
                )
            }
        }
        return
    }
    val context = LocalContext.current
    val id =
        when (name) {
            "neon_arena" -> R.drawable.neon_arena
            "neon_cafe" -> R.drawable.neon_cafe
            "pool_club" -> R.drawable.pool_club
            "kart_circuit" -> R.drawable.kart_circuit
            "tournament_banner" -> R.drawable.tournament_banner
            "iced_coffee" -> R.drawable.iced_coffee
            "iced_lemon_tea" -> R.drawable.iced_lemon_tea
            "energy_drink" -> R.drawable.energy_drink
            "mineral_water" -> R.drawable.mineral_water
            "chicken_burger" -> R.drawable.chicken_burger
            "fries" -> R.drawable.fries
            "cup_noodles" -> R.drawable.cup_noodles
            "potato_chips" -> R.drawable.potato_chips
            else -> 0
        }
    val bitmap by
        produceState<ImageBitmap?>(null, id) {
            value =
                withContext(Dispatchers.IO) {
                    if (id == 0) null
                    else {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeResource(context.resources, id, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 1000) sample *= 2
                        BitmapFactory.decodeResource(
                                context.resources,
                                id,
                                BitmapFactory.Options().apply { inSampleSize = sample },
                            )
                            ?.asImageBitmap()
                    }
                }
        }
    if (bitmap != null) Image(bitmap!!, description, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Brush.linearGradient(listOf(Panel, Color(0xFF34433A)))))
}

@Composable
fun Brand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(32.dp)) {
            val p =
                Path().apply {
                    moveTo(0f, size.height * .15f)
                    lineTo(size.width * .43f, size.height * .4f)
                    lineTo(size.width, size.height * .05f)
                    lineTo(size.width * .65f, size.height * .7f)
                    lineTo(size.width * .35f, size.height)
                    lineTo(size.width * .3f, size.height * .55f)
                    close()
                }
            drawPath(p, if (BuildConfig.STAFF_APP) Aqua else Lime)
        }
        if (!compact)
            Column {
                AppText(
                    if (BuildConfig.STAFF_APP) "Staff CTFLY" else "CTFLY",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                if (!BuildConfig.STAFF_APP) AppText("Gaming Cafe", fontSize = 14.sp, color = Muted)
            }
    }
}

@Composable
fun Eyebrow(text: String, color: Color = Lime) {
    AppText(
        text.uppercase(),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
    )
}

@Composable
fun SectionTitle(
    title: String,
    caption: String = "",
    action: String = "",
    onAction: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = MaterialTheme.typography.titleLarge)
            if (caption.isNotEmpty()) AppText(caption, color = Muted, fontSize = 13.sp)
        }
        if (action.isNotEmpty()) TextButton(onClick = onAction) { AppText(action) }
    }
}

@Composable
fun Chip(text: String, color: Color = Lime) {
    AppText(
        text,
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = .12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun FilterRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { label ->
            FilterChip(selected == label, onClick = { onSelect(label) }, label = { AppText(label) })
        }
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIcon(Icons.Outlined.AutoAwesome, null, Modifier.size(32.dp), tint = Lime)
        AppText(title, fontWeight = FontWeight.Bold)
        AppText(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
fun Hero(
    kicker: String,
    title: String,
    subtitle: String,
    image: String = "neon_arena",
    accent: Color = Lime,
    button: String = "",
    onClick: () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth().heightIn(min = 265.dp).clip(RoundedCornerShape(26.dp))) {
        Photo(image, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.horizontalGradient(listOf(Ink.copy(alpha = .94f), Ink.copy(alpha = .25f)))
                )
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .65f)))
                )
        )
        Column(
            Modifier.padding(26.dp).widthIn(max = 430.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Eyebrow(kicker, accent)
            AppText(title, style = MaterialTheme.typography.headlineLarge)
            AppText(subtitle, color = Color(0xFFD9E3E8), fontSize = 14.sp)
            if (button.isNotEmpty())
                Button(
                    onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    AppText(button, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    AppIcon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
                }
        }
    }
}

@Composable
fun Stat(value: String, label: String, accent: Color = Lime, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = .08f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppText(value, color = accent, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        AppText(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun Input(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    password: Boolean = false,
    numeric: Boolean = false,
    decimal: Boolean = false,
    multiline: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value,
        onChange,
        label = { AppText(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        shape = RoundedCornerShape(14.dp),
        visualTransformation =
            if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType =
                    when {
                        password -> KeyboardType.Password
                        decimal -> KeyboardType.Decimal
                        numeric -> KeyboardType.Number
                        label.contains("Email") -> KeyboardType.Email
                        else -> KeyboardType.Text
                    }
            ),
        trailingIcon =
            if (password) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        AppIcon(
                            if (visible) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                            if (visible) "Hide password" else "Show password",
                        )
                    }
                }
            } else null,
    )
}

@Composable
fun Select(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                AppText(label, color = Muted, fontSize = 10.sp, maxLines = 1, softWrap = false)
                AppText(
                    options.find { it.first == value }?.second ?: value.ifBlank { "Choose" },
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.width(6.dp))
            AppIcon(Icons.Outlined.ExpandMore, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { AppText(text) },
                    onClick = {
                        onChange(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun DateFields(label: String, value: String, onChange: (String) -> Unit, birth: Boolean = false) {
    val today = LocalDate.now(CafeZone)
    val parsed =
        runCatching { LocalDate.parse(value) }
            .getOrDefault(if (birth) today.minusYears(18) else today)
    fun change(y: Int = parsed.year, m: Int = parsed.monthValue, d: Int = parsed.dayOfMonth) {
        onChange(
            LocalDate.of(y, m, d.coerceAtMost(java.time.YearMonth.of(y, m).lengthOfMonth()))
                .toString()
        )
    }
    AppText(label, color = Muted, fontSize = 12.sp)
    val years =
        if (birth) (today.year - 120..today.year).reversed()
        else (today.year..today.year + 1).toList()
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoRows = maxWidth < 300.dp || fontScale > 1.4f
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Select(
                    "Day",
                    parsed.dayOfMonth.toString(),
                    (1..parsed.lengthOfMonth()).map { it.toString() to it.toString() },
                    { change(d = it.toInt()) },
                    Modifier.weight(1f),
                )
                Select(
                    "Month",
                    parsed.monthValue.toString(),
                    (1..12).map { it.toString() to java.time.Month.of(it).name.take(3) },
                    { change(m = it.toInt()) },
                    Modifier.weight(1f),
                )
                if (!twoRows)
                    Select(
                        "Year",
                        parsed.year.toString(),
                        years.map { it.toString() to it.toString() },
                        { change(y = it.toInt()) },
                        Modifier.weight(1f),
                    )
            }
            if (twoRows)
                Select(
                    "Year",
                    parsed.year.toString(),
                    years.map { it.toString() to it.toString() },
                    { change(y = it.toInt()) },
                    Modifier.fillMaxWidth(),
                )
        }
    }
    if (value.isBlank())
        TextButton(onClick = { onChange(parsed.toString()) }) { AppText("Use ${parsed}") }
}

@Composable
fun ActivityArt(kind: String, modifier: Modifier = Modifier) {
    val ink = Ink
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A2351), Panel))),
        contentAlignment = Alignment.Center,
    ) {
        if (kind in listOf("Pool", "Snooker"))
            Canvas(Modifier.fillMaxSize().padding(25.dp)) {
                drawRoundRect(
                    Color(0xFF56603C),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                )
                drawRoundRect(
                    Color(0xFF176354),
                    topLeft = androidx.compose.ui.geometry.Offset(8f, 8f),
                    size = androidx.compose.ui.geometry.Size(size.width - 16, size.height - 16),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                )
                for (x in listOf(10f, size.width / 2, size.width - 10)) for (y in
                    listOf(10f, size.height - 10)) drawCircle(
                    ink,
                    7f,
                    androidx.compose.ui.geometry.Offset(x, y),
                )
                drawCircle(
                    Color.White,
                    7f,
                    androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .55f),
                )
                drawCircle(
                    Orange,
                    7f,
                    androidx.compose.ui.geometry.Offset(size.width * .65f, size.height * .45f),
                )
                drawLine(
                    Color(0xFFE5CCA3),
                    androidx.compose.ui.geometry.Offset(size.width * .03f, size.height * .9f),
                    androidx.compose.ui.geometry.Offset(size.width * .31f, size.height * .58f),
                    4f,
                )
            }
        else
            AppIcon(
                if (kind in listOf("Racing", "GoKart")) Icons.Outlined.SportsMotorsports
                else if (kind == "Arcade") Icons.Outlined.VideogameAsset
                else Icons.Outlined.SportsEsports,
                null,
                Modifier.size(76.dp),
                tint = Violet,
            )
    }
}
