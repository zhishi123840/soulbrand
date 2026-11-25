package com.example.soulbrand.mixin;

import com.example.soulbrand.client.SoulAltarScreen;
import com.example.soulbrand.content.SoulAltarScreenHandler;
import com.example.soulbrand.registry.ModScreenHandlers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把附魂台界面接进客户端。
 *
 * <p><b>为什么不用正规注册途径</b>：1.20.1 的 {@code HandledScreens.register} 是 private，
 * 而 Fabric 的 fabric-screen-api-v1 并不提供注册入口（{@code Screens} 类只有
 * getButtons / getTextRenderer / getClient）。{@code HandledScreens.Provider} 接口本身
 * 还是 package-private，连 {@code @Invoker} 都没法把它的类型写进签名。</p>
 *
 * <p>所以改成拦截 {@code open}：{@code ScreenHandlerType#create} 是 public 的，
 * 我们自己造 handler、自己 setScreen，然后取消原版查找 —— 不依赖任何私有成员。</p>
 *
 * <p>只注入我们自己的 type，其它 mod 与原版的界面走原本的路径。</p>
 */
@Mixin(HandledScreens.class)
public abstract class HandledScreensOpenMixin {

    @Inject(
            method = "open(Lnet/minecraft/screen/ScreenHandlerType;Lnet/minecraft/client/MinecraftClient;ILnet/minecraft/text/Text;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void soulbrand$openAltar(ScreenHandlerType<?> type, MinecraftClient client,
                                           int syncId, Text title, CallbackInfo ci) {
        if (type != ModScreenHandlers.SOUL_ALTAR || client.player == null) {
            return;
        }
        ScreenHandler handler = type.create(syncId, client.player.getInventory());
        if (handler instanceof SoulAltarScreenHandler altarHandler) {
            SoulAltarScreen screen = new SoulAltarScreen(
                    altarHandler, client.player.getInventory(), title);
            // 必须设 currentScreenHandler —— 反编译原版 Provider.open 确认它有这一步
            // （create handler → create screen → player.currentScreenHandler = handler → setScreen）。
            // 漏掉的话客户端点击槽位发的还是旧容器的 syncId，服务端全部拒绝 ——
            // 表现就是「界面能打开但物品拖不动」。
            client.player.currentScreenHandler = altarHandler;
            client.setScreen(screen);
            ci.cancel();
        }
    }
}
