package com.example.soulbrand.content;

import com.example.soulbrand.registry.ModBlockEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 附魂台的方块实体：GUI 的容器锚点（1 格，放待处理的装备）。
 *
 * <p><b>为什么不继承原版 {@code EnchantingTableBlockEntity}</b>：它的构造器把 type 硬编码成
 * {@code BlockEntityType.ENCHANTING_TABLE}，存档里 BE 的 id 会写成 {@code minecraft:enchanting_table}，
 * 重载世界后原版工厂会造出原版 BE，我们的类被替换、界面也打不开。所以直接继承 BlockEntity。</p>
 *
 * <p>不存玩法数据：魂值永远在物品自己的 NBT 里，这里只是「装备当前放在台面上」的临时载体。</p>
 */
public class SoulAltarBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {

    /** 单格容器。canPlayerUse 交给界面生命周期管，不做距离校验。 */
    public final Inventory inventory = new SimpleInventory(1) {
        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }
    };

    public SoulAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOUL_ALTAR, pos, state);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtList items = new NbtList();
        ItemStack stack = inventory.getStack(0);
        if (!stack.isEmpty()) {
            items.add(stack.writeNbt(new NbtCompound()));
        }
        nbt.put("Items", items);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        inventory.clear();
        NbtList items = nbt.getList("Items", NbtElement.COMPOUND_TYPE);
        if (!items.isEmpty()) {
            inventory.setStack(0, ItemStack.fromNbt(items.getCompound(0)));
        }
    }

    // 掉落不写在这里（BlockEntity 没有 onRemove 钩子）。
    // 装备在台面上被砸时吐物品，由 SoulAltarBlock#onStateReplaced 调
    // Inventory.dropStacks(state, world, pos, be) 处理 —— 原版容器同款路径。

    /**
     * 接口要求的方法名是 {@code createMenu}（来自 {@code ScreenHandlerFactory}），
     * 参数顺序是 (syncId, playerInventory, player) —— 后者是 BlockEntity 上另一个无关的重名方法。
     */
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new SoulAltarScreenHandler(syncId, playerInventory, inventory, world, pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.soulbrand.soul_altar");
    }
}
