package com.xz.nquestql.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xz.nquestql.NQuestQinglanEdition;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TitleManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class TitleDef {
        public String id;
        public String name;
        public int price;
    }

    public static class PlayerTitleData {
        public List<String> unlocked = new ArrayList<>();       // 模板称号 ID 列表
        public List<String> customTitles = new ArrayList<>();   // 自定义称号显示名列表
        public String active = "";                               // 当前佩戴（模板ID 或 自定义称号显示名）
    }

    private static List<TitleDef> titles = new ArrayList<>();
    private static Map<String, PlayerTitleData> playerData = new HashMap<>();
    private static File titlesFile;
    private static File playerDataFile;

    public static void init(File configDir) {
        titlesFile = new File(configDir, "titles.json");
        playerDataFile = new File(configDir, "player_titles.json");
        load();
        if (titles.isEmpty()) {
            titles = Arrays.asList(
                    createTitle("architect", "§e建筑师", 2000),
                    createTitle("dreamer", "§d筑梦师", 5000),
                    createTitle("master", "§b建筑大师", 10000),
                    createTitle("collector", "§5收藏家", 3000),
                    createTitle("pioneer", "§a开拓者", 1500),
                    createTitle("legend", "§6§l传奇", 20000)
            );
            saveTitles();
        }
    }

    private static TitleDef createTitle(String id, String name, int price) {
        TitleDef t = new TitleDef();
        t.id = id; t.name = name; t.price = price;
        return t;
    }

    // ─── 文件读写 ───

    public static void load() {
        if (titlesFile != null && titlesFile.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(titlesFile), StandardCharsets.UTF_8)) {
                titles = GSON.fromJson(r, new TypeToken<List<TitleDef>>(){}.getType());
            } catch (Exception e) { e.printStackTrace(); }
        }
        if (titles == null) titles = new ArrayList<>();

        if (playerDataFile != null && playerDataFile.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(playerDataFile), StandardCharsets.UTF_8)) {
                playerData = GSON.fromJson(r, new TypeToken<Map<String, PlayerTitleData>>(){}.getType());
            } catch (Exception e) { e.printStackTrace(); }
        }
        if (playerData == null) playerData = new HashMap<>();
    }

    public static void saveTitles() {
        if (titlesFile == null) return;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(titlesFile), StandardCharsets.UTF_8)) {
            GSON.toJson(titles, w);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void savePlayerData() {
        if (playerDataFile == null) return;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(playerDataFile), StandardCharsets.UTF_8)) {
            GSON.toJson(playerData, w);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─── 查询 ───

    public static List<TitleDef> getTitles() { return titles; }

    public static TitleDef getTitle(String id) {
        for (TitleDef t : titles) if (t.id.equals(id)) return t;
        return null;
    }

    public static String uuidKey(UUID uuid) {
        return uuid.toString().replace("-", "").toLowerCase();
    }

    /**
     * 把玩家输入的 & 颜色代码替换成 MC 内部使用的 §，避免客户端拒绝 § 字符。
     * 例如 "&e最强建筑工" -> "§e最强建筑工"
     */
    public static String replaceAmpersand(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(next) >= 0) {
                    sb.append('\u00A7');
                    sb.append(next);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static PlayerTitleData getOrCreate(UUID uuid) {
        return playerData.computeIfAbsent(uuidKey(uuid), k -> new PlayerTitleData());
    }

    public static boolean hasTitle(UUID uuid, String titleId) {
        return getOrCreate(uuid).unlocked.contains(titleId);
    }

    public static String getActiveTitleId(UUID uuid) {
        return getOrCreate(uuid).active;
    }

    public static String getActiveTitleName(UUID uuid) {
        PlayerTitleData data = getOrCreate(uuid);
        if (data.active == null || data.active.isEmpty()) return null;
        // 先检查是否是自定义称号
        if (data.customTitles.contains(data.active)) return data.active;
        // 再检查是否是模板称号
        TitleDef def = getTitle(data.active);
        return def != null ? def.name : null;
    }

    // ─── 颜色解析（把 § 代码解析成真正的 Style，避免 § 作为原始字符进入聊天导致非法字符） ───
    public static MutableText colorize(String input) {
        MutableText root = Text.literal("");
        if (input == null || input.isEmpty()) return root;
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u00A7' && i + 1 < input.length()) {
                if (buf.length() > 0) { root.append(Text.literal(buf.toString()).setStyle(style)); buf.setLength(0); }
                char code = input.charAt(i + 1);
                if (code == 'r' || code == 'R') {
                    style = Style.EMPTY;
                } else {
                    Formatting f = Formatting.byCode(Character.toLowerCase(code));
                    if (f != null) style = style.withFormatting(f);
                }
                i++;
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) root.append(Text.literal(buf.toString()).setStyle(style));
        return root;
    }

    // ─── 操作 ───

    public static boolean buyTitle(ServerPlayerEntity player, String titleId) {
        TitleDef title = getTitle(titleId);
        if (title == null) {
            player.sendMessage(Text.literal("§c称号不存在: " + titleId), false);
            return false;
        }
        PlayerTitleData data = getOrCreate(player.getUuid());
        if (data.unlocked.contains(titleId)) {
            player.sendMessage(Text.literal("§c你已经拥有此称号了"), false);
            return false;
        }
        boolean success = NQuestQinglanEdition.getInstance().getQPStorage().deductQP(player.getUuid(), title.price);
        if (!success) {
            player.sendMessage(Text.literal("§cQP 不足，需要 " + title.price + " QP"), false);
            return false;
        }
        data.unlocked.add(titleId);
        savePlayerData();
        player.sendMessage(Text.literal("§a✔ 成功购买称号: " + title.name + " §7(花费 " + title.price + " QP)"), false);
        return true;
    }

    /**
     * 添加自定义称号（通过 CDK 兑换获得）
     */
    public static void addCustomTitle(ServerPlayerEntity player, String displayName) {
        String name = replaceAmpersand(displayName);
        PlayerTitleData data = getOrCreate(player.getUuid());
        if (data.customTitles.contains(name)) {
            // 已经有了，直接佩戴
            data.active = name;
        } else {
            data.customTitles.add(name);
            data.active = name;
        }
        savePlayerData();
        updateDisplay(player);
        player.sendMessage(Text.literal("§a✔ 获得自定义称号: ").append(colorize(name)), false);
    }

    public static void setActive(ServerPlayerEntity player, String titleId) {
        PlayerTitleData data = getOrCreate(player.getUuid());

        if (titleId == null || titleId.isEmpty()) {
            data.active = "";
            savePlayerData();
            updateDisplay(player);
            player.sendMessage(Text.literal("§e已取消称号佩戴"), false);
            return;
        }

        String input = replaceAmpersand(titleId);

        // 检查是否是自定义称号
        if (data.customTitles.contains(input)) {
            data.active = input;
            savePlayerData();
            updateDisplay(player);
            player.sendMessage(Text.literal("§a已佩戴称号: ").append(colorize(input)), false);
            return;
        }

        // 检查是否是模板称号
        if (data.unlocked.contains(input)) {
            data.active = input;
            savePlayerData();
            updateDisplay(player);
            TitleDef t = getTitle(input);
            player.sendMessage(Text.literal("§a已佩戴称号: ").append(t != null ? colorize(t.name) : Text.literal(input)), false);
            return;
        }

        player.sendMessage(Text.literal("§c你还未解锁此称号"), false);
    }

    public static void updateDisplay(ServerPlayerEntity player) {
        // 头顶/聊天/Tab 的显示名都由 ServerPlayerEntityMixin 里的 getDisplayName() 统一处理
        // 这里只需要刷新 Tab 列表包，让其他玩家/client 端看到最新称号
        if (player.getServer() != null) {
            player.getServer().getPlayerManager().sendToAll(
                    new net.minecraft.network.packet.s2c.play.PlayerListS2CPacket(
                            net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME,
                            player
                    )
            );
        }
    }


}
