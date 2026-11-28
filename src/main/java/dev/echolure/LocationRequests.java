package dev.echolure;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

final class LocationRequests {
    private static final int BLOCK_RADIUS = 32;
    private static final int ENTITY_RADIUS = 64;
    private static final int BIOME_RADIUS = 2048;
    private static final int STRUCTURE_SEARCH_RINGS = 12;
    private static final Pattern REPEAT = Pattern.compile("(?:еще раз |напомни(?: мне)? |повтори(?: мне)? )"
        + "(?:эти |те )?координаты|(?:repeat|remind me of) (?:(?:the|those) )?(?:coords|coordinates)");
    private static final Set<String> REFERENCES = Set.of("это", "он", "она", "они", "именно", "it", "that", "they", "exactly");
    private static final Set<String> SPAWN_NAMES = Set.of("спавн", "точка возрождения", "точку возрождения",
        "точки возрождения", "spawn", "world spawn");
    private static final Pattern SELF = Pattern.compile("где я(?: сейчас)?(?: нахожусь)?"
        + "|(?:(?:дай|скажи|покажи) )?(?:мои )?координаты|какие у меня координаты"
        + "|where am i|(?:(?:what are|show me|tell me) )?my (?:coords|coordinates)|coords");
    private static final Pattern REQUEST = Pattern.compile("^(?:(?:пожалуйста|please|а|а ну)[,\\s]+)?"
        + "(?:(?:можешь(?: ли ты)?|can you|could you)[,\\s]+)?"
        + "(?:(?:(?:дай(?: мне)?|скажи(?: мне)?|напиши|покажи|подскажи|мне нужны|какие) )?координаты"
        + "|где(?: (?:мне |я могу )?(?:можно )?(?:найти|добыть|искать|отыскать|есть|находится|находятся))?"
        + "|найди|найти|отыщи"
        + "|(?:покажи|показать|подскажи|скажи)(?: мне)?[,\\s]+где(?: (?:найти|находится|находятся))?"
        + "|where(?: (?:is|are|to find|(?:can|do) (?:i|we) (?:find|look for))|'s)"
        + "|(?:show|tell) me where(?: (?:to find|(?:is|are)))?|find|locate"
        + "|(?:(?:give|show|tell) me |what are )?(?:the )?(?:coordinates|coords)(?: (?:of|for|to))?"
        + "|покажи(?: мне)?(?= ближайш)|show me(?= (?:the )?(?:nearest|closest) ))"
        + "(?:\\s*[:,]\\s*|\\s+)(.*)$");
    private static final Pattern QUALIFIER = Pattern.compile("^(биом[а]?|biome|структур[ауы]|structure|"
        + "моб[а]?|mob|entity|блок[а]?|block)\\s+(?:of\\s+)?(.+)$");
    private static final Pattern DETAILS = Pattern.compile(
        "^(?:(?:the|a|an|ближайш[а-я]+|nearest|closest|мест[аоу] с|place with)\\s+)+"
        + "|(?:[,\\s]+(?:please|пожалуйста|рядом|поблизости|near me|nearby))+$"
        + "|\\s+(?:is|are|находится|находятся)$");

    enum Kind { ANY, SELF, BLOCK, ENTITY, BIOME, STRUCTURE }

    record Request(String target, Kind kind) {}

    record Result(String message, BlockPos position, boolean horizontalOnly) {
        Result(String message, BlockPos position) {
            this(message, position, false);
        }

        Result(String message) {
            this(message, null);
        }
    }

    private record Ore(List<Block> blocks, String hint) {}

    private LocationRequests() {}

    static boolean isRepeat(String question) {
        return REPEAT.matcher(normalizeQuestion(question)).matches();
    }

    static Request parse(String question) {
        String query = normalizeQuestion(question);
        if (SELF.matcher(query).matches()) return new Request("", Kind.SELF);
        var match = REQUEST.matcher(query);
        if (!match.matches()) return null;
        String target = DETAILS.matcher(match.group(1).strip()).replaceAll("").strip();
        if (REFERENCES.contains(target)) return null;
        var qualifier = QUALIFIER.matcher(target);
        if (!qualifier.matches()) return new Request(target, Kind.ANY);
        Kind kind = switch (qualifier.group(1)) {
            case "block", "блок", "блока" -> Kind.BLOCK;
            case "mob", "entity", "моб", "моба" -> Kind.ENTITY;
            case "biome", "биом", "биома" -> Kind.BIOME;
            default -> Kind.STRUCTURE;
        };
        return new Request(qualifier.group(2), kind);
    }

    private static String normalizeQuestion(String question) {
        return question.strip().toLowerCase(Locale.ROOT).replace('ё', 'е')
            .replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
    }

    static Result answer(ServerPlayer player, Request request) {
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        if (request.kind() == Kind.SELF) {
            return new Result("Ты на %d, %d, %d.".formatted(origin.getX(), origin.getY(), origin.getZ()), origin);
        }
        String target = request.target();
        Kind kind = request.kind();
        String name = ResourceRequests.normalize(target);
        Identifier id = Identifier.tryParse(target.replace(' ', '_'));
        boolean explicitId = target.contains(":");

        if (kind == Kind.ANY && SPAWN_NAMES.contains(target)) {
            return found(target, level.getWorldBorderAdjustedRespawnData(level.getRespawnData()).pos());
        }
        if (kind == Kind.ANY || kind == Kind.BLOCK) {
            Ore ore = explicitId ? null : Names.ORES.get(name);
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null && !explicitId) block = Names.BLOCKS.get(name);
            if (ore != null || block != null) {
                List<Block> blocks = ore == null ? List.of(block) : ore.blocks();
                BlockPos pos = nearestBlock(level, origin, state -> blocks.contains(state.getBlock()));
                if (pos != null) return found(target, pos);
                String missing = "В загруженных чанках в радиусе %d блоков не нашёл «%s».".formatted(BLOCK_RADIUS, target);
                return new Result(ore == null || ore.hint().isEmpty() ? missing : missing + " " + ore.hint());
            }
        }
        if (kind == Kind.ANY || kind == Kind.ENTITY) {
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null && !explicitId) type = Names.ENTITIES.get(name);
            if (type != null && type != EntityTypes.PLAYER) {
                BlockPos pos = nearestEntity(level, origin, type);
                return pos == null ? new Result("В радиусе %d блоков не нашёл «%s».".formatted(ENTITY_RADIUS, target))
                    : found(target, pos);
            }
        }
        if (kind == Kind.ANY || kind == Kind.STRUCTURE) {
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            HolderSet<Structure> structures = id == null ? null : registry.get(id).map(HolderSet::direct).orElse(null);
            String alias = explicitId ? null : Names.STRUCTURES.get(name);
            if (alias != null) {
                structures = alias.startsWith("#")
                    ? registry.get(TagKey.create(Registries.STRUCTURE, Identifier.parse(alias.substring(1)))).orElse(null)
                    : registry.get(Identifier.parse(alias)).map(HolderSet::direct).orElse(null);
            }
            if (structures != null) {
                BlockPos pos = level.findNearestMapStructure(structures, origin, STRUCTURE_SEARCH_RINGS, false);
                return pos == null ? new Result("В пределах поиска не нашёл структуру «" + target + "».")
                    : new Result("Нашёл «%s». X = %d, Z = %d.".formatted(target, pos.getX(), pos.getZ()), pos, true);
            }
        }
        if (kind == Kind.ANY || kind == Kind.BIOME) {
            var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            var biome = id == null ? null : registry.get(id).orElse(null);
            Identifier alias = explicitId ? null : Names.BIOMES.get(name);
            if (biome == null && alias != null) biome = registry.get(alias).orElse(null);
            if (biome != null) {
                var result = level.findClosestBiome3d(biome::equals, origin, BIOME_RADIUS, 32, 64);
                return result == null ? new Result("В радиусе %d блоков не нашёл биом «%s».".formatted(BIOME_RADIUS, target))
                    : found(target, result.getFirst());
            }
        }
        return new Result("Уточни, что искать. Например, «найди деревню», «где алмазы?» или «найди minecraft:cherry_grove».");
    }

    private static BlockPos nearestBlock(ServerLevel level, BlockPos origin, Predicate<BlockState> matches) {
        int minX = origin.getX() - BLOCK_RADIUS;
        int maxX = origin.getX() + BLOCK_RADIUS;
        int minY = Math.max(level.getMinY(), origin.getY() - BLOCK_RADIUS);
        int maxY = Math.min(level.getMaxY(), origin.getY() + BLOCK_RADIUS);
        int minZ = origin.getZ() - BLOCK_RADIUS;
        int maxZ = origin.getZ() + BLOCK_RADIUS;
        var chunks = level.getChunkSource();
        double nearest = BLOCK_RADIUS * BLOCK_RADIUS + 1;
        BlockPos found = null;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                var chunk = chunks.getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                int x = chunkX << 4;
                int z = chunkZ << 4;
                var sections = chunk.getSections();
                for (int index = chunk.getSectionIndex(minY); index <= chunk.getSectionIndex(maxY); index++) {
                    var section = sections[index];
                    if (!section.maybeHas(matches)) continue;
                    int y = chunk.getSectionYFromSectionIndex(index) << 4;
                    for (BlockPos pos : BlockPos.betweenClosed(
                        Math.max(x, minX), Math.max(y, minY), Math.max(z, minZ),
                        Math.min(x + 15, maxX), Math.min(y + 15, maxY), Math.min(z + 15, maxZ))) {
                        double distance = origin.distSqr(pos);
                        if (distance < nearest
                            && matches.test(section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15))) {
                            if (distance == 0) return origin;
                            nearest = distance;
                            found = pos.immutable();
                        }
                    }
                }
            }
        }
        return found;
    }

    private static BlockPos nearestEntity(ServerLevel level, BlockPos origin, EntityType<?> type) {
        double nearest = ENTITY_RADIUS * ENTITY_RADIUS + 1;
        BlockPos found = null;
        // The guide uses the vanilla allay entity type.
        for (Entity entity : level.getEntities(type, new AABB(origin).inflate(ENTITY_RADIUS),
            candidate -> candidate.isAlive() && !(candidate instanceof Actors.Guide))) {
            BlockPos pos = entity.blockPosition();
            double distance = origin.distSqr(pos);
            if (distance < nearest) {
                nearest = distance;
                found = pos;
            }
        }
        return found;
    }

    private static Result found(String target, BlockPos pos) {
        return new Result("Нашёл «%s». X = %d, Y = %d, Z = %d.".formatted(target, pos.getX(), pos.getY(), pos.getZ()), pos);
    }

    private static final class Names {
        private static final Map<String, Block> BLOCKS = new HashMap<>();
        private static final Map<String, EntityType<?>> ENTITIES = new HashMap<>();
        private static final Map<String, Ore> ORES = new HashMap<>();
        private static final Map<String, Identifier> BIOMES = new HashMap<>();
        private static final Map<String, String> STRUCTURES = new HashMap<>();

        static {
            var items = readNames("item_names");
            var locations = readNames("location_names");
            for (Block block : BuiltInRegistries.BLOCK) {
                String key = block.getDescriptionId();
                add(BLOCKS, block, Language.getInstance().getOrDefault(key));
                var translated = items.getAsJsonArray(key);
                if (translated != null) add(BLOCKS, block, translated.get(0).getAsString(), translated.get(1).getAsString());
            }
            var entities = locations.getAsJsonObject("entities");
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                String key = type.getDescriptionId();
                add(ENTITIES, type, Language.getInstance().getOrDefault(key));
                var translated = entities.get(key);
                if (translated != null) add(ENTITIES, type, translated.getAsString());
            }
            locations.getAsJsonObject("biomes").asMap().forEach((id, translated) ->
                add(BIOMES, Identifier.parse(id), translated.getAsString()));
            locations.getAsJsonObject("structures").asMap().forEach((id, aliases) -> {
                for (var alias : aliases.getAsJsonArray()) add(STRUCTURES, id, alias.getAsString());
            });

            add(ORES, new Ore(List.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
                "Попробуй поискать около Y = −59."), "diamond", "diamond ore", "алмаз", "алмазная руда");
            add(ORES, new Ore(List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
                "Попробуй поискать около Y = 16 или в высоких горах."), "iron", "iron ore", "железо", "железная руда");
            add(ORES, new Ore(List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE), ""),
                "coal", "coal ore", "уголь", "угля", "угольная руда");
            add(ORES, new Ore(List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE), ""),
                "copper", "copper ore", "медь", "медная руда");
            add(ORES, new Ore(List.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE), ""),
                "gold", "gold ore", "золото", "золотая руда");
            add(ORES, new Ore(List.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE), ""),
                "redstone", "redstone ore", "редстоун", "редстоуновая руда");
            add(ORES, new Ore(List.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE), ""),
                "lapis", "lapis lazuli", "lapis ore", "лазурит", "лазуритовая руда");
            add(ORES, new Ore(List.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE), ""),
                "emerald", "emerald ore", "изумруд", "изумрудная руда");
        }

        private static <T> void add(Map<String, T> names, T value, String... aliases) {
            for (String alias : aliases) names.putIfAbsent(ResourceRequests.normalize(alias), value);
        }

        private static JsonObject readNames(String filename) {
            try (var stream = LocationRequests.class.getResourceAsStream("/data/echo_lure/" + filename + ".json")) {
                if (stream == null) throw new IllegalStateException("Missing " + filename);
                return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot load " + filename, failure);
            }
        }
    }
}
