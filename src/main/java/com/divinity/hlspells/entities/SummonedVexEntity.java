package com.divinity.hlspells.entities;

import com.mojang.datafixers.types.templates.Sum;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.monster.SlimeEntity;
import net.minecraft.entity.monster.VexEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.IPacket;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class SummonedVexEntity extends VexEntity
{
    protected PlayerEntity owner;

    public SummonedVexEntity(EntityType<? extends VexEntity> entityType, World world)
    {
        super(entityType, world);
        this.moveControl = new SummonedVexEntity.MoveHelperController(this);
        this.xpReward = 0;
    }

    @Override
    public boolean isAlliedTo(Entity entity)
    {
        return entity instanceof SummonedVexEntity || entity == this.owner;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.owner != null)
        {
            this.setBoundOrigin(this.owner.blockPosition());
            if (this.isAlive() && this.distanceTo(this.owner) > 14.0D)
            {
                BlockPos ownerPos = this.owner.blockPosition().offset(-2 + this.owner.level.random.nextInt(5),
                        1, -2 + this.owner.level.random.nextInt(5));
                this.moveControl.setWantedPosition(ownerPos.getX(), ownerPos.getY(), ownerPos.getZ(), 0.75D);
                this.setBoundOrigin(this.owner.blockPosition());
            }
        }
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(4, new ChargeAttackGoal());
        this.goalSelector.addGoal(8, new MoveRandomGoal());
        this.goalSelector.addGoal(9, new LookAtGoal(this, PlayerEntity.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtGoal(this, MobEntity.class, 8.0F));
        this.targetSelector.addGoal(1, new CopyOwnerTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, MonsterEntity.class, true));
    }

    public PlayerEntity getSummonedOwner()
    {
        return this.owner;
    }

    public void setSummonedOwner(PlayerEntity owner)
    {
        this.owner = owner;
    }

    @Override
    protected void populateDefaultEquipmentSlots(DifficultyInstance instance)
    {
        if (this.random.nextInt(2) == 0)
        {
            this.setItemSlot(EquipmentSlotType.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }

        else
        {
            this.setItemSlot(EquipmentSlotType.MAINHAND, new ItemStack(Items.IRON_AXE));
        }
        this.setDropChance(EquipmentSlotType.MAINHAND, 0.0F);
    }


    @Override
    public IPacket<?> getAddEntityPacket()
    {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    class ChargeAttackGoal extends Goal
    {
        public ChargeAttackGoal()
        {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canUse()
        {
            if (SummonedVexEntity.this.getTarget() != null && !SummonedVexEntity.this.getMoveControl().hasWanted() && SummonedVexEntity.this.random.nextInt(2) == 0)
            {
                return SummonedVexEntity.this.distanceToSqr(SummonedVexEntity.this.getTarget()) > 4.0D;
            }
            else
            {
                return false;
            }
        }

        public boolean canContinueToUse()
        {
            return SummonedVexEntity.this.getMoveControl().hasWanted() && SummonedVexEntity.this.isCharging() && SummonedVexEntity.this.getTarget() != null && SummonedVexEntity.this.getTarget().isAlive();
        }

        public void start()
        {
            LivingEntity livingentity = SummonedVexEntity.this.getTarget();
            Vector3d vector3d = livingentity.getEyePosition(1.0F);
            SummonedVexEntity.this.moveControl.setWantedPosition(vector3d.x, vector3d.y, vector3d.z, 1.0D);
            SummonedVexEntity.this.setIsCharging(true);
            SummonedVexEntity.this.playSound(SoundEvents.VEX_CHARGE, 1.0F, 1.0F);
        }

        public void stop()
        {
            SummonedVexEntity.this.setIsCharging(false);
        }

        public void tick()
        {
            LivingEntity livingentity = SummonedVexEntity.this.getTarget();
            if (SummonedVexEntity.this.getBoundingBox().intersects(livingentity.getBoundingBox()))
            {
                SummonedVexEntity.this.doHurtTarget(livingentity);
                SummonedVexEntity.this.setIsCharging(false);
            }

            else
            {
                double d0 = SummonedVexEntity.this.distanceToSqr(livingentity);
                if (d0 < 9.0D)
                {
                    Vector3d vector3d = livingentity.getEyePosition(1.0F);
                    SummonedVexEntity.this.moveControl.setWantedPosition(vector3d.x, vector3d.y, vector3d.z, 1.0D);
                }
            }

        }
    }

    class CopyOwnerTargetGoal extends TargetGoal
    {
        private final EntityPredicate copyOwnerTargeting = (new EntityPredicate()).allowUnseeable().ignoreInvisibilityTesting();

        public CopyOwnerTargetGoal(CreatureEntity p_i47231_2_)
        {
            super(p_i47231_2_, false);
        }

        public boolean canUse()
        {
            return SummonedVexEntity.this.owner != null && SummonedVexEntity.this.owner.getLastHurtMob() != null && this.canAttack(SummonedVexEntity.this.owner.getLastHurtMob(), this.copyOwnerTargeting) && !(SummonedVexEntity.this.owner.getLastHurtMob() instanceof SummonedVexEntity) && !(SummonedVexEntity.this.owner.getLastHurtMob() instanceof PlayerEntity);
        }

        public void start()
        {
            SummonedVexEntity.this.setTarget(SummonedVexEntity.this.owner.getLastHurtMob());
            super.start();
        }
    }

    class MoveHelperController extends MovementController
    {
        public MoveHelperController(VexEntity p_i47230_2_)
        {
            super(p_i47230_2_);
        }

        public void tick()
        {
            if (this.operation == MovementController.Action.MOVE_TO)
            {
                Vector3d vector3d = new Vector3d(this.wantedX - SummonedVexEntity.this.getX(), this.wantedY - SummonedVexEntity.this.getY(), this.wantedZ - SummonedVexEntity.this.getZ());
                double d0 = vector3d.length();
                if (d0 < SummonedVexEntity.this.getBoundingBox().getSize())
                {
                    this.operation = MovementController.Action.WAIT;
                    // Try this for speed
                    SummonedVexEntity.this.setDeltaMovement(SummonedVexEntity.this.getDeltaMovement().scale(0.5D));
                }

                else
                {
                    SummonedVexEntity.this.setDeltaMovement(SummonedVexEntity.this.getDeltaMovement().add(vector3d.scale(this.speedModifier * 0.05D / d0)));

                    if (SummonedVexEntity.this.getTarget() == null)
                    {
                        Vector3d vector3d1 = SummonedVexEntity.this.getDeltaMovement();
                        SummonedVexEntity.this.yRot = -((float) MathHelper.atan2(vector3d1.x, vector3d1.z)) * (180F / (float)Math.PI);
                    }
                    else
                    {
                        double d2 = SummonedVexEntity.this.getTarget().getX() - SummonedVexEntity.this.getX();
                        double d1 = SummonedVexEntity.this.getTarget().getZ() - SummonedVexEntity.this.getZ();
                        SummonedVexEntity.this.yRot = -((float)MathHelper.atan2(d2, d1)) * (180F / (float)Math.PI);
                    }
                    SummonedVexEntity.this.yBodyRot = SummonedVexEntity.this.yRot;
                }
            }
        }
    }

    class MoveRandomGoal extends Goal
    {
        public MoveRandomGoal()
        {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canUse()
        {
            return !SummonedVexEntity.this.getMoveControl().hasWanted() && SummonedVexEntity.this.random.nextInt(7) == 0;
        }

        public boolean canContinueToUse() {
            return false;
        }

        public void tick()
        {
            BlockPos blockpos = SummonedVexEntity.this.getBoundOrigin();
            if (blockpos == null)
            {
                blockpos = SummonedVexEntity.this.owner.blockPosition();
            }

            for(int i = 0; i < 3; ++i)
            {
                BlockPos blockpos1 = blockpos.offset(SummonedVexEntity.this.random.nextInt(15) - 7, SummonedVexEntity.this.random.nextInt(11) - 5, SummonedVexEntity.this.random.nextInt(15) - 7);
                if (SummonedVexEntity.this.level.isEmptyBlock(blockpos1))
                {
                    SummonedVexEntity.this.moveControl.setWantedPosition((double)blockpos1.getX() + 0.5D, (double)blockpos1.getY() + 0.5D, (double)blockpos1.getZ() + 0.5D, 0.25D);
                    if (SummonedVexEntity.this.getTarget() == null)
                    {
                        SummonedVexEntity.this.getLookControl().setLookAt((double)blockpos1.getX() + 0.5D, (double)blockpos1.getY() + 0.5D, (double)blockpos1.getZ() + 0.5D, 180.0F, 20.0F);
                    }
                    break;
                }
            }
        }
    }
}