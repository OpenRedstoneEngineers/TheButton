package org.openredstone.button

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.FireworkEffect.Type
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.time.Instant
import java.util.*

import kotlin.random.Random

fun Double.roll() = Random.nextDouble() <= this

fun Location.centerOffset() = this.clone().add(Vector(0.5, 0.5, 0.5))

fun randomRgbColor() = Color.fromRGB((0..255).random(), (0..255).random(), (0..255).random())

fun String.colorify() =
    TextComponent(this).apply { color = ChatColor.values().random() }

fun Player.shortTitle(title: String?, subtitle: String?, fadeIn: Boolean = true, fadeOut: Boolean = true) =
    this.sendTitle(title, subtitle, if (fadeIn) 10 else 0, 50, if (fadeOut) 20 else 0)

val availablePotionEffects = listOf(
    Pair(PotionEffectType.SPEED, 255),
    Pair(PotionEffectType.BLINDNESS, 255),
    Pair(PotionEffectType.SLOW, 255), // essentially frozen
    Pair(PotionEffectType.CONFUSION, 255),
    Pair(PotionEffectType.JUMP, 33), // fun and avoids "mOvEd To QuIcKlY"
    Pair(PotionEffectType.INVISIBILITY, 0),
    Pair(PotionEffectType.BLINDNESS, 0)
)

class LimitedLinkedList<E>(private val limit: Int = 10) : LinkedList<E>() {
    var full: Boolean = false
    override fun add(element: E): Boolean {
        if (size == limit) full = true
        if (size >= limit) poll()
        return super.add(element)
    }
    override fun remove(): E {
        val removedElement = super.remove()
        if (size < limit) full = false
        return removedElement
    }
}

class TheButton : JavaPlugin(), Listener, CommandExecutor {
    private lateinit var buttonLocation: Location
    private lateinit var buttonPressStatements: List<String>
    private lateinit var enterTimeoutStatements: List<String>
    private lateinit var inTimeoutStatements: List<String>
    private var joinMessage = false
    private var actionProbability = 0.02
    private val presses = mutableMapOf<UUID, LimitedLinkedList<Instant>>()
    private val timeout = mutableMapOf<UUID, Instant>()
    private val configActions = mutableMapOf<String, Boolean>()
    private var kickCapoAction = false

    override fun onEnable() {
        saveDefaultConfig()
        loadConfig()
        server.pluginManager.registerEvents(this, this)
        getCommand("thebutton")?.setExecutor(this)
        logger.info("Button press statements: $buttonPressStatements")
        logger.info("Enter Timeout statements: $enterTimeoutStatements")
        logger.info("In Timeout statements: $inTimeoutStatements")
        logger.info("Current actions: $configActions")
        logger.info("Loaded!")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false
        if (!sender.hasPermission("thebutton.tp")) return false
        if (args.isEmpty()) {
            sender.teleport(buttonLocation.centerOffset())
            sender.sendMessage("This is the button...")
            return true
        }
        if (!sender.hasPermission("thebutton.manage")) return false
        val argument = args[0]
        when (argument) {
            "setbutton" -> {
                sender.sendMessage("not implemented yet (smh)")
            }
            "reload" -> {
                loadConfig(true)
                sender.sendMessage("Button location: ${buttonLocation.world?.name}, ${buttonLocation.x}," +
                    " ${buttonLocation.y}, ${buttonLocation.z}.")
                sender.sendMessage("Loaded ${buttonPressStatements.size} button press statements.")
                sender.sendMessage("Loaded ${enterTimeoutStatements.size} enter timeout statements.")
                sender.sendMessage("Loaded ${inTimeoutStatements.size} in timeout statements.")
                sender.sendMessage("Set action probability to $actionProbability.")
                sender.sendMessage("Loaded ${configActions.filter { it.value }.keys.size} enabled actions.")
                sender.sendMessage("Reloaded TheButton!")
            }
        }
        return true
    }

    private fun loadConfig(reload: Boolean = false) {
        if (reload) reloadConfig()
        config.run {
            buttonLocation = Location(
                server.getWorld(getString("button.world")!!),
                getInt("button.x").toDouble(),
                getInt("button.y").toDouble(),
                getInt("button.z").toDouble(),
                0.0F,
                0.0F
            )
            joinMessage = getBoolean("joinMessage")
            buttonPressStatements = getStringList("statements.button")
            enterTimeoutStatements = getStringList("statements.enterTimeout")
            inTimeoutStatements = getStringList("statements.inTimeout")
            actionProbability = getDouble("actionProbability")
            kickCapoAction = getBoolean("kickcapo")
            getConfigurationSection("actions")?.getKeys(false)?.forEach { key ->
                configActions[key] = getBoolean("actions.$key")
            }
        }
    }

    private fun doAction(event: PlayerInteractEvent) {
        if (!actionProbability.roll()) {
            event.player.spigot().sendMessage(ChatMessageType.ACTION_BAR, buttonPressStatements.random().colorify())
            return
        }
        if (kickCapoAction && 0.001.roll()) {
            val capo = server.getPlayer("LordDecapo")
            val subtitle = if (capo == null) "Oops... capo was offline" else "dang you actually did it, bye capo"
            event.player.shortTitle("/kick LordDecapo", "")
            server.scheduler.scheduleSyncDelayedTask(this, {
                capo?.kickPlayer("${event.player.displayName} pressed the button")
                event.player.shortTitle("/kick LordDecapo", subtitle, false)
            }, 20 * 2)
            return
        }
        val availableActions = configActions.filter { it.value }.keys.toList() // Filter available actions
        when (availableActions.random()) {
            "kick" -> {
                event.player.shortTitle("Bye", "(you asked for it)", false)
                server.scheduler.scheduleSyncDelayedTask(this, {
                    event.player.kickPlayer("${event.player.displayName} pressed the button")
                }, 20 * 3)
            }
            "kill" -> {
                event.player.shortTitle("RIP", "", false)
                server.scheduler.scheduleSyncDelayedTask(this, {
                    event.player.health = 0.0
                }, 20)
            }
            "tp" -> {
                event.player.teleport(buttonLocation.clone().add(Vector(
                    (-300..300).random(),
                    (0..200).random().coerceIn(-63, 319),
                    (-300..300).random()
                )))
                event.player.shortTitle("yyeeEEEEEEEet", "")
            }
            "boat" -> {
                val boatLoc = event.player.location
                val world = event.player.world
                val boat = world.spawnEntity(boatLoc, EntityType.BOAT)
                boat.addPassenger(event.player)
                event.player.shortTitle("", "You're better off boating...")
            }
            "smite" -> {
                val playerLoc = event.player.location
                playerLoc.world?.strikeLightning(playerLoc)
                event.player.shortTitle("Get smote", "(apparently this is the proper past tense??)")
            }
            "night" -> {
                event.player.setPlayerTime(13000, false)
                event.player.shortTitle("Nightnight", "")
                server.scheduler.scheduleSyncDelayedTask(this, {
                    event.player.setPlayerTime(6000, false)
                    event.player.shortTitle("", "Ok fine, day time")
                }, 20 * 30)
            }
            "storm" -> {
                event.player.setPlayerWeather(WeatherType.DOWNFALL)
                event.player.shortTitle("Mood killer", "")
                server.scheduler.scheduleSyncDelayedTask(this, {
                    event.player.setPlayerWeather(WeatherType.CLEAR)
                    event.player.shortTitle("", "Guess you can't have a storm forever...")
                }, 20 * 30)
            }
            "potion" -> {
                val chosenPotionEffect = availablePotionEffects.random()
                event.player.addPotionEffect(PotionEffect(
                    chosenPotionEffect.first,
                    (30..60).random() * 20,
                    chosenPotionEffect.second
                ))
                val funny = listOf("smh", "xd", "get rekt").random()
                event.player.shortTitle("", "Enjoy some ${chosenPotionEffect.first.name.lowercase()}, $funny")
            }
            "gamemode" -> {
                val gameModes = GameMode.values().filter { it != event.player.gameMode }
                val chosenGameMode = gameModes.random()
                event.player.gameMode = chosenGameMode
                event.player.shortTitle(chosenGameMode.name.lowercase(), "You have been banished to ${chosenGameMode.name.lowercase()} mode")
            }
            "fireworks" -> {
                val firework = buttonLocation.world!!.spawnEntity(buttonLocation.centerOffset(), EntityType.FIREWORK) as Firework
                val fireworkMeta = firework.fireworkMeta
                fireworkMeta.power = 1
                for (i in 4..(5..10).random()) {
                    val fireworkEffect = FireworkEffect.builder()
                    fireworkEffect.apply {
                        this.withFlicker()
                        this.withTrail()
                        this.withColor(randomRgbColor())
                        this.withFade(randomRgbColor())
                        this.with(Type.values().random())
                    }
                    fireworkMeta.addEffect(fireworkEffect.build())
                }
                firework.fireworkMeta = fireworkMeta
                event.player.shortTitle("Zoomzoom", "")
            }
            "levitation" -> {
                event.player.addPotionEffect(PotionEffect(
                    PotionEffectType.LEVITATION,
                    (60..100).random(),
                    127 // any more and it doesn't have intended effect
                ))
                event.player.shortTitle("To infinity...", "...and beyond!")
            }
        }
    }

    private fun buttonPressed(event: PlayerInteractEvent) {
        val limitedLinkedList = presses.getOrPut(event.player.uniqueId) { LimitedLinkedList() }
        val now = Instant.now()
        limitedLinkedList.add(now)
        if (event.player.uniqueId in timeout &&
            timeout[event.player.uniqueId]!!.plusSeconds(60) >= now) {
            event.player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR, inTimeoutStatements.random().colorify()
            )
            return
        }
        if (limitedLinkedList.full &&
            limitedLinkedList.first.plusSeconds(2) > limitedLinkedList.last) {
            event.player.sendTitle("Timeout time", "(this doesn't last long... or does it?)")
            event.player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR, enterTimeoutStatements.random().colorify()
            )
            timeout[event.player.uniqueId] = now
            return
        }
        doAction(event)
    }

    @EventHandler
    private fun onJoin(event: PlayerJoinEvent) {
        if (!joinMessage) return
        if (!event.player.hasPermission("thebutton.tp")) return
        server.scheduler.scheduleSyncDelayedTask(this, {
            event.player.sendMessage("You should check out /thebutton ...")
        }, 20 * 2)
    }

    @EventHandler
    private fun onButtonPress(event: PlayerInteractEvent) {
        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.location != buttonLocation) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!clickedBlock.type.name.endsWith("_BUTTON")) return
        buttonPressed(event)
    }
}