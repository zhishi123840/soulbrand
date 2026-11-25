package com.example.soulbrand.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ForgingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link ForgingScreenHandler} 的 protected 字段。
 *
 * <p><b>为什么需要它</b>：{@code input} / {@code output} / {@code player} 这三个字段
 * 定义在<b>父类</b> {@code ForgingScreenHandler} 上，不是 {@code AnvilScreenHandler} 自己的。
 * Mixin 的 {@code @Shadow} 只能在<b>目标类自身</b>找字段，对继承来的会报
 * {@code @Shadow field input was not located in the target class}（实测踩到，启动期直接崩）。</p>
 *
 * <p>而把 mixin 挂到父类上也不可行 —— {@code updateResult} 与 {@code onTakeOutput}
 * 在父类是 abstract，注入父类会影响所有 17 个锻造类容器（铁砧/砂轮/织布/锻造台…）。</p>
 *
 * <p>所以单独开一个 accessor 接口，让 anvil 专用 mixin 通过它取字段。</p>
 */
@Mixin(ForgingScreenHandler.class)
public interface ForgingScreenHandlerAccessor {

    @Accessor("input")
    Inventory soulbrand$getInput();

    @Accessor("output")
    CraftingResultInventory soulbrand$getOutput();

    @Accessor("player")
    PlayerEntity soulbrand$getPlayer();

    /**
     * 输出槽在 slots 列表里的下标。
     *
     * <p>原版是 public 方法，但 mixin 目标不是父类时调不到，
     * 这里用 accessor 暴露成接口方法给父类 mixin 用。</p>
     */
    @org.spongepowered.asm.mixin.gen.Invoker("getResultSlotIndex")
    int soulbrand$getResultSlotIndex();
}