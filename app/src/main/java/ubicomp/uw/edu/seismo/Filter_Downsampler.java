package ubicomp.uw.edu.seismo;

// Filter to downsample input signal by arbitrary factor
public class Filter_Downsampler {

    private double buf[];
    private int counter;

    // creates a new downsampling filter with given downsampling factor
    public Filter_Downsampler(int ds_factor) {
        buf = new double[ds_factor];
        counter = 0;
        clear_buf();
    }

    // adds new value to buffer and returns true if next downsampled value is ready
    public boolean step(double scg_val) {
        buf[counter] = scg_val;
        counter = (counter + 1) % buf.length;
        return counter == 0;
    }

    // returns the mean of the current buffer values
    public double get_mean() {
        double scg_ds = 0;
        for (double val : buf) {
            scg_ds += val;
        }
        scg_ds /= buf.length;
        return scg_ds;
    }

    // returns the max of the current buffer values
    public double get_max() {
        double max_val = buf[0];
        for (double val : buf) {
            if (val > max_val) {
                max_val = val;
            }
        }
        return max_val;
    }

    // returns the min of the current buffer values
    public double get_min() {
        double min_val = buf[0];
        for (double val : buf) {
            if (val < min_val) {
                min_val = val;
            }
        }
        return min_val;
    }

    // sets all buffer values to zero
    private void clear_buf() {
        for (int i = 0; i < buf.length; i++) {
            buf[i] = 0;
        }
    }

}
