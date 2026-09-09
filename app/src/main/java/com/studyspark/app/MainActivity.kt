package com.studyspark.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.studyspark.app.data.entity.QuizItemEntity
import com.studyspark.app.data.repository.StudyRepository
import com.studyspark.app.domain.QuizAnswerOutcome
import com.studyspark.app.domain.SessionRecap
import com.studyspark.app.notify.QuizScheduler
import com.studyspark.app.ui.agent.AgentScreen
import com.studyspark.app.ui.courses.CoursesScreen
import com.studyspark.app.ui.home.HomeScreen
import com.studyspark.app.ui.navigation.Dest
import com.studyspark.app.ui.progress.ProgressScreen
import com.studyspark.app.ui.quiz.QuizScreen
import com.studyspark.app.ui.settings.SettingsScreen
import com.studyspark.app.ui.theme.StudySparkTheme
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()

        val openQuiz = intent?.getBooleanExtra(EXTRA_OPEN_QUIZ, false) == true
        val app = application as StudySparkApp
        val repo = app.container.studyRepository
        val settingsRepo = app.container.settingsRepository

        setContent {
            StudySparkTheme {
                val nav = rememberNavController()
                val scope = rememberCoroutineScope()
                val profile by repo.observeProfile().collectAsStateWithLifecycle(initialValue = null)
                val topics by repo.observeTopics().collectAsStateWithLifecycle(initialValue = emptyList())
                val ready by repo.observeReadyCount().collectAsStateWithLifecycle(initialValue = 0)
                val courses by repo.observeCourses().collectAsStateWithLifecycle(initialValue = emptyList())
                val messages by repo.observeAgentMessages().collectAsStateWithLifecycle(initialValue = emptyList())
                val concepts by repo.observeConcepts().collectAsStateWithLifecycle(initialValue = emptyList())
                val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = settingsRepo.current())
                var nudge by remember { mutableStateOf<String?>(null) }
                var quizItem by remember { mutableStateOf<QuizItemEntity?>(null) }
                var choices by remember { mutableStateOf<List<String>>(emptyList()) }
                var selected by remember { mutableStateOf<Int?>(null) }
                var revealed by remember { mutableStateOf(false) }
                var quizOutcome by remember { mutableStateOf<QuizAnswerOutcome?>(null) }
                var quizStartedAt by remember { mutableStateOf(0L) }
                var agentBusy by remember { mutableStateOf(false) }
                var quizGenerating by remember { mutableStateOf(false) }
                var quizStatus by remember { mutableStateOf<String?>(null) }
                var quizPickReason by remember { mutableStateOf<String?>(null) }
                var quizSessionId by remember { mutableStateOf<String?>(null) }
                var quizTestMode by remember { mutableStateOf(false) }
                var quizSessionAnswered by remember { mutableStateOf(0) }
                var quizRecap by remember { mutableStateOf<SessionRecap?>(null) }

                fun newSessionId(): String = UUID.randomUUID().toString()

                fun startPractice() {
                    quizSessionId = newSessionId()
                    quizTestMode = false
                    quizSessionAnswered = 0
                    quizRecap = null
                    quizItem = null
                    revealed = false
                }

                fun startTest() {
                    quizSessionId = newSessionId()
                    quizTestMode = true
                    quizSessionAnswered = 0
                    quizRecap = null
                    quizItem = null
                    revealed = false
                }

                fun currentSessionId(): String {
                    val existing = quizSessionId
                    if (existing != null) return existing
                    val created = newSessionId()
                    quizSessionId = created
                    return created
                }

                suspend fun loadNextQuiz(topUpIfEmpty: Boolean = true) {
                    var pick = repo.nextQuizPick(testMode = quizTestMode)
                    if (pick == null && topUpIfEmpty) {
                        quizGenerating = true
                        quizStatus = "Generating quizzes…"
                        try {
                            quizStatus = repo.ensureQuizSupply()
                            pick = repo.nextQuizPick(testMode = quizTestMode)
                        } finally {
                            quizGenerating = false
                        }
                    } else if (topUpIfEmpty && ready < StudyRepository.LOW_WATER_MARK) {
                        scope.launch {
                            quizStatus = repo.ensureQuizSupply()
                        }
                    }
                    val next = pick?.item
                    quizPickReason = pick?.reason
                    quizItem = next
                    choices = next?.let { repo.parseChoices(it) } ?: emptyList()
                    selected = null
                    revealed = false
                    quizOutcome = null
                    quizStartedAt = System.currentTimeMillis()
                }

                suspend fun recordAndReveal(block: suspend (sessionId: String) -> QuizAnswerOutcome) {
                    val sid = currentSessionId()
                    quizOutcome = block(sid)
                    revealed = true
                    if (quizTestMode) quizSessionAnswered += 1
                    nudge = repo.nextStudyNudge()
                    quizStatus = repo.ensureQuizSupply()
                }

                suspend fun openRecap() {
                    quizRecap = repo.sessionRecap(currentSessionId())
                }

                LaunchedEffect(Unit) {
                    nudge = repo.nextStudyNudge()
                    repo.topUpKnowledgeQuizzesIfPossible()
                    if (openQuiz) nav.navigate(Dest.Quiz.route)
                }

                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route ?: Dest.Home.route

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            data class Tab(val dest: Dest, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)
                            val items = listOf(
                                Tab(Dest.Home, Icons.Outlined.Home, "Home"),
                                Tab(Dest.Quiz, Icons.Outlined.Quiz, "Quiz"),
                                Tab(Dest.Courses, Icons.Outlined.MenuBook, "Courses"),
                                Tab(Dest.Agent, Icons.Outlined.Chat, "Agent"),
                                Tab(Dest.Progress, Icons.Outlined.Timeline, "Progress"),
                                Tab(Dest.Settings, Icons.Outlined.Settings, "Settings")
                            )
                            items.forEach { tab ->
                                NavigationBarItem(
                                    selected = current == tab.dest.route,
                                    onClick = { nav.navigate(tab.dest.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = Dest.Home.route,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable(Dest.Home.route) {
                            HomeScreen(
                                streakDays = profile?.studyStreakDays ?: 0,
                                readyCount = ready,
                                nudge = nudge,
                                onStartQuiz = {
                                    startPractice()
                                    nav.navigate(Dest.Quiz.route)
                                },
                                onTestKnowledge = {
                                    startTest()
                                    nav.navigate(Dest.Quiz.route)
                                },
                                onOpenAgent = { nav.navigate(Dest.Agent.route) },
                                onOpenCourses = { nav.navigate(Dest.Courses.route) }
                            )
                        }
                        composable(Dest.Quiz.route) {
                            LaunchedEffect(quizSessionId, quizRecap) {
                                if (quizRecap != null) return@LaunchedEffect
                                if (quizSessionId == null) {
                                    quizSessionId = newSessionId()
                                    quizTestMode = false
                                    return@LaunchedEffect
                                }
                                if (quizItem == null || revealed) {
                                    loadNextQuiz(topUpIfEmpty = true)
                                }
                            }
                            val testDone = quizTestMode &&
                                quizSessionAnswered >= StudyRepository.TEST_SESSION_SIZE
                            QuizScreen(
                                item = quizItem,
                                choices = choices,
                                selected = selected,
                                revealed = revealed,
                                outcome = quizOutcome,
                                generating = quizGenerating,
                                statusMessage = quizStatus,
                                pickReason = quizPickReason,
                                recap = quizRecap,
                                sessionLabel = if (quizTestMode) {
                                    "Test ${quizSessionAnswered.coerceAtMost(StudyRepository.TEST_SESSION_SIZE)} / ${StudyRepository.TEST_SESSION_SIZE}"
                                } else {
                                    null
                                },
                                nextLabel = if (testDone) "See results" else "Next question",
                                onSeeResults = if (quizTestMode && quizSessionAnswered > 0) {
                                    { scope.launch { openRecap() } }
                                } else {
                                    null
                                },
                                onSelect = { selected = it },
                                onSubmit = {
                                    val item = quizItem ?: return@QuizScreen
                                    val choice = selected ?: return@QuizScreen
                                    scope.launch {
                                        recordAndReveal { sid ->
                                            repo.answerQuiz(item, choice, System.currentTimeMillis() - quizStartedAt, sessionId = sid)
                                        }
                                    }
                                },
                                onDontKnow = {
                                    val item = quizItem ?: return@QuizScreen
                                    scope.launch {
                                        recordAndReveal { sid ->
                                            repo.answerQuiz(
                                                item = item,
                                                selectedIndex = -1,
                                                latencyMs = System.currentTimeMillis() - quizStartedAt,
                                                unknown = true,
                                                sessionId = sid
                                            )
                                        }
                                    }
                                },
                                onNotFamiliar = {
                                    val item = quizItem ?: return@QuizScreen
                                    scope.launch {
                                        recordAndReveal { sid ->
                                            repo.markNotFamiliar(
                                                item,
                                                System.currentTimeMillis() - quizStartedAt,
                                                sessionId = sid
                                            )
                                        }
                                    }
                                },
                                onDontAskAgain = {
                                    val item = quizItem ?: return@QuizScreen
                                    scope.launch {
                                        recordAndReveal { sid ->
                                            repo.retireQuizItem(
                                                item,
                                                System.currentTimeMillis() - quizStartedAt,
                                                sessionId = sid
                                            )
                                        }
                                    }
                                },
                                onGenerateMore = {
                                    scope.launch {
                                        quizGenerating = true
                                        quizStatus = "Generating quizzes…"
                                        try {
                                            quizStatus = repo.ensureQuizSupply()
                                            loadNextQuiz(topUpIfEmpty = false)
                                        } finally {
                                            quizGenerating = false
                                        }
                                    }
                                },
                                onNext = {
                                    scope.launch {
                                        if (quizTestMode && quizSessionAnswered >= StudyRepository.TEST_SESSION_SIZE) {
                                            openRecap()
                                        } else {
                                            loadNextQuiz(topUpIfEmpty = true)
                                        }
                                    }
                                },
                                onBack = {
                                    if (quizRecap != null) {
                                        quizRecap = null
                                        quizTestMode = false
                                        quizSessionAnswered = 0
                                        quizItem = null
                                        nav.navigate(Dest.Home.route)
                                    } else {
                                        nav.navigate(Dest.Home.route)
                                    }
                                }
                            )
                        }
                        composable(Dest.Courses.route) {
                            CoursesScreen(
                                courses = courses,
                                onAdd = { title, module ->
                                    scope.launch {
                                        repo.addCourse(title, module.ifBlank { null })
                                        nudge = repo.nextStudyNudge()
                                    }
                                }
                            )
                        }
                        composable(Dest.Agent.route) {
                            AgentScreen(
                                messages = messages,
                                busy = agentBusy,
                                onSend = { text ->
                                    scope.launch {
                                        agentBusy = true
                                        repo.sendAgentMessage(text)
                                        agentBusy = false
                                    }
                                }
                            )
                        }
                        composable(Dest.Progress.route) {
                            ProgressScreen(topics = topics, concepts = concepts)
                        }
                        composable(Dest.Settings.route) {
                            SettingsScreen(
                                settings = settings,
                                topics = topics,
                                bankStatus = quizStatus,
                                onSettingsChange = { next ->
                                    settingsRepo.update { next }
                                    QuizScheduler.ensureScheduled(this@MainActivity, next)
                                },
                                onToggleTopic = { id, enabled ->
                                    scope.launch { repo.setTopicEnabled(id, enabled) }
                                },
                                onTopicDifficulty = { id, pref ->
                                    scope.launch { repo.setTopicDifficulty(id, pref) }
                                },
                                onTopicScope = { id, notes ->
                                    scope.launch { repo.setTopicScopeNotes(id, notes) }
                                },
                                onClearAnswered = {
                                    scope.launch { quizStatus = repo.clearAnsweredQuizzes() }
                                },
                                onClearAllQuizzes = {
                                    scope.launch {
                                        quizStatus = repo.clearAllQuizzes()
                                        quizItem = null
                                        quizPickReason = null
                                        choices = emptyList()
                                        selected = null
                                        revealed = false
                                        quizOutcome = null
                                    }
                                },
                                onRestoreSeeds = {
                                    scope.launch { quizStatus = repo.restoreSeedQuizzes() }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_QUIZ = "open_quiz"
    }
}
