package com.example.soulbrand.mixin;

import com.example.soulbrand.logic.SoulArrowSoulHolder;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给所有「持久化投射物」（箭、三叉戟等）加一个魂值字段。
 *
 * <p><b>为什么挂在 PersistentProjectileEntity 而不是 Entity</b>：
 * 只有能命中造成伤害的投射物才需要这个字段；挂太上层会给每个实体都加一个
 * 永远用不到的 int。</p>
 *
 * <p><b>为什么用普通字段而不是 NBT</b>：见 {@link SoulArrowSoulHolder} 的说明 ——
 * 这是纯服务端的瞬时数据，存盘与同步都是浪费。</p>
 *
 * <p><b>{@code @Unique} 必须加</b>：mixin 给目标类注入的成员名要加前缀避免冲突，
 * 同时告诉 Mixin 这是本 mixin 独有的字段而不是要覆盖目标类的同名字段。</p>
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class ArrowSoulFieldMixin implements SoulArrowSoulHolder {

    @Unique
    private int soulbrand$arrowSoul = 0;

    @Override
    public int soulbrand$getArrowSoul() {
        return soulbrand$arrowSoul;
    }

    @Override
    public void soulbrand$setArrowSoul(int soul) {
        soulbrand$arrowSoul = soul;
    }
}
