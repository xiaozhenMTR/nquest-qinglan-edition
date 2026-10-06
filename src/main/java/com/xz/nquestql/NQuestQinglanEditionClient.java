package com.xz.nquestql;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

@SuppressWarnings("deprecation")
public class NQuestQinglanEditionClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenRegistry.register(NQuestQinglanEdition.QUEST_UI_TYPE, GenericContainerScreen::new);
    }
}
