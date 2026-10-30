package sy.medpro.watani

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import sy.medpro.watani.data.ClinicalCaseDto
import sy.medpro.watani.data.SubjectEnvelope
import sy.medpro.watani.ui.activation.ActivationPromptDialog
import sy.medpro.watani.ui.welcome.WelcomeScreen
import sy.medpro.watani.ui.cases.CasePlayerScreen
import sy.medpro.watani.ui.cases.CasesScreen
import sy.medpro.watani.ui.clinical.BedsideClinicalTab
import sy.medpro.watani.ui.courses.CoursesScreen
import sy.medpro.watani.ui.drawer.*
import sy.medpro.watani.ui.home.*
import sy.medpro.watani.ui.leaderboard.LeaderboardScreen
import sy.medpro.watani.ui.library.LibraryScreen
import sy.medpro.watani.ui.offline.EponymsAtlasScreen
import sy.medpro.watani.ui.offline.SmartStudyPlannerScreen
import sy.medpro.watani.ui.progress.ProgressDashboardScreen
import sy.medpro.watani.ui.quiz.QuizScreen
import sy.medpro.watani.ui.reference.*
import sy.medpro.watani.ui.simulator.ExamSimulatorScreen
import sy.medpro.watani.ui.osce.OsceStationsScreen
import sy.medpro.watani.ui.features.*
import sy.medpro.watani.ui.theme.GoldAccent
import androidx.compose.material3.ExperimentalMaterial3Api
import sy.medpro.watani.ui.theme.MaroonPrimary
import sy.medpro.watani.ui.theme.WataniTheme
import sy.medpro.watani.ui.viewmodel.AppTab
import sy.medpro.watani.ui.viewmodel.MainViewModel
import sy.medpro.watani.ui.viewmodel.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // TEAM-3 device-protection (single wiring line): FLAG_SECURE on this
        // window (same call as before, via SecurityChecks) + app-close sweep
        // of plaintext temp files. No auth-gate logic touched.
        sy.medpro.watani.ui.security.SecurityChecks.install(this)
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApiKey("AIzaSyBlHOicffOYb5W5REVlEsF4KT0gEhzQ-u8")
                .setApplicationId("1:660952704333:android:69eb215833b3cb5a5fc304")
                .setProjectId("national-exam-8ecba")
                .setStorageBucket("national-exam-8ecba.firebasestorage.app")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(this, options)
        }
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = remember { context.getSharedPreferences("watani_settings", android.content.Context.MODE_PRIVATE) }
            var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == "dark") {
                        darkTheme = sharedPreferences.getBoolean("dark", false)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            WataniTheme(darkTheme = darkTheme) {
                // Team 1: NO hard sign-in wall. Auth state is observed so
                // sign-in/out from any screen (welcome, profile, activation
                // prompt) refreshes the UI without a manual trigger flag.
                var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { auth -> currentUser = auth.currentUser }
                    FirebaseAuth.getInstance().addAuthStateListener(listener)
                    onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
                }
                // Welcome is first-run only (persisted): returning users
                // go straight to the main interface.
                var showWelcome by remember { mutableStateOf(!prefs.getBoolean("welcome_seen", false)) }
                var pendingTab by remember { mutableStateOf<AppTab?>(null) }
                var pendingDrawer by remember { mutableStateOf<String?>(null) }
                var showActivationPrompt by remember { mutableStateOf(false) }
                var gateDialogMessage by remember { mutableStateOf<String?>(null) }

                // Gateway revocation event (M3): any repository fetch that
                // gets 401/403 clears + wipes and emits here; the prompt
                // opens even when the user is deep in a restricted screen.
                LaunchedEffect(Unit) {
                    sy.medpro.watani.data.WorkerActivationApi.activationLostEvents.collect {
                        viewModel.refreshDownloadedSections()
                        showActivationPrompt = true
                    }
                }

                // Quiz-entry activation request (M4/M8): a restricted
                // quiz tapped without a live activation opens the prompt
                // instead of the quiz (see MainViewModel.openTopicQuiz).
                LaunchedEffect(Unit) {
                    viewModel.activationPromptRequests.collect {
                        showActivationPrompt = true
                    }
                }

                // Gating point (UI): ONLY Questions (BANK / UNIVERSITIES /
                // UWORLD) and Library require activation. Everything else
                // is free. A gated tab tap shows the activation prompt
                // instead of switching; on success the pending tab opens.
                // (The fetch layer enforces the same rule independently:
                // restricted paths without a token are never fetched.)
                // M8: the device-integrity gate is checked here too, so a
                // blocked device cannot enter restricted tabs at all.
                fun requestTab(tab: AppTab) {
                    val restricted = tab == AppTab.BANK || tab == AppTab.UNIVERSITIES ||
                        tab == AppTab.UWORLD || tab == AppTab.LIBRARY
                    if (restricted) {
                        val gate = sy.medpro.watani.ui.security.RestrictedContentGate.check(context)
                        if (!gate.allowed) {
                            gateDialogMessage = gate.userMessage
                            return
                        }
                        if (!sy.medpro.watani.data.WorkerActivationApi.isActivated(context)) {
                            pendingTab = tab
                            showActivationPrompt = true
                            return
                        }
                    }
                    viewModel.setTab(tab)
                }

                if (showWelcome) {
                    // Entry point: welcome screen, no sign-in required.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        WelcomeScreen(onEnter = {
                            prefs.edit().putBoolean("welcome_seen", true).apply()
                            showWelcome = false
                        })
                    }
                } else {
                    val contextForSync = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(currentUser) {
                        val user = currentUser
                        if (user != null) {
                            sy.medpro.watani.data.WataniFirestoreSync.ensureAndSyncUser(contextForSync, user)
                        }
                    }
                    val uiState by viewModel.uiState.collectAsState()
                    val downloadedSections by viewModel.downloadedSections.collectAsState()
                    val coroutineScope = rememberCoroutineScope()
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                    var casesList by remember { mutableStateOf<List<ClinicalCaseDto>>(emptyList()) }
                    var activeSubjectEnvelope by remember { mutableStateOf<SubjectEnvelope?>(null) }
                    var drawerScreen by remember { mutableStateOf<String?>(null) }

                    // Shared guarded entry for restricted drawer screens,
                    // used by drawer taps AND home shortcuts alike:
                    // device gate first, then activation; on success the
                    // screen opens, else the prompt (with a pending drawer
                    // target) or the gate message.
                    fun openRestrictedDrawer(title: String) {
                        val drawerGate = sy.medpro.watani.ui.security.RestrictedContentGate.check(context)
                        if (!drawerGate.allowed) {
                            gateDialogMessage = drawerGate.userMessage
                        } else if (!sy.medpro.watani.data.WorkerActivationApi.isActivated(context)) {
                            pendingDrawer = title
                            showActivationPrompt = true
                        } else {
                            drawerScreen = title
                        }
                    }

                    // M4: on activation loss, close restricted UI state
                    // too — an open quiz keeps its questions in memory,
                    // and the wipe only deletes files. (The outer
                    // collector above opens the prompt; this one resets
                    // the screens underneath it.)
                    LaunchedEffect(Unit) {
                        sy.medpro.watani.data.WorkerActivationApi.activationLostEvents.collect {
                            drawerScreen = null
                            viewModel.navigateToHome()
                            if (uiState.currentTab == AppTab.BANK || uiState.currentTab == AppTab.UNIVERSITIES ||
                                uiState.currentTab == AppTab.UWORLD || uiState.currentTab == AppTab.LIBRARY) {
                                viewModel.setTab(AppTab.HOME)
                            }
                        }
                    }

                    LaunchedEffect(downloadedSections) {
                        val casesEnv = viewModel.getCasesEnvelope()
                        casesList = casesEnv?.cases ?: emptyList()
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MedicalServices,
                                                contentDescription = null,
                                                tint = MaroonPrimary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    "Med Pro - وطني",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = MaroonPrimary
                                                )
                                                Text(
                                                    "نجاحك في الوطني يبدأ من هنا",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // تجميع 28 أكتوبر 2026 (بأمر الدكتور): كل فكرة واحدة تحت
                                            // تبويب واحد — القلب والإصغاء، المختبرات والحاسبات،
                                            // البطاقات والمراجعة، والتواصل — بلا حذف أي محتوى.
                                            val drawerItems = listOf(
                                                "الملف الشخصي",
                                                "الاشتراك وتفعيل الحساب",
                                                "الكورسات",
                                                "التحدي اليومي",
                                                "البطاقات والمراجعة الذكية",
                                                "دليل الدراسة",
                                                "سجل أخطائي والاختبارات",
                                                "محاكاة الامتحان",
                                                "المريض الافتراضي",
                                                "نقاط الضعف",
                                                "مؤقت التركيز",
                                                "الإنجازات والأوسمة",
                                                "البحث الشامل",
                                                "المفضلة",
                                                "ملاحظاتي",
                                                "إعدادات وسجل الإشعارات",
                                                "الخصوصية والشروط",
                                                "التواصل",
                                                "محطات OSCE",
                                                "القلب والإصغاء",
                                                "محاكاة المناوبة الليلية",
                                                "غرفة الطوارئ",
                                                "المختبرات والحاسبات",
                                                "تسجيل الخروج"
                                            )
                                            items(drawerItems) { title ->
                                                val icon = when (title) {
                                                    "الملف الشخصي" -> Icons.Default.Person
                                                    "الاشتراك وتفعيل الحساب" -> Icons.Default.VpnKey
                                                    "الكورسات" -> Icons.Default.MenuBook
                                                    "التحدي اليومي" -> Icons.Default.Whatshot
                                                    "البطاقات والمراجعة الذكية" -> Icons.Default.Style
                                                    "دليل الدراسة" -> Icons.Default.Book
                                                    "سجل أخطائي والاختبارات" -> Icons.Default.History
                                                    "محاكاة الامتحان" -> Icons.Default.Quiz
                                                    "المريض الافتراضي" -> Icons.Default.LocalHospital
                                                    "نقاط الضعف" -> Icons.Default.Assessment
                                                    "مؤقت التركيز" -> Icons.Default.Timer
                                                    "الإنجازات والأوسمة" -> Icons.Default.WorkspacePremium
                                                    "البحث الشامل" -> Icons.Default.Search
                                                    "المفضلة" -> Icons.Default.Favorite
                                                    "ملاحظاتي" -> Icons.Default.NoteAlt
                                                    "إعدادات وسجل الإشعارات" -> Icons.Default.Settings
                                                    "الخصوصية والشروط" -> Icons.Default.Security
                                                    "التواصل" -> Icons.Default.SupportAgent
                                                    "محطات OSCE" -> Icons.Default.MedicalServices
                                                    "القلب والإصغاء" -> Icons.Default.MonitorHeart
                                                    "محاكاة المناوبة الليلية" -> Icons.Default.Nightlight
                                                    "غرفة الطوارئ" -> Icons.Default.LocalHospital
                                                    "المختبرات والحاسبات" -> Icons.Default.Science
                                                    else -> Icons.Default.Logout
                                                }
                                                NavigationDrawerItem(
                                                    icon = { Icon(icon, contentDescription = null, tint = MaroonPrimary) },
                                                    label = { Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                                    selected = false,
                                                    onClick = {
                                                        coroutineScope.launch { drawerState.close() }
                                                        if (title == "تسجيل الخروج") {
                                                            // AuthStateListener above refreshes currentUser;
                                                            // sign-out no longer drops the user to a gate —
                                                            // the app stays browsable, signed out.
                                                            // M2: activation is cleared AND restricted
                                                            // downloads are wiped, so a second user on
                                                            // this device inherits nothing.
                                                            FirebaseAuth.getInstance().signOut()
                                                            sy.medpro.watani.data.WorkerActivationApi.clearActivationAndWipe(context)
                                                            viewModel.refreshDownloadedSections()
                                                        } else if (title == "المريض الافتراضي") {
                                                            drawerScreen = null
                                                            viewModel.setTab(AppTab.CASES)
                                                        } else if (title == "محاكاة الامتحان" || title == "التحدي اليومي" ||
                                                            title == "البطاقات والمراجعة الذكية" || title == "البطاقات" ||
                                                            title == "المراجعة الذكية Anki") {
                                                            // M8 + activation: these drawer screens
                                                            // read downloaded question banks directly
                                                            // (sections_cache), so they must pass the
                                                            // same gates as the bank tabs instead of
                                                            // opening ungated from the drawer.
                                                            openRestrictedDrawer(title)
                                                        } else {
                                                            drawerScreen = title
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = {
                                            Column {
                                                Text(
                                                    "Med Pro - وطني",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = androidx.compose.ui.graphics.Color.White
                                                )
                                                Text(
                                                    "نجاحك في الوطني يبدأ من هنا",
                                                    fontSize = 11.sp,
                                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        },
                                        navigationIcon = {
                                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                                Icon(
                                                    Icons.Default.Menu,
                                                    contentDescription = "القائمة",
                                                    tint = androidx.compose.ui.graphics.Color.White
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary)
                                    )
                                },
                                bottomBar = {
                                    NavigationBar {
                                        val tabs = listOf(
                                            Triple(AppTab.HOME, "الرئيسية", Icons.Default.Home),
                                            Triple(AppTab.BANK, "بنك الأسئلة", Icons.Default.Book),
                                            Triple(AppTab.CASES, "المريض الافتراضي", Icons.Default.LocalHospital),
                                            Triple(AppTab.LIBRARY, "المكتبة", Icons.Default.Book),
                                            Triple(AppTab.LEADERBOARD, "الصدارة", Icons.Default.EmojiEvents),
                                            Triple(AppTab.PROGRESS, "تقدمي", Icons.Default.WorkspacePremium)
                                        )
                                        tabs.forEach { (tab, label, icon) ->
                                            val selected = uiState.currentTab == tab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { requestTab(tab) },
                                                icon = {
                                                    Icon(
                                                        icon,
                                                        contentDescription = label,
                                                        tint = if (selected) MaroonPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        label,
                                                        color = if (selected) MaroonPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Box(modifier = Modifier.padding(innerPadding)) {
                                    AnimatedContent(
                                        targetState = drawerScreen,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                                        },
                                        label = "drawerScreenTransition"
                                    ) { activeDrawer ->
                                        if (activeDrawer != null) {
                                            when (activeDrawer) {
                                                "الملف الشخصي" -> ProfileScreen(onBack = { drawerScreen = null })
                                                "الاشتراك وتفعيل الحساب" -> ActivationScreen(
                                                    onBack = { drawerScreen = null },
                                                    onActivationChanged = { viewModel.refreshDownloadedSections() }
                                                )
                                                "الكورسات" -> CoursesScreen(onBack = { drawerScreen = null })
                                                "التحدي اليومي" -> DailyChallengeScreen(onBack = { drawerScreen = null })
                                                "البطاقات والمراجعة الذكية" -> FlashcardsReviewScreen(onBack = { drawerScreen = null })
                                                "البطاقات" -> FlashcardsReviewScreen(initialTab = 0, onBack = { drawerScreen = null })
                                                "دليل الدراسة" -> StudyGuideScreen(onBack = { drawerScreen = null })
                                                "سجل أخطائي والاختبارات" -> WrongAnswersScreen(onBack = { drawerScreen = null })
                                                "المراجعة الذكية Anki" -> FlashcardsReviewScreen(initialTab = 1, onBack = { drawerScreen = null })
                                                "محاكاة الامتحان" -> ExamSimulatorScreen(onBack = { drawerScreen = null })
                                                "نقاط الضعف" -> WeakPointsScreen(onBack = { drawerScreen = null })
                                                "مؤقت التركيز" -> FocusTimerScreen(onBack = { drawerScreen = null })
                                                "الإنجازات والأوسمة" -> BadgesScreen(onBack = { drawerScreen = null })
                                                "البحث الشامل" -> GlobalSearchScreen(onBack = { drawerScreen = null })
                                                "المفضلة" -> FavoritesScreen(onBack = { drawerScreen = null })
                                                "ملاحظاتي" -> MyNotesScreen(onBack = { drawerScreen = null })
                                                "إعدادات وسجل الإشعارات" -> SettingsScreen(onBack = { drawerScreen = null })
                                                "الخصوصية والشروط" -> PrivacyScreen(onBack = { drawerScreen = null })
                                                "التواصل" -> ContactScreen(onBack = { drawerScreen = null })
                                                "التواصل مع الإدارة" -> ContactScreen(onBack = { drawerScreen = null })
                                                "روابط التواصل" -> ContactScreen(onBack = { drawerScreen = null })
                                                "خوارزميات التدبير" -> ClinicalAlgorithmsScreen(onBack = { drawerScreen = null })
                                                "الخطوط العلاجية الأولى" -> FirstLineDrugsScreen(onBack = { drawerScreen = null })
                                                "النقاط الذهبية" -> GoldenPearlsScreen(onBack = { drawerScreen = null })
                                                "موسوعة المتلازمات" -> EponymsAtlasScreen(onBack = { drawerScreen = null })
                                                "مخطط جدول الدراسة" -> SmartStudyPlannerScreen(onBack = { drawerScreen = null })
                                                // مجموعات 28 أكتوبر: الأسماء القديمة أسماء مستعارة تفتح
                                                // المجموعة على التبويب المقابل، فلا تنكسر روابط الرئيسية.
                                                "القلب والإصغاء" -> HeartAuscultationScreen(onBack = { drawerScreen = null })
                                                "مدرب تخطيط القلب" -> HeartAuscultationScreen(initialTab = 0, onBack = { drawerScreen = null })
                                                "أطلس تخطيط القلب" -> HeartAuscultationScreen(initialTab = 1, onBack = { drawerScreen = null })
                                                "مدرب الإصغاء" -> HeartAuscultationScreen(initialTab = 2, onBack = { drawerScreen = null })
                                                "المختبرات والحاسبات" -> LabsCalculatorsScreen(onBack = { drawerScreen = null })
                                                "مختبر غازات الدم" -> LabsCalculatorsScreen(initialTab = 0, onBack = { drawerScreen = null })
                                                "مختبر القيم المخبرية" -> LabsCalculatorsScreen(initialTab = 1, onBack = { drawerScreen = null })
                                                "الحاسبات السريرية الموسعة" -> LabsCalculatorsScreen(initialTab = 2, onBack = { drawerScreen = null })
                                                "الحاسبات السريرية" -> LabsCalculatorsScreen(initialTab = 3, onBack = { drawerScreen = null })
                                                "حاسبات العناية والسوائل" -> LabsCalculatorsScreen(
                                                    initialTab = 4,
                                                    initialBedsideTab = BedsideClinicalTab.ICU_INFUSIONS,
                                                    onBack = { drawerScreen = null }
                                                )
                                                "الصادات والكلية والأطفال" -> LabsCalculatorsScreen(
                                                    initialTab = 4,
                                                    initialBedsideTab = BedsideClinicalTab.RENAL_ANTIBIOTICS,
                                                    onBack = { drawerScreen = null }
                                                )
                                                "محطات OSCE" -> OsceStationsScreen(onBack = { drawerScreen = null })
                                                "محاكاة المناوبة الليلية" -> NightShiftSimulatorScreen(onBack = { drawerScreen = null })
                                                "غرفة الطوارئ" -> EmergencyRoomScreen(onBack = { drawerScreen = null })
                                                else -> ProfileScreen(onBack = { drawerScreen = null })
                                            }
                                        } else {
                                            when (val screen = uiState.screenState) {
                                                is ScreenState.Home -> {
                                                    when (uiState.currentTab) {
                                                        AppTab.HOME -> {
                                                            HomeDashboardScreen(
                                                                manifestVersion = uiState.manifestVersion,
                                                                onNavigateBank = { requestTab(AppTab.BANK) },
                                                                onNavigateCases = { viewModel.setTab(AppTab.CASES) },
                                                                onNavigateLeaderboard = { viewModel.setTab(AppTab.LEADERBOARD) },
                                                                onOpenDailyChallenge = { openRestrictedDrawer("التحدي اليومي") },
                                                                onOpenAlgorithms = { drawerScreen = "خوارزميات التدبير" },
                                                                onOpenFirstLineDrugs = { drawerScreen = "الخطوط العلاجية الأولى" },
                                                                onOpenGoldenPearls = { drawerScreen = "النقاط الذهبية" },
                                                                onOpenEponyms = { drawerScreen = "موسوعة المتلازمات" },
                                                                onOpenEcgAtlas = { drawerScreen = "أطلس تخطيط القلب" },
                                                                onOpenStudyPlanner = { drawerScreen = "مخطط جدول الدراسة" },
                                                                onOpenCalculators = { drawerScreen = "الحاسبات السريرية الموسعة" },
                                                                onOpenIcuAndDosing = { drawerScreen = "حاسبات العناية والسوائل" },
                                                                onOpenRenalAndPedsDosing = { drawerScreen = "الصادات والكلية والأطفال" }
                                                            )
                                                        }
                                                        AppTab.BANK, AppTab.UNIVERSITIES, AppTab.UWORLD -> {
                                                            // M8: re-checked at composition, so a device
                                                            // that becomes blocked mid-session (debugger
                                                            // attached after entry) cannot keep browsing
                                                            // restricted tabs.
                                                            val tabGate = remember(uiState.currentTab) {
                                                                sy.medpro.watani.ui.security.RestrictedContentGate.check(context)
                                                            }
                                                            if (!tabGate.allowed) {
                                                                GateBlockedMessage(
                                                                    message = tabGate.userMessage
                                                                        ?: "المحتوى المحمي غير متاح على هذا الجهاز.",
                                                                    onBackHome = { requestTab(AppTab.HOME) }
                                                                )
                                                            } else if (!sy.medpro.watani.data.WorkerActivationApi.isActivated(context)) {
                                                                // M4: activation expired mid-session —
                                                                // never render restricted content from
                                                                // local files without a live activation.
                                                                ActivationNeededMessage(
                                                                    onOpenActivation = {
                                                                        pendingTab = uiState.currentTab
                                                                        showActivationPrompt = true
                                                                    },
                                                                    onBackHome = { requestTab(AppTab.HOME) }
                                                                )
                                                            } else {
                                                                HomeScreen(
                                                                    currentTab = uiState.currentTab,
                                                                    manifestVersion = uiState.manifestVersion,
                                                                    files = uiState.files,
                                                                    isLoadingManifest = uiState.isLoadingManifest,
                                                                    manifestError = uiState.manifestError,
                                                                    downloadedSections = downloadedSections,
                                                                    downloadingPaths = uiState.downloadingPaths,
                                                                    downloadError = uiState.downloadError,
                                                                    casesList = casesList,
                                                                    // M5: the in-bank tab selector goes
                                                                    // through requestTab (activation +
                                                                    // device gate), never setTab directly.
                                                                    onTabSelected = { requestTab(it) },
                                                                onRefreshManifest = { viewModel.fetchManifest() },
                                                                onDownloadSection = { path, entry -> viewModel.downloadSection(path, entry) },
                                                                onOpenSubject = { path, name, tab ->
                                                                    coroutineScope.launch {
                                                                        activeSubjectEnvelope = viewModel.getSubjectEnvelope(path)
                                                                        viewModel.openSubject(path, name, tab)
                                                                    }
                                                                },
                                                                onOpenUniversity = { key, name -> viewModel.openUniversity(key, name) },
                                                                onCaseClick = { caseItem -> viewModel.openCasePlayer(caseItem) }
                                                                )
                                                            }
                                                        }
                                                        AppTab.CASES -> {
                                                            if (casesList.isEmpty()) {
                                                                val casesEntry = uiState.files["cases.json"]
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(16.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    if (casesEntry != null) {
                                                                        Card(
                                                                            shape = RoundedCornerShape(16.dp),
                                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                                                            modifier = Modifier.fillMaxWidth()
                                                                        ) {
                                                                            Column(
                                                                                modifier = Modifier.padding(20.dp),
                                                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                                                            ) {
                                                                                Row(
                                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                                    modifier = Modifier.fillMaxWidth()
                                                                                ) {
                                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                                        Text(
                                                                                            text = "الحالات السريرية التفاعلية",
                                                                                            fontWeight = FontWeight.Bold,
                                                                                            fontSize = 18.sp,
                                                                                            color = MaterialTheme.colorScheme.onSurface
                                                                                        )
                                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                                        Text(
                                                                                            text = "عدد الحالات: ${casesEntry.count ?: 436} | الحجم: ${((casesEntry.size ?: 0) / 1024)} كيلوبايت",
                                                                                            fontSize = 13.sp,
                                                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                                                        )
                                                                                    }
                                                                                }
                                                                                val isDownloading = uiState.downloadingPaths.contains("cases.json")
                                                                                if (isDownloading) {
                                                                                    Row(
                                                                                        modifier = Modifier.fillMaxWidth(),
                                                                                        horizontalArrangement = Arrangement.Center
                                                                                    ) {
                                                                                        CircularProgressIndicator(
                                                                                            modifier = Modifier.size(36.dp),
                                                                                            color = MaroonPrimary,
                                                                                            strokeWidth = 3.dp
                                                                                        )
                                                                                    }
                                                                                } else {
                                                                                    Button(
                                                                                        onClick = { viewModel.downloadSection("cases.json", casesEntry) },
                                                                                        colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                                                                                        shape = RoundedCornerShape(8.dp),
                                                                                        modifier = Modifier.fillMaxWidth()
                                                                                    ) {
                                                                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                                        Text("تحميل الحالات السريرية", fontSize = 14.sp)
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    } else {
                                                                        Text(
                                                                            text = "لا توجد حالات متاحة حالياً",
                                                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                                            fontSize = 14.sp
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                CasesScreen(
                                                                    cases = casesList,
                                                                    onCaseClick = { caseItem -> viewModel.openCasePlayer(caseItem) }
                                                                )
                                                            }
                                                        }
                                                        AppTab.LEADERBOARD -> {
                                                            LeaderboardScreen()
                                                        }
                                                        AppTab.LIBRARY -> {
                                                            // M8: same mid-session re-check as the
                                                            // bank tabs (see above).
                                                            val libGate = remember(uiState.currentTab) {
                                                                sy.medpro.watani.ui.security.RestrictedContentGate.check(context)
                                                            }
                                                            if (!libGate.allowed) {
                                                                GateBlockedMessage(
                                                                    message = libGate.userMessage
                                                                        ?: "المحتوى المحمي غير متاح على هذا الجهاز.",
                                                                    onBackHome = { requestTab(AppTab.HOME) }
                                                                )
                                                            } else if (!sy.medpro.watani.data.WorkerActivationApi.isActivated(context)) {
                                                                // M4: same mid-session activation
                                                                // re-check as the bank tabs above.
                                                                ActivationNeededMessage(
                                                                    onOpenActivation = {
                                                                        pendingTab = AppTab.LIBRARY
                                                                        showActivationPrompt = true
                                                                    },
                                                                    onBackHome = { requestTab(AppTab.HOME) }
                                                                )
                                                            } else {
                                                                LibraryScreen()
                                                            }
                                                        }
                                                        AppTab.PROGRESS -> {
                                                            ProgressDashboardScreen(
                                                                 onOpenStudyPlanner = { drawerScreen = "مخطط جدول الدراسة" },
                                                                 onOpenEponyms = { drawerScreen = "موسوعة المتلازمات" },
                                                                 onOpenEcgAtlas = { drawerScreen = "أطلس تخطيط القلب" },
                                                                 onOpenCalculators = { drawerScreen = "الحاسبات السريرية" }
                                                             )
                                                        }
                                                    }
                                                }
                                                is ScreenState.TopicSelection -> {
                                                    val isDownloaded = downloadedSections.any { it.sectionPath == screen.path }
                                                    TopicSelectionScreen(
                                                        subjectName = screen.subjectName,
                                                        subjectEnvelope = activeSubjectEnvelope,
                                                        downloaded = isDownloaded,
                                                        onDownloadClick = {
                                                            uiState.files[screen.path]?.let { entry ->
                                                                viewModel.downloadSection(screen.path, entry)
                                                            }
                                                        },
                                                        onTopicClick = { topicName ->
                                                            viewModel.openTopicQuiz(screen.path, topicName)
                                                        },
                                                        onBack = { viewModel.navigateToHome() }
                                                    )
                                                }
                                                is ScreenState.UniversitySubjects -> {
                                                    UniversitySubjectsScreen(
                                                        universityName = screen.universityName,
                                                        universityKey = screen.universityKey,
                                                        files = uiState.files,
                                                        downloadedSections = downloadedSections,
                                                        downloadingPaths = uiState.downloadingPaths,
                                                        onDownloadSection = { path, entry -> viewModel.downloadSection(path, entry) },
                                                        onOpenSubject = { path, name ->
                                                            coroutineScope.launch {
                                                                activeSubjectEnvelope = viewModel.getSubjectEnvelope(path)
                                                                viewModel.openSubject(path, name, AppTab.UNIVERSITIES)
                                                            }
                                                        },
                                                        onBack = { viewModel.navigateToHome() }
                                                    )
                                                }
                                                is ScreenState.Quiz -> {
                                                    QuizScreen(
                                                        title = screen.title,
                                                        questions = screen.questions,
                                                        onBack = { viewModel.navigateToHome() }
                                                    )
                                                }
                                                is ScreenState.CasePlayer -> {
                                                    CasePlayerScreen(
                                                        caseItem = screen.caseItem,
                                                        onBack = { viewModel.navigateToHome() }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Device-gate block dialog (M8): from requestTab or
                    // from the ViewModel quiz-entry check. Dismissing
                    // clears both sources so it cannot stick.
                    val gateMessage = gateDialogMessage ?: uiState.gateMessage
                    if (gateMessage != null) {
                        AlertDialog(
                            onDismissRequest = {
                                gateDialogMessage = null
                                viewModel.clearGateMessage()
                            },
                            title = { Text("المحتوى المحمي غير متاح", fontWeight = FontWeight.Bold) },
                            text = { Text(gateMessage, fontSize = 14.sp) },
                            confirmButton = {
                                TextButton(onClick = {
                                    gateDialogMessage = null
                                    viewModel.clearGateMessage()
                                }) {
                                    Text("حسنًا")
                                }
                            }
                        )
                    }
                    // Activation prompt overlay (Team 1 gating point):
                    // shown instead of switching to a restricted tab.
                    if (showActivationPrompt) {
                        ActivationPromptDialog(
                            onDismiss = {
                                showActivationPrompt = false
                                pendingTab = null
                                pendingDrawer = null
                            },
                            onActivated = {
                                showActivationPrompt = false
                                val target = pendingTab
                                pendingTab = null
                                val drawerTarget = pendingDrawer
                                pendingDrawer = null
                                // M5: uniform path — activation just
                                // succeeded, so requestTab switches.
                                if (drawerTarget != null) {
                                    drawerScreen = drawerTarget
                                } else if (target != null) {
                                    requestTab(target)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * M4: full-screen block shown INSTEAD of a restricted tab's content when
 * the activation expired mid-session (no live activation, no local
 * content). Offers the activation prompt, or the way back home.
 */
@Composable
private fun ActivationNeededMessage(onOpenActivation: () -> Unit, onBackHome: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "انتهى التفعيل — هذا القسم يحتاج كود تفعيل ساريًا.",
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onOpenActivation,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("فتح شاشة التفعيل", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onBackHome) {
                Text("العودة إلى الرئيسية")
            }
        }
    }
}

/**
 * M8: full-screen block shown INSTEAD of a restricted tab's content when
 * the device gate blocks mid-session. Free sections keep working; this
 * screen only replaces restricted content, and offers the way back home.
 */
@Composable
private fun GateBlockedMessage(message: String, onBackHome: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onBackHome,
                colors = ButtonDefaults.buttonColors(containerColor = MaroonPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("العودة إلى الرئيسية", fontWeight = FontWeight.Bold)
            }
        }
    }
}
