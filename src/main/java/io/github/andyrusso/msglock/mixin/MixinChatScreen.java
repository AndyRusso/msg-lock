package io.github.andyrusso.msglock.mixin;

import io.github.andyrusso.msglock.MsgLock;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @ModifyVariable(method = "sendMessage", at = @At(value = "STORE"), argsOnly = true)
    private String changeString(String chatText) {
        return MsgLock.generate(chatText);
    }

    @Inject(method = "sendMessage", at = @At(value = "HEAD"), cancellable = true)
    private void sendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        MsgLock.cancelSendMessage(chatText, ci);
    }

    @ModifyArg(
            method = "sendMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHud;addToMessageHistory(Ljava/lang/String;)V"
            )
    )
    private String changeHistory(String message) {
        return MsgLock.stripIfGenerated(message);
    }
}
