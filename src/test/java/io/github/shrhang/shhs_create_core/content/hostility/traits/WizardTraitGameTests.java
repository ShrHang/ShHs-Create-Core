package io.github.shrhang.shhs_create_core.content.hostility.traits;

import dev.xkmc.l2hostility.init.L2Hostility;
import dev.xkmc.l2hostility.init.registrate.LHEnchantments;
import dev.xkmc.l2hostility.init.registrate.LHMiscs;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobMagicManager;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobSpellCastGoal;
import io.github.shrhang.shhs_create_core.content.registries.ShHsTraits;
import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static io.github.shrhang.shhs_create_core.ShHsConfig.SERVER;
import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.*;

@GameTestHolder(ShHsCreateCore.MODID)
@PrefixGameTestTemplate(false)
public class WizardTraitGameTests {
    private static Mob mob(GameTestHelper helper) {
        for (int x = 0; x < 12; x++)
            for (int z = 0; z < 12; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Mob mob = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.setNoGravity(true);
        var cap = LHMiscs.MOB.type().getOrCreate(mob);
        cap.reinit(mob, 0, false);
        cap.tick(mob);
        cap.traits.clear();
        return mob;
    }

    private static void rank(Mob mob, int level) {
        var cap = LHMiscs.MOB.type().getOrCreate(mob);
        cap.setTrait(ShHsTraits.WIZARD.get(), level);
        cap.tick(mob);
    }

    private static long goals(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream().filter(goal -> goal.getGoal() instanceof MobSpellCastGoal).count();
    }

    private static void checkBook(GameTestHelper helper, ItemStack book, int level) {
        helper.assertTrue(book.is(ItemRegistry.WIMPY_SPELL_BOOK.get()), "Must grant the native wimpy spellbook");
        var container = ISpellContainer.get(book);
        helper.assertTrue(container.getMaxSpellCount() == level * 4 && container.getActiveSpellCount() == level * 4,
                "Book capacity and filled slots must follow trait rank");
        helper.assertTrue(container.getActiveSpells().stream().map(slot -> slot.getSpell().getSpellId()).distinct().count() == level * 4,
                "Generated book must not repeat spell IDs");
        for (var slot : container.getActiveSpells()) {
            helper.assertTrue(slot.getLevel() == Math.min(level * 2, slot.getSpell().getMaxLevel()), "Spell level must clamp to native maximum");
            helper.assertTrue(slot.getSpell().allowLooting() && SpellCastHelper.isSpellAllowed(null, slot.getSpell()),
                    "Generated spells must obey native loot and mob restrictions");
        }
        helper.assertTrue(book.getEnchantmentLevel(LHEnchantments.VANISH.holder()) == 1, "Book must carry L2 destruction enchantment");
    }

    @GameTest(template = "mob_magic")
    public static void ranksGiveBooksAndAttributesWithoutDuplicates(GameTestHelper helper) {
        for (int level = 1; level <= 5; level++) {
            Mob mob = mob(helper);
            rank(mob, level);
            checkBook(helper, mob.getOffhandItem(), level);
            helper.assertTrue(mob.getAttributeValue(MAX_MANA) == 100 + level * SERVER.wizardMaxManaPerLev.get(), "Maximum mana must scale by rank");
            helper.assertTrue(Math.abs(mob.getAttributeValue(MANA_REGEN) - (1 + level * SERVER.wizardManaRegenPerLev.get())) < 1e-6,
                    "Mana regeneration must scale by rank");
            helper.assertTrue(Math.abs(mob.getAttributeValue(COOLDOWN_REDUCTION) - (1 + level * SERVER.wizardCooldownReductionPerLev.get())) < 1e-6,
                    "Cooldown attribute must scale by rank");
            ItemStack before = mob.getOffhandItem().copy();
            MagicData.getPlayerMagicData(mob).setMana(17);
            rank(mob, level);
            helper.assertTrue(ItemStack.matches(before, mob.getOffhandItem()), "Repeated rank must not reroll book");
            helper.assertTrue(goals(mob) == 1 && MagicData.getPlayerMagicData(mob).getMana() == 17,
                    "Repeated initialization must not duplicate goal or refill mana");
        }
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void equipmentUsesNativeImbuementAndPreservesComponents(GameTestHelper helper) {
        boolean original = SERVER.wizardImbueEquipment.get();
        try {
            SERVER.wizardImbueEquipment.set(true);
            Mob mob = mob(helper);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.setDamageValue(30);
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("fixture sword"));
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
            ItemStack shield = new ItemStack(Items.SHIELD);
            mob.setItemSlot(EquipmentSlot.MAINHAND, sword);
            mob.setItemSlot(EquipmentSlot.HEAD, helmet);
            mob.setItemSlot(EquipmentSlot.OFFHAND, shield);
            rank(mob, 2);
            helper.assertTrue(ISpellContainer.get(sword).getActiveSpellCount() == 1, "Eligible sword must gain one spell");
            helper.assertTrue(sword.getDamageValue() == 30 && sword.getHoverName().getString().equals("fixture sword"), "Imbuement must preserve components");
            helper.assertTrue(sword.getEnchantmentLevel(LHEnchantments.VANISH.holder()) == 0, "Original equipment must not gain destruction");
            helper.assertTrue(ISpellContainer.isSpellContainer(helmet) == Utils.canImbue(new ItemStack(Items.DIAMOND_HELMET)),
                    "Armor eligibility must follow native arcane anvil");
            helper.assertTrue(mob.getOffhandItem() == shield, "Occupied offhand must not be replaced");
            ItemStack imbued = sword.copy();
            rank(mob, 4);
            helper.assertTrue(ItemStack.matches(imbued, sword), "Rank changes must not reroll original equipment");
            rank(mob, 0);
            helper.assertTrue(ItemStack.matches(imbued, sword), "Trait removal must preserve original equipment spells");

            Mob second = mob(helper);
            ItemStack existing = new ItemStack(Items.DIAMOND_SWORD);
            ISpellContainer.createImbuedContainer(SpellRegistry.MAGIC_MISSILE_SPELL.get(), 1, existing);
            second.setItemSlot(EquipmentSlot.MAINHAND, existing);
            ItemStack snapshot = existing.copy();
            rank(second, 3);
            helper.assertTrue(ItemStack.matches(snapshot, existing), "Existing spell must be preserved even at lower level");
        } finally { SERVER.wizardImbueEquipment.set(original); }
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void disablingImbuementStillGrantsBookAndCasting(GameTestHelper helper) {
        boolean original = SERVER.wizardImbueEquipment.get();
        try {
            SERVER.wizardImbueEquipment.set(false);
            Mob mob = mob(helper);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            rank(mob, 1);
            helper.assertFalse(ISpellContainer.isSpellContainer(mob.getMainHandItem()), "Disabled imbuement must leave sword alone");
            checkBook(helper, mob.getOffhandItem(), 1);
            helper.assertTrue(MobMagicManager.isEnabled(mob), "Imbuement switch must not disable casting");
        } finally { SERVER.wizardImbueEquipment.set(original); }
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void emptySlotAndLostBookRetryOnlyOnRankChanges(GameTestHelper helper) {
        Mob mob = mob(helper);
        mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.APPLE));
        rank(mob, 1);
        helper.assertTrue(mob.getOffhandItem().is(Items.APPLE), "Must preserve occupied slot");
        mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        rank(mob, 1);
        helper.assertTrue(mob.getOffhandItem().isEmpty(), "Same rank must not retry failed grant");
        rank(mob, 2);
        checkBook(helper, mob.getOffhandItem(), 2);
        ItemStack first = mob.getOffhandItem();
        mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        rank(mob, 2);
        helper.assertTrue(mob.getOffhandItem().isEmpty(), "Same rank must not replace a lost book");
        rank(mob, 3);
        checkBook(helper, mob.getOffhandItem(), 3);
        helper.assertFalse(first == mob.getOffhandItem(), "Retry must create a new book");
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void rankChangesRerollOnlyOwnedBookAndClampMana(GameTestHelper helper) {
        Mob mob = mob(helper);
        rank(mob, 3);
        ItemStack book = mob.getOffhandItem();
        book.set(DataComponents.CUSTOM_NAME, Component.literal("kept title"));
        ItemStack unrelated = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
        unrelated.enchant(LHEnchantments.VANISH.holder(), 1);
        mob.setItemSlot(EquipmentSlot.MAINHAND, unrelated);
        MagicData.getPlayerMagicData(mob).setMana(600);
        rank(mob, 1);
        checkBook(helper, book, 1);
        helper.assertTrue(book == mob.getOffhandItem() && book.getHoverName().getString().equals("kept title"), "Update must preserve book position and components");
        helper.assertTrue(MagicData.getPlayerMagicData(mob).getMana() <= mob.getAttributeValue(MAX_MANA), "Downgrade must clamp current mana");
        rank(mob, 0);
        helper.assertTrue(book.isEmpty() && mob.getMainHandItem() == unrelated, "Removal must reclaim only tagged owned book");
        helper.assertFalse(MobMagicManager.isEnabled(mob), "Trait-owned runtime must stop on removal");
        helper.assertTrue(goals(mob) == 0 && mob.getAttributeValue(MAX_MANA) == 100 && mob.getAttributeValue(MANA_REGEN) == 1
                && mob.getAttributeValue(COOLDOWN_REDUCTION) == 1, "All trait modifiers and goal must be removed");
        rank(mob, 1);
        checkBook(helper, mob.getOffhandItem(), 1);
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void attributesAreAppliedOnceAndTraitTickHandlesDirectRankChanges(GameTestHelper helper) {
        Mob mob = mob(helper);
        var trait = ShHsTraits.WIZARD.get();
        var cap = LHMiscs.MOB.type().getOrCreate(mob);
        cap.traits.put(trait, 1);
        trait.initialize(mob, 1);
        var attribute = Objects.requireNonNull(mob.getAttribute(MAX_MANA));
        var modifier = attribute.getModifier(L2Hostility.loc("wizard_max_mana"));
        trait.postInit(mob, 1);
        cap.tick(mob);
        helper.assertTrue(attribute.getModifier(L2Hostility.loc("wizard_max_mana")) == modifier,
                "Post-init and steady trait tick must not replace initialized modifiers");

        cap.traits.put(trait, 3);
        cap.tick(mob);
        checkBook(helper, mob.getOffhandItem(), 3);
        helper.assertTrue(mob.getAttributeValue(MAX_MANA) == 100 + 3 * SERVER.wizardMaxManaPerLev.get(),
                "Trait tick must reconcile direct rank changes without an entity tick event");
        modifier = attribute.getModifier(L2Hostility.loc("wizard_max_mana"));
        cap.tick(mob);
        helper.assertTrue(attribute.getModifier(L2Hostility.loc("wizard_max_mana")) == modifier,
                "Stable rank must not rewrite attributes every tick");
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void traitTickMigratesLegacyDataWithoutImbuingEquipment(GameTestHelper helper) {
        Mob mob = mob(helper);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        mob.setItemSlot(EquipmentSlot.MAINHAND, sword);
        var cap = LHMiscs.MOB.type().getOrCreate(mob);
        cap.traits.put(ShHsTraits.WIZARD.get(), 2);
        cap.tick(mob);
        checkBook(helper, mob.getOffhandItem(), 2);
        helper.assertFalse(ISpellContainer.isSpellContainer(sword), "Legacy recovery must not imbue loaded equipment");
        helper.assertTrue(goals(mob) == 1 && MobMagicManager.isEnabled(mob)
                        && mob.getAttributeValue(MAX_MANA) == 100 + 2 * SERVER.wizardMaxManaPerLev.get(),
                "Trait tick must restore legacy attributes and casting");
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void directTraitClearIsReconciled(GameTestHelper helper) {
        Mob mob = mob(helper);
        rank(mob, 2);
        LHMiscs.MOB.type().getOrCreate(mob).traits.clear();
        Mob removed = mob(helper);
        rank(removed, 2);
        LHMiscs.MOB.type().getOrCreate(removed).removeTrait(ShHsTraits.WIZARD.get());
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(MobMagicManager.isEnabled(mob), "Direct trait clear must disable owned runtime");
            helper.assertTrue(mob.getOffhandItem().isEmpty() && goals(mob) == 0, "Direct trait clear must reclaim book and goal");
            helper.assertTrue(Objects.requireNonNull(mob.getAttribute(MAX_MANA)).getModifier(L2Hostility.loc("wizard_max_mana")) == null,
                    "Direct trait clear must remove modifier, not leave a zero-valued modifier");
            helper.assertTrue(!MobMagicManager.isEnabled(removed) && goals(removed) == 0 && removed.getOffhandItem().isEmpty()
                            && removed.getAttributeValue(MAX_MANA) == 100,
                    "Direct removeTrait must also clean up without a level-zero callback");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic")
    public static void externalRuntimeAndGoalRemainOwnedByCaller(GameTestHelper helper) {
        Mob mob = mob(helper);
        MobMagicManager.enable(mob);
        var external = new MobSpellCastGoal(mob);
        mob.goalSelector.addGoal(2, external);
        rank(mob, 2);
        helper.assertTrue(goals(mob) == 1, "Existing casting goal must be reused");
        rank(mob, 0);
        helper.assertTrue(MobMagicManager.isEnabled(mob) && goals(mob) == 1, "Trait must not disable external runtime or remove external goal");
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void nativeCastersAreNotTakenOver(GameTestHelper helper) {
        Mob mob = helper.spawn(EntityRegistry.CRYOMANCER.get(), new BlockPos(2, 2, 2));
        ItemStack before = mob.getOffhandItem().copy();
        ShHsTraits.WIZARD.get().initialize(mob, 3);
        ShHsTraits.WIZARD.get().postInit(mob, 3);
        helper.assertFalse(MobMagicManager.isEnabled(mob), "Native caster must stay outside mob spell runtime");
        helper.assertTrue(ItemStack.matches(before, mob.getOffhandItem()), "Native caster equipment must be preserved");
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void weightedGenerationIsBoundedAndDeterministic(GameTestHelper helper) {
        var root = SpellRegistry.ROOT_SPELL.get();
        var heal = SpellRegistry.HEAL_SPELL.get();
        var oak = SpellRegistry.OAKSKIN_SPELL.get();
        var fortify = SpellRegistry.FORTIFY_SPELL.get();
        var missile = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        var fireball = SpellRegistry.FIREBALL_SPELL.get();
        var teleport = SpellRegistry.TELEPORT_SPELL.get();
        var stomp = SpellRegistry.STOMP_SPELL.get();
        var summon = SpellRegistry.SUMMON_HORSE_SPELL.get();
        List<AbstractSpell> seed = List.of(root, heal, oak, fortify);
        helper.assertTrue(WizardTrait.comboWeight(seed, missile, 2) == 1 && WizardTrait.comboWeight(seed.subList(0, 3), missile, 5) == 1,
                "Low ranks and first four slots must use uniform weights");
        for (int rank = 3; rank <= 5; rank++)
            helper.assertTrue(WizardTrait.comboWeight(seed, missile, rank) == 1 + 2 * (rank - 2), "Missing control damage must get rank weight");
        helper.assertTrue(WizardTrait.comboWeight(List.of(root, missile, fireball, heal), oak, 5) == 1,
                "Unrelated utility must not receive combo weight");
        helper.assertTrue(WizardTrait.comboWeight(List.of(root, missile, fireball, heal), stomp, 5) == 1,
                "Completed control combo must not keep boosting damage");
        helper.assertTrue(WizardTrait.comboWeight(List.of(teleport, heal, oak, fortify), stomp, 5) == 7
                && WizardTrait.comboWeight(List.of(stomp, heal, oak, fortify), teleport, 5) == 7,
                "Approach combo must be completable in both generation directions");
        helper.assertTrue(WizardTrait.comboWeight(List.of(summon, heal, oak, fortify), missile, 5) == 7,
                "Summon combo must favor damage");
        List<AbstractSpell> pool = List.of(root, heal, oak, fortify, missile, fireball, teleport, stomp, summon);
        var chosen = WizardTrait.selectSpells(pool, 5, RandomSource.create(42));
        helper.assertTrue(chosen.size() == pool.size() && chosen.stream().distinct().count() == chosen.size(),
                "Small candidate pools must terminate without duplicates");
        helper.assertTrue(chosen.equals(WizardTrait.selectSpells(pool, 5, RandomSource.create(42))), "Fixed seed must reproduce selection");
        helper.assertTrue(WizardTrait.selectSpells(List.of(), 5, RandomSource.create(42)).isEmpty(), "Empty pool must terminate");
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void spellbookSlotsPrecedeOffhandAndGenericSlotsAreForbidden(GameTestHelper helper) {
        // Native mock wearer supplies Curios capability; the placement helper also serves mobs.
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        var inventory = CuriosApi.getCuriosInventory(wearer).orElseThrow();
        var bookSlots = new CurioStacksHandler(inventory, "spellbook", 2, true, true, true, ICurio.DropRule.DEFAULT);
        var genericSlots = new CurioStacksHandler(inventory, "curio", 1, true, true, true, ICurio.DropRule.DEFAULT);
        var slots = new HashMap<>(inventory.getCurios());
        slots.put("spellbook", bookSlots);
        slots.put("curio", genericSlots);
        inventory.setCurios(slots);
        ItemStack book = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
        bookSlots.getStacks().setStackInSlot(0, new ItemStack(Items.APPLE));
        bookSlots.getCosmeticStacks().setStackInSlot(1, book.copy());
        Objects.requireNonNull(WizardTrait.findBookSlot(wearer, book)).accept(book);
        helper.assertTrue(bookSlots.getStacks().getStackInSlot(1) == book && wearer.getOffhandItem().isEmpty(),
                "First empty functional book slot must win even when cosmetic slot is occupied");
        ItemStack generic = book.copy();
        helper.assertFalse(genericSlots.getStacks().isItemValid(0, generic), "Iron's native rule must reject books in generic slots");
        Objects.requireNonNull(WizardTrait.findBookSlot(wearer, generic)).accept(generic);
        helper.assertTrue(genericSlots.getStacks().getStackInSlot(0).isEmpty() && wearer.getOffhandItem() == generic,
                "Empty generic slot must be skipped in favor of offhand");
        bookSlots.getStacks().setStackInSlot(1, ItemStack.EMPTY);
        inventory.setSlotActive("spellbook", 1, false);
        wearer.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        ItemStack offhand = book.copy();
        Objects.requireNonNull(WizardTrait.findBookSlot(wearer, offhand)).accept(offhand);
        helper.assertTrue(wearer.getOffhandItem() == offhand, "Inactive book slot must fall back to offhand");
        helper.assertTrue(WizardTrait.findBookSlot(wearer, book.copy()) == null, "Full eligible slots must reject new book");
        helper.succeed();
    }

    @GameTest(template = "mob_magic")
    public static void saveLoadPreservesBookManaCooldownAndRestoresGoal(GameTestHelper helper) {
        Mob original = mob(helper);
        rank(original, 2);
        ItemStack book = original.getOffhandItem().copy();
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        var magic = MagicData.getPlayerMagicData(original);
        magic.setMana(17);
        magic.getPlayerCooldowns().addCooldown(spell, 100);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        Mob restored = Objects.requireNonNull(EntityType.PIG.create(helper.getLevel()));
        restored.load(saved);
        original.discard();
        helper.getLevel().addFreshEntity(restored);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(ItemStack.matches(book, restored.getOffhandItem()), "Reload must not regenerate book");
            helper.assertTrue(goals(restored) == 1 && MobMagicManager.isEnabled(restored), "Reload must reinstall casting goal once");
            helper.assertTrue(MagicData.getPlayerMagicData(restored).getMana() == 17
                    && MagicData.getPlayerMagicData(restored).getPlayerCooldowns().isOnCooldown(spell), "Reload must preserve mana and cooldown");
            var cap = LHMiscs.MOB.type().getOrCreate(restored);
            WizardTrait.Data data = cap.getData(ShHsTraits.WIZARD.get().getRegistryName());
            helper.assertTrue(data.equipmentProcessed && data.ownsCasting && data.level == 2, "L2 must persist wizard data");
            rank(restored, 0);
            helper.assertFalse(MobMagicManager.isEnabled(restored), "Reload must preserve ownership for later removal");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic")
    public static void wizardGoalDealsDamageAndPaysResources(GameTestHelper helper) {
        Mob mob = mob(helper);
        rank(mob, 2);
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        var container = ISpellContainer.create(8, true, false).mutableCopy();
        container.addSpell(spell, 4, false);
        ISpellContainer.set(mob.getOffhandItem(), container.toImmutable());
        Mob target = helper.spawn(EntityType.COW, new BlockPos(2, 2, 5));
        target.goalSelector.removeAllGoals(goal -> true);
        target.setNoGravity(true);
        mob.setTarget(target);
        float before = MagicData.getPlayerMagicData(mob).getMana();
        helper.runAfterDelay(15, () -> {
            var data = MagicData.getPlayerMagicData(mob);
            helper.assertTrue(data.getMana() < before, "Wizard goal must spend real mana");
            helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Wizard goal must settle cooldown");
            helper.assertTrue(target.getHealth() < target.getMaxHealth() || !helper.getLevel().getEntitiesOfClass(MagicMissileProjectile.class,
                    mob.getBoundingBox().inflate(8), missile -> missile.getOwner() == mob).isEmpty(), "Wizard goal must create a real spell effect");
            rank(mob, 0);
            helper.assertTrue(goals(mob) == 0 && !MobMagicManager.isEnabled(mob), "Removing trait must stop automatic casting");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic")
    public static void nativeAttributesAffectRegenAndCooldown(GameTestHelper helper) {
        Mob mob = mob(helper);
        rank(mob, 5);
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        MobMagicManager.addCooldown(mob, spell, CastSource.SPELLBOOK);
        var data = MagicData.getPlayerMagicData(mob);
        int duration = data.getPlayerCooldowns().saveNBTData().getCompound(0).getInt("scd");
        int expected = (int) (spell.getSpellCooldown() * (2 - Utils.softCapFormula(mob.getAttributeValue(COOLDOWN_REDUCTION))));
        helper.assertTrue(duration == expected && duration < spell.getSpellCooldown(), "Native cooldown attribute must shorten actual cooldown");
        data.setMana(0);
        mob.tickCount = 0;
        helper.runAfterDelay(11, () -> {
            float increment = (float) (mob.getAttributeValue(MAX_MANA) * mob.getAttributeValue(MANA_REGEN) * .01 * SERVER.mobManaRegenMultiplier.get());
            helper.assertTrue(Math.abs(data.getMana() - increment) < .01, "Native mana regen attribute must affect actual recovery");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic")
    public static void synchronizationDoesNotClearFailureStop(GameTestHelper helper) {
        Mob mob = mob(helper);
        rank(mob, 2);
        MobMagicManager.fail(mob, SpellRegistry.MAGIC_MISSILE_SPELL.get().getSpellId(),
                new IllegalStateException("Intentional wizard failure-stop test"));
        var data = LHMiscs.MOB.type().getOrCreate(mob).<WizardTrait.Data>getData(ShHsTraits.WIZARD.get().getRegistryName());
        mob.goalSelector.removeGoal(data.goal);
        data.goal = null;
        data.runtimeReady = false;
        helper.runAfterDelay(2, () -> {
            var goal = mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(MobSpellCastGoal.class::isInstance).findFirst().orElseThrow();
            helper.assertFalse(goal.canContinueToUse(), "Restoring wizard goal must not clear core failure stop");
            helper.succeed();
        });
    }
}
