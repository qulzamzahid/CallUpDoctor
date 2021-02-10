package qulzam.sheharyar.usman.callupdoctor;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;

import android.os.Bundle;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatientMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private TextView doctorName, doctorPhone, doctorType;
    private ImageView doctorProfileImage;
    private RatingBar rating;
    private RadioGroup radioGroup;
    private Button btnSignOut, btnCallDoctor, btnSettings, btnHistory;
    private String destination, requestDoctorType, doctorFoundID;
    private Boolean request = false, doctorFound = false, getDoctorsAroundStarted = false;
    private int radius = 1;

    private LinearLayout doctorInfo;
    private SupportMapFragment mapFrag;
    PlaceAutocompleteFragment placeAutoCompleteFrag;

    private GoogleMap mMap;
    Location lastLocation;
    LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationClient;

    private LatLng destinationLatLng, checkUpLocation;
    private Marker checkUpMarker, doctorMarker;
    private DatabaseReference doctorLocationRef, checkUpHasEndedRef;
    private ValueEventListener doctorLocationRefListener, checkUpHasEndedRefListener;

    GeoQuery geoQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_map);
        
        init();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        mapFrag.getMapAsync(this);

        destinationLatLng = new LatLng(0.0,0.0);
        
        radioGroup.check(R.id.physiotherapist);

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PatientMapActivity.this, PatientSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PatientMapActivity.this, HistoryActivity.class);
                intent.putExtra("patientOrDoctor", "Patients");
                startActivity(intent);
                return;
            }
        });
        
        btnSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(PatientMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        btnCallDoctor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                
                if (request)
                {
                    endCheckUp();
                }
                else
                {
                    int selectId = radioGroup.getCheckedRadioButtonId();
                    final RadioButton radioButton = (RadioButton) findViewById(selectId);

                    if (radioButton.getText() == null)
                    {
                        return;
                    }

                    requestDoctorType = radioButton.getText().toString();
                    request = true;

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("patientRequest");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()));

                    checkUpLocation = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                    checkUpMarker = mMap.addMarker(new MarkerOptions().position(checkUpLocation).title("Check Up Here!").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_checkup)));

                    btnCallDoctor.setText("Finding your Doctor.....");

                    getNearestDoctor();
                }
            }
        });

        placeAutoCompleteFrag.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                destination = place.getName().toString();
                destinationLatLng = place.getLatLng();
            }
            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
            }
        });


    }

    private void init() {
        btnSettings = (Button) findViewById(R.id.btnSettings);
        btnHistory = (Button) findViewById(R.id.btnHistory);
        btnSignOut = (Button) findViewById(R.id.btnSignOut);
        btnCallDoctor = (Button) findViewById(R.id.btnCallDoctor);
        doctorName = (TextView) findViewById(R.id.doctorName);
        doctorPhone = (TextView) findViewById(R.id.doctorPhone);
        doctorType = (TextView) findViewById(R.id.doctorType);
        doctorInfo = (LinearLayout) findViewById(R.id.doctorInfo);
        doctorProfileImage = (ImageView) findViewById(R.id.doctorProfileImage);
        rating = (RatingBar) findViewById(R.id.rating);
        radioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFrag);
        placeAutoCompleteFrag = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.placeAutoCompleteFrag);
    }
    
    private void getNearestDoctor(){
        DatabaseReference doctorLocation = FirebaseDatabase.getInstance().getReference().child("doctorsAvailable");

        GeoFire geoFire = new GeoFire(doctorLocation);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(checkUpLocation.latitude, checkUpLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!doctorFound && request){
                    DatabaseReference patientDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(key);
                    patientDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                                Map<String, Object> doctorMap = (Map<String, Object>) dataSnapshot.getValue();
                                if (doctorFound){
                                    return;
                                }

                                if(doctorMap.get("service").equals(requestDoctorType)){
                                    doctorFound = true;
                                    doctorFoundID = dataSnapshot.getKey();

                                    DatabaseReference doctorRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(doctorFoundID).child("patientRequest");
                                    String patientId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                    HashMap map = new HashMap();
                                    map.put("patientCheckUpId", patientId);
                                    map.put("destination", destination);
                                    map.put("destinationLat", destinationLatLng.latitude);
                                    map.put("destinationLng", destinationLatLng.longitude);
                                    doctorRef.updateChildren(map);

                                    getDoctorLocation();
                                    getDoctorInfo();
                                    getHasCheckUpEnded();
                                    btnCallDoctor.setText("Finding for Doctor Location.....");
                                }
                            }
                        }
                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                        }
                    });
                }
            }

            @Override
            public void onKeyExited(String key) {
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
            }

            @Override
            public void onGeoQueryReady() {
                if (!doctorFound)
                {
                    radius++;
                    getNearestDoctor();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
            }
        });
    }
    
    private void getDoctorLocation(){
        doctorLocationRef = FirebaseDatabase.getInstance().getReference().child("doctorsWorking").child(doctorFoundID).child("l");
        doctorLocationRefListener = doctorLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && request){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    if(map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng doctorLatLng = new LatLng(locationLat,locationLng);
                    if(doctorMarker != null){
                        doctorMarker.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(checkUpLocation.latitude);
                    loc1.setLongitude(checkUpLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(doctorLatLng.latitude);
                    loc2.setLongitude(doctorLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);

                    if (distance<100){
                        btnCallDoctor.setText("Your Doctor's Here!");
                    }else{
                        btnCallDoctor.setText("Doctor Found: " + String.valueOf(distance));
                    }
                    
                    doctorMarker = mMap.addMarker(new MarkerOptions().position(doctorLatLng).title("Your Doctor's Here!").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_ambulance)));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });

    }

    private void getDoctorInfo(){
        doctorInfo.setVisibility(View.VISIBLE);
        DatabaseReference patientDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(doctorFoundID);
        patientDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    if(dataSnapshot.child("name")!=null){
                        doctorName.setText(dataSnapshot.child("name").getValue().toString());
                    }
                    if(dataSnapshot.child("phone")!=null){
                        doctorPhone.setText(dataSnapshot.child("phone").getValue().toString());
                    }
                    if(dataSnapshot.child("type")!=null){
                        doctorType.setText(dataSnapshot.child("type").getValue().toString());
                    }
                    if(dataSnapshot.child("profileImageUrl").getValue()!=null){
                        Glide.with(getApplication()).load(dataSnapshot.child("profileImageUrl").getValue().toString()).into(doctorProfileImage);
                    }

                    int ratingsSum = 0;
                    float ratingTotal = 0;
                    float ratingAvg = 0;
                    for (DataSnapshot child : dataSnapshot.child("rating").getChildren()){
                        ratingsSum = ratingsSum + Integer.valueOf(child.getValue().toString());
                        ratingTotal++;
                    }
                    if(ratingTotal!= 0){
                        ratingAvg = ratingsSum/ratingTotal;
                        rating.setRating(ratingAvg);
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }
    
    private void getHasCheckUpEnded(){
        checkUpHasEndedRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(doctorFoundID).child("patientRequest").child("patientCheckUpId");
        checkUpHasEndedRefListener = checkUpHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){

                }else{
                    endCheckUp();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void endCheckUp(){
        request = false;
        geoQuery.removeAllListeners();
        doctorLocationRef.removeEventListener(doctorLocationRefListener);
        checkUpHasEndedRef.removeEventListener(checkUpHasEndedRefListener);

        if (doctorFoundID != null){
            DatabaseReference doctorRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(doctorFoundID).child("patientRequest");
            doctorRef.removeValue();
            doctorFoundID = null;
        }
        doctorFound = false;
        radius = 1;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("patientRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        if(checkUpMarker != null){
            checkUpMarker.remove();
        }
        if (doctorMarker != null){
            doctorMarker.remove();
        }
        btnCallDoctor.setText("Call Doctor");

        doctorInfo.setVisibility(View.GONE);
        doctorName.setText("");
        doctorPhone.setText("");
        doctorType.setText("");
        doctorProfileImage.setImageResource(R.mipmap.ic_user);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            }else{
                checkLocationPermission();
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    LocationCallback locationCallback = new LocationCallback(){
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for(Location location : locationResult.getLocations()){
                if(getApplicationContext()!=null){
                    lastLocation = location;

                    LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());

                    //mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    //mMap.animateCamera(CameraUpdateFactory.zoomTo(11));
                    if(!getDoctorsAroundStarted)
                        getDoctorsAround();
                }
            }
        }
    };
    
    private void checkLocationPermission() {
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Give Permission")
                        .setMessage("Give Permission Message")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(PatientMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                            }
                        })
                        .create()
                        .show();
            }
            else{
                ActivityCompat.requestPermissions(PatientMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case 1:{
                if(grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                } else{
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    List<Marker> marker = new ArrayList<Marker>();
    private void getDoctorsAround(){
        getDoctorsAroundStarted = true;
        DatabaseReference doctorLocation = FirebaseDatabase.getInstance().getReference().child("doctorsAvailable");

        GeoFire geoFire = new GeoFire(doctorLocation);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(lastLocation.getLongitude(), lastLocation.getLatitude()), 999999999);

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {

                for(Marker markers : marker){
                    if(markers.getTag().equals(key))
                        return;
                }

                LatLng doctorLocation = new LatLng(location.latitude, location.longitude);

                Marker doctorMarker = mMap.addMarker(new MarkerOptions().position(doctorLocation).title(key).icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_ambulance)));
                doctorMarker.setTag(key);

                marker.add(doctorMarker);


            }

            @Override
            public void onKeyExited(String key) {
                for(Marker markers : marker){
                    if(markers.getTag().equals(key)){
                        markers.remove();
                    }
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                for(Marker markers : marker){
                    if(markers.getTag().equals(key)){
                        markers.setPosition(new LatLng(location.latitude, location.longitude));
                    }
                }
            }

            @Override
            public void onGeoQueryReady() {
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }
}
