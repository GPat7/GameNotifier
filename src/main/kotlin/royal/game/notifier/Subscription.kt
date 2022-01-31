package royal.game.notifier

import net.dv8tion.jda.api.entities.MessageChannel

data class Subscription(
    val streamer: String,
    val game: String,
    val subscriber: String,
    val exclude: Boolean,
    val channel: MessageChannel
)