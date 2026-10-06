package com.xz.nquestql.data;

import com.google.gson.JsonObject;
import java.util.*;

public class QuestData {
    public String questId;
    public String questName;
    public String type = "one_time";
    public int qpReward;
    public List<QuestPoint> points = new ArrayList<>();

    public static QuestData fromJson(JsonObject obj) {
        QuestData q = new QuestData();
        q.questId   = optStr(obj, "questId", optStr(obj, "id", ""));
        q.questName = optStr(obj, "questName", optStr(obj, "name", ""));
        q.type      = optStr(obj, "type", "one_time");
        q.qpReward  = obj.has("qpReward") ? obj.get("qpReward").getAsInt()
                : obj.has("qp") ? obj.get("qp").getAsInt() : 0;
        if (obj.has("points") && obj.get("points").isJsonArray()) {
            for (var e : obj.getAsJsonArray("points"))
                q.points.add(QuestPoint.fromJson(e.getAsJsonObject()));
        }
        return q;
    }

    private static String optStr(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }
}
