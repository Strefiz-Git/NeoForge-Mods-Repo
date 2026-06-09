package com.littlemine.webbrowser.config

import net.neoforged.neoforge.common.ModConfigSpec

object MonitorConfig {
    lateinit var CLIENT_SPEC: ModConfigSpec

    var renderScreens: ModConfigSpec.BooleanValue? = null
    var maxMonitorsRendered: ModConfigSpec.IntValue? = null
    var maxResolutionPerBlock: ModConfigSpec.IntValue? = null
    var logBrowserLifecycle: ModConfigSpec.BooleanValue? = null

    lateinit var COMMON_SPEC: ModConfigSpec

    var maxMonitorsPerPlayer: ModConfigSpec.IntValue? = null

    fun registerClientConfigs() {
        val builder = ModConfigSpec.Builder()
        builder.push("rendering")
        renderScreens = builder.comment("Toggle global para renderizar/ocultar todos os ecrãs no mundo").define("renderScreens", true)
        maxMonitorsRendered = builder.comment("Máximo de monitores renderizados ao mesmo tempo (0 = ilimitado)").defineInRange("maxMonitorsRendered", 8, 0, 256)
        maxResolutionPerBlock = builder.comment("Máximo de pixels por bloco").defineInRange("maxResolutionPerBlock", 120, 4, 512)
        builder.pop()
        builder.push("debug")
        logBrowserLifecycle = builder.comment("Log detalhado de criação/destruição de browsers").define("logBrowserLifecycle", false)
        builder.pop()
        CLIENT_SPEC = builder.build()
    }

    fun registerCommonConfigs() {
        val builder = ModConfigSpec.Builder()
        builder.push("limits")
        maxMonitorsPerPlayer = builder.comment("Máximo de monitores ativos por jogador (-1 = ilimitado)").defineInRange("maxMonitorsPerPlayer", -1, -1, 256)
        builder.pop()
        COMMON_SPEC = builder.build()
    }
}
