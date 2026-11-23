package dev.echolure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

record Progress(boolean enabled, int seconds, int trust, int retaliation) {
    static final int ARRIVAL = 25;
    static final int STATIC = 8 * 60;
    static final int WATCH = 20 * 60;
    static final int HUNT = 40 * 60;
    static final int MAX_SECONDS = HUNT + 180;
    private static final int ANGER_TRUST = -6;
    private static final int ANGER_START = 4;
    static final Progress INITIAL = new Progress(true, 0, 0, 0);

    static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("enabled").forGetter(Progress::enabled),
        Codec.intRange(0, MAX_SECONDS).fieldOf("seconds").forGetter(Progress::seconds),
        Codec.intRange(-12, 12).fieldOf("trust").forGetter(Progress::trust),
        Codec.intRange(0, ANGER_START).optionalFieldOf("retaliation", 0).forGetter(Progress::retaliation)
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
        if (seconds >= HUNT + trust * 15) return Phase.HUNT;
        if (seconds >= WATCH + trust * 15) return Phase.WATCH;
        if (seconds >= STATIC) return Phase.STATIC;
        return Phase.GUIDE;
    }

    Progress advance() {
        int nextSecond = Math.min(seconds + 1, MAX_SECONDS);
        int nextRetaliation = retaliation > 1 ? retaliation - 1 : retaliation;
        if (nextSecond == seconds && nextRetaliation == retaliation) return this;
        return new Progress(enabled, nextSecond, trust, nextRetaliation);
    }

    Progress changeTrust(int delta) {
        Phase current = phase();
        if (current != Phase.GUIDE && current != Phase.STATIC) return this;
        int nextTrust = Math.clamp(trust + delta, -12, 12);
        int nextRetaliation = delta < 0 && nextTrust <= ANGER_TRUST ? ANGER_START : 0;
        return new Progress(enabled, seconds, nextTrust, nextRetaliation);
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
