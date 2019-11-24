package ubicomp.uw.edu.seismo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class App_Main extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        App_MainFragment main_fragment = new App_MainFragment();
        ft.replace(R.id.main_fragment, main_fragment);
        ft.commit();

    }


    public void newMeasurement(View view){
        Intent intent = new Intent(this, App_Measurement.class);
        startActivity(intent);
    }


}
