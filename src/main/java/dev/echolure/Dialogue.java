package dev.echolure;

import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class Dialogue {
    private static final int ORE_RADIUS = 16;
    private static final Pattern CHAT_PREFIX = Pattern.compile("(?iu)^\\s*(?:отблеск|echo|verity)\\s*[:,]\\s*(.*)$");
    private static final Pattern MATH = Pattern.compile("(-?\\d{1,7})\\s*([+*/×÷-])\\s*(-?\\d{1,7})[?=]?");

    private Dialogue() {}

    static String addressedQuestion(String message) {
        var match = CHAT_PREFIX.matcher(message);
        return match.matches() ? match.group(1) : null;
    }

    static String answer(ServerPlayer player, String question, Progress.Phase phase) {
        if (phase == Progress.Phase.ANGER) return "Отойди. Сейчас.";
        if (phase == Progress.Phase.WATCH) return "Не оборачивайся. Так мне проще.";
        if (phase == Progress.Phase.HUNT) return "Я уже слышу твои шаги.";

        String query = question.strip().toLowerCase(Locale.ROOT).replace('ё', 'е');
        String reply = help(player, query);
        return phase == Progress.Phase.STATIC ? "…ш-ш… " + reply : reply;
    }

    private static String help(ServerPlayer player, String query) {
        var calculation = MATH.matcher(query);
        if (calculation.find()) {
            long left = Long.parseLong(calculation.group(1));
            long right = Long.parseLong(calculation.group(3));
            return switch (calculation.group(2)) {
                case "+" -> "Получается " + (left + right) + ".";
                case "-" -> "Получается " + (left - right) + ".";
                case "*", "×" -> "Получается " + (left * right) + ".";
                default -> right == 0 ? "На ноль делить нельзя." : "Получается " + ((double) left / right) + ".";
            };
        }
        if (query.contains("алмаз") || query.contains("diamond")) {
            return oreHint(player, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                "Алмазы чаще встречаются глубоко. Попробуй около Y = −59.");
        }
        if (query.contains("желез") || query.contains("iron")) {
            return oreHint(player, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                "Железо можно искать около Y = 16 и в высоких горах.");
        }
        BlockPos here = player.blockPosition();
        if (query.contains("координат") || query.contains("где я") || query.contains("coords")) {
            return "Ты на %d, %d, %d.".formatted(here.getX(), here.getY(), here.getZ());
        }
        if (query.contains("ночь") || query.contains("время") || query.contains("time")) {
            return player.level().isDarkOutside() ? "Снаружи темно." : "Сейчас ещё светло.";
        }
        return "Спроси про алмазы, железо, координаты или время. Могу посчитать, например, 17 * 6.";
    }

    private static String oreHint(ServerPlayer player, Block ore, Block deepOre, String fallback) {
        var level = player.level();
        BlockPos start = player.blockPosition();
        int radius = ORE_RADIUS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int nearest = radius * radius + 1;
        BlockPos found = null;
        for (int x = start.getX() - radius; x <= start.getX() + radius; x++) {
            for (int z = start.getZ() - radius; z <= start.getZ() + radius; z++) {
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) continue;
                int dx = x - start.getX();
                int dz = z - start.getZ();
                int horizontal = dx * dx + dz * dz;
                if (horizontal >= nearest) continue;
                for (int y = Math.max(level.getMinY(), start.getY() - radius);
                     y <= Math.min(level.getMaxY(), start.getY() + radius); y++) {
                    int dy = y - start.getY();
                    int distance = horizontal + dy * dy;
                    if (distance >= nearest) continue;
                    var state = chunk.getBlockState(pos.set(x, y, z));
                    if (state.is(ore) || state.is(deepOre)) {
                        nearest = distance;
                        found = pos.immutable();
                    }
                }
            }
        }
        if (found == null) return "Поблизости жилы не слышно. " + fallback;
        return "Ближайший сигнал примерно на %d, %d, %d.".formatted(found.getX(), found.getY(), found.getZ());
    }
}
