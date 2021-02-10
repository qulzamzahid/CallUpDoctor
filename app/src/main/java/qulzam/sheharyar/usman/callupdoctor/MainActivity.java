package qulzam.sheharyar.usman.callupdoctor;

import android.content.Intent;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private Button btnDoctor, btnPatient;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();

        startService(new Intent(MainActivity.this, AppKill.class));

        btnDoctor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DoctorSignInActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        btnPatient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PatientSignInActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
    }

    private void init() {
        btnDoctor = (Button) findViewById(R.id.btnDoctor);
        btnPatient = (Button) findViewById(R.id.btnPatient);
    }
}
