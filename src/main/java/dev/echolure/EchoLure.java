package dev.echolure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class EchoLure implements ModInitializer {
    public static final String GUIDE_NAME_KEY = "entity.echo_lure.guide";

    private final Map<UUID, Session> sessions = new HashMap<>();
    private AttachmentType<Progress> progressType;

    @Override
    public void onInitialize() {
        Actors.register();
        PayloadTypeRegistry.clientboundPlay().register(CarryState.TYPE, CarryState.CODEC);
        progressType = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("echo_lure", "progress"),
            builder -> builder.persistent(Progress.CODEC).copyOnDeath());
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(commands()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Session session : sessions.values()) session.close();
            sessions.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearSession(handler.player));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, player, type) -> {
            String question = Dialogue.addressedQuestion(message.signedContent());
            if (question == null) return true;
            ask(player, question);
            return false;
        });

        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof Actors.Guide guide)) {
                return InteractionResult.PASS;
            }
            if (hand == InteractionHand.MAIN_HAND && guide.belongsTo(serverPlayer) && active(serverPlayer)) {
                useGuide(serverPlayer, session(serverPlayer), guide);
            }
            return InteractionResult.SUCCESS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !(target instanceof Actors.Guide guide)) {
                return InteractionResult.PASS;
            }
            if (guide.belongsTo(serverPlayer) && active(serverPlayer)) {
                var session = session(serverPlayer);
                guide.react(Actors.Face.STARTLED, 22);
                if (session.mayChangeTrust() && storeTrust(serverPlayer, -2)) {
                    if (progress(serverPlayer).phase() == Progress.Phase.ANGER) {
                        guide.enrage();
                    } else {
                        reply(serverPlayer, "Я запомню этот стук.");
                    }
                }
            }
            return InteractionResult.FAIL;
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> commands() {
        var demo = Commands.literal("demo")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        for (Progress.Phase phase : Progress.Phase.values()) {
            demo.then(Commands.literal(phase.name().toLowerCase(Locale.ROOT)).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                clearSession(player);
                Progress next = Progress.demo(phase);
                store(player, next);
                reply(player, "Пробная фаза — " + phase.label + ".");
                if (active(player)) session(player).update(next);
                return 1;
            }));
        }
        return Commands.literal("yellow")
            .then(Commands.literal("throw").executes(context -> handleGuide(
                context.getSource().getPlayerOrException())))
            .then(Commands.literal("status").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Progress progress = progress(player);
                reply(player, progress.enabled()
                    ? "%s · %d:%02d · доверие %+d".formatted(
                        progress.phase().label, progress.seconds() / 60, progress.seconds() % 60, progress.trust())
                        + " · " + AngerBar.label(progress)
                    : "Сигнал отключён. /yellow start начнёт историю заново.");
                return 1;
            }))
            .then(Commands.literal("start").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (progress(player).enabled()) {
                    reply(player, "Сигнал уже включён. /yellow status покажет текущую фазу.");
                    return 0;
                }
                clearSession(player);
                store(player, Progress.INITIAL);
                reply(player, "Слушаю эфир. Оставайся в Верхнем мире.");
                return 1;
            }))
            .then(Commands.literal("stop").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                clearSession(player);
                Progress progress = progress(player);
                store(player, progress.withEnabled(false));
                reply(player, "Сигнал отключён.");
                return 1;
            }))
            .then(Commands.literal("ask").then(Commands.argument("question", StringArgumentType.greedyString())
                .executes(context -> ask(context.getSource().getPlayerOrException(),
                    StringArgumentType.getString(context, "question")))))
            .then(demo);
    }

    private void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!active(player)) {
                clearSession(player);
                continue;
            }
            Progress previous = progress(player);
            Session session = session(player);
            if (session.actor instanceof Actors.Guide guide && guide.thrownIntoLava()) {
                guide.react(Actors.Face.STARTLED, 30);
                if (session.mayChangeTrust() && storeTrust(player, -2)) {
                    reply(player, "Горячо! Вытащи меня из лавы!");
                    sound(player, SoundEvents.FIRE_EXTINGUISH, 0.8f, 1.2f);
                }
                previous = progress(player);
            }
            if (previous.phase() == Progress.Phase.WAITING && !session.prepareArrival()) continue;
            Progress next = previous.advance();
            if (next != previous) store(player, next);
            if (previous.phase() == Progress.Phase.HUNT && next.phase() != Progress.Phase.HUNT) {
                reply(player, "Я успокоился. Давай попробуем ещё раз.");
                sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            } else if (next.phase() != previous.phase()) {
                announce(player, next.phase());
            }
            session.update(next);
        }
    }

    private boolean active(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping()
            && player.level().dimension().equals(Level.OVERWORLD) && progress(player).enabled();
    }

    private int ask(ServerPlayer player, String question) {
        Progress progress = progress(player);
        if (!active(player) || progress.phase() == Progress.Phase.WAITING) {
            reply(player, progress.enabled() ? "Связь пока недоступна в этом месте или в этой фазе."
                : "Сигнал отключён. Включить снова — /yellow start.");
            return 0;
        }
        Session session = session(player);
        if (session.age < session.nextQuestion) {
            reply(player, "Дай сигналу несколько секунд.");
            return 0;
        }
        if (question.length() > 500) {
            reply(player, "Задай вопрос покороче — до 500 символов.");
            return 0;
        }
        session.nextQuestion = session.age + 5;
        String answer = Dialogue.answer(player, question, progress.phase());
        if (answer != null) {
            speak(player, session, answer);
            return 1;
        }
        if (session.pending != null) {
            reply(player, "Я ещё обдумываю предыдущий вопрос.");
            return 0;
        }
        var pos = player.blockPosition();
        String context = "Фаза: %s. Доверие: %d. Координаты игрока: %d, %d, %d. Снаружи %s."
            .formatted(progress.phase().label, progress.trust(), pos.getX(), pos.getY(), pos.getZ(),
                player.level().isDarkOutside() ? "темно" : "светло");
        var server = player.level().getServer();
        var request = OllamaChat.ask(question, context, List.copyOf(session.history));
        session.pending = request;
        reply(player, "Думаю…");
        request.whenComplete((generated, failure) -> server.execute(() -> {
            if (sessions.get(player.getUUID()) != session || session.pending != request) return;
            session.pending = null;
            if (!active(player) || progress(player).phase() != progress.phase()) return;
            if (failure != null) {
                reply(player, "Модель сейчас не отвечает. Открой Ollama и проверь, что скачана gemma3:4b.");
                return;
            }
            session.history.add(new OllamaChat.Message("user", question));
            session.history.add(new OllamaChat.Message("assistant", generated));
            while (session.history.size() > 12) session.history.removeFirst();
            speak(player, session, generated);
        }));
        return 1;
    }

    private static void speak(ServerPlayer player, Session session, String answer) {
        if (session.actor instanceof Actors.Guide guide) guide.react(Actors.Face.SPEAKING, 50);
        reply(player, answer);
    }

    private void useGuide(ServerPlayer player, Session session, Actors.Guide guide) {
        var held = player.getMainHandItem();
        if (guide.isCarriedBy(player)) {
            handleGuide(player);
            return;
        }
        if (held.is(Items.AMETHYST_SHARD)) {
            if (progress(player).trust() == 12) {
                guide.react(Actors.Face.PLEASED, 32);
                reply(player, "Осколок уже звучит в полную силу. Оставь этот себе.");
            } else if (session.mayChangeTrust() && storeTrust(player, 2)) {
                if (!player.isCreative()) held.shrink(1);
                guide.react(Actors.Face.PLEASED, 32);
                reply(player, "Теперь я слышу тебя чуть яснее.");
                sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.85f, 1.2f);
            }
        } else {
            handleGuide(player);
        }
    }

    private int handleGuide(ServerPlayer player) {
        if (!active(player) || !(session(player).actor instanceof Actors.Guide guide)
            || progress(player).phase() == Progress.Phase.ANGER) {
            reply(player, "Сейчас рядом нет шарика, которого можно взять.");
            return 0;
        }
        if (guide.isCarriedBy(player)) {
            guide.throwFrom(player);
            reply(player, "Эй! Осторожнее!");
            return 1;
        }
        if (!guide.take(player)) {
            reply(player, "Подойди к шарику ближе — на три блока — и убери преграды между вами.");
            return 0;
        }
        reply(player, "Держишь меня? Нажми ПКМ или G, чтобы бросить в направлении взгляда.");
        return 1;
    }

    private Progress progress(ServerPlayer player) {
        return ((AttachmentTarget) player).getAttachedOrElse(progressType, Progress.INITIAL);
    }

    private void store(ServerPlayer player, Progress progress) {
        ((AttachmentTarget) player).setAttached(progressType, progress);
    }

    private boolean storeTrust(ServerPlayer player, int delta) {
        var previous = progress(player);
        var next = previous.changeTrust(delta);
        if (previous.equals(next)) return false;
        store(player, next);
        session(player).angerBar.update(next);
        if (next.phase() != previous.phase()) announce(player, next.phase());
        return true;
    }

    private Session session(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session != null && session.player == player) return session;
        if (session != null) session.close();
        Session fresh = new Session(player);
        sessions.put(player.getUUID(), fresh);
        return fresh;
    }

    private void clearSession(ServerPlayer player) {
        Session session = sessions.remove(player.getUUID());
        if (session != null) session.close();
    }

    private static void announce(ServerPlayer player, Progress.Phase phase) {
        switch (phase) {
            case WAITING -> { }
            case GUIDE -> {
                reply(player, "Слышу тебя. Напиши «verity: привет!» и задай любой вопрос.");
                sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.1f);
            }
            case STATIC -> reply(player, "Почему эхо возвращается раньше твоего голоса?");
            case ANGER -> {
                reply(player, "Хватит. Теперь моя очередь.");
                sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.55f);
            }
            case WATCH -> reply(player, "Я нашёл источник. Останься там, где стоишь.");
            case HUNT -> reply(player, "Теперь я говорю с другой стороны.");
        }
    }

    private static void reply(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("◈ Желтый шарик  ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
    }

    private static void sound(ServerPlayer player, SoundEvent event, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(Holder.direct(event), SoundSource.AMBIENT,
            player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    private static final class Session {
        private final ServerPlayer player;
        private final AngerBar angerBar;
        private final List<OllamaChat.Message> history = new ArrayList<>();
        private CompletableFuture<String> pending;
        private Progress.Phase phase;
        private Mob actor;
        private ArrivalBox box;
        private int boxExpires;
        private int age;
        private int nextQuestion;
        private int nextTrust;
        private int nextEvent;
        private int expire;

        private Session(ServerPlayer player) {
            this.player = player;
            angerBar = new AngerBar(player);
        }

        private boolean mayChangeTrust() {
            if (age < nextTrust) return false;
            nextTrust = age + Progress.INTERACTION_INTERVAL;
            return true;
        }

        private boolean prepareArrival() {
            if (box != null && (box.isRemoved() || box.distanceToSqr(player) > 16 * 16)) {
                box.discard();
                box = null;
            }
            if (box != null) return true;
            var arrival = new ArrivalBox(Actors.BOX, player.level());
            if (!Actors.spawnBox(arrival, player)) return false;
            box = arrival;
            boxExpires = 0;
            sound(player, SoundEvents.CHEST_OPEN, 0.45f, 0.7f);
            return true;
        }

        private void update(Progress progress) {
            angerBar.update(progress);
            Progress.Phase newPhase = progress.phase();
            age++;
            if (box != null && (box.isRemoved() || (boxExpires > 0 && age >= boxExpires))) {
                box.discard();
                box = null;
            }
            BlockPos transformationPoint = null;
            if (phase != newPhase) {
                if (newPhase == Progress.Phase.HUNT && phase == Progress.Phase.ANGER
                    && actor instanceof Actors.Guide guide && guide.level() == player.level()
                    && guide.distanceToSqr(player) <= 16 * 16) {
                    transformationPoint = guide.blockPosition();
                }
                boolean leavingBox = phase == Progress.Phase.WAITING && newPhase == Progress.Phase.GUIDE;
                if (newPhase != Progress.Phase.ANGER && newPhase != Progress.Phase.WAITING && !leavingBox) {
                    clearActor();
                }
                phase = newPhase;
                nextEvent = age;
            }
            if (actor != null && (actor.isRemoved() || !actor.isAlive() || actor.level() != player.level()
                || actor.distanceToSqr(player) > 32 * 32)) clearActor();

            switch (newPhase) {
                case WAITING -> {
                    if (prepareArrival()) box.setSceneSeconds(progress.seconds());
                }
                case GUIDE, STATIC, ANGER -> guide();
                case WATCH, HUNT -> encounter(newPhase == Progress.Phase.HUNT, transformationPoint,
                    progress.secondsUntilNormal());
            }
        }

        private void guide() {
            if (actor instanceof Actors.Guide guide && (!guide.hasOrb() || guide.distanceToSqr(player) > 16 * 16)) {
                clearActor();
            }
            if (actor == null) {
                var guide = new Actors.Guide(player, phase == Progress.Phase.STATIC);
                boolean spawned = box == null ? Actors.spawn(guide, player, false) : guide.emergeFrom(box);
                if (spawned && guide.attachOrb()) {
                    actor = guide;
                    if (box != null) {
                        box.setSceneSeconds(Progress.ARRIVAL);
                        boxExpires = age + 3;
                        sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
                    }
                } else {
                    guide.discard();
                }
            }
            if (actor instanceof Actors.Guide guide) {
                if (phase == Progress.Phase.ANGER) guide.enrage();
                guide.follow(player);
            }
            if (phase == Progress.Phase.STATIC && age >= nextEvent) {
                sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.75f, 0.48f);
                nextEvent = age + 28 + player.getRandom().nextInt(20);
            }
        }

        private void encounter(boolean hunt, BlockPos transformationPoint, int remainingSeconds) {
            boolean peaceful = player.level().getDifficulty() == Difficulty.PEACEFUL;
            if (actor != null && (age >= expire || (hunt && (peaceful || player.isCreative())))) clearActor();
            if (actor != null || age < nextEvent) return;
            boolean attacks = hunt && !peaceful && !player.isCreative();
            if (hunt && !attacks) {
                sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.75f, 0.45f);
                nextEvent = age + 25;
                return;
            }

            var watcher = new Actors.Watcher(player, attacks);
            boolean spawnedAtGuide = transformationPoint != null
                && Actors.spawnGroundedAt(watcher, player.level(), transformationPoint);
            if (!spawnedAtGuide && !Actors.spawn(watcher, player, true)) {
                nextEvent = age + 5;
                return;
            }
            actor = watcher;
            expire = age + (attacks ? remainingSeconds : 8);
            nextEvent = age + (attacks ? 55 : 33) + player.getRandom().nextInt(14);
            watcher.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
            if (attacks) {
                watcher.setTarget(player);
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, true, false));
            }
            sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.45f);
            player.level().sendParticles(ParticleTypes.REVERSE_PORTAL,
                watcher.getX(), watcher.getY() + 1, watcher.getZ(), 24, 0.35, 0.85, 0.35, 0.04);
        }

        private void clearActor() {
            if (actor != null) actor.discard();
            actor = null;
            if (box != null) box.discard();
            box = null;
        }

        private void close() {
            angerBar.removeAllPlayers();
            if (pending != null) {
                var request = pending;
                pending = null;
                request.cancel(true);
            }
            history.clear();
            clearActor();
        }
    }
}
