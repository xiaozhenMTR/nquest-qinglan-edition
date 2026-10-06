package com.xz.nquestql.manager;

import com.xz.nquestql.data.QuestData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.UUID;

/**
 * 主线程侧：请求 MTR 状态更新 + 消费到站事件推进任务。
 * 本类不 import 任何 org.mtr.* 类，MTR 未安装时安全。
 */
public class MtrStationWatcher {

    public static boolean enabled = false;

    public static void tick(MinecraftServer server, List<ServerPlayerEntity> players) {
        if (!enabled) return;
        if (server.getTicks() % 20 == 5) {
            MtrTracker.requestUpdate(players);
        }
        if (server.getTicks() % 20 != 15) return;
        for (ServerPlayerEntity p : players) {
            QuestManager.QuestState st = QuestManager.getActive(p.getUuid());
            if (st == null) continue;
            QuestData q = QuestManager.getQuest(st.questId);
            if (q == null || q.points == null || st.step >= q.points.size()) continue;
            var point = q.points.get(st.step);
            if (point.station == null || point.station.isEmpty()) continue;
            if (MtrTracker.isArrived(p.getUuid(), point.station, point.ride)) {
                QuestManager.trigger(p, q, point.code);
            }
        }
    }

    public static void remove(UUID uuid) {
        if (enabled) MtrTracker.remove(uuid);
    }
}
