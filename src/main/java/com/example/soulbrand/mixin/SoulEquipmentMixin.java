package com.example.soulbrand.mixin;

import com.example.soulbrand.event.SoulEvents;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 换装备 → 刷新护甲加成。
 *
 * <p>Fabric 没有 Forge 的 {@code LivingEquipmentChangeEvent}，钉在
 * {@code LivingEntity#onEquipStack} 的 TAIL —— 这是「某件装备换上了/脱下了」在 Fabric 上
 * 唯一稳定的观测点。</p>
 *
 * <p>这个方法对<b>所有</b> LivingEntity 都会触发（僵尸也会换装备），所以先判
 * {@code instanceof PlayerEntity} 再走，避免给怪物算属性。</p>
 */
@Mixin(LivingEntity.class)
public abstract class SoulEquipmentMixin {

    @Inject(
            method = "onEquipStack(Lnet/minecraft/entity/EquipmentSlot;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V",
            at = @At("TAIL")
    )
    private void soulbrand$syncOnEquip(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack, CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) {
            SoulEvents.onEquipmentChanged(player);
        }
    }
}
