package com.orbix.pixora.launcher.data.models

data class Story(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val coverImage: String = "",
    val glowColor: String = "#7C4DFF",
    val category: String = "STORIES",
    val intervalMinutes: Int = 30,
    val frames: List<StoryFrame> = emptyList(),
)

data class StoryFrame(
    val imageFile: String = "",
    val captionEs: String = "",
    val captionEn: String = "",
    val captionJa: String = "",
) {
    fun captionForLang(langIndex: Int): String = when (langIndex % 3) {
        0 -> captionEs
        1 -> captionEn
        2 -> captionJa
        else -> captionEn
    }
}

data class StoryCatalog(
    val stories: List<Story> = emptyList()
)
