package dev.downlevel.firedns.ui

import dev.downlevel.firedns.ui.navigation.Navigator
import dev.downlevel.firedns.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    @Test
    fun `push and pop`() {
        val nav = Navigator(Screen.Home)
        assertFalse(nav.canGoBack)
        nav.push(Screen.Settings)
        nav.push(Screen.Info)
        assertEquals(Screen.Info, nav.current)
        nav.pop()
        assertEquals(Screen.Settings, nav.current)
        nav.pop()
        assertEquals(Screen.Home, nav.current)
    }

    @Test
    fun `pop on the root does nothing`() {
        val nav = Navigator(Screen.Home)
        nav.pop()
        assertEquals(Screen.Home, nav.current)
    }

    @Test
    fun `after onboarding home becomes the root`() {
        val nav = Navigator(Screen.Onboarding)
        nav.replaceAll(Screen.Home)
        assertEquals(Screen.Home, nav.current)
        assertFalse(nav.canGoBack)
        nav.push(Screen.Editor(profileId = null))
        assertTrue(nav.canGoBack)
    }
}
