package com.shohjahon.treble

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.CouponRepository
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.repository.NotificationRepository
import com.shohjahon.treble.notify.ReminderWorker
import com.shohjahon.treble.notify.AppState
import com.shohjahon.treble.notify.NavIntentState
import com.shohjahon.treble.notify.PushRepository
import com.shohjahon.treble.ui.screens.NotificationsScreen
import com.shohjahon.treble.ui.screens.DmScreen
import com.shohjahon.treble.data.repository.ChatRepository
import com.shohjahon.treble.data.repository.SettingsRepository
import com.shohjahon.treble.notify.ReminderScheduler
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.shohjahon.treble.ui.screens.ForgotPasswordScreen
import com.shohjahon.treble.ui.screens.GameDetailScreen
import com.shohjahon.treble.ui.screens.GameFormScreen
import com.shohjahon.treble.ui.screens.ProfilePageScreen
import com.shohjahon.treble.ui.screens.HomeScreen
import com.shohjahon.treble.ui.screens.IntroScreen
import com.shohjahon.treble.ui.screens.LoginScreen
import com.shohjahon.treble.ui.screens.RegistrationScreen
import com.shohjahon.treble.ui.screens.SplashScreen
import com.shohjahon.treble.ui.theme.BackgroundDark
import com.shohjahon.treble.ui.theme.TrebleTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private var askedNotificationPermission = false

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
    }

    override fun onStop() {
        AppState.foreground = false
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTabExtra(intent)
    }

    /** Bildirishnomaga bosib kirilsa, kerakli bo'lim (masalan, Chat) ochiladi. */
    private fun readTabExtra(intent: android.content.Intent?) {
        val tab = intent?.getIntExtra("tab", -1) ?: -1
        if (tab >= 0) NavIntentState.tab = tab
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readTabExtra(intent)

        // Saqlangan hisoblar, o'yinlar va oxirgi sessiyani yuklaymiz
        AuthRepository.init(applicationContext)
        GameRepository.init(applicationContext)
        CouponRepository.init(applicationContext)
        SettingsRepository.init(applicationContext)
        NotificationRepository.init(applicationContext)
        PushRepository.init(applicationContext)
        ReminderScheduler.init(applicationContext)
        ReminderWorker.schedule(applicationContext) // ilova yopiq bo'lsa ham xabarlar kelib turishi uchun

        // Ilova ochiq turganda ma'lumotlarni vaqti-vaqti bilan serverdan yangilab turamiz
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (AuthRepository.currentUser != null) {
                        AuthRepository.refreshMe()
                        GameRepository.refresh()
                        CouponRepository.refresh()
                        SettingsRepository.refresh()
                        NotificationRepository.sync(applicationContext, showSystem = false)
                        ChatRepository.refreshStatus() // chat belgisidagi o'qilmagan xabarlar
                        PushRepository.ensureRegistered() // telefon push xabar olishi uchun (bir marta)

                        // Android 13+: bildirishnoma ko'rsatish uchun ruxsat so'raladi (bir marta)
                        if (!askedNotificationPermission && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            askedNotificationPermission = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    delay(25_000L)
                }
            }
        }

        // Telefonda animatsiyalar butunlay o'chirilgan bo'lsa, kirish animatsiyasi ko'rsatilmaydi
        val animationsOff = try {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }

        setContent {
            TrebleTheme {
                var showSplash by rememberSaveable { mutableStateOf(!animationsOff) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    TrebleAppNavigation()
                    if (showSplash) {
                        SplashScreen(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }
}

@Composable
fun TrebleAppNavigation() {
    val navController = rememberNavController()

    // Hisobdan chiqilsa (yoki sessiya tugasa) istalgan sahifadan kirish sahifasiga qaytamiz
    val signedIn = AuthRepository.currentUser != null
    LaunchedEffect(signedIn) {
        if (!signedIn) {
            val route = navController.currentBackStackEntry?.destination?.route
            if (route != null && route !in listOf("intro", "login", "register", "forgot")) {
                navController.navigate("intro") {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    // Agar foydalanuvchi avval kirgan bo'lsa, to'g'ridan-to'g'ri bosh sahifaga o'tamiz
    val startDestination = remember {
        if (AuthRepository.currentUser != null) "home" else "intro"
    }

    // iOS uslubidagi silliq o'tish: yangi sahifa o'ngdan suriladi, eskisi biroz chapga siljib xiralashadi
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } +
                    fadeIn(tween(220))
        },
        exitTransition = {
            slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 3 } +
                    fadeOut(tween(300), targetAlpha = 0.6f)
        },
        popEnterTransition = {
            slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 3 } +
                    fadeIn(tween(300), initialAlpha = 0.6f)
        },
        popExitTransition = {
            slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } +
                    fadeOut(tween(260))
        }
    ) {
        composable("intro") {
            IntroScreen(
                onNavigateToRegister = { navController.navigate("register") },
                onNavigateToLogin = { navController.navigate("login") }
            )
        }

        composable("register") {
            RegistrationScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("intro") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("intro")
                    }
                }
            )
        }

        composable("login") {
            LoginScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("intro") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register") {
                        popUpTo("intro")
                    }
                },
                onNavigateToForgot = { navController.navigate("forgot") }
            )
        }

        composable("forgot") {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("intro") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onOpenGame = { id -> navController.navigate("game/$id") },
                onAddGame = { navController.navigate("gameform/new") },
                onOpenPage = { page -> navController.navigate("page/$page") },
                onLoggedOut = { /* chiqishni yuqoridagi umumiy kuzatuvchi boshqaradi */ },
                onOpenNotifications = { navController.navigate("notifications") },
                onOpenDm = { id, name ->
                    navController.navigate("dm/${android.net.Uri.encode(id)}/${android.net.Uri.encode(name)}")
                }
            )
        }

        composable(
            route = "dm/{userId}/{userName}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("userName") { type = NavType.StringType }
            )
        ) { entry ->
            DmScreen(
                peerId = entry.arguments?.getString("userId") ?: "",
                peerName = entry.arguments?.getString("userName") ?: "",
                onBack = { navController.popBackStack() }
            )
        }

        composable("notifications") {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenGame = { id -> navController.navigate("game/$id") }
            )
        }

        composable(
            route = "game/{gameId}",
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) { entry ->
            val gameId = entry.arguments?.getString("gameId") ?: ""
            GameDetailScreen(
                gameId = gameId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate("gameform/$id") },
                onCopy = { id -> navController.navigate("gameform/copy-$id") }
            )
        }

        composable(
            route = "gameform/{gameId}",
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) { entry ->
            val raw = entry.arguments?.getString("gameId") ?: "new"
            GameFormScreen(
                gameId = if (raw == "new" || raw.startsWith("copy-")) null else raw,
                copyFromId = if (raw.startsWith("copy-")) raw.removePrefix("copy-") else null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "page/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { entry ->
            val name = entry.arguments?.getString("name") ?: ""
            ProfilePageScreen(
                name = name,
                onBack = { navController.popBackStack() },
                onOpenPage = { page -> navController.navigate("page/$page") }
            )
        }
    }
}
