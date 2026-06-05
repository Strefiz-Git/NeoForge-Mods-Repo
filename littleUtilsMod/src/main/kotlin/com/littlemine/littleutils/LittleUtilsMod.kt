package com.littlemine.littleutils

import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.CraftingMenu
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import org.apache.logging.log4j.LogManager
import kotlin.math.ceil

@Mod("littleutils")
class LittleUtilsMod(container: ModContainer) {
    companion object {
        val LOGGER = LogManager.getLogger("littleutils")
        lateinit var CONFIG: ServerConfig
    }

    init {
        CONFIG = ServerConfig(ModConfigSpec.Builder())

        val forgeBus = NeoForge.EVENT_BUS
        forgeBus.addListener(::onLivingDeath)
        forgeBus.addListener(::onRegisterCommands)
        forgeBus.addListener(::onServerTick)
        forgeBus.addListener(::onPlayerLogout)

        LOGGER.info("LittleUtils carregado!")
    }

    private fun onLivingDeath(event: LivingDeathEvent) {
        if (!CONFIG.deathCoords.get()) return
        val player = event.entity as? ServerPlayer ?: return
        val pos = player.blockPosition()
        val dim = player.level().dimension().location()
        val msg = "§cMorreste em §6X: ${pos.x} Y: ${pos.y} Z: ${pos.z} §cno §6$dim"
        player.sendSystemMessage(Component.literal(msg))
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        if (!CONFIG.craftCommand.get()) return
        event.dispatcher.register(
            Commands.literal("craft")
                .executes { ctx ->
                    val player = ctx.source.playerOrException
                    player.openMenu(SimpleMenuProvider(
                        { id, inv, p -> CraftingMenu(id, inv, ContainerLevelAccess.create(p.level(), p.blockPosition())) },
                        Component.translatable("container.crafting")
                    ))
                    1
                }
        )
    }

    private val durabilityState = mutableMapOf<String, String>()

    private fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        if (server.tickCount % 10 != 0) return

        for (player in server.playerList.players) {
            val held = player.mainHandItem
            if (CONFIG.durabilityWarning.get() && !held.isEmpty && held.isDamageableItem) {
                val maxDamage = held.maxDamage
                val remaining = maxDamage - held.damageValue
                val pct = (remaining.toDouble() / maxDamage.toDouble()) * 100.0

                val warningState = when {
                    pct <= CONFIG.durabilityRedPercent.get().toDouble() || remaining <= CONFIG.durabilityRedUses.get() -> "red"
                    pct <= CONFIG.durabilityYellowPercent.get().toDouble() -> "yellow"
                    else -> "ok"
                }

                val lastState = durabilityState[player.scoreboardName]
                if (warningState != lastState) {
                    when (warningState) {
                        "red" -> player.displayClientMessage(
                            Component.literal("§c⚠ §6${held.hoverName.string} §ccom apenas §4$remaining §cusos restantes!"),
                            true
                        )
                        "yellow" -> player.displayClientMessage(
                            Component.literal("§e⚠ §6${held.hoverName.string} §eestá com §7$remaining §cusos (${"%.0f".format(pct)}%)"),
                            true
                        )
                    }
                    durabilityState[player.scoreboardName] = warningState
                }
            } else {
                durabilityState.remove(player.scoreboardName)
            }

            if (CONFIG.fastSleep.get() && player.isSleeping) {
                val level = player.level() as ServerLevel
                val sleeping = level.players().count { it.isSleeping }
                val total = level.players().count { !it.isSpectator && it.isAlive }
                if (total > 0) {
                    val needed = ceil(total * CONFIG.fastSleepPercent.get() / 100.0).toInt()
                    if (sleeping >= needed) {
                        level.dayTime = 0L
                        for (p in level.players().filter { it.isSleeping }) {
                            p.stopSleepInBed(false, false)
                        }
                        LOGGER.info("Noite acelerada! ($sleeping/$total dormindo)")
                    }
                }
            }
        }

        if (CONFIG.afkEnabled.get() && server.tickCount % 20 == 0) {
            checkAFK(server)
        }
    }

    private val afkTracker = mutableMapOf<String, Long>()
    private val afkStatus = mutableMapOf<String, Boolean>()

    private fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val name = event.entity.scoreboardName
        if (afkStatus[name] == true) {
            val server = event.entity.server
            server?.playerList?.players?.forEach { p ->
                p.sendSystemMessage(Component.literal("§7$name saiu (estava AFK)"))
            }
        }
        afkTracker.remove(name)
        afkStatus.remove(name)
    }

    private fun checkAFK(server: net.minecraft.server.MinecraftServer) {
        val now = System.currentTimeMillis()
        val timeout = CONFIG.afkTimeoutMinutes.get() * 60 * 1000L

        for (player in server.playerList.players) {
            val name = player.scoreboardName
            val lastActive = afkTracker[name] ?: run {
                afkTracker[name] = now
                now
            }
            val isAfk = (now - lastActive) > timeout

            if (isAfk && afkStatus[name] != true) {
                afkStatus[name] = true
                server.playerList.players.forEach { p ->
                    p.sendSystemMessage(Component.literal("§e$name está agora AFK"))
                }
            } else if (!isAfk && afkStatus[name] == true) {
                afkStatus[name] = false
                server.playerList.players.forEach { p ->
                    p.sendSystemMessage(Component.literal("§a$name voltou"))
                }
            }
        }
    }
}
