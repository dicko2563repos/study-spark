package com.studyspark.app.ui.navigation

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Quiz : Dest("quiz")
    data object Courses : Dest("courses")
    data object Agent : Dest("agent")
    data object Progress : Dest("progress")
    data object Settings : Dest("settings")
}
