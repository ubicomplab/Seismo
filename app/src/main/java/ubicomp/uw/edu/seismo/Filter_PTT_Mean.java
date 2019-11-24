package ubicomp.uw.edu.seismo;

// Moving average filter for PTT
public class Filter_PTT_Mean {

    private double buf[]; // recent values

    // creates new PTT moving average filter
    public Filter_PTT_Mean(int size) {
        buf = new double[size];
        clear_buf();
    }

    // steps moving average filter with new value
    public double step(double newval) {
        double val = (double) newval;
        shift_buf(val);
        return get_mean();
    }

    // clears buffer
    private void clear_buf() {
        for (int i = 0; i < buf.length; i++) {
            buf[i] = 0;
        }
    }

    // shifts all buffer elements to the left with given rightmost replacement
    private void shift_buf(double newval) {
        for (int i = 0; i < buf.length - 1; i++) {
            buf[i] = buf[i+1];
        }
        buf[buf.length-1] = newval;
    }

    // returns the current mean of the buffer
    private double get_mean() {
        double result = 0;
        for (int i = 0; i < buf.length; i++) {
            result += buf[i];
        }
        return result / buf.length;
    }

}
