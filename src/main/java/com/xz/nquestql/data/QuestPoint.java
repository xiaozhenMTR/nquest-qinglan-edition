package com.xz.nquestql.data;

import com.google.gson.JsonObject;

public class QuestPoint {
    public int code;
    public String bossbarText;
    public boolean teleport;
    /** MTR 站名：非空时表示“乘坐 MTR 列车到达该站”自动推进（需服务器安装 MTR 4.x） */
    public String station;
    /** false 时步行进入车站区域也算到达 */
    public boolean ride = true;

    public QuestPoint() {
        this.code = 0;
        this.bossbarText = "";
        this.teleport = false;
    }

    public QuestPoint(int code, String bossbarText, boolean teleport) {
        this.code = code;
        this.bossbarText = bossbarText;
        this.teleport = teleport;
    }

    public static QuestPoint fromJson(JsonObject obj) {
        if (obj == null) return null;
        QuestPoint p = new QuestPoint();
        p.code = obj.has("code") ? obj.get("code").getAsInt() : 0;
        p.bossbarText = obj.has("bossbarText") ? obj.get("bossbarText").getAsString() : "";
        p.teleport = obj.has("teleport") ? obj.get("teleport").getAsBoolean() : false;
        p.station = obj.has("station") && obj.get("station").isJsonNull() ? null
                : obj.has("station") ? obj.get("station").getAsString() : null;
        p.ride = obj.has("ride") ? obj.get("ride").getAsBoolean() : true;
        return p;
    }
}
