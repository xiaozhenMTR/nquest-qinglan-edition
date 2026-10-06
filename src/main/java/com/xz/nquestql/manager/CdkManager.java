package com.xz.nquestql.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class CdkManager {

    private static final String API_URL = "https://liyuzhen.cn/qinglan/games/qlquest/cdk-api.php";

    public static class ClaimResult {
        public boolean success;
        public String titleName;
        public int price;
        public String error;
    }

    /**
     * 兑换 CDK（调用远程 API，标记为已使用）
     * @param cdkCode 兑换码
     * @param playerUuid 玩家 UUID
     * @return 兑换结果
     */
    public static ClaimResult claim(String cdkCode, UUID playerUuid) {
        ClaimResult result = new ClaimResult();
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            JsonObject body = new JsonObject();
            body.addProperty("action", "claim");
            body.addProperty("cdk", cdkCode);
            body.addProperty("uuid", playerUuid.toString());

            try (OutputStreamWriter ow = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                ow.write(body.toString());
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                result.success = false;
                result.error = "API 返回状态码: " + code;
                conn.disconnect();
                return result;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            conn.disconnect();

            JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
            result.success = json.get("success").getAsBoolean();

            if (result.success) {
                result.titleName = json.get("titleName").getAsString();
                result.price = json.get("price").getAsInt();
            } else {
                result.error = json.has("error") ? json.get("error").getAsString() : "未知错误";
            }
        } catch (Exception e) {
            result.success = false;
            result.error = "网络错误: " + e.getMessage();
        }
        return result;
    }
}
