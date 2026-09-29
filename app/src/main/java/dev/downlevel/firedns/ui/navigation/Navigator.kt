package dev.downlevel.firedns.ui.navigation

import androidx.compose.runtime.mutableStateListOf

sealed interface Screen {
    data object Onboarding : Screen

    data object Home : Screen

    /** [profileId] `null` = new profile. */
    data class Editor(val profileId: String?) : Screen

    data object Settings : Screen

    data object Info : Screen
}

/** Minimal back stack: the app has few screens and no deep links. */
class Navigator(start: Screen) {
    private val stack = mutableStateListOf(start)

    val current: Screen get() = stack.last()

    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }

    fun replaceAll(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }
}
