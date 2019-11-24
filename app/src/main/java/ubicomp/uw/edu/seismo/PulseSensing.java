package ubicomp.uw.edu.seismo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.StringJoiner;
import java.lang.System;

/*
This class contains the main real-time signal processing code. OnImageAvailableListener contains
the code that runs for every new video frame and onSensorChanged contains the code that runs for
every new accelerometer or gyroscope value.
 */

public class PulseSensing extends Fragment
        implements SensorEventListener, FragmentCompat.OnRequestPermissionsResultCallback {

    // camera settings
    float   gain_setting[]  = {2f,3.0f,18.0f};
    long    exposure_time   = 1600000L;
    long    frame_duration  = 33333333L;
    int     iso             = 300;

    // Debugging tag
    private static final String TAG = "PulseSensingFragment";

    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
    };

    // Sensor orientation settings
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * An {@link Camera_AutoFitTextureView} for camera preview.
     */
    private Camera_AutoFitTextureView mTextureView;

    /**
     * A refernce to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * A reference to the current {@link CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mPreviewSession;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * The {@link Size} of video recording.
     */
    private Size mVideoSize;

    /**
     * MediaRecorder
     */
    private MediaRecorder mMediaRecorder;

    private Surface previewSurface;

//    private boolean mIsRecordingVideo; // not used
//    private boolean mIsRecordingReview;
//    private Long startTime;
//    private boolean buttonPressed = false;
//    private boolean buttonPressed_old = false;
    private String mCameraId;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            // Display the camera preview
            try {
                startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight()); //*
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    private Integer mSensorOrientation;
    private String mNextVideoAbsolutePath;
    private CaptureRequest.Builder mPreviewBuilder;

    private ImageReader mImageReader;

    private PPGExtractor ppgExtractor = new PPGExtractor();

    // colors to display for graphs on the screen
    private int graphColor[] = {Color.argb(255,255,180,9), // orange
                                Color.argb(255,46, 168, 255), // blue
                                Color.argb(225, 129, 209, 24), // green
                                Color.argb(225, 225, 225, 0)}; // yellow

    // Max number of data points LineGraphSeries should hold while displayed on the UI
    private static final int PPG_PREVIEW_SIZE = 60; // sample rate is about 30 fps
    private static final int SCG_PREVIEW_SIZE = 201; // sample rate is 402.7 Hz, ds factor of 4

    // number of SCG points behind the PPG peak to search
    // these numbers index backwards from the current time
    // after a PPG peak is detected, we seek the SCG somewhere between t-110 and t-240
    private int SCG_SEARCH_FROM = 110; // first point to ignore
    private int SCG_SEARCH_TO = 240; // first point to search

    // when a PPG peak event is detected, we look back to find the local maximum
    // the PPG peak is between t-4 and t-20 of the peak detection event (see Filter_PPG_Detector)
    private int PPG_SEARCH_FROM = 4; // first point
    private int PPG_SEARCH_TO = 20; // last point

    // buffers to store values as they are collected
    private ArrayList<Float> accel_x = new ArrayList<>();
    private ArrayList<Float> accel_y = new ArrayList<>();
    private ArrayList<Float> accel_z = new ArrayList<>();
    private ArrayList<Long> accel_time = new ArrayList<>();
    private ArrayList<Float> gyro_x = new ArrayList<>();
    private ArrayList<Float> gyro_y = new ArrayList<>();
    private ArrayList<Float> gyro_z = new ArrayList<>();
    private ArrayList<Long> gyro_time = new ArrayList<>();

    // arrays to contain the entire session data
    private ArrayList<Long> ppg_time = new ArrayList<>();
    private ArrayList<Double> ppg_recorded_r = new ArrayList<>();
    private ArrayList<Double> ppg_recorded_g = new ArrayList<>();
    private ArrayList<Double> ppg_recorded_b = new ArrayList<>();

    private ArrayList<Double> ppg_all = new ArrayList<>();
    private ArrayList<Double> scg_all = new ArrayList<>();
    private ArrayList<Double> scg_spikey_all = new ArrayList<>();

    // display buffers containing the points displayed on the screen
    private LineGraphSeries<DataPoint> ppg_display_buf = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> scg_display_buf = new LineGraphSeries<>();

    // PPG filter objects -- see them in use in the OnImageAvailableListener
    private Filter_MinMax ppg_minmax = new Filter_MinMax(60, 1);
    private Filter_PPG_Bandpass ppg_band_filt = new Filter_PPG_Bandpass();
    private Filter_PPG_Spikey ppg_spikey_filt = new Filter_PPG_Spikey(5);
    private Filter_PPG_Detector ppg_detector = new Filter_PPG_Detector(12);

    // displayes the lines that mark the PPG wavefronts
    private LineGraphSeries<DataPoint> ppg_beats = new LineGraphSeries<>();
    // toggles up and down to mark the wavefronts
    private int ppg_beat_toggle = 0;
    // aligns the timing of the beat to the correct display time on the screen
    private int ppg_beat_delay = 0;

    // SCG filter objects -- see them in use in the onSensorChanged callback
    private Filter_SCG_Bandpass scg_band_filt = new Filter_SCG_Bandpass();
    private Filter_SCG_Spikey scg_spikey_filt = new Filter_SCG_Spikey(5);
    private Filter_MinMax scg_minmax = new Filter_MinMax(SCG_SEARCH_TO, 1);
    private Filter_Downsampler scg_downsampler = new Filter_Downsampler(4);
    private Filter_Downsampler scg_spikey_downsampler = new Filter_Downsampler(4);

    // displays the lines that mark the SCG peaks
    private LineGraphSeries<DataPoint> scg_beats = new LineGraphSeries<>();
    // toggles up and down to mark the SCG peaks
    private int scg_beat_toggle = 0;
    // aligns the timing of the SCG beat to the correct display time on the screen
    private int scg_beat_delay = 0;

    // SCG spikey filter and search box visualization
    private LineGraphSeries<DataPoint> scg_spikes = new LineGraphSeries<>();
    private LineGraphSeries<DataPoint> scg_search_box = new LineGraphSeries<>();
    private int scg_search_box_counter = 0;

    // PTT moving average filter
    private Filter_PTT_Mean ptt_mean_filt = new Filter_PTT_Mean(5);

    // PTT plot over time
    private int ptt_counter = 0;
    private double ptt_smoothed = 200.0;
    private ArrayList<Double> ptt_recorded = new ArrayList<>();
    private ArrayList<Long> ptt_time = new ArrayList<>();
    private LineGraphSeries<DataPoint> ptt_plot = new LineGraphSeries<>();
    private int PTT_PLOT_SIZE = 30 * 30; // 30 fps * 30 seconds

    // BP plot over time
    private LineGraphSeries<DataPoint> bp_plot = new LineGraphSeries<>();
    private int BP_PLOT_SIZE = PTT_PLOT_SIZE;

    // Signal x axis counters
    public int ppgGraphX = 0;
    public int scgGraphX = 0;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;

    // keeps track of when the user is recording data
    public boolean recording = false;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    // This callback responds to new video frames. When a PPG pulse is detected, it triggers a
            // backwards search for the preceding SCG peak
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

            double[] mvideooutput = ppgExtractor.extractPPG(reader);
            final double new_ppg_val_r = - mvideooutput[0];
            final double new_ppg_val_g = - mvideooutput[1];
            final double new_ppg_val_b = - mvideooutput[2];
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    // pass raw PPG value through the bandpass filter
                    double new_ppg_filt_val = ppg_band_filt.step(new_ppg_val_r);
                    ppg_all.add(new_ppg_filt_val); // save to storage buffer

                    if (recording) {
                        // save to the array of recorded values
                        ppg_recorded_r.add(new_ppg_val_r);
                        ppg_recorded_g.add(new_ppg_val_g);
                        ppg_recorded_b.add(new_ppg_val_b);
                        ppg_time.add(System.nanoTime());
                    }

                    // apply spiky filter to accentuate peak forms
                    double new_ppg_spikey_val = ppg_spikey_filt.step(new_ppg_filt_val);

                    // keep track of recent min and max values of the spikey filter output
                    ppg_minmax.step(new_ppg_spikey_val);

                    // display filtered PPG to user
                    DataPoint new_ppg_filt_point = new DataPoint(ppgGraphX, new_ppg_filt_val);
                    ppg_display_buf.appendData(new_ppg_filt_point, true, PPG_PREVIEW_SIZE);

                    // trigger peaks with given threshold
                    double ppg_threshold = ppg_minmax.getThreshold(0.25);
                    if (ppg_detector.is_peak(new_ppg_spikey_val, ppg_threshold)) {
                        wavefront_calc(); // calculate the location of the PPG wavefront
                        ptt_calc(); // find the preceding SCG and measure the PTT
                    }

                    // delay the display of the PPG beat for timing alignment
                    // (this is a hack to align the graphs in realtime)
                    if (ppg_beat_delay > 0) {
                        ppg_beat_delay--;
                        if (ppg_beat_delay <= 0) {
                            ppg_beat_toggle = 1 - ppg_beat_toggle;
                        }
                    }

                    // display the new point on the PPG peak graph
                    // note that this is plotted even when no beat occurs
                    // the graph is out of view
                    // the graph is out of view; a verticle bar appears when it toggles
                    // this is a hack to circumnavigate the GraphView  library limitations
                    DataPoint new_peak = new DataPoint(ppgGraphX - PPG_SEARCH_TO, ppg_beat_toggle);
                    ppg_beats.appendData(new_peak, true, PPG_PREVIEW_SIZE);

                    // plot the instantaneous pulse transit time
                    DataPoint ptt_plot_point = new DataPoint(ppgGraphX, ptt_smoothed);
                    ptt_plot.appendData(ptt_plot_point, true, PTT_PLOT_SIZE);

                    // plot the instantaneous blood pressure BP = 1 / PTT
                    DataPoint bp_plot_point = new DataPoint(ppgGraphX, 1000.0 / ptt_smoothed);
                    bp_plot.appendData(bp_plot_point, true, BP_PLOT_SIZE);

                    // increment PPG x axis counter
                    ppgGraphX++;


                }
            });
        }
    };

    // calculate the location of the wavefront
    private void wavefront_calc() {

        // catch edge case at the beginning of the measurement
        if (ppg_all.size() < PPG_SEARCH_TO * 5) {
            return;
        }

        // look within the a recent search window to find the PPG peak
        int index = ppg_all.size() - 1 - PPG_SEARCH_FROM;
        int min_index = ppg_all.size() - 1 - PPG_SEARCH_TO;

        // from the peak, look backwards to find the peak of the 2nd derivative
        int max_curv_loc = index; // the index of the max 2nd derivative of the PPG signal
        double max_curv = ppg_all.get(index);
        while (index > min_index) {
            // estimate the 2nd derivative from three points
            double local_curv = ppg_all.get(index) - 2*ppg_all.get(index-1) + ppg_all.get(index-2);
            if (local_curv > max_curv) {
                max_curv_loc = index;
                max_curv = local_curv;
            }
            index--;
        }

        // making this delay nonzero will cause the beat to be displayed at the right time
        // see the OnImageAvailable function for where this is used
        ppg_beat_delay = PPG_SEARCH_TO - (ppg_all.size() - max_curv_loc);


    }

    // this method fires whenever a new accelerometer/gyroscope measurment is available
    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            // save the recorded raw values
            if (recording) {
                accel_time.add(System.nanoTime());
                accel_x.add(event.values[0]);
                accel_y.add(event.values[1]);
                accel_z.add(event.values[2]);
            }

            // get new raw SCG value from accelerometer
            float scg_y = event.values[1];
            double new_scg_val = (double) scg_y;
            scg_all.add(new_scg_val); // save raw value

            // apply bandpass filter to raw SCG
            double new_scg_filt_val = scg_band_filt.step(new_scg_val);

            // apply spikey filter to accentuate pulse events
            double new_scg_spikey_val = scg_spikey_filt.step(new_scg_filt_val);

            // store all the spikey data in a buffer
            scg_spikey_all.add(new_scg_spikey_val);

            // keep track of recent max and min of the spikey filter output
            scg_minmax.step(new_scg_spikey_val);

            // downsample the filtered signal
            // the conditional evaluates true and executes the if block on each multiple of hte
            // downsample factor supplied in the constructor of the scg_downsampler object
            // note that downsampling is used for more responsive realtime-display,
                // but the full sampling rate is used for the signal processing
            if (scg_downsampler.step(new_scg_filt_val)) {

                // calculate the mean filtered scg value over the down-sampled period
                new_scg_filt_val = scg_downsampler.get_mean();

                // display the downsampled, filtered SCG signal
                DataPoint new_scg_filt_point = new DataPoint(scgGraphX, new_scg_filt_val);
                scg_display_buf.appendData(new_scg_filt_point, true, SCG_PREVIEW_SIZE);

                // display the SCG beats once they are identified
                if (scg_beat_delay > 0) {
                    scg_beat_delay -= 4;
                    if (scg_beat_delay <= 0) {
                        scg_beat_delay = 0;
                        scg_beat_toggle = 1 - scg_beat_toggle;
                    }
                }

                // display the SCG pulse events as they are detected
                DataPoint scg_peak_point = new DataPoint(scgGraphX - SCG_SEARCH_TO, scg_beat_toggle);
                scg_beats.appendData(scg_peak_point, true, SCG_PREVIEW_SIZE);

                // display the SCG search window
                // TODO this may be plotted for debugging or just deleted
                if (scg_search_box_counter > 0) {
                    DataPoint scg_search_box_point = new DataPoint(scgGraphX - SCG_SEARCH_TO, 1);
                    scg_search_box.appendData(scg_search_box_point, true, SCG_PREVIEW_SIZE);
                    scg_search_box_counter -= 4;
                } else {
                    DataPoint scg_search_box_point = new DataPoint(scgGraphX - SCG_SEARCH_TO, 0);
                    scg_search_box.appendData(scg_search_box_point, true, SCG_PREVIEW_SIZE);
                }

            }

            // TODO this may be plotted for debugging or just deleted
            if (scg_spikey_downsampler.step(new_scg_spikey_val)) {
                // display the spikey value (decimated, i.e. downsampled but not averaged)
                scg_spikes.appendData(new DataPoint(scgGraphX, scg_spikey_downsampler.get_max()), true, SCG_PREVIEW_SIZE);
            }

            // increment SCG x axis counter
            scgGraphX++;

        } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            // logging the gyroscope data while recording -- not used for any signal proecssing
            if (recording) {
                gyro_time.add(System.nanoTime());
                gyro_x.add(event.values[0]);
                gyro_y.add(event.values[1]);
                gyro_z.add(event.values[2]);
            }
        }
    }

    // searches backwards to find the SCG beat preceding the most recent PTT beat
    private void ptt_calc() {

        // counts the number of times the ptt calculation has been triggered
        ptt_counter++;

        // ignore the first three triggers - the filters probably haven't settled yet
        if (ptt_counter < 3) {
            return;
        }

        // catch the edge case near the beginning of the measurement (not enough points recorded)
        if (scg_spikey_all.size() < SCG_SEARCH_TO * 5) {
            return;
        }

        // start index at most recent value
        int index = scg_spikey_all.size() - 1 - SCG_SEARCH_FROM;
        // stop searching after passing the given
        int min_index = scg_spikey_all.size() - 1 - SCG_SEARCH_TO;

        // find maximum of spikey filter output
        int max_spikey_loc = index;
        double max_val = scg_spikey_all.get(index);
        while (index > min_index) {
            if (scg_spikey_all.get(index) > max_val) {
                max_spikey_loc = index;
                max_val = scg_spikey_all.get(index);
            }
            index--;
        }

        // find the maximum of the scg signal (not the spikey version) for the actual event
        // note that the spikey filt is squared (always positive), whereas we actually want to find
        // the most positive peak to mark as the SCG pulse
        index = max_spikey_loc - 5;
        int max_scg_loc = index;
        double max_scg = scg_all.get(index);
        for (int i = 0; i < 10; i++) {
            if (scg_all.get(index+i) > max_scg) {
                max_scg_loc = index+i;
                max_scg = scg_all.get(index+i);
            }
        }

        // set scg beat delay to display beat timing
        scg_beat_delay = SCG_SEARCH_TO - (scg_spikey_all.size() - max_scg_loc);

        // display search box of the appropriate size
        // TODO this can be plotted for debugging or deleted
        scg_search_box_counter = SCG_SEARCH_TO - SCG_SEARCH_FROM;


        // the time of the ppg peak that triggered this measurement
        // 1 / (30 fps) = 33.33 ms
        double ppg_peak_time = (PPG_SEARCH_TO - ppg_beat_delay) * 33.33;

        // the time of the SCG beat just identified
        // 1 / (402.7 Hz) = 2.48 ms
        double scg_peak_time = (SCG_SEARCH_TO - scg_beat_delay) * 2.48;

        // the pulse transit time is the timing differential between these two events
        double ptt = scg_peak_time - ppg_peak_time;

        // record the stream of ptt measurements
        if (recording) {
            ptt_recorded.add(ptt);
            ptt_time.add(System.nanoTime());
        }

        // Calculate mean ptt with moving average
        if (80 < ptt && ptt < 300) {
            ptt_smoothed = ptt_mean_filt.step(ptt);

            displayPTT(ptt_smoothed);
        }


    }

    // displays the given PTT value to the user
    public void displayPTT(double ptt){
        final double final_ptt = ptt;
        if (null == getActivity()) {
            return; // prevents app from crashing when back button is pressed
        }
        getActivity().runOnUiThread(new Runnable() { // Any changes to the text on the UI must go through the main UI Thread
            @Override
            public void run() {
                TextView ptt_view = (TextView) getActivity().findViewById(R.id.view_ptt);
                ptt_view.setText(String.format("%.1f ms", final_ptt));
//                ptt_view.setText(" ");
            }
        });
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }



    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 235) { //1080
                return size;
            }

        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Bundle args = getArguments();
        mCameraId = args.getString("id");
        return inflater.inflate(R.layout.fragment_camera0, container, false);
    }

    // configures all of the graphs displayed to the user
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = (Camera_AutoFitTextureView) view.findViewById(R.id.texture0);
        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);


        // Initialize graph0 properties
        GraphView graph0 = view.findViewById(R.id.graph0);
        graph0.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        graph0.setBackgroundColor(Color.TRANSPARENT);
        graph0.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph0.getGridLabelRenderer().setVerticalLabelsVisible(false);

        // Display filtered PPG signal
        ppg_display_buf.setColor(graphColor[0]);
        ppg_display_buf.setThickness(10);
        graph0.addSeries(ppg_display_buf);

        // Display PPG peak detection
        ppg_beats.setThickness(10);
        ppg_beats.setColor(graphColor[1]);
        graph0.getSecondScale().addSeries(ppg_beats);
        graph0.getSecondScale().setMinY(0.45);
        graph0.getSecondScale().setMaxY(0.55);
        graph0.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.WHITE);


        // Initialize graph1 properties
        GraphView graph1 = view.findViewById(R.id.graph1);
        graph1.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        graph1.setBackgroundColor(Color.TRANSPARENT);
        graph1.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph1.getGridLabelRenderer().setVerticalLabelsVisible(false);

        // Display filtered SCG signal
        scg_display_buf.setColor(graphColor[1]);
        scg_display_buf.setThickness(10);
        graph1.addSeries(scg_display_buf);

        // Display SCG peak detection
        scg_beats.setThickness(10);
        scg_beats.setColor(graphColor[0]);
        graph1.getSecondScale().addSeries(scg_beats);
        graph1.getSecondScale().setMinY(0.45);
        graph1.getSecondScale().setMaxY(0.55);
        graph1.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.WHITE);

        GraphView graph2 = view.findViewById(R.id.graph2);
        graph2.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        graph2.setBackgroundColor(Color.TRANSPARENT);
        graph2.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph2.getGridLabelRenderer().setVerticalLabelsVisible(false);
        graph2.getViewport().setYAxisBoundsManual(true);
        graph2.getViewport().setMinY(150);
        graph2.getViewport().setMaxY(250);

        ptt_plot.setColor(graphColor[2]);
        ptt_plot.setThickness(7);
        graph2.addSeries(ptt_plot);



        // Display search box visualization
//        ppg_beats.setColor(graphColor[3]);
//        graph2.getSecondScale().addSeries(scg_search_box);
//        graph2.getSecondScale().setMinY(0);
//        graph2.getSecondScale().setMaxY(1);
//        graph2.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.WHITE);

        // Display SCG spikey filter
//        bp_plot.setColor(graphColor[2]);
//        bp_plot.setThickness(7);
//        graph2.addSeries(bp_plot);
//        graph2.getViewport().setYAxisBoundsManual(true);
//        graph2.getViewport().setMinY(2);
//        graph2.getViewport().setMaxY(8);
    }


    /* this is code for recording saved files */

    public void writeAccelData(PrintWriter writer) {
        writer.println("accel_time,accel_x,accel_y,accel_z");
        for (int i = 0; i < Integer.min(accel_time.size(), gyro_time.size()); i++) {
            StringJoiner joiner = new StringJoiner(",");
            joiner.add(accel_time.get(i).toString());
            joiner.add(accel_x.get(i).toString());
            joiner.add(accel_y.get(i).toString());
            joiner.add(accel_z.get(i).toString());
            writer.println(joiner.toString());
        }
    }

    public void writeGyroData(PrintWriter writer) {
        writer.println("gyro_time,gyro_x,gyro_y,gyro_z");
        for (int i = 0; i < Integer.min(accel_time.size(), gyro_time.size()); i++) {
            StringJoiner joiner = new StringJoiner(",");
            joiner.add(gyro_time.get(i).toString());
            joiner.add(gyro_x.get(i).toString());
            joiner.add(gyro_y.get(i).toString());
            joiner.add(gyro_z.get(i).toString());
            writer.println(joiner.toString());
        }
    }

    public void writePPGData(PrintWriter writer) {
        writer.println("ppg_time,ppg_r,ppg_g,ppg_b");
        for (int i = 0; i < ppg_time.size(); i++) {
            writer.println(ppg_time.get(i) + "," + ppg_recorded_r.get(i) +
                    "," + ppg_recorded_g.get(i) + "," + ppg_recorded_b.get(i));
        }
    }

    public void writePTTData(PrintWriter writer) {
        writer.println("ptt_time,ptt");
        for (int i = 0; i < ptt_time.size(); i++) {
            writer.println(ptt_time.get(i) + "," + ptt_recorded.get(i));
        }
    }



    @Override
    public void onResume() {
        super.onResume();
        Bundle args = getArguments();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight()); //*
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
    }


    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        sensorManager.unregisterListener(this);
        stopRecordingVideo();
        super.onPause();
    }


    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  String[] permissions,
                                            int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    private void openCamera(int width, int height) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight()); //reversed
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            manager.openCamera(mCameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        } catch (Exception e) {
            Log.d("exception",e.toString());
        }

    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() throws IOException {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {

            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());



            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<Surface>();

            previewSurface = new Surface(texture);
            ImageReader mImageReader = ImageReader.newInstance(mVideoSize.getWidth(), mVideoSize.getHeight(),
                    ImageFormat.YUV_420_888, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler);

            setUpMediaRecorder();
            Surface mRecorderSurface = mMediaRecorder.getSurface();


            surfaces.add(mRecorderSurface);
            surfaces.add(mImageReader.getSurface());
            surfaces.add(previewSurface);



            mPreviewBuilder.addTarget(mImageReader.getSurface());
            mPreviewBuilder.addTarget(previewSurface);
            mPreviewBuilder.addTarget(mRecorderSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.d("camera exception start",e.toString());
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpPreviewRequestBuilder(mPreviewBuilder);
            //setUpPreviewRequestBuilder_M(mPreviewBuilder);//Morelle
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void setUpPreviewRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time);
        builder.set(CaptureRequest.SENSOR_FRAME_DURATION,frame_duration);
        builder.set(CaptureRequest.SENSOR_SENSITIVITY,iso);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range(59,60));
        mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);    //set flash on
        RggbChannelVector correctionGains;

        correctionGains = new RggbChannelVector(gain_setting[0], gain_setting[1], gain_setting[1], gain_setting[2]);


        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraCharacteristics.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);//
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, correctionGains);
    }


    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(getActivity());
        }
//        try {
//            accel_writer = new PrintWriter(mNextVideoAbsolutePath.replace(".mp4", ".acc"));
//            gyro_writer = new PrintWriter(mNextVideoAbsolutePath.replace(".mp4", ".gyro"));
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        accel_writer.println("upTime" + "," + "Acc X" + "," + "Acc Y" + "," + "Acc Z");
//        gyro_writer.println("upTime" + "," + "Gyro X" + "," + "Gyro Y" + "," + "Gyro Z");
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(60);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
        Log.d("tag", "recording set up");
    }

    private String getVideoFilePath(Context context) {
        SharedPreferences mSharedPreferences = this.getActivity().getSharedPreferences("pref", 0);
        String id = Integer.toString(0); // TODO replace this with real subject ID
        String measurement_time = "um";
        return context.getExternalFilesDir(null).getAbsolutePath() + "/seismo_"
                + id + "_" + measurement_time + "_" + ".mp4";  // format = id-measurement_time.mp4
    }


    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void stopRecordingVideo() {
        if (null == getActivity()) {
            return;
        }
//        mIsRecordingVideo = false;
//        if (accel_writer != null) {
//            accel_writer.close();
//        }
//        if (gyro_writer != null) {
//            gyro_writer.close();
//        }
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
        mNextVideoAbsolutePath = null;
    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }


}
