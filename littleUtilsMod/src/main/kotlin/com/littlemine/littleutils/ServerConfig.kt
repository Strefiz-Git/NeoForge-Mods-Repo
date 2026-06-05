package com.littlemine.littleutils

import net.neoforged.neoforge.common.ModConfigSpec

class ServerConfig(builder: ModConfigSpec.Builder) {

    val durabilityWarning: ModConfigSpec.BooleanValue = builder
        .comment("Shows durability warnings on the action bar when holding a damaged item")
        .define("durabilityWarning", true)

    val durabilityYellowPercent: ModConfigSpec.IntValue = builder
        .comment("Durability percentage for yellow warning (0-100)")
        .defineInRange("durabilityYellowPercent", 50, 1, 100)

    val durabilityRedPercent: ModConfigSpec.IntValue = builder
        .comment("Durability percentage for red warning (0-100)")
        .defineInRange("durabilityRedPercent", 5, 1, 100)

    val durabilityRedUses: ModConfigSpec.IntValue = builder
        .comment("Remaining uses for red warning")
        .defineInRange("durabilityRedUses", 25, 1, 10000)

    val deathCoords: ModConfigSpec.BooleanValue = builder
        .comment("Sends death coordinates in chat when a player dies")
        .define("deathCoords", true)

    val fastSleep: ModConfigSpec.BooleanValue = builder
        .comment("Enables fast sleep (skip night when enough players sleep)")
        .define("fastSleep", true)

    val fastSleepPercent: ModConfigSpec.DoubleValue = builder
        .comment("Percentage of players needed to sleep to skip night (0.0-100.0)")
        .defineInRange("fastSleepPercent", 50.0, 1.0, 100.0)

    val craftCommand: ModConfigSpec.BooleanValue = builder
        .comment("Enables /craft command to open a crafting table from anywhere")
        .define("craftCommand", true)

    val afkEnabled: ModConfigSpec.BooleanValue = builder
        .comment("Enables AFK detection and chat announcements")
        .define("afkEnabled", true)

    val afkTimeoutMinutes: ModConfigSpec.IntValue = builder
        .comment("Minutes of inactivity before a player is marked AFK")
        .defineInRange("afkTimeoutMinutes", 5, 1, 60)
}
