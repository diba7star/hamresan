package co.yooz.hamresan.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.yooz.hamresan.App
import co.yooz.hamresan.crypto.Crypto
import co.yooz.hamresan.data.PostView
import co.yooz.hamresan.data.Prefs
import co.yooz.hamresan.mesh.MeshState
import co.yooz.hamresan.net.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================================================
// navigation
// =====================================================================================================

sealed class Screen {
    object Home : Screen()
    object Explore : Screen()
    object Activity : Screen()
    object Profile : Screen()
    data class Post(val id: String) : Screen()
    data class Community(val id: String) : Screen()
    object NewPost : Screen()
    object CreateCommunity : Screen()
    object Themes : Screen()
    object Bookmarks : Screen()

    val isTab get() = this == Home || this == Explore || this == Activity || this == Profile
}

val ScreenSaver = Saver<Screen, String>(
    save = {
        when (it) {
            Screen.Home -> "home"; Screen.Explore -> "explore"; Screen.Activity -> "activity"; Screen.Profile -> "profile"
            is Screen.Post -> "post:" + it.id; is Screen.Community -> "community:" + it.id
            Screen.NewPost -> "new"; Screen.CreateCommunity -> "create"; Screen.Themes -> "themes"; Screen.Bookmarks -> "bookmarks"
        }
    },
    restore = {
        when {
            it.startsWith("post:") -> Screen.Post(it.removePrefix("post:"))
            it.startsWith("community:") -> Screen.Community(it.removePrefix("community:"))
            it == "explore" -> Screen.Explore; it == "activity" -> Screen.Activity; it == "profile" -> Screen.Profile
            it == "new" -> Screen.NewPost; it == "create" -> Screen.CreateCommunity; it == "themes" -> Screen.Themes; it == "bookmarks" -> Screen.Bookmarks
            else -> Screen.Home
        }
    }
)

val ID_RE = Regex("^[a-z0-9][a-z0-9-]{2,30}$")

@Composable
fun Root(
    hasPermissions: () -> Boolean,
    neededPermissions: () -> Array<String>,
    startMesh: () -> Unit,
    stopMesh: () -> Unit,
    openBatterySettings: () -> Unit
) {
    val app = App.instance
    val name by app.prefs.name.collectAsStateWithLifecycle()
    val communities by app.prefs.communities.collectAsStateWithLifecycle()
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
    val snack = remember { SnackbarHostState() }
    val go: (Screen) -> Unit = { screen = it }

    LaunchedEffect(Unit) { app.mesh.notices.collect { snack.showSnackbar(it) } }
    val unverified = communities.count { !it.verified }
    LaunchedEffect(communities.size, unverified) { if (unverified > 0) app.server.verifyCommunities() }

    if (name.isBlank() || communities.isEmpty()) {
        Onboarding(neededPermissions = neededPermissions, onDone = { startMesh() })
        return
    }

    val s = screen
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { if (s.isTab) BottomNav(s, go) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (s) {
                Screen.Home -> FeedScreen(snack, go)
                Screen.Explore -> ExploreScreen(snack, go)
                Screen.Activity -> ActivityScreen(snack, hasPermissions, neededPermissions, startMesh, stopMesh, openBatterySettings)
                Screen.Profile -> ProfileScreen(snack, go)
                is Screen.Post -> PostScreen(s.id, snack, onBack = { go(Screen.Home) }, go)
                is Screen.Community -> CommunityScreen(s.id, snack, go)
                Screen.NewPost -> NewPostScreen(snack, onBack = { go(Screen.Home) })
                Screen.CreateCommunity -> CreateCommunityScreen(snack, go)
                Screen.Themes -> ThemesScreen(onBack = { go(Screen.Profile) })
                Screen.Bookmarks -> BookmarksScreen(onBack = { go(Screen.Profile) }, go)
            }
        }
    }
}

@Composable
private fun BottomNav(current: Screen, go: (Screen) -> Unit) {
    val app = App.instance
    val palette = LocalPalette.current
    val skin = LocalSkin.current
    val bg = if (skin == Skin.BROWSER) palette.chrome else MaterialTheme.colorScheme.surface
    val fg = if (skin == Skin.BROWSER) palette.onChrome else MaterialTheme.colorScheme.onSurface
    val peers by app.mesh.peers.collectAsStateWithLifecycle()
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().background(bg).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            NavItem(Icons.Default.Home, "خانه", current == Screen.Home, fg) { go(Screen.Home) }
            NavItem(Icons.Default.Search, "کاوش", current == Screen.Explore, fg) { go(Screen.Explore) }
            // "+" : writers compose; others get a hint
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, fg, RoundedCornerShape(12.dp))
                    .clickable { go(if (app.prefs.isWriter()) Screen.NewPost else Screen.Profile) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, "مطلب تازه", tint = fg) }
            Box {
                NavItem(Icons.Default.Notifications, "همسایه‌ها", current == Screen.Activity, fg) { go(Screen.Activity) }
                if (peers.isNotEmpty()) Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp).size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
                    Text(peers.size.fa(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            NavItem(Icons.Default.Person, "پروفایل", current == Screen.Profile, fg) { go(Screen.Profile) }
        }
    }
}

@Composable
private fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, fg: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (on) fg else fg.copy(alpha = .55f), modifier = Modifier.size(26.dp))
        Text(label, fontSize = 10.sp, color = if (on) fg else fg.copy(alpha = .55f), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

// =====================================================================================================
// feed (home)
// =====================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(snack: SnackbarHostState, go: (Screen) -> Unit) {
    val app = App.instance
    val skin = LocalSkin.current
    val palette = LocalPalette.current
    val communities by app.prefs.communities.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    val now = remember { System.currentTimeMillis() }
    val feedFlow = remember(filter) { app.repo.items().feed(now, if (filter.isBlank()) 1 else 0, filter, app.prefs.pub) }
    val postsOrNull by feedFlow.collectAsStateWithLifecycle(null)
    val latestFlow = remember { app.repo.items().latestByCommunity() }
    val latest by latestFlow.collectAsStateWithLifecycle(emptyList())
    val peers by app.mesh.peers.collectAsStateWithLifecycle()
    val running by app.mesh.running.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    val nameOf: (String?) -> String = { id -> communities.firstOrNull { it.id == id }?.name?.ifBlank { id } ?: (id ?: "") }
    val status = when { !running -> "شبکه محلی خاموش"; peers.isEmpty() -> "در جست‌وجوی همسایه‌ها…"; else -> "${peers.size.fa()} همسایه متصل" }

    fun doSync() {
        syncing = true
        scope.launch {
            val r = app.server.sync(); syncing = false
            snack.showSnackbar(r.fold({ "همگام با سرور: ${it.received.fa()} دریافت، ${it.sent.fa()} ارسال" }, { syncError(it) }))
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- header by skin
        when (skin) {
            Skin.INSTA -> Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 10.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("همرسان", fontSize = 26.sp, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall.copy(brush = palette.accentBrush))
                    Text(status, fontSize = 11.sp, color = if (peers.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
                if (app.prefs.serverLocked) {
                    if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { doSync() }) { Icon(Icons.Default.Refresh, "همگام‌سازی با سرور") }
                }
            }
            Skin.MAGAZINE -> Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(persianDate(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
                Text("همرسان", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 34.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("نشریه محله", fontSize = 10.sp, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp)); DoubleRule(); Spacer(Modifier.height(4.dp))
            }
            Skin.BROWSER -> Column(Modifier.fillMaxWidth().background(palette.chrome)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    BrowserTab("همه", filter.isBlank(), Brush.linearGradient(listOf(Color.Gray, Color.LightGray))) { filter = "" }
                    communities.forEach { c -> BrowserTab(c.name.ifBlank { c.id }, filter == c.id, brushFor(c.id)) { filter = c.id } }
                    Text("+", color = palette.onChrome, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clickable { go(Screen.Explore) })
                }
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(10.dp, 8.dp, 10.dp, 6.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        val verified = filter.isBlank() || communities.firstOrNull { it.id == filter }?.verified == true
                        Box(Modifier.size(16.dp).clip(CircleShape).background(if (verified) MaterialTheme.colorScheme.secondary else Color(0xFFF59E0B)), contentAlignment = Alignment.Center) { Text(if (verified) "✓" else "!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Text("‎hamresan://" + (filter.ifBlank { "all" }) + "/", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.Left,
                            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                        if (app.prefs.serverLocked) Text(if (syncing) "…" else "⟳", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { if (!syncing) doSync() })
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("● $status", fontSize = 10.5.sp, color = if (peers.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary)
                        Text(if (filter.isBlank()) "${communities.size.fa()} محله" else "کلید " + if (communities.firstOrNull { it.id == filter }?.verified == true) "تأییدشده ✓" else "تأییدنشده", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---- community selector: stories (insta) / tabs (magazine) ; browser has tabs above
        if (skin == Skin.INSTA) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Story("افزودن", "+", null, false, onClick = { go(Screen.Explore) }, plus = true)
                communities.forEach { c ->
                    val lastIn = latest.firstOrNull { it.community == c.id }?.latest ?: 0L
                    val isNew = lastIn > app.prefs.lastSeen(c.id)
                    Story(c.name.ifBlank { c.id }, c.name.ifBlank { c.id }, c.id, isNew, onClick = { app.prefs.seenCommunity(c.id); go(Screen.Community(c.id)) }, selected = filter == c.id,
                        onLong = { filter = if (filter == c.id) "" else c.id })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        } else if (skin == Skin.MAGAZINE) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MagTab("همه", filter.isBlank()) { filter = "" }
                communities.forEach { c -> MagTab(c.name.ifBlank { c.id }, filter == c.id) { filter = c.id } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        if (skin != Skin.INSTA && filter.isNotBlank()) {
            // tiny community strip for mag/browser so its page is reachable
            Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(nameOf(filter), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("صفحه محله ›", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.clickable { go(Screen.Community(filter)) })
            }
        }

        val missing = communities.filter { it.adminPub.isBlank() }
        if (missing.isNotEmpty()) Banner("کلید محله ${missing.joinToString("، ") { "«${it.id}»" }} هنوز دریافت نشده؛ با اولین همسایه یا اینترنت می‌رسد.")

        val posts = postsOrNull
        when {
            posts == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            posts.isEmpty() -> EmptyFeed(go)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(posts, key = { it.id }) { p ->
                    when (skin) {
                        Skin.INSTA -> InstaPost(p, nameOf(p.community), snack, go)
                        Skin.MAGAZINE -> if (posts.indexOf(p) == 0) MagHero(p, nameOf(p.community), go) else MagRow(p, nameOf(p.community), go)
                        Skin.BROWSER -> BrowserCard(p, nameOf(p.community), go)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFeed(go: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("هنوز مطلبی نرسیده", style = MaterialTheme.typography.titleMedium)
        Text("به یک همسایه که برنامه را دارد نزدیک شوید، یا در «کاوش» محله‌های بیشتری دنبال کنید.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        TextButton(onClick = { go(Screen.Explore) }) { Text("کاوش محله‌ها") }
    }
}

@Composable
fun Banner(text: String) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun DoubleRule() {
    Column { HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.height(2.dp)); HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun MagTab(text: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(top = 8.dp)) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(2.dp).fillMaxWidth().background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent))
    }
}

@Composable
private fun BrowserTab(text: String, on: Boolean, favicon: Brush, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(if (on) MaterialTheme.colorScheme.surface else Color(0xFF2A2D35))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(favicon))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.5.sp, color = if (on) MaterialTheme.colorScheme.onSurface else palette.onChrome.copy(alpha = .7f), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun Story(label: String, initial: String, key: String?, isNew: Boolean, onClick: () -> Unit, plus: Boolean = false, selected: Boolean = false, onLong: (() -> Unit)? = null) {
    val palette = LocalPalette.current
    val ring = when { plus -> SolidColor(MaterialTheme.colorScheme.outline); isNew -> palette.accentBrush; else -> SolidColor(MaterialTheme.colorScheme.outline) }
    Column(
        Modifier.width(66.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (plus) Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
            Text("+", fontSize = 26.sp, fontWeight = FontWeight.Light)
        } else Avatar(initial, key ?: label, size = 56, ring = ring)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ---- post renderers

@Composable
fun InstaPost(p: PostView, communityName: String, snack: SnackbarHostState, go: (Screen) -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val bookmarks by app.prefs.bookmarks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.authorName ?: "?", p.author, size = 34, ring = palette.accentBrush, modifier = Modifier.clickable { p.community?.let { go(Screen.Community(it)) } })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.authorName ?: Crypto.shortId(p.author), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("$communityName · ${relativeTime(p.createdAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("···", fontWeight = FontWeight.Black, modifier = Modifier.clickable { go(Screen.Post(p.id)) })
        }
        Box(Modifier.fillMaxWidth().clickable { go(Screen.Post(p.id)) }) {
            if (p.image != null) ImageBytes(p.image, Modifier.fillMaxWidth().heightIn(max = 420.dp), ContentScale.FillWidth, 0)
            else TextTile(p.title.ifBlank { p.body.take(120) }, p.id, Modifier.fillMaxWidth().aspectRatio(1.25f))
        }
        Row(Modifier.fillMaxWidth().padding(8.dp, 6.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { if (app.repo.like(p.id)) snack.showSnackbar("پسندیدید ❤") } }) {
                Icon(if (p.likedByMe > 0) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "پسندیدن", tint = if (p.likedByMe > 0) palette.likeColor else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = { go(Screen.Post(p.id)) }) { Icon(Icons.Default.MailOutline, "دیدگاه", modifier = Modifier.size(24.dp)) }
            IconButton(onClick = { sharePost(ctx, p, communityName) }) { Icon(Icons.Default.Share, "اشتراک‌گذاری", modifier = Modifier.size(23.dp)) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { app.prefs.toggleBookmark(p.id) }) { Icon(Icons.Default.Star, "ذخیره", tint = if (p.id in bookmarks) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp)) }
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            if (p.likeCount > 0) Text("${p.likeCount.fa()} نفر پسندیدند", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (p.title.isNotBlank() || p.body.isNotBlank()) Text(
                buildString { if (p.title.isNotBlank()) append(p.title); if (p.title.isNotBlank() && p.body.isNotBlank()) append(" — "); append(p.body) },
                fontSize = 13.5.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).clickable { go(Screen.Post(p.id)) }
            )
            if (p.commentCount > 0) Text("دیدن هر ${p.commentCount.fa()} دیدگاه", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).clickable { go(Screen.Post(p.id)) })
            Text("${relativeTime(p.createdAt)} · ${remainingDays(p.expiresAt)}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun MagHero(p: PostView, communityName: String, go: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { go(Screen.Post(p.id)) }.padding(18.dp, 12.dp)) {
        Text("$communityName · ویژه", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        if (p.image != null) ImageBytes(p.image, Modifier.fillMaxWidth().heightIn(max = 220.dp), ContentScale.Crop, 4)
        else TextTile(p.title.ifBlank { p.body.take(100) }, p.id, Modifier.fillMaxWidth().height(170.dp), 18)
        Spacer(Modifier.height(10.dp))
        if (p.title.isNotBlank()) Text(p.title, style = MaterialTheme.typography.headlineSmall)
        if (p.body.isNotBlank()) Text(p.body, fontSize = 13.5.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.authorName ?: "?", p.author, 22); Spacer(Modifier.width(6.dp))
            Text("${p.authorName ?: Crypto.shortId(p.author)} · ${relativeTime(p.createdAt)} · ${p.commentCount.fa()} دیدگاه · ${p.likeCount.fa()} پسند", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun MagRow(p: PostView, communityName: String, go: (Screen) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().clickable { go(Screen.Post(p.id)) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.title.ifBlank { p.body.take(80) }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${p.authorName ?: ""} · $communityName · ${relativeTime(p.createdAt)} · ${p.commentCount.fa()} دیدگاه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(12.dp))
            if (p.image != null) ImageBytes(p.image, Modifier.size(84.dp, 66.dp), ContentScale.Crop, 3)
            else Box(Modifier.size(84.dp, 66.dp).clip(RoundedCornerShape(3.dp)).background(brushFor(p.id)))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun BrowserCard(p: PostView, communityName: String, go: (Screen) -> Unit) {
    Card(onClick = { go(Screen.Post(p.id)) }, modifier = Modifier.fillMaxWidth().padding(14.dp, 7.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        if (p.image != null) ImageBytes(p.image, Modifier.fillMaxWidth().heightIn(max = 200.dp), ContentScale.Crop, 0)
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(brushFor(p.community ?: p.id)), contentAlignment = Alignment.Center) { Text((p.title.ifBlank { communityName }).take(1), color = Color.White, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title.ifBlank { p.body.take(80) }, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("‎hamresan://${p.community ?: "?"}/post/${p.id.take(4)}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth(),
                    style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                Text("${p.authorName ?: ""} · ${relativeTime(p.createdAt)} · ${p.commentCount.fa()} دیدگاه · ${p.likeCount.fa()} پسند", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ImageBytes(bytes: ByteArray, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, corner: Int = 4) {
    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier.clip(RoundedCornerShape(corner.dp)), contentScale = contentScale)
}

fun syncError(e: Throwable): String = when (e) {
    is ServerApi.NoServer -> "نشانی سرور تنظیم نشده (پروفایل)"
    is ServerApi.WrongCommunity -> "این سرور متعلق به محله «${e.id}» است که دنبال نمی‌کنید"
    else -> "اتصال به سرور نشد (اینترنت دارید؟)"
}

// =====================================================================================================
// explore: directory list + nearby communities + create local community
// =====================================================================================================

@Composable
fun ExploreScreen(snack: SnackbarHostState, go: (Screen) -> Unit) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("کاوش محله‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("هر محله‌ای را دنبال کنید؛ مطالبش از همسایه‌ها یا سرور به گوشی شما می‌رسد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        NearbySection(go)
        CommunityPicker(onJoined = { id ->
            app.mesh.commands.tryEmit(MeshState.Command.ResyncAll)
            scope.launch { snack.showSnackbar("محله «$id» اضافه شد") }
        })
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("محله خودتان را بسازید", fontWeight = FontWeight.Bold)
        Text("اگر هیچ‌کس در منطقه اینترنت ندارد، همین‌جا یک محله بسازید و نویسنده‌اش باشید. همسایه‌ها آن را در «محله‌های اطراف» می‌بینند. هر وقت اینترنت آمد، با یک دکمه در فهرست عمومی ثبت می‌شود.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
        OutlinedButton(onClick = { go(Screen.CreateCommunity) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !app.prefs.isAdmin()) {
            Text(if (app.prefs.isAdmin()) "این گوشی قبلاً مدیر یک محله است" else "ساخت محله جدید (آفلاین)")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NearbySection(go: (Screen) -> Unit) {
    val app = App.instance
    val nearby by app.mesh.nearby.collectAsStateWithLifecycle()
    val followed by app.prefs.communities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val list = nearby.values.filter { n -> followed.none { it.id == n.id } }
    if (list.isEmpty()) return
    Text("محله‌های اطراف (از همسایه‌ها)", fontWeight = FontWeight.Bold)
    Text("این‌ها را همسایه‌های نزدیک دنبال می‌کنند؛ بدون اینترنت قابل دنبال‌کردن هستند.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    list.forEach { n ->
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(n.name.ifBlank { n.id }, n.id, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.name.ifBlank { n.id }, fontWeight = FontWeight.Bold)
                    Text("${n.id} · از ${n.peerName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    scope.launch {
                        app.prefs.upsertCommunity(Prefs.Community(n.id, n.name, n.adminPub, verified = false))
                        app.repo.ensureSelfAuthor()
                        app.mesh.forgetNearby(n.id)
                        app.mesh.commands.tryEmit(MeshState.Command.ResyncAll)
                        go(Screen.Community(n.id))
                    }
                }) { Text("دنبال کردن") }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("فهرست عمومی (نیاز به اینترنت)", fontWeight = FontWeight.Bold)
}

@Composable
fun CommunityPicker(onJoined: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = App.instance
    val followed by app.prefs.communities.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ServerApi.DirEntry>?>(null) }
    var offline by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var manual by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query, attempt) {
        var r = app.server.directoryList(query)
        if (r.isFailure) { kotlinx.coroutines.delay(1500); r = app.server.directoryList(query) }
        r.fold({ items = it; offline = false }, { items = emptyList(); offline = true })
    }

    Column(modifier) {
        OutlinedTextField(value = query, onValueChange = { query = it.take(40) }, label = { Text("جست‌وجوی محله یا نویسنده") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            offline -> {
                Text("فهرست عمومی به اینترنت نیاز دارد. بدون اینترنت از «محله‌های اطراف» استفاده کنید یا شناسه را دستی وارد کنید.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { items = null; attempt++ }, modifier = Modifier.fillMaxWidth()) { Text("تلاش دوباره") }
                OutlinedTextField(value = manual, onValueChange = { manual = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(31); error = null },
                    label = { Text("شناسه محله (دستی)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (ID_RE.matches(manual)) TextButton(onClick = {
                            scope.launch { val e = joinCommunity(manual); if (e != null) error = e else { onJoined(manual); manual = "" } }
                        }) { Text("پیوستن") }
                    })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
            list.isEmpty() -> Text("محله‌ای پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            else -> list.forEach { e ->
                val already = followed.any { it.id == e.id }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(e.name.ifBlank { e.id }, e.id, 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name.ifBlank { e.id }, fontWeight = FontWeight.Bold)
                        Text(e.id, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (already) Text("دنبال می‌کنید ✓", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Button(enabled = busy == null, onClick = {
                        busy = e.id
                        scope.launch {
                            app.prefs.upsertCommunity(Prefs.Community(e.id, e.name, e.pub, verified = true))
                            app.repo.ensureSelfAuthor(); busy = null; onJoined(e.id)
                        }
                    }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text(if (busy == e.id) "…" else "دنبال کردن", fontSize = 12.sp) }
                }
            }
        }
    }
}

suspend fun joinCommunity(id: String): String? {
    val app = App.instance
    val cid = id.trim().lowercase()
    if (!ID_RE.matches(cid)) return "شناسه نامعتبر است"
    if (app.prefs.community(cid) != null) return "این محله را قبلاً دنبال می‌کنید"
    val r = app.server.directoryLookup(cid)
    return r.fold(
        { entry -> if (entry == null) "این شناسه در فهرست ثبت نشده." else { app.prefs.upsertCommunity(Prefs.Community(entry.id, entry.name, entry.pub, verified = true)); null } },
        { app.prefs.upsertCommunity(Prefs.Community(cid, "", "", verified = false)); null }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityScreen(snack: SnackbarHostState, go: (Screen) -> Unit) {
    val app = App.instance
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("ساخت محله") }, navigationIcon = { IconButton(onClick = { go(Screen.Explore) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } })
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("شما مدیر و اولین نویسنده این محله می‌شوید. کلید مدیر روی همین گوشی ساخته و نگه داشته می‌شود؛ از آن مراقبت کنید (اگر گوشی را عوض کنید محله قابل ادامه نیست مگر کلید را از پروفایل کپی کرده باشید).",
                fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("نام محله (فارسی)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("شناسه عمومی به‌صورت خودکار از کلید شما ساخته می‌شود و در دنیا یکتاست (مثل l-3f9a1c2b7d). هر وقت اینترنت داشتید می‌توانید آن را در فهرست عمومی ثبت کنید.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            Button(enabled = name.trim().length >= 2 && !busy, onClick = {
                busy = true
                scope.launch {
                    val c = app.repo.createLocalCommunity(name)
                    busy = false
                    if (c == null) snack.showSnackbar("این گوشی قبلاً مدیر یک محله است")
                    else { app.mesh.commands.tryEmit(MeshState.Command.ResyncAll); snack.showSnackbar("محله «${c.name}» ساخته شد؛ شما نویسنده آن هستید"); go(Screen.Community(c.id)) }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "…" else "ساخت محله و شروع نوشتن") }
        }
    }
}

// =====================================================================================================
// community page (profile-like)
// =====================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(id: String, snack: SnackbarHostState, go: (Screen) -> Unit) {
    val app = App.instance
    val communities by app.prefs.communities.collectAsStateWithLifecycle()
    val c = communities.firstOrNull { it.id == id }
    val now = remember { System.currentTimeMillis() }
    val feedFlow = remember(id) { app.repo.items().feed(now, 0, id, app.prefs.pub) }
    val posts by feedFlow.collectAsStateWithLifecycle(emptyList())
    val writersFlow = remember(id) { app.repo.authors().writersOf(id) }
    val writers by writersFlow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val palette = LocalPalette.current
    val label = c?.name?.ifBlank { id } ?: id
    val adminSeed by app.prefs.adminSeed.collectAsStateWithLifecycle()
    val mine = adminSeed.isNotBlank() && app.repo.adminCommunity(adminSeed)?.id == id

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(id, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = { go(Screen.Home) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } })
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Column(Modifier.padding(16.dp, 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(label, id, 78, ring = palette.accentBrush)
                        Spacer(Modifier.width(18.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                            StatBox(posts.size.fa(), "مطلب"); StatBox(writers.size.fa(), "نویسنده"); StatBox(posts.sumOf { it.likeCount }.fa(), "پسند")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        if (c != null) Text(
                            when { c.local && !c.verified -> "محلی · ثبت‌نشده"; c.verified -> "✓ تأییدشده"; c.adminPub.isBlank() -> "بدون کلید"; else -> "تأییدنشده" },
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (c.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(CircleShape).background(if (c.verified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(8.dp, 3.dp)
                        )
                    }
                    if (writers.isNotEmpty()) Text("نویسنده‌ها: " + writers.joinToString("، ") { it.name }, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    if (c?.adminPub?.isNotBlank() == true) Text("اثر انگشت کلید: ${Crypto.shortId(c.adminPub)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (c != null && communities.size > 1 && !mine) OutlinedButton(onClick = {
                            scope.launch { app.repo.leaveCommunity(id); snack.showSnackbar("محله «$label» و مطالبش حذف شد"); go(Screen.Home) }
                        }, modifier = Modifier.weight(1f)) { Text("دنبال نکردن") }
                        else Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(if (mine) "محله شما (مدیر)" else "دنبال می‌کنید ✓") }
                        if (mine && c?.verified == false) OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                val r = app.server.registerLocalCommunity(id); busy = false
                                snack.showSnackbar(r.fold({ "محله در فهرست عمومی ثبت شد ✓" }, { "ثبت نشد: " + (if (it.message?.contains("taken") == true) "شناسه قبلاً گرفته شده" else "اینترنت در دسترس نیست") }))
                            }
                        }, modifier = Modifier.weight(1f)) { Text(if (busy) "…" else "ثبت در فهرست عمومی") }
                    }
                }
            }
            items(posts, key = { it.id }) { p ->
                Box(Modifier.aspectRatio(1f).padding(1.dp).clickable { go(Screen.Post(p.id)) }) {
                    if (p.image != null) ImageBytes(p.image, Modifier.fillMaxSize(), ContentScale.Crop, 0)
                    else TextTile(p.title.ifBlank { p.body.take(40) }, p.id, Modifier.fillMaxSize(), 12)
                }
            }
        }
    }
}

// =====================================================================================================
// activity: neighbours + mesh
// =====================================================================================================

@Composable
fun ActivityScreen(
    snack: SnackbarHostState, hasPermissions: () -> Boolean, neededPermissions: () -> Array<String>,
    startMesh: () -> Unit, stopMesh: () -> Unit, openBatterySettings: () -> Unit
) {
    val app = App.instance
    val prefs = app.prefs
    val peers by app.mesh.peers.collectAsStateWithLifecycle()
    val running by app.mesh.running.collectAsStateWithLifecycle()
    val log by app.mesh.log.collectAsStateWithLifecycle()
    val adminSeed by prefs.adminSeed.collectAsStateWithLifecycle()
    var meshOn by remember { mutableStateOf(prefs.meshEnabled) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startMesh() }
    val adminCommunity = if (adminSeed.isBlank()) null else app.repo.adminCommunity(adminSeed)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("همسایه‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Section("شبکه محلی") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("همگام‌سازی با همسایه‌ها", fontSize = 14.sp)
                    Text(if (running) "فعال · ${peers.size.fa()} همسایه متصل" else "غیرفعال", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = meshOn, onCheckedChange = { on ->
                    meshOn = on; prefs.meshEnabled = on
                    if (on) { if (hasPermissions()) startMesh() else permLauncher.launch(neededPermissions()) } else stopMesh()
                })
            }
            if (meshOn && !hasPermissions()) Button(onClick = { permLauncher.launch(neededPermissions()) }, modifier = Modifier.fillMaxWidth()) { Text("دادن اجازه‌های لازم") }
            TextButton(onClick = openBatterySettings) { Text("خارج‌کردن برنامه از بهینه‌سازی باتری") }
        }
        Section("متصل الان") {
            if (peers.isEmpty()) Text("هنوز همسایه‌ای متصل نیست. بلوتوث و وای‌فای روشن باشد و به کسی که برنامه را دارد نزدیک شوید.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            peers.values.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.name, p.pub.ifBlank { p.endpointId }, 36)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name + if (p.isWriter) "  ✎" else "", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text("دریافت ${p.received.fa()} · ارسال ${p.sent.fa()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (adminCommunity != null && !p.isWriter) TextButton(onClick = { app.mesh.commands.tryEmit(MeshState.Command.GrantWriter(p.endpointId)) }) { Text("نویسنده کن") }
                }
            }
        }
        Section("گزارش") {
            if (log.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            log.take(30).forEach { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// =====================================================================================================
// profile / settings
// =====================================================================================================

@Composable
fun ProfileScreen(snack: SnackbarHostState, go: (Screen) -> Unit) {
    val app = App.instance
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
    val name by prefs.name.collectAsStateWithLifecycle()
    val cert by prefs.cert.collectAsStateWithLifecycle()
    val communities by prefs.communities.collectAsStateWithLifecycle()
    val adminSeed by prefs.adminSeed.collectAsStateWithLifecycle()
    val bookmarks by prefs.bookmarks.collectAsStateWithLifecycle()
    val postCountFlow = remember { app.repo.items().postCount(System.currentTimeMillis()) }
    val postCount by postCountFlow.collectAsStateWithLifecycle(0)
    val palette = LocalPalette.current

    var editName by rememberSaveable { mutableStateOf(name) }
    var serverInput by rememberSaveable { mutableStateOf("") }
    var serverLocked by remember { mutableStateOf(prefs.serverLocked) }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var seedInput by remember { mutableStateOf("") }
    var showAdmin by rememberSaveable { mutableStateOf(false) }
    var showSeed by remember { mutableStateOf(false) }
    var ttl by rememberSaveable { mutableStateOf(prefs.postTtlDays.toString()) }
    val writerCommunity = prefs.writerCommunity()
    val isWriter = cert != null && writerCommunity != null
    val adminCommunity = if (adminSeed.isBlank()) null else app.repo.adminCommunity(adminSeed)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- header like a profile
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, prefs.pub, 78, ring = palette.accentBrush)
            Spacer(Modifier.width(18.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                StatBox(postCount.fa(), "مطلب"); StatBox(communities.size.fa(), "محله"); StatBox(bookmarks.size.fa(), "ذخیره")
            }
        }
        Column {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(if (isWriter) "نویسنده «${writerCommunity!!.name.ifBlank { writerCommunity.id }}» · اعتبار تا " + SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(cert!!.exp)).fa() else "خواننده · شناسه ${Crypto.shortId(prefs.pub)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { go(Screen.Bookmarks) }, modifier = Modifier.weight(1f)) { Text("ذخیره‌شده‌ها") }
            OutlinedButton(onClick = { go(Screen.Themes) }, modifier = Modifier.weight(1f)) { Text("ظاهر برنامه") }
        }

        Section("محله‌هایی که دنبال می‌کنید") {
            communities.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { go(Screen.Community(c.id)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.name.ifBlank { c.id }, c.id, 34); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name.ifBlank { c.id }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(when { c.local && !c.verified -> "${c.id} · محلی، ثبت‌نشده"; c.verified -> "${c.id} · تأییدشده ✓"; c.adminPub.isBlank() -> "${c.id} · بدون کلید"; else -> "${c.id} · تأییدنشده" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = { go(Screen.Explore) }) { Text("+ افزودن محله") }
            if (communities.any { !it.verified && !it.local }) OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch { val n = app.server.verifyCommunities(); busy = false; snack.showSnackbar("${n.fa()} از ${communities.size.fa()} محله تأیید شد") }
            }, modifier = Modifier.fillMaxWidth()) { Text("بررسی کلیدها با فهرست عمومی") }
        }

        if (!isWriter) Section("نام نمایشی") {
            OutlinedTextField(value = editName, onValueChange = { editName = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { if (editName.trim() != name && editName.trim().length >= 2) TextButton(onClick = { prefs.setName(editName.trim()); scope.launch { app.repo.ensureSelfAuthor() } }) { Text("ذخیره") } })
        }

        Section("سرور محله (نویسنده‌ها و افراد دارای اینترنت)") {
            if (!serverLocked) {
                Text("نشانی را از مدیر محله بگیرید. یک‌بار ثبت می‌شود و بعد پنهان می‌ماند؛ برای تغییر باید برنامه را حذف و دوباره نصب کنید.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = serverInput, onValueChange = { serverInput = it }, label = { Text("https://…/api.php") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Button(enabled = !busy && serverInput.trim().startsWith("http"), onClick = {
                    busy = true
                    scope.launch {
                        app.server.probeServer(serverInput).fold({ entry ->
                            val mine = prefs.community(entry.id)
                            when {
                                mine == null -> snack.showSnackbar("این سرور متعلق به محله «${entry.id}» است که دنبال نمی‌کنید؛ اول آن را دنبال کنید.")
                                mine.adminPub.isNotBlank() && mine.adminPub != entry.pub -> snack.showSnackbar("کلید این سرور با کلید محله «${entry.id}» یکی نیست")
                                else -> {
                                    if (mine.adminPub.isBlank()) app.repo.adoptAdminKey(entry.id, entry.name, entry.pub, verified = false)
                                    prefs.lockServer(serverInput.trim()); serverLocked = true
                                    snack.showSnackbar("سرور ثبت شد"); app.server.verifyCommunities()
                                }
                            }
                            busy = false
                        }, { busy = false; snack.showSnackbar("اتصال به این نشانی نشد") })
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال بررسی…" else "ثبت نهایی نشانی سرور") }
            } else {
                Text("نشانی سرور ثبت شده و پنهان است. " + (if (prefs.lastServerSync == 0L) "هنوز همگام نشده." else "آخرین همگام‌سازی ${relativeTime(prefs.lastServerSync)}."), fontSize = 12.5.sp)
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch { val r = app.server.sync(); busy = false; snack.showSnackbar(r.fold({ "دریافت ${it.received.fa()}، ارسال ${it.sent.fa()}" }, { syncError(it) })) }
                }, modifier = Modifier.fillMaxWidth()) { Text("همگام‌سازی با سرور") }
                if (!isWriter) {
                    HorizontalDivider()
                    Text("دریافت دسترسی نویسنده", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("نام کاربری") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("رمز") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy && user.isNotBlank() && pass.isNotBlank(), onClick = {
                        busy = true
                        scope.launch {
                            val r = app.server.login(user, pass); busy = false; pass = ""
                            snack.showSnackbar(r.fold({ "شما نویسنده شدید: ${it.name}" }, { if (it is ServerApi.WrongCommunity) syncError(it) else "ورود ناموفق: ${it.message}" }))
                            if (r.isSuccess) app.mesh.commands.tryEmit(MeshState.Command.ResyncAll)
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "…" else "ورود") }
                }
            }
        }

        Section("مدیریت", trailing = { TextButton(onClick = { showAdmin = !showAdmin }) { Text(if (showAdmin) "بستن" else "باز کردن") } }) {
            if (showAdmin) {
                if (adminSeed.isBlank()) {
                    Text("کلید مدیر را صفحه نصب سرور به شما داده است. فقط روی گوشی خودتان وارد کنید.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = seedInput, onValueChange = { seedInput = it }, label = { Text("کلید مدیر") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(enabled = seedInput.isNotBlank(), onClick = {
                        val pub = app.repo.pubOfSeed(seedInput)
                        scope.launch {
                            if (pub == null) { snack.showSnackbar("کلید مدیر درست نیست"); return@launch }
                            val match = communities.firstOrNull { it.adminPub == pub }
                            val pending = communities.filter { it.adminPub.isBlank() }
                            when {
                                match != null -> { prefs.setAdminSeed(seedInput.trim()); seedInput = ""; snack.showSnackbar("حالت مدیر محله «${match.id}» فعال شد") }
                                pending.size == 1 -> { app.repo.adoptAdminKey(pending[0].id, pending[0].name, pub, verified = false); prefs.setAdminSeed(seedInput.trim()); seedInput = ""; snack.showSnackbar("حالت مدیر محله «${pending[0].id}» فعال شد"); app.server.verifyCommunities() }
                                else -> snack.showSnackbar("این کلید با هیچ‌یک از محله‌های شما نمی‌خواند")
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("فعال‌سازی حالت مدیر") }
                } else {
                    Text(if (adminCommunity != null) "این گوشی مدیر محله «${adminCommunity.name.ifBlank { adminCommunity.id }}» است." else "کلید مدیر ذخیره شده ولی با محله‌های فعلی نمی‌خواند.", fontSize = 13.sp)
                    if (!isWriter && adminCommunity != null) Button(onClick = {
                        val c = app.repo.adminIssueCert(adminSeed, prefs.pub, name.ifBlank { "مدیر" })
                        scope.launch { if (c != null && app.repo.applyOwnCert(c)) { snack.showSnackbar("شما نویسنده شدید"); app.mesh.commands.tryEmit(MeshState.Command.ResyncAll) } else snack.showSnackbar("خطا در صدور مجوز") }
                    }, modifier = Modifier.fillMaxWidth()) { Text("خودم را نویسنده کن") }
                    Text("در «همسایه‌ها» کنار نام هر همسایه دکمه «نویسنده کن» دارید.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = ttl, onValueChange = { ttl = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("عمر هر مطلب تازه (روز)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { val v = ttl.toIntOrNull(); if (v != null && v in 1..365 && v != prefs.postTtlDays) TextButton(onClick = { prefs.postTtlDays = v }) { Text("ذخیره") } })
                    if (adminCommunity?.local == true) {
                        TextButton(onClick = { showSeed = !showSeed }) { Text(if (showSeed) "پنهان‌کردن کلید مدیر" else "نمایش کلید مدیر (برای پشتیبان‌گیری)") }
                        if (showSeed) Text(adminSeed, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp), textAlign = TextAlign.Left)
                    }
                    TextButton(onClick = { prefs.setAdminSeed("") }) { Text("حذف کلید مدیر از این گوشی") }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun Section(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(onBack: () -> Unit) {
    val app = App.instance
    val current by app.prefs.themeFlow.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("ظاهر برنامه") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } })
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("هر سه ظاهر داخل برنامه است و بدون اینترنت عوض می‌شود.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Skin.entries.forEach { s ->
                val on = s.id == current
                Card(onClick = { app.prefs.theme = s.id }, modifier = Modifier.fillMaxWidth().then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(
                            when (s) { Skin.INSTA -> Brush.sweepGradient(listOf(Color(0xFFF9CE34), Color(0xFFEE2A7B), Color(0xFF6228D7), Color(0xFFF9CE34)))
                                Skin.MAGAZINE -> Brush.linearGradient(listOf(Color(0xFFFBF8F2), Color(0xFFC9A24A)))
                                Skin.BROWSER -> Brush.linearGradient(listOf(Color(0xFF17181C), Color(0xFF4F8CFF))) }), contentAlignment = Alignment.Center) {
                            Text(when (s) { Skin.INSTA -> "◎"; Skin.MAGAZINE -> "¶"; Skin.BROWSER -> "⌂" }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title + if (s == Skin.INSTA) "  (پیش‌فرض)" else "", fontWeight = FontWeight.Bold)
                            Text(s.blurb, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (on) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(onBack: () -> Unit, go: (Screen) -> Unit) {
    val app = App.instance
    val ids by app.prefs.bookmarks.collectAsStateWithLifecycle()
    val flow = remember(ids) { app.repo.items().postsByIds(ids.toList().ifEmpty { listOf("-") }, app.prefs.pub) }
    val posts by flow.collectAsStateWithLifecycle(emptyList())
    val communities by app.prefs.communities.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("ذخیره‌شده‌ها") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } })
        if (posts.isEmpty()) Text("هنوز مطلبی ذخیره نکرده‌اید. در فید روی ستاره بزنید.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
            items(posts, key = { it.id }) { p ->
                Box(Modifier.aspectRatio(1f).padding(1.dp).clickable { go(Screen.Post(p.id)) }) {
                    if (p.image != null) ImageBytes(p.image, Modifier.fillMaxSize(), ContentScale.Crop, 0)
                    else TextTile(p.title.ifBlank { p.body.take(40) }, p.id, Modifier.fillMaxSize(), 12)
                }
            }
        }
    }
}

// =====================================================================================================
// post detail / new post / onboarding
// =====================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(id: String, snack: SnackbarHostState, onBack: () -> Unit, go: (Screen) -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val postFlow = remember(id) { app.repo.items().post(id, app.prefs.pub) }
    val commentsFlow = remember(id) { app.repo.items().comments(id) }
    val post by postFlow.collectAsStateWithLifecycle(null)
    val comments by commentsFlow.collectAsStateWithLifecycle(emptyList())
    val communities by app.prefs.communities.collectAsStateWithLifecycle()
    val bookmarks by app.prefs.bookmarks.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(post?.title?.ifBlank { "مطلب" } ?: "مطلب", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
            actions = {
                post?.let { p ->
                    IconButton(onClick = { app.prefs.toggleBookmark(p.id) }) { Icon(Icons.Default.Star, "ذخیره", tint = if (p.id in bookmarks) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface) }
                    IconButton(onClick = { sharePost(ctx, p, communities.firstOrNull { it.id == p.community }?.name ?: "") }) { Icon(Icons.Default.Share, "اشتراک‌گذاری") }
                }
            }
        )
        val p = post
        if (p == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Column }
        val communityName = communities.firstOrNull { it.id == p.community }?.name?.ifBlank { p.community } ?: (p.community ?: "")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.authorName ?: "?", p.author, 38, ring = palette.accentBrush)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.authorName ?: Crypto.shortId(p.author), fontWeight = FontWeight.Bold)
                        Text("$communityName · ${relativeTime(p.createdAt)} · ${remainingDays(p.expiresAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { p.community?.let { go(Screen.Community(it)) } })
                    }
                }
                p.image?.let { ImageBytes(it, Modifier.fillMaxWidth().heightIn(max = 420.dp), ContentScale.FillWidth, 0) }
                Row(Modifier.padding(8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { app.repo.like(p.id) } }) { Icon(if (p.likedByMe > 0) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "پسندیدن", tint = if (p.likedByMe > 0) palette.likeColor else MaterialTheme.colorScheme.onSurface) }
                    Text("${p.likeCount.fa()} پسند · ${p.commentCount.fa()} دیدگاه", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(16.dp, 4.dp)) {
                    if (p.title.isNotBlank()) Text(p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (p.body.isNotBlank()) Text(p.body, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp, modifier = Modifier.padding(top = 8.dp))
                }
                HorizontalDivider(Modifier.padding(top = 10.dp))
                Text("دیدگاه‌ها (${comments.size.fa()})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
            }
            items(comments, key = { it.id }) { c ->
                Row(Modifier.padding(16.dp, 8.dp)) {
                    Avatar(c.authorName ?: "?", c.author, 30); Spacer(Modifier.width(10.dp))
                    Column {
                        Row { Text(c.authorName ?: Crypto.shortId(c.author), fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.width(8.dp)); Text(relativeTime(c.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(c.body, fontSize = 13.5.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Avatar(app.prefs.displayName(), app.prefs.pub, 32); Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, placeholder = { Text("دیدگاه شما…") }, modifier = Modifier.weight(1f), maxLines = 4, shape = CircleShape)
            IconButton(enabled = text.isNotBlank(), onClick = {
                val t = text; text = ""
                scope.launch { app.repo.createComment(id, t).onFailure { snack.showSnackbar("ثبت دیدگاه نشد") } }
            }) { Icon(Icons.AutoMirrored.Filled.Send, "ارسال", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostScreen(snack: SnackbarHostState, onBack: () -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var image by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val bytes = withContext(Dispatchers.IO) { runCatching { compressImage(ctx, uri) }.onFailure { android.util.Log.e("Hamresan", "image compress failed", it) }.getOrNull() }
            busy = false
            if (bytes == null) snack.showSnackbar("این عکس قابل استفاده نیست") else image = bytes
        }
    }
    val wc = app.prefs.writerCommunity()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("مطلب تازه" + (wc?.let { " · ${it.name.ifBlank { it.id }}" } ?: "")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, "بستن") } },
            actions = { TextButton(enabled = !busy && (title.isNotBlank() || body.isNotBlank()), onClick = {
                busy = true
                scope.launch { val r = app.repo.createPost(title, body, image); busy = false; r.fold({ onBack() }, { snack.showSnackbar("انتشار نشد: ${it.message}") }) }
            }) { Text("انتشار", fontWeight = FontWeight.Bold) } })
        Column(Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            val img = image
            if (img != null) {
                Box {
                    ImageBytes(img, Modifier.fillMaxWidth().heightIn(max = 300.dp), ContentScale.FillWidth, 12)
                    IconButton(onClick = { image = null }, modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f), CircleShape)) { Icon(Icons.Default.Close, "حذف عکس") }
                }
                Text("حجم عکس: ${(img.size / 1024).fa()} کیلوبایت", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    Text(if (busy) "در حال فشرده‌سازی…" else "📷  افزودن عکس (اختیاری)")
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = title, onValueChange = { title = it.take(120) }, label = { Text("عنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = body, onValueChange = { body = it.take(4000) }, label = { Text("متن") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
            Spacer(Modifier.height(12.dp))
            Text("این مطلب ${app.prefs.postTtlDays.fa()} روز در شبکه می‌ماند و با امضای دیجیتال شما پخش می‌شود؛ پس از انتشار قابل ویرایش نیست.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Onboarding(neededPermissions: () -> Array<String>, onDone: () -> Unit) {
    val app = App.instance
    val palette = LocalPalette.current
    var name by rememberSaveable { mutableStateOf("") }
    var step by rememberSaveable { mutableStateOf(0) }
    var createName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }

    Scaffold { pad ->
        Column(Modifier.padding(pad).padding(24.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = if (step == 0) Arrangement.Center else Arrangement.Top) {
            Text("همرسان", fontSize = 40.sp, fontWeight = FontWeight.Black, style = MaterialTheme.typography.displaySmall.copy(brush = palette.accentBrush))
            Spacer(Modifier.height(8.dp))
            if (step == 0) {
                Text("شبکه اجتماعی محله، حتی بدون اینترنت. گوشی‌ها وقتی به هم نزدیک می‌شوند مطالب تازه را با هم رد و بدل می‌کنند.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("نام شما") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Button(onClick = { app.prefs.setName(name.trim()); step = 1 }, enabled = name.trim().length >= 2, modifier = Modifier.fillMaxWidth()) { Text("ادامه") }
            } else {
                Text("یک محله را دنبال کنید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("از فهرست عمومی، از محله‌های همسایه‌ها، یا خودتان یک محله بسازید.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                val nearby by app.mesh.nearby.collectAsStateWithLifecycle()
                nearby.values.forEach { n ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(n.name.ifBlank { n.id }, n.id, 44); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(n.name.ifBlank { n.id }, fontWeight = FontWeight.Bold); Text("از همسایه ${n.peerName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(onClick = { scope.launch { app.prefs.upsertCommunity(Prefs.Community(n.id, n.name, n.adminPub, false)); app.repo.ensureSelfAuthor(); launcher.launch(neededPermissions()) } }) { Text("دنبال کردن") }
                    }
                }
                CommunityPicker(onJoined = { launcher.launch(neededPermissions()) })
                Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                Text("یا محله خودتان را بسازید (بدون اینترنت)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(value = createName, onValueChange = { createName = it.take(60) }, label = { Text("نام محله") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = createName.trim().length >= 2 && !busy, onClick = {
                    busy = true
                    scope.launch { val c = app.repo.createLocalCommunity(createName); busy = false; if (c != null) launcher.launch(neededPermissions()) }
                }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("ساخت محله و نویسنده شدن") }
                Spacer(Modifier.height(10.dp))
                Text("برای پیدا کردن همسایه‌ها، برنامه به بلوتوث و وای‌فای نیاز دارد؛ بعد از انتخاب، اجازه‌ها را تأیید کنید.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
