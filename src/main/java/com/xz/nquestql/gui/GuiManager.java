package com.xz.nquestql.gui;

import com.xz.nquestql.data.QuestData;
import com.xz.nquestql.manager.QuestManager;
import com.xz.nquestql.manager.TitleManager;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;

public class GuiManager {

    private static final int BACK_SLOT = 49;
    private static final int CLEAR_SLOT = 53;

    public static void openMainMenu(ServerPlayerEntity player) {
        int qp = com.xz.nquestql.NQuestQinglanEdition.getInstance().getQPStorage().getQP(player.getUuid());
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> {
            SimpleInventory inventory = new SimpleInventory(54);
            fillMainMenu(inventory, qp, player);
            return new QuestUiScreenHandler(syncId, inv, inventory, QuestUiScreenHandler.UiType.MAIN_MENU);
        }, Text.literal("§6§lQLQuest")));
    }

    public static void openQuestList(ServerPlayerEntity player) {
        Collection<QuestData> quests = QuestManager.getAll();
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> {
            SimpleInventory inventory = new SimpleInventory(54);
            fillQuestList(inventory, quests, p);
            return new QuestUiScreenHandler(syncId, inv, inventory, QuestUiScreenHandler.UiType.QUEST_LIST);
        }, Text.literal("§6§l任务列表")));
    }

    public static void openTitleShop(ServerPlayerEntity player) {
        List<TitleManager.TitleDef> titles = TitleManager.getTitles();
        int qp = com.xz.nquestql.NQuestQinglanEdition.getInstance().getQPStorage().getQP(player.getUuid());

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> {
            SimpleInventory inventory = new SimpleInventory(54);
            fillTitleShop(inventory, titles, qp, player);
            return new QuestUiScreenHandler(syncId, inv, inventory, QuestUiScreenHandler.UiType.TITLE_SHOP);
        }, Text.literal("§6§l称号商店")));
    }

    public static void openMyTitles(ServerPlayerEntity player) {
        TitleManager.PlayerTitleData data = TitleManager.getOrCreate(player.getUuid());

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> {
            SimpleInventory inventory = new SimpleInventory(54);
            fillMyTitles(inventory, data);
            return new QuestUiScreenHandler(syncId, inv, inventory, QuestUiScreenHandler.UiType.MY_TITLES);
        }, Text.literal("§6§l我的称号")));
    }

    private static void fillMainMenu(SimpleInventory inv, int qp, ServerPlayerEntity player) {
        inv.clear();
        for (int i = 0; i < inv.size(); i++) inv.setStack(i, placeholder());

        String activeTitle = TitleManager.getActiveTitleName(player.getUuid());
        String activeLore = activeTitle != null ? "§a当前称号: " + activeTitle : "§7当前未佩戴称号";

        inv.setStack(11, iconItem(Items.BOOK, "§e§l任务列表", "§7点击查看可接任务", "§a点击进入"));
        inv.setStack(13, iconItem(Items.NAME_TAG, "§e§l称号商店", "§7点击打开称号商店", "§8购买称号消耗 QP"));
        inv.setStack(15, withGlow(iconItem(Items.CHEST, "§e§l我的称号", activeLore, "§7点击管理已拥有称号")));
        inv.setStack(21, iconItem(Items.PAPER, "§e§l兑换 CDK", "§7在聊天框输入 /qlquest redeem <CDK>"));
        inv.setStack(23, iconItem(Items.EMERALD, "§e§l我的 QP", "§6当前余额: " + qp + " QP", "§7QP 可用于购买称号"));
        inv.setStack(26, iconItem(Items.BARRIER, "§c§l关闭", "§7点击关闭界面"));
    }

    private static void fillQuestList(SimpleInventory inv, Collection<QuestData> quests, PlayerEntity player) {
        inv.clear();
        for (int i = 0; i < inv.size(); i++) inv.setStack(i, placeholder());

        // 按任务名称 A-Z（中文按拼音）排序
        List<QuestData> sorted = new java.util.ArrayList<>(quests);
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.CHINA);
        sorted.sort(java.util.Comparator.comparing(q -> plainName(q.questName), collator));

        inv.setStack(4, iconItem(Items.PAPER, "§6§l任务列表 §7(" + sorted.size() + " 个)",
                "§7按名称 A-Z 排序",
                "§a点击任务图标即可接取"));

        QuestManager.QuestState active = QuestManager.getActive(player.getUuid());

        int slot = 9;
        for (QuestData q : sorted) {
            if (slot >= BACK_SLOT) break;

            boolean isDaily = "daily".equals(q.type);
            boolean isOneTime = "one_time".equals(q.type);
            String typeStr = isDaily ? "§b每日任务" : (isOneTime ? "§d一次性任务" : "§a可重复任务");
            Item icon = isDaily ? Items.CLOCK : (isOneTime ? Items.BOOK : Items.WRITABLE_BOOK);

            boolean isActive = active != null && active.questId.equals(q.questId);
            boolean done = isOneTime && QuestManager.isOnceDone(player.getUuid(), q.questId);
            boolean doneToday = isDaily && QuestManager.isDailyDoneToday(player.getUuid(), q.questId);

            List<String> lore = new java.util.ArrayList<>();
            lore.add("§8§m                                    ");
            lore.add("§7类型: " + typeStr);
            lore.add("§7奖励: §6✦ " + q.qpReward + " QP");
            lore.add("§7步骤: §f" + (q.points != null ? q.points.size() : "?"));
            if (q.points != null && !q.points.isEmpty()) {
                int stepIdx = isActive && active.step < q.points.size() ? active.step : 0;
                var point = q.points.get(stepIdx);
                if (point.station != null && !point.station.isEmpty()) {
                    lore.add((isActive ? "§7当前目标: " : "§7首站: ") + "§f乘车至 §b" + point.station);
                }
            }
            lore.add("§8§m                                    ");
            if (isActive) lore.add("§e● 进行中");
            else if (done) lore.add("§a✔ 已完成");
            else if (doneToday) lore.add("§a✔ 今日已完成");
            else lore.add(isDaily ? "§a▶ 点击接取" : "§a▶ 点击开始");

            ItemStack stack = iconItem(icon, "§e" + q.questName, lore.toArray(new String[0]));
            if (isActive || done || doneToday) stack = withGlow(stack);
            inv.setStack(slot++, stack);
        }

        inv.setStack(BACK_SLOT, iconItem(Items.ARROW, "§7返回主菜单", ""));
    }

    private static void fillTitleShop(SimpleInventory inv, List<TitleManager.TitleDef> titles, int qp, ServerPlayerEntity player) {
        inv.clear();
        for (int i = 0; i < inv.size(); i++) inv.setStack(i, placeholder());

        TitleManager.PlayerTitleData data = TitleManager.getOrCreate(player.getUuid());

        // 顶部显示当前 QP
        inv.setStack(4, iconItem(Items.EMERALD, "§6§l当前 QP: " + qp, "§7购买称号会从这里扣款"));

        int slot = 9;
        for (TitleManager.TitleDef t : titles) {
            if (slot >= inv.size() - 9) break;
            boolean owned = data.unlocked.contains(t.id);
            ItemStack stack = iconItem(Items.NAME_TAG, t.name,
                    owned ? "§a已拥有" : "§7价格: §6" + t.price + " QP",
                    owned ? "§7你已经拥有此称号" : "§a点击购买");
            if (owned) {
                stack = withGlow(stack);
            }
            inv.setStack(slot++, stack);
        }

        inv.setStack(BACK_SLOT, iconItem(Items.ARROW, "§7返回主菜单", ""));
    }

    private static void fillMyTitles(SimpleInventory inv, TitleManager.PlayerTitleData data) {
        inv.clear();
        for (int i = 0; i < inv.size(); i++) inv.setStack(i, placeholder());

        int slot = 0;
        for (String id : data.unlocked) {
            if (slot >= inv.size() - 9) break;
            TitleManager.TitleDef t = TitleManager.getTitle(id);
            String name = t != null ? t.name : id;
            boolean active = id.equals(data.active);
            ItemStack stack = iconItem(Items.NAME_TAG, name,
                    active ? "§a当前已佩戴" : "§7点击佩戴",
                    active ? "§7再次点击可保持佩戴" : "§e点击装备该称号");
            if (active) {
                stack = withGlow(stack);
            }
            inv.setStack(slot++, stack);
        }
        for (String name : data.customTitles) {
            if (slot >= inv.size() - 9) break;
            boolean active = name.equals(data.active);
            ItemStack stack = iconItem(Items.PAPER, name,
                    active ? "§a当前已佩戴" : "§7点击佩戴",
                    active ? "§7再次点击可保持佩戴" : "§e点击装备该称号");
            if (active) {
                stack = withGlow(stack);
            }
            inv.setStack(slot++, stack);
        }

        inv.setStack(BACK_SLOT, iconItem(Items.ARROW, "§7返回主菜单", ""));
        inv.setStack(CLEAR_SLOT, iconItem(Items.BARRIER, "§c取消佩戴", ""));
    }

    public static void onClick(ServerPlayerEntity player, QuestUiScreenHandler.UiType type, int slot, ItemStack stack) {
        String name = stack.getName().getString();

        switch (type) {
            case MAIN_MENU -> {
                switch (slot) {
                    case 11 -> openQuestList(player);
                    case 13 -> openTitleShop(player);
                    case 15 -> openMyTitles(player);
                    case 21 -> player.sendMessage(Text.literal("§7在聊天框输入: /qlquest redeem <CDK>"), false);
                    case 23 -> player.sendMessage(Text.literal("§6你的 QP: " + com.xz.nquestql.NQuestQinglanEdition.getInstance().getQPStorage().getQP(player.getUuid())), false);
                    case 26 -> player.closeHandledScreen();
                }
            }
            case QUEST_LIST -> {
                if (slot == BACK_SLOT) {
                    openMainMenu(player);
                    return;
                }
                QuestData q = findQuestByName(name);
                if (q != null && q.points != null && !q.points.isEmpty()) {
                    QuestManager.start(player, q);
                }
            }
            case TITLE_SHOP -> {
                if (slot == BACK_SLOT) {
                    openMainMenu(player);
                    return;
                }
                TitleManager.TitleDef t = findTitleByName(name);
                if (t != null) {
                    boolean success = TitleManager.buyTitle(player, t.id);
                    if (success) {
                        refreshTitleShop(player);
                    }
                }
            }
            case MY_TITLES -> {
                if (slot == BACK_SLOT) {
                    openMainMenu(player);
                    return;
                }
                if (slot == CLEAR_SLOT) {
                    TitleManager.setActive(player, "");
                    refreshMyTitles(player);
                    return;
                }
                TitleManager.PlayerTitleData data = TitleManager.getOrCreate(player.getUuid());
                // 先按模板称号查找
                TitleManager.TitleDef t = findTitleByName(name);
                if (t != null && data.unlocked.contains(t.id)) {
                    TitleManager.setActive(player, t.id);
                    refreshMyTitles(player);
                    return;
                }
                // 再按自定义称号显示名匹配
                if (data.customTitles.contains(name)) {
                    TitleManager.setActive(player, name);
                    refreshMyTitles(player);
                    return;
                }
                player.sendMessage(Text.literal("§c你还未解锁此称号"), false);
            }
        }
    }

    private static void refreshMyTitles(ServerPlayerEntity player) {
        if (player.currentScreenHandler instanceof QuestUiScreenHandler handler && handler.getUiType() == QuestUiScreenHandler.UiType.MY_TITLES) {
            Inventory inv = handler.getInventory();
            if (inv instanceof SimpleInventory simpleInv) {
                fillMyTitles(simpleInv, TitleManager.getOrCreate(player.getUuid()));
                handler.sendContentUpdates();
                return;
            }
        }
        openMyTitles(player);
    }

    private static void refreshTitleShop(ServerPlayerEntity player) {
        if (player.currentScreenHandler instanceof QuestUiScreenHandler handler && handler.getUiType() == QuestUiScreenHandler.UiType.TITLE_SHOP) {
            Inventory inv = handler.getInventory();
            if (inv instanceof SimpleInventory simpleInv) {
                fillTitleShop(simpleInv, TitleManager.getTitles(),
                        com.xz.nquestql.NQuestQinglanEdition.getInstance().getQPStorage().getQP(player.getUuid()), player);
                handler.sendContentUpdates();
                return;
            }
        }
        openTitleShop(player);
    }

    /** 去除 § 颜色代码，用于名称比对/排序 */
    private static String plainName(String s) {
        return s == null ? "" : s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    private static QuestData findQuestByName(String name) {
        String plain = plainName(name);
        for (QuestData q : QuestManager.getAll()) {
            if (plainName(q.questName).equals(plain)) {
                return q;
            }
        }
        return null;
    }

    private static TitleManager.TitleDef findTitleByName(String name) {
        for (TitleManager.TitleDef t : TitleManager.getTitles()) {
            if (plainName(t.name).equals(plainName(name))) {
                return t;
            }
        }
        return null;
    }

    private static ItemStack placeholder() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.setCustomName(Text.literal(" "));
        return stack;
    }

    private static ItemStack iconItem(net.minecraft.item.Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtList loreList = new NbtList();
        for (String line : lore) {
            if (line == null || line.isEmpty()) continue;
            loreList.add(NbtString.of(net.minecraft.text.Text.Serializer.toJson(Text.literal(line))));
        }
        NbtCompound display = nbt.getCompound("display");
        if (display == null || display.isEmpty()) display = new NbtCompound();
        display.put("Lore", loreList);
        nbt.put("display", display);
        stack.setNbt(nbt);
        return stack;
    }

    private static ItemStack withGlow(ItemStack stack) {
        stack.addEnchantment(Enchantments.UNBREAKING, 1);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("HideFlags", 1);
        stack.setNbt(nbt);
        return stack;
    }
}
