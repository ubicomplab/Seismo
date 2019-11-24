package ubicomp.uw.edu.seismo;


// bandpass coefficients generated from http://www-users.cs.york.ac.uk/~fisher/mkfilter/trad.html


public class Filter_PPG_Bandpass {

    private double[] x_buf = new double[5]; // feedforward signal input
    private double[] y_buf = new double[5]; // feedback filter output

    private double[] x_coeffs = {1.0, 0.0, -2.0, 0.0, 1.0}; // filter coefficients
    private double[] y_coeffs = {0.0, -0.1715728753, 0.3053974642, -0.9279891778, 1.7799868642};

    // creates a new PPG bandpass filter
    public Filter_PPG_Bandpass() {
        init_buffer(x_buf);
        init_buffer(y_buf);
    }

    // returns filter output for given input
    public double step(double x_new) {
        shift_buffer(x_buf, x_new);
        double y_new = 0;
        for (int i = 0; i < x_buf.length; i++) {
            y_new += x_coeffs[i] * x_buf[i];
            y_new += y_coeffs[i] * y_buf[i];
        }
        shift_buffer(y_buf, y_new);
        return y_new;
    }

    // initialize buffers with zeros
    private void init_buffer(double[] buf) {
        for (int i = 0; i < buf.length; i++) {
            buf[i] = 0;
        }
    }

    // shift all buffer values to the left
    private void shift_buffer(double[] buf, double newval) {
        for (int i = 0; i < buf.length - 1; i++) {
            buf[i] = buf[i+1];
        }
        buf[buf.length-1] = newval;
    }

}
