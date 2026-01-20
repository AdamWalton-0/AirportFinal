package sim.ui;

import sim.model.ArrivalCurveConfig;
import sim.model.Flight;
import sim.model.Passenger;
import sim.service.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * SimulationSummaryFramePreview
 *
 * Goal:
 * - Inputs: show engine settings cleanly (including Sq Ft per passenger).
 * - Outputs: box/card layout + flight output section.
 * - Square Footage Summary: contains Ticket + Checkpoint + Hold Rooms + Totals.
 *
 * IMPORTANT:
 * - Do NOT change engine API names (keep newest zip compatibility).
 * - Avoid repeating info across tabs.
 */
public class SimulationSummaryFramePreview extends JFrame {

    private final SimulationEngine engine;

    // Formatting helpers
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public SimulationSummaryFramePreview(SimulationEngine engine) {
        super("Simulation Summary");
        this.engine = engine;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        add(buildTabs(), BorderLayout.CENTER);

        setSize(1000, 700);
        setLocationRelativeTo(null);
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Inputs", buildInputsPanel());
        tabs.addTab("Outputs", buildOutputsPanel());
        tabs.addTab("Square Footage Summary", buildSquareFootageSummaryPanel());
        return tabs;
    }

    // ==========================================================
    // INPUTS TAB
    // ==========================================================

    private JComponent buildInputsPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        // General
        JPanel general = new JPanel();
        general.setLayout(new BoxLayout(general, BoxLayout.Y_AXIS));
        addKV(general, "% In Person", fmtPct(getPercentInPersonBestEffort()));
        addKV(general, "Arrival Span (min)", String.valueOf(engine.getArrivalSpan()));
        addKV(general, "Transit Delay (min)", String.valueOf(engine.getTransitDelayMinutes()));
        addKV(general, "Hold Delay (min)", String.valueOf(engine.getHoldDelayMinutes()));
        addKV(general, "Interval (min)", String.valueOf(engine.getInterval()));

        // Sq Ft per passenger input
        addKV(general, "Sq Ft per Passenger", String.valueOf(engine.getSqftPerPassenger()));

        root.add(section("General", general));
        root.add(Box.createVerticalStrut(10));

        // Arrival Curve
        ArrivalCurveConfig curve = engine.getArrivalCurveConfigCopy();
        JPanel curvePanel = new JPanel();
        curvePanel.setLayout(new BoxLayout(curvePanel, BoxLayout.Y_AXIS));
        if (curve != null) {
            addKV(curvePanel, "Legacy Mode", String.valueOf(curve.isLegacyMode()));
            addKV(curvePanel, "Window Start (min before dep)", String.valueOf(curve.getWindowStartMinutesBeforeDeparture()));
            addKV(curvePanel, "Peak (min before dep)", String.valueOf(curve.getPeakMinutesBeforeDeparture()));
            addKV(curvePanel, "Left Sigma (min)", String.valueOf(curve.getLeftSigmaMinutes()));
            addKV(curvePanel, "Right Sigma (min)", String.valueOf(curve.getRightSigmaMinutes()));
            addKV(curvePanel, "Late Clamp Enabled", String.valueOf(curve.isLateClampEnabled()));
            addKV(curvePanel, "Late Clamp (min before dep)", String.valueOf(curve.getLateClampMinutesBeforeDeparture()));
            addKV(curvePanel, "Boarding Close (min before dep)", String.valueOf(curve.getBoardingCloseMinutesBeforeDeparture()));
        } else {
            curvePanel.add(new JLabel("No ArrivalCurveConfig available."));
        }
        root.add(section("Arrival Curve", curvePanel));
        root.add(Box.createVerticalStrut(10));

        // Ticket Counters
        JPanel ticketPanel = new JPanel();
        ticketPanel.setLayout(new BoxLayout(ticketPanel, BoxLayout.Y_AXIS));
        List<TicketCounterConfig> counters = safeList(engine.getCounterConfigs());
        if (!counters.isEmpty()) {
            for (TicketCounterConfig c : counters) {
                double perMin = c.getRate();
                double perHour = perMin * 60.0;
                ticketPanel.add(new JLabel("Counter " + c.getId()
                        + " — rate=" + fmt1(perHour) + " passengers/hour"));
            }
        } else {
            ticketPanel.add(new JLabel("No ticket counters configured."));
        }
        root.add(section("Ticket Counters", ticketPanel));
        root.add(Box.createVerticalStrut(10));

        // Checkpoints
        JPanel cpPanel = new JPanel();
        cpPanel.setLayout(new BoxLayout(cpPanel, BoxLayout.Y_AXIS));
        List<CheckpointConfig> checkpoints = safeList(engine.getCheckpointConfigs());
        if (!checkpoints.isEmpty()) {
            for (CheckpointConfig c : checkpoints) {
                cpPanel.add(new JLabel("Checkpoint " + c.getId()
                        + " — rate=" + fmt1(c.getRatePerHour()) + " passengers/hour"));
            }
        } else {
            cpPanel.add(new JLabel("No checkpoints configured."));
        }
        root.add(section("Checkpoints", cpPanel));
        root.add(Box.createVerticalStrut(10));

        // Hold Rooms
        JPanel holdPanel = new JPanel();
        holdPanel.setLayout(new BoxLayout(holdPanel, BoxLayout.Y_AXIS));
        List<HoldRoomConfig> holdRooms = safeList(engine.getHoldRoomConfigs());
        if (!holdRooms.isEmpty()) {
            for (HoldRoomConfig h : holdRooms) {
                String flightsAllowed = allowedFlightsText(h);
                holdPanel.add(new JLabel("Hold Room " + h.getId()
                        + " — walk=" + h.getWalkMinutes() + "m " + h.getWalkSecondsPart() + "s"
                        + " — flights: " + flightsAllowed));
            }
        } else {
            holdPanel.add(new JLabel("No hold rooms configured."));
        }
        root.add(section("Hold Rooms", holdPanel));
        root.add(Box.createVerticalStrut(10));

        // Flights
        JPanel flightsPanel = new JPanel();
        flightsPanel.setLayout(new BoxLayout(flightsPanel, BoxLayout.Y_AXIS));
        List<Flight> flights = safeList(engine.getFlights());
        if (!flights.isEmpty()) {
            for (Flight f : flights) {
                flightsPanel.add(new JLabel(f.getFlightNumber()
                        + " — dep " + safeTime(f.getDepartureTime())
                        + " — seats " + f.getSeats()
                        + " — fill " + fmtPct(f.getFillPercent())));
            }
        } else {
            flightsPanel.add(new JLabel("No flights configured."));
        }
        root.add(section("Flights", flightsPanel));

        return scrollable(root);
    }

    // ==========================================================
    // OUTPUTS TAB
    // ==========================================================

    private JPanel buildOutputsPanel() {
        JTabbedPane subTabs = new JTabbedPane();
        subTabs.addTab("Overview", buildOutputsOverviewPanel());
        subTabs.addTab("Flights", buildOutputsFlightsPanel());
        subTabs.addTab("Queues + Capacity", buildOutputsQueuesCapacityPanel());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(subTabs, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * ✅ Output Overview math fix:
     *
     * The engine clears hold rooms at departure time,
     * so engine.getHoldRoomLines() may be empty by the time summary opens.
     *
     * Correct behavior:
     * - "Reached Hold Rooms" means passengers who EVER entered a hold room at ANY interval.
     * - Compute from engine.getHistoryHoldRooms() (unique Passenger objects across all intervals).
     */
    private JPanel buildOutputsOverviewPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // ✅ Unique passengers who EVER appeared in any hold room snapshot
        List<Passenger> everHeld = collectAllEverHoldRoomPassengers();
        int reachedHold = everHeld.size();

        int flightsCount = safeList(engine.getFlights()).size();

        // Total generated = sum all arrivals per minute across all flights
        int totalGenerated = sumArrivalsFromMinuteArrivalsMap(engine.getMinuteArrivalsMap());

        // Passenger mix should reflect the generator mix (not "currently visible in hold rooms")
        double fracInPerson = getPercentInPersonBestEffort();
        int totalInPersonGenerated = (int) Math.round(totalGenerated * fracInPerson);
        int totalOnlineGenerated = Math.max(0, totalGenerated - totalInPersonGenerated);

        // Missed = expected (seats*fill) minus those who EVER reached hold (per flight)
        int missedPassengers = computeMissedPassengersFromEverHeld(everHeld);

        // Average times (minutes) should be computed over passengers who reached hold at least once
        int avgTicket = avgMinutesTicket(everHeld);
        int avgCpOnline = avgMinutesCheckpointOnline(everHeld);
        int avgCpInPerson = avgMinutesCheckpointInPerson(everHeld);
        int avgArrToHold = avgMinutesArrivalToHold(everHeld);

        // Peaks
        PeakResult peakTicketQ = peakFromMap(safeMap(engine.getTicketQueuedByInterval()));
        PeakResult peakCpQ = peakFromMap(safeMap(engine.getCheckpointQueuedByInterval()));
        PeakResult peakHoldTotal = peakFromMap(safeMap(engine.getHoldRoomTotalByInterval()));
        PeakResult peakHeldUp = peakFromMap(safeMap(engine.getHoldUpsByInterval()));

        // --- Run Totals ---
        root.add(cardSectionTitle("Run Totals"));
        root.add(cardGrid(4,
                statCard("Flights", String.valueOf(flightsCount)),
                statCard("Total Passengers Generated", String.valueOf(totalGenerated)),
                statCard("Reached Hold Rooms", String.valueOf(reachedHold)),
                statCard("Missed Flights", String.valueOf(missedPassengers))
        ));
        root.add(Box.createVerticalStrut(10));

        // --- Passenger Mix ---
        root.add(cardSectionTitle("Passenger Mix"));
        root.add(cardGrid(3,
                statCard("In Person", String.valueOf(totalInPersonGenerated)),
                statCard("Online", String.valueOf(totalOnlineGenerated)),
                statCard("In Person %", fmtPct(fracInPerson))
        ));
        root.add(Box.createVerticalStrut(10));

        // --- Average Times ---
        root.add(cardSectionTitle("Average Times (minutes)"));
        root.add(cardGrid(4,
                statCard("Ticket (arrival → done)", avgTicket + " min"),
                statCard("Checkpoint (online)", avgCpOnline + " min"),
                statCard("Checkpoint (in-person)", avgCpInPerson + " min"),
                statCard("Arrival → Hold Room", avgArrToHold + " min")
        ));
        root.add(Box.createVerticalStrut(10));

        // --- Peak Counts ---
        root.add(cardSectionTitle("Peak Counts"));
        root.add(cardGrid(4,
                statCard("Peak Ticket Queue", String.valueOf(peakTicketQ.peakValue)),
                statCard("Peak Checkpoint Queue", String.valueOf(peakCpQ.peakValue)),
                statCard("Peak Hold Rooms Total", String.valueOf(peakHoldTotal.peakValue)),
                statCard("Peak Held-Up (ticket+checkpoint)", String.valueOf(peakHeldUp.peakValue))
        ));

        root.add(Box.createVerticalGlue());
        return root;
    }

    private JPanel buildOutputsFlightsPanel() {
        String[] cols = new String[]{
                "Flight #", "Departure", "Close", "Expected Passengers", "Generated", "Reached Hold", "Missed"
        };

        List<Flight> flights = safeList(engine.getFlights());
        Object[][] rows = new Object[flights.size()][cols.length];

        int closeMin = 20;
        ArrivalCurveConfig cfg = engine.getArrivalCurveConfigCopy();
        if (cfg != null && cfg.getBoardingCloseMinutesBeforeDeparture() > 0) {
            closeMin = cfg.getBoardingCloseMinutesBeforeDeparture();
        }

        // ✅ Use EVER-held passengers (not current holdRoomLines)
        List<Passenger> everHeld = collectAllEverHoldRoomPassengers();
        Map<Flight, Integer> reachedByFlight = new HashMap<>();
        for (Passenger p : everHeld) {
            if (p == null || p.getFlight() == null) continue;
            if (p.isMissed()) continue;
            reachedByFlight.put(p.getFlight(), reachedByFlight.getOrDefault(p.getFlight(), 0) + 1);
        }

        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);

            int expected = (int) Math.round(f.getSeats() * f.getFillPercent());
            int reached = reachedByFlight.getOrDefault(f, 0);
            int missed = Math.max(0, expected - reached);

            rows[i][0] = f.getFlightNumber();
            rows[i][1] = safeTime(f.getDepartureTime());
            rows[i][2] = safeTime(f.getDepartureTime().minusMinutes(closeMin));
            rows[i][3] = expected;
            rows[i][4] = expected;
            rows[i][5] = reached;
            rows[i][6] = missed;
        }

        JTable table = new JTable(rows, cols);
        table.setFillsViewportHeight(true);

        JPanel root = new JPanel(new BorderLayout());
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildOutputsQueuesCapacityPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        PeakResult peakTicketQ = peakFromMap(safeMap(engine.getTicketQueuedByInterval()));
        PeakResult peakCpQ = peakFromMap(safeMap(engine.getCheckpointQueuedByInterval()));
        PeakResult peakHoldTotal = peakFromMap(safeMap(engine.getHoldRoomTotalByInterval()));
        PeakResult peakHeldUp = peakFromMap(safeMap(engine.getHoldUpsByInterval()));

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        addKV(list, "Peak Ticket Queue", String.valueOf(peakTicketQ.peakValue));
        addKV(list, "Peak Checkpoint Queue", String.valueOf(peakCpQ.peakValue));
        addKV(list, "Peak Hold Rooms Total", String.valueOf(peakHoldTotal.peakValue));
        addKV(list, "Peak Held-Up (ticket+checkpoint)", String.valueOf(peakHeldUp.peakValue));

        root.add(section("Queues + Capacity", list));
        root.add(Box.createVerticalGlue());
        return root;
    }

    // ==========================================================
    // SQUARE FOOTAGE SUMMARY TAB
    // ==========================================================

    private JComponent buildSquareFootageSummaryPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        root.add(section("Ticket Counters (Max People + Required Area)", buildTicketCounterSqftTablePanel()));
        root.add(Box.createVerticalStrut(10));

        root.add(section("Checkpoints (Max People + Required Area)", buildCheckpointSqftTablePanel()));
        root.add(Box.createVerticalStrut(10));

        root.add(section("Hold Rooms (Max People + Required Area)", buildHoldRoomSqftTablePanel()));
        root.add(Box.createVerticalStrut(10));

        root.add(section("Totals (sum of per-unit maxima)", buildSqftTotalsPanel()));

        return scrollable(root);
    }

    private JPanel buildTicketCounterSqftTablePanel() {
        int sqftPerPassenger = engine.getSqftPerPassenger();
        List<TicketCounterConfig> counters = safeList(engine.getCounterConfigs());
        Object[][] rows = computeTicketCounterSqftRows(counters, sqftPerPassenger);

        JTable t = new JTable(rows, new Object[]{
                "Ticket Counter", "Max Passengers", "Max Sq Ft", "Time", "Interval"
        });
        t.setFillsViewportHeight(true);

        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildCheckpointSqftTablePanel() {
        int sqftPerPassenger = engine.getSqftPerPassenger();
        List<CheckpointConfig> cps = safeList(engine.getCheckpointConfigs());
        Object[][] rows = computeCheckpointSqftRows(cps, sqftPerPassenger);

        JTable t = new JTable(rows, new Object[]{
                "Checkpoint", "Max Passengers", "Max Sq Ft", "Time", "Interval"
        });
        t.setFillsViewportHeight(true);

        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildHoldRoomSqftTablePanel() {
        int sqftPerPassenger = engine.getSqftPerPassenger();
        List<HoldRoomConfig> rooms = safeList(engine.getHoldRoomConfigs());
        Object[][] rows = computeHoldRoomSqftRows(rooms, sqftPerPassenger);

        JTable t = new JTable(rows, new Object[]{
                "Hold Room", "Max Passengers", "Max Sq Ft", "Time", "Interval"
        });
        t.setFillsViewportHeight(true);

        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildSqftTotalsPanel() {
        int sqftPerPassenger = engine.getSqftPerPassenger();

        int ticketTotal = sumMaxAcrossLines(engine.getHistoryQueuedTicket(), safeList(engine.getCounterConfigs()).size());
        int checkpointTotal = sumMaxAcrossLines(engine.getHistoryQueuedCheckpoint(), safeList(engine.getCheckpointConfigs()).size());
        int holdRoomTotal = sumMaxAcrossLines(engine.getHistoryHoldRooms(), safeList(engine.getHoldRoomConfigs()).size());

        JPanel panel = new JPanel(new GridLayout(3, 1, 4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        panel.add(new JLabel("Ticket Counters Total: " + (ticketTotal * sqftPerPassenger) + " sq ft"));
        panel.add(new JLabel("Checkpoints Total: " + (checkpointTotal * sqftPerPassenger) + " sq ft"));
        panel.add(new JLabel("Hold Rooms Total: " + (holdRoomTotal * sqftPerPassenger) + " sq ft"));

        return panel;
    }

    // ==========================================================
    // ✅ EVER-HOLD PASSENGER COLLECTION (THE MAIN FIX)
    // ==========================================================

    private List<Passenger> collectAllEverHoldRoomPassengers() {
        LinkedHashSet<Passenger> set = new LinkedHashSet<>();

        List<List<List<Passenger>>> hist = engine.getHistoryHoldRooms();
        if (hist != null) {
            for (List<List<Passenger>> interval : hist) {
                if (interval == null) continue;
                for (List<Passenger> room : interval) {
                    if (room == null) continue;
                    for (Passenger p : room) {
                        if (p != null) set.add(p);
                    }
                }
            }
        }

        // Fallback: current hold-room lines (if history is empty)
        if (set.isEmpty()) {
            List<LinkedList<Passenger>> rooms = engine.getHoldRoomLines();
            if (rooms != null) {
                for (LinkedList<Passenger> room : rooms) {
                    if (room == null) continue;
                    for (Passenger p : room) {
                        if (p != null) set.add(p);
                    }
                }
            }
        }

        return new ArrayList<>(set);
    }

    private int computeMissedPassengersFromEverHeld(List<Passenger> everHeld) {
        List<Flight> flights = safeList(engine.getFlights());
        if (flights.isEmpty()) return 0;

        Map<Flight, Integer> reached = new HashMap<>();
        for (Passenger p : everHeld) {
            if (p == null || p.getFlight() == null) continue;
            if (p.isMissed()) continue;
            reached.put(p.getFlight(), reached.getOrDefault(p.getFlight(), 0) + 1);
        }

        int missed = 0;
        for (Flight f : flights) {
            int expected = (int) Math.round(f.getSeats() * f.getFillPercent());
            int got = reached.getOrDefault(f, 0);
            missed += Math.max(0, expected - got);
        }
        return missed;
    }

    // ==========================================================
    // Time averages
    // ==========================================================

    private int avgMinutesTicket(List<Passenger> passengers) {
        if (passengers == null || passengers.isEmpty()) return 0;

        int sum = 0;
        int n = 0;
        for (Passenger p : passengers) {
            if (p == null) continue;
            if (!p.isInPerson()) continue;

            int a = p.getArrivalMinute();
            int t = p.getTicketCompletionMinute();
            if (a >= 0 && t >= 0 && t >= a) {
                sum += (t - a);
                n++;
            }
        }
        return (n == 0) ? 0 : (int) Math.round(sum / (double) n);
    }

    private int avgMinutesCheckpointOnline(List<Passenger> passengers) {
        if (passengers == null || passengers.isEmpty()) return 0;

        int sum = 0;
        int n = 0;
        for (Passenger p : passengers) {
            if (p == null) continue;
            if (p.isInPerson()) continue;

            int a = p.getArrivalMinute();
            int c = p.getCheckpointCompletionMinute();
            if (a >= 0 && c >= 0 && c >= a) {
                sum += (c - a);
                n++;
            }
        }
        return (n == 0) ? 0 : (int) Math.round(sum / (double) n);
    }

    private int avgMinutesCheckpointInPerson(List<Passenger> passengers) {
        if (passengers == null || passengers.isEmpty()) return 0;

        int sum = 0;
        int n = 0;
        for (Passenger p : passengers) {
            if (p == null) continue;
            if (!p.isInPerson()) continue;

            int t = p.getTicketCompletionMinute();
            int c = p.getCheckpointCompletionMinute();
            if (t >= 0 && c >= 0 && c >= t) {
                sum += (c - t);
                n++;
            }
        }
        return (n == 0) ? 0 : (int) Math.round(sum / (double) n);
    }

    private int avgMinutesArrivalToHold(List<Passenger> passengers) {
        if (passengers == null || passengers.isEmpty()) return 0;

        int sum = 0;
        int n = 0;
        for (Passenger p : passengers) {
            if (p == null) continue;
            int a = p.getArrivalMinute();
            int h = p.getHoldRoomEntryMinute();
            if (a >= 0 && h >= 0 && h >= a) {
                sum += (h - a);
                n++;
            }
        }
        return (n == 0) ? 0 : (int) Math.round(sum / (double) n);
    }

    // ==========================================================
    // Square footage computations
    // ==========================================================

    private Object[][] computeTicketCounterSqftRows(List<TicketCounterConfig> counters, int sqftPerPassenger) {
        int lines = (counters == null) ? 0 : counters.size();
        Object[][] rows = new Object[lines][5];

        LocalTime startTime = computeStartTime(engine);
        int intervalMinutes = engine.getInterval();

        for (int i = 0; i < lines; i++) {
            MaxResult max = maxQueueForLine(engine.getHistoryQueuedTicket(), i);
            int id = counters.get(i).getId();

            rows[i][0] = "Counter " + id;
            rows[i][1] = max.maxCount;
            rows[i][2] = max.maxCount * sqftPerPassenger;
            rows[i][3] = formatTimeForIndex(startTime, intervalMinutes, max.maxIndex);
            rows[i][4] = formatIntervalForIndex(max.maxIndex);
        }

        return rows;
    }

    private Object[][] computeCheckpointSqftRows(List<CheckpointConfig> cps, int sqftPerPassenger) {
        int lines = (cps == null) ? 0 : cps.size();
        Object[][] rows = new Object[lines][5];

        LocalTime startTime = computeStartTime(engine);
        int intervalMinutes = engine.getInterval();

        for (int i = 0; i < lines; i++) {
            MaxResult max = maxQueueForLine(engine.getHistoryQueuedCheckpoint(), i);
            int id = cps.get(i).getId();

            rows[i][0] = "Checkpoint " + id;
            rows[i][1] = max.maxCount;
            rows[i][2] = max.maxCount * sqftPerPassenger;
            rows[i][3] = formatTimeForIndex(startTime, intervalMinutes, max.maxIndex);
            rows[i][4] = formatIntervalForIndex(max.maxIndex);
        }

        return rows;
    }

    private Object[][] computeHoldRoomSqftRows(List<HoldRoomConfig> rooms, int sqftPerPassenger) {
        int count = (rooms == null) ? 0 : rooms.size();
        Object[][] rows = new Object[count][5];

        LocalTime startTime = computeStartTime(engine);
        int intervalMinutes = engine.getInterval();

        for (int i = 0; i < count; i++) {
            MaxResult max = maxQueueForLine(engine.getHistoryHoldRooms(), i);
            int id = rooms.get(i).getId();

            rows[i][0] = "Hold Room " + id;
            rows[i][1] = max.maxCount;
            rows[i][2] = max.maxCount * sqftPerPassenger;
            rows[i][3] = formatTimeForIndex(startTime, intervalMinutes, max.maxIndex);
            rows[i][4] = formatIntervalForIndex(max.maxIndex);
        }

        return rows;
    }

    // ==========================================================
    // Utility methods
    // ==========================================================

    private static JPanel section(String title, JComponent inner) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createTitledBorder(title));
        wrap.add(inner, BorderLayout.CENTER);
        return wrap;
    }

    private static void addKV(JPanel panel, String k, String v) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        JLabel left = new JLabel(k + ": ");
        left.setPreferredSize(new Dimension(240, 18));
        row.add(left, BorderLayout.WEST);
        row.add(new JLabel(v == null ? "" : v), BorderLayout.CENTER);
        panel.add(row);
    }

    private static JScrollPane scrollable(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private static <T> List<T> safeList(List<T> in) {
        return (in == null) ? Collections.emptyList() : in;
    }

    private static Map<Integer, Integer> safeMap(Map<Integer, Integer> in) {
        return (in == null) ? Collections.emptyMap() : in;
    }

    private static String fmt1(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private static String fmtPct(double frac01) {
        return String.format(Locale.US, "%.1f%%", frac01 * 100.0);
    }

    private static String safeTime(LocalTime t) {
        if (t == null) return "";
        return t.format(TIME_FMT);
    }

    private static LocalTime computeStartTime(SimulationEngine engine) {
        LocalTime firstDep = safeList(engine.getFlights()).stream()
                .map(Flight::getDepartureTime)
                .min(LocalTime::compareTo)
                .orElse(LocalTime.MIDNIGHT);
        return firstDep.minusMinutes(engine.getArrivalSpan());
    }

    private static String allowedFlightsText(HoldRoomConfig h) {
        if (h == null) return "ALL";

        Set<String> nums = h.getAllowedFlightNumbers();
        if (nums == null || nums.isEmpty()) return "ALL";

        List<String> sorted = new ArrayList<>(nums);
        Collections.sort(sorted);
        return String.join(", ", sorted);
    }

    private static String formatTimeForIndex(LocalTime startTime, int intervalMinutes, int historyIndex) {
        if (historyIndex < 0 || startTime == null) return "";
        return startTime.plusMinutes((long) (historyIndex + 1) * intervalMinutes).format(TIME_FMT);
    }

    private static String formatIntervalForIndex(int historyIndex) {
        if (historyIndex < 0) return "";
        return Integer.toString(historyIndex + 1);
    }

    // ==========================================================
    // Peak + Max helpers
    // ==========================================================

    private static final class PeakResult {
        final int peakValue;
        final int peakInterval;

        PeakResult(int peakValue, int peakInterval) {
            this.peakValue = peakValue;
            this.peakInterval = peakInterval;
        }
    }

    private static PeakResult peakFromMap(Map<Integer, Integer> m) {
        int bestV = 0;
        int bestK = -1;
        if (m == null) return new PeakResult(0, -1);
        for (Map.Entry<Integer, Integer> e : m.entrySet()) {
            int k = e.getKey();
            int v = (e.getValue() == null) ? 0 : e.getValue();
            if (v > bestV) {
                bestV = v;
                bestK = k;
            }
        }
        return new PeakResult(bestV, bestK);
    }

    private static final class MaxResult {
        final int maxCount;
        final int maxIndex;

        MaxResult(int maxCount, int maxIndex) {
            this.maxCount = maxCount;
            this.maxIndex = maxIndex;
        }
    }

    private static MaxResult maxQueueForLine(List<List<List<Passenger>>> history, int lineIdx) {
        int max = 0;
        int maxIndex = -1;
        if (history == null) return new MaxResult(0, -1);
        for (int intervalIndex = 0; intervalIndex < history.size(); intervalIndex++) {
            List<List<Passenger>> interval = history.get(intervalIndex);
            if (interval == null || lineIdx < 0 || lineIdx >= interval.size()) continue;
            List<Passenger> line = interval.get(lineIdx);
            if (line != null && line.size() > max) {
                max = line.size();
                maxIndex = intervalIndex;
            }
        }
        return new MaxResult(max, maxIndex);
    }

    private static int sumMaxAcrossLines(List<List<List<Passenger>>> history, int lineCount) {
        int sum = 0;
        for (int i = 0; i < lineCount; i++) {
            sum += maxQueueForLine(history, i).maxCount;
        }
        return sum;
    }

    // ==========================================================
    // Card UI helpers
    // ==========================================================

    private static JLabel cardSectionTitle(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 13f));
        lbl.setBorder(BorderFactory.createEmptyBorder(4, 2, 6, 2));
        return lbl;
    }

    private static JPanel cardGrid(int cols, JComponent... cards) {
        JPanel grid = new JPanel(new GridLayout(1, cols, 10, 10));
        for (JComponent c : cards) grid.add(c);
        return grid;
    }

    private static JPanel statCard(String title, String value) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.PLAIN, 11f));
        t.setForeground(Color.DARK_GRAY);

        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 16f));

        card.add(t, BorderLayout.NORTH);
        card.add(v, BorderLayout.CENTER);

        return card;
    }

    // ==========================================================
    // percentInPerson + arrivals sum
    // ==========================================================

    private double getPercentInPersonBestEffort() {
        Double viaMethod = tryInvokeDouble(engine,
                "getPercentInPerson",
                "getInPersonPercent",
                "getInPersonFraction",
                "getInPersonRate"
        );
        if (viaMethod != null) return clamp01(viaMethod);

        Double viaField = tryReadDoubleField(engine,
                "percentInPerson"
        );
        if (viaField != null) return clamp01(viaField);

        return 0.0;
    }

    private static Double tryInvokeDouble(Object target, String... methodNames) {
        if (target == null || methodNames == null) return null;
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                Object v = m.invoke(target);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static Double tryReadDoubleField(Object target, String... fieldNames) {
        if (target == null || fieldNames == null) return null;
        for (String fname : fieldNames) {
            try {
                Field f = target.getClass().getDeclaredField(fname);
                f.setAccessible(true);
                Object v = f.get(target);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static int sumArrivalsFromMinuteArrivalsMap(Map<Flight, int[]> m) {
        if (m == null || m.isEmpty()) return 0;
        int sum = 0;
        for (int[] arr : m.values()) {
            if (arr == null) continue;
            for (int v : arr) sum += v;
        }
        return sum;
    }
}
