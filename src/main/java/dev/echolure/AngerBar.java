package dev.echolure;

import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

final class AngerBar extends ServerBossEvent {
    AngerBar(ServerPlayer player) {
        super(UUID.randomUUID(), Component.literal("Желтый шарик"), BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.NOTCHED_6);
        addPlayer(player);
    }

    void update(Progress progress) {
        setName(Component.literal(label(progress)));
        switch (progress.phase()) {
            case WAITING -> {
                setColor(BossBarColor.BLUE);
                setProgress(progress.seconds() / (float) Progress.ARRIVAL);
            }
            case HUNT -> {
                setColor(BossBarColor.RED);
                setProgress(progress.secondsUntilNormal() / (float) Progress.MONSTER_DURATION);
            }
            default -> {
                setColor(progress.angerPercent() == 100 ? BossBarColor.RED :
                    progress.angerPercent() > 0 ? BossBarColor.YELLOW : BossBarColor.GREEN);
                setProgress(progress.angerPercent() / 100f);
            }
        }
    }

    static String label(Progress progress) {
        return switch (progress.phase()) {
            case WAITING -> "Желтый шарик · появление через " + time(Progress.ARRIVAL - progress.seconds());
            case ANGER -> "Желтый шарик · злость 100% · до монстра " + time(progress.secondsUntilMonster());
            case HUNT -> "Желтый шарик · монстр · до обычной формы " + time(progress.secondsUntilNormal());
            case WATCH -> "Желтый шарик · наблюдение · до монстра " + time(progress.secondsUntilMonster());
            default -> "Желтый шарик · злость %d%% · до срыва ≈%s плохого обращения".formatted(
                progress.angerPercent(), time(progress.provocationSecondsRemaining()));
        };
    }

    private static String time(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
