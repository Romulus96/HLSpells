package com.divinity.hlspells.setup.client;

import com.divinity.hlspells.HLSpells;
import com.divinity.hlspells.init.EntityInit;
import com.divinity.hlspells.init.ItemInit;
import com.divinity.hlspells.init.SpellInit;
import com.divinity.hlspells.items.SpellHoldingItem;
import com.divinity.hlspells.items.capabilities.spellholdercap.SpellHolderProvider;
import com.divinity.hlspells.items.capabilities.totemcap.ITotemCap;
import com.divinity.hlspells.items.capabilities.totemcap.TotemItemProvider;
import com.divinity.hlspells.network.NetworkManager;
import com.divinity.hlspells.network.packets.WandInputPacket;
import com.divinity.hlspells.renderers.BaseBoltRenderer;
import com.divinity.hlspells.renderers.StormBoltRenderer;
import com.divinity.hlspells.spell.Spell;
import com.divinity.hlspells.util.SpellUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemModelsProperties;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = HLSpells.MODID, value = Dist.CLIENT)
public class ClientSetup {
    public static final KeyBinding WAND_BINDING = new KeyBinding("Wand Cycle", KeyConflictContext.UNIVERSAL, InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_G, "HLSpells");
    static boolean buttonPressedFlag;

    public static void init(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerItemModel(ItemInit.SPELL_BOOK.get(), new ResourceLocation("using"), 3, 0.2F, 0.4F, 0.6F, 0.8F, 1F);
            registerItemModel(ItemInit.WAND.get(), new ResourceLocation("pull"), 3, 0.2F, 0.4F, 0.6F, 0.8F, 1F);
            registerItemModel(ItemInit.STAFF.get(), new ResourceLocation("pull"), 3, 0.2F, 0.4F, 0.6F, 0.8F, 1F);
        });
        ItemModelsProperties.register(ItemInit.TOTEM_OF_RETURNING.get(), new ResourceLocation("used"), (stack, world, living) -> {
            if (living instanceof PlayerEntity) {
                LazyOptional<ITotemCap> totemCap = stack.getCapability(TotemItemProvider.TOTEM_CAP);
                if (totemCap.isPresent()) {
                    return totemCap.map(ITotemCap::getHasDied).orElse(false) ? 1 : 0;
                }
            }
            return 0;
        });
        ClientRegistry.registerKeyBinding(WAND_BINDING);
        RenderingRegistry.registerEntityRenderingHandler(EntityInit.INVISIBLE_TARGETING_ENTITY.get(), StormBoltRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityInit.PIERCING_BOLT_ENTITY.get(), manager -> new BaseBoltRenderer<>(manager, new ResourceLocation(HLSpells.MODID, "textures/entity/bolt/green_bolt.png")));
        RenderingRegistry.registerEntityRenderingHandler(EntityInit.FLAMING_BOLT_ENTITY.get(), manager -> new BaseBoltRenderer<>(manager, new ResourceLocation(HLSpells.MODID, "textures/entity/bolt/orange_bolt.png")));
        RenderingRegistry.registerEntityRenderingHandler(EntityInit.AQUA_BOLT_ENTITY.get(), manager -> new BaseBoltRenderer<>(manager, new ResourceLocation(HLSpells.MODID, "textures/entity/bolt/blue_bolt.png")));
        RenderingRegistry.registerEntityRenderingHandler(EntityInit.SUMMONED_VEX_ENTITY.get(), VexRenderer::new);
    }

    /**
     * Registers the model of a given item
     * @param item The item to register the model of
     * @param location The resource location of the model predicate
     * @param useItemRemainTickOffset The tick offset at which the item will change models at
     * @param values The values to return for each model
     */
    private static void registerItemModel(Item item, ResourceLocation location, int useItemRemainTickOffset, float... values) {
        ItemModelsProperties.register(item, location, (stack, world, living) -> {
            if (living instanceof PlayerEntity && living.isUsingItem() && living.getUseItem() == stack) {
                int useDuration = item.getUseDuration(item.getDefaultInstance());
                int minUseAmount = useDuration - (useItemRemainTickOffset * (values.length - 1));
                for (int i = 0; i < values.length; i++) {
                    if ((double) living.getUseItemRemainingTicks() < minUseAmount) return values[values.length - 1];
                    else if ((double) living.getUseItemRemainingTicks() < useDuration && (double) living.getUseItemRemainingTicks() >= (useDuration - (useItemRemainTickOffset * (i == 0 ? 1 : i)))) {
                        return values[i];
                    }
                }
            }
            return 0;
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (WAND_BINDING.isDown() && !buttonPressedFlag) {
                if (player != null && !player.isUsingItem()) {
                    NetworkManager.INSTANCE.sendToServer(new WandInputPacket(WAND_BINDING.getKey().getValue()));
                    ItemStack stack = ItemStack.EMPTY;
                    ItemStack mainHand = player.getMainHandItem();
                    ItemStack offHand = player.getOffhandItem();
                    boolean mainHandWand = mainHand.getItem() instanceof SpellHoldingItem && ((SpellHoldingItem) mainHand.getItem()).isWand();
                    boolean offHandWand = offHand.getItem() instanceof SpellHoldingItem && ((SpellHoldingItem) offHand.getItem()).isWand();
                    if (mainHandWand)
                        stack = mainHand;
                    else if (offHandWand)
                        stack = offHand;
                    if (!stack.isEmpty()) {
                        stack.getCapability(SpellHolderProvider.SPELL_HOLDER_CAP, null).ifPresent(cap ->
                        {
                            if (!cap.getSpells().isEmpty()) {
                                cap.setCurrentSpellCycle(cap.getCurrentSpellCycle() + 1);
                                Spell spell = SpellUtils.getSpellByID(cap.getCurrentSpell());
                                player.displayClientMessage(new StringTextComponent("Spell : " + spell.getTrueDisplayName()).withStyle(TextFormatting.GOLD), true);
                            }
                        });
                    }
                }
                buttonPressedFlag = true;
            }
            if (!WAND_BINDING.isDown() && buttonPressedFlag) {
                buttonPressedFlag = false;
            }
        }
    }

    /**
     * When a spell holding item is used it stops the slowness effect
     */
    @SubscribeEvent
    @SuppressWarnings("ConstantConditions")
    public static void onInput(InputUpdateEvent event) {
        ClientPlayerEntity player = (ClientPlayerEntity) event.getPlayer();
        Hand hand = player.getUsedItemHand();
        // Don't remove this even if it complaints it can't be null it can be null
        if (hand != null) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isUsingItem() && !player.isPassenger() && stack.getItem() instanceof SpellHoldingItem) {
                Spell spell = SpellUtils.getSpell(stack);
                if (spell == SpellInit.SPEED.get() || spell == SpellInit.FROST_PATH.get()) {
                    player.input.leftImpulse /= 0.2F;
                    player.input.forwardImpulse /= 0.2F;
                }
            }
        }
    }

    public static void displayActivation(PlayerEntity playerEntity, ItemStack stack, boolean particleIn) {
        Minecraft.getInstance().gameRenderer.displayItemActivation(stack);
        if (particleIn) {
            Minecraft.getInstance().particleEngine.createTrackingEmitter(playerEntity, ParticleTypes.TOTEM_OF_UNDYING, 30);
        }
    }
}