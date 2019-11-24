package ubicomp.uw.edu.seismo;

// Filter used to detect PPG beats
public class Filter_PPG_Detector {

    private double last_val; // used to keep track of instantaneous 1st derivative
    private int pulse_delay; // time to wait before next pulse
    private int pulse_counter; // counter to wait before next pulse
    private boolean seeking_peak; // keeps track of whether the peak has been found
    private int counter; // total step counter

    // creates new PPG detector
    public Filter_PPG_Detector(int delay) {
        last_val = 0;
        this.pulse_delay = delay;
        pulse_counter = 0;
        seeking_peak = false;
        counter = 0;
    }

    // returns true if the new value is a peak using the given threshold
    // does NOT simply check if the val is above the threshold
    // the threshold is passed in to allow for another filter to create an adaptive threshold
    // after threshold is crossed, looks for a local max, and delays next pulse detection
    // returns true after local PPG local max is passed
    public boolean is_peak(double new_val, double thresh) {
        boolean isPeak = false;
        if (counter < pulse_delay * 2) { // ignore early peaks
        } else if (pulse_counter > 0) {
            if (seeking_peak && new_val < last_val) {
                isPeak = true;
                seeking_peak = false;
            }
            pulse_counter--;
        } else {
            if (last_val < thresh && new_val >= thresh) {
                pulse_counter = pulse_delay;
                seeking_peak = true;
            }
        }
        last_val = new_val;
        counter ++;
        return isPeak;
    }

}
