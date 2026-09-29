package io.github.shrhang.shhs_create_core.content.data;

import io.github.shrhang.shhs_create_core.content.registries.ShHsKeys;
import io.github.shrhang.shhs_create_core.content.ponder.ShHsPonderPlugin;
import com.tterrag.registrate.providers.ProviderType;
import joptsimple.internal.Strings;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.function.BiConsumer;

import static io.github.shrhang.shhs_create_core.ShHsCreateCore.MODID;
import static io.github.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;
import static net.createmod.catnip.lang.LangBuilder.DEFAULT_SPACE_WIDTH;

public class ShHsLang {

    public static final String CATEGORY_KEY = "key.categories." + MODID;

    public static String key(String type, String key) {
        return type + "." + MODID + "." + key;
    }
    
    public static MutableComponent component(String type, String key, Object... args) {
        return Component.translatable(key(type, key), args);
    }

    public static MutableComponent textComponent(String key, Object... args) {
        return component("text", key, args);
    }

    public static MutableComponent titleComponent(String key, Object... args) {
        return component("title", key, args);
    }


    public static MutableComponent tooltipComponent(String key, Object... args) {
        return component("tooltip", key, args);
    }

    public static MutableComponent tooltipComponentForGoggles(String key, Object... args) {
        return Component.literal(Strings.repeat(' ', getIndents(Minecraft.getInstance().font))).append(tooltipComponent(key, args));
    }

    private static int getIndents(Font font) {
        int spaceWidth = font.width(" ");
        if (DEFAULT_SPACE_WIDTH == spaceWidth) {
            return 4;
        }
        return Mth.ceil(DEFAULT_SPACE_WIDTH * 4 / spaceWidth);
    }

    public static void init() {
        REGISTRATE.addRawLang(key("text", "terminal.title"), "Dimension Logistics Terminal");
        REGISTRATE.addRawLang(key("text", "terminal.search"), "Search inventory");
        REGISTRATE.addRawLang(key("text", "terminal.collect"), "Request items");
        REGISTRATE.addRawLang(key("text", "terminal.orders"), "My orders");
        REGISTRATE.addRawLang(key("text", "terminal.stock"), "Inventory");
        REGISTRATE.addRawLang(key("text", "terminal.switch"), "Switch");
        REGISTRATE.addRawLang(key("text", "terminal.claim"), "Claim packages");
        REGISTRATE.addRawLang(key("text", "terminal.end"), "End wait & claim");
        REGISTRATE.addRawLang(key("text", "terminal.clear_craft"), "Clear crafting grid");
        REGISTRATE.addRawLang(key("text", "terminal.basket"), "Selection · scroll to browse");
        REGISTRATE.addRawLang(key("text", "terminal.no_network"), "No accessible primary network");
        REGISTRATE.addRawLang(key("text", "terminal.deposit_hint"), "Shift-click inventory to deposit");
        REGISTRATE.addRawLang(key("text", "terminal.no_orders"), "No pending orders");
        REGISTRATE.addRawLang(key("text", "terminal.ready"), "Ready to collect");
        REGISTRATE.addRawLang(key("text", "terminal.waiting"), "Waiting for deliveries");
        REGISTRATE.addRawLang(key("text", "terminal.progress"), "Held: %s · Packages: %s");
        REGISTRATE.addRawLang(key("text", "terminal.missing"), "Still missing:");
        REGISTRATE.addRawLang(key("text", "terminal.amount"), "Amount: %s");
        REGISTRATE.addRawLang(key("text", "terminal.local"), "Source: dimension network");
        REGISTRATE.addRawLang(key("text", "terminal.external"), "Source: logistics network %s");
        REGISTRATE.addRawLang(key("text", "terminal.dimension_category"), "Dimension network #%s");
        REGISTRATE.addRawLang(key("text", "terminal.storage_category"), "Logistics network %s");
        REGISTRATE.addRawLang(key("text", "terminal.storage_category_address"), "Logistics network %s · %s");
        REGISTRATE.addRawLang(key("text", "terminal.address_value"), "Receiving address: %s");
        REGISTRATE.addRawLang(key("text", "terminal.unavailable"), "Route unavailable or address unset");
        REGISTRATE.addRawLang(key("text", "terminal.missing_materials"), "Missing materials in your inventory and dimension network.");
        REGISTRATE.addRawLang(key("text", "terminal.submit_failed"), "Unable to submit: check stock, route, permissions and packager capacity.");
        REGISTRATE.addRawLang(key("text", "terminal.address"), "Receiving address");
        REGISTRATE.addRawLang(key("text", "terminal.save"), "Save");
        REGISTRATE.addRawLang(key("text", "terminal.no_routes"), "No connected logistics networks");
        REGISTRATE.addRawLang(key("text", "terminal.route"), "Network %s/%s · %s");
        REGISTRATE.addRawLang(key("text", "terminal.address_hint"), "Exact address; no * or ? wildcards.");
        REGISTRATE.addRawLang(key("text", "terminal.order_status"), "%s/%s · %s · %s pkg");
        REGISTRATE.addRawLang(key("text", "terminal.ready_short"), "Ready");
        REGISTRATE.addRawLang(key("text", "terminal.waiting_short"), "Waiting");
        REGISTRATE.addRawLang(key("text", "terminal.delivery_progress"), "Delivered: %s/%s");
        REGISTRATE.addRawLang(key("text", "terminal.show_orders"), "Show order module");
        REGISTRATE.addRawLang(key("text", "terminal.hide_orders"), "Hide order module");
        REGISTRATE.addRawLang(key("text", "terminal.show_crafting"), "Show crafting module");
        REGISTRATE.addRawLang(key("text", "terminal.hide_crafting"), "Hide crafting module");
        REGISTRATE.addRawLang(key("text", "terminal.crafting_hidden"), "Show the crafting module before transferring a recipe.");
        REGISTRATE.addRawLang(key("text", "terminal.cancel_current"), "Cancel current order and claim delivered items");
        REGISTRATE.addRawLang(key("text", "terminal.previous_order"), "Previous order");
        REGISTRATE.addRawLang(key("text", "terminal.next_order"), "Next order");
        REGISTRATE.addRawLang(key("text", "terminal.claim_ready"), "Claim all ready orders");
        REGISTRATE.addRawLang(key("text", "terminal.cancel_incomplete"), "Cancel all incomplete orders and claim delivered items");
        REGISTRATE.addRawLang(key("text","no_enough_spell_tolerance"), "At least %s Spell Tolerance is required to cast this spell.");
        REGISTRATE.addRawLang(key("text","need_tolerance"), "Need Spell Tolerance: %s");
        REGISTRATE.addRawLang(key("text","empty_trait_no_target"), "No valid target in sight.");
        REGISTRATE.addRawLang(key("text","empty_trait_no_traits"), "No traits can be extracted from this target.");

        REGISTRATE.addRawLang(key("tooltip", "hostility_debit_card.dimensions"), "Visited dimensions: %s");
        REGISTRATE.addRawLang(key("text", "hostility_debit_card.invalid_profile"), "This card contains an unsupported hostility profile.");
        REGISTRATE.addRawLang(key("text", "hostility_debit_card.swap_failed"), "Failed to swap hostility profiles.");
        REGISTRATE.addRawLang(key("text", "hostility_debit_card.swapped"), "Player %s -> %s, Card %s -> %s");

        REGISTRATE.addRawLang(key("title", "container.endchest"), "%s's %s");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.header"), "Ender Chest Info");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.owner"), "Owner: %s");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.owner_unknown"), "Cannot find owner %s");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.locked"), "Locked: Only the owner can open.");
        REGISTRATE.addRawLang(key("tooltip", "brass_ender_chest.unlocked"), "Unlocked: Anyone can open.");

        REGISTRATE.addRawLang(key("text","portable_stock_ticker.tooltip.linked"), "Linked.");
        REGISTRATE.addRawLang(key("text","portable_stock_ticker.no_data"), "Not Linked to a Logistics Network");
        REGISTRATE.addRawLang(key("text","portable_stock_ticker.no_network"), "Linked Logistics Network no exists.");
        REGISTRATE.addRawLang(key("text","portable_stock_ticker.unloaded"), "Linked Logistics Network is unloaded.");
        REGISTRATE.addRawLang(key("text", "portable_stock_ticker.no_clipboard_addresses"), "No package addresses found on the clipboard.");
        REGISTRATE.addRawLang(key("text", "portable_stock_ticker.addresses_saved"), "Saved %s new address(es); %s total.");
        REGISTRATE.addRawLang(key("text", "portable_stock_ticker.addresses_cleared"), "Cleared %s saved address(es).");

        REGISTRATE.addRawLang("container.shhs_create_core.dimension_parcel_station", "Dimension Parcel Station");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.bound"), "Bound to %s.");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.unbound"), "Unbound from dimension network %s.");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.no_binding_permission"), "Only network managers may bind or unbind this station.");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.no_primary_network"), "You do not have a primary dimension network.");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.limit_reached"), "This dimension network cannot accept another parcel station.");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.item_input"), "Item input");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.item_output"), "Item output");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.fluid_input"), "Fluid input");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.fluid_output"), "Fluid output");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.enabled"), "On");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.disabled"), "Off");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.unbound_status"), "Not bound to a dimension network");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.network_status"), "Network #%s · %s station(s)");
        REGISTRATE.addRawLang(key("text", "dimension_parcel_station.read_only"), "Read-only: insufficient network permission");

        REGISTRATE.addRawLang(key("tooltip","sprayer.header"), "Sprayer Info");
        REGISTRATE.addRawLang(key("tooltip","sprayer.angle"), "Angle: %s / %s°");
        REGISTRATE.addRawLang(key("tooltip","sprayer.range"), "Range: %s x %s x %s");

        REGISTRATE.addRawLang(key("title", "fan_miracle"), "Fan Miracle");
        REGISTRATE.addRawLang(key("text", "fan_miracle.fan"), "Encased Fan with Miracle");

        REGISTRATE.addRawLang(CATEGORY_KEY, "ShH's Create Core");
        REGISTRATE.addDataGenerator(ProviderType.LANG, provider -> {
            ShHsKeys.provideLang(provider::add);
            providePonderLang(provider::add);
        });
    }

    private static void providePonderLang(BiConsumer<String, String> consumer) {
        PonderIndex.addPlugin(new ShHsPonderPlugin());
        PonderIndex.getLangAccess().provideLang(MODID, consumer);
    }
}
