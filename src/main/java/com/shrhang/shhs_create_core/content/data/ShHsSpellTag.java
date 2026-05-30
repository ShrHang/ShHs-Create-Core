package com.shrhang.shhs_create_core.content.data;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.function.Supplier;

import static io.redspace.ironsspellbooks.api.registry.SpellRegistry.*;

public record ShHsSpellTag(TagKey<AbstractSpell> tag) {
    public static ShHsSpellTag create(ResourceLocation tag) {
        return new ShHsSpellTag(TagKey.create(SPELL_REGISTRY_KEY, tag));
    }

    @SafeVarargs
    public final ShHsSpellTag addSpell(Supplier<AbstractSpell>... spells) {
        ShHsTagKey.SPELL_TAG_BUILDER.put(tag.location(), provider -> {
            TagsProvider.TagAppender<AbstractSpell> appender = provider.addTag(tag);
            for (Supplier<AbstractSpell> spell : spells) {
                appender.addOptional(spell.get().getSpellResource());
            }
        });
        return this;
    }

    public boolean contains(AbstractSpell spell) {
        return REGISTRY.wrapAsHolder(spell).is(tag);
    }
}
