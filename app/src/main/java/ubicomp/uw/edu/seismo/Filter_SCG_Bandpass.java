package ubicomp.uw.edu.seismo;

// 4th order realtime bandpass butterworth filter
// sampling frequency = 400 Hz
// low cutoff frequency = 20 Hz
// high cutoff frequency = 50 Hz
// generated from http://www-users.cs.york.ac.uk/~fisher/mkfilter/trad.html

public class Filter_SCG_Bandpass {

    private double[] x_buf = new double[9]; // feedforward signal input
    private double[] y_buf = new double[9]; // feedback filter output
    private double[] x_coeffs = {1.0, 0.0, -4.0, 0.0, 6.0, 0.0, -4.0, 0.0, 1.0}; // filter coeffs
    private double[] y_coeffs = {0.0, -0.1067997882, 0.9717485707, -4.0611282729, 10.1448928397,
            -16.5338807306, 17.9662318299, -12.6797170177, 5.2973787891};

    // creates new SCG bandpass filter
    public Filter_SCG_Bandpass() {
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
