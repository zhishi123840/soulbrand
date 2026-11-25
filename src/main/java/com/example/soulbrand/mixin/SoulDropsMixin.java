package com.example.soulbrand.mixin;

import com.example.soulbrand.event.SoulEvents;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 碎晶掉落：Fabric 没有 Forge 的 {@code LivingDropsEvent}。
 *
 * <p>钉在 {@code LivingEntity#dropLoot} 的 HEAD：此时该实体已经死亡，
 * 原版战利品表还没生成，本方法在里面往世界里 spawn 自己的掉落物即可。</p>
 */
@Mixin(LivingEntity.class)
public abstract class SoulDropsMixin {

    @Inject(
            method = "dropLoot(Lnet/minecraft/entity/damage/DamageSource;Z)V",
            at = @At("HEAD")
    )
    private void soulbrand$dropFragments(DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        SoulEvents.onLoot((LivingEntity) (Object) this, source, causedByPlayer);
    }
}
