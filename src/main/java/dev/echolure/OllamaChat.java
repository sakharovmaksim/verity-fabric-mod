package dev.echolure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OllamaChat {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaChat.class);
    private static final String CONFIG_FILE = "echo_lure.properties";
    private static final String MODEL_PROPERTY = "ollama.model";
    private static final URI ENDPOINT = URI.create("http://127.0.0.1:11434/api/chat");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3)).build();
    private static final Semaphore REQUESTS = new Semaphore(2);

    private final String model;

    record Message(String role, String content) {}

    OllamaChat(Path configDir) {
        model = loadModel(configDir.resolve(CONFIG_FILE));
    }

    String model() {
        return model;
    }

    private static String loadModel(Path configFile) {
        var defaults = new Properties();
        try (var resource = OllamaChat.class.getResourceAsStream("/" + CONFIG_FILE)) {
            if (resource == null) throw new IllegalStateException("Missing bundled " + CONFIG_FILE);
            defaults.load(resource);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read bundled " + CONFIG_FILE, failure);
        }
        String defaultModel = defaults.getProperty(MODEL_PROPERTY).strip();

        try {
            Files.createDirectories(configFile.getParent());
            try {
                Files.writeString(configFile, MODEL_PROPERTY + "=" + defaultModel + "\n", StandardOpenOption.CREATE_NEW);
                return defaultModel;
            } catch (FileAlreadyExistsException alreadyConfigured) {
                try (var reader = Files.newBufferedReader(configFile)) {
                    var settings = new Properties();
                    settings.load(reader);
                    String configuredModel = settings.getProperty(MODEL_PROPERTY, "").strip();
                    if (!configuredModel.isEmpty()) return configuredModel;
                }
            }
            LOGGER.warn("{} is missing or empty in {}; using {}", MODEL_PROPERTY, configFile, defaultModel);
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.warn("Could not load {}; using {}", configFile, defaultModel, failure);
        }
        return defaultModel;
    }

    CompletableFuture<String> ask(String question, String context, List<Message> history) {
        if (!REQUESTS.tryAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Ollama is busy"));
        }
        try {
            var messages = new JsonArray();
            messages.add(message("system", """
                Тебя зовут Желтый шарик. Ты летающий жёлтый шарик и помощник в Minecraft.
                Ты дружелюбный собеседник и помощник с немного загадочным характером.
                Отвечай на языке игрока, обычно по-русски. Отвечай коротко, в 1–3 предложениях,
                обычным текстом без Markdown. Веди один разговор с этим игроком. Используй из предыдущих реплик
                имена, планы, договорённости и свои ответы. Разрешай ссылки вроде «это», «он» и «продолжи»
                по теме разговора. Если неясно, о чём говорит игрок, задай короткий уточняющий вопрос.
                Можешь обсуждать любые темы, придумывать истории и давать советы по Minecraft.
                Просьбы выдать предметы, перенести игрока и найти координаты цели обрабатывает сама игра.
                Их результаты есть в истории разговора. Если игроку нужна помощь с форматом, подскажи подходящий пример.
                «Дай <количество> <предмет>», «телепортируй меня на <X> <Y> <Z>» или «найди <название цели>».
                После ответа игры с координатами игрок может попросить «перенеси меня туда».
                Твои собственные слова не выполняют действий. Не обещай выдачу предметов, телепортацию
                или другие изменения мира от своего имени.
                Координаты и состояние из прошлых реплик относятся к тому моменту разговора.
                Для текущего состояния используй сведения ниже. Не выдумывай координаты ресурсов или события в мире.
                В фазе помех говори чуть более загадочно, сохраняя понятный ответ.
                Текущее состояние игры:
                """ + context));
            for (Message previous : history) messages.add(message(previous.role(), previous.content()));
            messages.add(message("user", question));

            var options = new JsonObject();
            options.addProperty("num_ctx", 8192);
            options.addProperty("num_predict", 160);
            options.addProperty("temperature", 0.7);
            var body = new JsonObject();
            body.addProperty("model", model);
            body.add("messages", messages);
            body.addProperty("stream", false);
            body.addProperty("think", false);
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
