package io.github.shrhang.shhs_create_core.api.registrate;

import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/**
 * 扩展 Registrate 的物品构建器，用于在注册物品时顺便声明 Create 风格 tooltip 的语言键。
 * <p>
 * 生成的键会基于物品描述 ID，例如 {@code item.modid.item_name.tooltip.summary}。
 * 运行时仍由 Create 的 {@code ItemDescription.Modifier} 读取和显示这些语言键。
 */
public class ShHsItemBuilder<T extends Item, P> extends ItemBuilder<T, P> {

    /**
     * 创建带默认模型和默认物品名语言生成器的 ShHs 物品构建器。
     *
     * @param owner    拥有该构建器的 Registrate 实例
     * @param parent   链式注册的父对象
     * @param name     注册名
     * @param callback Registrate 用于完成注册的回调
     * @param factory  物品工厂
     * @return 新的 ShHs 物品构建器
     */
    public static <T extends Item, P> ShHsItemBuilder<T, P> create(AbstractRegistrate<?> owner, P parent, String name,
                                                                  BuilderCallback callback,
                                                                  NonNullFunction<Item.Properties, T> factory) {
        return new ShHsItemBuilder<>(owner, parent, name, callback, factory)
                .defaultModel()
                .defaultLang();
    }

    protected ShHsItemBuilder(AbstractRegistrate<?> owner, P parent, String name, BuilderCallback callback,
                              NonNullFunction<Item.Properties, T> factory) {
        super(owner, parent, name, callback, factory);
    }

    /**
     * 添加 Create 风格 tooltip 的摘要文本。
     * <p>
     * 这是启用 Create 风格 tooltip 的必要键。Create 的 {@code ItemDescription.create(...)}
     * 会先检查 {@code .tooltip.summary} 是否存在；如果不存在，即使定义了 behaviour/action 也不会显示。
     * 文本中可用成对下划线标记高亮片段，例如 {@code "_高亮文本_ 普通文本"}。
     *
     * @param summary 按住 Shift 后显示的摘要文本
     * @return 当前构建器
     */
    public ShHsItemBuilder<T, P> tooltipSummary(String summary) {
        return tooltipLang("summary", summary);
    }

    /**
     * 添加 Create 风格 tooltip 的行为说明。
     * <p>
     * 会生成 {@code .tooltip.conditionN} 和 {@code .tooltip.behaviourN} 两个语言键。
     * Create 会从 1 开始连续读取编号，遇到第一个缺失的 {@code conditionN} 就停止；
     * 因此编号应当从 1 开始且不要跳号。
     *
     * @param index     行为编号，通常从 1 开始
     * @param condition 灰色条件标题，例如“右键点击时”
     * @param behaviour 条件下的具体行为说明，可用成对下划线标记高亮
     * @return 当前构建器
     */
    public ShHsItemBuilder<T, P> tooltipBehaviour(int index, String condition, String behaviour) {
        tooltipLang("condition" + index, condition);
        return tooltipLang("behaviour" + index, behaviour);
    }

    /**
     * 添加 Create 风格 tooltip 的控制说明。
     * <p>
     * 会生成 {@code .tooltip.controlN} 和 {@code .tooltip.actionN} 两个语言键。
     * 这些内容显示在按住 Ctrl 时的 tooltip 页面。Create 会从 1 开始连续读取编号，
     * 遇到第一个缺失的 {@code controlN} 就停止。
     *
     * @param index   控制说明编号，通常从 1 开始
     * @param control 灰色控制标题，例如“滚轮滚动时”
     * @param action  控制对应的具体行为说明，可用成对下划线标记高亮
     * @return 当前构建器
     */
    public ShHsItemBuilder<T, P> tooltipAction(int index, String control, String action) {
        tooltipLang("control" + index, control);
        return tooltipLang("action" + index, action);
    }

    private ShHsItemBuilder<T, P> tooltipLang(String suffix, String value) {
        addMiscData(ProviderType.LANG, provider -> {
            String key = getEntry().getDescriptionId() + ".tooltip." + suffix;
            provider.add(key, value);
        });
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> properties(NonNullUnaryOperator<Item.Properties> func) {
        super.properties(func);
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> initialProperties(NonNullSupplier<Item.Properties> properties) {
        super.initialProperties(properties);
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> tab(ResourceKey<CreativeModeTab> tab,
                                    NonNullBiConsumer<DataGenContext<Item, T>, com.tterrag.registrate.util.CreativeModeTabModifier> modifier) {
        super.tab(tab, modifier);
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> tab(ResourceKey<CreativeModeTab> tab) {
        super.tab(tab);
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> model(NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> cons) {
        super.model(cons);
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> defaultModel() {
        super.defaultModel();
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> defaultLang() {
        super.defaultLang();
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> lang(String name) {
        super.lang(name);
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> recipe(NonNullBiConsumer<DataGenContext<Item, T>, RegistrateRecipeProvider> cons) {
        super.recipe(cons);
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> burnTime(int tick) {
        super.burnTime(tick);
        return this;
    }

    @Override
    public ShHsItemBuilder<T, P> compostable(float chance) {
        super.compostable(chance);
        return this;
    }
}
