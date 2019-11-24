package ubicomp.uw.edu.seismo;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ToggleButton;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class handles the user button interactions and saves the recorded signals as csv files
 */
public class App_MeasurementFragment extends Fragment {

    private boolean ready_to_record = true;
    private boolean ready_for_click = true;
    private long last_click_interval = 1000L;
    private long this_click_time = 0;
    private long last_click_time = 0;

    private ToggleButton recbtn;

    public PulseSensing psensor; // this class contains all the main signal processing

    public App_MeasurementFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_app_measurement_mc, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        recbtn = getActivity().findViewById(R.id.rec_toggle);
        recbtn.setSoundEffectsEnabled(false);

        psensor = new PulseSensing();
        if (null == savedInstanceState) {
            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            transaction.add(R.id.back_camera_preview, psensor);
            transaction.commit();
        }

        getView().setFocusableInTouchMode(true);
        getView().requestFocus();
        getView().setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                this_click_time = System.currentTimeMillis();
                if (this_click_time - last_click_time > last_click_interval){
                    ready_for_click = true;
                }
                last_click_time = this_click_time;
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && ready_for_click) {
                    if (ready_to_record) {
                        ready_to_record = false;
                        startRecording(); // Kick off the test sequence
                    } else {
                        stopRecording();
                    }
                    ready_for_click = false;
                    return true;
                }
                return false;
            }
        });
    }

    private void startRecording() {
        psensor.recording = true;
        recbtn.setChecked(true);
    }

    private void stopRecording() {

        psensor.recording = false;
        recbtn.setChecked(false);

        String root = getActivity().getExternalFilesDir(null).getAbsolutePath();

        String timestamp = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss").format(new Date());

        String accel_file = root + "/seismo_accel_" + timestamp + ".csv";
        String gyro_file = root + "/seismo_gyro_" + timestamp + ".csv";
        String ppg_file = root + "/seismo_ppg_" + timestamp + ".csv";
        String ptt_file = root + "/seismo_ptt_" + timestamp + ".csv";


        try {

            PrintWriter accel_writer = new PrintWriter(accel_file);
            psensor.writeAccelData(accel_writer);
            accel_writer.close();

            PrintWriter gyro_writer = new PrintWriter(gyro_file);
            psensor.writeGyroData(gyro_writer);
            gyro_writer.close();

            PrintWriter ppg_writer = new PrintWriter(ppg_file);
            psensor.writePPGData(ppg_writer);
            ppg_writer.close();

            PrintWriter ptt_writer = new PrintWriter(ptt_file);
            psensor.writePTTData(ptt_writer);
            ptt_writer.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();

        }



        getActivity().finish();
    }

}
