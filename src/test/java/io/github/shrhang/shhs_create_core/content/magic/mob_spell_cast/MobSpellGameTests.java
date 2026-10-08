package io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.api.events.SpellOnEntityCastEvent;
import io.github.shrhang.shhs_create_core.content.registries.ShHsAttachmentTypes;
import io.github.shrhang.shhs_create_core.mixin.irons_spellbooks.capabilities.magic.RecastInstanceAccessor;
import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import io.redspace.ironsspellbooks.registries.DataAttachmentRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.function.Consumer;

import static io.redspace.ironsspellbooks.api.registry.AttributeRegistry.*;

@GameTestHolder(ShHsCreateCore.MODID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = ShHsCreateCore.MODID)
public class MobSpellGameTests {
    @SubscribeEvent
    public static void prepareTemplate(ServerStartedEvent event) {
        if (!GameTestHooks.isGametestEnabled()) return;
        try {
            // Keep the fixture in memory instead of adding a binary structure asset.
            var template = event.getServer().getStructureManager().getOrCreate(ShHsCreateCore.rl("mob_magic"));
            template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseTag("{size:[12,6,12],palette:[{Name:'minecraft:air'}],blocks:[],entities:[]}"));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize mob spell test template", exception);
        }
    }

    private static Mob caster(GameTestHelper helper) {
        for (int x = 0; x < 12; x++)
            for (int z = 0; z < 12; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Mob mob = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.setNoGravity(true);
        Objects.requireNonNull(mob.getAttribute(MAX_MANA)).setBaseValue(1000);
        Objects.requireNonNull(mob.getAttribute(MANA_REGEN)).setBaseValue(0);
        Objects.requireNonNull(mob.getAttribute(CAST_TIME_REDUCTION)).setBaseValue(1);
        Mob target = helper.spawn(EntityType.COW, new BlockPos(2, 2, 5));
        target.goalSelector.removeAllGoals(goal -> true);
        target.targetSelector.removeAllGoals(goal -> true);
        Objects.requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(10000);
        target.setHealth(10000);
        target.setNoGravity(true);
        mob.setTarget(target);
        helper.assertTrue(MobMagicManager.enable(mob), "Caster must enable");
        return mob;
    }

    private static ItemStack equip(Mob mob, AbstractSpell spell, boolean scroll, int count) {
        ItemStack stack = new ItemStack(scroll ? ItemRegistry.SCROLL.get() : ItemRegistry.WIMPY_SPELL_BOOK.get(), count);
        if (scroll) ISpellContainer.createScrollContainer(spell, 1, stack);
        else setSpells(stack, false, spell);
        mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
        return stack;
    }

    private static void setSpells(ItemStack stack, boolean mustEquip, AbstractSpell... spells) {
        var container = ISpellContainer.create(spells.length, true, mustEquip).mutableCopy();
        for (var spell : spells) container.addSpell(spell, 1, true);
        ISpellContainer.set(stack, container.toImmutable());
    }

    private static Mob target(Mob mob) {
        return (Mob) Objects.requireNonNull(mob.getTarget(), "Fixture target must remain present");
    }

    private static void cast(GameTestHelper helper, Mob mob) {
        var sources = MobMagicManager.getAvailableSources(mob);
        helper.assertTrue(sources.size() == 1, "Exactly one spell source expected");
        helper.assertTrue(MobMagicManager.tryCast(mob, sources.getFirst())
                == MobMagicManager.CastAttemptResult.STARTED, "Cast should start");
    }

    private static int missiles(GameTestHelper helper, Mob mob) {
        return helper.getLevel().getEntitiesOfClass(MagicMissileProjectile.class,
                mob.getBoundingBox().inflate(8), p -> p.getOwner() == mob).size();
    }

    private static void restore(Mob mob) {
        CompoundTag saved = new CompoundTag();
        MagicData.getPlayerMagicData(mob).saveNBTData(saved, mob.registryAccess());
        MagicData loaded = new MagicData(true);
        loaded.loadNBTData(saved, mob.registryAccess());
        mob.setData(DataAttachmentRegistry.MAGIC_DATA, loaded);
        var stateTag = MobCastState.SERIALIZER.write(MobMagicManager.state(mob), mob.registryAccess());
        if (stateTag != null) {
            mob.setData(ShHsAttachmentTypes.MOB_CAST_STATE,
                    MobCastState.SERIALIZER.read(mob, stateTag, mob.registryAccess()));
        }
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void instantBookPaysOnce(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        equip(mob, spell, false, 1);
        var data = MagicData.getPlayerMagicData(mob);
        data.setMana(100);
        cast(helper, mob);
        helper.assertTrue(missiles(helper, mob) == 1, "One actual projectile must be created");
        helper.assertTrue(data.getMana() == 100 - spell.getManaCost(1), "Mana paid exactly once");
        helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Book spell must have cooldown");
        helper.assertFalse(MobMagicManager.isCasting(mob), "Instant cast must finish");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void scrollBypassesManaAndCooldown(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        ItemStack stack = equip(mob, spell, true, 2);
        var data = MagicData.getPlayerMagicData(mob);
        data.setMana(0);
        data.getPlayerCooldowns().addCooldown(spell, 100);
        cast(helper, mob);
        helper.assertTrue(missiles(helper, mob) == 1, "Scroll must produce actual projectile");
        helper.assertTrue(stack.getCount() == 1, "Exactly one scroll consumed");
        helper.assertTrue(data.getMana() == 0, "Scroll must not spend mana");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void cancelledChantKeepsScroll(GameTestHelper helper) {
        Mob mob = caster(helper);
        ItemStack stack = equip(mob, SpellRegistry.FIREBALL_SPELL.get(), true, 1);
        cast(helper, mob);
        MobMagicManager.cancelCast(mob);
        helper.assertTrue(stack.getCount() == 1, "Cancel before release must keep scroll");
        helper.assertFalse(MobMagicManager.isCasting(mob), "Cancel must finish the cast");
        helper.assertFalse(MagicData.getPlayerMagicData(mob).getPlayerCooldowns()
                .isOnCooldown(SpellRegistry.FIREBALL_SPELL.get()), "Unreleased spell must not get cooldown");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void modifiedCostCannotCreateFreeEffect(GameTestHelper helper) {
        Mob mob = caster(helper);
        equip(mob, SpellRegistry.MAGIC_MISSILE_SPELL.get(), false, 1);
        var data = MagicData.getPlayerMagicData(mob);
        data.setMana(20);
        Consumer<SpellOnEntityCastEvent> listener = event -> {
            if (event.getEntity() == mob) event.setManaCost(200);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            cast(helper, mob);
            helper.assertTrue(missiles(helper, mob) == 0, "Unaffordable modified cost must prevent projectile");
            helper.assertTrue(data.getMana() == 20, "Failed release must preserve mana");
            helper.assertFalse(MobMagicManager.isCasting(mob), "Failed release must clean state");
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void continuousScrollPaysOnceAndStops(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.FIRE_BREATH_SPELL.get();
        ItemStack stack = equip(mob, spell, true, 1);
        Objects.requireNonNull(mob.getAttribute(CAST_TIME_REDUCTION)).setBaseValue(.2);
        MagicData.getPlayerMagicData(mob).setMana(0);
        AtomicInteger pulses = new AtomicInteger();
        Consumer<SpellOnEntityCastEvent> listener = event -> {
            if (event.getEntity() == mob) pulses.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(listener);
        cast(helper, mob);
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(stack.isEmpty(), "Last scroll must be consumed");
                helper.assertTrue(pulses.get() == 2, "20 tick channel must execute exactly two pulses");
                helper.assertFalse(MobMagicManager.isCasting(mob), "Channel must finish without replaying final pulse");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void continuousManaExhaustionStopsEarly(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.FIRE_BREATH_SPELL.get();
        equip(mob, spell, false, 1);
        var data = MagicData.getPlayerMagicData(mob);
        data.setMana(spell.getManaCost(1));
        cast(helper, mob);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(data.getMana() == 0, "Only affordable pulse may spend mana");
            helper.assertFalse(MobMagicManager.isCasting(mob), "No free pulses after exhaustion");
            helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Executed channel needs cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void allRecastsExecuteWithOneManaPayment(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.ELDRITCH_BLAST_SPELL.get();
        equip(mob, spell, false, 1);
        var data = MagicData.getPlayerMagicData(mob);
        data.setMana(spell.getManaCost(1));
        AtomicInteger effects = new AtomicInteger();
        Consumer<SpellOnEntityCastEvent> listener = event -> {
            if (event.getEntity() == mob) effects.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(listener);
        cast(helper, mob);
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(effects.get() == spell.getRecastCount(1, mob), "All configured casts must execute");
                helper.assertTrue(data.getMana() == 0, "Recasts must not repay initial mana");
                helper.assertFalse(data.getPlayerRecasts().hasRecastForSpell(spell), "Recasts must be exhausted");
                helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Finished group gets cooldown");
                helper.assertTrue(target(mob).getHealth() < target(mob).getMaxHealth(),
                        "Native spell effects must damage target");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void sourceRemovalCancelsUnreleasedSpell(GameTestHelper helper) {
        Mob mob = caster(helper);
        equip(mob, SpellRegistry.FIREBALL_SPELL.get(), false, 1);
        var data = MagicData.getPlayerMagicData(mob);
        data.setMana(100);
        cast(helper, mob);
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(MobMagicManager.isCasting(mob), "Removed source must cancel chant");
            helper.assertTrue(data.getMana() == 100, "Unreleased chant must not pay mana");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void restorePreservesManaAndCooldown(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        var data = MagicData.getPlayerMagicData(mob);
        data.setMana(27);
        data.getPlayerCooldowns().addCooldown(spell, 100);
        restore(mob);
        helper.runAfterDelay(2, () -> {
            var restored = MagicData.getPlayerMagicData(mob);
            helper.assertTrue(restored.getMana() == 27, "Rebind must not refill mana");
            helper.assertTrue(restored.getPlayerCooldowns().isOnCooldown(spell), "Cooldown survives rebind");
            helper.assertFalse(MobMagicManager.isCasting(mob), "No orphaned cast restored");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void wallRecastsCreateRealWall(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.WALL_OF_FIRE_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(WallOfFireEntity.class,
                    mob.getBoundingBox().inflate(24)).size() == 1, "Completed anchors must produce a real fire wall");
            helper.assertFalse(MagicData.getPlayerMagicData(mob).getPlayerRecasts().hasRecastForSpell(spell),
                    "Wall recasts must finish");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void summonSurvivesCounterspellButNotDisable(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.SUMMON_HORSE_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        helper.runAfterDelay(25, () -> {
            var data = MagicData.getPlayerMagicData(mob);
            var summons = SummonManager.getSummons(mob);
            helper.assertTrue(summons.size() == 1, "Summon must remain instead of auto-dismissing");
            helper.assertTrue(data.getPlayerRecasts().hasRecastForSpell(spell), "Summon batch must remain active");
            NeoForge.EVENT_BUS.post(new CounterSpellEvent(mob.getTarget(), mob));
            helper.assertTrue(data.getPlayerRecasts().hasRecastForSpell(spell), "Counterspell preserves summon batch");
            helper.assertTrue(MobMagicManager.tryCast(mob, MobMagicManager.getAvailableSources(mob).getFirst())
                    == MobMagicManager.CastAttemptResult.BUSY, "Active batch prevents duplicate summon");
            MobMagicManager.disable(mob);
            helper.assertTrue(SummonManager.getSummons(mob).isEmpty(), "Disable must detach summoned entities");
            helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Dismissal must settle cooldown");
            helper.assertFalse(MobMagicManager.isEnabled(mob), "Disable must stop runtime");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void lastSummonDeathFinishesBatch(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.SUMMON_HORSE_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        helper.runAfterDelay(25, () -> {
            var summons = SummonManager.getSummons(mob);
            helper.assertTrue(summons.size() == 1, "A real summon must exist");
            Objects.requireNonNull(helper.getLevel().getEntity(summons.iterator().next())).kill();
        });
        helper.runAfterDelay(60, () -> {
            var data = MagicData.getPlayerMagicData(mob);
            helper.assertFalse(data.getPlayerRecasts().hasRecastForSpell(spell), "Last summon death must close batch");
            helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Last summon death needs cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 100)
    public static void goalAlternatesControlAndTwoAttacks(GameTestHelper helper) {
        Mob mob = caster(helper);
        ItemStack book = equip(mob, SpellRegistry.ROOT_SPELL.get(), false, 1);
        setSpells(book, false, SpellRegistry.ROOT_SPELL.get(), SpellRegistry.MAGIC_MISSILE_SPELL.get(),
                SpellRegistry.RAY_OF_FROST_SPELL.get());
        List<String> effects = new ArrayList<>();
        Consumer<SpellOnEntityCastEvent> listener = event -> {
            if (event.getEntity() == mob) effects.add(event.getSpellId());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        mob.goalSelector.addGoal(0, new MobSpellCastGoal(mob));
        helper.runAfterDelay(75, () -> {
            try {
                helper.assertTrue(effects.size() == 3, "One complete three-step combo expected: " + effects);
                helper.assertTrue(effects.getFirst().equals(SpellRegistry.ROOT_SPELL.get().getSpellId()),
                        "Control must precede damage");
                helper.assertTrue(effects.stream().distinct().count() == 3, "Combo must alternate different spells");
                helper.assertTrue(target(mob).getHealth() < target(mob).getMaxHealth(), "Combo must deal real damage");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void zeroManaGoalFallsBackToOffhandScroll(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        var scroll = equip(mob, spell, true, 1);
        mob.setItemSlot(EquipmentSlot.OFFHAND, scroll);
        equip(mob, spell, false, 1);
        MagicData.getPlayerMagicData(mob).setMana(0);
        mob.goalSelector.addGoal(0, new MobSpellCastGoal(mob));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(scroll.isEmpty(), "Unaffordable book must fall back to offhand scroll");
            helper.assertTrue(MagicData.getPlayerMagicData(mob).getMana() == 0, "Scroll fallback must remain mana-free");
            helper.assertTrue(target(mob).getHealth() < target(mob).getMaxHealth(), "Fallback must execute real spell");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void mustEquipAndScrollLocationsAreEnforced(GameTestHelper helper) {
        Mob mob = caster(helper);
        ItemStack equipment = new ItemStack(Items.DIAMOND_HELMET);
        setSpells(equipment, true, SpellRegistry.MAGIC_MISSILE_SPELL.get());
        mob.setItemSlot(EquipmentSlot.MAINHAND, equipment);
        helper.assertTrue(MobMagicManager.getAvailableSources(mob).isEmpty(), "mustEquip equipment cannot activate in hand");
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.HEAD, equipment);
        var sources = MobMagicManager.getAvailableSources(mob);
        helper.assertTrue(sources.size() == 1 && sources.getFirst().castSource() == CastSource.SWORD,
                "Worn equipment must expose sword source");
        var scroll = equip(mob, SpellRegistry.MAGIC_MISSILE_SPELL.get(), true, 1);
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.HEAD, scroll);
        helper.assertTrue(MobMagicManager.getAvailableSources(mob).isEmpty(), "Scroll outside hands must not activate");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void counterspellBeforeReleasePreservesScroll(GameTestHelper helper) {
        Mob mob = caster(helper);
        var stack = equip(mob, SpellRegistry.FIREBALL_SPELL.get(), true, 1);
        cast(helper, mob);
        NeoForge.EVENT_BUS.post(new CounterSpellEvent(mob.getTarget(), mob));
        helper.assertFalse(MobMagicManager.isCasting(mob), "Counterspell must cancel chant");
        helper.assertTrue(stack.getCount() == 1, "Unreleased countered scroll must be preserved");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void restoreSettlesExecutedChannelCooldown(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.FIRE_BREATH_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(Objects.requireNonNull(MobMagicManager.state(mob).active).emitted, "Channel must emit before saving");
            restore(mob);
        });
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(MobMagicManager.isCasting(mob), "Reload must not resume partial channel");
            helper.assertTrue(MagicData.getPlayerMagicData(mob).getPlayerCooldowns().isOnCooldown(spell),
                    "Executed channel's pending cooldown must survive reload");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void curiosUsesFunctionalActiveSlotsOnly(GameTestHelper helper) {
        // Vanilla pigs have no Curios entity slots; use a native wearer to test the shared scanner.
        var wearer = helper.makeMockPlayer(GameType.SURVIVAL);
        var inventory = CuriosApi.getCuriosInventory(wearer).orElseThrow();
        var handler = new CurioStacksHandler(inventory, "spellbook", 1, true, true, true, ICurio.DropRule.DEFAULT);
        var slots = new HashMap<>(inventory.getCurios());
        slots.put("spellbook", handler);
        inventory.setCurios(slots);
        var book = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
        setSpells(book, false, SpellRegistry.MAGIC_MISSILE_SPELL.get());
        handler.getCosmeticStacks().setStackInSlot(0, book.copy());
        helper.assertTrue(SpellCastHelper.getEntitySpells(wearer).isEmpty(), "Cosmetic book must not expose spells");
        handler.getStacks().setStackInSlot(0, book);
        var sources = SpellCastHelper.getEntitySpells(wearer);
        helper.assertTrue(sources.size() == 1 && "spellbook".equals(sources.getFirst().curiosSlot()),
                "Functional Curios book must expose spell source");
        var source = sources.getFirst();
        inventory.setSlotActive("spellbook", 0, false);
        helper.assertTrue(SpellCastHelper.getEntitySpells(wearer).isEmpty(), "Inactive Curios slot must not expose spells");
        helper.assertFalse(source.isValid(wearer, book.copy()), "Inactive source must invalidate in-flight cast");
        inventory.setSlotActive("spellbook", 0, true);
        helper.assertTrue(source.isValid(wearer, book.copy()) && source.stack(wearer) == book,
                "Active source must resolve the original functional item");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void equipmentHonorsManaConfig(GameTestHelper helper) {
        boolean original = ServerConfigs.SWORDS_CONSUME_MANA.get();
        try {
            for (boolean consumes : List.of(false, true)) {
                ServerConfigs.SWORDS_CONSUME_MANA.set(consumes);
                Mob mob = caster(helper);
                var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
                var weapon = new ItemStack(Items.DIAMOND_SWORD);
                setSpells(weapon, false, spell);
                mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                var data = MagicData.getPlayerMagicData(mob);
                data.setMana(100);
                cast(helper, mob);
                helper.assertTrue(data.getMana() == 100 - (consumes ? spell.getManaCost(1) : 0),
                        "Weapon mana must honor native config: " + consumes);
                helper.assertTrue(missiles(helper, mob) == 1, "Weapon must create one projectile");
            }
            helper.succeed();
        } finally { ServerConfigs.SWORDS_CONSUME_MANA.set(original); }
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void cooldownIsSharedAcrossEquipmentSources(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        helper.runAfterDelay(6, () -> {
            var weapon = new ItemStack(Items.DIAMOND_SWORD);
            setSpells(weapon, false, spell);
            mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            helper.assertTrue(MobMagicManager.tryCast(mob, MobMagicManager.getAvailableSources(mob).getFirst())
                    == MobMagicManager.CastAttemptResult.ON_COOLDOWN, "Switching source must not bypass cooldown");
            equip(mob, spell, true, 1);
            cast(helper, mob);
            helper.assertTrue(mob.getMainHandItem().isEmpty(), "Scroll may bypass shared cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void offensiveRecastsBlockFirstCastsAndTimeout(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.ELDRITCH_BLAST_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        equip(mob, SpellRegistry.MAGIC_MISSILE_SPELL.get(), false, 1);
        helper.assertTrue(MobMagicManager.tryCast(mob, MobMagicManager.getAvailableSources(mob).getFirst())
                == MobMagicManager.CastAttemptResult.BUSY, "Offensive recast group must own cast sequencing");
        var recast = MagicData.getPlayerMagicData(mob).getPlayerRecasts().getRecastInstance(spell.getSpellId());
        ((RecastInstanceAccessor) recast).shhs$setRemainingTicks(2);
        helper.runAfterDelay(3, () -> {
            var data = MagicData.getPlayerMagicData(mob);
            helper.assertFalse(data.getPlayerRecasts().hasRecastForSpell(spell), "Mob recast timer must expire");
            helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Timeout must settle cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void restoredOffensiveRecastsDoNotReplay(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.ELDRITCH_BLAST_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        float mana = MagicData.getPlayerMagicData(mob).getMana();
        restore(mob);
        helper.runAfterDelay(3, () -> {
            var data = MagicData.getPlayerMagicData(mob);
            helper.assertFalse(data.getPlayerRecasts().hasRecastForSpell(spell), "Reload must terminate offensive group");
            helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Restored group must settle cooldown");
            helper.assertTrue(data.getMana() == mana, "Rebind must not refund or repay initial mana");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void damageInterruptsUnreleasedChant(GameTestHelper helper) {
        Mob mob = caster(helper);
        var stack = equip(mob, SpellRegistry.FIREBALL_SPELL.get(), true, 1);
        cast(helper, mob);
        mob.hurt(mob.damageSources().generic(), 1);
        helper.assertFalse(MobMagicManager.isCasting(mob), "Real damage must interrupt long chant");
        helper.assertTrue(stack.getCount() == 1, "Damage before release must preserve scroll");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void spellComponentChangeCancelsChant(GameTestHelper helper) {
        Mob mob = caster(helper);
        var book = equip(mob, SpellRegistry.FIREBALL_SPELL.get(), false, 1);
        cast(helper, mob);
        setSpells(book, false, SpellRegistry.MAGIC_MISSILE_SPELL.get());
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(MobMagicManager.isCasting(mob), "Mutated spell component must invalidate source snapshot");
            helper.assertFalse(MagicData.getPlayerMagicData(mob).getPlayerCooldowns()
                    .isOnCooldown(SpellRegistry.FIREBALL_SPELL.get()), "Unreleased mutated source needs no cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void goalPreemptionCancelsChant(GameTestHelper helper) {
        Mob mob = caster(helper);
        var stack = equip(mob, SpellRegistry.FIREBALL_SPELL.get(), true, 1);
        var goal = new MobSpellCastGoal(mob);
        cast(helper, mob);
        goal.stop();
        helper.assertFalse(MobMagicManager.isCasting(mob), "Goal stop must cancel active cast");
        helper.assertTrue(stack.getCount() == 1, "Preempted unreleased scroll must remain");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void leaveEndsChannelAndPreservesOptIn(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.FIRE_BREATH_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        helper.runAfterDelay(2, () -> {
            NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(mob, mob.level()));
            helper.assertFalse(MobMagicManager.isCasting(mob), "Leaving level must finish channel callbacks");
            helper.assertTrue(MobMagicManager.isEnabled(mob), "Unloading must preserve opt-in");
            helper.assertTrue(MagicData.getPlayerMagicData(mob).getPlayerCooldowns().isOnCooldown(spell),
                    "Released channel must settle cooldown on leave");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void goalChoosesAffordableLowerLevel(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
        var lowBook = equip(mob, spell, false, 1);
        mob.setItemSlot(EquipmentSlot.OFFHAND, lowBook);
        var highBook = equip(mob, spell, false, 1);
        var container = ISpellContainer.create(1, true, false).mutableCopy();
        container.addSpell(spell, spell.getMaxLevel(), true);
        ISpellContainer.set(highBook, container.toImmutable());
        var data = MagicData.getPlayerMagicData(mob);
        data.setMana(spell.getManaCost(1));
        AtomicInteger level = new AtomicInteger();
        Consumer<SpellOnEntityCastEvent> listener = event -> {
            if (event.getEntity() == mob) level.set(event.getSpellLevel());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        mob.goalSelector.addGoal(0, new MobSpellCastGoal(mob));
        helper.runAfterDelay(15, () -> {
            try {
                helper.assertTrue(level.get() == 1, "Unaffordable high level must not hide lower-level source");
                helper.assertTrue(data.getMana() == 0, "Affordable source must pay its actual level cost");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void summonOwnerDeathClearsBatch(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.SUMMON_HORSE_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(SummonManager.getSummons(mob).size() == 1, "Summon must exist before owner death");
            mob.hurt(mob.damageSources().generic(), 1000);
            helper.assertTrue(SummonManager.getSummons(mob).isEmpty(), "Owner death must detach loaded summons");
            helper.assertFalse(MobMagicManager.isEnabled(mob), "Dead owner must disable runtime");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void selfHealDoesNotRequireEnemy(GameTestHelper helper) {
        Mob mob = caster(helper);
        mob.setTarget(null);
        mob.setHealth(2);
        equip(mob, SpellRegistry.HEAL_SPELL.get(), false, 1);
        cast(helper, mob);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(mob.getHealth() > 2, "Self heal must really restore caster health without enemy");
            helper.assertFalse(MobMagicManager.isCasting(mob), "Heal must complete");
            helper.succeed();
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void approachRechecksRangeBeforeCloseAttack(GameTestHelper helper) {
        Mob mob = caster(helper);
        target(mob).setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 9.5)));
        ItemStack book = equip(mob, SpellRegistry.TELEPORT_SPELL.get(), false, 1);
        setSpells(book, false, SpellRegistry.TELEPORT_SPELL.get(), SpellRegistry.STOMP_SPELL.get());
        List<String> effects = new ArrayList<>();
        Consumer<SpellOnEntityCastEvent> listener = event -> {
            if (event.getEntity() == mob) {
                if (event.getSpellId().equals(SpellRegistry.STOMP_SPELL.get().getSpellId())) {
                    helper.assertTrue(mob.distanceToSqr(target(mob)) <= 36, "Close spell must recheck distance");
                }
                effects.add(event.getSpellId());
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        mob.goalSelector.addGoal(0, new MobSpellCastGoal(mob));
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(effects.equals(List.of(SpellRegistry.TELEPORT_SPELL.get().getSpellId(),
                        SpellRegistry.STOMP_SPELL.get().getSpellId())), "Approach must precede close attack: " + effects);
                helper.assertTrue(mob.distanceToSqr(target(mob)) <= 36, "Teleport must actually approach enemy");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }

    @GameTest(template = "mob_magic", timeoutTicks = 60)
    public static void nativeCounterspellCanHitEnabledMob(GameTestHelper helper) {
        Mob mob = caster(helper);
        var scroll = equip(mob, SpellRegistry.FIREBALL_SPELL.get(), true, 1);
        cast(helper, mob);
        Mob counterCaster = target(mob);
        counterCaster.setTarget(mob);
        helper.assertTrue(MobMagicManager.enable(counterCaster), "Counter caster must enable");
        MagicData.getPlayerMagicData(counterCaster).setMana(100);
        equip(counterCaster, SpellRegistry.COUNTERSPELL_SPELL.get(), false, 1);
        cast(helper, counterCaster);
        helper.assertFalse(MobMagicManager.isCasting(mob), "Native raycast counterspell must hit ordinary enabled mob");
        helper.assertTrue(scroll.getCount() == 1, "Native counterspell before release must preserve scroll");
        helper.succeed();
    }

    @GameTest(template = "mob_magic", timeoutTicks = 80)
    public static void callbackFailureTerminatesOffensiveGroup(GameTestHelper helper) {
        Mob mob = caster(helper);
        var spell = SpellRegistry.ELDRITCH_BLAST_SPELL.get();
        equip(mob, spell, false, 1);
        cast(helper, mob);
        AtomicInteger attempts = new AtomicInteger();
        Consumer<SpellOnEntityCastEvent> listener = event -> {
            if (event.getEntity() == mob) {
                attempts.incrementAndGet();
                throw new IllegalStateException("Intentional mob spell GameTest callback failure");
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        helper.runAfterDelay(15, () -> {
            try {
                var data = MagicData.getPlayerMagicData(mob);
                helper.assertTrue(attempts.get() == 1, "Failed callback must not be retried");
                helper.assertTrue(MobMagicManager.state(mob).autoCastFailed, "Callback error must stop automatic casting");
                helper.assertFalse(MobMagicManager.isCasting(mob), "Failed cast must release runtime state");
                helper.assertFalse(data.getPlayerRecasts().hasRecastForSpell(spell), "Failed group must terminate, not timeout normally");
                helper.assertTrue(data.getPlayerCooldowns().isOnCooldown(spell), "Failed already-executed group needs cooldown");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); }
        });
    }
}
