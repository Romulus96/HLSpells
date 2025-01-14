package com.divinity.hlspells.events.entity;

import com.divinity.hlspells.HLSpells;
import com.divinity.hlspells.compat.CuriosCompat;
import com.divinity.hlspells.init.EnchantmentInit;
import com.divinity.hlspells.init.ItemInit;
import com.divinity.hlspells.items.ModTotemItem;
import com.divinity.hlspells.items.capabilities.totemcap.ITotemCap;
import com.divinity.hlspells.items.capabilities.totemcap.TotemItemProvider;
import com.divinity.hlspells.player.capability.PlayerCapProvider;
import com.divinity.hlspells.setup.client.ClientSetup;
import com.divinity.hlspells.util.Util;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.ListNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Iterator;

@Mod.EventBusSubscriber(modid = HLSpells.MODID)
public class EntityDiesEvent {

    //Prioritize Death totem savers
    @SubscribeEvent
    public static void onEntityDies(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) event.getEntityLiving();
            World world = player.level;

            //Responsible for stopping the totem in both hands being activated
            boolean griefingTotem = true;
            boolean escapingTotem = true;
            boolean returnTotem = true;
            boolean keepingTotem = true;

            // Check if any item in the player's inventory contains soul bond enchant
            boolean soulBond = player.inventory.compartments
                    .stream()
                    .flatMap(Collection::stream) // Flatmap to reduce overhead
                    .anyMatch(p -> EnchantmentHelper.getItemEnchantmentLevel(EnchantmentInit.SOUL_BOND.get(), p) > 0);
            // Handles totem functionality in curios slots if present
            if (HLSpells.isCurioLoaded) {
                if (CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_ESCAPING.get()).isPresent()) {
                    event.setCanceled(true);
                    CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_ESCAPING.get()).ifPresent(map -> {
                        ItemStack stack = map.getRight();
                        ModTotemItem.vanillaTotemBehavior(player, stack, ItemInit.TOTEM_OF_ESCAPING.get());
                    });
                    event.setCanceled(true);
                    player.addEffect(new EffectInstance(Effects.INVISIBILITY, 200, 0));
                    // Teleport the player randomly nearby
                    randomTeleport(player);
                    escapingTotem = false;
                    keepingTotem = false;
                    returnTotem = false;
                    griefingTotem = false;
                }

                if (CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_KEEPING.get()).isPresent()) {
                    CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_KEEPING.get()).ifPresent(map -> {
                        ItemStack stack = map.getRight();
                        stack.getCapability(TotemItemProvider.TOTEM_CAP).ifPresent(cap -> {
                            cap.hasDied(true);
                            cap.setTotemInHand(Hand.MAIN_HAND);
                            cap.setCuriosSlot(map.getMiddle());
                            cap.setDiedTotemInCurios(true);
                        });
                    });
                    escapingTotem = false;
                    keepingTotem = false;
                    soulBond = false;
                }

                if (CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_GRIEFING.get()).isPresent() && griefingTotem) {
                    CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_GRIEFING.get()).ifPresent(triple -> {
                        world.explode(player, player.getX(), player.getY(), player.getZ(), 5.0F, Explosion.Mode.BREAK);
                        triple.getRight().shrink(1);
                    });
                    escapingTotem = false;
                    griefingTotem = false;
                }

                if (CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_RETURNING.get()).isPresent() && returnTotem) {
                    CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_RETURNING.get()).ifPresent(map -> {
                        ItemStack stack = map.getRight();
                        stack.getCapability(TotemItemProvider.TOTEM_CAP).ifPresent(cap -> {
                            cap.hasDied(true);
                            cap.setTotemInHand(Hand.MAIN_HAND);
                            cap.setCuriosSlot(map.getMiddle());
                            cap.setDiedTotemInCurios(true);
                            cap.setBlockPos(player.blockPosition());
                        });
                    });
                    escapingTotem = false;
                    returnTotem = false;
                }
            }
            // Disable all custom totems if vanilla totem is activated
            for (Hand hand : Hand.values()) {
                ItemStack heldItem = player.getItemInHand(hand);
                if (heldItem.getItem() == Items.TOTEM_OF_UNDYING) {
                    escapingTotem = false;
                    keepingTotem = false;
                    griefingTotem = false;
                    returnTotem = false;
                }
            }
            // TOTEM OF ESCAPING (Does vanilla totem logic and teleports the player randomly)
            for (Hand hand : Hand.values()) {
                ItemStack heldItem = player.getItemInHand(hand);
                if (heldItem.getItem() == ItemInit.TOTEM_OF_ESCAPING.get() && escapingTotem) {
                    event.setCanceled(true);
                    ModTotemItem.vanillaTotemBehavior(player, heldItem, ItemInit.TOTEM_OF_ESCAPING.get());
                    player.addEffect(new EffectInstance(Effects.INVISIBILITY, 200, 0));
                    // Teleport the player randomly nearby
                    randomTeleport(player);
                    escapingTotem = false;
                    keepingTotem = false;
                    returnTotem = false;
                    griefingTotem = false;
                }
            }
            // TOTEM OF GRIEFING (Explodes if the totem is held)
            for (Hand hand : Hand.values()) {
                ItemStack heldItem = player.getItemInHand(hand);
                if (heldItem.getItem() == ItemInit.TOTEM_OF_GRIEFING.get() && griefingTotem) {
                    world.explode(player, player.getX(), player.getY(), player.getZ(), 5.0F, Explosion.Mode.BREAK);
                    // Removes the totem from dropping
                    player.setItemInHand(hand, ItemStack.EMPTY);
                    griefingTotem = false;
                }
            }
            // TOTEM OF RETURNING (Saves the hand the totem is in and updates the totem the player has died)
            for (Hand hand : Hand.values()) {
                ItemStack heldItem = player.getItemInHand(hand);
                if (heldItem.getItem() == ItemInit.TOTEM_OF_RETURNING.get() && returnTotem) {
                    heldItem.getCapability(TotemItemProvider.TOTEM_CAP).ifPresent(cap -> {
                        cap.hasDied(true);
                        cap.setBlockPos(player.blockPosition());
                        if (hand == Hand.MAIN_HAND) cap.setTotemInHand(Hand.MAIN_HAND);
                        else if (hand == Hand.OFF_HAND) cap.setTotemInHand(Hand.OFF_HAND);
                    });
                    returnTotem = false;
                }
            }
            // Save the inventory and curios at the end to save modifications.
            for (Hand hand : Hand.values()) {
                ItemStack heldItem = player.getItemInHand(hand);
                if (heldItem.getItem() == ItemInit.TOTEM_OF_KEEPING.get() && keepingTotem) {
                    heldItem.getCapability(TotemItemProvider.TOTEM_CAP).ifPresent(cap -> {
                        cap.hasDied(true);
                        if (hand == Hand.MAIN_HAND) cap.setTotemInHand(Hand.MAIN_HAND);
                        else if (hand == Hand.OFF_HAND) cap.setTotemInHand(Hand.OFF_HAND);
                        cap.setInventoryNBT(player.inventory.save(new ListNBT()));
                        if (HLSpells.isCurioLoaded)
                            cap.setCuriosNBT(CuriosCompat.getCuriosInv(player));
                    });
                    keepingTotem = false;
                    soulBond = false;
                }
            }
            if (HLSpells.isCurioLoaded && CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_KEEPING.get()).isPresent()) {
                soulBond = false;
                CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_KEEPING.get()).ifPresent(map -> {
                    ItemStack stack = map.getRight();
                    stack.getCapability(TotemItemProvider.TOTEM_CAP).ifPresent(cap -> {
                        cap.setInventoryNBT(player.inventory.save(new ListNBT()));
                        cap.setCuriosNBT(CuriosCompat.getCuriosInv(player));
                    });
                });
            }

            if (soulBond) {
                player.getCapability(PlayerCapProvider.PLAYER_CAP).ifPresent(cap -> {
                    int size = player.inventory.compartments.stream().mapToInt(NonNullList::size).sum();
                    for (int i = 0; i < size; i++) {
                        ItemStack stack = player.inventory.getItem(i);
                        if (EnchantmentHelper.getItemEnchantmentLevel(EnchantmentInit.SOUL_BOND.get(), stack) > 0)
                            cap.addSoulBondItem(i, stack);
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public static void onEntityDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) event.getEntity();
            // If true the inventory is loaded back and drops are removed
            boolean[] keepingTotem = new boolean[1];
            for (Iterator<ItemEntity> itemEntityIterator = event.getDrops().iterator(); itemEntityIterator.hasNext(); ) {
                ItemStack stack = itemEntityIterator.next().getItem();
                // TOTEM OF KEEPING (Reloads player inventory even after dying and disables inventory from spilling)
                if (stack.getItem() == ItemInit.TOTEM_OF_KEEPING.get() && !keepingTotem[0]) {
                    stack.getCapability(TotemItemProvider.TOTEM_CAP).filter(ITotemCap::getHasDied).ifPresent(cap -> {
                        Hand hand = cap.getTotemInHand();
                        if (hand == Hand.MAIN_HAND || hand == Hand.OFF_HAND) {
                            player.inventory.load(cap.getInventoryNBT());
                            if (HLSpells.isCurioLoaded) {
                                CuriosCompat.restoreCuriosInv(player, cap.getCuriosNBT());
                                CuriosCompat.getItemInCuriosSlot(player, ItemInit.TOTEM_OF_KEEPING.get()).ifPresent(triple -> {
                                    triple.getRight().getCapability(TotemItemProvider.TOTEM_CAP).ifPresent(totemCap -> {
                                        totemCap.setDiedTotemInCurios(true);
                                        totemCap.setCuriosSlot(cap.getCuriosSlot());
                                    });
                                });
                            }
                            cap.setTotemInHand(null);
                            keepingTotem[0] = true;
                            itemEntityIterator.remove();
                        }
                    });
                }
                // TOTEM OF RETURNING (Sets BlockPos to teleportToLocation to and sets the slot the totem should be in)
                if (stack.getItem() == ItemInit.TOTEM_OF_RETURNING.get()) {
                    stack.getCapability(TotemItemProvider.TOTEM_CAP).filter(ITotemCap::getHasDied).ifPresent(cap ->
                    {
                        Hand hand = cap.getTotemInHand();
                        boolean returnInCurio = false;
                        if (HLSpells.isCurioLoaded && cap.diedTotemInCurios()) {
                            CuriosCompat.getStackHandler(player).ifPresent(stackHandler -> stackHandler.getStacks().setStackInSlot(cap.getCuriosSlot(), stack));
                            itemEntityIterator.remove();
                            cap.setTotemInHand(null);
                            returnInCurio = true;
                        }
                        // The drop is removed here to avoid deleting both totems held in both hands
                        if (!returnInCurio) {
                            if (hand == Hand.MAIN_HAND) {
                                player.inventory.add(player.inventory.selected, stack);
                                itemEntityIterator.remove();
                                cap.setTotemInHand(null);
                            } else if (hand == Hand.OFF_HAND) {
                                player.inventory.offhand.set(0, stack);
                                itemEntityIterator.remove();
                                cap.setTotemInHand(null);
                            }
                        }
                    });
                }
            }
            for (Iterator<ItemEntity> itemEntityIterator = event.getDrops().iterator(); itemEntityIterator.hasNext(); ) {
                ItemStack stack = itemEntityIterator.next().getItem();
                // SOUL BOND
                if (EnchantmentHelper.getItemEnchantmentLevel(EnchantmentInit.SOUL_BOND.get(), stack) > 0 && !keepingTotem[0]) {
                    itemEntityIterator.remove();
                }
            }
            if (!keepingTotem[0]) {
                // Present here to show the soul bond items on respawn screen.
                player.getCapability(PlayerCapProvider.PLAYER_CAP).ifPresent(cap -> cap.getSoulBondItems().forEach((pIndex, pStack) -> {
                    if (player.inventory.getItem(pIndex).isEmpty()) {
                        player.inventory.setItem(pIndex, pStack);
                    } else {
                        player.inventory.add(pStack);
                    }
                }));
            }
            if (keepingTotem[0])
                event.getDrops().removeIf(itemEntity -> {
                    if (player.inventory.contains(itemEntity.getItem())) return true;
                    return HLSpells.isCurioLoaded && CuriosCompat.getItemInCuriosSlot(player, itemEntity.getItem().getItem()).isPresent();
                });
        }
    }

    @SubscribeEvent
    public static void onEntityCloned(PlayerEvent.Clone event) {
        if (event.isWasDeath() && !event.getEntity().level.isClientSide()) {
            PlayerEntity original = event.getOriginal();
            PlayerEntity current = event.getPlayer();
            boolean keepingActivated = false;
            // TOTEM OF KEEPING (Restores the inventory)
            if (HLSpells.isCurioLoaded && CuriosCompat.getItemInCuriosSlot(original, ItemInit.TOTEM_OF_KEEPING.get()).isPresent()) {
                CuriosCompat.getItemInCuriosSlot(original, ItemInit.TOTEM_OF_KEEPING.get()).ifPresent(triple -> {
                    triple.getRight().getCapability(TotemItemProvider.TOTEM_CAP).ifPresent(cap -> {
                        if (cap.diedTotemInCurios()) {
                            triple.getRight().shrink(1);
                        }
                    });
                });
                CuriosCompat.restoreCuriosInv(current, CuriosCompat.getCuriosInv(original));
                keepingActivated = true;
            } else if (original.getMainHandItem().getItem() == ItemInit.TOTEM_OF_KEEPING.get()) {
                int mainSlot = original.inventory.findSlotMatchingItem(original.getMainHandItem());
                original.inventory.getItem(mainSlot != -1 ? mainSlot : 0).shrink(mainSlot != -1 ? 1 : 0);
                keepingActivated = true;
            } else if (original.getOffhandItem().getItem() == ItemInit.TOTEM_OF_KEEPING.get()) {
                original.inventory.offhand.get(0).shrink(1);
                keepingActivated = true;
            }
            if (keepingActivated) {
                current.inventory.replaceWith(original.inventory);
                displayActivation(current, ItemInit.TOTEM_OF_KEEPING.get(), true);
            }

            // TOTEM OF RETURNING (Adds totem to the inventory)
            if (HLSpells.isCurioLoaded && CuriosCompat.getItemInCuriosSlot(original, ItemInit.TOTEM_OF_RETURNING.get()).isPresent()) {
                CuriosCompat.restoreCuriosInv(current, CuriosCompat.getCuriosInv(original));
            } else if (original.getMainHandItem().getItem() == ItemInit.TOTEM_OF_RETURNING.get()) {
                current.inventory.setItem(original.inventory.selected, original.inventory.getSelected());
            } else if (original.getOffhandItem().getItem() == ItemInit.TOTEM_OF_RETURNING.get()) {
                current.inventory.offhand.set(0, original.inventory.offhand.get(0));
            }

            // SOUL BOND
            if (!keepingActivated) {
                original.getCapability(PlayerCapProvider.PLAYER_CAP).ifPresent(cap -> {
                    if (!cap.getSoulBondItems().isEmpty()) {
                        cap.getSoulBondItems().forEach((pIndex, pStack) -> {
                            if (current.inventory.getItem(pIndex).isEmpty()) {
                                current.inventory.setItem(pIndex, pStack);
                            } else {
                                current.inventory.add(pStack);
                            }
                        });
                        cap.getSoulBondItems().clear();
                    }
                });
            }
        }
    }

    // TOTEM OF RETURNING (Teleports the player to last died pos when right clicked)
    @SubscribeEvent
    public static void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getPlayer() != null) {
            PlayerEntity player = event.getPlayer();
            World world = player.level;
            if (!world.isClientSide()) {
                for (Hand hand : Hand.values()) {
                    ItemStack stack = player.getItemInHand(hand);
                    if (stack.getItem() == ItemInit.TOTEM_OF_RETURNING.get()) {
                        stack.getCapability(TotemItemProvider.TOTEM_CAP).filter(ITotemCap::getHasDied).ifPresent(cap -> {
                            BlockPos pos = cap.getBlockPos();
                            displayActivation(player, ItemInit.TOTEM_OF_RETURNING.get(), true);
                            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundCategory.PLAYERS, 0.3F, 0.3F);
                            player.teleportTo(pos.getX(), pos.getY(), pos.getZ());
                            player.setItemInHand(hand, ItemStack.EMPTY);
                            Util.doTeleportParticles(world, pos, 200);
                            world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7F, 0.7F);
                        });
                        return;
                    }
                }
            }
        }
    }

    /**
     * Method to hide the client side call to show totem activation and/or particles
     */
    public static void displayActivation(PlayerEntity player, Item item, boolean particleIn) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.displayActivation(player, new ItemStack(item), particleIn));
    }

    private static void randomTeleport(LivingEntity entity) {
        double d0 = entity.getX();
        double d1 = entity.getY();
        double d2 = entity.getZ();
        for (int i = 0; i < 16; ++i) {
            double d3 = entity.getX() + (entity.getRandom().nextDouble() - 0.5D) * 16.0D;
            double d4 = MathHelper.clamp(entity.getY() + (entity.getRandom().nextInt(16) - 8), 0.0D, (entity.level.getHeight() - 1));
            double d5 = entity.getZ() + (entity.getRandom().nextDouble() - 0.5D) * 16.0D;
            if (entity.isPassenger()) {
                entity.stopRiding();
            }

            if (entity.randomTeleport(d3, d4, d5, true)) {
                SoundEvent soundevent = SoundEvents.CHORUS_FRUIT_TELEPORT;
                entity.level.playSound(null, d0, d1, d2, soundevent, SoundCategory.PLAYERS, 1.0F, 1.0F);
                entity.playSound(soundevent, 1.0F, 1.0F);
                break;
            }
        }
    }
}
