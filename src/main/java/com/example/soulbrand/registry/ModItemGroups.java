package com.example.soulbrand.registry;

import com.example.soulbrand.Soulbrand;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 创造模式物品栏。
 *
 * <p>没有这一步，三个物品只存在于注册表里，玩家在创造模式物品栏里翻不到 ——
 * 只能靠 {@code /give} 拿。这既是自测时的不便，也是发布后被抱怨的第一件事。</p>
 *
 * <p>注意 {@code ItemGroupEvents.modifyEntriesEvent} 收的是 {@link RegistryKey} 而不是
 * {@link ItemGroup} 实例，所以这里先把 key 存下来再注册。</p>
 */
public final class ModItemGroups {

    public static final RegistryKey<ItemGroup> SOULBRAND_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP,
                    new Identifier(Soulbrand.MOD_ID, "main"));

    private ModItemGroups() {}

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, SOULBRAND_KEY,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModItems.SOUL_FRAGMENT))
                        .displayName(Text.translatable("itemGroup.soulbrand.main"))
                        .build());

        ItemGroupEvents.modifyEntriesEvent(SOULBRAND_KEY).register(entries -> {
            entries.add(ModItems.SOUL_FRAGMENT);
            entries.add(ModItems.SOUL_CLEANSER);
            entries.add(ModItems.LIFE_CRYSTAL);
            entries.add(ModItems.SOUL_ALTAR);
        });
    }
}
