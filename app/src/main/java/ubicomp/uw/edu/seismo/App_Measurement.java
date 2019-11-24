package ubicomp.uw.edu.seismo;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;


public class App_Measurement extends Activity {

    private App_MeasurementFragment measure_frag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement_session);
        measure_frag = new App_MeasurementFragment();

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.measurement_session_fragment, measure_frag);
        ft.commit();

    }

}