package ubicomp.uw.edu.seismo;

/*
Keeps track of the local signal minimum and maximum by storing recent values in a comb buffer
(i.e. a decimated subset of recent signal values)
 */
public class Filter_MinMax {

    private int comb_size; // number of points to save in buffer
    private int comb_skip; // downsampling factor (set to 1 for no downsampling)
    private double comb_buf[]; // buffer of recent values
    private int counter; // counter to track downsampling state

    // creates a new minmax filter with given comb size and comb skip
    public Filter_MinMax(int comb_size, int comb_skip) {
        this.comb_size = comb_size;
        this.comb_skip = comb_skip;
        comb_buf = new double[comb_size];
        counter = 0;
    }

    // adds a new value to the buffer modulo the comb skip
    public void step(double val) {
        if (counter % comb_skip == 0) {
            int index = (counter / comb_skip) % comb_size;
            comb_buf[index] = val;
        }
        counter++;
    }

    // returns the maximum value in the buffer
    public double getMax() {
        int limit = counter / comb_skip > comb_size ? comb_size : counter / comb_skip;
        double maxVal = comb_buf[0];
        for (int i = 0; i < limit; i++) {
            if (comb_buf[i] > maxVal) {
                maxVal = comb_buf[i];
            }
        }
        return maxVal;
    }

    // returns the minimum value in the buffer
    public double getMin() {
        int limit = counter / comb_skip > comb_size ? comb_size : counter / comb_skip;
        double minVal = comb_buf[0];
        for (int i = 0; i < limit; i++) {
            if (comb_buf[i] < minVal) {
                minVal = comb_buf[i];
            }
        }
        return minVal;
    }

    // returns the average of the max and min values in the buffer
    public double getMiddle() {
        return (getMax() + getMin()) / 2.0;
    }

    // returns a threshold at the given fraction between max and min values
    // min + (max - min) * fraction
    // (fraction should be between 0 and 1 inclusive)
    public double getThreshold(double fraction) {
        if (fraction >= 1) {
            return getMax();
        }
        if (fraction <= 0) {
            return getMin();
        }
        return getMin() + (getMax() - getMin()) * fraction;
    }

}
