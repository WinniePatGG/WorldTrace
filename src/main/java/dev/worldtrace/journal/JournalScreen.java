package dev.worldtrace.journal;

import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.storage.LoadReport;
import dev.worldtrace.util.Distance;
import dev.worldtrace.util.NameFormatting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The WorldTrace journal.
 *
 * <p>Structure, top to bottom: header with world name and totals, a search box, a wrapping category
 * chip row plus the dimension / favorite / sort controls, then the list and detail panes side by
 * side — or one at a time on very small windows, so nothing ever becomes unreachable.
 *
 * <p>Every interactive element is a hit region derived from the same {@link JournalLayout} that
 * draws it. Regions are rebuilt only when the layout or the result set changes, never per frame, so
 * hit testing and rendering cannot disagree and the render loop stays allocation-free.
 */
public final class JournalScreen extends Screen {

    private static final int MAX_DIMENSIONS = 8;
    private static final int ROW_ACTION_HEIGHT = 20;

    // Hit region actions owned by this screen. Detail-pane actions live in DetailPanel.
    private static final int ACTION_SORT = 101;
    private static final int ACTION_DIMENSION_CYCLE = 102;
    private static final int ACTION_FAVORITES_ONLY = 103;
    private static final int ACTION_CATEGORY = 104;
    private static final int ACTION_CLEAR_FILTERS = 105;
    private static final int ACTION_ROW = 106;
    private static final int ACTION_ADD_BOOKMARK = 107;
    private static final int ACTION_ADD_PIN = 108;
    private static final int ACTION_CLEAR_DESTINATION = 109;
    private static final int ACTION_BACK_TO_LIST = 110;
    private static final int ACTION_MANAGE_PINS = 111;
    private static final int ACTION_CLEAR_SEARCH = 112;

    private final DiscoveryManager manager;
    private final JournalQuery query = new JournalQuery();
    private final List<HitRegion> regions = new ArrayList<>();
    private final List<String> dimensions = new ArrayList<>();

    private SortMode sortMode = SortMode.NEWEST;
    private List<TraceEntry> results = List.of();
    private JournalLayout layout;
    private TextFieldWidget searchField;
    private String selectedEntryId;
    private int scrollOffset;
    private int dimensionIndex;
    private boolean detailOnly;
    private String statusMessage;
    private long statusUntilMillis;

    private JournalScreen(DiscoveryManager manager, String focusEntryId) {
        super(Text.literal("WorldTrace"));
        this.manager = manager;
        this.selectedEntryId = focusEntryId;
        this.detailOnly = focusEntryId != null;
    }

    /**
     * Opens the journal, optionally pre-selecting an entry. The focus argument is what lets the
     * "open the journal to the thing you just found" notification action jump straight to it.
     */
    public static JournalScreen open(DiscoveryManager manager, String focusEntryId) {
        return new JournalScreen(manager, focusEntryId);
    }

    // ------------------------------------------------------------------- setup

    @Override
    protected void init() {
        layout = new JournalLayout(width, height);
        collectDimensions();

        int row = layout.searchTop();
        int available = layout.width() - layout.padding() * 2 - 64;
        int searchWidth = Math.max(60, Math.min(available, 220));
        searchField = new TextFieldWidget(textRenderer, layout.listLeft(), row, searchWidth,
                Math.max(10, layout.searchRowHeight() - 4), Text.literal("Search"));
        searchField.setMaxLength(128);
        searchField.setPlaceholder(Text.literal("Search name, notes, tags, coords…"));
        searchField.setChangedListener(value -> {
            query.setSearchText(value);
            refreshResults();
        });
        searchField.setFocused(false);
        // Order matters: the journal surface is registered first so the search field stays on top
        // of it, and both render in the same pass that produces crisp text.
        // UiDrawable is only a Drawable, not a Selectable, so it goes in via addDrawable().
        addDrawable(new UiDrawable());
        addDrawableChild(searchField);

        refreshResults();
    }

    private void collectDimensions() {
        Set<String> present = new LinkedHashSet<>();
        for (TraceEntry entry : manager.store().entries()) {
            present.add(entry.dimensionId());
        }
        dimensions.clear();
        present.stream()
                .sorted((a, b) -> {
                    boolean aFirst = a.equals("minecraft:overworld");
                    boolean bFirst = b.equals("minecraft:overworld");
                    if (aFirst != bFirst) {
                        return aFirst ? -1 : 1;
                    }
                    return a.compareToIgnoreCase(b);
                })
                .limit(MAX_DIMENSIONS)
                .forEach(dimensions::add);
        if (dimensions.isEmpty()) {
            dimensions.add("minecraft:overworld");
        }
        dimensionIndex = Math.min(dimensionIndex, dimensions.size());
    }

    // ------------------------------------------------------------------ search

    /** Re-runs filtering and sorting, keeping the selection alive when it is still visible. */
    private void refreshResults() {
        List<TraceEntry> filtered = JournalFilter.apply(manager.store().entryList(), query);
        results = JournalSorter.sorted(filtered, sortMode);

        boolean stillVisible = selectedEntryId != null
                && results.stream().anyMatch(entry -> entry.id().equals(selectedEntryId));
        if (selectedEntryId != null && !stillVisible) {
            // Clearing the filters must bring back exactly the context the player was in, so the
            // id is retained while a filter is active.
            if (!query.isFiltered()) {
                selectedEntryId = null;
            }
        }
        if (selectedEntryId == null) {
            detailOnly = false;
        }

        // Regions first: they establish the filter line count, which the visible-row count (and
        // therefore the scroll range) depends on.
        rebuildRegions();
        int maxScroll = Math.max(0, results.size() - layout.visibleRows());
        int clamped = Math.max(0, Math.min(maxScroll, scrollOffset));
        if (clamped != scrollOffset) {
            scrollOffset = clamped;
            rebuildRegions();
        }
    }

    private Optional<TraceEntry> selected() {
        return selectedEntryId == null ? Optional.empty() : manager.store().entry(selectedEntryId);
    }

    private boolean isDestination(TraceEntry entry) {
        return entry.id().equals(manager.destinationEntryId());
    }

    // ----------------------------------------------------------------- regions

    private void register(int action, int payload, int x1, int y1, int x2, int y2) {
        regions.add(new HitRegion(action, payload, x1, y1, x2, y2));
    }

    private void rebuildRegions() {
        regions.clear();
        if (layout == null) {
            return;
        }
        int categoryLines = buildFilterRegions();
        buildSearchRegions();
        // The dimension / favourites / sort controls always get their own row underneath the
        // category chips. Sharing a row with them would overlap on any window wide enough to wrap,
        // and the line count has to be known before the content area is measured.
        int controlY = layout.filterTop() + categoryLines * layout.filterRowHeight() + 2;
        buildHeaderRegions(controlY);
        layout.setFilterLines(categoryLines + 1);
        buildFooterRegions();
        if (showList()) {
            int y = layout.contentTop();
            for (int i = scrollOffset; i < Math.min(results.size(), scrollOffset + layout.visibleRows()); i++) {
                int top = y + (i - scrollOffset) * layout.rowHeight();
                register(ACTION_ROW, i, layout.listLeft(), top, layout.listRight(), top + layout.rowHeight());
            }
        }
        if (showDetail()) {
            buildDetailRegions();
        }
    }

    /** Category chips wrap across as many lines as the window needs. */
    private int buildFilterRegions() {
        int y = layout.filterTop();
        int maxX = layout.width() - layout.padding();
        int x = layout.listLeft();
        int line = 0;

        for (EntryCategory category : EntryCategory.values()) {
            int chipWidth = JournalPainter.chipWidth(textRenderer, category.displayName());
            if (x + chipWidth > maxX && x > layout.listLeft()) {
                line++;
                x = layout.listLeft();
            }
            register(ACTION_CATEGORY, category.ordinal(), x, y + line * layout.filterRowHeight(),
                    x + chipWidth, y + line * layout.filterRowHeight() + JournalPainter.chipHeight());
            x += chipWidth + JournalPainter.CHIP_SPACING;
        }
        return line + 1;
    }

    /** The "Clear" chip next to the search box, so typing is never the only way to reset. */
    private void buildSearchRegions() {
        int y = layout.searchTop();
        int x = layout.listLeft() + searchField.getWidth() + 4;
        int width = JournalPainter.chipWidth(textRenderer, "Clear");
        register(ACTION_CLEAR_SEARCH, 0, x, y, x + width, y + layout.searchRowHeight());
    }

    private void buildHeaderRegions(int controlY) {
        int chipHeight = JournalPainter.chipHeight();
        int maxX = layout.width() - layout.padding();

        String dimensionLabel = "Dim: " + (dimensionIndex < 0
                ? "All" : NameFormatting.prettifyIdentifier(dimensions.get(dimensionIndex)));
        String favoritesLabel = "★";
        String sortLabel = "Sort: " + sortMode.displayName();

        int dimensionWidth = JournalPainter.chipWidth(textRenderer, dimensionLabel);
        int favoritesWidth = JournalPainter.chipWidth(textRenderer, favoritesLabel);
        int sortWidth = JournalPainter.chipWidth(textRenderer, sortLabel);
        int clearWidth = JournalPainter.chipWidth(textRenderer, "Clear");

        // Sort sits on the far right of the control row; the rest flow leftwards from the list edge.
        register(ACTION_SORT, 0, maxX - sortWidth, controlY, maxX, controlY + chipHeight);

        int x = layout.listLeft();
        if (x + dimensionWidth + favoritesWidth + sortWidth + 3 * JournalPainter.CHIP_SPACING <= maxX) {
            register(ACTION_DIMENSION_CYCLE, 0, x, controlY, x + dimensionWidth, controlY + chipHeight);
            x += dimensionWidth + JournalPainter.CHIP_SPACING;
            register(ACTION_FAVORITES_ONLY, 0, x, controlY, x + favoritesWidth, controlY + chipHeight);
            x += favoritesWidth + JournalPainter.CHIP_SPACING;
        }
        // "Clear all filters" is only useful when something is actually filtered, so it only appears
        // when it fits without colliding with the sort chip.
        if (x + clearWidth <= maxX - sortWidth - JournalPainter.CHIP_SPACING && query.isFiltered()) {
            register(ACTION_CLEAR_FILTERS, 0, x, controlY, x + clearWidth, controlY + chipHeight);
        }
    }

    private void buildFooterRegions() {
        int y = layout.footerTop();
        int x = layout.listLeft();
        int maxX = layout.width() - layout.padding();

        List<int[]> buttons = new ArrayList<>();
        buttons.add(new int[]{ACTION_ADD_BOOKMARK});
        buttons.add(new int[]{ACTION_ADD_PIN});
        buttons.add(new int[]{ACTION_MANAGE_PINS});
        if (manager.destinationEntryId() != null) {
            buttons.add(new int[]{ACTION_CLEAR_DESTINATION});
        }
        if (layout.narrow() && detailOnly) {
            buttons.add(new int[]{ACTION_BACK_TO_LIST});
        }

        for (int[] button : buttons) {
            int action = button[0];
            int buttonWidth = JournalPainter.chipWidth(textRenderer, footerLabel(action)) + 6;
            if (x + buttonWidth > maxX) {
                break;
            }
            register(action, 0, x, y, x + buttonWidth, y + layout.footerHeight());
            x += buttonWidth + 4;
        }
    }

    private void buildDetailRegions() {
        Optional<TraceEntry> maybe = selected();
        if (maybe.isEmpty()) {
            return;
        }
        TraceEntry entry = maybe.get();
        int left = layout.detailLeft();
        int width = layout.detailRight() - left;

        List<Integer> actions = new ArrayList<>();
        actions.add(DetailPanel.FAVORITE);
        actions.add(DetailPanel.DESTINATION);
        actions.add(DetailPanel.COPY);
        actions.add(DetailPanel.EDIT);
        if (entry.screenshotRef() == null) {
            actions.add(DetailPanel.SCREENSHOT);
        }
        actions.add(DetailPanel.DELETE);

        int rows = (actions.size() + 1) / 2;
        int top = detailActionsTop();
        int columnWidth = (width - JournalPainter.CHIP_SPACING) / 2;

        for (int i = 0; i < actions.size(); i++) {
            int column = i % 2;
            int row = i / 2;
            int x = left + column * (columnWidth + JournalPainter.CHIP_SPACING);
            int y = top + row * (ROW_ACTION_HEIGHT + 2);
            register(actions.get(i), 0, x, y, x + columnWidth, y + ROW_ACTION_HEIGHT);
        }
    }

    /** Top of the detail action grid, so the pane's text never overlaps the buttons. */
    private int detailActionsTop() {
        Optional<TraceEntry> maybe = selected();
        if (maybe.isEmpty()) {
            return layout.contentBottom();
        }
        int count = maybe.get().screenshotRef() == null ? 6 : 5;
        int rows = (count + 1) / 2;
        return Math.max(layout.contentTop() + 30, layout.contentBottom() - rows * (ROW_ACTION_HEIGHT + 2) - 2);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        // The whole interface is drawn by a child element rather than inline here. Everything drawn
        // directly in render() came out blurred, while content drawn through super.render() — the
        // search field — stayed razor sharp, so the UI is moved into that same pass. See UiDrawable.
        super.render(context, mouseX, mouseY, delta);
        renderTooltip(context, mouseX, mouseY);
    }

    /** Draws the entire journal surface. Registered as the first child, under the search field. */
    private final class UiDrawable implements Drawable {

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int accent = JournalPalette.opaque(manager.config().accentRgb());
            JournalPainter.window(context, width, height, accent);
            renderHeader(context, accent);
            renderFilterRow(context, mouseX, mouseY, accent);
            if (showList()) {
                renderList(context, mouseX, mouseY, accent);
            }
            if (showDetail()) {
                renderDetail(context, mouseX, mouseY, accent);
            }
            renderFooter(context, mouseX, mouseY, accent);
        }
    }

    private void renderHeader(DrawContext context, int accent) {
        int x = layout.listLeft();
        int y = layout.headerTop();
        int right = layout.width() - layout.padding();

        JournalPainter.text(context, textRenderer, "WorldTrace", x, y + 5, JournalPalette.TEXT);
        int titleWidth = textRenderer.getWidth("WorldTrace");
        context.fill(x + titleWidth + 5, y + 6, x + titleWidth + 6, y + 14, accent);

        String info = layout.mode() == JournalLayout.Mode.ROOMY
                ? manager.store().identity().displayName() + "  ·  " + manager.store().entryCount()
                + " entries  ·  " + Distance.formatDistance(manager.totalDistanceTravelled())
                + " travelled  ·  " + results.size() + " shown"
                : manager.store().identity().displayName() + " · " + results.size() + "/" +
                manager.store().entryCount();
        JournalPainter.text(context, textRenderer,
                JournalPainter.fit(textRenderer, info, Math.max(20, right - x - titleWidth - 12)),
                x + titleWidth + 12, y + 6, JournalPalette.TEXT_MUTED);

        // Status message shares the header line, right aligned, so it never covers the list.
        if (statusMessage != null) {
            if (System.currentTimeMillis() > statusUntilMillis) {
                statusMessage = null;
            } else {
                String message = JournalPainter.fit(textRenderer, statusMessage,
                        Math.max(0, right - x - titleWidth - 24 - textRenderer.getWidth(info)));
                context.drawText(textRenderer, Text.literal(message), right - textRenderer.getWidth(message),
                        y + 17, JournalPalette.POSITIVE, false);
            }
        }

        context.fill(x, y + layout.headerHeight() - 1, right, y + layout.headerHeight(), JournalPalette.BORDER);
    }

    private void renderFilterRow(DrawContext context, int mouseX, int mouseY, int accent) {
        for (HitRegion region : regions) {
            boolean hover = region.contains(mouseX, mouseY);
            switch (region.action()) {
                case ACTION_CATEGORY -> {
                    EntryCategory category = EntryCategory.values()[region.payload()];
                    JournalPainter.chip(context, textRenderer, category.displayName(), region.x1(), region.y1(),
                            query.isCategorySelected(category), hover, accent);
                }
                case ACTION_DIMENSION_CYCLE -> {
                    String label = "Dim: " + (dimensionIndex < 0
                            ? "All" : NameFormatting.prettifyIdentifier(dimensions.get(dimensionIndex)));
                    JournalPainter.chip(context, textRenderer, label, region.x1(), region.y1(),
                            dimensionIndex >= 0, hover, accent);
                }
                case ACTION_FAVORITES_ONLY -> JournalPainter.chip(context, textRenderer, "★",
                        region.x1(), region.y1(), query.favoritesOnly(), hover, accent);
                case ACTION_SORT -> JournalPainter.chip(context, textRenderer, "Sort: " + sortMode.displayName(),
                        region.x1(), region.y1(), false, hover, accent);
                case ACTION_CLEAR_FILTERS -> JournalPainter.chip(context, textRenderer, "Clear",
                        region.x1(), region.y1(), false, hover, accent);
                case ACTION_CLEAR_SEARCH ->
                        JournalPainter.chip(context, textRenderer, "Clear", region.x1(), region.y1() + 3,
                                false, hover, accent);
                default -> { }
            }
        }
    }

    private void renderList(DrawContext context, int mouseX, int mouseY, int accent) {
        int top = layout.contentTop();
        int bottom = layout.contentBottom();
        int left = layout.listLeft();
        int right = layout.listRight();

        JournalPainter.panel(context, left, top, right, bottom, JournalPalette.PANEL_SUNKEN, JournalPalette.BORDER);

        if (manager.store().entryCount() == 0) {
            renderEmptyState(context, left, top, right, bottom, true);
            return;
        }
        if (results.isEmpty()) {
            renderEmptyState(context, left, top, right, bottom, false);
            return;
        }

        context.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        int y = top + 1;
        for (int i = scrollOffset; i < Math.min(results.size(), scrollOffset + layout.visibleRows()); i++) {
            renderRow(context, results.get(i), i, left + 1, y, right - 1, y + layout.rowHeight() - 1,
                    mouseX, mouseY);
            y += layout.rowHeight();
        }
        context.disableScissor();

        renderScrollbar(context, left, right, top, bottom, accent);
    }

    private void renderRow(DrawContext context, TraceEntry entry, int index, int x1, int y1, int x2, int y2,
                           int mouseX, int mouseY) {
        boolean isSelected = entry.id().equals(selectedEntryId);
        boolean hovered = mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;

        int background = isSelected ? JournalPalette.ROW_SELECTED
                : hovered ? JournalPalette.ROW_HOVER
                : index % 2 == 1 ? JournalPalette.ROW_STRIPE
                : JournalPalette.PANEL_SUNKEN;

        JournalPainter.panel(context, x1, y1, x2, y2, background, 0);
        context.fill(x1, y1, x1 + 2, y2, entry.category().argb());

        int textX = x1 + 7;
        int maxWidth = x2 - textX - 8;
        String name = entry.favorite() ? "★ " + entry.name() : entry.name();
        JournalPainter.text(context, textRenderer,
                JournalPainter.fit(textRenderer, name, maxWidth), textX, y1 + 3, JournalPalette.TEXT);

        if (layout.rowHeight() >= 22) {
            String subtitle = NameFormatting.prettifyIdentifier(entry.dimensionId())
                    + " · " + entry.x() + ", " + entry.y() + ", " + entry.z();
            JournalPainter.text(context, textRenderer,
                    JournalPainter.fit(textRenderer, subtitle, maxWidth), textX, y1 + 3 + textRenderer.fontHeight,
                    JournalPalette.TEXT_FAINT);
        }

        if (entry.category() == EntryCategory.STRUCTURE && !entry.confirmed()) {
            // Unconfirmed findings are marked, never presented as fact.
            context.fill(x2 - 4, y1 + 4, x2 - 2, y1 + 6, JournalPalette.WARNING);
        }
    }

    private void renderScrollbar(DrawContext context, int left, int right, int top, int bottom, int accent) {
        int visible = layout.visibleRows();
        if (results.size() <= visible) {
            return;
        }
        int trackHeight = bottom - top - 2;
        int thumbHeight = Math.max(14, trackHeight * visible / results.size());
        int maxScroll = results.size() - visible;
        int thumbY = top + 1 + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        context.fill(right - 3, top + 1, right - 1, bottom - 1, JournalPalette.withAlpha(accent, 0.12f));
        context.fill(right - 3, thumbY, right - 1, thumbY + thumbHeight, JournalPalette.withAlpha(accent, 0.8f));
    }

    private void renderEmptyState(DrawContext context, int left, int top, int right, int bottom, boolean noData) {
        int centerX = (left + right) / 2;
        int middle = top + (bottom - top) / 2;
        if (noData) {
            JournalPainter.centered(context, textRenderer, "Your journal is empty", centerX, middle - 16,
                    JournalPalette.TEXT);
            String[] hint = {"Explore to record biomes and milestones.",
                    "Press K to bookmark where you stand."};
            for (int i = 0; i < hint.length; i++) {
                JournalPainter.centered(context, textRenderer,
                        JournalPainter.fit(textRenderer, hint[i], right - left - 12), centerX,
                        middle + 2 + i * (textRenderer.fontHeight + 3), JournalPalette.TEXT_MUTED);
            }
            return;
        }
        JournalPainter.centered(context, textRenderer, "No matching entries", centerX, middle - 8,
                JournalPalette.TEXT);
        JournalPainter.centered(context, textRenderer,
                JournalPainter.fit(textRenderer, "Try a different search or clear the filters.", right - left - 12),
                centerX, middle + 8, JournalPalette.TEXT_MUTED);
    }

    private void renderDetail(DrawContext context, int mouseX, int mouseY, int accent) {
        int left = layout.detailLeft();
        int right = layout.detailRight();
        int top = layout.contentTop();
        int bottom = layout.contentBottom();

        JournalPainter.panel(context, left, top, right, bottom, JournalPalette.PANEL, JournalPalette.BORDER);

        Optional<TraceEntry> maybe = selected();
        if (maybe.isEmpty()) {
            int centerX = (left + right) / 2;
            int middle = top + (bottom - top) / 2;
            JournalPainter.centered(context, textRenderer, "Select an entry", centerX, middle - 6,
                    JournalPalette.TEXT_MUTED);
            JournalPainter.centered(context, textRenderer, "to see its details here.", centerX, middle + 6,
                    JournalPalette.TEXT_MUTED);
            return;
        }
        DetailPanel.render(context, textRenderer, manager.config(), maybe.get(),
                new DetailPanel.Area(left, right, top, bottom, detailActionsTop()),
                regions, isDestination(maybe.get()), manager.store().readOnly(),
                mouseX, mouseY, accent);
    }

    private void renderFooter(DrawContext context, int mouseX, int mouseY, int accent) {
        int y = layout.footerTop();
        context.fill(layout.listLeft(), y - 4, layout.width() - layout.padding(), y - 3, JournalPalette.BORDER);
        for (HitRegion region : regions) {
            String label = footerLabel(region.action());
            if (label == null) {
                continue;
            }
            boolean enabled = !manager.store().readOnly();
            JournalPainter.button(context, textRenderer, label, region.x1(), region.y1(),
                    region.x2() - region.x1(), region.y2() - region.y1(), enabled,
                    region.contains(mouseX, mouseY), false, accent);
        }
    }

    private String footerLabel(int action) {
        return switch (action) {
            case ACTION_ADD_BOOKMARK -> "+ Bookmark";
            case ACTION_ADD_PIN -> "+ Watch pin";
            case ACTION_MANAGE_PINS -> "Pins";
            case ACTION_CLEAR_DESTINATION -> "Clear destination";
            case ACTION_BACK_TO_LIST -> "← List";
            default -> null;
        };
    }

    private void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        for (HitRegion region : regions) {
            if (!region.contains(mouseX, mouseY)) {
                continue;
            }
            String tooltip = switch (region.action()) {
                case ACTION_SORT -> "Change the order of the list";
                case ACTION_DIMENSION_CYCLE -> "Show entries from one dimension only";
                case ACTION_FAVORITES_ONLY -> "Show favorited entries only";
                case ACTION_ADD_BOOKMARK -> "Bookmark your current position";
                case ACTION_ADD_PIN -> "Expect a structure here; WorldTrace confirms it when you arrive";
                case ACTION_MANAGE_PINS -> "Review and remove watch pins";
                case DetailPanel.SCREENSHOT -> "Attach the screenshot you just took";
                case DetailPanel.DESTINATION -> "Use this as the HUD destination. Never teleports.";
                case DetailPanel.DELETE -> "Remove this entry from the journal";
                case ACTION_CLEAR_SEARCH -> "Clear the search box";
                default -> null;
            };
            if (tooltip != null) {
                context.drawTooltip(textRenderer, List.of(Text.literal(tooltip)), mouseX, mouseY);
            }
            return;
        }
    }

    // ------------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = (int) mouseX;
            int y = (int) mouseY;
            for (HitRegion region : regions) {
                if (region.contains(x, y) && handleClick(region)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleClick(HitRegion region) {
        switch (region.action()) {
            case ACTION_SORT -> {
                sortMode = sortMode.next();
                refreshResults();
            }
            case ACTION_DIMENSION_CYCLE -> {
                // -1 means "all dimensions"; each click steps through the dimensions found in data.
                dimensionIndex = dimensionIndex >= dimensions.size() - 1 ? -1 : dimensionIndex + 1;
                query.clearDimensions();
                if (dimensionIndex >= 0) {
                    query.toggleDimension(dimensions.get(dimensionIndex));
                }
                refreshResults();
            }
            case ACTION_FAVORITES_ONLY -> {
                query.setFavoritesOnly(!query.favoritesOnly());
                refreshResults();
            }
            case ACTION_CATEGORY -> {
                query.toggleCategory(EntryCategory.values()[region.payload()]);
                refreshResults();
            }
            case ACTION_CLEAR_FILTERS -> {
                query.clearAll();
                dimensionIndex = -1;
                if (searchField != null) {
                    searchField.setText("");
                }
                refreshResults();
            }
            case ACTION_CLEAR_SEARCH -> {
                if (searchField != null) {
                    searchField.setText("");
                    searchField.setFocused(false);
                }
                query.setSearchText("");
                refreshResults();
            }
            case ACTION_ROW -> selectIndex(region.payload());
            case DetailPanel.FAVORITE -> withSelection(id -> {
                manager.toggleFavorite(id);
                refreshResults();
            });
            case DetailPanel.DESTINATION -> withSelection(id -> {
                if (id.equals(manager.destinationEntryId())) {
                    manager.clearDestination();
                } else {
                    manager.setDestination(id);
                    setStatus("Destination set: " + manager.store().entry(id).map(TraceEntry::name).orElse(""));
                }
                refreshResults();
            });
            case DetailPanel.COPY -> selected().ifPresent(this::copyCoordinates);
            case DetailPanel.EDIT -> selected()
                    .ifPresent(entry -> client.setScreen(new EntryEditorScreen(manager, entry, this)));
            case DetailPanel.DELETE -> {
                if (manager.store().readOnly()) {
                    setStatus("Journal is read-only; nothing was deleted");
                } else {
                    withSelection(id -> {
                        manager.deleteEntry(id);
                        selectedEntryId = null;
                        refreshResults();
                    });
                }
            }
            case DetailPanel.SCREENSHOT -> withSelection(this::attachRecentScreenshot);
            case ACTION_ADD_BOOKMARK -> addBookmarkHere();
            case ACTION_ADD_PIN -> openPinEditor();
            case ACTION_MANAGE_PINS -> client.setScreen(new PinListScreen(manager, this));
            case ACTION_CLEAR_DESTINATION -> {
                manager.clearDestination();
                refreshResults();
            }
            case ACTION_BACK_TO_LIST -> {
                detailOnly = false;
                rebuildRegions();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Runs {@code action} only when something is selected and the journal is writable. */
    private void withSelection(java.util.function.Consumer<String> action) {
        String id = selectedEntryId;
        if (id == null) {
            return;
        }
        if (manager.store().readOnly()) {
            setStatus("Journal is read-only (file written by a newer version)");
            return;
        }
        action.accept(id);
    }

    private void attachRecentScreenshot(String id) {
        String attached = manager.attachMostRecentScreenshot(id);
        setStatus(attached == null
                ? "No recent screenshot found — take one with F2 first"
                : "Screenshot attached");
        refreshResults();
    }

    private void copyCoordinates(TraceEntry entry) {
        String text = entry.x() + ", " + entry.y() + ", " + entry.z()
                + "  (" + NameFormatting.prettifyIdentifier(entry.dimensionId()) + ")";
        client.keyboard.setClipboard(text);
        setStatus("Copied " + text);
    }

    private void addBookmarkHere() {
        if (client.player == null || client.world == null) {
            setStatus("You need to be in a world to add a bookmark");
            return;
        }
        if (manager.store().readOnly()) {
            setStatus("Journal is read-only (file written by a newer version)");
            return;
        }
        var position = client.player.getBlockPos();
        String dimension = client.world.getRegistryKey().getValue().toString();
        TraceEntry entry = manager.createBookmark(null, dimension, position.getX(), position.getY(),
                position.getZ(), System.currentTimeMillis());
        selectedEntryId = entry.id();
        sortMode = SortMode.NEWEST;
        query.clearAll();
        dimensionIndex = -1;
        if (searchField != null) {
            searchField.setText("");
        }
        refreshResults();
        setStatus("Bookmarked " + entry.name());
    }

    private void openPinEditor() {
        if (client.player == null || client.world == null) {
            setStatus("You need to be in a world to place a watch pin");
            return;
        }
        if (manager.store().readOnly()) {
            setStatus("Journal is read-only (file written by a newer version)");
            return;
        }
        client.setScreen(new PinEditorScreen(manager, client, this));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (showList() && results.size() > layout.visibleRows()) {
            int step = Math.max(1, layout.visibleRows() / 3);
            scrollOffset = Math.max(0,
                    Math.min(results.size() - layout.visibleRows(),
                            scrollOffset + (int) Math.signum(vertical) * step));
            rebuildRegions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The search field gets first refusal so typing works while it has focus.
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        int page = layout == null ? 5 : Math.max(1, layout.visibleRows());
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> moveSelection(1);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> moveSelection(-1);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> moveSelection(page);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> moveSelection(-page);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> selectIndex(0);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> selectIndex(results.size() - 1);
            case org.lwjgl.glfw.GLFW.GLFW_KEY_F -> withSelection(id -> {
                manager.toggleFavorite(id);
                refreshResults();
            });
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                if (selectedEntryId != null) {
                    withSelection(id -> {
                        manager.deleteEntry(id);
                        selectedEntryId = null;
                        refreshResults();
                    });
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void moveSelection(int delta) {
        if (results.isEmpty()) {
            return;
        }
        int current = indexOfSelection();
        int target = current < 0 ? 0 : Math.max(0, Math.min(results.size() - 1, current + delta));
        selectIndex(target);
        if (target < scrollOffset) {
            scrollOffset = target;
        } else if (target >= scrollOffset + layout.visibleRows()) {
            scrollOffset = target - layout.visibleRows() + 1;
        }
        refreshResults();
    }

    private int indexOfSelection() {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).id().equals(selectedEntryId)) {
                return i;
            }
        }
        return -1;
    }

    private void selectIndex(int index) {
        if (index < 0 || index >= results.size()) {
            return;
        }
        selectedEntryId = results.get(index).id();
        if (layout.narrow()) {
            detailOnly = true;
        }
        rebuildRegions();
    }

    private boolean showList() {
        return !layout.narrow() || !detailOnly;
    }

    private boolean showDetail() {
        return !layout.narrow() || detailOnly;
    }

    /** Sets a transient status message shown in the header line. */
    public void setStatus(String message) {
        statusMessage = message;
        statusUntilMillis = System.currentTimeMillis() + 4000;
    }

    /** Reopens the journal after a child screen changed the journal. */
    public void reopen() {
        if (client != null) {
            client.setScreen(this);
        }
    }

}
