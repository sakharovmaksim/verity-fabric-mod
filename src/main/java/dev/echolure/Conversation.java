package dev.echolure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

final class Conversation {
    private static final int MAX_EXCHANGES = 24;
    private static final int HISTORY_BYTES = 4600;
    private static final int CHAT_FORMAT_BYTES = 32;

    // Model replies can arrive after answers to newer questions handled by the mod.
    private final TreeMap<Long, Exchange> exchanges = new TreeMap<>();
    private long turn;
    private LocationRequests.Result lastLocation;
    private String lastCoordinates;

    private record Exchange(String question, String answer, int bytes) {}

    long nextTurn() {
        return ++turn;
    }

    void remember(long turn, String question, String answer) {
        int bytes = question.getBytes(StandardCharsets.UTF_8).length + answer.getBytes(StandardCharsets.UTF_8).length
            + CHAT_FORMAT_BYTES;
        exchanges.put(turn, new Exchange(question, answer, bytes));
        if (exchanges.size() > MAX_EXCHANGES) exchanges.pollFirstEntry();
    }

    List<OllamaChat.Message> history() {
        var recent = new ArrayList<Exchange>();
        int remaining = HISTORY_BYTES;
        for (Exchange exchange : exchanges.descendingMap().values()) {
            if (exchange.bytes() > remaining) break;
            recent.add(exchange);
            remaining -= exchange.bytes();
        }
        var messages = new ArrayList<OllamaChat.Message>(recent.size() * 2);
        for (Exchange exchange : recent.reversed()) {
            messages.add(new OllamaChat.Message("user", exchange.question()));
            messages.add(new OllamaChat.Message("assistant", exchange.answer()));
        }
        return messages;
    }

    void rememberLocation(LocationRequests.Result result) {
        lastLocation = result;
        if (result.position() != null) lastCoordinates = result.message();
    }

    LocationRequests.Result lastLocation() {
        return lastLocation;
    }

    String repeatLocation() {
        if (lastCoordinates == null) return "Я ещё не находил координаты в этой сессии. Скажи, что искать.";
        return "Вот мой прошлый ответ о координатах. " + lastCoordinates;
    }
}
