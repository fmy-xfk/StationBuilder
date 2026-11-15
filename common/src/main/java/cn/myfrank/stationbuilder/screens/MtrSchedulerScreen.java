package cn.myfrank.stationbuilder.screens;

import cn.myfrank.stationbuilder.scheduler.MtrNetwork;
import cn.myfrank.stationbuilder.scheduler.MtrRoute;
import cn.myfrank.stationbuilder.scheduler.MtrStop;
import cn.myfrank.stationbuilder.scheduler.SchedulerAnalysis;
import cn.myfrank.stationbuilder.scheduler.SchedulerSession;
import cn.myfrank.stationbuilder.scheduler.StopTime;
import cn.myfrank.stationbuilder.scheduler.TimeFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * In-game MTR schedule planner.
 *
 * <p>A native Minecraft UI (no embedded browser) that reads MTR's in-game data through
 * {@link SchedulerSession} and renders:</p>
 * <ul>
 *   <li>a route timetable with editable departures,</li>
 *   <li>a platform occupancy chart (one row per platform),</li>
 *   <li>a platform conflict / headway report with depot-offset suggestions,</li>
 * </ul>
 *
 * <p>Only version-stable drawing and input APIs are used, so the same source serves the 1.20.x and
 * 1.21.x Fabric targets. Nothing in this screen writes to MTR.</p>
 */
public class MtrSchedulerScreen extends Screen {

    private static final int ROW_HEIGHT = 11;
    private static final int FIELD_HEIGHT = 14;

    private static final int COLOR_BG = 0xF00E1217;
    private static final int COLOR_PANEL = 0xFF1A2029;
    private static final int COLOR_PANEL_DARK = 0xFF141922;
    private static final int COLOR_BORDER = 0xFF37414C;
    private static final int COLOR_TEXT = 0xFFE6ECF2;
    private static final int COLOR_TEXT_DIM = 0xFF95A3B2;
    private static final int COLOR_ACCENT = 0xFF4DA3FF;
    private static final int COLOR_BUTTON = 0xFF262F3B;
    private static final int COLOR_BUTTON_HOVER = 0xFF34425A;
    private static final int COLOR_BUTTON_OFF = 0xFF1B222B;
    private static final int COLOR_ERROR = 0xFFFF6B6B;
    private static final int COLOR_WARNING = 0xFFFFC857;
    private static final int COLOR_OK = 0xFF6BCB77;
    private static final int COLOR_ROW_SELECTED = 0xFF2C3B4E;
    private static final int COLOR_ROW_HOVER = 0xFF232C38;

    private enum Tab {
        ROUTES("routes"),
        STATIONS("stations"),
        ISSUES("issues");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private final SchedulerSession session = SchedulerSession.get();

    private final List<Hit> hits = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();

    private Tab tab = Tab.ROUTES;

    private Field startField;
    private Field intervalField;
    private Field countField;
    private Field windowFromField;
    private Field windowToField;
    private Field focusedField;

    private int routePage;
    private int stationPage;
    private int issuePage;
    private int tripIndex;
    private int timetableScroll;
    private int lastTimetableRows = 1;
    private int lastTimetableX;
    private int lastTimetableY;
    private int lastTimetableW;
    private int lastTimetableH;
    private int lastTimetableMaxScroll;
    private int platformPage;
    private MtrNetwork lastSyncedNetwork;
    private String lastSyncedRouteId;
    private boolean choosingFollow;
    private String message;
    private int messageTicks;

    private int mouseX;
    private int mouseY;
    private String hoverTooltip;


    public MtrSchedulerScreen() {
        super(Text.translatable("gui.stationbuilder.scheduler.title"));
        createFields();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        session.ensureLoaded();
        syncWindowFields();
    }

    @Override
    public void tick() {
        session.poll();
        if (messageTicks > 0) {
            messageTicks--;
            if (messageTicks == 0) {
                message = null;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        hoverTooltip = null;

        int pad = 6;
        int topY = pad;
        int topH = 20;
        int tabY = topY + topH + 4;
        int tabH = 16;
        int bodyY = tabY + tabH + 4;
        int footerY = height - 12;
        int bodyH = Math.max(30, footerY - bodyY - 3);
        int sideW = Math.max(92, Math.min(168, width / 3));
        int mainX = pad + sideW + 4;
        int mainW = Math.max(80, width - mainX - pad);

        context.fill(0, 0, width, height, COLOR_BG);
        renderTopBar(context, pad, topY, topH);
        renderTabs(context, pad, tabY, tabH);
        renderBody(context, pad, bodyY, bodyH, sideW, mainX, mainW);
        renderFooter(context, pad, footerY);

        if (hoverTooltip != null) {
            renderTooltipBox(context, hoverTooltip);
        }
    }

    private void renderTopBar(DrawContext context, int pad, int y, int h) {
        drawPanel(context, pad, y, width - pad * 2, h);

        String title = tr("gui.stationbuilder.scheduler.title");
        drawText(context, title, pad + 5, y + (h - 9) / 2 + 1, COLOR_ACCENT);

        int closeW = 34;
        int reloadW = 38;
        int saveW = 76;
        int reloadX = width - pad - closeW - reloadW - 4;
        int saveX = reloadX - saveW - 4;
        button(context, saveX, y + 3, saveW, h - 6,
                tr("gui.stationbuilder.scheduler.save_timetable"),
                !session.isLoading(), session::writeCurrentSchedule);
        button(context, reloadX, y + 3, reloadW, h - 6,
                tr("gui.stationbuilder.scheduler.reload"),
                !session.isLoading(), session::refresh);
        button(context, width - pad - closeW, y + 3, closeW, h - 6,
                tr("gui.stationbuilder.scheduler.close"),
                true, this::close);
    }

    private void renderTabs(DrawContext context, int pad, int y, int h) {
        Tab[] tabs = Tab.values();
        int tabW = Math.max(52, (width - pad * 2) / tabs.length);
        for (int index = 0; index < tabs.length; index++) {
            Tab candidate = tabs[index];
            int x = pad + index * tabW;
            boolean active = candidate == tab;
            boolean hovered = inside(mouseX, mouseY, x, y, tabW - 2, h);
            context.fill(x, y, x + tabW - 2, y + h,
                    active ? COLOR_PANEL : (hovered ? COLOR_ROW_HOVER : COLOR_PANEL_DARK));
            border(context, x, y, tabW - 2, h, active ? COLOR_ACCENT : COLOR_BORDER);
            drawCenteredText(context,
                    Text.translatable("gui.stationbuilder.scheduler.tab." + candidate.key).getString(),
                    x, y + (h - 9) / 2 + 1, tabW - 2, active ? COLOR_ACCENT : COLOR_TEXT_DIM);
            if (!active) {
                addHit(x, y, tabW - 2, h, () -> {
                    focusField(null);
                    tab = candidate;
                    choosingFollow = false;
                });
            }
        }
    }

    private void renderBody(
            DrawContext context,
            int pad,
            int bodyY,
            int bodyH,
            int sideW,
            int mainX,
            int mainW
    ) {
        if (session.network() == null) {
            renderLoadingOrError(context, pad, bodyY, bodyH);
            return;
        }
        if (session.network().routes.isEmpty()) {
            renderEmptyState(context, pad, bodyY, bodyH);
            return;
        }
        switch (tab) {
            case ROUTES -> {
                renderSidebar(context, pad, bodyY, bodyH, sideW);
                renderRoutePanel(context, bodyY, bodyH, mainX, mainW);
            }
            case STATIONS -> {
                renderSidebar(context, pad, bodyY, bodyH, sideW);
                renderStationPanel(context, bodyY, bodyH, mainX, mainW);
            }
            case ISSUES -> renderIssuesPanel(context, pad, bodyY, bodyH, width - pad * 2);
        }
    }

    private void renderLoadingOrError(DrawContext context, int pad, int bodyY, int bodyH) {
        int w = width - pad * 2;
        drawPanel(context, pad, bodyY, w, bodyH);
        int centerY = bodyY + bodyH / 2 - 18;

        boolean error = session.state() == SchedulerSession.LoadState.ERROR;
        drawCenteredText(context,
                Text.translatable(error
                        ? "gui.stationbuilder.scheduler.error"
                        : "gui.stationbuilder.scheduler.loading").getString(),
                0, centerY, width, error ? COLOR_ERROR : COLOR_ACCENT);

        String status = session.status();
        if (!status.isEmpty()) {
            drawCenteredText(context, trim(status, w - 20), 0, centerY + 14, width, COLOR_TEXT_DIM);
        }
        if (error) {
            button(context, width / 2 - 45, centerY + 56, 90, 16,
                    Text.translatable("gui.stationbuilder.scheduler.retry").getString(),
                    true, session::refresh);
        }
    }

    private void renderEmptyState(DrawContext context, int pad, int bodyY, int bodyH) {
        int w = width - pad * 2;
        drawPanel(context, pad, bodyY, w, bodyH);
        int centerY = bodyY + bodyH / 2 - 22;

        drawCenteredText(context,
                Text.translatable("gui.stationbuilder.scheduler.no_routes_title").getString(),
                0, centerY, width, COLOR_ACCENT);
        drawCenteredText(context,
                Text.translatable("gui.stationbuilder.scheduler.no_routes_hint").getString(),
                0, centerY + 16, width, COLOR_TEXT_DIM);
        if (session.state() == SchedulerSession.LoadState.LOADING) {
            drawCenteredText(context,
                    Text.translatable("gui.stationbuilder.scheduler.loading").getString(),
                    0, centerY + 60, width, COLOR_WARNING);
        } else {
            button(context, width / 2 - 45, centerY + 58, 90, 16,
                    Text.translatable("gui.stationbuilder.scheduler.reload").getString(),
                    true, session::refresh);
        }
    }

    // ------------------------------------------------------------------
    // Sidebar
    // ------------------------------------------------------------------

    private void renderSidebar(DrawContext context, int pad, int y, int h, int w) {
        drawPanel(context, pad, y, w, h);

        boolean routesMode = tab == Tab.ROUTES && !choosingFollow;
        int page = routesMode ? routePage : stationPage;
        int count = routesMode ? session.routes().size() : session.stationIds().size();
        int rows = Math.max(1, (h - 20) / ROW_HEIGHT);
        int pageCount = Math.max(1, (count + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pageCount - 1));
        final int currentPage = page;

        String header;
        if (choosingFollow) {
            header = Text.translatable("gui.stationbuilder.scheduler.choose_preceding").getString();
        } else if (routesMode) {
            header = Text.translatable("gui.stationbuilder.scheduler.routes").getString()
                    + " (" + session.routes().size() + ")";
        } else {
            header = Text.translatable("gui.stationbuilder.scheduler.stations").getString()
                    + " (" + session.stationIds().size() + ")";
        }
        drawText(context, trim(header, w - 36), pad + 4, y + 5, COLOR_ACCENT);

        if (choosingFollow) {
            button(context, pad + w - 16, y + 3, 12, 11, "x", true, () -> choosingFollow = false);
        } else {
            button(context, pad + w - 30, y + 3, 12, 11, "<", currentPage > 0,
                    () -> setPage(routesMode, currentPage - 1));
            button(context, pad + w - 16, y + 3, 12, 11, ">", currentPage < pageCount - 1,
                    () -> setPage(routesMode, currentPage + 1));
        }

        int listY = y + 18;
        int listH = h - 22;
        if (listH <= 2) {
            return;
        }
        context.enableScissor(pad + 1, listY, pad + w - 1, listY + listH);

        int drawn = 0;
        if (choosingFollow) {
            MtrRoute selected = session.selectedRoute();
            List<MtrRoute> candidates = selected == null ? List.of() : session.followCandidates(selected.id);
            for (int index = 0; index < candidates.size() && drawn < rows; index++) {
                MtrRoute candidate = candidates.get(index);
                int rowY = listY + drawn * ROW_HEIGHT;
                if (inside(mouseX, mouseY, pad + 2, rowY, w - 4, ROW_HEIGHT)) {
                    context.fill(pad + 2, rowY, pad + w - 2, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
                }
                drawRouteRow(context, pad + 4, rowY, w - 8, candidate, false);
                addHit(pad + 2, rowY, w - 4, ROW_HEIGHT, () -> {
                    MtrRoute current = session.selectedRoute();
                    if (current != null) {
                        String error = session.setFollow(current.id, candidate.id);
                        showMessage(error);
                        if (error == null) {
                            choosingFollow = false;
                            focusField(null);
                            syncDepartureFields(current);
                        }
                    }
                });
                drawn++;
            }
            if (candidates.isEmpty()) {
                drawText(context, Text.translatable("gui.stationbuilder.scheduler.no_candidates").getString(),
                        pad + 5, listY + 3, COLOR_TEXT_DIM);
            }
        } else if (routesMode) {
            List<MtrRoute> routes = session.routes();
            for (int index = page * rows; index < routes.size() && drawn < rows; index++) {
                MtrRoute route = routes.get(index);
                int rowY = listY + drawn * ROW_HEIGHT;
                boolean selected = route.id.equals(session.selectedRouteId());
                if (selected) {
                    context.fill(pad + 2, rowY, pad + w - 2, rowY + ROW_HEIGHT, COLOR_ROW_SELECTED);
                } else if (inside(mouseX, mouseY, pad + 2, rowY, w - 4, ROW_HEIGHT)) {
                    context.fill(pad + 2, rowY, pad + w - 2, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
                }
                drawRouteRow(context, pad + 4, rowY, w - 8, route, selected);
                addHit(pad + 2, rowY, w - 4, ROW_HEIGHT, () -> {
                    focusField(null);
                    session.selectRoute(route.id);
                    tripIndex = 0;
                    timetableScroll = 0;
                    choosingFollow = false;
                });
                drawn++;
            }
        } else {
            List<String> stations = session.stationIds();
            for (int index = page * rows; index < stations.size() && drawn < rows; index++) {
                String stationId = stations.get(index);
                int rowY = listY + drawn * ROW_HEIGHT;
                boolean selected = stationId.equals(session.selectedStationId());
                if (selected) {
                    context.fill(pad + 2, rowY, pad + w - 2, rowY + ROW_HEIGHT, COLOR_ROW_SELECTED);
                } else if (inside(mouseX, mouseY, pad + 2, rowY, w - 4, ROW_HEIGHT)) {
                    context.fill(pad + 2, rowY, pad + w - 2, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
                }
                drawText(context, trim(session.network().stationName(stationId), w - 12),
                        pad + 5, rowY + 2, selected ? COLOR_TEXT : COLOR_TEXT_DIM);
                addHit(pad + 2, rowY, w - 4, ROW_HEIGHT, () -> {
                    focusField(null);
                    session.selectStation(stationId);
                    platformPage = 0;
                });
                drawn++;
            }
        }
        context.disableScissor();
    }

    private void setPage(boolean routesMode, int page) {
        if (routesMode) {
            routePage = Math.max(0, page);
        } else {
            stationPage = Math.max(0, page);
        }
    }

    private void drawRouteRow(DrawContext context, int x, int y, int w, MtrRoute route, boolean selected) {
        context.fill(x, y + 2, x + 2, y + ROW_HEIGHT - 2, route.argb());
        drawText(context, trim(route.label(), w - 32), x + 5, y + 2,
                selected ? COLOR_TEXT : COLOR_TEXT_DIM);
        String trips = String.valueOf(route.startTimes().size());
        drawText(context, trips, x + w - textRenderer.getWidth(trips) - 1, y + 2,
                route.hasDepartures() ? COLOR_TEXT_DIM : COLOR_WARNING);
    }

    // ------------------------------------------------------------------
    // Routes tab
    // ------------------------------------------------------------------

    private void renderRoutePanel(DrawContext context, int y, int bodyH, int x, int w) {
        drawPanel(context, x, y, w, bodyH);
        MtrRoute route = session.selectedRoute();
        if (route == null) {
            drawText(context, Text.translatable("gui.stationbuilder.scheduler.select_route").getString(),
                    x + 8, y + 8, COLOR_TEXT_DIM);
            return;
        }
        if (lastSyncedNetwork != session.network() || !route.id.equals(lastSyncedRouteId)) {
            if (lastSyncedNetwork != session.network()) {
                timetableScroll = 0;
            }
            if (focusedField == null) {
                lastSyncedNetwork = session.network();
                syncDepartureFields(route);
            }
        }

        String depot = route.primaryDepot() == null
                ? Text.translatable("gui.stationbuilder.scheduler.no_depot").getString()
                : route.primaryDepot();
        drawText(context, trim(route.label(), w - 16), x + 6, y + 5, COLOR_TEXT);
        String depotLine = Text.translatable("gui.stationbuilder.scheduler.depot").getString() + ": " + depot;
        if (!route.startTimes().isEmpty()) {
            depotLine += " · " + route.startTimes().size() + " "
                    + Text.translatable("gui.stationbuilder.scheduler.departures").getString();
        }
        drawText(context, trim(depotLine, w - 16), x + 6, y + 15, COLOR_TEXT_DIM);

        String following = session.followedBy(route.id);
        if (following != null) {
            MtrRoute preceding = session.route(following);
            String auto = session.isAutoFollow(route.id)
                    ? " (" + Text.translatable("gui.stationbuilder.scheduler.auto").getString() + ")"
                    : "";
            drawText(context, trim(Text.translatable("gui.stationbuilder.scheduler.follows").getString()
                    + ": " + (preceding == null ? following : preceding.label()) + auto, w - 16),
                    x + 6, y + 25, COLOR_OK);
        }

        int warningY = following == null ? y + 25 : y + 35;
        if (route.repeatInfinitely || route.departuresIgnored()) {
            String warning = route.repeatInfinitely
                    ? Text.translatable("gui.stationbuilder.scheduler.repeat_warning").getString()
                    : Text.translatable("gui.stationbuilder.scheduler.abnormal_warning").getString();
            if (route.repeatInfinitely && !route.repeatDepots.isEmpty()) {
                warning += " (" + String.join(", ", route.repeatDepots) + ")";
            }
            drawText(context, trim(warning, w - 16), x + 6, warningY, COLOR_WARNING);
        }

        int controlsY = y + 46;
        int fieldGap = 14;
        int fieldW = Math.max(34, (w - 12 - fieldGap * 2) / 3);
        startField.setBounds(x + 6, controlsY, fieldW, FIELD_HEIGHT);
        intervalField.setBounds(x + 6 + fieldW + fieldGap, controlsY, fieldW, FIELD_HEIGHT);
        countField.setBounds(x + 6 + (fieldW + fieldGap) * 2, controlsY, fieldW, FIELD_HEIGHT);

        drawText(context, Text.translatable("gui.stationbuilder.scheduler.start").getString(),
                x + 6, controlsY - 9, COLOR_TEXT_DIM);
        drawText(context, Text.translatable("gui.stationbuilder.scheduler.interval").getString(),
                x + 6 + fieldW + fieldGap, controlsY - 9, COLOR_TEXT_DIM);
        drawText(context, Text.translatable("gui.stationbuilder.scheduler.count").getString(),
                x + 6 + (fieldW + fieldGap) * 2, controlsY - 9, COLOR_TEXT_DIM);

        renderField(context, startField);
        renderField(context, intervalField);
        renderField(context, countField);

        int actionRow = controlsY + FIELD_HEIGHT + 4;
        int cursor = x + 6;
        button(context, cursor, actionRow, 50, 12,
                Text.translatable("gui.stationbuilder.scheduler.generate").getString(),
                true, () -> applyDepartureFields(route));
        cursor += 54;
        button(context, cursor, actionRow, 42, 12,
                Text.translatable("gui.stationbuilder.scheduler.follow").getString(),
                true, () -> choosingFollow = true);
        cursor += 45;
        button(context, cursor, actionRow, 38, 12,
                Text.translatable("gui.stationbuilder.scheduler.clear").getString(),
                following != null, () -> session.clearFollow(route.id));
        cursor += 41;

        int smallW = 26;
        int shiftRow = actionRow;
        boolean shiftInline = (x + w - 6) - cursor >= smallW * 4 + 9;
        if (!shiftInline) {
            shiftRow = actionRow + 15;
            cursor = x + 6;
        }
        button(context, cursor, shiftRow, smallW, 12, "-5m", true,
                () -> session.offsetRoute(route, -5 * TimeFormat.MINUTE_MS));
        cursor += smallW + 3;
        button(context, cursor, shiftRow, smallW, 12, "-1m", true,
                () -> session.offsetRoute(route, -TimeFormat.MINUTE_MS));
        cursor += smallW + 3;
        button(context, cursor, shiftRow, smallW, 12, "+1m", true,
                () -> session.offsetRoute(route, TimeFormat.MINUTE_MS));
        cursor += smallW + 3;
        button(context, cursor, shiftRow, smallW, 12, "+5m", true,
                () -> session.offsetRoute(route, 5 * TimeFormat.MINUTE_MS));

        int tableTop = shiftRow + 18;
        renderTimetable(context, x + 4, tableTop, w - 8, y + bodyH - tableTop - 4, route);
    }

    private void renderTimetable(DrawContext context, int x, int y, int w, int h, MtrRoute route) {
        List<List<StopTime>> timelines = route.timelines();
        if (timelines.isEmpty()) {
            String emptyKey = route.repeatInfinitely || route.departuresIgnored()
                    ? "gui.stationbuilder.scheduler.repeat_no_departures"
                    : "gui.stationbuilder.scheduler.no_departures";
            drawText(context, Text.translatable(emptyKey).getString(), x + 2, y + 2, COLOR_WARNING);
            return;
        }
        tripIndex = Math.max(0, Math.min(tripIndex, timelines.size() - 1));
        List<StopTime> timeline = timelines.get(tripIndex);

        String tripLabel = Text.translatable("gui.stationbuilder.scheduler.trip").getString()
                + " " + (tripIndex + 1) + "/" + timelines.size();
        drawText(context, tripLabel, x + 2, y, COLOR_TEXT_DIM);
        int navX = x + w - 30;
        button(context, navX, y - 2, 12, 11, "<", tripIndex > 0, () -> tripIndex--);
        button(context, navX + 14, y - 2, 12, 11, ">", tripIndex < timelines.size() - 1, () -> tripIndex++);

        int headerY = y + 13;
        boolean compactTimes = w < 260;
        int timeWidth = compactTimes ? 32 : 50;
        int columnStation = x + 2;
        int columnDeparture = x + w - timeWidth - 2;
        int columnArrival = columnDeparture - timeWidth - 2;
        int columnPlatform = x + Math.max(compactTimes ? 54 : 70, (int) (w * 0.42));
        drawText(context, Text.translatable("gui.stationbuilder.scheduler.column_station").getString(),
                columnStation, headerY, COLOR_ACCENT);
        drawText(context, Text.translatable("gui.stationbuilder.scheduler.column_platform").getString(),
                columnPlatform, headerY, COLOR_ACCENT);
        drawText(context, Text.translatable(compactTimes
                        ? "gui.stationbuilder.scheduler.column_arrival_short"
                        : "gui.stationbuilder.scheduler.column_arrival").getString(),
                columnArrival, headerY, COLOR_ACCENT);
        drawText(context, Text.translatable(compactTimes
                        ? "gui.stationbuilder.scheduler.column_departure_short"
                        : "gui.stationbuilder.scheduler.column_departure").getString(),
                columnDeparture, headerY, COLOR_ACCENT);
        context.fill(x, headerY + 10, x + w, headerY + 11, COLOR_BORDER);

        int rowH = 11;
        int maxRows = Math.max(1, (y + h - (headerY + 14)) / rowH);
        int maxScroll = Math.max(0, timeline.size() - maxRows);
        timetableScroll = Math.max(0, Math.min(timetableScroll, maxScroll));
        lastTimetableRows = maxRows;
        lastTimetableX = x;
        lastTimetableY = headerY + 14;
        lastTimetableW = w;
        lastTimetableH = maxRows * rowH;
        lastTimetableMaxScroll = maxScroll;

        if (maxScroll > 0) {
            int scrollX = x + w - 62;
            String range = (timetableScroll + 1) + "-"
                    + Math.min(timetableScroll + maxRows, timeline.size())
                    + "/" + timeline.size();
            drawText(context, range,
                    scrollX - 4 - textRenderer.getWidth(range), y - 2, COLOR_TEXT_DIM);
            button(context, scrollX, y - 2, 12, 11, "^",
                    timetableScroll > 0, () -> timetableScroll = Math.max(0, timetableScroll - 1));
            button(context, scrollX + 14, y - 2, 12, 11, "v",
                    timetableScroll < maxScroll, () -> timetableScroll = Math.min(maxScroll, timetableScroll + 1));
        }

        int drawn = 0;
        for (int index = timetableScroll; index < timeline.size() && drawn < maxRows; index++) {
            StopTime stopTime = timeline.get(index);
            MtrStop stop = route.stops.get(index);
            int rowY = headerY + 14 + drawn * rowH;
            if (drawn % 2 == 1) {
                context.fill(x, rowY - 1, x + w, rowY + rowH - 1, 0x14FFFFFF);
            }
            String stationName = session.network() == null
                    ? stop.stationId
                    : session.network().stationName(stop.stationId);
            drawText(context, trim(stationName, columnPlatform - columnStation - 6),
                    columnStation, rowY, COLOR_TEXT);
            drawText(context, trim(stop.platformName, columnArrival - columnPlatform - 6),
                    columnPlatform, rowY, COLOR_TEXT_DIM);
            drawText(context, compactTimes
                            ? TimeFormat.hm(stopTime.arrivalMs())
                            : TimeFormat.hms(stopTime.arrivalMs()),
                    columnArrival, rowY, COLOR_TEXT_DIM);
            drawText(context, compactTimes
                            ? TimeFormat.hm(stopTime.departureMs())
                            : TimeFormat.hms(stopTime.departureMs()),
                    columnDeparture, rowY, COLOR_TEXT);
            addHit(x, rowY - 1, w, rowH, () -> {
                session.selectStation(stop.stationId);
                tab = Tab.STATIONS;
                stationPage = 0;
                choosingFollow = false;
            });
            drawn++;
        }
        if (maxScroll > 0) {
            drawText(context, Text.translatable("gui.stationbuilder.scheduler.scroll_hint").getString(),
                    x + 2, y + h - 9, COLOR_TEXT_DIM);
        }
    }

    private void applyDepartureFields(MtrRoute route) {
        long start = TimeFormat.parseHms(startField.text);
        long interval = TimeFormat.parseHms(intervalField.text);
        int count;
        try {
            count = Integer.parseInt(countField.text.trim());
        } catch (NumberFormatException e) {
            count = -1;
        }
        if (start < 0L) {
            showMessage(Text.translatable("gui.stationbuilder.scheduler.invalid_start").getString());
            return;
        }
        if (interval <= 0L) {
            showMessage(Text.translatable("gui.stationbuilder.scheduler.invalid_interval").getString());
            return;
        }
        if (count <= 0 || count > 10_000) {
            showMessage(Text.translatable("gui.stationbuilder.scheduler.invalid_count").getString());
            return;
        }
        showMessage(session.generateDepartures(route, start, interval, count));
        tripIndex = 0;
    }

    // ------------------------------------------------------------------
    // Stations tab
    // ------------------------------------------------------------------

    private void renderStationPanel(DrawContext context, int y, int bodyH, int x, int w) {
        drawPanel(context, x, y, w, bodyH);
        MtrNetwork network = session.network();
        String stationId = session.selectedStationId();
        if (network == null || stationId == null) {
            drawText(context, Text.translatable("gui.stationbuilder.scheduler.select_station").getString(),
                    x + 8, y + 8, COLOR_TEXT_DIM);
            return;
        }
        if (lastSyncedNetwork != network) {
            lastSyncedNetwork = network;
            syncWindowFields();
        }

        drawText(context, trim(network.stationName(stationId), w - 16), x + 6, y + 5, COLOR_TEXT);

        int windowY = y + 17;
        int fieldW = 40;
        int fieldGap = 10;
        windowFromField.setBounds(x + 6, windowY, fieldW, FIELD_HEIGHT);
        windowToField.setBounds(x + 6 + fieldW + fieldGap, windowY, fieldW, FIELD_HEIGHT);
        renderField(context, windowFromField);
        renderField(context, windowToField);
        drawText(context, "~", x + 6 + fieldW + 2, windowY + 3, COLOR_TEXT_DIM);

        int buttonX = x + 6 + fieldW * 2 + fieldGap + 8;
        button(context, buttonX, windowY, 30, FIELD_HEIGHT,
                Text.translatable("gui.stationbuilder.scheduler.now").getString(), true, () -> {
                    long now = TimeFormat.localDayMillis(System.currentTimeMillis());
                    session.setWindow(now - 30 * TimeFormat.MINUTE_MS, now + 90 * TimeFormat.MINUTE_MS);
                    syncWindowFields();
                });
        buttonX += 34;
        button(context, buttonX, windowY, 22, FIELD_HEIGHT, "-1h", true, () -> {
            session.setWindow(session.windowStartMs() - TimeFormat.HOUR_MS,
                    session.windowEndMs() - TimeFormat.HOUR_MS);
            syncWindowFields();
        });
        buttonX += 25;
        button(context, buttonX, windowY, 22, FIELD_HEIGHT, "+1h", true, () -> {
            session.setWindow(session.windowStartMs() + TimeFormat.HOUR_MS,
                    session.windowEndMs() + TimeFormat.HOUR_MS);
            syncWindowFields();
        });

        int chartY = windowY + FIELD_HEIGHT + 6;
        renderPlatformChart(context, x + 4, chartY, w - 8, y + bodyH - chartY - 4, stationId);
    }

    private void renderPlatformChart(DrawContext context, int x, int y, int w, int h, String stationId) {
        MtrNetwork network = session.network();
        if (network == null) {
            return;
        }
        List<String> platforms = network.platformsOf(stationId);
        if (platforms.isEmpty() || h < 24 || w < 40) {
            drawText(context, Text.translatable("gui.stationbuilder.scheduler.no_platforms").getString(),
                    x + 2, y + 2, COLOR_TEXT_DIM);
            return;
        }

        long windowStart = session.windowStartMs();
        long windowEnd = Math.max(session.windowEndMs(), windowStart + TimeFormat.MINUTE_MS);
        int labelWidth = Math.min(64, Math.max(34, w / 4));
        int chartX = x + labelWidth + 4;
        int chartW = w - labelWidth - 6;
        int axisH = 12;
        int rowsArea = h - axisH - 2;
        int rowHeight = Math.max(11, Math.min(22, rowsArea / platforms.size()));
        int rows = Math.max(1, rowsArea / rowHeight);
        int pageCount = Math.max(1, (platforms.size() + rows - 1) / rows);
        platformPage = Math.max(0, Math.min(platformPage, pageCount - 1));

        if (pageCount > 1) {
            button(context, x + w - 28, y - 1, 12, 11, "<", platformPage > 0, () -> platformPage--);
            button(context, x + w - 14, y - 1, 12, 11, ">", platformPage < pageCount - 1, () -> platformPage++);
            drawText(context, (platformPage + 1) + "/" + pageCount, x + w - 50, y + 1, COLOR_TEXT_DIM);
        }

        long span = windowEnd - windowStart;
        long step = chooseAxisStep(span, chartW);
        context.fill(chartX, y + axisH, chartX + chartW, y + axisH + 1, COLOR_BORDER);
        long tick = (Math.floorDiv(windowStart, step) + 1) * step;
        while (tick < windowEnd) {
            int tickX = timeToX(tick, windowStart, windowEnd, chartX, chartW);
            context.fill(tickX, y + axisH, tickX + 1, y + h, 0x22FFFFFF);
            drawText(context, TimeFormat.hm(tick), tickX + 2, y + 1, COLOR_TEXT_DIM);
            tick += step;
        }
        drawText(context, TimeFormat.hm(windowStart), chartX + 1, y + 1, COLOR_ACCENT);

        int firstPlatform = platformPage * rows;
        for (int rowIndex = 0; rowIndex < rows && firstPlatform + rowIndex < platforms.size(); rowIndex++) {
            int platformIndex = firstPlatform + rowIndex;
            String platform = platforms.get(platformIndex);
            int rowY = y + axisH + 2 + rowIndex * rowHeight;
            if (rowIndex % 2 == 1) {
                context.fill(x, rowY, x + w, rowY + rowHeight, 0x10FFFFFF);
            }
            drawText(context, trim(platform, labelWidth - 2), x + 1, rowY + (rowHeight - 9) / 2, COLOR_TEXT_DIM);

            for (Bar bar : barsFor(network, stationId, platform, windowStart, windowEnd)) {
                long clippedStart = Math.max(bar.start, windowStart);
                long clippedEnd = Math.min(bar.end, windowEnd);
                if (clippedEnd <= clippedStart) {
                    continue;
                }
                int barX = timeToX(clippedStart, windowStart, windowEnd, chartX, chartW);
                int barEndX = timeToX(clippedEnd, windowStart, windowEnd, chartX, chartW);
                int barWidth = Math.max(2, barEndX - barX);
                int barY = rowY + Math.max(1, (rowHeight - 7) / 2);
                boolean hovered = inside(mouseX, mouseY, barX, rowY, Math.max(barWidth, 3), rowHeight);
                context.fill(barX, barY, barX + barWidth, barY + 7,
                        hovered ? brighten(bar.route.argb()) : bar.route.argb());
                if (hovered) {
                    border(context, barX - 1, barY - 1, barWidth + 2, 9, 0xFFFFFFFF);
                    hoverTooltip = bar.route.label() + "  " + TimeFormat.hms(bar.start) + " - "
                            + TimeFormat.hms(bar.end) + "  (" + bar.route.startTimes().size() + " "
                            + Text.translatable("gui.stationbuilder.scheduler.trips").getString() + ")";
                }
                String number = bar.route.number;
                if (barWidth >= textRenderer.getWidth(number) + 4 && number != null && !number.isEmpty()) {
                    drawText(context, number, barX + 2, barY, 0xFF10141A);
                }
            }
        }
    }

    private static int timeToX(long time, long windowStart, long windowEnd, int chartX, int chartW) {
        double ratio = (double) (time - windowStart) / (double) (windowEnd - windowStart);
        return chartX + (int) Math.round(ratio * chartW);
    }

    private static long chooseAxisStep(long span, int chartWidth) {
        long[] candidates = {
                5 * TimeFormat.MINUTE_MS,
                10 * TimeFormat.MINUTE_MS,
                15 * TimeFormat.MINUTE_MS,
                30 * TimeFormat.MINUTE_MS,
                TimeFormat.HOUR_MS,
                2 * TimeFormat.HOUR_MS,
                4 * TimeFormat.HOUR_MS
        };
        for (long candidate : candidates) {
            if ((double) candidate / (double) span * chartWidth >= 42.0) {
                return candidate;
            }
        }
        return candidates[candidates.length - 1];
    }

    private List<Bar> barsFor(
            MtrNetwork network,
            String stationId,
            String platform,
            long windowStart,
            long windowEnd
    ) {
        List<Bar> bars = new ArrayList<>();
        Map<String, String> follows = session.follows();
        for (MtrRoute route : network.routes) {
            boolean followed = network.isPlatformContinuation(follows, route);
            List<List<StopTime>> timelines = route.timelines();
            for (List<StopTime> timeline : timelines) {
                for (int index = 0; index < timeline.size(); index++) {
                    if (index == 0 && followed) {
                        continue;
                    }
                    MtrStop stop = route.stops.get(index);
                    if (!stop.stationId.equals(stationId) || !stop.platformName.equals(platform)) {
                        continue;
                    }
                    StopTime stopTime = timeline.get(index);
                    for (int shift = -1; shift <= 1; shift++) {
                        long start = stopTime.arrivalMs() + shift * TimeFormat.DAY_MS;
                        long end = stopTime.departureMs() + shift * TimeFormat.DAY_MS;
                        if (end > windowStart && start < windowEnd) {
                            bars.add(new Bar(route, start, end));
                        }
                    }
                }
            }
        }
        bars.sort((left, right) -> Long.compare(left.start, right.start));
        return bars;
    }

    // ------------------------------------------------------------------
    // Issues tab
    // ------------------------------------------------------------------

    private void renderIssuesPanel(DrawContext context, int x, int y, int h, int w) {
        drawPanel(context, x, y, w, h);
        SchedulerAnalysis analysis = session.analysis();
        MtrNetwork network = session.network();

        int color = analysis.overlapCount() > 0 ? COLOR_ERROR
                : (analysis.warningCount() > 0 ? COLOR_WARNING : COLOR_OK);
        List<String> summaryLines = wrapText(conflictSummary(analysis), w - 10);
        int cursor = y + 5;
        for (String line : summaryLines) {
            drawText(context, line, x + 5, cursor, color);
            cursor += 10;
        }
        cursor += 3;

        String offsets = tr("gui.stationbuilder.scheduler.suggested_offsets") + ": ";
        if (analysis.depotOffsetsMs().isEmpty()) {
            offsets += tr("gui.stationbuilder.scheduler.none");
        } else {
            List<String> parts = new ArrayList<>();
            analysis.depotOffsetsMs().forEach((depot, offset) ->
                    parts.add(depot + " " + TimeFormat.offset(offset)));
            parts.sort(String.CASE_INSENSITIVE_ORDER);
            offsets += String.join("   ", parts);
        }
        button(context, x + w - 74, cursor - 2, 68, 13,
                tr("gui.stationbuilder.scheduler.apply_offsets"),
                analysis.solvable() && !session.isLoading(), session::writeOffsets);
        drawText(context, trim(offsets, w - 88), x + 5, cursor, COLOR_TEXT_DIM);
        cursor += 14;

        int listY = cursor;
        int rowH = 11;
        List<SchedulerAnalysis.ConflictGroup> groups = analysis.groups();
        int listBottom = y + h - 4;
        int rows = Math.max(1, (listBottom - listY) / rowH);
        int count = groups.size();
        int pageCount = Math.max(1, (count + rows - 1) / rows);
        if (pageCount > 1) {
            listBottom -= 14;
            rows = Math.max(1, (listBottom - listY) / rowH);
            pageCount = Math.max(1, (count + rows - 1) / rows);
        }
        issuePage = Math.max(0, Math.min(issuePage, pageCount - 1));

        if (pageCount > 1) {
            String pageLabel = (issuePage + 1) + "/" + pageCount;
            drawText(context, pageLabel,
                    x + w - 36 - textWidth(pageLabel), y + h - 12, COLOR_TEXT_DIM);
            button(context, x + w - 30, y + h - 14, 12, 11, "<", issuePage > 0, () -> issuePage--);
            button(context, x + w - 16, y + h - 14, 12, 11, ">", issuePage < pageCount - 1, () -> issuePage++);
        }

        if (groups.isEmpty()) {
            drawText(context, tr("gui.stationbuilder.scheduler.no_issues"), x + 5, listY, COLOR_OK);
            return;
        }
        if (listBottom <= listY) {
            return;
        }
        context.enableScissor(x + 1, listY, x + w - 1, listBottom);
        int drawn = 0;
        for (int index = issuePage * rows; index < count && drawn < rows; index++) {
            SchedulerAnalysis.ConflictGroup group = groups.get(index);
            int rowY = listY + drawn * rowH;
            boolean hovered = inside(mouseX, mouseY, x + 2, rowY, w - 4, rowH);
            if (hovered) {
                context.fill(x + 2, rowY, x + w - 2, rowY + rowH, COLOR_ROW_HOVER);
            }
            boolean overlap = group.hasOverlap();
            drawText(context, overlap ? "!" : "~", x + 4, rowY + 1, overlap ? COLOR_ERROR : COLOR_WARNING);
            String stationName = network == null ? group.stationId : network.stationName(group.stationId);
            String firstRoute = routeLabel(network, group.firstRouteId);
            String secondRoute = routeLabel(network, group.secondRouteId);
            String gapText = overlap
                    ? tr("gui.stationbuilder.scheduler.conflict_overlap", Math.max(0L, -group.worstGapMs) / 1000L)
                    : tr("gui.stationbuilder.scheduler.conflict_gap", Math.max(0L, group.worstGapMs) / 1000L);
            String text = stationName + " · " + group.platform + " · "
                    + firstRoute + " " + tr("gui.stationbuilder.scheduler.conflict_vs") + " "
                    + secondRoute + " · " + group.occurrences + "× · " + gapText;
            drawText(context, trim(text, w - 22), x + 13, rowY + 1,
                    overlap ? COLOR_ERROR : COLOR_WARNING);
            if (hovered) {
                hoverTooltip = stationName + " · " + group.platform + " · "
                        + firstRoute + " " + tr("gui.stationbuilder.scheduler.conflict_vs") + " "
                        + secondRoute + " · " + group.occurrences + " "
                        + tr("gui.stationbuilder.scheduler.trips") + " · "
                        + TimeFormat.hms(group.earliestStartMs) + " - " + TimeFormat.hms(group.latestEndMs)
                        + " · " + gapText;
            }
            addHit(x + 2, rowY, w - 4, rowH, () -> {
                session.selectStation(group.stationId);
                platformPage = 0;
                tab = Tab.STATIONS;
            });
            drawn++;
        }
        context.disableScissor();
    }

    private String conflictSummary(SchedulerAnalysis analysis) {
        StringBuilder summary = new StringBuilder();
        summary.append(tr("gui.stationbuilder.scheduler.conflict_summary",
                analysis.issues().size(), analysis.groups().size()));
        if (!analysis.solvable()) {
            summary.append(" · ").append(tr("gui.stationbuilder.scheduler.summary_infeasible"));
        } else if (analysis.achievableGapMs() > 0
                && analysis.achievableGapMs() < SchedulerAnalysis.DEFAULT_SAFE_GAP_MS) {
            summary.append(" · ").append(tr("gui.stationbuilder.scheduler.summary_fallback_10s"));
        } else if (analysis.hasConstraints()) {
            summary.append(" · ").append(tr("gui.stationbuilder.scheduler.summary_target_30s"));
        }
        if (analysis.solvable()) {
            if (analysis.depotOffsetsMs().isEmpty()) {
                summary.append(" · ").append(tr("gui.stationbuilder.scheduler.summary_no_offsets"));
            } else {
                summary.append(" · ").append(tr("gui.stationbuilder.scheduler.summary_offsets",
                        analysis.depotOffsetsMs().size()));
            }
        }
        if (analysis.sameDepotProblems() > 0) {
            summary.append(" · ").append(tr("gui.stationbuilder.scheduler.summary_same_depot",
                    analysis.sameDepotProblems()));
        }
        MtrNetwork network = session.network();
        if (network != null && !network.warnings().isEmpty()) {
            summary.append(" · ").append(tr("gui.stationbuilder.scheduler.summary_warnings",
                    network.warnings().size()));
        }
        return summary.toString();
    }

    private static String routeLabel(MtrNetwork network, String routeId) {
        MtrRoute route = network == null ? null : network.route(routeId);
        return route == null ? routeId : route.label();
    }

    // ------------------------------------------------------------------
    // Footer / fields
    // ------------------------------------------------------------------

    private void renderFooter(DrawContext context, int pad, int y) {
        String status;
        if (message != null) {
            status = message;
        } else if (session.state() == SchedulerSession.LoadState.READY && session.network() != null) {
            MtrNetwork network = session.network();
            status = tr("gui.stationbuilder.scheduler.status_counts",
                    network.stations.size(), network.platformCount(),
                    network.routes.size(), network.tripCount());
            if (!network.warnings().isEmpty()) {
                status += " · " + tr("gui.stationbuilder.scheduler.status_warnings",
                        network.warnings().size());
            }
            status += " · " + TimeFormat.hms(TimeFormat.localDayMillis(network.fetchedAt));
        } else if (session.state() == SchedulerSession.LoadState.LOADING) {
            status = tr("gui.stationbuilder.scheduler.status_loading");
        } else {
            status = session.status();
        }
        int color = message != null ? COLOR_ACCENT : switch (session.state()) {
            case ERROR -> COLOR_ERROR;
            case LOADING -> COLOR_WARNING;
            default -> COLOR_TEXT_DIM;
        };
        drawText(context, trim(status, Math.max(30, width - pad * 2 - 130)), pad, y, color);

        String hint = tr("gui.stationbuilder.scheduler.readonly");
        drawText(context, hint, width - pad - textWidth(hint), y, COLOR_TEXT_DIM);
    }

    private void createFields() {
        startField = new Field("00:00:00", 12, this::isTimeCharacter, value -> { });
        intervalField = new Field("00:10:00", 12, this::isTimeCharacter, value -> { });
        countField = new Field("60", 5, this::isDigitCharacter, value -> { });
        windowFromField = new Field(TimeFormat.hm(session.windowStartMs()), 6, this::isTimeCharacter,
                value -> applyWindowFields());
        windowToField = new Field(TimeFormat.hm(session.windowEndMs()), 6, this::isTimeCharacter,
                value -> applyWindowFields());
        fields.add(startField);
        fields.add(intervalField);
        fields.add(countField);
        fields.add(windowFromField);
        fields.add(windowToField);
    }

    private void applyWindowFields() {
        long start = TimeFormat.parseHms(windowFromField.text);
        long end = TimeFormat.parseHms(windowToField.text);
        if (start < 0L || end < 0L) {
            showMessage(Text.translatable("gui.stationbuilder.scheduler.invalid_window").getString());
            syncWindowFields();
            return;
        }
        if (end <= start) {
            end += TimeFormat.DAY_MS;
        }
        session.setWindow(start, end);
        syncWindowFields();
    }

    private void syncWindowFields() {
        windowFromField.text = TimeFormat.hm(session.windowStartMs());
        windowToField.text = TimeFormat.hm(session.windowEndMs());
    }

    private void syncDepartureFields(MtrRoute route) {
        lastSyncedNetwork = session.network();
        lastSyncedRouteId = route.id;
        intervalField.text = "";
        if (route.startTimes().isEmpty()) {
            startField.text = "06:00:00";
            countField.text = "60";
        } else {
            startField.text = TimeFormat.hms(route.startTimes().get(0));
            countField.text = String.valueOf(route.startTimes().size());
            if (route.startTimes().size() >= 2) {
                List<Long> gaps = new ArrayList<>(route.startTimes().size() - 1);
                for (int index = 1; index < route.startTimes().size(); index++) {
                    long gap = route.startTimes().get(index) - route.startTimes().get(index - 1);
                    if (gap > 0L) {
                        gaps.add(gap);
                    }
                }
                if (!gaps.isEmpty()) {
                    // Use the median gap: the first gap can be an irregular service gap and would
                    // make the headway field look wrong for an otherwise regular route.
                    gaps.sort(Long::compareTo);
                    intervalField.text = TimeFormat.duration(gaps.get(gaps.size() / 2));
                }
            }
        }
    }

    private boolean isTimeCharacter(char c) {
        return Character.isDigit(c) || c == ':';
    }

    private boolean isDigitCharacter(char c) {
        return Character.isDigit(c);
    }

    private void renderField(DrawContext context, Field field) {
        boolean hovered = inside(mouseX, mouseY, field.x, field.y, field.w, field.h);
        context.fill(field.x, field.y, field.x + field.w, field.y + field.h, COLOR_PANEL_DARK);
        border(context, field.x, field.y, field.w, field.h,
                field.focused ? COLOR_ACCENT : (hovered ? COLOR_TEXT_DIM : COLOR_BORDER));
        int textY = field.y + (field.h - 9) / 2 + 1;
        String visible = field.text;
        if (visible.isEmpty() && !field.focused) {
            drawText(context, "-", field.x + 3, textY, COLOR_TEXT_DIM);
        } else {
            int maxWidth = field.w - 6;
            while (textRenderer.getWidth(visible) > maxWidth && visible.length() > 1) {
                visible = visible.substring(1);
            }
            drawText(context, visible, field.x + 3, textY, COLOR_TEXT);
            if (field.focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caretX = field.x + 3 + textRenderer.getWidth(visible) + 1;
                context.fill(caretX, field.y + 3, caretX + 1, field.y + field.h - 3, COLOR_ACCENT);
            }
        }
        addHit(field.x, field.y, field.w, field.h, () -> focusField(field));
    }

    private void focusField(Field field) {
        for (Field candidate : fields) {
            candidate.focused = candidate == field;
        }
        focusedField = field;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double clickedX, double clickedY, int button) {
        if (button == 0) {
            for (int index = hits.size() - 1; index >= 0; index--) {
                Hit hit = hits.get(index);
                if (inside(clickedX, clickedY, hit.x, hit.y, hit.w, hit.h)) {
                    hit.action.run();
                    return true;
                }
            }
        }
        focusField(null);
        return super.mouseClicked(clickedX, clickedY, button);
    }

    // 1.20.1 uses the three-argument signature; 1.20.4+ uses the four-argument one. Declaring both
    // overloads without @Override keeps this shared source compiling against every supported version.
    public boolean mouseScrolled(double scrolledX, double scrolledY, double amount) {
        return handleTimetableScroll(scrolledX, scrolledY, amount);
    }

    public boolean mouseScrolled(
            double scrolledX,
            double scrolledY,
            double horizontalAmount,
            double verticalAmount
    ) {
        return handleTimetableScroll(scrolledX, scrolledY, verticalAmount);
    }

    private boolean handleTimetableScroll(double scrolledX, double scrolledY, double verticalAmount) {
        if (tab != Tab.ROUTES || verticalAmount == 0.0 || lastTimetableMaxScroll <= 0) {
            return false;
        }
        if (!inside(scrolledX, scrolledY, lastTimetableX, lastTimetableY, lastTimetableW, lastTimetableH)) {
            return false;
        }
        int step = verticalAmount > 0.0 ? -1 : 1;
        timetableScroll = Math.max(0, Math.min(lastTimetableMaxScroll, timetableScroll + step));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focusedField != null) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!focusedField.text.isEmpty()) {
                        focusedField.text = focusedField.text.substring(0, focusedField.text.length() - 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    focusedField.text = "";
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    commitFocusedField();
                    return true;
                }
                case GLFW.GLFW_KEY_TAB -> {
                    cycleField((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    focusField(null);
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                        String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
                        for (char character : clipboard.toCharArray()) {
                            if (focusedField.filter.test(character)
                                    && focusedField.text.length() < focusedField.maxLength) {
                                focusedField.text += character;
                            }
                        }
                    }
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }
        if (tab == Tab.ROUTES) {
            int step = Math.max(1, lastTimetableRows - 1);
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                timetableScroll = Math.max(0, timetableScroll - step);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                timetableScroll += step;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                timetableScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                timetableScroll = Integer.MAX_VALUE;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_R && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            session.refresh();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitFocusedField() {
        if (focusedField == null) {
            return;
        }
        focusedField.onCommit.accept(focusedField.text);
        focusField(null);
    }

    private void cycleField(int direction) {
        if (fields.isEmpty()) {
            return;
        }
        int index = focusedField == null ? -1 : fields.indexOf(focusedField);
        index = Math.floorMod(index + direction, fields.size());
        focusField(fields.get(index));
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focusedField != null) {
            if (focusedField.filter.test(chr) && focusedField.text.length() < focusedField.maxLength) {
                focusedField.text += chr;
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    // ------------------------------------------------------------------
    // Small drawing helpers
    // ------------------------------------------------------------------

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        context.drawText(textRenderer, text, x, y, color, false);
    }

    private void drawCenteredText(DrawContext context, String text, int x, int y, int areaWidth, int color) {
        drawText(context, text, x + (areaWidth - textRenderer.getWidth(text)) / 2, y, color);
    }

    private static void border(DrawContext context, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawPanel(DrawContext context, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        context.fill(x, y, x + w, y + h, COLOR_PANEL);
        border(context, x, y, w, h, COLOR_BORDER);
    }

    private void button(
            DrawContext context,
            int x,
            int y,
            int w,
            int h,
            String label,
            boolean enabled,
            Runnable action
    ) {
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h,
                enabled ? (hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON) : COLOR_BUTTON_OFF);
        border(context, x, y, w, h, hovered ? COLOR_ACCENT : COLOR_BORDER);
        drawCenteredText(context, trim(label, w - 3), x, y + (h - 9) / 2 + 1, w,
                enabled ? (hovered ? COLOR_ACCENT : COLOR_TEXT) : COLOR_TEXT_DIM);
        if (enabled && action != null) {
            addHit(x, y, w, h, action);
        }
    }

    private void addHit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    private int textWidth(String text) {
        return textRenderer.getWidth(text);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return lines;
        }
        for (String paragraph : text.split("\n")) {
            String remaining = paragraph.trim();
            while (!remaining.isEmpty()) {
                int end = 0;
                int width = 0;
                int lastSpace = -1;
                while (end < remaining.length()) {
                    int codePoint = remaining.codePointAt(end);
                    String character = new String(Character.toChars(codePoint));
                    int charWidth = textWidth(character);
                    if (width + charWidth > maxWidth) {
                        break;
                    }
                    width += charWidth;
                    if (character.equals(" ")) {
                        lastSpace = end;
                    }
                    end += Character.charCount(codePoint);
                }
                if (end >= remaining.length()) {
                    lines.add(remaining);
                    break;
                }
                int cut = lastSpace > 0 ? lastSpace : end;
                if (cut <= 0) {
                    cut = end;
                }
                lines.add(remaining.substring(0, cut).trim());
                remaining = remaining.substring(cut).trim();
            }
        }
        return lines;
    }

    private String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (maxWidth <= 0) {
            return "";
        }
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            String chunk = new String(Character.toChars(codePoint));
            int charWidth = textRenderer.getWidth(chunk);
            if (width + charWidth + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(chunk);
            width += charWidth;
            offset += Character.charCount(codePoint);
        }
        return builder + ellipsis;
    }

    private static int brighten(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int red = Math.min(255, ((color >> 16) & 0xFF) + 48);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 48);
        int blue = Math.min(255, (color & 0xFF) + 48);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private void renderTooltipBox(DrawContext context, String text) {
        int padding = 3;
        int boxW = textRenderer.getWidth(text) + padding * 2;
        int boxH = 9 + padding * 2;
        int boxX = Math.min(mouseX + 8, width - boxW - 4);
        int boxY = Math.max(4, mouseY - boxH - 4);
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xE0101418);
        border(context, boxX, boxY, boxW, boxH, COLOR_ACCENT);
        drawText(context, text, boxX + padding, boxY + padding, COLOR_TEXT);
    }

    private void showMessage(String text) {
        if (text == null || text.isEmpty()) {
            message = null;
            messageTicks = 0;
        } else {
            message = text;
            messageTicks = 100;
        }
    }

    // ------------------------------------------------------------------
    // Helper types
    // ------------------------------------------------------------------

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private record Bar(MtrRoute route, long start, long end) {
    }

    private final class Field {
        String text;
        final int maxLength;
        final Predicate<Character> filter;
        final Consumer<String> onCommit;
        int x;
        int y;
        int w;
        int h;
        boolean focused;

        Field(String text, int maxLength, Predicate<Character> filter, Consumer<String> onCommit) {
            this.text = text;
            this.maxLength = maxLength;
            this.filter = filter;
            this.onCommit = onCommit;
        }

        void setBounds(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
