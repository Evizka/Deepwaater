package com.evizka.deepwaater.item;

import com.evizka.deepwaater.DeepWaater;
import com.evizka.deepwaater.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block items and the creative tab of the mod. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DeepWaater.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DeepWaater.MOD_ID);

    public static final DeferredItem<BlockItem> DEEP_BOG_LOG_ITEM = ITEMS.registerSimpleBlockItem("deep_bog_log", ModBlocks.DEEP_BOG_LOG);
    public static final DeferredItem<BlockItem> DEEP_BOG_LEAVES_ITEM = ITEMS.registerSimpleBlockItem("deep_bog_leaves", ModBlocks.DEEP_BOG_LEAVES);
    public static final DeferredItem<BlockItem> DEEP_BOG_ROOTS_ITEM = ITEMS.registerSimpleBlockItem("deep_bog_roots", ModBlocks.DEEP_BOG_ROOTS);
    public static final DeferredItem<BlockItem> BOG_MUD_ITEM = ITEMS.registerSimpleBlockItem("bog_mud", ModBlocks.BOG_MUD);
    public static final DeferredItem<BlockItem> BOG_MOSS_ITEM = ITEMS.registerSimpleBlockItem("bog_moss", ModBlocks.BOG_MOSS);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEEPWATER_TAB = CREATIVE_TABS.register(
            "deepwaater",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.deepwaater"))
                    .icon(() -> DEEP_BOG_LOG_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(DEEP_BOG_LOG_ITEM.get());
                        output.accept(DEEP_BOG_LEAVES_ITEM.get());
                        output.accept(DEEP_BOG_ROOTS_ITEM.get());
                        output.accept(BOG_MUD_ITEM.get());
                        output.accept(BOG_MOSS_ITEM.get());
                    })
                    .build());

    private ModItems() {
    }
}
