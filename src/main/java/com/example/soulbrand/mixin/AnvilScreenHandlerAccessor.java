package com.example.soulbrand.mixin;

import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link AnvilScreenHandler} 的 private {@code levelCost} 属性。
 *
 * <p><b>为什么需要它</b>：原版铁砧的 {@code levelCost} 决定「取走输出物要花多少经验」，
 * 也是 {@code canTakeOutput} 的判定依据。我们接管注魂流程后，必须保证它为 0 ——
 * 否则玩家会被要求付经验等级，而我们的设计是「注魂不额外收费」。</p>
 *
 * <p>字段是 private，但 {@code @Accessor} 可以直接访问（Mixin 的 accessor 会生成
 * 访问器方法，绕过 Java 可见性限制）。</p>
 */
@Mixin(AnvilScreenHandler.class)
public interface AnvilScreenHandlerAccessor {

    @Accessor("levelCost")
    Property soulbrand$getLevelCost();
}
