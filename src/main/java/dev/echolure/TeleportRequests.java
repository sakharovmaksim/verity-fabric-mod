package dev.echolure;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class TeleportRequests {
    static final int COOLDOWN = 30;
    static final int LANDING_RADIUS = 6;
    static final String USAGE = "Назови целые координаты в Верхнем мире: «телепортируй меня на 120 70 -35».";
    private static final Pattern REQUEST = Pattern.compile("^(?:(?:please|пожалуйста)[,\\s]+)?(?:"
        + "(?:(?:can|could) you\\s+)?teleport me"
        + "|(?:(?:ты )?можешь )?(?:телепортируй|перенеси|перемести)(?: меня)?"
        + "|(?:(?:ты )?можешь )?(?:телепортировать|перенести|переместить) меня)(?=$|[\\s!?.,:])");
    private static final String SEPARATOR = "(?:\\s*,\\s*|\\s+)";
    private static final String REQUEST_END = "(?:[,\\s]+(?:please|пожалуйста))?[.!?]*";
    private static final Pattern REFERENCE = Pattern.compile(
        "(?:туда|к этим координатам|на эти координаты|по этим координатам|there|to those (?:coordinates|coords))"
        + REQUEST_END);
    private static final Pattern COORDINATES = Pattern.compile("(?:to\\s+|на\\s+|в\\s+)?"
        + "(?:(?:coordinates|coords|координаты)(?:\\s*:\\s*|\\s+))?"
        + "([+-]?\\d+)" + SEPARATOR + "([+-]?\\d+)" + SEPARATOR + "([+-]?\\d+)"
        + REQUEST_END);

    record Request(BlockPos destination, boolean useLastLocation) {}

    private TeleportRequests() {}

    static Request parse(String question) {
        String query = question.strip().toLowerCase(Locale.ROOT).replace('−', '-');
        var request = REQUEST.matcher(query);
        if (!request.lookingAt()) return null;
        String target = query.substring(request.end()).strip();
        if (REFERENCE.matcher(target).matches()) return new Request(null, true);
        var coordinates = COORDINATES.matcher(target);
        if (!coordinates.matches()) return new Request(null, false);
        try {
            return new Request(new BlockPos(Integer.parseInt(coordinates.group(1)),
                Integer.parseInt(coordinates.group(2)), Integer.parseInt(coordinates.group(3))), false);
        } catch (NumberFormatException outOfRange) {
            return new Request(null, false);
        }
    }

    static BlockPos nearbyLanding(ServerPlayer player, BlockPos target) {
        int radius = LANDING_RADIUS;
        double nearest = radius * radius + 1;
        BlockPos landing = null;
        for (BlockPos candidate : BlockPos.betweenClosed(target.offset(-radius, -radius, -radius),
            target.offset(radius, radius, radius))) {
            double distance = target.distSqr(candidate);
            if (distance >= nearest || landingError(player, candidate) != null) continue;
            if (distance == 0) return target;
            nearest = distance;
            landing = candidate.immutable();
        }
        return landing;
    }

    static String landingError(ServerPlayer player, BlockPos feet) {
        var level = player.level();
        if (!Level.isInSpawnableBounds(feet) || feet.getY() <= level.getMinY() || feet.getY() >= level.getMaxY()) {
            return "Эти координаты за пределами мира. Выбери другое место.";
        }
        var standing = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(feet));
        if (!level.getWorldBorder().isWithinBounds(standing) || standing.maxY > level.getMaxY() + 1) {
            return "Эти координаты за пределами мира. Выбери другое место.";
        }
        if (!level.hasChunksAt(BlockPos.containing(standing.minX, standing.minY, standing.minZ),
            BlockPos.containing(standing.maxX, standing.maxY, standing.maxZ))) {
            return "Это место пока не загружено. Назови координаты поближе.";
        }
        BlockPos floor = feet.below();
        var support = level.getBlockState(floor);
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
            || !support.isFaceSturdy(level, floor, Direction.UP) || !support.getFluidState().isEmpty()
            || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CACTUS)
            || !level.noCollision(player, standing) || level.containsAnyLiquid(standing)) {
            return "Там нет безопасного места для приземления. Назови свободный блок над твёрдым полом.";
        }
        return null;
    }

    static boolean teleport(ServerPlayer player, BlockPos feet) {
        Vec3 destination = Vec3.atBottomCenterOf(feet);
        if (!player.teleportTo(player.level(), destination.x, destination.y, destination.z,
            Set.of(), player.getYRot(), player.getXRot(), true)) return false;
        player.resetFallDistance();
        return true;
    }
}
