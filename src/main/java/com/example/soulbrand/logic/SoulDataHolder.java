package com.example.soulbrand.logic;

import net.minecraft.nbt.NbtCompound;

/**
 * Duck interface：给 {@code PlayerEntity} 挂一个持久 NBT 槽。
 *
 * <p>Forge 有 {@code IForgeEntity#getPersistentData()}，Fabric 没有等价物。
 * Fabric 上的标准做法是自己开一个接口，用 Mixin 把它实现在 PlayerEntity 上，
 * 并在 {@code writeCustomDataToNbt / readCustomDataFromNbt} 里跟着存档走。</p>
 */
public interface SoulDataHolder {

    /** 拿本模组的持久 compound；没有就建一个空的（不落盘，直到真的写了东西）。 */
    NbtCompound soulbrand$getPersistentData();
}
