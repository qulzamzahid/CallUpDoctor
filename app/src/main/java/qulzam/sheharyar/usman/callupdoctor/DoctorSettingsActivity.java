package qulzam.sheharyar.usman.callupdoctor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;


import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DoctorSettingsActivity extends AppCompatActivity {

    private Button btnBack, btnConfirm;
    private EditText etName, etPhone, etType;
    private ImageView ivProfileImage;
    private RadioGroup radioGroup;
    private String userID, name, phone, type, service, ivProfileImageUrl;
    
    private FirebaseAuth auth;
    private DatabaseReference doctorDatabase;
    private Uri uriResult;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_settings);

        init();

        auth = FirebaseAuth.getInstance();
        userID = auth.getCurrentUser().getUid();
        doctorDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(userID);

        getUserInfo();

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserInfo();
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                return;
            }
        });

        ivProfileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            }
        });
    }

    private void init() {
        etName = (EditText) findViewById(R.id.etName);
        etPhone = (EditText) findViewById(R.id.etPhone);
        etType = (EditText) findViewById(R.id.etType);
        radioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        btnConfirm = (Button) findViewById(R.id.btnConfirm);
        btnBack = (Button) findViewById(R.id.btnBack);
        ivProfileImage = (ImageView) findViewById(R.id.ivProfileImage);
    }

    private void getUserInfo(){
        doctorDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name")!=null){
                        name = map.get("name").toString();
                        etName.setText(name);
                    }
                    if(map.get("phone")!=null){
                        phone = map.get("phone").toString();
                        etPhone.setText(phone);
                    }
                    if(map.get("type")!=null){
                        type = map.get("type").toString();
                        etType.setText(type);
                    }
                    if(map.get("service")!=null){
                        service = map.get("service").toString();
                        switch (service){
                            case"Physiotherapist":
                                radioGroup.check(R.id.physiotherapist);
                                break;
                            case"Dermatologist":
                                radioGroup.check(R.id.physician);
                                break;
                            case"Psychiatrist":
                                radioGroup.check(R.id.psychiatrist);
                                break;
                        }
                    }
                    if(map.get("profileImageUrl")!=null){
                        ivProfileImageUrl = map.get("profileImageUrl").toString();
                        Glide.with(getApplication()).load(ivProfileImageUrl).into(ivProfileImage);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void saveUserInfo() {
        name = etName.getText().toString();
        phone = etPhone.getText().toString();
        type = etType.getText().toString();

        int selectId = radioGroup.getCheckedRadioButtonId();

        final RadioButton btnRadio = (RadioButton) findViewById(selectId);

        if (btnRadio.getText() == null){
            return;
        }

        service = btnRadio.getText().toString();

        Map userInfo = new HashMap();
        userInfo.put("name", name);
        userInfo.put("phone", phone);
        userInfo.put("type", type);
        userInfo.put("service", service);
        doctorDatabase.updateChildren(userInfo);

        if(uriResult != null) {

            StorageReference filePathRef = FirebaseStorage.getInstance().getReference().child("profileImages").child(userID);
            Bitmap bitmap = null;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(), uriResult);
            } catch (IOException e) {
                e.printStackTrace();
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 20, byteArrayOutputStream);
            byte[] data = byteArrayOutputStream.toByteArray();
            UploadTask uploadTask = filePathRef.putBytes(data);

            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    finish();
                    return;
                }
            });

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Task<Uri> downloadUrl = taskSnapshot.getMetadata().getReference().getDownloadUrl();

                    Map newImage = new HashMap();
                    newImage.put("profileImageUrl", downloadUrl.toString());
                    doctorDatabase.updateChildren(newImage);

                    finish();
                    return;
                }
            });
        }else{
            finish();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 1 && resultCode == Activity.RESULT_OK){
            final Uri imageUri = data.getData();
            uriResult = imageUri;
            ivProfileImage.setImageURI(uriResult);
        }
    }
}
