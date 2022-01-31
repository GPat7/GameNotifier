package royal.game.notifier

import com.github.twitch4j.TwitchClient
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.events.ChannelChangeGameEvent
import com.github.twitch4j.events.ChannelGoLiveEvent
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class GameNotifier : ListenerAdapter() {

    private val jda: JDA
    private val streamers = HashMap<String, ArrayList<Subscription>>()
    private val twitchClient: TwitchClient
    private val properties: Properties

    init {
        properties = Properties()
        properties.load(FileInputStream(File("config.properties")))

        jda = JDABuilder.createDefault(properties.getProperty("discordToken")).build()
        jda.addEventListener(this)

        twitchClient = TwitchClientBuilder.builder()
        .withClientId(properties.getProperty("twitchClientId"))
            .withClientSecret(properties.getProperty("twitchSecret"))
            .withEnableHelix(true)
            .build()
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val msg = event.message
        if (msg.contentRaw.startsWith("!subGame")) {
            val commands = msg.contentRaw.split(" ")
            if(commands.size > 1) {
                val streamer = commands[1]
                //TODO Check for game
                val game = msg.contentRaw.substring(commands[0].length + commands[1].length + 2, msg.contentRaw.length)

                addGameSubscription(Subscription(streamer, game, event.author.id, false, event.channel))
            }
            else {
                errorMessage(event.channel)
            }
        }
        else if (msg.contentRaw.startsWith("!subNotGame")) {

            val commands = msg.contentRaw.split(" ")
            if(commands.size > 1) {
                val streamer = commands[1]
                val game = msg.contentRaw.substring(2, msg.contentRaw.length)

                addGameSubscription(Subscription(streamer, game, event.author.id, true, event.channel))
            }
            else {
                errorMessage(event.channel)
            }
        }
        else if (msg.contentRaw.startsWith("!getSubs")) {
            getSubs(event.author.id, event.channel)
        }
        else if (msg.contentRaw.startsWith("!clear")) {
            val commands = msg.contentRaw.split(" ")
            if(commands.size == 1) {
                removeSubscription(event.author.id, event.channel)
            }
            else {
                removeSubscription(event.author.id, commands[1], event.channel)
            }
        }
        else if(msg.contentRaw.startsWith("!help")) {
            event.channel.sendMessage("List of commands:\n" +
                    "```!subGame <streamer> <game>\n" +
                    "!subNotGame <streamer> <game>\n" +
                    "!getSubs\n" +
                    "!clear```").queue()
        }
    }

    private fun errorMessage(channel: MessageChannel) {
        channel.sendMessage("Command was not valid. Type !help for a list of valid commands").queue()
    }

    private fun getSubs(subscriber: String, channel: MessageChannel) {
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

        val subBuilder = StringBuilder().append("List of Subscriptions for <${subscriber}>:\n")
        for (subscription in subscriptions) {
            subBuilder.append("${subscription.streamer}: ${subscription.game}\n")
        }
        channel.sendMessage(subBuilder.toString()).queue()
    }

    //FIXME Getting duplicate messages
    private fun addGameSubscription(subscription: Subscription) {

        if(!streamers.containsKey(subscription.streamer)) {
            streamers[subscription.streamer] = arrayListOf(subscription)
            twitchClient.clientHelper.enableStreamEventListener(subscription.streamer)
            twitchClient.eventManager.onEvent(ChannelGoLiveEvent::class.java) { event ->
                for(sub: Subscription in streamers[subscription.streamer]!!) {
                    if (event.stream.gameName == sub.game && !sub.exclude) {
                        sub.channel.sendMessage("<@${sub.subscriber}>, ${sub.streamer} Just started playing ${sub.game}!").queue()
                    }
                    println("[${event.channel.name}] went live with title ${event.stream.title} on game ${event.stream.gameName}!");
                }
            }

            twitchClient.eventManager.onEvent(ChannelChangeGameEvent::class.java) { event ->
                for(sub: Subscription in streamers[subscription.streamer]!!) {
                    if ((event.stream.gameName == sub.game && !sub.exclude) || (event.stream.gameName != sub.game && sub.exclude)) {
                        println("${event.channel.name} started playing ${event.stream.gameName}")
                        sub.channel.sendMessage("<@${sub.subscriber}>, ${sub.streamer} Just started playing ${sub.game}!").queue()
                    }
                }
            }
        }
        else {
            streamers[subscription.streamer]?.add(subscription)
        }
        subscription.channel.sendMessage("<@${subscription.subscriber}>, subscription created for ${subscription.streamer} playing ${subscription.game}").queue()
    }

    private fun removeSubscription(subscriber: String, channel: MessageChannel) {
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

        channel.sendMessage("<@${subscriber}>, All Subscriptions removed").queue()
    }

    private fun removeSubscription(subscriber: String, streamer: String, channel: MessageChannel) {
        val iterator = streamers[streamer]?.iterator()
        if(iterator != null) {
            while(iterator.hasNext()) {
                val sub = iterator.next()
                if(sub.subscriber == subscriber) {
                    channel.sendMessage("<@${sub.subscriber}>, removing subscription for ${sub.streamer}").queue()
                    iterator.remove()
                }
            }
        }
        if(streamers[streamer].isNullOrEmpty()) {
            streamers.remove(streamer)
        }
    }

}