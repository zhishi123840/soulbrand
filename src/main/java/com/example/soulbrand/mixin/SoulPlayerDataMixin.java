package com.example.soulbrand.mixin;

import com.example.soulbrand.logic.SoulDataHolder;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家持久 NBT 的落地实现。
 *
 * <p>三个动作：</p>
 * <ul>
 *   <li>{@code @Unique} 一个 NbtCompound 字段（不进原版字段表）；</li>
 *   <li>{@code readCustomDataFromNbt} 的 TAIL：把存档里的 compound 读回来；</li>
 *   <li>{@code writeCustomDataToNbt} 的 TAIL：把 compound 写进玩家存档。</li>
 * </ul>
 * <p>只挂 PlayerEntity，不带客户端类，放服务端 mixin 配置即可。</p>
 */
@Mixin(PlayerEntity.class)
public abstract class SoulPlayerDataMixin implements SoulDataHolder {

    @Unique
    private static final String SOULBRAND_KEY = "SoulbrandPlayer";

    @Unique
    private NbtCompound soulbrand$data = new NbtCompound();

    @Override
    public NbtCompound soulbrand$getPersistentData() {
        return this.soulbrand$data;
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void soulbrand$read(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(SOULBRAND_KEY, 10)) {
            this.soulbrand$data = nbt.getCompound(SOULBRAND_KEY);
        }
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void soulbrand$write(NbtCompound nbt, CallbackInfo ci) {
        if (!this.soulbrand$data.isEmpty()) {
            nbt.put(SOULBRAND_KEY, this.soulbrand$data);
        }
    }
}
