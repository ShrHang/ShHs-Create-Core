package io.github.shrhang.shhs_create_core.content.util.magic;

import io.github.shrhang.shhs_create_core.content.data.ShHsTagKey;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public class SpellCastHelper {
    public record SpellSource(SpellData spellData, CastSource castSource,
                              @Nullable EquipmentSlot slot, @Nullable String curiosSlot,
                              int curiosIndex, int spellIndex) {
        public String slotName() {
            return slot != null ? slot.getName() : curiosSlot + "_" + curiosIndex;
        }

        public ItemStack stack(LivingEntity entity) {
            if (slot != null) return entity.getItemBySlot(slot);
            if (curiosSlot == null || curiosIndex < 0) return ItemStack.EMPTY;
            return CuriosApi.getCuriosInventory(entity)
                    .filter(inv -> inv.isSlotActive(curiosSlot, curiosIndex))
                    .flatMap(inv -> inv.getStacksHandler(curiosSlot)).filter(handler -> curiosIndex < handler.getStacks().getSlots()).map(handler -> handler.getStacks().getStackInSlot(curiosIndex)).orElse(ItemStack.EMPTY);
        }

        public boolean isValid(LivingEntity entity, ItemStack snapshot) {
            ItemStack stack = stack(entity);
            if (!ItemStack.isSameItemSameComponents(stack, snapshot)) return false;
            if (!ISpellContainer.isSpellContainer(stack) || !accepts(stack, slot)) return false;
            if (sourceType(stack) != castSource) return false;
            var container = ISpellContainer.get(stack);
            if (spellIndex < 0 || spellIndex >= container.getMaxSpellCount()) return false;
            return container.getActiveSpells().stream().anyMatch(s -> s.index() == spellIndex
                    && s.getSpell().equals(spellData.getSpell()) && s.getLevel() == spellData.getLevel());
        }
    }

    public static List<SpellSource> getEntitySpells(LivingEntity entity) {
        List<SpellSource> result = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values())
            collect(result, entity.getItemBySlot(slot), slot, null, -1);
        CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.getCurios().forEach((name, handler) -> {
            for (int i = 0; i < handler.getStacks().getSlots(); i++)
                if (inv.isSlotActive(name, i))
                    collect(result, handler.getStacks().getStackInSlot(i), null, name, i);
        }));
        return result;
    }

    private static void collect(List<SpellSource> result, ItemStack stack, EquipmentSlot slot,
                                String curiosSlot, int curiosIndex) {
        if (!ISpellContainer.isSpellContainer(stack) || !accepts(stack, slot)) return;
        for (SpellSlot spellSlot : ISpellContainer.get(stack).getActiveSpells()) {
            if (stack.getItem() instanceof IScroll && spellSlot.index() != 0) continue;
            if (spellSlot.getSpell().equals(SpellRegistry.none()) || spellSlot.getLevel() < 1) continue;
            result.add(new SpellSource(spellSlot.spellData(), sourceType(stack), slot,
                    curiosSlot, curiosIndex, spellSlot.index()));
        }
    }

    private static boolean accepts(ItemStack stack, EquipmentSlot slot) {
        boolean held = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        if (stack.getItem() instanceof IScroll) return held;
        var container = ISpellContainer.get(stack);
        if (stack.getItem() instanceof ISpellbook) return container.isSpellWheel();
        return container.isSpellWheel() && (!container.mustEquip() || !held);
    }

    private static CastSource sourceType(ItemStack stack) {
        if (stack.getItem() instanceof IScroll) return CastSource.SCROLL;
        if (stack.getItem() instanceof ISpellbook) return CastSource.SPELLBOOK;
        return CastSource.SWORD;
    }

    public static boolean isSpellAllowed(LivingEntity entity, AbstractSpell spell) {
        return !spell.equals(SpellRegistry.none()) && spell.isEnabled()
                && !ShHsTagKey.ENTITY_SPELL_BLACKLIST.contains(spell);
    }

    public static void forceLookAtTarget(LivingEntity entity, LivingEntity target) {
        lookAt(entity, target.getEyePosition());
    }

    public static void lookAt(LivingEntity entity, Vec3 point) {
        Vec3 direction = point.subtract(entity.getEyePosition());
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setXRot(pitch);
    }
}
