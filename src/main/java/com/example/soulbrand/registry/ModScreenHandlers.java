package com.example.soulbrand.registry;

import com.example.soulbrand.Soulbrand;
import com.example.soulbrand.content.SoulAltarScreenHandler;

import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * 附魂台界面类型。
 *
 * <p>客户端 factory 每次给一个新的 {@code SimpleInventory(1)} —— 槽位内容由服务端
 * 通过标准 slot 同步推送过来，客户端这份只是占位，保证两边 slots 数量一致。</p>
 */
public final class ModScreenHandlers {

    public static final ScreenHandlerType<SoulAltarScreenHandler> SOUL_ALTAR =
            ScreenHandlerRegistry.registerSimple(
                    new Identifier(Soulbrand.MOD_ID, "soul_altar"),
                    (syncId, playerInventory) ->
                            new SoulAltarScreenHandler(syncId, playerInventory, new SimpleInventory(1)));

    private ModScreenHandlers() {}
}
