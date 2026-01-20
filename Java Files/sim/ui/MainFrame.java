package sim.ui;

import sim.model.ArrivalCurveConfig;
import sim.model.Flight;
import sim.service.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;

public class MainFrame extends JFrame {
    private GlobalInputPanel    globalInputPanel;
    private FlightTablePanel    flightTablePanel;
    private TicketCounterPanel  ticketCounterPanel;
    private CheckpointPanel     checkpointPanel;
    private HoldRoomSetupPanel  holdRoomSetupPanel;
    // NEW (Step 6)
    private ArrivalCurveEditorPanel arrivalCurvePanel;

    private JButton             startSimulationButton;

    public MainFrame() {
        super("Airport Setup");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
//new stuff 
JPanel root = new JPanel(new BorderLayout());
root.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18)); // top,left,bottom,right
setContentPane(root);


        initializeComponents();
        initMenuBar();
        pack();
        setLocationRelativeTo(null);
    }

    private void initializeComponents() {
        globalInputPanel   = new GlobalInputPanel();
        flightTablePanel   = new FlightTablePanel();
        ticketCounterPanel = new TicketCounterPanel(flightTablePanel.getFlights());
        checkpointPanel    = new CheckpointPanel();
        holdRoomSetupPanel = new HoldRoomSetupPanel(flightTablePanel.getFlights());
    // NEW (Step 6)
        arrivalCurvePanel  = new ArrivalCurveEditorPanel(ArrivalCurveConfig.legacyDefault());

        startSimulationButton = new JButton("Start Simulation");

        startSimulationButton.setForeground(Color.WHITE);
        startSimulationButton.setOpaque(true);
        startSimulationButton.setContentAreaFilled(true);
        
        startSimulationButton.addActionListener(e -> onStartSimulation());

        add(globalInputPanel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Flights", flightTablePanel);
        tabs.addTab("Ticket Counters", ticketCounterPanel);
        tabs.addTab("Checkpoints", checkpointPanel);
        tabs.addTab("Hold Rooms", holdRoomSetupPanel);
        // NEW TAB (Step 6)
        tabs.addTab("Arrivals Curve", arrivalCurvePanel);

        add(tabs, BorderLayout.CENTER);

        add(startSimulationButton, BorderLayout.SOUTH);
    }

    private void initMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem saveItem = new JMenuItem("Save Config...");
        saveItem.addActionListener(e -> onSaveConfig());

        JMenuItem loadItem = new JMenuItem("Load Config...");
        loadItem.addActionListener(e -> onLoadConfig());

        fileMenu.add(saveItem);
        fileMenu.add(loadItem);
        bar.add(fileMenu);

        setJMenuBar(bar);
    }

    private void onSaveConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Configuration");
        chooser.setSelectedFile(new File("airport-config.properties"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            saveConfigToFile(file);
            JOptionPane.showMessageDialog(this,
                    "Configuration saved to:\n" + file.getAbsolutePath(),
                    "Saved",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Error saving configuration", ex);
        }
    }

    private void onLoadConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Configuration");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            loadConfigFromFile(file);
            JOptionPane.showMessageDialog(this,
                    "Configuration loaded from:\n" + file.getAbsolutePath(),
                    "Loaded",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Error loading configuration", ex);
        }
    }

    private void saveConfigToFile(File file) throws Exception {
        Properties p = new Properties();

        p.setProperty("global.percentInPerson", Double.toString(globalInputPanel.getPercentInPerson()));
        p.setProperty("global.arrivalSpanMinutes", Integer.toString(globalInputPanel.getArrivalSpanMinutes()));
        p.setProperty("global.transitDelayMinutes", Integer.toString(globalInputPanel.getTransitDelayMinutes()));
        p.setProperty("global.intervalMinutes", Integer.toString(globalInputPanel.getIntervalMinutes()));
        p.setProperty("global.sqftPerPassenger", Integer.toString(globalInputPanel.getSqftPerPassenger()));

        List<Flight> flights = flightTablePanel.getFlights();
        p.setProperty("flights.count", Integer.toString(flights.size()));
        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            p.setProperty("flights." + i + ".number", safeString(f.getFlightNumber()));
            p.setProperty("flights." + i + ".depTime", f.getDepartureTime().toString());
            p.setProperty("flights." + i + ".seats", Integer.toString(f.getSeats()));
            p.setProperty("flights." + i + ".fillPercent", Double.toString(f.getFillPercent()));
            p.setProperty("flights." + i + ".shape", f.getShape().name());
        }

        List<TicketCounterConfig> counters = ticketCounterPanel.getCounters();
        p.setProperty("counters.count", Integer.toString(counters.size()));
        for (int i = 0; i < counters.size(); i++) {
            TicketCounterConfig cfg = counters.get(i);
            p.setProperty("counters." + i + ".ratePerMinute", Double.toString(cfg.getRate()));
            p.setProperty("counters." + i + ".allowedFlights", encodeFlightSet(cfg.getAllowedFlights()));
        }

        List<CheckpointConfig> checkpoints = checkpointPanel.getCheckpoints();
        p.setProperty("checkpoints.count", Integer.toString(checkpoints.size()));
        for (int i = 0; i < checkpoints.size(); i++) {
            CheckpointConfig cfg = checkpoints.get(i);
            p.setProperty("checkpoints." + i + ".ratePerHour", Double.toString(cfg.getRatePerHour()));
        }

        List<HoldRoomConfig> holdRooms = holdRoomSetupPanel.getHoldRooms();
        p.setProperty("holdRooms.count", Integer.toString(holdRooms.size()));
        for (int i = 0; i < holdRooms.size(); i++) {
            HoldRoomConfig cfg = holdRooms.get(i);
            p.setProperty("holdRooms." + i + ".walkMinutes", Integer.toString(cfg.getWalkMinutes()));
            p.setProperty("holdRooms." + i + ".walkSeconds", Integer.toString(cfg.getWalkSecondsPart()));
            p.setProperty("holdRooms." + i + ".allowedFlights", encodeStringSet(cfg.getAllowedFlightNumbers()));
        }

        ArrivalCurveConfig curveCfg = arrivalCurvePanel.getConfigCopy();
        p.setProperty("arrivalCurve.legacyMode", Boolean.toString(curveCfg.isLegacyMode()));
        p.setProperty("arrivalCurve.peakMinutesBeforeDeparture", Integer.toString(curveCfg.getPeakMinutesBeforeDeparture()));
        p.setProperty("arrivalCurve.leftSigmaMinutes", Integer.toString(curveCfg.getLeftSigmaMinutes()));
        p.setProperty("arrivalCurve.rightSigmaMinutes", Integer.toString(curveCfg.getRightSigmaMinutes()));
        p.setProperty("arrivalCurve.lateClampEnabled", Boolean.toString(curveCfg.isLateClampEnabled()));
        p.setProperty("arrivalCurve.lateClampMinutesBeforeDeparture",
                Integer.toString(curveCfg.getLateClampMinutesBeforeDeparture()));
        p.setProperty("arrivalCurve.windowStartMinutesBeforeDeparture",
                Integer.toString(curveCfg.getWindowStartMinutesBeforeDeparture()));
        p.setProperty("arrivalCurve.boardingCloseMinutesBeforeDeparture",
                Integer.toString(curveCfg.getBoardingCloseMinutesBeforeDeparture()));

        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "Airport UI Configuration");
        }
    }

    private void loadConfigFromFile(File file) throws Exception {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        }

        double percentInPerson = parseDouble(p, "global.percentInPerson", 0.4);
        int arrivalSpan = parseInt(p, "global.arrivalSpanMinutes", 120);
        int transitDelay = parseInt(p, "global.transitDelayMinutes", 2);
        int sqftPerPassenger = parseInt(p, "global.sqftPerPassenger", 15);

        globalInputPanel.setPercentInPerson(percentInPerson);
        globalInputPanel.setArrivalSpanMinutes(arrivalSpan);
        globalInputPanel.setTransitDelayMinutes(transitDelay);
        globalInputPanel.setSqftPerPassenger(sqftPerPassenger);

        int flightCount = parseInt(p, "flights.count", 0);
        List<Flight> flights = new ArrayList<>();
        for (int i = 0; i < flightCount; i++) {
            String number = p.getProperty("flights." + i + ".number", Integer.toString(i + 1));
            LocalTime dep = parseTime(p.getProperty("flights." + i + ".depTime", "00:00"));
            int seats = parseInt(p, "flights." + i + ".seats", 180);
            double fill = parseDouble(p, "flights." + i + ".fillPercent", 0.85);
            Flight.ShapeType shape = parseShape(p.getProperty("flights." + i + ".shape", "CIRCLE"));
            flights.add(new Flight(number, dep, seats, fill, shape));
        }
        flightTablePanel.setFlights(flights);

        Map<String, Flight> flightByNumber = new HashMap<>();
        for (Flight f : flightTablePanel.getFlights()) {
            if (f.getFlightNumber() != null) {
                flightByNumber.put(f.getFlightNumber().trim(), f);
            }
        }

        int counterCount = parseInt(p, "counters.count", 0);
        List<TicketCounterConfig> counters = new ArrayList<>();
        for (int i = 0; i < counterCount; i++) {
            double ratePerMinute = parseDouble(p, "counters." + i + ".ratePerMinute", 1.0);
            String allowed = p.getProperty("counters." + i + ".allowedFlights", "*");
            Set<Flight> allowedFlights = parseFlightSet(allowed, flightByNumber);
            TicketCounterConfig cfg = new TicketCounterConfig(i + 1);
            cfg.setRate(ratePerMinute);
            cfg.setAllowedFlights(allowedFlights);
            counters.add(cfg);
        }
        ticketCounterPanel.setCounters(counters);

        int checkpointCount = parseInt(p, "checkpoints.count", 0);
        List<CheckpointConfig> checkpoints = new ArrayList<>();
        for (int i = 0; i < checkpointCount; i++) {
            double ratePerHour = parseDouble(p, "checkpoints." + i + ".ratePerHour", 0.0);
            CheckpointConfig cfg = new CheckpointConfig(i + 1);
            cfg.setRatePerHour(ratePerHour);
            checkpoints.add(cfg);
        }
        checkpointPanel.setCheckpoints(checkpoints);

        int holdCount = parseInt(p, "holdRooms.count", 0);
        List<HoldRoomConfig> holdRooms = new ArrayList<>();
        for (int i = 0; i < holdCount; i++) {
            int walkMin = parseInt(p, "holdRooms." + i + ".walkMinutes", 0);
            int walkSec = parseInt(p, "holdRooms." + i + ".walkSeconds", 0);
            HoldRoomConfig cfg = new HoldRoomConfig(i + 1, walkMin * 60 + walkSec);
            String allowed = p.getProperty("holdRooms." + i + ".allowedFlights", "*");
            if (!isAllMarker(allowed)) {
                cfg.setAllowedFlightNumbers(parseStringSet(allowed));
            }
            holdRooms.add(cfg);
        }
        holdRoomSetupPanel.setHoldRooms(holdRooms);

        ArrivalCurveConfig curveCfg = ArrivalCurveConfig.legacyDefault();
        curveCfg.setLegacyMode(parseBoolean(p, "arrivalCurve.legacyMode", true));
        curveCfg.setPeakMinutesBeforeDeparture(
                parseInt(p, "arrivalCurve.peakMinutesBeforeDeparture", curveCfg.getPeakMinutesBeforeDeparture()));
        curveCfg.setLeftSigmaMinutes(
                parseInt(p, "arrivalCurve.leftSigmaMinutes", curveCfg.getLeftSigmaMinutes()));
        curveCfg.setRightSigmaMinutes(
                parseInt(p, "arrivalCurve.rightSigmaMinutes", curveCfg.getRightSigmaMinutes()));
        curveCfg.setLateClampEnabled(parseBoolean(p, "arrivalCurve.lateClampEnabled", curveCfg.isLateClampEnabled()));
        curveCfg.setLateClampMinutesBeforeDeparture(
                parseInt(p, "arrivalCurve.lateClampMinutesBeforeDeparture",
                        curveCfg.getLateClampMinutesBeforeDeparture()));
        curveCfg.setWindowStartMinutesBeforeDeparture(
                parseInt(p, "arrivalCurve.windowStartMinutesBeforeDeparture",
                        curveCfg.getWindowStartMinutesBeforeDeparture()));
        curveCfg.setBoardingCloseMinutesBeforeDeparture(
                parseInt(p, "arrivalCurve.boardingCloseMinutesBeforeDeparture",
                        curveCfg.getBoardingCloseMinutesBeforeDeparture()));
        curveCfg.validateAndClamp();
        arrivalCurvePanel.setConfig(curveCfg);
    }

    private void showError(String title, Exception ex) {
        ex.printStackTrace();
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        JTextArea area = new JTextArea(sw.toString(), 20, 60);
        area.setEditable(false);
        JOptionPane.showMessageDialog(this,
                new JScrollPane(area),
                title,
                JOptionPane.ERROR_MESSAGE);
    }

    private static String safeString(String v) {
        return v == null ? "" : v;
    }

    private static String encodeFlightSet(Set<Flight> flights) {
        if (flights == null || flights.isEmpty()) return "*";
        StringBuilder sb = new StringBuilder();
        for (Flight f : flights) {
            if (f == null || f.getFlightNumber() == null) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(f.getFlightNumber().trim());
        }
        return sb.length() == 0 ? "*" : sb.toString();
    }

    private static String encodeStringSet(Set<String> values) {
        if (values == null || values.isEmpty()) return "*";
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v == null) continue;
            String trimmed = v.trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(trimmed);
        }
        return sb.length() == 0 ? "*" : sb.toString();
    }

    private static boolean isAllMarker(String v) {
        if (v == null) return true;
        String t = v.trim();
        return t.isEmpty() || "*".equals(t) || "ALL".equalsIgnoreCase(t);
    }

    private static Set<Flight> parseFlightSet(String value, Map<String, Flight> flightByNumber) {
        Set<Flight> out = new HashSet<>();
        if (isAllMarker(value)) return out;
        for (String part : value.split(",")) {
            String key = part.trim();
            if (key.isEmpty()) continue;
            Flight f = flightByNumber.get(key);
            if (f != null) out.add(f);
        }
        return out;
    }

    private static Set<String> parseStringSet(String value) {
        Set<String> out = new HashSet<>();
        if (isAllMarker(value)) return out;
        for (String part : value.split(",")) {
            String key = part.trim();
            if (!key.isEmpty()) out.add(key);
        }
        return out;
    }

    private static int parseInt(Properties p, String key, int fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseDouble(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean parseBoolean(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        return Boolean.parseBoolean(v.trim());
    }

    private static LocalTime parseTime(String value) {
        if (value == null) return LocalTime.MIDNIGHT;
        String v = value.trim();
        if (v.isEmpty()) return LocalTime.MIDNIGHT;
        try {
            return LocalTime.parse(v);
        } catch (Exception ignored) {
        }
        if (v.contains(".")) {
            String[] parts = v.split("\\.");
            if (parts.length == 2) {
                try {
                    int h = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]);
                    return LocalTime.of(h, m);
                } catch (Exception ignored) {
                }
            }
        }
        return LocalTime.MIDNIGHT;
    }

    private static Flight.ShapeType parseShape(String value) {
        if (value == null) return Flight.ShapeType.CIRCLE;
        try {
            return Flight.ShapeType.valueOf(value.trim());
        } catch (Exception e) {
            return Flight.ShapeType.CIRCLE;
        }
    }

    private void onStartSimulation() {
        if (flightTablePanel.getFlights().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please add at least one flight before starting simulation.",
                    "No Flights Defined",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<TicketCounterConfig> counters = ticketCounterPanel.getCounters();
        if (counters.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please add at least one ticket counter before starting simulation.",
                    "No Counters Defined",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<CheckpointConfig> checkpoints = checkpointPanel.getCheckpoints();
        if (checkpoints.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please add at least one checkpoint before starting simulation.",
                    "No Checkpoints Defined",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<HoldRoomConfig> holdRooms = holdRoomSetupPanel.getHoldRooms();
        if (holdRooms.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please add at least one hold room before starting simulation.",
                    "No Hold Rooms Defined",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            double percentInPerson = globalInputPanel.getPercentInPerson();
            if (percentInPerson < 0 || percentInPerson > 1) {
                throw new IllegalArgumentException("Percent in person must be between 0 and 1");
            }

            int baseArrivalSpan  = globalInputPanel.getArrivalSpanMinutes();
            int interval         = globalInputPanel.getIntervalMinutes();
            int transitDelay     = globalInputPanel.getTransitDelayMinutes();
            int sqftPerPassenger = globalInputPanel.getSqftPerPassenger();

            // Hold-room delay is no longer a GlobalInputPanel control.
            // Prefer pulling it from the Hold Rooms tab if available; fallback safely.
            int holdDelay = resolveHoldDelayMinutes();

            List<Flight> flights = flightTablePanel.getFlights();
    // NEW (Step 6)
            ArrivalCurveConfig curveCfg = arrivalCurvePanel.getConfigCopy();
            curveCfg.validateAndClamp();
    // NEW (Step 6)
            // - Legacy mode: keep behavior SAME (2h default) unless user already changed baseArrivalSpan
            // - Edited mode: allow earlier than base via windowStart up to 240
            int curveStart = curveCfg.isLegacyMode()
                    ? ArrivalCurveConfig.DEFAULT_WINDOW_START
                    : curveCfg.getWindowStartMinutesBeforeDeparture();

            int effectiveArrivalSpan = Math.max(baseArrivalSpan, curveStart);

            // build the pre-run engine for the data table (populate its history)
            SimulationEngine tableEngine = createEngine(
                    percentInPerson,
                    counters,
                    checkpoints,
                    effectiveArrivalSpan,
                    interval,
                    transitDelay,
                    holdDelay,
                    flights,
                    holdRooms
            );
    // NEW (Step 6)
            tableEngine.setArrivalCurveConfig(curveCfg);
            tableEngine.setSqftPerPassenger(sqftPerPassenger);
            tableEngine.runAllIntervals();

            // build the fresh engine for live animation
            SimulationEngine simEngine = createEngine(
                    percentInPerson,
                    counters,
                    checkpoints,
                    effectiveArrivalSpan,
                    interval,
                    transitDelay,
                    holdDelay,
                    flights,
                    holdRooms
            );
    // NEW (Step 6)
            simEngine.setArrivalCurveConfig(curveCfg);
            simEngine.setSqftPerPassenger(sqftPerPassenger);

            new DataTableFrame(tableEngine).setVisible(true);
            new SimulationFrame(simEngine).setVisible(true);

        } catch (Exception ex) {
            ex.printStackTrace();
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            JTextArea area = new JTextArea(sw.toString(), 20, 60);
            area.setEditable(false);
            JOptionPane.showMessageDialog(this,
                    new JScrollPane(area),
                    "Simulation Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Try to get hold-room delay from the Hold Rooms tab/panel, without hard-coding
     * a specific method name (so you do not break if you renamed it).
     *
     * Falls back to 5 minutes if nothing is found (matching your old default).
     */
    private int resolveHoldDelayMinutes() {
        // 1) Try common method names on the setup panel
        Integer fromPanel = tryInvokeInt(holdRoomSetupPanel,
                "getHoldDelayMinutes",
                "getDefaultHoldDelayMinutes",
                "getHoldroomDelayMinutes",
                "getHoldRoomDelayMinutes",
                "getCheckpointToHoldDelayMinutes"
        );
        if (fromPanel != null && fromPanel >= 0) return fromPanel;

        // 2) Try to infer from HoldRoomConfig objects (if they store a delay)
        try {
            List<HoldRoomConfig> rooms = holdRoomSetupPanel.getHoldRooms();
            if (rooms != null && !rooms.isEmpty()) {
                for (HoldRoomConfig cfg : rooms) {
                    Integer v = tryInvokeInt(cfg,
                            "getHoldDelayMinutes",
                            "getDelayMinutes",
                            "getHoldroomDelayMinutes",
                            "getHoldRoomDelayMinutes",
                            "getCheckpointToHoldDelayMinutes"
                    );
                    if (v != null && v >= 0) return v;

                    // If they store seconds, convert to minutes (round up)
                    Integer sec = tryInvokeInt(cfg,
                            "getWalkSeconds",
                            "getCheckpointToHoldSeconds",
                            "getSecondsFromCheckpoint"
                    );
                    if (sec != null && sec > 0) {
                        return (sec + 59) / 60;
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore and fall back
        }

        // 3) Old default
        return 5;
    }

    private Integer tryInvokeInt(Object target, String... methodNames) {
        if (target == null) return null;
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                Class<?> rt = m.getReturnType();
                if (rt == int.class || rt == Integer.class) {
                    Object out = m.invoke(target);
                    return (out == null) ? null : ((Number) out).intValue();
                }
            } catch (Exception ignored) {
                // try next name
            }
        }
        return null;
    }

    /**
     * Compatibility helper:
     * - Prefer NEW constructor that supports per-checkpoint configs AND hold rooms.
     * - Also supports variants that omit holdDelay (if you have moved delay into hold-room configs).
     * - Fall back to older constructors if needed (using average checkpoint rate).
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private SimulationEngine createEngine(
            double percentInPerson,
            List<TicketCounterConfig> counters,
            List<CheckpointConfig> checkpoints,
            int arrivalSpan,
            int interval,
            int transitDelay,
            int holdDelay,
            List<Flight> flights,
            List<HoldRoomConfig> holdRooms
    ) throws Exception {

        // Preferred signature WITH holdDelay:
        // (double, List, List, int, int, int, int, List, List)
        for (Constructor<?> c : SimulationEngine.class.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 9
                    && p[0] == double.class
                    && List.class.isAssignableFrom(p[1])   // counters
                    && List.class.isAssignableFrom(p[2])   // checkpoints
                    && p[3] == int.class
                    && p[4] == int.class
                    && p[5] == int.class
                    && p[6] == int.class                  // holdDelay
                    && List.class.isAssignableFrom(p[7])   // flights
                    && List.class.isAssignableFrom(p[8]))  // holdRooms
            {
                return (SimulationEngine) c.newInstance(
                        percentInPerson,
                        counters,
                        checkpoints,
                        arrivalSpan,
                        interval,
                        transitDelay,
                        holdDelay,
                        flights,
                        holdRooms
                );
            }
        }

        // Preferred signature WITHOUT holdDelay (if delay is fully inside hold-room configs now):
        // (double, List, List, int, int, int, List, List)
        for (Constructor<?> c : SimulationEngine.class.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 8
                    && p[0] == double.class
                    && List.class.isAssignableFrom(p[1])   // counters
                    && List.class.isAssignableFrom(p[2])   // checkpoints
                    && p[3] == int.class
                    && p[4] == int.class
                    && p[5] == int.class                  // transitDelay
                    && List.class.isAssignableFrom(p[6])   // flights
                    && List.class.isAssignableFrom(p[7]))  // holdRooms
            {
                return (SimulationEngine) c.newInstance(
                        percentInPerson,
                        counters,
                        checkpoints,
                        arrivalSpan,
                        interval,
                        transitDelay,
                        flights,
                        holdRooms
                );
            }
        }

        // Fallback: older constructor style using numCheckpoints + avgRatePerMin (and maybe holdDelay)
        int numCheckpoints = (checkpoints == null) ? 0 : checkpoints.size();
        double avgRatePerMin = 0.0;
        if (checkpoints != null && !checkpoints.isEmpty()) {
            double sum = 0.0;
            for (CheckpointConfig cfg : checkpoints) sum += cfg.getRatePerMinute();
            avgRatePerMin = sum / checkpoints.size();
        }

        // Older hold-rooms constructor WITH holdDelay:
        // (double, List, int, double, int, int, int, int, List, List)
        for (Constructor<?> c : SimulationEngine.class.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 10
                    && p[0] == double.class
                    && List.class.isAssignableFrom(p[1])
                    && p[2] == int.class
                    && p[3] == double.class
                    && p[4] == int.class
                    && p[5] == int.class
                    && p[6] == int.class
                    && p[7] == int.class
                    && List.class.isAssignableFrom(p[8])
                    && List.class.isAssignableFrom(p[9])) {

                return (SimulationEngine) c.newInstance(
                        percentInPerson,
                        counters,
                        numCheckpoints,
                        avgRatePerMin,
                        arrivalSpan,
                        interval,
                        transitDelay,
                        holdDelay,
                        flights,
                        holdRooms
                );
            }
        }

        // Older hold-rooms constructor WITHOUT holdDelay:
        // (double, List, int, double, int, int, int, List, List)
        for (Constructor<?> c : SimulationEngine.class.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 9
                    && p[0] == double.class
                    && List.class.isAssignableFrom(p[1])
                    && p[2] == int.class
                    && p[3] == double.class
                    && p[4] == int.class
                    && p[5] == int.class
                    && p[6] == int.class
                    && List.class.isAssignableFrom(p[7])
                    && List.class.isAssignableFrom(p[8])) {

                return (SimulationEngine) c.newInstance(
                        percentInPerson,
                        counters,
                        numCheckpoints,
                        avgRatePerMin,
                        arrivalSpan,
                        interval,
                        transitDelay,
                        flights,
                        holdRooms
                );
            }
        }

        // Final fallback: old constructor (no hold rooms)
        // (double, List, int, double, int, int, int, int, List)
        return new SimulationEngine(
                percentInPerson,
                counters,
                numCheckpoints,
                avgRatePerMin,
                arrivalSpan,
                interval,
                transitDelay,
                holdDelay,
                flights
        );
    }
}
