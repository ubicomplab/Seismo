package ubicomp.uw.edu.seismo;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A placeholder fragment containing a simple view.
 */
public class App_MainFragment extends Fragment {

    public App_MainFragment() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_measure, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
    }

}
