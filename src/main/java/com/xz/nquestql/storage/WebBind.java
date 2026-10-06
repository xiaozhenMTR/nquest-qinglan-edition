package com.xz.nquestql.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xz.nquestql.NQuestQinglanEdition;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

/**
 * /qlquest bind web 持久化：记录后台基地址（不带 https:// 前缀）。
 * 例如 liyuzhen.cn/qinglan/games/qlquest
 */
public class WebBind {
    private static final Path FILE = Path.of(NQuestQinglanEdition.CACHE_DIR, "web_bind.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String base = "";

    private WebBind() {}
    private WebBind(String b) { base = b; }

    /** 读取绑定的后台基地址，未绑定时返回空串 */
    public static String load() {
        File f = FILE.toFile();
        if (!f.exists()) return "";
        try (FileReader fr = new FileReader(f)) {
            WebBind d = GSON.fromJson(fr, WebBind.class);
            return (d != null && d.base != null) ? d.base : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void save(String base) {
        try {
            FILE.toFile().getParentFile().mkdirs();
            try (FileWriter fw = new FileWriter(FILE.toFile())) {
                GSON.toJson(new WebBind(base), fw);
            }
        } catch (Exception ignored) {}
    }
}
