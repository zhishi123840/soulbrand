package com.example.soulbrand.registry;

import com.example.soulbrand.Soulbrand;
import com.example.soulbrand.content.SoulAltarBlockEntity;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * 附魂台方块实体类型。
 *
 * <p>datafixer 类型必须给：原版 {@code BlockEntityType.create} 内部用
 * {@code Util.getChoiceType(TypeReferences.BLOCK_ENTITY, id)}，缺了它存档读写会报
 * missing datafixer type。</p>
 */
public final class ModBlockEntities {

    public static final BlockEntityType<SoulAltarBlockEntity> SOUL_ALTAR =
            BlockEntityType.Builder
                    .create(SoulAltarBlockEntity::new, ModItems.SOUL_ALTAR)
                    .build(Util.getChoiceType(TypeReferences.BLOCK_ENTITY, "soul_altar"));

    private ModBlockEntities() {}

    public static void register() {
        Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(Soulbrand.MOD_ID, "soul_altar"), SOUL_ALTAR);
    }
}
