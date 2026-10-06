package com.xz.nquestql.gui;

import com.xz.nquestql.NQuestQinglanEdition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class QuestUiScreenHandler extends GenericContainerScreenHandler {

    public enum UiType {
        MAIN_MENU,
        QUEST_LIST,
        TITLE_SHOP,
        MY_TITLES
    }

    private UiType type = UiType.MAIN_MENU;

    // 客户端重建用（固定 6 行，避免不同页面行数不一致）
    public QuestUiScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(NQuestQinglanEdition.QUEST_UI_TYPE, syncId, playerInventory, new SimpleInventory(54), 6);
    }

    public QuestUiScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, UiType type) {
        super(NQuestQinglanEdition.QUEST_UI_TYPE, syncId, playerInventory, inventory, 6);
        this.type = type;
    }

    public UiType getUiType() {
        return type;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }
        ItemStack stack = this.getSlot(slotIndex).getStack();
        if (stack.isEmpty()) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }

        // 阻止把物品拿出来
        if (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE || actionType == SlotActionType.SWAP) {
            GuiManager.onClick(serverPlayer, this.type, slotIndex, stack);
            return;
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }
}
