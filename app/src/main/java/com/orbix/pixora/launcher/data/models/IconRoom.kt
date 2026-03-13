package com.orbix.pixora.launcher.data.models

data class IconRoom(
    val id: String,
    val title: String,
    val assetName: String,
) {
    companion object {
        val ALL = listOf(
            IconRoom("01", "Mystic Portal", "icon_room_01"),
            IconRoom("02", "Digital Dawn", "icon_room_02"),
            IconRoom("03", "Candy Kingdom", "icon_room_03"),
            IconRoom("04", "Neon District", "icon_room_04"),
            IconRoom("05", "Crystal Cave", "icon_room_05"),
            IconRoom("06", "Jungle Ruins", "icon_room_06"),
            IconRoom("07", "Luxury Penthouse", "icon_room_07"),
            IconRoom("08", "Space Station", "icon_room_08"),
            IconRoom("09", "Steampunk Lab", "icon_room_09"),
            IconRoom("10", "Zen Garden", "icon_room_10"),
            IconRoom("11", "Cyber Alley", "icon_room_11"),
            IconRoom("12", "Enchanted Forest", "icon_room_12"),
            IconRoom("13", "Haunted Mansion", "icon_room_13"),
            IconRoom("14", "Wild West", "icon_room_14"),
            IconRoom("15", "Retro Arcade", "icon_room_15"),
            IconRoom("16", "Frozen Palace", "icon_room_16"),
            IconRoom("17", "Egyptian Temple", "icon_room_17"),
            IconRoom("18", "Atlantis Lab", "icon_room_18"),
            IconRoom("19", "Tron Grid", "icon_room_19"),
            IconRoom("20", "Synthwave", "icon_room_20"),
            IconRoom("21", "Voxel World", "icon_room_21"),
            IconRoom("22", "Retro Office", "icon_room_22"),
            IconRoom("23", "Neon Gallery", "icon_room_23"),
            IconRoom("24", "Beach Resort", "icon_room_24"),
            IconRoom("25", "Gothic Castle", "icon_room_25"),
            IconRoom("26", "Penthouse Night", "icon_room_26"),
        )
    }
}
