package qulzam.sheharyar.usman.callupdoctor;

import android.content.Intent;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DoctorSignInActivity extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private Button btnSignIn, btnRegister;

    private FirebaseAuth.AuthStateListener firebaseAuthListener;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_signin);
        
        init();

        auth = FirebaseAuth.getInstance();

        firebaseAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if(user!=null){
                    Intent intent = new Intent(DoctorSignInActivity.this, DoctorMapActivity.class);
                    startActivity(intent);
                    finish();
                    return;
                }
            }
        };

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = etEmail.getText().toString();
                final String password = etPassword.getText().toString();
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(DoctorSignInActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(!task.isSuccessful()){
                            btnRegister.setError("Sign Up Error!");
                            Toast.makeText(DoctorSignInActivity.this, "Sign Up Error!", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            String userId = auth.getCurrentUser().getUid();
                            DatabaseReference currentUserDb = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(userId);
                            currentUserDb.setValue(true);
                            Toast.makeText(DoctorSignInActivity.this, "Successfully Registered!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        btnSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = etEmail.getText().toString();
                final String password = etPassword.getText().toString();
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(DoctorSignInActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(!task.isSuccessful()){
                            btnSignIn.setError("Sign In Error!");
                            Toast.makeText(DoctorSignInActivity.this, "Sign In Error!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void init() {
        etEmail = (EditText) findViewById(R.id.etEmail);
        etPassword = (EditText) findViewById(R.id.etPassword);
        
        btnSignIn = (Button) findViewById(R.id.btnSignIn);
        btnRegister = (Button) findViewById(R.id.btnRegister);
    }

    @Override
    protected void onStart() {
        super.onStart();
        auth.addAuthStateListener(firebaseAuthListener);
    }
    @Override
    protected void onStop() {
        super.onStop();
        auth.removeAuthStateListener(firebaseAuthListener);
    }
}
