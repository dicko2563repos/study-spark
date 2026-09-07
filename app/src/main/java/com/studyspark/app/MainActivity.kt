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

                suspend fun loadNextQuiz(topUpIfEmpty: Boolean = true) {
                    var pick = repo.nextQuizPick()
                    if (pick == null && topUpIfEmpty) {
                        quizGenerating = true
                        quizStatus = "Generating quizzes…"
                        try {
                            quizStatus = repo.ensureQuizSupply()
                            pick = repo.nextQuizPick()
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
                                onStartQuiz = { nav.navigate(Dest.Quiz.route) },
                                onOpenAgent = { nav.navigate(Dest.Agent.route) },
                                onOpenCourses = { nav.navigate(Dest.Courses.route) }
                            )
                        }
                        composable(Dest.Quiz.route) {
                            LaunchedEffect(Unit) {
                                if (quizItem == null || revealed) {
                                    loadNextQuiz(topUpIfEmpty = true)
                                }
                            }
                            QuizScreen(
                                item = quizItem,
                                choices = choices,
                                selected = selected,
                                revealed = revealed,
                                outcome = quizOutcome,
                                generating = quizGenerating,
                                statusMessage = quizStatus,
                                pickReason = quizPickReason,
                                onSelect = { selected = it },
                                onSubmit = {
                                    val item = quizItem ?: return@QuizScreen
                                    val choice = selected ?: return@QuizScreen
                                    scope.launch {
                                        val latency = System.currentTimeMillis() - quizStartedAt
                                        quizOutcome = repo.answerQuiz(item, choice, latency)
                                        revealed = true
                                        nudge = repo.nextStudyNudge()
                                        quizStatus = repo.ensureQuizSupply()
                                    }
                                },
                                onDontKnow = {
                                    val item = quizItem ?: return@QuizScreen
                                    scope.launch {
                                        val latency = System.currentTimeMillis() - quizStartedAt
                                        quizOutcome = repo.answerQuiz(
                                            item = item,
                                            selectedIndex = -1,
                                            latencyMs = latency,
                                            unknown = true
                                        )
                                        revealed = true
                                        nudge = repo.nextStudyNudge()
                                        quizStatus = repo.ensureQuizSupply()
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
                                        loadNextQuiz(topUpIfEmpty = true)
                                    }
                                },
                                onBack = { nav.navigate(Dest.Home.route) }
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
                            ProgressScreen(topics = topics)
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
