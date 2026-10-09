package io.github.shrhang.shhs_create_core.content.hostility.traits;

import com.google.common.base.Suppliers;
import dev.xkmc.l2hostility.content.capability.mob.CapStorageData;
import dev.xkmc.l2hostility.content.logic.TraitManager;
import dev.xkmc.l2hostility.content.traits.legendary.LegendaryTrait;
import dev.xkmc.l2hostility.init.L2Hostility;
import dev.xkmc.l2hostility.init.registrate.LHEnchantments;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import dev.xkmc.l2serial.serialization.marker.SerialClass;
import dev.xkmc.l2serial.serialization.marker.SerialField;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobMagicManager;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellCastGoal;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellSupport;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellTactics;
import io.github.shrhang.shhs_create_core.content.registries.ShHsTraits;
import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.loot.SpellFilter;
import io.redspace.ironsspellbooks.registries.DataAttachmentRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static io.github.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static io.github.shrhang.shhs_create_core.content.data.ShHsTagKey.*;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.*;

public class WizardTrait extends LegendaryTrait {
    private static final String BOOK_OWNER = "shhs_create_core:wizard_book_owner";

    public WizardTrait(IntSupplier color) {
        super(color);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, WizardTrait::onTick);
    }

    @SerialClass
    public static class Data extends CapStorageData {
        @SerialField public boolean equipmentProcessed;
        @SerialField public int level = -1;
        @SerialField public boolean ownsCasting;
        public transient boolean runtimeReady;
        public transient MobSpellCastGoal goal;
    }

    @Override
    public void initialize(@NotNull LivingEntity entity, int traitLV) {
        if (!(entity instanceof Mob mob) || ineligible(mob)) return;
        int level = Mth.clamp(traitLV, 0, 5);
        attribute(mob, MAX_MANA, "wizard_max_mana", level * SERVER.wizardMaxManaPerLev.get(), AttributeModifier.Operation.ADD_VALUE);
        attribute(mob, MANA_REGEN, "wizard_mana_regen", level * SERVER.wizardManaRegenPerLev.get(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        attribute(mob, COOLDOWN_REDUCTION, "wizard_cooldown_reduction", level * SERVER.wizardCooldownReductionPerLev.get(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (mob.hasData(DataAttachmentRegistry.MAGIC_DATA)) {
            var magic = MagicData.getPlayerMagicData(mob);
            magic.setMana(Math.min(magic.getMana(), (float) mob.getAttributeValue(MAX_MANA)));
        }
    }

    @Override
    public void postInit(@NotNull LivingEntity entity, int traitLV) {
        if (!(entity instanceof Mob mob) || ineligible(mob)) return;
        int level = Mth.clamp(traitLV, 0, 5);
        var cap = LHMiscs.MOB.type().getOrCreate(mob);
        Data data = cap.getOrCreateData(getRegistryName(), Data::new);
        var pool = Suppliers.memoize(() -> spellPool(mob, level)); // 初始化法术池
        if (level > 0 && !data.equipmentProcessed) {
            data.equipmentProcessed = true;
            if (SERVER.wizardImbueEquipment.get()) imbueEquipment(mob, level, pool.get());
        }
        sync(mob, data, level, pool);
    }

    private static boolean ineligible(Mob mob) {
        return mob.level().isClientSide || !mob.isAlive() || mob instanceof IMagicEntity;
    }

    @Override
    public void tick(@NotNull LivingEntity entity, int traitLV) {
        if (!(entity instanceof Mob mob) || ineligible(mob)) return;
        var cap = LHMiscs.MOB.type().getExisting(mob).orElse(null);
        if (cap == null) return;
        int level = Mth.clamp(traitLV, 0, 5);
        Data data = cap.getData(getRegistryName());
        if (data == null) {
            if (level == 0) return;
            // Legacy loaded entities have no generation record; do not imbue their equipment.
            data = cap.getOrCreateData(getRegistryName(), Data::new);
            data.equipmentProcessed = true;
        }
        if (data.level != level || level > 0 && !data.runtimeReady) {
            initialize(mob, level);
            sync(mob, data, level);
        }
    }

    /** 仅清理绕过移除回调的巫师状态 */
    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || ineligible(mob)) return;
        var cap = LHMiscs.MOB.type().getExisting(mob).orElse(null);
        var trait = ShHsTraits.WIZARD.get();
        if (cap == null || !cap.isInitialized() || cap.hasTrait(trait)) return; // 如果词条未被移除，父类的 tick() 会在 postInit 后清理状态。
        Data data = cap.getData(trait.getRegistryName());
        // 反之，若词条已被移除，但数据仍存在，则清理状态。
        if (data != null && data.level > 0) {
            trait.initialize(mob, 0);
            sync(mob, data, 0);
        }
    }

    private static void sync(Mob mob, Data data, int level) {
        sync(mob, data, level, Suppliers.memoize(() -> spellPool(mob, level))); // 实际调用处只会进入 level == 0 的分支，不会使用法术池。
    }

    private static void sync(Mob mob, Data data, int level, Supplier<List<AbstractSpell>> pool) {
        if (level == 0) {
            if (data.goal != null) mob.goalSelector.removeGoal(data.goal);
            if (data.ownsCasting) MobMagicManager.disable(mob);
            ownedBooks(mob).forEach(book -> book.setCount(0));
            data.goal = null;
            data.runtimeReady = false;
            data.ownsCasting = false;
        } else {
            if (data.level != level) updateBook(mob, level, pool);
            if (!data.runtimeReady) {
                boolean enabled = MobMagicManager.isEnabled(mob);
                if (data.level <= 0 || !enabled) data.ownsCasting = !enabled;
                if ((enabled || MobMagicManager.enable(mob)) && mob.goalSelector.getAvailableGoals().stream()
                        .noneMatch(goal -> goal.getGoal() instanceof MobSpellCastGoal)) {
                    data.goal = new MobSpellCastGoal(mob);
                    mob.goalSelector.addGoal(1, data.goal);
                }
                data.runtimeReady = true;
            }
        }
        data.level = level;
    }

    private static void attribute(Mob mob, Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation operation) {
        var instance = mob.getAttribute(attribute);
        if (instance == null) return;
        if (amount == 0) instance.removeModifier(L2Hostility.loc(id));
        else TraitManager.addAttribute(mob, attribute, id, amount, operation);
    }

    /** 过滤掉不允许的、无施法类型的、最大等级为 0 的，以及不支持的法术。 */
    private static List<AbstractSpell> spellPool(Mob mob, int level) {
        return new SpellFilter().getApplicableSpells().stream()
                .filter(spell -> SpellCastHelper.isSpellAllowed(mob, spell) && spell.getCastType() != CastType.NONE
                        && spell.getMaxLevel() > 0 && MobSpellSupport.supports(spell, spellLevel(spell, level), mob))
                .distinct().toList();
    }

    private static int spellLevel(AbstractSpell spell, int level) {
        return Math.min(level * 2, spell.getMaxLevel());
    }

    private static void imbueEquipment(Mob mob, int level, List<AbstractSpell> pool) {
        if (pool.isEmpty()) return;
        for (var slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty() || stack.getItem() instanceof ISpellbook || stack.getItem() instanceof IScroll
                    || !Utils.canImbue(stack)) continue;
            var container = ISpellContainer.getOrCreate(stack).mutableCopy();
            if (!container.getActiveSpells().isEmpty() || !container.isSpellWheel()
                    || container.mustEquip() && slot.getType() == EquipmentSlot.Type.HAND) continue;
            int index = container.getNextAvailableIndex();
            if (index < 0) continue;
            var spell = pool.get(mob.getRandom().nextInt(pool.size()));
            if (container.addSpellAtIndex(spell, spellLevel(spell, level), index, false))
                ISpellContainer.set(stack, container.toImmutable());
        }
    }

    private static void updateBook(Mob mob, int level, Supplier<List<AbstractSpell>> pool) {
        var books = ownedBooks(mob);
        ItemStack book = books.isEmpty() ? new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get()) : books.getFirst();
        Consumer<ItemStack> destination = books.isEmpty() ? findBookSlot(mob, book) : null;
        if (books.isEmpty() && destination == null) return;
        var selected = selectSpells(pool.get(), level, mob.getRandom());
        if (books.isEmpty() && selected.isEmpty()) return;
        var container = ISpellContainer.create(level * 4, true, false).mutableCopy();
        for (var spell : selected) container.addSpell(spell, spellLevel(spell, level), false);
        ISpellContainer.set(book, container.toImmutable());
        book.enchant(LHEnchantments.VANISH.holder(), 1);
        CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> tag.putUUID(BOOK_OWNER, mob.getUUID()));
        if (destination != null) destination.accept(book);
    }

    static @Nullable Consumer<ItemStack> findBookSlot(LivingEntity entity, ItemStack book) {
        var inventory = CuriosApi.getCuriosInventory(entity).orElse(null);
        var handler = inventory == null ? null : inventory.getStacksHandler("spellbook").orElse(null);
        if (handler != null) {
            var stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (inventory.isSlotActive("spellbook", i) && stacks.getStackInSlot(i).isEmpty() && stacks.isItemValid(i, book)) {
                    int index = i;
                    return stack -> stacks.setStackInSlot(index, stack);
                }
            }
        }
        return entity.getOffhandItem().isEmpty() ? stack -> entity.setItemSlot(EquipmentSlot.OFFHAND, stack) : null;
    }

    private static List<ItemStack> ownedBooks(Mob mob) {
        List<ItemStack> items = new ArrayList<>();
        for (var slot : EquipmentSlot.values()) items.add(mob.getItemBySlot(slot));
        CuriosApi.getCuriosInventory(mob).ifPresent(inventory -> inventory.getCurios().values().forEach(handler -> {
            for (var stacks : List.of(handler.getStacks(), handler.getCosmeticStacks()))
                for (int i = 0; i < stacks.getSlots(); i++) items.add(stacks.getStackInSlot(i));
        }));
        return items.stream().filter(stack -> isOwnedBook(mob, stack)).toList();
    }

    private static boolean isOwnedBook(Mob mob, ItemStack stack) {
        if (!stack.is(ItemRegistry.WIMPY_SPELL_BOOK.get())) return false;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(BOOK_OWNER) && mob.getUUID().equals(tag.getUUID(BOOK_OWNER));
    }

    static List<AbstractSpell> selectSpells(List<AbstractSpell> pool, int level, RandomSource random) {
        List<AbstractSpell> remaining = new ArrayList<>(pool);
        List<AbstractSpell> selected = new ArrayList<>();
        while (selected.size() < level * 4 && !remaining.isEmpty()) {
            int[] weights = remaining.stream().mapToInt(spell -> comboWeight(selected, spell, level)).toArray();
            int total = 0;
            for (int weight : weights) total += weight;
            int roll = random.nextInt(total);
            for (int i = 0; i < remaining.size(); i++) {
                roll -= weights[i];
                if (roll < 0) {
                    selected.add(remaining.remove(i));
                    break;
                }
            }
        }
        return selected;
    }

    static int comboWeight(List<AbstractSpell> selected, AbstractSpell candidate, int level) {
        if (level < 3 || selected.size() < 4) return 1;
        // Each starter needs distinct follow-up spells, including for multi-tag spells.
        boolean control = selected.stream().anyMatch(MOB_SPELL_CONTROL::contains);
        boolean summon = selected.stream().anyMatch(MOB_SPELL_SUMMON::contains);
        boolean approach = selected.stream().anyMatch(MOB_SPELL_APPROACH::contains);
        boolean damage = selected.stream().anyMatch(MobSpellTactics::isDamageSpell);
        boolean close = selected.stream().anyMatch(spell -> MobSpellTactics.isDamageSpell(spell) && MOB_SPELL_CLOSE_RANGE.contains(spell));
        boolean complements = !control && damage && MOB_SPELL_CONTROL.contains(candidate)
                || !summon && damage && MOB_SPELL_SUMMON.contains(candidate)
                || !approach && close && MOB_SPELL_APPROACH.contains(candidate);
        if (MobSpellTactics.isDamageSpell(candidate)) {
            complements |= selected.stream().filter(MOB_SPELL_CONTROL::contains)
                    .anyMatch(start -> selected.stream().filter(spell -> spell != start && MobSpellTactics.isDamageSpell(spell)).count() < 2);
            complements |= selected.stream().filter(MOB_SPELL_SUMMON::contains)
                    .anyMatch(start -> selected.stream().noneMatch(spell -> spell != start && MobSpellTactics.isDamageSpell(spell)));
            if (MOB_SPELL_CLOSE_RANGE.contains(candidate))
                complements |= selected.stream().filter(MOB_SPELL_APPROACH::contains)
                        .anyMatch(start -> selected.stream().noneMatch(spell -> spell != start && MobSpellTactics.isDamageSpell(spell)
                                && MOB_SPELL_CLOSE_RANGE.contains(spell)));
        }
        return complements ? 1 + 2 * (level - 2) : 1;
    }

    @Override
    public boolean allow(@NotNull LivingEntity entity, int difficulty, int maxModLv) {
        return super.allow(entity, difficulty, maxModLv) && entity instanceof Mob && !(entity instanceof IMagicEntity);
    }
}
