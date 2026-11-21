package dev.livingworld.client.gui;

import dev.livingworld.LivingWorld;
import dev.livingworld.client.config.ClientConfig;
import dev.livingworld.client.config.Opt;
import dev.livingworld.client.config.Opt.Category;
import dev.livingworld.client.env.ClientEnvironment;
import dev.livingworld.config.ServerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        super(Text.translatable("livingworld.config.title"));
        this.parent = parent;
        for (Category category : Category.values()) {
            entries.put(category, new ArrayList<>());
        }
        for (Field field : ClientConfig.class.getDeclaredFields()) {
            Opt opt = field.getAnnotation(Opt.class);
            if (opt != null) {
                entries.get(opt.value()).add(new Entry(field, opt, "livingworld.option." + field.getName()));
            }
        }
    }

    @Override
    protected void init() {
        int y = height - 26;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(width - MARGIN - 80, y, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("livingworld.config.reset"), b -> resetCategory())
                .dimensions(width - MARGIN - 80 - 6 - 110, y, 110, 20).build());
    }

    // ---- geometry --------------------------------------------------------------------------------

    private int categoryHeight() {
        int available = height - HEADER - FOOTER - 4;
        return MathHelper.clamp(available / Category.values().length, 11, 20);
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
        return client == null || client.world == null || client.isIntegratedServerRunning();
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
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1f));
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
        float t = MathHelper.clamp((float) (mouseX - x0) / SLIDER_WIDTH, 0f, 1f);
        float value = e.opt.min() + t * (e.opt.max() - e.opt.min());
        float step = e.opt.step();
        value = Math.round(value / step) * step;
        set(e, MathHelper.clamp(value, e.opt.min(), e.opt.max()));
    }

    // ---- input -----------------------------------------------------------------------------------

    /** Opens a page of the settings without a click (for the documentation screenshots). */
    public LWConfigScreen show(Category category) {
        selected = category;
        scroll = 0f;
        return this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        int ch = categoryHeight();
        if (mouseX >= MARGIN && mouseX < MARGIN + SIDEBAR && mouseY >= contentTop()) {
            int index = (int) ((mouseY - contentTop()) / ch);
            Category[] categories = Category.values();
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null) {
            setSliderFromMouse(dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            click();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = MathHelper.clamp(scroll - (float) amount * ROW, 0f, maxScroll());
        return true;
    }

    @Override
    public void close() {
        config.save();
        if (serverConfigDirty) {
            ServerConfig.get().save();
        }
        if (client != null) {
            client.setScreen(parent);
        }
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        hovered = null;
        serverRuleHovered = false;

        // Header
        context.drawTextWithShadow(textRenderer, title, MARGIN, 8, ACCENT);
        Text subtitle = Text.translatable("livingworld.config.subtitle");
        context.drawTextWithShadow(textRenderer, subtitle, MARGIN + textRenderer.getWidth(title) + 8, 8, TEXT_DIM);
        Text preset = Text.translatable("livingworld.config.preset_label",
                Text.translatable("livingworld.preset." + config.preset.name().toLowerCase(Locale.ROOT)));
        context.drawTextWithShadow(textRenderer, preset, contentRight() - textRenderer.getWidth(preset), 8, TEXT_DIM);
        context.fill(MARGIN, 21, contentRight(), 22, ACCENT_DIM);

        // Sidebar
        int ch = categoryHeight();
        int top = contentTop();
        Category[] categories = Category.values();
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
            context.drawText(textRenderer, Text.translatable(categories[i].key()), MARGIN + 8, y + (ch - 8) / 2,
                    categories[i] == selected ? TEXT : TEXT_DIM, false);
        }

        // Options
        int left = contentLeft(), right = contentRight(), bottom = contentBottom();
        context.fill(left, top, right, bottom, PANEL);
        scroll = MathHelper.clamp(scroll, 0f, maxScroll());
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
            context.drawText(textRenderer, Text.translatable("livingworld.option.temperatureEffects"), left + 8, y + 7,
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
        Text tip = null;
        if (hovered != null) {
            tip = Text.translatable(hovered.key + ".tooltip");
        } else if (serverRuleHovered) {
            tip = Text.translatable(serverRuleEditable() ? "livingworld.option.temperatureEffects.tooltip"
                    : "livingworld.option.temperatureEffects.locked");
        }
        if (tip != null) {
            int tipWidth = width - MARGIN * 2 - 206;
            List<OrderedText> lines = textRenderer.wrapLines(tip, Math.max(120, tipWidth));
            int ty = height - FOOTER + 4;
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                context.drawText(textRenderer, lines.get(i), MARGIN, ty + i * 9, TEXT_DIM, false);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRow(DrawContext context, Entry e, int left, int right, int y, int mouseX, int mouseY) {
        boolean over = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW
                && mouseY >= contentTop() && mouseY < contentBottom();
        if (over || dragging == e) {
            context.fill(left, y, right, y + ROW - 1, ROW_HOVER);
            hovered = e;
        }
        context.drawText(textRenderer, Text.translatable(e.key), left + 8, y + 7, TEXT, false);
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
            context.drawText(textRenderer, value, x0 - 8 - textRenderer.getWidth(value), y + 7, TEXT_DIM, false);
        } else if (type.isEnum()) {
            Object value = get(e);
            Text label = Text.translatable("livingworld.preset." + String.valueOf(value).toLowerCase(Locale.ROOT));
            int w = 96;
            int x0 = right - 8 - w;
            context.fill(x0, y + 3, x0 + w, y + ROW - 4, over ? ACCENT_DIM : OFF);
            context.drawCenteredTextWithShadow(textRenderer, label, x0 + w / 2, y + 7, TEXT);
        }
    }

    private void drawToggle(DrawContext context, int x, int y, boolean on, boolean enabled) {
        int track = !enabled ? 0xFF2A3034 : on ? ACCENT : OFF;
        context.fill(x, y, x + 26, y + 12, track);
        int knobX = on ? x + 15 : x + 1;
        context.fill(knobX, y + 1, knobX + 10, y + 11, enabled ? 0xFFFFFFFF : 0xFF707A80);
    }
}
