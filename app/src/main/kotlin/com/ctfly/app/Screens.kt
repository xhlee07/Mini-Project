@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ctfly.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import kotlinx.coroutines.delay
import org.json.JSONObject

data class Destination(val label: String, val icon: ImageVector)

val CustomerPages =
    listOf(
        Destination("Discover", Icons.Outlined.Explore),
        Destination("Play", Icons.Outlined.SportsEsports),
        Destination("Café", Icons.Outlined.LocalCafe),
        Destination("Events", Icons.Outlined.EmojiEvents),
        Destination("Club", Icons.Outlined.PersonOutline),
    )
val CrewPages =
    listOf(
        Destination("Operations", Icons.Outlined.Dashboard),
        Destination("My shifts", Icons.Outlined.Badge),
        Destination("Handover", Icons.Outlined.Forum),
        Destination("Equipment", Icons.Outlined.Build),
        Destination("Events", Icons.Outlined.EmojiEvents),
    )

@Composable
fun CtflyApp(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message, vm.field("dialog"), vm.field("page")) {
        if (vm.field("dialog").isNotBlank()) snackbar.currentSnackbarData?.dismiss()
        if (
            vm.message.isNotEmpty() &&
                vm.field("page") != "Checkout" &&
                vm.field("dialog").isBlank()
        ) {
            snackbar.showSnackbar(vm.message)
            vm.dismissMessage()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh(silent = true) }
    // Lifecycle-aware refresh: no polling while the app is stopped.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, vm.hasAccount) {
        lifecycle.repeatOnLifecycleCompat {
            var elapsed = 0
            while (true) {
                delay(1000)
                vm.tick()
                elapsed++
                if (elapsed % 30 == 0 && vm.hasAccount && !vm.busy) {
                    vm.refresh(silent = true)
                }
            }
        }
    }
    val logged = vm.hasAccount && vm.role.isNotEmpty()
    val page = vm.field("page", if (vm.crew) "Operations" else "Discover").ifBlank { "Discover" }
    LaunchedEffect(page) { snackbar.currentSnackbarData?.dismiss() }
    BackHandler(logged) {
        when {
            vm.field("dialog").isNotBlank() -> vm.set("dialog", "")
            page != (if (vm.crew) "Operations" else "Discover") ->
                vm.set("page", if (vm.crew) "Operations" else "Discover")
            else -> vm.set("dialog", "logout")
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        val wide = maxWidth >= 840.dp
        if (page == "Settings") {
            BackHandler { vm.set("page", vm.field("settingsReturn", "Discover")) }
            SettingsScreen(vm) { vm.set("page", vm.field("settingsReturn", "Discover")) }
            AppSnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        } else if (!logged) {
            Box(Modifier.fillMaxSize()) {
                AuthScreen(vm, wide)
                IconButton(
                    onClick = { vm.openSettings() },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding(),
                ) {
                    AppIcon(Icons.Outlined.Settings, tr("Settings"))
                }
                AppSnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        } else if (!BuildConfig.STAFF_APP && page == "Tour") {
            TourScreen(vm)
        } else if (page == "Checkout") {
            CheckoutScreen(vm) { vm.set("page", vm.field("checkoutReturn", "Bookings")) }
        } else {
            val pages = if (vm.crew) CrewPages else CustomerPages
            Scaffold(
                containerColor = Ink,
                snackbarHost = { AppSnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide)
                        NavigationBar(containerColor = Panel) {
                            pages.forEach { d ->
                                NavigationBarItem(
                                    selected = page == d.label,
                                    onClick = { vm.set("page", d.label) },
                                    icon = { AppIcon(d.icon, d.label) },
                                    label = { AppText(d.label, fontSize = 10.sp) },
                                )
                            }
                        }
                },
            ) { insets ->
                Row(Modifier.fillMaxSize().padding(insets)) {
                    if (wide)
                        NavigationRail(
                            containerColor = Panel,
                            modifier = Modifier.fillMaxHeight().width(96.dp),
                            header = { Brand(Modifier.padding(vertical = 22.dp), compact = true) },
                        ) {
                            pages.forEach { d ->
                                NavigationRailItem(
                                    page == d.label,
                                    { vm.set("page", d.label) },
                                    icon = { AppIcon(d.icon, d.label) },
                                    label = { AppText(d.label, fontSize = 10.sp) },
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            AppText(
                                "MY · 18+",
                                color = Muted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!wide) Brand(compact = true)
                            else
                                AppText(
                                    if (vm.crew) "CREW WORKSPACE" else "GAMING • SOCIAL • CULTURE",
                                    color = Muted,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                )
                            Spacer(Modifier.weight(1f))
                            if (!vm.crew)
                                IconButton(onClick = { vm.set("page", "Tour") }) {
                                    AppIcon(Icons.Outlined.ViewInAr, "3D venue tour")
                                }
                            if (vm.admin)
                                IconButton(onClick = { vm.set("page", "Manage") }) {
                                    AppIcon(Icons.Outlined.Tune, "Management")
                                }
                            if (!vm.crew)
                                IconButton(onClick = { vm.set("page", "Bookings") }) {
                                    AppIcon(Icons.Outlined.ConfirmationNumber, "My bookings")
                                }
                            if (!vm.crew)
                                BadgedBox(
                                    badge = {
                                        if (vm.cart().isNotEmpty())
                                            Badge { AppText(vm.cart().values.sum().toString()) }
                                    }
                                ) {
                                    IconButton(onClick = { vm.set("dialog", "cart") }) {
                                        AppIcon(Icons.Outlined.ShoppingBag, "Shopping bag")
                                    }
                                }
                            if (vm.crew) {
                                var more by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { more = true }) {
                                        AppIcon(Icons.Outlined.MoreVert, "More staff tools")
                                    }
                                    DropdownMenu(
                                        expanded = more,
                                        onDismissRequest = { more = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { AppText("Transactions") },
                                            onClick = {
                                                more = false
                                                vm.set("page", "Transactions")
                                            },
                                        )
                                    }
                                }
                            }
                            if (vm.crew)
                                IconButton(onClick = { vm.set("page", "Feedback") }) {
                                    AppIcon(Icons.Outlined.Feedback, tr("Customer feedback"))
                                }
                            IconButton(onClick = { vm.openSettings() }) {
                                AppIcon(Icons.Outlined.Settings, tr("Settings"))
                            }
                            IconButton(onClick = { vm.refresh() }) {
                                AppIcon(Icons.Outlined.Refresh, "Refresh")
                            }
                            if (vm.crew)
                                IconButton(onClick = { vm.set("dialog", "logout") }) {
                                    AppIcon(Icons.Outlined.Logout, "Sign out")
                                }
                        }
                        if (vm.loading)
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime)
                        if (vm.loadError.isNotEmpty())
                            Surface(color = Orange.copy(alpha = .12f)) {
                                AppText(
                                    vm.loadError,
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    color = Orange,
                                    fontSize = 12.sp,
                                )
                            }
                        Row(Modifier.weight(1f)) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                key(page) {
                                    when {
                                        vm.crew ->
                                            when (page) {
                                                "My shifts" -> CrewScreen(vm)
                                                "Handover" -> HandoverScreen(vm)
                                                "Equipment" -> EquipmentScreen(vm)
                                                "Feedback" -> FeedbackInboxScreen(vm)
                                                "Transactions" -> TransactionsScreen(vm)
                                                "Events" -> StaffEventsScreen(vm)
                                                "Manage" ->
                                                    if (vm.admin) ManagementScreen(vm)
                                                    else OperationsScreen(vm)
                                                else -> OperationsScreen(vm)
                                            }
                                        else ->
                                            when (page) {
                                                "Play" -> PlayScreen(vm)
                                                "Café" -> CafeScreen(vm)
                                                "Events" -> EventsScreen(vm)
                                                "Club" -> ClubScreen(vm)
                                                "Bookings" -> BookingsScreen(vm)
                                                else -> DiscoverScreen(vm)
                                            }
                                    }
                                }
                            }
                            if (wide && !vm.crew)
                                Column(
                                    Modifier.width(280.dp)
                                        .fillMaxHeight()
                                        .background(Panel.copy(alpha = .4f))
                                        .padding(20.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    Eyebrow("YOUR NEXT SESSION")
                                    BookingSummary(vm)
                                    HorizontalDivider(color = Muted.copy(alpha = .2f))
                                    Eyebrow("CLUB STATUS", Violet)
                                    AppText(
                                        if (logged) vm.profile.text("name") else "Welcome, player",
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    AppText(
                                        if (logged)
                                            "${tier(vm.profile.optInt("spent"))} member · ${vm.profile.optInt("points")} points"
                                        else "Make room for a better game.",
                                        color = Muted,
                                    )
                                    Button(
                                        onClick = {
                                            if (logged) vm.set("page", "Club")
                                            else vm.set("explore", "false")
                                        }
                                    ) {
                                        AppText(if (logged) "Your player pass" else "Sign in")
                                    }
                                    Photo("neon_cafe", Modifier.fillMaxWidth().height(150.dp))
                                    AppText(
                                        "Good games.\nGreat company.",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                        }
                    }
                }
            }
        }
        AppDialogs(vm)
        if (vm.validationError.isNotBlank() && vm.field("dialog").isBlank())
            AlertDialog(
                onDismissRequest = { vm.clearValidationError() },
                title = { AppText("Please check your details") },
                text = { AppText(vm.validationError) },
                confirmButton = {
                    TextButton(onClick = { vm.clearValidationError() }) { AppText("OK") }
                },
            )
    }
}

private suspend fun Lifecycle.repeatOnLifecycleCompat(
    block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
) = repeatOnLifecycle(Lifecycle.State.STARTED, block)

@Composable
fun AuthScreen(vm: AppViewModel, wide: Boolean) {
    val mode = vm.field("authMode", "login")
    val portal = if (BuildConfig.STAFF_APP) "staff" else "customer"
    Row(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        if (wide)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Photo("neon_arena", Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Ink.copy(alpha = .3f), Ink)
                            )
                        )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Brand()
                    Eyebrow(
                        if (BuildConfig.STAFF_APP) "THE PEOPLE BEHIND THE PLAY"
                        else "YOUR AFTER-HOURS UPGRADE"
                    )
                    AppText(
                        if (BuildConfig.STAFF_APP) "One team.\nEvery shift.\nReady to go."
                        else "Log in.\nLevel up.\nStay a while.",
                        fontSize = 52.sp,
                        lineHeight = 57.sp,
                        fontWeight = FontWeight.Black,
                    )
                    AppText(
                        if (BuildConfig.STAFF_APP) "Operations, people and the club — together."
                        else "Premium rigs. Real competition. Your people.",
                        color = Muted,
                    )
                }
            }
        Column(
            Modifier.weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 28.dp)
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Brand()
            Spacer(Modifier.height(4.dp))
            Eyebrow(
                if (portal == "staff") "THE PEOPLE BEHIND THE PLAY"
                else "WELCOME TO YOUR NEXT LEVEL",
                if (portal == "staff") Aqua else Lime,
            )
            AppText(
                when (mode) {
                    "register" -> "Join the club."
                    "forgot" -> "Let’s get you back."
                    "verify" -> "Check your inbox."
                    "reset" -> "A fresh start."
                    else -> if (portal == "staff") "Your shift starts here." else "Good to see you."
                },
                style = MaterialTheme.typography.headlineLarge,
            )
            AppText(
                when (mode) {
                    "register" -> "Create your 18+ player account."
                    "verify" -> "Enter the verification code sent to your email."
                    "forgot" -> "We’ll send a recovery code to your registered email."
                    "reset" -> "Choose a new password for your CTFLY account."
                    else ->
                        if (portal == "staff") "Staff and managers only. Your tools, in one place."
                        else "Sign in. Pick your space. Make it a good night."
                },
                color = Muted,
            )
            if (mode != "reset") Input("Email address", vm.field("email"), { vm.set("email", it) })
            if (mode == "register") {
                Input("Full name", vm.field("name"), { vm.set("name", it) })
                Input("Phone number", vm.field("phone"), { vm.set("phone", it) })
                DateFields(
                    "Date of birth · 18+",
                    vm.field("dob"),
                    { vm.set("dob", it) },
                    birth = true,
                )
            }
            if (mode in listOf("login", "register", "reset"))
                Input("Password", vm.password, { vm.password = it }, password = true)
            if (mode in listOf("register", "reset"))
                Input(
                    "Confirm password",
                    vm.confirmPassword,
                    { vm.confirmPassword = it },
                    password = true,
                )
            if (mode == "verify")
                Input("Email code", vm.field("code"), { vm.set("code", it) }, numeric = true)
            if (mode == "login")
                TextButton(
                    onClick = { vm.set("authMode", "forgot") },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    AppText("Forgot password?")
                }
            Button(
                onClick = { vm.authenticate() },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (vm.busy)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Ink)
                else
                    AppText(
                        when (mode) {
                            "register" -> "Create account"
                            "verify" -> "Verify code"
                            "forgot" -> "Send recovery code"
                            "reset" -> "Save new password"
                            else ->
                                if (BuildConfig.STAFF_APP) "Start your shift  →"
                                else "Let’s play  →"
                        },
                        fontWeight = FontWeight.Bold,
                    )
            }
            if (mode == "login" && portal == "customer")
                OutlinedButton(
                    onClick = { vm.set("authMode", "register") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText("New here? Join CTFLY")
                }
            if (mode != "login")
                TextButton(
                    onClick = {
                        vm.set("authMode", "login")
                        vm.set("code", "")
                        vm.password = ""
                        vm.confirmPassword = ""
                    }
                ) {
                    AppText("Back to sign in")
                }
            if (mode == "verify")
                TextButton(onClick = { vm.resend() }, enabled = !vm.busy) { AppText("Resend code") }
            if (vm.loadError.isNotEmpty()) AppText(vm.loadError, color = Orange, fontSize = 12.sp)
            AppText(
                if (BuildConfig.STAFF_APP)
                    "Staff CTFLY · Malaysia\nStaff and administrators only. Contact your manager for access."
                else
                    "CTFLY Gaming Cafe · Malaysia\nBookings available to players aged 18 and above.",
                color = Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun DiscoverScreen(vm: AppViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("MAKE TONIGHT COUNT")
                    AppText(
                        "Hey, ${vm.profile.text("name","player").substringBefore(" ") }.",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Chip("18+ CLUB")
            }
        }
        item {
            Hero(
                "THE CTFLY EXPERIENCE",
                "Your game.\nYour space.",
                "144 Hz to 240 Hz. Zero distractions. Find your favourite seat.",
                button = "Find a PC",
            ) {
                vm.set("playKind", "PC")
                vm.set("page", "Play")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(
                    vm.rows("resources")
                        .count { it.text("kind") == "PC" && it.text("status") == "Available" }
                        .toString(),
                    "Gaming PCs",
                    modifier = Modifier.weight(1f),
                )
                Stat("4", "Ways to unwind", Violet, Modifier.weight(1f))
            }
        }
        item { SectionTitle("Your next move", "Everything for a great session") }
        item {
            SurfaceCard(Modifier.fillMaxWidth().clickable { vm.set("page", "Tour") }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppIcon(Icons.Outlined.ViewInAr, null, Modifier.size(40.dp), tint = Aqua)
                    Column(Modifier.weight(1f)) {
                        Eyebrow("EXPLORE IN 3D", Aqua)
                        AppText("Walk through CTFLY", style = MaterialTheme.typography.titleLarge)
                        AppText(
                            "First-person venue tour · Joystick controls",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    AppIcon(Icons.Outlined.ArrowForward, null, tint = Aqua)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SurfaceCard(
                    Modifier.weight(1f).clickable {
                        vm.set("playKind", "Pool")
                        vm.set("page", "Play")
                    }
                ) {
                    AppIcon(Icons.Outlined.SportsEsports, null, tint = Violet)
                    AppText("Beyond the PC", fontWeight = FontWeight.Bold)
                    AppText("Pool, snooker & more", color = Muted, fontSize = 12.sp)
                }
                SurfaceCard(Modifier.weight(1f).clickable { vm.set("page", "Café") }) {
                    AppIcon(Icons.Outlined.LocalCafe, null, tint = Orange)
                    AppText("Fuel your play", fontWeight = FontWeight.Bold)
                    AppText("Bites & cold drinks", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            SectionTitle("On your calendar", action = "View all") { vm.set("page", "Bookings") }
            Spacer(Modifier.height(12.dp))
            BookingSummary(vm)
        }
        item {
            Hero(
                "COMPETE TOGETHER",
                "Bring your\nA-game.",
                "Find your next tournament and meet your next rival.",
                "tournament_banner",
                Violet,
                "Explore events",
            ) {
                vm.set("page", "Events")
            }
        }
    }
}

@Composable
fun BookingSummary(vm: AppViewModel) {
    val b =
        vm.rows("bookings")
            .filter { it.text("status") in listOf("Reserved", "Confirmed", "Active") }
            .minByOrNull { it.text("starts_at") }
    if (b == null)
        SurfaceCard {
            AppText("A good night starts here.", fontWeight = FontWeight.Bold)
            AppText("Pick a PC or bring friends to the lounge.", color = Muted, fontSize = 13.sp)
            TextButton(onClick = { vm.set("page", "Play") }) { AppText("Find your space →") }
        }
    else BookingCard(vm, b)
}

@Composable
fun PlayScreen(vm: AppViewModel) {
    val kind = vm.field("playKind", "PC")
    val zone = vm.field("zone", "All")
    val roomPlayers = vm.field("roomPlayers", "1").toIntOrNull()?.coerceIn(1, 4) ?: 1
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val area = VenueDirectory.area(kind)
        val count =
            vm.rows("resources").count { it.text("kind") == kind && it.text("status") != "Retired" }
        Box(Modifier.fillMaxWidth().height(126.dp)) {
            Photo(
                when (area) {
                    TourArea.BILLIARDS -> "pool_club"
                    TourArea.OUTDOOR -> "kart_circuit"
                    TourArea.LOUNGE -> "neon_cafe"
                    else -> "neon_arena"
                },
                Modifier.fillMaxSize(),
                "${area.label} concept",
            )
            Box(
                Modifier.fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Ink.copy(alpha = .94f), Ink.copy(alpha = .3f))
                        )
                    )
            )
            Column(
                Modifier.padding(18.dp).align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Eyebrow(
                    "${area.floor} / ${area.label.uppercase()}",
                    if (area == TourArea.OUTDOOR) Orange else Lime,
                )
                AppText(
                    when (area) {
                        TourArea.GAMING -> "Your next great game."
                        TourArea.BILLIARDS -> "A whole floor for your crew."
                        TourArea.LOUNGE -> "Find your kind of play."
                        TourArea.OUTDOOR -> "Take it outside."
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                AppText(
                    "$count ${VenueDirectory.label(kind).lowercase()} facilities · Choose your time",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = {
                    vm.set(
                        "tourPose",
                        (if (kind == "VIPRoom")
                                PrivateRoomPlan.rooms
                                    .first { it.x == 0f && it.capacity >= roomPlayers }
                                    .insidePose
                            else if (kind == "Racing")
                                TourPlan.stops
                                    .first {
                                        it.name ==
                                            if (zone == "VIP") "VIP simulator" else "Simulator lab"
                                    }
                                    .pose
                            else TourPlan.entry(area).pose)
                            .let { "${it.x},${it.z},${it.yaw},${it.pitch},${it.area.name}" },
                    )
                    vm.set("page", "Tour")
                },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                AppIcon(Icons.Outlined.ViewInAr, "Preview ${area.label} in 3D", tint = Lime)
            }
        }
        FilterRow(
            VenueDirectory.kinds.map { VenueDirectory.label(it) },
            VenueDirectory.label(kind),
        ) { label ->
            vm.set("playKind", VenueDirectory.kinds.first { VenueDirectory.label(it) == label })
            vm.set("zone", "All")
        }
        if (kind in listOf("PC", "Racing"))
            FilterRow(listOf("All", "Standard", "VIP"), zone) { vm.set("zone", it) }
        if (kind == "VIPRoom")
            FilterRow(
                listOf("1 player", "2 players", "3 players", "4 players"),
                if (roomPlayers == 1) "1 player" else "$roomPlayers players",
            ) {
                vm.set("roomPlayers", it.substringBefore(' '))
                vm.set("bookGuests", it.substringBefore(' '))
            }
        AppText(
            if (kind == "GoKart") "Kart sessions: 10, 15, 30 or 60 minutes · One driver per kart"
            else if (kind == "VIPRoom")
                "Choose your group · Price for the whole room · 3 guests use a 4-PC room"
            else if (kind == "Racing") "Standard or private VIP cockpit · One driver per simulator"
            else "Rates per hour · Availability confirmed when you reserve",
            color = Muted,
            fontSize = 11.sp,
        )
        val resources =
            vm.rows("resources")
                .filter {
                    it.text("kind") == kind &&
                        (kind !in listOf("PC", "Racing") ||
                            zone == "All" ||
                            it.text("zone") == zone) &&
                        (kind != "VIPRoom" || it.optInt("capacity", 1) >= roomPlayers) &&
                        it.text("status") != "Retired"
                }
                .sortedBy { if (kind == "VIPRoom") it.optInt("capacity", 1) else 0 }
        LazyVerticalGrid(
            GridCells.Adaptive(if (kind == "PC") 155.dp else 245.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(resources, key = { it.text("id") }) { r ->
                SurfaceCard {
                    if (kind in listOf("PC", "VIPRoom")) {
                        AppIcon(
                            if (kind == "VIPRoom") Icons.Outlined.MeetingRoom
                            else Icons.Outlined.DesktopWindows,
                            null,
                            Modifier.size(44.dp),
                            tint = if (r.text("zone") == "VIP") Violet else Lime,
                        )
                        Chip(r.text("zone"), if (r.text("zone") == "VIP") Violet else Lime)
                    } else {
                        ActivityArt(kind, Modifier.fillMaxWidth().height(120.dp))
                        if (kind == "Racing")
                            Chip(r.text("zone"), if (r.text("zone") == "VIP") Violet else Aqua)
                    }
                    AppText(r.text("name"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    AppText(
                        "${area.floor} · ${area.label} · Up to ${r.optInt("capacity",1)} player${if(r.optInt("capacity",1)>1)"s" else ""}",
                        color = Aqua,
                        fontSize = 11.sp,
                    )
                    AppText(r.text("description"), color = Muted, fontSize = 12.sp)
                    AppText(
                        if (kind == "GoKart") "${money(bookingPrice(r.optInt("rate"),15))} / 15 min"
                        else if (kind == "VIPRoom") "${money(r.optInt("rate"))} / room / hr"
                        else "${money(r.optInt("rate"))} / hr",
                        color = if (kind == "PC") Lime else Violet,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(
                        onClick = {
                            vm.resetBookingDraft(r.text("id"))
                            vm.set(
                                "bookGuests",
                                if (kind == "VIPRoom") roomPlayers.toString() else "1",
                            )
                            vm.set("dialog", "book")
                        },
                        enabled = r.text("status") == "Available",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(
                            if (r.text("status") == "Available") "Reserve" else r.text("status")
                        )
                    }
                }
            }
            if (resources.isEmpty())
                item {
                    EmptyState(
                        "No spaces to show",
                        if (vm.loading) "Loading your club…"
                        else if (kind == "VIPRoom")
                            "Private rooms need the 1.7 cloud inventory update. Then refresh Play."
                        else "New spaces are coming. Try another activity or refresh.",
                    )
                }
        }
    }
}

@Composable
fun CafeScreen(vm: AppViewModel) {
    val query = vm.field("search")
    val category = vm.field("category", "All")
    val cats = listOf("All", "Favorites") + vm.rows("categories").map { it.text("name") }
    val favorites = vm.rows("favorites").map { it.text("product_id") }.toSet()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Eyebrow("THE PIT STOP", Orange)
        AppText("Play hungry? Never.", style = MaterialTheme.typography.headlineMedium)
        Input("Search drinks & bites", query, { vm.set("search", it) })
        FilterRow(cats, category) { vm.set("category", it) }
        val products =
            vm.rows("products").filter { p ->
                p.optBoolean("active", true) &&
                    p.text("name").contains(query, true) &&
                    (category == "All" ||
                        (category == "Favorites" && p.text("id") in favorites) ||
                        vm.rows("categories").any {
                            it.text("id") == p.text("category_id") && it.text("name") == category
                        })
            }
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(products, key = { it.text("id") }) { p ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                ) {
                    Box {
                        Photo(
                            p.text("image"),
                            Modifier.fillMaxWidth().height(135.dp),
                            p.text("name"),
                        )
                        IconButton(
                            onClick = { vm.action("favorite", obj("id" to p.text("id"))) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            AppIcon(
                                if (p.text("id") in favorites) Icons.Outlined.Favorite
                                else Icons.Outlined.FavoriteBorder,
                                "Favorite",
                                tint = Lime,
                            )
                        }
                    }
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppText(p.text("name"), fontWeight = FontWeight.Bold, minLines = 2)
                        AppText(
                            money(p.optInt("price")),
                            color = Orange,
                            fontWeight = FontWeight.Bold,
                        )
                        val qty = vm.cart()[p.text("id")] ?: 0
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (qty > 0) {
                                IconButton(onClick = { vm.cartChange(p.text("id"), -1) }) {
                                    AppIcon(Icons.Outlined.Remove, "Remove one")
                                }
                                AppText(qty.toString())
                            } else
                                AppText(
                                    if (p.optInt("stock") == 0) "Sold out"
                                    else "${p.optInt("stock")} available",
                                    fontSize = 11.sp,
                                    color = Muted,
                                    modifier = Modifier.weight(1f),
                                )
                            FilledIconButton(
                                onClick = { vm.cartChange(p.text("id"), 1) },
                                enabled = p.optInt("stock") > qty,
                            ) {
                                AppIcon(Icons.Outlined.Add, "Add ${p.text("name")}")
                            }
                        }
                    }
                }
            }
            if (products.isEmpty())
                item { EmptyState("Nothing here yet", "Try another search or category.") }
        }
    }
}

@Composable
fun EventsScreen(vm: AppViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Hero(
                "THE ARENA",
                "Small stage.\nBig moments.",
                "Join the bracket. Find your rivals. Play for the story.",
                "tournament_banner",
                Violet,
            )
        }
        item { SectionTitle("Upcoming & live", "One team. One shot.") }
        items(
            vm.rows("events").filter { it.text("status") !in listOf("Cancelled", "Draft") },
            key = { it.text("id") },
        ) { e ->
            SurfaceCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Chip(e.text("game"), Violet)
                    Chip(e.text("status"), Aqua)
                }
                EventPoster(e.text("poster_url"), Modifier.fillMaxWidth().height(170.dp))
                AppText(e.text("name"), style = MaterialTheme.typography.titleLarge)
                EventInformation(e)
                AppText(
                    "${money(e.optInt("fee"))} / team · ${e.optInt("capacity")} slots",
                    color = Violet,
                )
                val mine =
                    vm.rows("registrations").find {
                        it.text("event_id") == e.text("id") && it.text("status") == "Registered"
                    }
                if (mine == null && e.text("status") == "Open")
                    Button(
                        onClick = {
                            vm.set("selectedEvent", e.text("id"))
                            vm.set("dialog", "event")
                        }
                    ) {
                        AppText("Enter tournament →")
                    }
                if (mine != null) {
                    AppText("Your team: ${mine.text("team")}", color = Lime)
                    if (
                        vm.rows("test_payments").any {
                            it.text("source_id") == mine.text("id") &&
                                it.text("status") == "Succeeded"
                        }
                    )
                        Chip("Test payment", Aqua)
                    if (!mine.optBoolean("paid")) {
                        AppText("Entry fee payable at counter", color = Orange)
                        TestPayButton(vm, "Event", mine.text("id"))
                        TextButton(
                            onClick = { vm.action("withdraw_event", obj("id" to mine.text("id"))) },
                            enabled = !vm.busy,
                        ) {
                            AppText("Withdraw entry")
                        }
                    }
                }
                val matches = vm.rows("matches").filter { it.text("event_id") == e.text("id") }
                if (matches.isNotEmpty()) {
                    HorizontalDivider()
                    AppText("Tournament bracket", fontWeight = FontWeight.Bold)
                    matches.forEach { m ->
                        AppText(
                            "R${m.optInt("round")} · ${m.text("team_a","TBD")}  vs  ${m.text("team_b","Bye / TBD")}" +
                                (if (m.optBoolean("done")) "  → ${m.text("team_winner")}" else ""),
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                }
            }
        }
        if (vm.rows("events").isEmpty())
            item { EmptyState("The next match is coming", "Check back for new tournaments.") }
    }
}

@Composable
fun BookingCard(vm: AppViewModel, b: JSONObject) {
    val resource = vm.rows("resources").find { it.text("id") == b.text("resource_id") }
    SurfaceCard {
        Chip(b.text("status"), if (b.text("status") == "Active") Aqua else Lime)
        AppText(
            resource?.text("name") ?: "Reserved space",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        AppText(
            "${dateLabel(b.text("starts_at"))}\nUntil ${dateLabel(b.text("ends_at"))}",
            color = Muted,
            fontSize = 12.sp,
        )
        AppText(
            "${money(b.optInt("total"))} · ${b.optInt("guests")} player(s)",
            fontWeight = FontWeight.SemiBold,
        )
        if (b.text("status") == "Active") {
            val left =
                runCatching {
                        ((Instant.parse(b.text("ends_at")).toEpochMilli() - vm.nowMillis) / 1000)
                            .coerceAtLeast(0)
                    }
                    .getOrDefault(0)
            AppText(
                "%02d:%02d:%02d remaining".format(left / 3600, (left % 3600) / 60, left % 60),
                color = Aqua,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (b.text("status") == "Confirmed")
            Button(
                onClick = {
                    vm.set("selectedBooking", b.text("id"))
                    vm.set("dialog", "qr")
                }
            ) {
                AppText("Show entry QR")
            }
        if (
            vm.rows("test_payments").any {
                it.text("source_id") == b.text("id") && it.text("status") == "Succeeded"
            }
        )
            Chip("Test payment", Aqua)
        if (b.text("status") == "Reserved") {
            AppText(
                if (b.text("hold_expires_at").isNotBlank())
                    "Complete payment within the checkout hold."
                else "Pay at counter · unpaid holds expire 15 min after start",
                color = Orange,
                fontSize = 11.sp,
            )
            if (b.text("hold_expires_at").isNotBlank()) HoldCountdown(vm, b.text("hold_expires_at"))
            TestPayButton(vm, "Booking", b.text("id"))
            if (!vm.crew)
                TextButton(
                    onClick = {
                        vm.set("cancelBooking", b.text("id"))
                        vm.set("dialog", "cancelBooking")
                    }
                ) {
                    AppText("Cancel reservation")
                }
        }
    }
}

@Composable
fun BookingsScreen(vm: AppViewModel) {
    val tab = vm.field("bookingTab", "Spaces")
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Eyebrow("ALL SET FOR A GOOD TIME")
            AppText("Your plans.", style = MaterialTheme.typography.headlineLarge)
            FilterRow(listOf("Spaces", "Orders", "Receipts", "Payments"), tab) {
                vm.set("bookingTab", it)
            }
        }
        when (tab) {
            "Payments" -> {
                item { TestModeNotice() }
                items(vm.rows("test_payments"), key = { it.text("id") }) { p ->
                    SurfaceCard {
                        Chip(p.text("status"))
                        AppText(
                            "${p.text("kind")} · ${money(p.optInt("total"))} · ${p.text("method")}"
                        )
                        AppText(p.text("reference"), color = Muted, localize = false)
                        TextButton(onClick = { vm.resumeCheckout(p) }) {
                            AppText("Payment details")
                        }
                    }
                }
                if (vm.rows("test_payments").isEmpty())
                    item {
                        EmptyState("No test transactions yet", "Test payments will appear here.")
                    }
            }
            "Spaces" -> {
                items(vm.rows("bookings"), key = { it.text("id") }) { BookingCard(vm, it) }
                if (vm.rows("bookings").isEmpty())
                    item { EmptyState("No reservations yet", "Your next session starts in Play.") }
            }
            "Orders" -> {
                items(vm.rows("orders"), key = { it.text("id") }) { o ->
                    SurfaceCard {
                        Chip(o.text("status"), Orange)
                        AppText("Order #${o.text("id").take(8)}", fontWeight = FontWeight.Bold)
                        vm.rows("order_items")
                            .filter { it.text("order_id") == o.text("id") }
                            .forEach {
                                AppText("${it.optInt("qty")} × ${it.text("name")}", color = Muted)
                            }
                        AppText(
                            "${money(o.optInt("total"))} · ${if(o.optBoolean("paid"))"Paid" else "Pay at counter"}"
                        )
                        if (o.text("status") == "Pending" && !o.optBoolean("paid")) {
                            TestPayButton(vm, "Order", o.text("id"))
                            if (o.text("hold_expires_at").isNotBlank())
                                HoldCountdown(vm, o.text("hold_expires_at"))
                        }
                        if (
                            vm.rows("test_payments").any {
                                it.text("source_id") == o.text("id") &&
                                    it.text("status") == "Succeeded"
                            }
                        )
                            Chip("Test payment", Aqua)
                        if (o.text("status") == "Pending" && !o.optBoolean("paid"))
                            TextButton(
                                onClick = { vm.action("cancel_order", obj("id" to o.text("id"))) },
                                enabled = !vm.busy,
                            ) {
                                AppText("Cancel order")
                            }
                        TextButton(
                            onClick = {
                                vm.rows("order_items")
                                    .filter { it.text("order_id") == o.text("id") }
                                    .forEach { i ->
                                        vm.cartChange(i.text("product_id"), i.optInt("qty"))
                                    }
                                vm.set("dialog", "cart")
                            }
                        ) {
                            AppText("Order again")
                        }
                    }
                }
                if (vm.rows("orders").isEmpty())
                    item {
                        EmptyState("Your cravings belong here", "Explore drinks and food in Café.")
                    }
            }
            else -> {
                items(vm.rows("bills"), key = { it.text("id") }) { b ->
                    SurfaceCard {
                        Chip(b.text("status", "Paid"))
                        if (b.text("environment") == "test")
                            AppText("TEST MODE · NO MONEY CHARGED", color = Lime)
                        AppText(
                            "${b.text("kind")} · ${money(b.optInt("total"))}",
                            fontWeight = FontWeight.Bold,
                        )
                        AppText(dateLabel(b.text("created_at")), color = Muted)
                        AppText(
                            "Subtotal ${money(b.optInt("subtotal"))} − discount ${money(b.optInt("discount"))}",
                            fontSize = 12.sp,
                        )
                        AppText("${b.text("method")} · +${b.optInt("points")} points", color = Lime)
                        TextButton(
                            onClick = {
                                vm.set("receipt", b.toString())
                                vm.set("dialog", "receipt")
                            }
                        ) {
                            AppText("View / share receipt")
                        }
                    }
                }
                if (vm.rows("bills").isEmpty())
                    item { EmptyState("No receipts yet", "Completed payments will appear here.") }
            }
        }
    }
}

@Composable
fun ClubScreen(vm: AppViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Eyebrow("MEMBERS GET MORE", Violet)
            AppText("Your player pass.", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            SurfaceCard(Modifier.background(Lime.copy(alpha = .06f), RoundedCornerShape(22.dp))) {
                Brand()
                AppText(
                    vm.profile.text("name", "Join the club"),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Chip("${tier(vm.profile.optInt("spent"))} member", Violet)
                AppText(
                    "${vm.profile.optInt("points")} points",
                    color = Lime,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                val spent = vm.profile.optInt("spent")
                val next = if (spent < 30000) 30000 else 100000
                LinearProgressIndicator(
                    progress = { (spent.toFloat() / next).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppText(
                    if (spent >= 100000) "Gold unlocked · 10% off your next bill"
                    else
                        "${money(next-spent)} to ${if(next==30000)"Silver · 5%" else "Gold · 10%"} discount",
                    color = Muted,
                    fontSize = 12.sp,
                )
                AppText(
                    "Earn 1 point per RM 1 paid. Tier upgrades apply to the next payment.",
                    fontSize = 11.sp,
                    color = Muted,
                )
                Row {
                    TextButton(onClick = { vm.set("dialog", "profile") }) {
                        AppText("Edit profile")
                    }
                    TextButton(
                        onClick = {
                            vm.set("page", "Bookings")
                            vm.set("bookingTab", "Receipts")
                        }
                    ) {
                        AppText("Receipts")
                    }
                }
            }
        }
        if (vm.testPaymentsEnabled)
            item {
                SurfaceCard {
                    AppText("Demo membership", fontWeight = FontWeight.Bold)
                    AppText(
                        tr("Demo points: %s").format(vm.profile.optInt("test_points")),
                        color = Lime,
                        localize = false,
                    )
                    AppText(
                        tr("Test spend: %s").format(money(vm.profile.optInt("test_spent"))),
                        color = Muted,
                        localize = false,
                    )
                    AppText("Demo points cannot be exchanged for real rewards.", color = Muted)
                }
            }
        item { SectionTitle("Good things, earned.", "Exchange points for club favourites") }
        items(
            vm.rows("rewards").filter { it.optBoolean("active", true) },
            key = { it.text("id") },
        ) { r ->
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        Icons.Outlined.Redeem,
                        null,
                        tint = Violet,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        AppText(r.text("name"), fontWeight = FontWeight.Bold)
                        AppText(
                            "${r.optInt("points")} points · ${r.optInt("stock")} left",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { vm.action("redeem", obj("id" to r.text("id"))) },
                    enabled =
                        !vm.busy &&
                            vm.profile.optInt("points") >= r.optInt("points") &&
                            r.optInt("stock") > 0,
                ) {
                    AppText("Redeem reward")
                }
            }
        }
        if (vm.rows("redemptions").isNotEmpty())
            item { SectionTitle("Your rewards", "Show your voucher at the counter") }
        items(vm.rows("redemptions")) { r ->
            SurfaceCard {
                AppText(r.text("name"), fontWeight = FontWeight.Bold)
                Chip(r.text("status"))
                AppText("Voucher ${r.text("id").take(8)}", color = Muted)
            }
        }
        if (vm.rows("points_transactions").isNotEmpty()) item { SectionTitle("Points activity") }
        items(vm.rows("points_transactions").take(20)) { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AppText(p.text("reason"), color = Muted, modifier = Modifier.weight(1f))
                AppText("${p.optInt("points")} pts", color = Lime)
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    if (vm.hasAccount) vm.set("dialog", "logout") else vm.set("explore", "false")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(if (vm.hasAccount) "Sign out" else "Sign in to join")
            }
        }
    }
}
