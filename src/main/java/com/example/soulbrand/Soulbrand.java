package com.example.soulbrand;

import com.example.soulbrand.registry.ModBlockEntities;
import com.example.soulbrand.registry.ModEnchantments;
import com.example.soulbrand.registry.ModItemGroups;
import com.example.soulbrand.registry.ModItems;
import com.example.soulbrand.registry.ModScreenHandlers;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

/**
 * 附魂（Soulbrand）—— Fabric 版。
 *
 * <p>装备越杀越强：在附魂台界面里刻下附魂，之后每次击杀涨魂值，
 * 魂值每满 100 升一级，等级越高攻击力、破甲与防护力越强（无上限）。</p>
 *
 * <p>注册顺序有依赖：ModItems 必须最先（方块/物品/BE 类型都引用它的字段），
 * ModBlockEntities 紧随其后（它的静态字段构造时读 ModItems.SOUL_ALTAR）。</p>
 */
public class Soulbrand implements ModInitializer {

    public static final String MOD_ID = "soulbrand";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        SoulConfig.load();

        ModItems.register();
        ModBlockEntities.register();
        ModEnchantments.register();
        ModItemGroups.register();

        // ScreenHandlerType 在 Fabric 的 registerSimple 里当场注册进注册表，
        // 这里只是触发 ModScreenHandlers 的类初始化。
        net.minecraft.screen.ScreenHandlerType<?> ignored = ModScreenHandlers.SOUL_ALTAR;

        com.example.soulbrand.event.SoulEvents.register();

        LOGGER.info("[{}] loaded (Fabric)", MOD_ID);
    }
}
