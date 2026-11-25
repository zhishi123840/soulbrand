package com.example.soulbrand.registry;

import com.example.soulbrand.Soulbrand;
import com.example.soulbrand.content.SoulAltarBlock;
import com.example.soulbrand.content.SoulCleanserItem;
import com.example.soulbrand.content.SoulHeartItem;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 物品与方块注册（Fabric：直接 Registry.register，无 DeferredRegister）。
 */
public final class ModItems {

    /** 残魂碎晶：可叠 64（默认即 64）。 */
    public static final Item SOUL_FRAGMENT =
            new Item(new Item.Settings());

    /** 净魂粉：可叠 16。 */
    public static final Item SOUL_CLEANSER =
            new SoulCleanserItem(new Item.Settings().maxCount(16));

    /** 生命魂晶：右键使用，每颗永久 +2 点生命上限（1 颗心），累计封顶 +40 点（20 颗）。 */
    public static final Item LIFE_CRYSTAL =
            new SoulHeartItem(new Item.Settings().maxCount(16));

    /** 附魂台：带方块实体（只为驱动那本会动的书），玩法逻辑仍在方块与事件里。 */
    public static final Block SOUL_ALTAR =
            new SoulAltarBlock(Block.Settings.create()
                    .strength(3.5F, 6.0F)
                    .requiresTool()
                    .nonOpaque());

    private ModItems() {}

    public static void register() {
        regItem("soul_fragment", SOUL_FRAGMENT);
        regItem("soul_cleanser", SOUL_CLEANSER);
        regItem("life_crystal", LIFE_CRYSTAL);

        regBlock("soul_altar", SOUL_ALTAR);
        regItem("soul_altar", new BlockItem(SOUL_ALTAR, new Item.Settings()));
    }

    private static void regItem(String path, Item item) {
        Registry.register(Registries.ITEM, new Identifier(Soulbrand.MOD_ID, path), item);
    }

    private static void regBlock(String path, Block block) {
        Registry.register(Registries.BLOCK, new Identifier(Soulbrand.MOD_ID, path), block);
    }
}
