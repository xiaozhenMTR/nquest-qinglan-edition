package com.xz.nquestql.manager;

import com.xz.nquestql.NQuestQinglanEdition;
import com.xz.nquestql.data.QuestData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

public class QuestManager {

    private static final Map<UUID, QuestState> active = new HashMap<>();
    private static final Map<UUID, Set<String>> onceDone = new HashMap<>();
    private static final Map<UUID, Set<String>> dailyDone = new HashMap<>();
    private static final Map<UUID, Long> lastBar = new HashMap<>();

    public static class QuestState {
        public String questId;
        public int step;
        public String name;
        public QuestState(String q, int s, String n) { questId = q; step = s; name = n; }
    }

    /** tick：每 3 秒刷 ActionBar */
    public static void tick(Iterable<ServerPlayerEntity> players) {
        long now = System.currentTimeMillis();
        for (ServerPlayerEntity p : players) {
            UUID id = p.getUuid();

            // ── ActionBar 刷新 ──
            QuestState st = active.get(id);
            if (st == null) continue;
            Long last = lastBar.get(id);
            if (last != null && now - last < 3000) continue;

            QuestData q = getQuest(st.questId);
            if (q == null || q.points == null || st.step >= q.points.size()) continue;
            p.sendMessage(Text.literal("§f" + q.points.get(st.step).bossbarText), true);
            lastBar.put(id, now);
        }
    }

    /** 显式开始任务（箱子 UI 点击 / /qlquest start）。trigger 不再自动开始任务。 */
    public static boolean start(ServerPlayerEntity p, QuestData q) {
        UUID id = p.getUuid();

        if (q.points == null || q.points.isEmpty()) {
            p.sendMessage(Text.literal("§c该任务没有步骤"), false);
            return false;
        }

        if (active.containsKey(id)) {
            QuestState cur = active.get(id);
            if (cur != null && cur.questId.equals(q.questId)) {
                p.sendMessage(Text.literal("§e该任务已在进行中"), false);
            } else {
                p.sendMessage(Text.literal("§c已有进行中的任务，用 /qlquest abort 取消"), false);
            }
            return false;
        }

        if ("one_time".equals(q.type) && onceDone.getOrDefault(id, Set.of()).contains(q.questId)) {
            p.sendMessage(Text.literal("§c已完成过此任务"), false);
            return false;
        }
        if ("daily".equals(q.type)) {
            String t = java.time.LocalDate.now().toString();
            if (dailyDone.getOrDefault(id, Set.of()).contains(q.questId + "_" + t)) {
                p.sendMessage(Text.literal("§c今天已完成此每日任务"), false);
                return false;
            }
        }

        QuestState st = new QuestState(q.questId, 0, p.getName().getString());
        active.put(id, st);
        p.sendMessage(Text.literal("§a任务开始: " + q.questName), false);

        String txt = q.points.get(0).bossbarText;
        p.sendMessage(Text.literal("§f" + txt), true);
        lastBar.put(id, System.currentTimeMillis());
        return true;
    }

    public static boolean isOnceDone(UUID id, String questId) {
        return onceDone.getOrDefault(id, Set.of()).contains(questId);
    }

    public static boolean isDailyDoneToday(UUID id, String questId) {
        return dailyDone.getOrDefault(id, Set.of()).contains(questId + "_" + java.time.LocalDate.now());
    }

    /**
     * trigger：仅推进已开始的任务，绝不自动开始
     */
    public static boolean trigger(ServerPlayerEntity p, QuestData q, int code) {
        UUID id = p.getUuid();
        QuestState st = active.get(id);

        // ── 任务未开始 → 拒绝（不自动启动） ──
        if (st == null) {
            p.sendMessage(Text.literal("§c任务未开始，请先接取任务（/qlquest start " + q.questId + "）"), false);
            return false;
        }
        if (!st.questId.equals(q.questId)) {
            p.sendMessage(Text.literal("§c当前进行中的是其他任务，用 /qlquest abort 取消"), false);
            return false;
        }

        // ── 进行中 → 校验 code ──
        if (q.points == null || st.step >= q.points.size()) {
            p.sendMessage(Text.literal("§c该任务已完成"), false);
            return false;
        }

        int expectedCode = q.points.get(st.step).code;
        if (code != expectedCode) {
            p.sendMessage(Text.literal("§ccode 不匹配！当前步骤 code=" + expectedCode + "，你传入的是 " + code), false);
            return false;
        }

        // code 匹配 → 推进
        st.step++;
        int step = st.step;

        if (step >= q.points.size()) {
            return complete(p, q);
        }

        String txt = q.points.get(step).bossbarText;
        p.sendMessage(Text.literal("§a步骤 " + (step + 1) + "/" + q.points.size()), false);
        p.sendMessage(Text.literal("§f" + txt), true);
        lastBar.put(id, System.currentTimeMillis());
        return true;
    }

    private static boolean complete(ServerPlayerEntity p, QuestData q) {
        UUID id = p.getUuid();
        active.remove(id);
        lastBar.remove(id);

        int reward = q.qpReward;
        NQuestQinglanEdition.getInstance().getQPStorage().addQP(id, p.getName().getString(), reward);

        p.sendMessage(Text.literal("§a§l✔ 任务完成: " + q.questName + " §7(+" + reward + " QP)"), false);

        if ("one_time".equals(q.type))
            onceDone.computeIfAbsent(id, k -> new HashSet<>()).add(q.questId);
        else if ("daily".equals(q.type)) {
            String t = java.time.LocalDate.now().toString();
            dailyDone.computeIfAbsent(id, k -> new HashSet<>()).add(q.questId + "_" + t);
        }
        return true;
    }

    public static boolean abort(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        if (!active.containsKey(id)) {
            p.sendMessage(Text.literal("§c无进行中任务"), false);
            return false;
        }
        active.remove(id);
        lastBar.remove(id);
        p.sendMessage(Text.literal("§e任务已取消"), false);
        return true;
    }

    public static QuestData getQuest(String id) {
        return NQuestQinglanEdition.getInstance().getQuestCache().get(id);
    }

    public static Collection<QuestData> getAll() {
        Map<String, QuestData> c = NQuestQinglanEdition.getInstance().getQuestCache();
        return c != null ? c.values() : Collections.emptyList();
    }

    public static QuestState getActive(UUID id) {
        return active.get(id);
    }
}