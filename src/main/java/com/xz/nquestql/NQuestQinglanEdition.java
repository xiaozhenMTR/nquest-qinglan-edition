package com.xz.nquestql;

import com.google.gson.*;
import com.xz.nquestql.gui.QuestUiScreenHandler;
import com.xz.nquestql.manager.TitleManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class NQuestQinglanEdition implements ModInitializer {
    public static final String MOD_ID = "nquest-qinglan-edition";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String QUESTS_URL = "https://liyuzhen.cn/qinglan/games/qlquest/quests.json";
    public static final String CACHE_DIR = "config/" + MOD_ID;

    private static NQuestQinglanEdition instance;
    public static NQuestQinglanEdition getInstance() { return instance; }

    /** 任务数据地址（可被 /qlquest bind web 覆盖） */
    private String questsUrl = QUESTS_URL;
    public String getQuestsUrl() { return questsUrl; }

    /** 应用后台绑定：由基地址推导 quests.json 与 qp-upload.php */
    public void applyWebBind(String base) {
        if (base == null || base.isEmpty()) return;
        questsUrl = "https://" + base + "/quests.json";
        if (qpStorage != null) qpStorage.setUploadUrl("https://" + base + "/qp-upload.php");
    }

    public static ScreenHandlerType<QuestUiScreenHandler> QUEST_UI_TYPE;

    private Map<String, com.xz.nquestql.data.QuestData> questCache = new HashMap<>();
    public Map<String, com.xz.nquestql.data.QuestData> getQuestCache() { return questCache; }

    private com.xz.nquestql.storage.QPStorage qpStorage;
    public com.xz.nquestql.storage.QPStorage getQPStorage() { return qpStorage; }

    @Override
    public void onInitialize() {
        instance = this;
        // 必须依赖 MTR（fabric.mod.json 已声明 depends mtr），到站检测始终启用
        com.xz.nquestql.manager.MtrStationWatcher.enabled = true;
        qpStorage = new com.xz.nquestql.storage.QPStorage(Path.of(CACHE_DIR, "qp_data.json"));
        // 应用已保存的后台绑定（/qlquest bind web 持久化）
        String boundBase = com.xz.nquestql.storage.WebBind.load();
        if (!boundBase.isEmpty()) {
            applyWebBind(boundBase);
            LOGGER.info("Web bind applied: " + boundBase);
        }
        TitleManager.init(new File(CACHE_DIR));

        QUEST_UI_TYPE = Registry.register(Registries.SCREEN_HANDLER, new Identifier(MOD_ID, "quest_ui"),
                new ScreenHandlerType<>(QuestUiScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            com.xz.nquestql.command.QuestCommand.register(dispatcher);
        });

        // ★ ActionBar 定时刷新 + MTR 到站检测
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var players = server.getPlayerManager().getPlayerList();
            com.xz.nquestql.manager.QuestManager.tick(players);
            com.xz.nquestql.manager.MtrStationWatcher.tick(server, players);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.xz.nquestql.manager.MtrStationWatcher.remove(handler.getPlayer().getUuid()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                handler.getPlayer().sendMessage(
                        Text.literal("§7[QLQuest] §f任务数: " + questCache.size() + " | /qlquest list 查看"), false);
                // 进服时刷新称号显示（头顶/Tab/聊天显示名）
                TitleManager.updateDisplay(handler.getPlayer());
            });
        });

        loadQuests();
        LOGGER.info("NQuest Qinglan Edition loaded.");
    }

    public void loadQuests() {
        CompletableFuture.runAsync(() -> {
            new File(CACHE_DIR).mkdirs();
            try {
                String json = fetchHttp(questsUrl);
                if (json != null) {
                    questCache = parseQuests(json);
                    try (FileWriter fw = new FileWriter(Path.of(CACHE_DIR, "quests_cache.json").toFile())) {
                        fw.write(json);
                    }
                    LOGGER.info("Quests: " + questCache.size() + " (remote)");
                    return;
                }
            } catch (Exception ignored) {}
            File f = Path.of(CACHE_DIR, "quests_cache.json").toFile();
            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append("\n");
                    questCache = parseQuests(sb.toString());
                    LOGGER.info("Quests: " + questCache.size() + " (cache)");
                } catch (Exception e) {
                    LOGGER.error("Cache read failed: " + e.getMessage());
                }
            }
        });
    }

    private String fetchHttp(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (conn.getResponseCode() != 200) { conn.disconnect(); return null; }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        conn.disconnect();
        return sb.toString();
    }

    private Map<String, com.xz.nquestql.data.QuestData> parseQuests(String json) {
        Map<String, com.xz.nquestql.data.QuestData> map = new HashMap<>();
        JsonElement elem = JsonParser.parseString(json);
        if (elem.isJsonArray()) {
            for (JsonElement e : elem.getAsJsonArray()) {
                var q = com.xz.nquestql.data.QuestData.fromJson(e.getAsJsonObject());
                if (q != null && !q.questId.isEmpty()) map.put(q.questId, q);
            }
        } else if (elem.isJsonObject()) {
            for (String k : elem.getAsJsonObject().keySet()) {
                var q = com.xz.nquestql.data.QuestData.fromJson(elem.getAsJsonObject().getAsJsonObject(k));
                if (q != null) { q.questId = k; map.put(k, q); }
            }
        }
        return map;
    }
}
