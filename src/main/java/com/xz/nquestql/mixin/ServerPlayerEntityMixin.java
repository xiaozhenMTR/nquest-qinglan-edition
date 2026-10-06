package com.xz.nquestql.mixin;

import com.xz.nquestql.manager.TitleManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        // 只对 ServerPlayerEntity 生效
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        String titleName = TitleManager.getActiveTitleName(player.getUuid());
        if (titleName != null && !titleName.isEmpty()) {
            cir.setReturnValue(TitleManager.colorize("§7[" + titleName + "§7] §r").append(player.getName().copy()));
        }
    }
}
