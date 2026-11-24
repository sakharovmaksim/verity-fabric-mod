package dev.echolure;

import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

final class PocketGuide extends Item {
    private static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM,
        Identifier.fromNamespaceAndPath("echo_lure", "pocket_guide"));
    static final Item ITEM = new PocketGuide();
    private static BiPredicate<ServerPlayer, ItemStack> release;

    private PocketGuide() { super(new Item.Properties().setId(KEY).stacksTo(1).fireResistant()); }

    static void register(BiPredicate<ServerPlayer, ItemStack> action) {
        Registry.register(BuiltInRegistries.ITEM, KEY, ITEM);
        release = action;
    }

    static ItemStack create(ServerPlayer owner, String token) {
        var stack = new ItemStack(ITEM);
        var tag = new CompoundTag();
        tag.putString("owner", owner.getUUID().toString());
        tag.putString("token", token);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.translatable("item.echo_lure.pocket_guide.release"),
            Component.literal(owner.getName().getString()))));
        return stack;
    }

    static boolean matches(ItemStack stack, ServerPlayer owner, String token) {
        if (!stack.is(ITEM) || token.isEmpty()) return false;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var tag = data.copyTag();
        return tag.getStringOr("owner", "").equals(owner.getUUID().toString())
            && tag.getStringOr("token", "").equals(token);
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            return release.test(serverPlayer, player.getItemInHand(hand)) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        return use(context.getLevel(), context.getPlayer(), context.getHand());
    }
}
