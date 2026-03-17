package com.orbix.pixora.launcher.ui.tutorial

import androidx.compose.ui.unit.dp

/**
 * Pre-defined tutorial steps for each screen.
 * Spotlight positions are fractions of screen dimensions (0..1).
 */
object TutorialSteps {

    val homeTour = listOf(
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.12f,
            spotlightRadius = 70.dp,
            title = "Your Clock",
            description = "The time is always visible. The bars below show seconds (SEC) and milliseconds (MS) in real-time. Double-tap the clock to open your alarms.",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.08f, spotlightY = 0.12f,
            spotlightRadius = 30.dp,
            title = "Explore Wallpapers",
            description = "Tap here to browse wallpapers, panoramic stories, and icon rooms. Find the perfect look for your home screen.",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.08f, spotlightY = 0.18f,
            spotlightRadius = 30.dp,
            title = "Settings",
            description = "Configure visual effects, sounds, backup your layouts, and set Pixora as your default launcher.",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.08f, spotlightY = 0.24f,
            spotlightRadius = 30.dp,
            title = "All Apps",
            description = "Tap here or swipe up from anywhere to open the app drawer. Your recent apps appear at the top!",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.55f,
            spotlightRadius = 90.dp,
            title = "Edit Mode",
            description = "Long-press anywhere on the home screen to enter edit mode. Then drag icons to rearrange, move between pages, or remove them.",
            tooltipPosition = TooltipPosition.ABOVE,
        ),
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.92f,
            spotlightRadius = 60.dp,
            title = "Your Dock",
            description = "Your favorite apps live here. Long-press a dock icon to remove it. Add apps from the drawer.",
            tooltipPosition = TooltipPosition.ABOVE,
        ),
    )

    val catalogTour = listOf(
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.1f,
            spotlightRadius = 50.dp,
            title = "Explore Content",
            description = "Browse through Wallpapers, Stories, and Icon Rooms. Each tab has unique content for your launcher.",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.16f,
            spotlightRadius = 40.dp,
            title = "Filter by Category",
            description = "Use categories to find exactly what you want — Anime, Gaming, Nature, Horror, and more.",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.5f,
            spotlightRadius = 80.dp,
            title = "Stories Are Special",
            description = "Stories are panoramic wallpapers that change automatically! They have subtitles and cycle through frames at your chosen interval.",
            tooltipPosition = TooltipPosition.ABOVE,
        ),
    )

    val editModeTour = listOf(
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.5f,
            spotlightRadius = 80.dp,
            title = "Drag to Move",
            description = "Tap and hold an icon, then drag it to a new position. Icons will swap places. Drag to the edge to move to another page.",
            tooltipPosition = TooltipPosition.ABOVE,
        ),
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.05f,
            spotlightRadius = 60.dp,
            title = "Edit Toolbar",
            description = "Clear all icons, auto-sort alphabetically, set the current page as your home page, or tap Done when finished.",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.92f,
            spotlightRadius = 50.dp,
            title = "Page Controls",
            description = "Add or remove pages with the +/- buttons. The highlighted dot shows your home page — the page that opens when you press the home button.",
            tooltipPosition = TooltipPosition.ABOVE,
        ),
    )

    val settingsTour = listOf(
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.2f,
            spotlightRadius = 60.dp,
            title = "Visual Effects",
            description = "Toggle Touch Glow, Equalizer, Battery Ring, System Rings, and Ambient Particles. Make it yours!",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.4f,
            spotlightRadius = 50.dp,
            title = "Sound Themes",
            description = "Choose between Cyberpunk, Zen, Arcade, or Minimal sounds. Each theme gives a unique feel to every interaction.",
            tooltipPosition = TooltipPosition.BELOW,
        ),
        TutorialStep(
            spotlightX = 0.5f, spotlightY = 0.6f,
            spotlightRadius = 50.dp,
            title = "Equalizer & Launcher",
            description = "Reconnect the equalizer if it stops responding, or set Pixora as your default home screen from here.",
            tooltipPosition = TooltipPosition.ABOVE,
        ),
    )
}
