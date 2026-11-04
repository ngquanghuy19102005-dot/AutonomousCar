package com.smartiot.autonomouscar;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class AiCarCopilotHelper {

    private static final String TAG = "AiCarCopilot";
    private static final String GEMINI_API_KEY = "AIzaSyCheOba3OFaVtj1xLaKE--l3ts1HVh7-og";
    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface CopilotCallback {
        void onResponse(String advice);
        void onError(String err);
    }

    private CopilotCallback callback;

    public void setCallback(CopilotCallback callback) {
        this.callback = callback;
    }

    public void askCopilot(int front, int left, int right, int speed, String mode, String question) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String systemPrompt = String.format(
                        "Bạn là Trợ lý AI Copilot của Xe Robot Tự Hành Tránh Vật Cản. " +
                        "Dữ liệu cảm biến thời gian thực: Phía trước: %d cm, Bên trái: %d cm, Bên phải: %d cm, Tốc độ PWM: %d/255, Chế độ: %s. " +
                        "Hãy trả lời ngắn gọn (dưới 3 câu), đưa ra lời khuyên điều hướng an toàn và phân tích tình huống đường đi.",
                        front, left, right, speed, mode
                );

                JSONObject contentObj = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("text", systemPrompt + "\nCâu hỏi: " + question);
                parts.put(textPart);
                contentObj.put("parts", parts);

                JSONArray contents = new JSONArray();
                contents.put(contentObj);

                JSONObject body = new JSONObject();
                body.put("contents", contents);

                URL url = new URL(GEMINI_ENDPOINT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);

                    JSONObject resp = new JSONObject(sb.toString());
                    JSONArray candidates = resp.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        String ans = candidates.getJSONObject(0).optJSONObject("content").optJSONArray("parts").getJSONObject(0).optString("text", "");
                        notifyResponse(ans);
                        return;
                    }
                }
                notifyResponse("Tuyến đường phía trước " + (front > 40 ? "thông thoáng, có thể tăng tốc." : "có vật cản gần, nên giảm tốc độ hoặc rẽ hướng khác."));

            } catch (Exception e) {
                notifyResponse("Tuyến đường phía trước " + (front > 40 ? "thông thoáng, có thể tăng tốc." : "có vật cản gần, nên giảm tốc độ hoặc rẽ hướng khác."));
            }
        });
    }

    private void notifyResponse(String advice) {
        mainHandler.post(() -> {
            if (callback != null) callback.onResponse(advice);
        });
    }
}
