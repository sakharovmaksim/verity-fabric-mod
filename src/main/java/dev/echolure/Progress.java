package dev.echolure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

record Progress(boolean enabled, int seconds, int trust, int retaliation, int huntSeconds, int calmSeconds,
                int interactionCooldown) {
    static final int ARRIVAL = 8;
    static final int STATIC = 8 * 60;
    static final int WATCH = 20 * 60;
    static final int HUNT = 40 * 60;
    static final int MAX_SECONDS = HUNT + 180;
    private static final int ANGER_TRUST = -120;
    private static final int ANGER_START = 4;
    static final int MONSTER_DURATION = 60;
    private static final int CALM_INTERVAL = 10;
    static final int INTERACTION_INTERVAL = 3;
    static final Progress INITIAL = new Progress(true, 0, 0, 0);

    Progress(boolean enabled, int seconds, int trust, int retaliation) {
        this(enabled, seconds, trust, retaliation, 0, 0, 0);
    }

    static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("enabled").forGetter(Progress::enabled),
        Codec.intRange(0, MAX_SECONDS).fieldOf("seconds").forGetter(Progress::seconds),
        Codec.intRange(ANGER_TRUST, 12).fieldOf("trust").forGetter(Progress::trust),
        Codec.intRange(0, ANGER_START).optionalFieldOf("retaliation", 0).forGetter(Progress::retaliation),
        Codec.intRange(0, MONSTER_DURATION).optionalFieldOf("huntSeconds", 0).forGetter(Progress::huntSeconds),
        Codec.intRange(0, CALM_INTERVAL - 1).optionalFieldOf("calmSeconds", 0).forGetter(Progress::calmSeconds),
        Codec.intRange(0, INTERACTION_INTERVAL).optionalFieldOf("interactionCooldown", 0)
            .forGetter(Progress::interactionCooldown)
    ).apply(instance, Progress::new));

    enum Phase {
        WAITING("Ожидание"), GUIDE("Контакт"), STATIC("Помехи"),
        ANGER("Ярость"), WATCH("Наблюдение"), HUNT("Охота");

        final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    Phase phase() {
        if (retaliation > 1) return Phase.ANGER;
        if (retaliation == 1) return Phase.HUNT;
        if (seconds < ARRIVAL) return Phase.WAITING;
        // Trust can hasten or delay the timed phases.
        if (seconds >= HUNT + storyShift()) return Phase.HUNT;
        if (seconds >= WATCH + storyShift()) return Phase.WATCH;
        if (seconds >= STATIC) return Phase.STATIC;
        return Phase.GUIDE;
    }

    Progress advance() {
        int nextSecond = Math.min(seconds + 1, MAX_SECONDS);
        int nextCooldown = Math.max(0, interactionCooldown - 1);
        if (phase() == Phase.HUNT) {
            if (huntSeconds + 1 >= MONSTER_DURATION) {
                return new Progress(enabled, Math.min(nextSecond, STATIC), 0, 0);
            }
            return new Progress(enabled, nextSecond, trust, retaliation, huntSeconds + 1, 0, nextCooldown);
        }
        int nextRetaliation = retaliation > 1 ? retaliation - 1 : retaliation;
        int nextCalm = 0;
        int nextTrust = trust;
        if (trust < 0 && (phase() == Phase.GUIDE || phase() == Phase.STATIC)) {
            nextCalm = calmSeconds + 1;
            if (nextCalm >= CALM_INTERVAL) {
                nextTrust++;
                nextCalm = 0;
            }
        }
        return new Progress(enabled, nextSecond, nextTrust, nextRetaliation, 0, nextCalm, nextCooldown);
    }

    Progress withEnabled(boolean value) {
        return new Progress(value, seconds, trust, retaliation, huntSeconds, calmSeconds, interactionCooldown);
    }

    int angerPercent() {
        if (phase() == Phase.ANGER || phase() == Phase.HUNT) return 100;
        return Math.clamp(Math.round(-trust * 100f / -ANGER_TRUST), 0, 100);
    }

    int hitsUntilMonster() {
        return Math.max(0, (trust - ANGER_TRUST + 1) / 2);
    }

    int provocationSecondsRemaining() {
        return hitsUntilMonster() * INTERACTION_INTERVAL;
    }

    private int storyShift() {
        return Math.clamp(trust, -12, 12) * 15;
    }

    int secondsUntilMonster() {
        if (phase() == Phase.ANGER) return retaliation - 1;
        return Math.max(0, HUNT + storyShift() - seconds);
    }

    int secondsUntilNormal() {
        if (phase() == Phase.HUNT) return Math.max(0, MONSTER_DURATION - huntSeconds);
        return Math.max(0, -trust * CALM_INTERVAL - calmSeconds);
    }

    Progress changeTrust(int delta) {
        Phase current = phase();
        if ((current != Phase.GUIDE && current != Phase.STATIC) || interactionCooldown > 0) return this;
        int nextTrust = Math.clamp(trust + delta, ANGER_TRUST, 12);
        int nextRetaliation = delta < 0 && nextTrust <= ANGER_TRUST ? ANGER_START : 0;
        return new Progress(enabled, seconds, nextTrust, nextRetaliation, 0, 0, INTERACTION_INTERVAL);
    }

    static Progress demo(Phase phase) {
        int time = switch (phase) {
            case WAITING -> 0;
            case GUIDE, ANGER -> ARRIVAL;
            case STATIC -> STATIC;
            case WATCH -> WATCH;
            case HUNT -> HUNT;
        };
        return phase == Phase.ANGER ? new Progress(true, time, ANGER_TRUST, ANGER_START)
            : new Progress(true, time, 0, 0);
    }
}
