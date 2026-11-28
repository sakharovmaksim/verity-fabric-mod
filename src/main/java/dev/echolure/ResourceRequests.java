package dev.echolure;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;

final class ResourceRequests {
    static final int COOLDOWN = 30;
    private static final Pattern REQUEST = Pattern.compile("^(?:(?:пожалуйста|please)[, ]+)?(?:"
        + "(?:(?:ты )?можешь(?: ли ты)? |мог бы ты |не мог бы ты )?"
        + "(?:дай|дать|подари|подарить|выдай|выдать|принеси|принести|достань|достать)"
        + "|мне (?:нужен|нужна|нужно|нужны)|я хочу|хочу"
        + "|(?:can you |could you )?(?:give|bring|get)(?: me)?)(?:[, ]+)(.+)$");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d_:])(-?\\d+)(?![\\p{L}\\d_:])");
    private static final Set<String> FILLER = Set.of("мне", "пожалуйста", "плиз", "штук", "штуки", "штуку", "шт",
        "немного", "немножко", "some", "please", "a", "an", "the", "of");
    private static final Set<String> CHAT = Set.of("совет", "подсказку", "историю", "имя", "ответ", "пример",
        "вопрос", "объяснение", "анекдот", "план", "инструкцию", "advice", "hint", "story", "name", "answer");
    private static final Map<String, String> QUANTITIES = Map.ofEntries(
        Map.entry("один", "1"), Map.entry("одну", "1"), Map.entry("одно", "1"), Map.entry("одна", "1"),
        Map.entry("два", "2"), Map.entry("две", "2"), Map.entry("три", "3"), Map.entry("четыре", "4"),
        Map.entry("пять", "5"), Map.entry("шесть", "6"), Map.entry("семь", "7"), Map.entry("восемь", "8"),
        Map.entry("девять", "9"), Map.entry("десять", "10"), Map.entry("шестнадцать", "16"),
        Map.entry("полстака", "32"), Map.entry("стак", "64"), Map.entry("stack", "64"));
    private static final Map<String, String> WORD_FORMS = Map.ofEntries(
        Map.entry("досок", "доски"), Map.entry("бревен", "бревно"), Map.entry("камня", "камень"),
        Map.entry("угля", "уголь"), Map.entry("углей", "уголь"), Map.entry("слитков", "слиток"),
        Map.entry("слитки", "слиток"), Map.entry("кусков", "кусок"), Map.entry("самородков", "самородок"),
        Map.entry("ведер", "ведро"), Map.entry("яиц", "яйцо"), Map.entry("палок", "палка"),
        Map.entry("пузырьков", "пузырек"), Map.entry("семян", "семена"), Map.entry("пыли", "пыль"));
    private static final Map<String, Item> NAMES = new HashMap<>();
    private static final Map<Item, String> LABELS = new HashMap<>();
    private static final Set<String> AMBIGUOUS = new HashSet<>();

    record Request(Item item, int count, boolean limited, String error) {}

    static {
        try (var stream = ResourceRequests.class.getResourceAsStream("/data/echo_lure/item_names.json")) {
            if (stream == null) throw new IllegalStateException("Missing item names");
            var translations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                var id = BuiltInRegistries.ITEM.getKey(item);
                addName(id.getPath().replace('_', ' '), item);
                var translated = translations.getAsJsonArray(item.getDescriptionId());
                if (translated == null) continue;
                LABELS.put(item, translated.get(0).getAsString());
                for (var name : translated) addName(name.getAsString(), item);
                if (item instanceof SmithingTemplateItem) {
                    var title = translations.getAsJsonArray("item." + id.getNamespace() + "." + id.getPath() + ".new");
                    if (title != null) addTemplateNames(item, title.get(0).getAsString(), title.get(1).getAsString());
                }
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot load item names", failure);
        }
        alias(Items.IRON_INGOT, "железо", "железные слитки");
        alias(Items.IRON_INGOT, "слитки железа");
        alias(Items.GOLD_INGOT, "золото", "золотые слитки");
        alias(Items.COPPER_INGOT, "медь", "медные слитки");
        alias(Items.OAK_LOG, "дерево", "древесина", "wood");
        alias(Items.OAK_LOG, "бревен", "дубовых бревен");
        alias(Items.OAK_PLANKS, "доски", "planks");
        alias(Items.COBBLESTONE, "булыга");
        alias(Items.STONE, "камня");
        alias(Items.BREAD, "еда", "food");
        alias(Items.COAL, "угля", "углей");
        alias(Items.DIAMOND_CHESTPLATE, "алмазная броня");
        alias(Items.IRON_CHESTPLATE, "броня", "железная броня");
        alias(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "шаблон для незерита", "незеритовый шаблон",
            "шаблон для незеритового улучшения");
    }

    private ResourceRequests() {}

    static Request parse(String question) {
        String query = question.strip().toLowerCase(Locale.ROOT).replace('ё', 'е');
        var match = REQUEST.matcher(query);
        if (!match.matches()) return null;
        String body = match.group(1).replaceAll("[!?.,;]", " ").strip();
        if (body.startsWith("чтобы ") || body.equals("чтобы")) return null;
        var words = body.split("\\s+");
        if (Arrays.stream(words).anyMatch(CHAT::contains)) return null;
        StringBuilder cleaned = new StringBuilder();
        for (String word : words) {
            if (!FILLER.contains(word)) cleaned.append(QUANTITIES.getOrDefault(word, word)).append(' ');
        }
        body = cleaned.toString().strip();
        var numbers = NUMBER.matcher(body);
        long requested = 1;
        if (numbers.find()) {
            String number = numbers.group(1);
            if (number.startsWith("-") || number.matches("0+")) return error("Попроси положительное количество предметов.");
            requested = number.length() > 6 ? Long.MAX_VALUE : Long.parseLong(number);
            if (numbers.find()) return error("Попроси один вид предметов и одно количество за раз.");
            body = NUMBER.matcher(body).replaceAll(" ").strip();
        }
        // Item IDs are resolved literally; requests never execute commands or accept item data.
        Item item = null;
        Identifier id = Identifier.tryParse(body.replace(' ', '_'));
        if (id != null) item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            String name = normalize(body);
            if (!AMBIGUOUS.contains(name)) item = NAMES.get(name);
        }
        if (item == PocketGuide.ITEM) return error("Чтобы убрать меня в инвентарь, подойди и нажми Shift + ПКМ.");
        if (item == null && body.matches(".*(?:шаблон|\\btemplate).*")) return error(
            "Какой шаблон? Например, «дай шаблон Тишина» или «дай шаблон для незерита». Можно указать ID предмета.");
        if (item == null || item == Items.AIR) return error(
            "Уточни предмет: например, «дай 8 алмазов», «подари алмазную кирку» или «дай minecraft:oak_log». Можно использовать русское или английское название.");
        int maximum = new ItemStack(item).getMaxStackSize();
        return new Request(item, (int) Math.min(requested, maximum), requested > maximum, null);
    }

    static String grant(ServerPlayer player, Request request) {
        var stack = new ItemStack(request.item(), request.count());
        int capacity = 0;
        for (var existing : player.getInventory().getNonEquipmentItems()) {
            if (existing.isEmpty()) capacity += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                capacity += Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
        }
        if (capacity < request.count()) return null;
        player.getInventory().add(stack);
        player.inventoryMenu.broadcastChanges();
        String name = LABELS.getOrDefault(request.item(), BuiltInRegistries.ITEM.getKey(request.item()).toString());
        return "Держи: " + name + " × " + request.count() + "."
            + (request.limited() ? " За одну просьбу даю не больше одной стопки этого предмета." : "");
    }

    private static Request error(String message) { return new Request(null, 0, false, message); }

    private static void addName(String name, Item item) {
        String key = normalize(name);
        Item previous = NAMES.putIfAbsent(key, item);
        if (previous != null && previous != item) AMBIGUOUS.add(key);
    }

    private static void addTemplateNames(Item item, String russian, String english) {
        LABELS.put(item, russian);
        addName(russian, item);
        addName(english, item);
        String pattern = russian.replaceFirst("^Отделка ", "");
        for (String prefix : new String[]{"шаблон ", "кузнечный шаблон "}) {
            addName(prefix + russian, item);
            addName(prefix + pattern, item);
        }
        addName(english + " smithing template", item);
        addName(english + " template", item);
        if (english.endsWith(" Armor Trim")) {
            String trim = english.substring(0, english.length() - " Armor Trim".length());
            addName(trim + " trim", item);
            addName(trim + " template", item);
            addName(trim + " smithing template", item);
            addName("шаблон отделки брони " + pattern, item);
        }
    }

    private static void alias(Item item, String... aliases) {
        for (String alias : aliases) {
            String key = normalize(alias);
            NAMES.put(key, item);
            AMBIGUOUS.remove(key);
        }
    }

    static String normalize(String name) {
        return Arrays.stream(name.toLowerCase(Locale.ROOT).replace('ё', 'е').replace('_', ' ')
            .split("[^\\p{L}\\d]+"))
            .filter(word -> !word.isEmpty() && !FILLER.contains(word)).map(ResourceRequests::stem).sorted()
            .collect(java.util.stream.Collectors.joining(" "));
    }

    private static String stem(String word) {
        word = WORD_FORMS.getOrDefault(word, word);
        if (!word.matches("[а-я]+")) return word.endsWith("s") && word.length() > 3
            ? word.substring(0, word.length() - 1) : word;
        for (String ending : new String[]{"иями", "ами", "ями", "ого", "ему", "ому", "ыми", "ими",
            "ая", "яя", "ое", "ее", "ые", "ие", "ой", "ый", "ий", "ую", "юю", "ых", "их", "ов", "ев",
            "ей", "ам", "ям", "ах", "ях", "ом", "ем", "а", "я", "ы", "и", "у", "ю", "е", "о"}) {
            if (word.endsWith(ending) && word.length() - ending.length() >= 3) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        return word;
    }
}
