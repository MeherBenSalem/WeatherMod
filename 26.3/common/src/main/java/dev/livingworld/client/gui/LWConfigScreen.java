package dev.livingworld.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.livingworld.LivingWorld;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.Opt;
import dev.livingworld.client.config.Opt.Category;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.config.ServerConfig;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The settings screen: categories down the left, the chosen category's options on the right, and a
 * description of whatever is under the cursor along the bottom. Rows are generated from the
 * {@link Opt}-annotated fields of {@link ClientConfig}, so the screen can never drift out of sync with
 * the options that exist.
 */
public final class LWConfigScreen extends Screen {
    private static final int ACCENT = 0xFF4FC3DC;
    private static final int ACCENT_DIM = 0xFF2C6F80;
    private static final int PANEL = 0xD0101519;
    private static final int PANEL_LIGHT = 0xD01A2228;
    private static final int ROW_HOVER = 0x30FFFFFF;
    private static final int TEXT = 0xFFE8EEF0;
    private static final int TEXT_DIM = 0xFF93A1A8;
    private static final int OFF = 0xFF3B444A;
    private static final int ROW = 22;
    private static final int SIDEBAR = 136;
    private static final int MARGIN = 10;
    private static final int HEADER = 30;
    private static final int FOOTER = 44;
    private static final int SLIDER_WIDTH = 96;

    /** One option row. {@code field == null} marks the special server-rule row. */
    private record Entry(Field field, Opt opt, String key) {
    }

    private final Screen parent;
    private final ClientConfig config = ClientConfig.get();
    private final Map<Category, List<Entry>> entries = new EnumMap<>(Category.class);
    private Category selected = Category.GENERAL;
    private float scroll;
    private Entry dragging;
    private Entry hovered;
    private boolean serverRuleHovered;
    private boolean serverConfigDirty;

    public LWConfigScreen(Screen parent) {
        super(Component.translatable("livingworld.config.title"));
        this.parent = parent;
        for (Category category : Category.values()) {
            entries.put(category, new ArrayList<>());
        }
        for (Field field : ClientConfig.class.getDeclaredFields()) {
            Opt opt = field.getAnnotation(Opt.class);
            if (opt != null && !NOT_ON_THIS_VERSION.contains(field.getName())) {
                entries.get(opt.value()).add(new Entry(field, opt, "livingworld.option." + field.getName()));
            }
        }
        List<Category> shown = new ArrayList<>();
        for (Category category : Category.values()) {
            if (!entries.get(category).isEmpty()) {
                shown.add(category);
            }
        }
        categories = shown.toArray(new Category[0]);
    }

    /**
     * Settings of effects that other versions draw with the mod's own shader programs (atmosphere pass,
     * water, vegetation sway, lit decals, lens program, sky and fog tint, dropped item physics). This version
     * of the game has no place for them, so they are not offered here.
     */
    private static final java.util.Set<String> NOT_ON_THIS_VERSION = java.util.Set.of(
            "itemPhysics", "heatHaze", "heatHazeStrength", "temperatureTint", "wetSurfaces", "puddles", "stormClouds",
            "snowCover", "litFootprints", "vegetationSway", "swayStrength", "vegetationBending", "atmosphericFog",
            "fogDensity", "groundMist", "colorGrading", "gradingStrength", "exposureAdaptation", "lightShafts",
            "volumetricClouds", "cloudShadows", "enhancedSky", "aurora", "waterShading", "waterReflections", "waveHeight",
            "caustics", "shoreline", "lensDroplets", "bloom", "bloomStrength", "sunGlow", "filmLook", "vignette",
            "postProcessing", "yieldToShaderPacks", "simpleRendering", "overrideSkyColors", "reduceDistortion", "reduceFog");
    /** The categories with at least one setting on this version. */
    private final Category[] categories;

    @Override
    protected void init() {
        int y = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width - MARGIN - 80, y, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("livingworld.config.reset"), b -> resetCategory())
                .bounds(width - MARGIN - 80 - 6 - 110, y, 110, 20).build());
    }

    // ---- geometry --------------------------------------------------------------------------------

    private int categoryHeight() {
        int available = height - HEADER - FOOTER - 4;
        return Mth.clamp(available / categories.length, 11, 20);
    }

    private int contentLeft() {
        return MARGIN + SIDEBAR + 8;
    }

    private int contentRight() {
        return width - MARGIN;
    }

    private int contentTop() {
        return HEADER + 2;
    }

    private int contentBottom() {
        return height - FOOTER;
    }

    private boolean showsServerRule() {
        return selected == Category.CLIMATE;
    }

    private int rowCount() {
        return entries.get(selected).size() + (showsServerRule() ? 1 : 0);
    }

    private float maxScroll() {
        return Math.max(0, rowCount() * ROW + 6 - (contentBottom() - contentTop()));
    }

    /** The server rule can be changed from here in single-player or before joining a world. */
    private boolean serverRuleEditable() {
        return minecraft == null || minecraft.level == null || minecraft.hasSingleplayerServer();
    }

    private boolean serverRuleValue() {
        return serverRuleEditable() ? ServerConfig.get().temperatureEffects : ClientEnvironment.INSTANCE.server.gameplayEffects;
    }

    // ---- values ----------------------------------------------------------------------------------

    private boolean getBool(Entry e) {
        try {
            return e.field.getBoolean(config);
        } catch (IllegalAccessException ex) {
            return false;
        }
    }

    private float getFloat(Entry e) {
        try {
            return e.field.getFloat(config);
        } catch (IllegalAccessException ex) {
            return 0f;
        }
    }

    private void set(Entry e, Object value) {
        try {
            e.field.set(config, value);
        } catch (IllegalAccessException ex) {
            LivingWorld.LOGGER.warn("Could not set option {}", e.field.getName());
        }
    }

    private Object get(Entry e) {
        try {
            return e.field.get(config);
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    private void resetCategory() {
        ClientConfig defaults = new ClientConfig();
        for (Entry e : entries.get(selected)) {
            try {
                e.field.set(config, e.field.get(defaults));
            } catch (IllegalAccessException ignored) {
                // Fields are public; cannot happen.
            }
        }
        click();
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    private String valueText(Entry e) {
        float v = getFloat(e);
        if (e.opt.percent()) {
            return Math.round(v * 100f) + "%";
        }
        return e.opt.step() >= 1f ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.2f", v);
    }

    private void setSliderFromMouse(Entry e, double mouseX) {
        int x0 = contentRight() - 8 - SLIDER_WIDTH;
        float t = Mth.clamp((float) (mouseX - x0) / SLIDER_WIDTH, 0f, 1f);
        float value = e.opt.min() + t * (e.opt.max() - e.opt.min());
        float step = e.opt.step();
        value = Math.round(value / step) * step;
        set(e, Mth.clamp(value, e.opt.min(), e.opt.max()));
    }

    // ---- input -----------------------------------------------------------------------------------

    /** Opens a page of the settings without a click (for the documentation screenshots). */
    public LWConfigScreen show(Category category) {
        selected = category;
        scroll = 0f;
        return this;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        int ch = categoryHeight();
        if (mouseX >= MARGIN && mouseX < MARGIN + SIDEBAR && mouseY >= contentTop()) {
            int index = (int) ((mouseY - contentTop()) / ch);
            if (index >= 0 && index < categories.length && categories[index] != selected) {
                selected = categories[index];
                scroll = 0f;
                click();
            }
            return true;
        }
        if (mouseX >= contentLeft() && mouseX < contentRight() && mouseY >= contentTop() && mouseY < contentBottom()) {
            int index = (int) ((mouseY - contentTop() - 3 + scroll) / ROW);
            List<Entry> list = entries.get(selected);
            if (index >= 0 && index < list.size()) {
                Entry e = list.get(index);
                Class<?> type = e.field.getType();
                if (type == boolean.class) {
                    set(e, !getBool(e));
                    click();
                } else if (type == float.class) {
                    if (mouseX >= contentRight() - 14 - SLIDER_WIDTH) {
                        dragging = e;
                        setSliderFromMouse(e, mouseX);
                    }
                } else if (type.isEnum()) {
                    Object[] constants = type.getEnumConstants();
                    Object current = get(e);
                    int next = 0;
                    for (int i = 0; i < constants.length; i++) {
                        if (constants[i] == current) {
                            next = (i + 1) % constants.length;
                        }
                    }
                    set(e, constants[next]);
                    click();
                }
                return true;
            }
            if (index == list.size() && showsServerRule() && serverRuleEditable()) {
                ServerConfig.get().temperatureEffects = !ServerConfig.get().temperatureEffects;
                serverConfigDirty = true;
                click();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging != null) {
            setSliderFromMouse(dragging, event.x());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            click();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (float) scrollY * ROW, 0f, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        config.save();
        if (serverConfigDirty) {
            ServerConfig.get().save();
        }
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    // ---- drawing ---------------------------------------------------------------------------------

    /** Set while {@code super.extractRenderState} draws the widgets: the background is already drawn by then. */
    private boolean backgroundDrawn;

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // The game draws (and blurs) the background once per frame; this screen draws it first itself
        // and its own panels over it, so a second call is skipped.
        if (!backgroundDrawn) {
            super.extractBackground(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        backgroundDrawn = true;
        hovered = null;
        serverRuleHovered = false;

        // Header
        context.text(font, title, MARGIN, 8, ACCENT, true);
        Component subtitle = Component.translatable("livingworld.config.subtitle");
        context.text(font, subtitle, MARGIN + font.width(title) + 8, 8, TEXT_DIM, true);
        Component preset = Component.translatable("livingworld.config.preset_label",
                Component.translatable("livingworld.preset." + config.preset.name().toLowerCase(Locale.ROOT)));
        context.text(font, preset, contentRight() - font.width(preset), 8, TEXT_DIM, true);
        context.fill(MARGIN, 21, contentRight(), 22, ACCENT_DIM);

        // Sidebar
        int ch = categoryHeight();
        int top = contentTop();
        context.fill(MARGIN, top, MARGIN + SIDEBAR, top + ch * categories.length, PANEL);
        for (int i = 0; i < categories.length; i++) {
            int y = top + i * ch;
            boolean over = mouseX >= MARGIN && mouseX < MARGIN + SIDEBAR && mouseY >= y && mouseY < y + ch;
            if (categories[i] == selected) {
                context.fill(MARGIN, y, MARGIN + SIDEBAR, y + ch, PANEL_LIGHT);
                context.fill(MARGIN, y, MARGIN + 2, y + ch, ACCENT);
            } else if (over) {
                context.fill(MARGIN, y, MARGIN + SIDEBAR, y + ch, ROW_HOVER);
            }
            context.text(font, Component.translatable(categories[i].key()), MARGIN + 8, y + (ch - 8) / 2,
                    categories[i] == selected ? TEXT : TEXT_DIM, false);
        }

        // Options
        int left = contentLeft(), right = contentRight(), bottom = contentBottom();
        context.fill(left, top, right, bottom, PANEL);
        scroll = Mth.clamp(scroll, 0f, maxScroll());
        context.enableScissor(left, top, right, bottom);
        List<Entry> list = entries.get(selected);
        int y = top + 3 - Math.round(scroll);
        for (Entry e : list) {
            if (y + ROW > top && y < bottom) {
                drawRow(context, e, left, right, y, mouseX, mouseY);
            }
            y += ROW;
        }
        if (showsServerRule() && y + ROW > top && y < bottom) {
            boolean over = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW && mouseY >= top && mouseY < bottom;
            if (over) {
                context.fill(left, y, right, y + ROW - 1, ROW_HOVER);
                serverRuleHovered = true;
            }
            boolean editable = serverRuleEditable();
            context.text(font, Component.translatable("livingworld.option.temperatureEffects"), left + 8, y + 7,
                    editable ? TEXT : TEXT_DIM, false);
            drawToggle(context, right - 8 - 26, y + 5, serverRuleValue(), editable);
        }
        context.disableScissor();
        if (maxScroll() > 0f) {
            int track = bottom - top;
            int thumb = Math.max(12, (int) (track * (track / (float) (rowCount() * ROW + 6))));
            int thumbY = top + (int) ((track - thumb) * (scroll / maxScroll()));
            context.fill(right - 3, thumbY, right - 1, thumbY + thumb, ACCENT_DIM);
        }

        // Description of the hovered option
        Component tip = null;
        if (hovered != null) {
            tip = Component.translatable(hovered.key + ".tooltip");
        } else if (serverRuleHovered) {
            tip = Component.translatable(serverRuleEditable() ? "livingworld.option.temperatureEffects.tooltip"
                    : "livingworld.option.temperatureEffects.locked");
        }
        if (tip != null) {
            int tipWidth = width - MARGIN * 2 - 206;
            List<FormattedCharSequence> lines = font.split(tip, Math.max(120, tipWidth));
            int ty = height - FOOTER + 4;
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                context.text(font, lines.get(i), MARGIN, ty + i * 9, TEXT_DIM, false);
            }
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        backgroundDrawn = false;
    }

    private void drawRow(GuiGraphicsExtractor context, Entry e, int left, int right, int y, int mouseX, int mouseY) {
        boolean over = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW
                && mouseY >= contentTop() && mouseY < contentBottom();
        if (over || dragging == e) {
            context.fill(left, y, right, y + ROW - 1, ROW_HOVER);
            hovered = e;
        }
        context.text(font, Component.translatable(e.key), left + 8, y + 7, TEXT, false);
        Class<?> type = e.field.getType();
        if (type == boolean.class) {
            drawToggle(context, right - 8 - 26, y + 5, getBool(e), true);
        } else if (type == float.class) {
            int x0 = right - 8 - SLIDER_WIDTH;
            float t = (getFloat(e) - e.opt.min()) / (e.opt.max() - e.opt.min());
            int knob = x0 + Math.round(t * SLIDER_WIDTH);
            context.fill(x0, y + 10, x0 + SLIDER_WIDTH, y + 12, OFF);
            context.fill(x0, y + 10, knob, y + 12, ACCENT);
            context.fill(knob - 2, y + 5, knob + 2, y + 17, dragging == e ? 0xFFFFFFFF : TEXT);
            String value = valueText(e);
            context.text(font, value, x0 - 8 - font.width(value), y + 7, TEXT_DIM, false);
        } else if (type.isEnum()) {
            Object value = get(e);
            Component label = Component.translatable("livingworld.preset." + String.valueOf(value).toLowerCase(Locale.ROOT));
            int w = 96;
            int x0 = right - 8 - w;
            context.fill(x0, y + 3, x0 + w, y + ROW - 4, over ? ACCENT_DIM : OFF);
            context.centeredText(font, label, x0 + w / 2, y + 7, TEXT);
        }
    }

    private void drawToggle(GuiGraphicsExtractor context, int x, int y, boolean on, boolean enabled) {
        int track = !enabled ? 0xFF2A3034 : on ? ACCENT : OFF;
        context.fill(x, y, x + 26, y + 12, track);
        int knobX = on ? x + 15 : x + 1;
        context.fill(knobX, y + 1, knobX + 10, y + 11, enabled ? 0xFFFFFFFF : 0xFF707A80);
    }
}
