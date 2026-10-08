package io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast;

import io.github.shrhang.shhs_create_core.content.util.magic.SpellCastHelper.SpellSource;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MobCastState {
    boolean enabled;
    boolean initialized;
    boolean bound;
    boolean autoCastFailed;
    @Nullable ActiveCast active;
    @Nullable UUID recastTarget;
    @Nullable String pendingCooldown;
    CastSource pendingSource = CastSource.NONE;
    long nextAction;
    long nextDecision;
    long stageDeadline;
    int tagGeneration;
    MobSpellTactics.Tactic tactic = MobSpellTactics.Tactic.NONE;
    int stage;
    @Nullable UUID comboTarget;
    final Set<String> usedSpells = new HashSet<>();
    @Nullable String lastSpell;

    public static final IAttachmentSerializer<CompoundTag, MobCastState> SERIALIZER = new IAttachmentSerializer<>() {
        @Override
        public MobCastState read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            var state = new MobCastState();
            state.enabled = tag.getBoolean("enabled");
            state.initialized = tag.getBoolean("initialized");
            state.autoCastFailed = tag.getBoolean("auto_cast_failed");
            if (tag.contains("pending_cooldown")) {
                state.pendingCooldown = tag.getString("pending_cooldown");
                try { state.pendingSource = CastSource.valueOf(tag.getString("pending_source")); }
                catch (IllegalArgumentException ignored) { state.pendingSource = CastSource.SPELLBOOK; }
            }
            return state;
        }

        @Override
        public CompoundTag write(MobCastState state, HolderLookup.Provider provider) {
            var tag = new CompoundTag();
            tag.putBoolean("enabled", state.enabled);
            tag.putBoolean("initialized", state.initialized);
            tag.putBoolean("auto_cast_failed", state.autoCastFailed);
            if (state.pendingCooldown != null) {
                tag.putString("pending_cooldown", state.pendingCooldown);
                tag.putString("pending_source", state.pendingSource.name());
            }
            return tag;
        }
    };

    void clearCombo() {
        tactic = MobSpellTactics.Tactic.NONE;
        stage = 0;
        comboTarget = null;
        usedSpells.clear();
        stageDeadline = 0;
    }

    static class ActiveCast {
        final AbstractSpell spell;
        final int level;
        final CastSource source;
        @Nullable final SpellSource item;
        final ItemStack snapshot;
        @Nullable final UUID target;
        final boolean self;
        final boolean recast;
        boolean emitted;
        boolean cooldownApplied;
        int lastPulse = -1;

        ActiveCast(AbstractSpell spell, int level, CastSource source, @Nullable SpellSource item,
                   ItemStack snapshot, @Nullable UUID target, boolean self, boolean recast) {
            this.spell = spell;
            this.level = level;
            this.source = source;
            this.item = item;
            this.snapshot = snapshot;
            this.target = target;
            this.self = self;
            this.recast = recast;
        }
    }
}
