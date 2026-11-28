package dev.echolure;

import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.server.level.ServerPlayer;

final class Dialogue {
    private static final Pattern CHAT_PREFIX = Pattern.compile("(?iu)^\\s*(?:ж[её]лтый\\s+шарик|yellow|verity)\\s*[:,]\\s*(.*)$");
    private static final Pattern MATH = Pattern.compile(
        "(?:(?:сколько будет|посчитай|вычисли|what is|calculate)[:\\s]+)?"
        + "(-?\\d{1,7})\\s*([+*/×÷-])\\s*(-?\\d{1,7})\\s*[?=!.]*");
    private static final Pattern DAYLIGHT = Pattern.compile(
        "(?:время|(?:какое|которое|скажи(?: мне)?) время(?: суток| сейчас)?|сколько (?:сейчас )?времени"
        + "|(?:сейчас |уже )?(?:ночь|день)(?: сейчас)?|time|what time is it|what(?:'s| is) the time"
        + "|is it (?:night|day|daytime|nighttime|dark|light)(?: outside)?)[.!?]*");

    private Dialogue() {}

    static String addressedQuestion(String message) {
        var match = CHAT_PREFIX.matcher(message);
        return match.matches() ? match.group(1) : null;
    }

    static String answer(ServerPlayer player, String question, Progress.Phase phase) {
        if (phase == Progress.Phase.ANGER) return "Отойди. Сейчас.";
        if (phase == Progress.Phase.WATCH) return "Не оборачивайся. Так мне проще.";
        if (phase == Progress.Phase.HUNT) return "Я уже слышу твои шаги.";

        return withPhase(help(player, question.strip().toLowerCase(Locale.ROOT).replace('ё', 'е')), phase);
    }

    static String withPhase(String reply, Progress.Phase phase) {
        return reply != null && phase == Progress.Phase.STATIC ? "…ш-ш… " + reply : reply;
    }

    private static String help(ServerPlayer player, String query) {
        var calculation = MATH.matcher(query);
        if (calculation.matches()) {
            long left = Long.parseLong(calculation.group(1));
            long right = Long.parseLong(calculation.group(3));
            return switch (calculation.group(2)) {
                case "+" -> "Получается " + (left + right) + ".";
                case "-" -> "Получается " + (left - right) + ".";
                case "*", "×" -> "Получается " + (left * right) + ".";
                default -> right == 0 ? "На ноль делить нельзя." : "Получается " + ((double) left / right) + ".";
            };
        }
        if (DAYLIGHT.matcher(query).matches()) {
            return player.level().isDarkOutside() ? "Снаружи темно." : "Сейчас ещё светло.";
        }
        if (query.isEmpty()) return "Напиши «verity: привет!» — поговорим. Также понимаю «Желтый шарик:» и «yellow:».";
        return null;
    }
}
