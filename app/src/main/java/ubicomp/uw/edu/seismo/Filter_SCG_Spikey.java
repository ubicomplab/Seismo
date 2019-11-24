package ubicomp.uw.edu.seismo;

// Spikey filter to accentuate SCG beats
public class Filter_SCG_Spikey {

    private double history[]; // history of variable size

    // creates a new spikey filter with the given size of history
    public Filter_SCG_Spikey(int size) {
        history = new double[size];
        clear_history();
    }

    // steps the filter with new value
    // returns the spikey filtered output
    public double step(double newval) {
        // shift history with new value
        shift_history(newval);
        // sum the squares of the history values
        double output = 0.0;
        for (double val : history) {
            output += val * val;
        }
        // square the overall output
        return output * output;
    }

    // set all history elements to zero
    private void clear_history() {
        for (int i = 0; i < history.length; i++) {
            history[i] = 0;
        }
    }

    // shift all history values to the left with given rightmost replacement
    private void shift_history(double newval) {
        for (int i = 0; i < history.length - 1; i++) {
            history[i] = history[i+1];
        }
        history[history.length-1] = newval;
    }

}
