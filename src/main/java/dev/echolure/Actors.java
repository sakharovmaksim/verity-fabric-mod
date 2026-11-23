package dev.echolure;

import java.util.Locale;
import java.util.UUID;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

final class Actors {
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1, 2, -2, 3, -3, 4, -4};

    private static final ResourceKey<EntityType<?>> WATCHER_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
        Identifier.fromNamespaceAndPath("echo_lure", "watcher"));
    static final EntityType<Watcher> WATCHER = Registry.register(BuiltInRegistries.ENTITY_TYPE, WATCHER_KEY,
        EntityType.Builder.<Watcher>of(Watcher::new, MobCategory.MONSTER).sized(0.65f, 2.23f).eyeHeight(1.96f)
            .clientTrackingRange(8).noSave().noSummon().build(WATCHER_KEY));

    private Actors() {}

    static void register() {
        FabricDefaultAttributeRegistry.register(WATCHER, Zombie.createAttributes());
    }

    static boolean spawn(Mob actor, ServerPlayer player, boolean grounded) {
        ServerLevel level = player.level();
        Vec3 view = player.getLookAngle();
        double heading = Math.atan2(view.z, view.x) + (grounded ? Math.PI : 0);
        for (int i = 0; i < 24; i++) {
            double angle = heading + (i == 0 ? 0 : player.getRandom().nextDouble() * 2 * Math.PI);
            double distance = grounded ? 11 + player.getRandom().nextDouble() * 6 : 2.5;
            int x = (int) Math.floor(player.getX() + distance * Math.cos(angle));
            int z = (int) Math.floor(player.getZ() + distance * Math.sin(angle));
            int y = player.getBlockY() + (grounded ? 0 : 1);
            if (spawnInColumn(actor, level, new BlockPos(x, y, z), grounded)) return true;
        }
        return false;
    }

    static boolean spawnGroundedAt(Mob actor, ServerLevel level, BlockPos origin) {
        return spawnInColumn(actor, level, origin, true);
    }

    private static boolean spawnInColumn(Mob actor, ServerLevel level, BlockPos origin, boolean grounded) {
        if (level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4) == null) return false;
        for (int offset : VERTICAL_OFFSETS) {
            BlockPos at = origin.offset(0, offset, 0);
            if (level.isOutsideBuildHeight(at.below()) || level.isOutsideBuildHeight(at.above(3))
                || !level.getWorldBorder().isWithinBounds(at)) continue;
            BlockPos floor = at.below();
            if (grounded && !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) continue;
            actor.snapTo(Vec3.atBottomCenterOf(at));
            if (level.noCollision(actor) && !level.containsAnyLiquid(actor.getBoundingBox())) {
                return level.addFreshEntity(actor);
            }
        }
        return false;
    }

    enum Face {
        CALM, BLINK, SPEAKING, PLEASED, STARTLED, UNEASY, ANGRY;

        private final Identifier model = Identifier.fromNamespaceAndPath("echo_lure",
            "guide_orb_" + name().toLowerCase(Locale.ROOT));
    }

    static final class Guide extends Allay {
        private final UUID owner;
        private Face idleFace;
        private Orb orb;
        private Face reaction;
        private int reactionUntil;
        private int blinkAt = 45;

        Guide(ServerPlayer player, boolean uneasy) {
            super(EntityTypes.ALLAY, player.level());
            owner = player.getUUID();
            idleFace = uneasy ? Face.UNEASY : Face.CALM;
            setCustomName(Component.translatableWithFallback(EchoLure.GUIDE_NAME_KEY, "Отблеск"));
            addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION,
                0, false, false, false));
            setPersistenceRequired();
            setSilent(true);
            setPermanentlyInvulnerable(true);
            setNoGravity(true);
        }

        boolean attachOrb() {
            var level = (ServerLevel) level();
            var display = new Orb(this, level);
            display.snapTo(orbCenter());
            display.show(idleFace);
            if (!level.addFreshEntity(display)) return false;
            orb = display;
            return true;
        }

        boolean hasOrb() {
            return orb != null && !orb.isRemoved();
        }

        void enrage() {
            idleFace = Face.ANGRY;
            reactionUntil = 0;
            if (hasOrb()) orb.show(Face.ANGRY);
        }

        void react(Face face, int ticks) {
            if (idleFace == Face.ANGRY) return;
            reaction = face;
            reactionUntil = tickCount + ticks;
            if (hasOrb()) orb.show(face);
        }

        private Vec3 orbCenter() {
            return position().add(0, getBbHeight() * 0.55, 0);
        }

        @Override
        public void tick() {
            super.tick();
            if (!hasOrb()) return;
            orb.setPos(orbCenter());
            if (idleFace == Face.ANGRY) return;
            if (tickCount >= blinkAt + 3) blinkAt = tickCount + 55 + random.nextInt(55);
            Face face = idleFace;
            if (tickCount < reactionUntil) {
                // Alternate between an open and closed mouth while answering.
                if (reaction != Face.SPEAKING || ((reactionUntil - tickCount) / 4) % 2 == 0) {
                    face = reaction;
                }
            } else if (tickCount >= blinkAt) {
                face = Face.BLINK;
            }
            orb.show(face);
        }

        @Override
        public void remove(RemovalReason reason) {
            if (hasOrb()) orb.discard();
            super.remove(reason);
        }

        boolean belongsTo(ServerPlayer player) {
            return owner.equals(player.getUUID());
        }

        void follow(ServerPlayer player) {
            getLookControl().setLookAt(player, 30, 30);
            if (distanceToSqr(player) > 9) {
                getNavigation().moveTo(player.getX(), player.getEyeY(), player.getZ(), 1.25);
            } else {
                getNavigation().stop();
            }
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            // This actor follows the signal owner instead of the vanilla Allay brain.
        }

        @Override
        protected InteractionResult mobInteract(Player player, InteractionHand hand) {
            return InteractionResult.FAIL;
        }

        @Override public boolean canPickUpLoot() { return false; }
        @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
        @Override public boolean shouldBeSaved() { return false; }
    }

    private static final class Orb extends Display.ItemDisplay {
        private final Guide guide;
        private Face currentFace;

        private Orb(Guide guide, ServerLevel level) {
            super(EntityTypes.ITEM_DISPLAY, level);
            this.guide = guide;

            // Orient the face to the camera and interpolate the guide's movement.
            var settings = new CompoundTag();
            settings.putString(Display.TAG_BILLBOARD, "center");
            settings.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, 1);
            settings.putString("item_display", "fixed");
            var brightness = new CompoundTag();
            brightness.putInt("block", 15);
            brightness.putInt("sky", 15);
            settings.put(Display.TAG_BRIGHTNESS, brightness);
            readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), settings));
        }

        private void show(Face face) {
            if (face == currentFace) return;
            var item = new ItemStack(Items.GOLD_NUGGET);
            item.set(DataComponents.ITEM_MODEL, face.model);
            getSlot(0).set(item);
            currentFace = face;
        }

        @Override
        public void tick() {
            if (guide.isRemoved()) {
                discard();
                return;
            }
            super.tick();
        }

        @Override public boolean shouldBeSaved() { return false; }
    }

    static final class Watcher extends Husk {
        private boolean hunter;
        private UUID owner;

        private Watcher(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }

        Watcher(ServerPlayer player, boolean hunter) {
            this(WATCHER, player.level());
            this.hunter = hunter;
            owner = player.getUUID();
            setCustomName(Component.literal("Обратный сигнал"));
            getAttribute(Attributes.SCALE).setBaseValue(1.12);
            getAttribute(Attributes.ARMOR).setBaseValue(10);
            getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(3);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(30);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.30);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5);
            getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0);
            setHealth(getMaxHealth());
            setPersistenceRequired();
            setSilent(!hunter);
            setNoAi(!hunter);
            setPermanentlyInvulnerable(!hunter);
        }

        @Override
        protected void registerGoals() {
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
            goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 18));
        }

        @Override
        public boolean doHurtTarget(ServerLevel level, Entity victim) {
            return hunter && owner.equals(victim.getUUID()) && super.doHurtTarget(level, victim);
        }

        @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
        @Override public boolean shouldBeSaved() { return false; }
        @Override public boolean shouldDropExperience() { return false; }
        @Override protected boolean shouldDropLoot(ServerLevel level) { return false; }
        @Override protected boolean convertsInWater() { return false; }
    }
}
