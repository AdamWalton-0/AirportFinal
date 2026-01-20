package sim.ui;

import sim.model.Passenger;
import sim.service.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SquareFootageSummaryFrame extends JFrame {
    public SquareFootageSummaryFrame(SimulationEngine engine) {
        super("Square Footage Summary");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        int sqftPerPassenger = engine.getSqftPerPassenger();
        LocalTime startTime = computeStartTime(engine);
        int intervalMinutes = engine.getInterval();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        JTable ticketTable = buildTicketCounterTable(engine, sqftPerPassenger, startTime, intervalMinutes, timeFmt);
        JTable checkpointTable = buildCheckpointTable(engine, sqftPerPassenger, startTime, intervalMinutes, timeFmt);
        JTable holdRoomTable = buildHoldRoomTable(engine, sqftPerPassenger, startTime, intervalMinutes, timeFmt);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ticket Counters", wrapTable(ticketTable));
        tabs.addTab("Checkpoints", wrapTable(checkpointTable));
        tabs.addTab("Hold Rooms", wrapTable(holdRoomTable));

        add(tabs, BorderLayout.CENTER);
        add(buildTotalsPanel(engine, sqftPerPassenger), BorderLayout.SOUTH);

        setSize(720, 520);
        setLocationRelativeTo(null);
    }

    private static JScrollPane wrapTable(JTable table) {
        table.setFillsViewportHeight(true);
        return new JScrollPane(table);
    }

    private static JTable buildTicketCounterTable(SimulationEngine engine,
                                                  int sqftPerPassenger,
                                                  LocalTime startTime,
                                                  int intervalMinutes,
                                                  DateTimeFormatter timeFmt) {
        int lines = engine.getCounterConfigs().size();
        Object[][] rows = new Object[lines][5];

        for (int i = 0; i < lines; i++) {
            MaxResult max = maxQueueForLine(engine.getHistoryQueuedTicket(), i);
            int id = engine.getCounterConfigs().get(i).getId();
            rows[i][0] = "Counter " + id;
            rows[i][1] = max.maxCount;
            rows[i][2] = max.maxCount * sqftPerPassenger;
            rows[i][3] = formatTimeForIndex(startTime, intervalMinutes, timeFmt, max.maxIndex);
            rows[i][4] = formatIntervalForIndex(max.maxIndex);
        }

        return new JTable(rows, new Object[]{"Ticket Counter", "Max Passengers", "Max Sq Ft", "Time", "Interval"});
    }

    private static JTable buildCheckpointTable(SimulationEngine engine,
                                               int sqftPerPassenger,
                                               LocalTime startTime,
                                               int intervalMinutes,
                                               DateTimeFormatter timeFmt) {
        int lines = engine.getCheckpointConfigs().size();
        Object[][] rows = new Object[lines][5];

        for (int i = 0; i < lines; i++) {
            MaxResult max = maxQueueForLine(engine.getHistoryQueuedCheckpoint(), i);
            int id = engine.getCheckpointConfigs().get(i).getId();
            rows[i][0] = "Checkpoint " + id;
            rows[i][1] = max.maxCount;
            rows[i][2] = max.maxCount * sqftPerPassenger;
            rows[i][3] = formatTimeForIndex(startTime, intervalMinutes, timeFmt, max.maxIndex);
            rows[i][4] = formatIntervalForIndex(max.maxIndex);
        }

        return new JTable(rows, new Object[]{"Checkpoint", "Max Passengers", "Max Sq Ft", "Time", "Interval"});
    }

    private static JTable buildHoldRoomTable(SimulationEngine engine,
                                             int sqftPerPassenger,
                                             LocalTime startTime,
                                             int intervalMinutes,
                                             DateTimeFormatter timeFmt) {
        int rooms = engine.getHoldRoomConfigs().size();
        Object[][] rows = new Object[rooms][5];

        for (int i = 0; i < rooms; i++) {
            MaxResult max = maxQueueForLine(engine.getHistoryHoldRooms(), i);
            int id = engine.getHoldRoomConfigs().get(i).getId();
            rows[i][0] = "Hold Room " + id;
            rows[i][1] = max.maxCount;
            rows[i][2] = max.maxCount * sqftPerPassenger;
            rows[i][3] = formatTimeForIndex(startTime, intervalMinutes, timeFmt, max.maxIndex);
            rows[i][4] = formatIntervalForIndex(max.maxIndex);
        }

        return new JTable(rows, new Object[]{"Hold Room", "Max Passengers", "Max Sq Ft", "Time", "Interval"});
    }

    private static JPanel buildTotalsPanel(SimulationEngine engine, int sqftPerPassenger) {
        int ticketTotal = sumMaxAcrossLines(engine.getHistoryQueuedTicket(), engine.getCounterConfigs().size());
        int checkpointTotal = sumMaxAcrossLines(engine.getHistoryQueuedCheckpoint(), engine.getCheckpointConfigs().size());
        int holdRoomTotal = sumMaxAcrossLines(engine.getHistoryHoldRooms(), engine.getHoldRoomConfigs().size());

        JPanel panel = new JPanel(new GridLayout(3, 1, 4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Totals (sum of per-unit maxima)"));

        panel.add(new JLabel("Ticket Counters Total: " + (ticketTotal * sqftPerPassenger) + " sq ft"));
        panel.add(new JLabel("Checkpoints Total: " + (checkpointTotal * sqftPerPassenger) + " sq ft"));
        panel.add(new JLabel("Hold Rooms Total: " + (holdRoomTotal * sqftPerPassenger) + " sq ft"));

        return panel;
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

    private static LocalTime computeStartTime(SimulationEngine engine) {
        LocalTime firstDep = engine.getFlights().stream()
                .map(f -> f.getDepartureTime())
                .min(LocalTime::compareTo)
                .orElse(LocalTime.MIDNIGHT);
        return firstDep.minusMinutes(engine.getArrivalSpan());
    }

    private static String formatTimeForIndex(LocalTime startTime,
                                             int intervalMinutes,
                                             DateTimeFormatter timeFmt,
                                             int historyIndex) {
        if (historyIndex < 0) return "";
        return startTime.plusMinutes((long) (historyIndex + 1) * intervalMinutes).format(timeFmt);
    }

    private static String formatIntervalForIndex(int historyIndex) {
        if (historyIndex < 0) return "";
        return Integer.toString(historyIndex + 1);
    }

    private static final class MaxResult {
        final int maxCount;
        final int maxIndex;

        MaxResult(int maxCount, int maxIndex) {
            this.maxCount = maxCount;
            this.maxIndex = maxIndex;
        }
    }
}
