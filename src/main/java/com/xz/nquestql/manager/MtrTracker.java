package com.xz.nquestql.manager;

import net.minecraft.server.network.ServerPlayerEntity;
import org.mtr.core.data.Position;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MTR 模拟线程 ↔ 主线程 的数据桥梁（对应 NQuestMod 的 TscStatus）
 */
public class MtrTracker {

    public static volatile long updateRequestNonce;
    public static final Map<UUID, Position> CLIENT_POSITIONS = new ConcurrentHashMap<>();
    public static final Map<UUID, ClientState> CLIENTS = new ConcurrentHashMap<>();

    public static void requestUpdate(Iterable<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            CLIENT_POSITIONS.put(p.getUuid(), new Position(p.getBlockX(), p.getBlockY(), p.getBlockZ()));
        }
        updateRequestNonce = System.currentTimeMillis();
    }

    public static void remove(UUID uuid) {
        CLIENT_POSITIONS.remove(uuid);
        CLIENTS.remove(uuid);
    }

    /** 乘车到达判定：在目标站区域 ∧ (乘车 ∧ 车门开)。requireRide=false 时步行进站也算 */
    public static boolean isArrived(UUID uuid, String stationName, boolean requireRide) {
        ClientState st = CLIENTS.get(uuid);
        if (st == null) return false;
        boolean inStation = false;
        for (String name : st.stations()) {
            if (name.equalsIgnoreCase(stationName)) { inStation = true; break; }
        }
        if (!inStation) return false;
        if (!requireRide) return true;
        return st.riding() && !st.doorClosed();
    }

    public record ClientState(List<String> stations, boolean riding, boolean doorClosed, double speedKmph) {}
}
