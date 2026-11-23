package dev.echolure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class OllamaChat {
    static final String MODEL = "gemma3:4b";
    private static final URI ENDPOINT = URI.create("http://127.0.0.1:11434/api/chat");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)).build();
    private static final Semaphore REQUESTS = new Semaphore(2);

    record Message(String role, String content) {}

    private OllamaChat() {}

    static CompletableFuture<String> ask(String question, String context, List<Message> history) {
        if (!REQUESTS.tryAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Ollama is busy"));
        }
        try {
            var messages = new JsonArray();
            messages.add(message("system", """
                Тебя зовут Желтый шарик. Ты летающий жёлтый шарик и помощник в Minecraft.
                Ты дружелюбный собеседник и помощник с немного загадочным характером.
                Отвечай на языке игрока, обычно по-русски. Отвечай коротко, в 1–3 предложениях,
                обычным текстом без Markdown. Помни предыдущие реплики этого игрока.
                Можешь обсуждать любые темы, придумывать истории и давать советы по Minecraft.
                Твои слова не выполняют действия в игре. Ты можешь только разговаривать.
                Не выдумывай координаты ресурсов или события в мире. Используй текущее состояние ниже.
                В фазе помех говори чуть более загадочно, сохраняя понятный ответ.
                Текущее состояние игры:
                """ + context));
            for (Message previous : history) messages.add(message(previous.role(), previous.content()));
            messages.add(message("user", question));

            var options = new JsonObject();
            options.addProperty("num_ctx", 4096);
            options.addProperty("num_predict", 160);
            options.addProperty("temperature", 0.7);
            var body = new JsonObject();
            body.addProperty("model", MODEL);
            body.add("messages", messages);
            body.addProperty("stream", false);
            body.addProperty("keep_alive", "10m");
            body.add("options", options);
            var request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();

            var transport = CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            transport.whenComplete((response, failure) -> REQUESTS.release());
            var result = transport.thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Ollama HTTP " + response.statusCode());
                }
                var json = JsonParser.parseString(response.body()).getAsJsonObject();
                String answer = json.getAsJsonObject("message").get("content").getAsString()
                    .replaceAll("[\\p{Cntrl}§]", " ").replaceAll("\\s+", " ").strip();
                if (answer.isEmpty()) throw new IllegalStateException("Empty Ollama response");
                if (answer.length() > 600) {
                    int end = Character.isHighSurrogate(answer.charAt(599)) ? 599 : 600;
                    answer = answer.substring(0, end) + "…";
                }
                return answer;
            });
            result.whenComplete((answer, failure) -> {
                if (result.isCancelled()) transport.cancel(true);
            });
            return result;
        } catch (RuntimeException failure) {
            REQUESTS.release();
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static JsonObject message(String role, String content) {
        var message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }
}
