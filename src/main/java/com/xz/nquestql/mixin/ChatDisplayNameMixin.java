package com.xz.nquestql.mixin;

import com.xz.nquestql.manager.TitleManager;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 让聊天栏显示自定义称号。
 *
 * 1.20.1 启用签名聊天后，聊天栏的发送者名来自签名消息里的真实游戏名（签名校验），
 * 不会读取 getDisplayName()。因此仅改 ServerPlayerEntityMixin 的 getDisplayName()
 * 只对头顶/Tab 生效，聊天栏仍是原名。
 *
 * 这里在服务端向每个玩家广播签名聊天消息（sendChatMessage）时，若消息发送者佩戴了称号，
 * 则取消原签名广播，改为无签名（profileless）广播，并用发送者的称号+原名构造新的 params。
 * 无签名消息的发送者名由 MessageType.Parameters.name 决定，因此聊天栏会显示称号。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ChatDisplayNameMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Shadow
    public void sendProfilelessChatMessage(Text message, MessageType.Parameters params) {
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void nquest_onSendChat(SignedMessage message, MessageType.Parameters params, CallbackInfo ci) {
        UUID senderUuid = message.link().sender();
        String title = TitleManager.getActiveTitleName(senderUuid);
        if (title == null || title.isEmpty()) {
            return; // 发送者没佩戴称号，走原逻辑
        }

        // 通过 PlayerManager 获取真实发送者，以拿到原始玩家名
        if (player.getServer() == null) {
            return;
        }
        ServerPlayerEntity sender = player.getServer().getPlayerManager().getPlayer(senderUuid);
        if (sender == null) {
            return; // 发送者已离线或不是玩家，走原逻辑
        }

        ci.cancel();
        Text titledName = TitleManager.colorize("§7[" + title + "§7] §r").append(sender.getName().copy());
        // 保留原消息类型和目标名（如 /msg /teammsg），只把发送者名替换为带头衔的名字
        MessageType.Parameters titledParams = new MessageType.Parameters(params.type(), titledName, params.targetName());
        sendProfilelessChatMessage(message.getContent(), titledParams);
    }
}
