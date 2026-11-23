package dev.echolure;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

final class ArrivalBox extends Mob {
    private static final EntityDataAccessor<Integer> ELAPSED =
        SynchedEntityData.defineId(ArrivalBox.class, EntityDataSerializers.INT);

    ArrivalBox(EntityType<? extends ArrivalBox> type, Level level) {
        super(type, level);
        setNoAi(true);
        setSilent(true);
        setPermanentlyInvulnerable(true);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ELAPSED, 0);
    }

    void setSceneSeconds(int seconds) {
        entityData.set(ELAPSED, seconds * 20);
    }

    float opening(float partialTick) {
        return Math.clamp((entityData.get(ELAPSED) + partialTick - (Progress.ARRIVAL - 3) * 20) / 60f, 0, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && entityData.get(ELAPSED) < Progress.ARRIVAL * 20) {
            entityData.set(ELAPSED, entityData.get(ELAPSED) + 1);
        }
    }

    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity entity) { }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
}
