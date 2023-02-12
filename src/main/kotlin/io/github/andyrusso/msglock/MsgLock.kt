package io.github.andyrusso.msglock

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.hud.ChatHud
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.command.EntitySelector
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

class MsgLock : ClientModInitializer {

    companion object {
        @JvmStatic
        var player: String? = null
        private var playerProper: String? = null

        @JvmStatic
        fun isOnline(name: String): Boolean {
            val playerListEntries: Collection<PlayerListEntry> =
                MinecraftClient.getInstance().networkHandler?.playerList ?: listOf()
            for (entry in playerListEntries) {
                if (entry.profile.name.lowercase() == name.lowercase()) {
                    playerProper = entry.profile.name
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun stripIfGenerated(message: String): String {
            if (player != null) {
                val modifiedMessage = "/w $player "
                if (message.startsWith(modifiedMessage)) {
                    return message.substring(modifiedMessage.length)
                }
            }
            return message
        }

        @JvmStatic
        fun cancelSendMessage(chatText: String, cir: CallbackInfoReturnable<Boolean>) {
            if (player != null && !isOnline(player!!) && !stripIfGenerated(chatText).startsWith("/")) {
                val chatHud: ChatHud = MinecraftClient.getInstance().inGameHud.chatHud
                chatHud.addToMessageHistory(stripIfGenerated(chatText))
                chatHud.addMessage(Text.translatable("msglock.not_online.unlock", player))
                player = null
                val client: MinecraftClient = MinecraftClient.getInstance()
                val sound =
                    PositionedSoundInstance(
                        SoundEvents.BLOCK_CHAIN_PLACE,
                        SoundCategory.MASTER,
                        Float.MAX_VALUE,
                        1F,
                        SoundInstance.createRandom(),
                        client.player?.x ?: 0.0,
                        client.player?.y ?: 0.0,
                        client.player?.z ?: 0.0,
                    )
                client.soundManager.play(sound)
                cir.returnValue = true
            }
        }

        @JvmStatic
        fun generate(chatText: String): String {
            return if (chatText.startsWith("/") || player == null) chatText else "/w $player $chatText"
        }
    }

    private fun rootCommand(context: CommandContext<FabricClientCommandSource>): Int {
        context.source.sendFeedback(
            if (player != null) Text.translatable("msglock.unlock")
            else Text.translatable("msglock.nounlock")
        )
        player = null
        return 0
    }

    private fun registerAlias(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        alias: String,
        command: LiteralCommandNode<FabricClientCommandSource>
    ) {
        dispatcher.register(ClientCommandManager.literal(alias).executes(this::rootCommand).redirect(command))
    }

    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val command: LiteralCommandNode<FabricClientCommandSource> = dispatcher.register(
                ClientCommandManager.literal("msglock")
                    .executes(this::rootCommand)
                    .then(
                        ClientCommandManager.argument("player", EntityArgumentType.player())
                            .executes {context ->
                                val name: String =
                                    context.getArgument("player", EntitySelector::class.java).playerName ?: ""
                                if (!isOnline(name)) {
                                    context.source.sendFeedback(
                                        if (player == null) Text.translatable("msglock.not_online.nolock", name)
                                        else Text.translatable("msglock.not_online.locked", name, player)
                                    )
                                    return@executes 0
                                }
                                context.source.sendFeedback(
                                    when (player) {
                                        null -> Text.translatable("msglock.lock", playerProper)
                                        playerProper -> Text.translatable("msglock.norelock", playerProper)
                                        else -> Text.translatable("msglock.relock", player, playerProper)
                                    }
                                )

                                player = playerProper
                                return@executes 0
                            }
                    )
            )
            registerAlias(dispatcher, "telllock", command)
            registerAlias(dispatcher, "wl", command)
        }
    }
}
