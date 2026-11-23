package dev.echolure;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
        progressType = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("echo_lure", "progress"),
            builder -> builder.persistent(Progress.CODEC).copyOnDeath());
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(commands()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Session session : sessions.values()) session.clearActor();
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
                if (active(player)) session(player).update(next.phase());
                return 1;
            }));
        }
        return Commands.literal("echo")
            .then(Commands.literal("status").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Progress progress = progress(player);
                reply(player, progress.enabled()
                    ? "%s · %d:%02d · доверие %+d".formatted(
                        progress.phase().label, progress.seconds() / 60, progress.seconds() % 60, progress.trust())
                    : "Сигнал отключён. /echo start начнёт историю заново.");
                return 1;
            }))
            .then(Commands.literal("start").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (progress(player).enabled()) {
                    reply(player, "Сигнал уже включён. /echo status покажет текущую фазу.");
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
                store(player, new Progress(false, progress.seconds(), progress.trust(), progress.retaliation()));
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
            Progress next = previous.advance();
            if (next != previous) store(player, next);
            if (next.phase() != previous.phase()) announce(player, next.phase());
            session(player).update(next.phase());
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
                : "Сигнал отключён. Включить снова — /echo start.");
            return 0;
        }
        Session session = session(player);
        if (session.age < session.nextQuestion) {
            reply(player, "Дай сигналу несколько секунд.");
            return 0;
        }
        session.nextQuestion = session.age + 5;
        if (session.actor instanceof Actors.Guide guide) guide.react(Actors.Face.SPEAKING, 28);
        reply(player, Dialogue.answer(player, question, progress.phase()));
        return 1;
    }

    private void useGuide(ServerPlayer player, Session session, Actors.Guide guide) {
        var held = player.getMainHandItem();
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
            ask(player, "");
        }
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
        if (next.phase() != previous.phase()) announce(player, next.phase());
        return true;
    }

    private Session session(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session != null && session.player == player) return session;
        if (session != null) session.clearActor();
        Session fresh = new Session(player);
        sessions.put(player.getUUID(), fresh);
        return fresh;
    }

    private void clearSession(ServerPlayer player) {
        Session session = sessions.remove(player.getUUID());
        if (session != null) session.clearActor();
    }

    private static void announce(ServerPlayer player, Progress.Phase phase) {
        switch (phase) {
            case WAITING -> { }
            case GUIDE -> {
                reply(player, "Слышу тебя. Напиши «Отблеск: где алмазы?» или подойди ко мне.");
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
        player.sendSystemMessage(Component.literal("◈ Отблеск  ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
    }

    private static void sound(ServerPlayer player, SoundEvent event, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(Holder.direct(event), SoundSource.AMBIENT,
            player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    private static final class Session {
        private final ServerPlayer player;
        private Progress.Phase phase;
        private Mob actor;
        private int age;
        private int nextQuestion;
        private int nextTrust;
        private int nextEvent;
        private int expire;

        private Session(ServerPlayer player) {
            this.player = player;
        }

        private boolean mayChangeTrust() {
            if (age < nextTrust) return false;
            nextTrust = age + 3;
            return true;
        }

        private void update(Progress.Phase newPhase) {
            age++;
            BlockPos transformationPoint = null;
            if (phase != newPhase) {
                if (newPhase == Progress.Phase.HUNT && phase == Progress.Phase.ANGER
                    && actor instanceof Actors.Guide guide && guide.level() == player.level()
                    && guide.distanceToSqr(player) <= 16 * 16) {
                    transformationPoint = guide.blockPosition();
                }
                if (newPhase != Progress.Phase.ANGER) clearActor();
                phase = newPhase;
                nextEvent = age;
            }
            if (actor != null && (actor.isRemoved() || !actor.isAlive() || actor.level() != player.level()
                || actor.distanceToSqr(player) > 32 * 32)) clearActor();

            switch (newPhase) {
                case WAITING -> { }
                case GUIDE, STATIC, ANGER -> guide();
                case WATCH, HUNT -> encounter(newPhase == Progress.Phase.HUNT, transformationPoint);
            }
        }

        private void guide() {
            if (actor instanceof Actors.Guide guide && (!guide.hasOrb() || guide.distanceToSqr(player) > 16 * 16)) {
                clearActor();
            }
            if (actor == null) {
                var guide = new Actors.Guide(player, phase == Progress.Phase.STATIC);
                if (Actors.spawn(guide, player, false) && guide.attachOrb()) {
                    actor = guide;
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

        private void encounter(boolean hunt, BlockPos transformationPoint) {
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
            expire = age + (attacks ? 28 : 8);
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
        }
    }
}
