package ubicomp.uw.edu.seismo;

// Spikey filter to accentuate PPG beats
public class Filter_PPG_Spikey {

    private double history[]; // history of variable size

    // creates a new spikey filter with the given size of history
    public Filter_PPG_Spikey(int size) {
        history = new double[size];
        clear_history();
    }

    // steps the filter with new value
    // returns the spikey filtered output
    public double step(double newval) {
        // find the minimum value in the history (shift at the same time)
        double minval = history[0];
        for (int i = 0; i < history.length - 1; i++) {
            if (history[i + 1] < minval) {
                minval = history[i + 1];
            }
            history[i] = history[i + 1];
        }
        // add new value to end of history
        history[history.length - 1] = newval;
        // sum the history values relative to the minimum
        double output = 0.0;
        for (double val : history) {
            output += val - minval;
        }
        // square the result
        return output * output;
    }

    // sets all elements in history to 0
    private void clear_history() {
        for (int i = 0; i < history.length; i++) {
            history[i] = 0;
        }
    }


}
