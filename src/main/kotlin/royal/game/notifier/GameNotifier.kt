package royal.game.notifier

import com.github.twitch4j.TwitchClient
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.events.ChannelChangeGameEvent
import com.github.twitch4j.events.ChannelGoLiveEvent
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class GameNotifier : ListenerAdapter() {

    private val jda: JDA
    private val streamers = HashMap<String, ArrayList<Subscription>>()
    private val twitchClient: TwitchClient
    private val properties: Properties = Properties()

    private val ADD_SUB = "addsub"
    private val CLEAR_SUBS = "clearsubs"
    private val GET_SUBS = "getsubs"
    private val HELP = "help"
    private val EXCLUDE = "exclude"

    private val STREAMER = "streamer"
    private val GAME = "game"


    init {
        properties.load(FileInputStream(File(System.getProperty("ConfigFile"))))

        jda = JDABuilder.createDefault(properties.getProperty("discordToken")).build()
        jda.addEventListener(this)

        twitchClient = TwitchClientBuilder.builder()
        .withClientId(properties.getProperty("twitchClientId"))
            .withClientSecret(properties.getProperty("twitchSecret"))
            .withEnableHelix(true)
            .build()

        twitchClient.eventManager.onEvent(ChannelGoLiveEvent::class.java) { event ->
            if(streamers.isNotEmpty()) {
                for (sub: Subscription in streamers[event.channel.name]!!) {
                    if (event.stream.gameName.lowercase(Locale.getDefault()) == sub.game.lowercase(Locale.getDefault()) && !sub.exclude) {
                        sub.channel.sendMessage("<@${sub.subscriber}>, ${sub.streamer} Just started playing ${sub.game}!")
                            .queue()
                    }
                    println("[${event.channel.name}] went live with title ${event.stream.title} on game ${event.stream.gameName}!");
                }
            }
        }

        twitchClient.eventManager.onEvent(ChannelChangeGameEvent::class.java) { event ->
            if(streamers.isNotEmpty()) {
                for (sub: Subscription in streamers[event.channel.name]!!) {
                    if ((event.stream.gameName.lowercase(Locale.getDefault()) == sub.game.lowercase(Locale.getDefault()) && !sub.exclude) || (event.stream.gameName != sub.game && sub.exclude)) {
                        println("${event.channel.name} started playing ${event.stream.gameName}")
                        sub.channel.sendMessage("<@${sub.subscriber}>, ${sub.streamer} Just started playing ${sub.game}!")
                            .queue()
                    }
                }
            }
        }

        jda.awaitReady()
        addCommands()
    }

    private fun addCommands() {
        val commands = ArrayList<CommandData>()

        var command = CommandData(ADD_SUB, "Adds a subscription for a streamer playing a specific game")
        command.addOption(OptionType.STRING, STREAMER, "The Streamer to Subscribe to", true)
        command.addOption(OptionType.STRING, GAME, "Game to subscribe to for the streamer", true)
        commands.add(command)

        command = CommandData(CLEAR_SUBS, "Remove all subscriptions from all streamers or a specific streamer")
        command.addOption(OptionType.STRING, STREAMER, "The Streamer to remove all subscriptions from")
        commands.add(command)

        command = CommandData(GET_SUBS, "Gets a list of all current subscriptions")
        commands.add(command)

        command = CommandData(HELP, "Returns the list of commands that can be called")
        commands.add(command)

        command = CommandData(EXCLUDE, "Adds a subscription for a streamer playing all EXCEPT a specific game")
        command.addOption(OptionType.STRING, STREAMER, "The Streamer to Subscribe to", true)
        command.addOption(OptionType.STRING, GAME, "Game to be excluded from for the streamer", true)
        commands.add(command)


        //Debug code
        //jda.getGuildById(731300630378971138)?.upsertCommand(command)

        for (commandData in commands) {
            jda.upsertCommand(commandData).queue()
        }


    }

    override fun onSlashCommand(event: SlashCommandEvent) {

        when(event.name) {
            ADD_SUB -> {
                val streamer = event.getOption(STREAMER)?.asString?.lowercase(Locale.getDefault())
                val game = event.getOption(GAME)?.asString?.lowercase(Locale.getDefault())
                if(streamer != null && game != null) {
                    event.reply(addGameSubscription(Subscription(streamer, game, event.user.id, false, event.channel))).queue()
                }
                else {
                    event.reply("Unable to create subscription. Check command and try again").queue()
                }
            }
            CLEAR_SUBS -> {
                val streamer = event.getOption(STREAMER)
                if(streamer == null) {
                    event.reply(removeSubscription(event.user.id)).queue()
                }
                else {
                    event.reply(removeSubscription(event.user.id, streamer.asString)).queue()
                }
            }
            GET_SUBS -> {
                event.reply(getSubs(event.user.id)).queue()
            }
            HELP -> {
                event.reply("List of commands:\n" +
                        "```/${ADD_SUB} <streamer> <game>\n" +
                        "/${EXCLUDE} <streamer> <game>\n" +
                        "/${GET_SUBS}\n" +
                        "/${CLEAR_SUBS} optional:<streamer>```").queue()
            }
            EXCLUDE -> {
                val streamer = event.getOption(STREAMER)?.asString?.lowercase(Locale.getDefault())
                val game = event.getOption(GAME)?.asString?.lowercase(Locale.getDefault())
                if(streamer != null && game != null) {
                    event.reply(addGameSubscription(Subscription(streamer, game, event.user.id, true, event.channel))).queue()
                }
                else {
                    event.reply("Unable to create subscription. Check command and try again").queue()
                }
            }
        }
    }

    private fun getSubs(subscriber: String): String {
        val subscriptions = ArrayList<Subscription>()
        val streamerIterator = streamers.entries.iterator()
        while (streamerIterator.hasNext()) {
            val entry = streamerIterator.next()
            val subIterator = entry.value.iterator()
            while(subIterator.hasNext()) {
                val subscription = subIterator.next()
                if(subscription.subscriber == subscriber) {
                    subscriptions.add(subscription)
                }
            }
        }

        val subBuilder = StringBuilder().append("List of Subscriptions for <@${subscriber}>:\n")
        for (subscription in subscriptions) {
            subBuilder.append("${subscription.streamer}: ${subscription.game}\n")
        }
        return subBuilder.toString()
    }
    
    private fun addGameSubscription(subscription: Subscription): String {
        //FIXME subs may still not be fully removed correctly. Not sure
        if(!streamers.containsKey(subscription.streamer)) {
            streamers[subscription.streamer] = arrayListOf(subscription)
            twitchClient.clientHelper.enableStreamEventListener(subscription.streamer)
        }
        else {
            streamers[subscription.streamer]?.add(subscription)
        }
        return "Subscription created for ${subscription.streamer} playing ${subscription.game}"
    }

    private fun removeSubscription(subscriber: String): String {
        val streamerIterator = streamers.entries.iterator()
        while (streamerIterator.hasNext()) {
            val entry = streamerIterator.next()
            val subIterator = entry.value.iterator()
            while(subIterator.hasNext()) {
                val subscription = subIterator.next()
                if(subscription.subscriber == subscriber) {
                    subIterator.remove()
                    if (entry.value.isEmpty()) {
                        twitchClient.clientHelper.disableStreamEventListener(subscription.streamer)
                        streamerIterator.remove()
                    }
                }
            }
        }
        return "All Subscriptions removed"
    }

    private fun removeSubscription(subscriber: String, streamer: String): String {
        val stringBuilder = StringBuilder("removing subscription for ")
        val iterator = streamers[streamer]?.iterator()
        if(iterator != null) {
            while(iterator.hasNext()) {
                val sub = iterator.next()
                if(sub.subscriber == subscriber) {
                    stringBuilder.append(sub.streamer)
                    iterator.remove()
                }
            }
        }
        if(streamers[streamer].isNullOrEmpty()) {
            streamers.remove(streamer)
        }
        return stringBuilder.toString()
    }
}