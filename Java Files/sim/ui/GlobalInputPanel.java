package sim.ui;

import javax.swing.*;
import java.awt.*;

public class GlobalInputPanel extends JPanel {
    private final JTextField percentInPersonField;
    private final JTextField arrivalSpanField;
    private final JTextField transitDelayField;
    private final JTextField intervalField;
    private final JTextField sqftPerPassengerField;

    public GlobalInputPanel() {
        // Removed:
        //  - # of Checkpoints
        //  - Rate / Checkpoint
        //  - Hold-room Delay
        //
        // Remaining global inputs:
        //  - % In Person
        //  - Arrival Span
        //  - Transit Delay
        //  - Interval (forced to 1)
        setLayout(new GridLayout(5, 2, 5, 5));

        percentInPersonField = addLabeledField("% In Person (0-1):");
        arrivalSpanField     = addLabeledField("Arrival Span (min):");
        transitDelayField    = addLabeledField("Transit Delay (min):");
        intervalField        = addLabeledField("Interval (min):");
        sqftPerPassengerField = addLabeledField("Sq Ft per Passenger:");

        // defaults
        percentInPersonField.setText("0.4");
        arrivalSpanField.setText("120");
        transitDelayField.setText("2");
        sqftPerPassengerField.setText("15");

        // force interval = 1 and disable editing
        intervalField.setText("1");
        intervalField.setEnabled(true);
        intervalField.setEditable(false);
        intervalField.setFocusable(false);
        intervalField.setBackground(new Color(200, 200, 200)); // medium gray
        intervalField.setForeground(Color.BLACK);              // keep text readable
    }

    private JTextField addLabeledField(String label) {
        add(new JLabel(label));
        JTextField field = new JTextField();
        add(field);
        return field;
    }

    public double getPercentInPerson() {
        return Double.parseDouble(percentInPersonField.getText());
    }

    public void setPercentInPerson(double value) {
        percentInPersonField.setText(Double.toString(value));
    }

    public int getArrivalSpanMinutes() {
        return Integer.parseInt(arrivalSpanField.getText());
    }

    public void setArrivalSpanMinutes(int value) {
        arrivalSpanField.setText(Integer.toString(value));
    }

    public int getTransitDelayMinutes() {
        return Integer.parseInt(transitDelayField.getText());
    }

    public void setTransitDelayMinutes(int value) {
        transitDelayField.setText(Integer.toString(value));
    }

    /** Always returns 1 */
    public int getIntervalMinutes() {
        return 1;
    }

    public int getSqftPerPassenger() {
        return Integer.parseInt(sqftPerPassengerField.getText());
    }

    public void setSqftPerPassenger(int value) {
        sqftPerPassengerField.setText(Integer.toString(value));
    }
}
