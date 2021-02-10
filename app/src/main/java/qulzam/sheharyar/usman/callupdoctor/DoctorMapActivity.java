package qulzam.sheharyar.usman.callupdoctor;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;

import android.os.Bundle;

import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
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

public class DoctorMapActivity extends FragmentActivity implements OnMapReadyCallback, RoutingListener {

    private Button btnSignOut, btnSettings, btnSelectPatient, btnHistory;
    private Switch switchWorking;
    private TextView patientName, patientPhone, patientDestination;
    private ImageView patientProfileImage;
    private String patientId = "", destination;
    private Boolean isLoggingOut = false;
    private int flag = 0;

    private SupportMapFragment mapFrag;
    private LinearLayout patientInfo;

    private float distanceRide;
    private LatLng destinationLatLng, checkUpLatLng;

    private GoogleMap mMap;
    LocationRequest locationRequest;
    Location lastLocation;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_map);

        init();

        polylines = new ArrayList<>();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapFrag.getMapAsync(this);
        
        switchWorking.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    connectDoctor();
                }else{
                    disconnectDoctor();
                }
            }
        });

        btnSelectPatient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(flag){
                    case 1:
                        flag = 2;
                        erasePolylines();
                        if(destinationLatLng.latitude!=0.0 && destinationLatLng.longitude!=0.0){
                            getRouteToMarker(destinationLatLng);
                        }
                        btnSelectPatient.setText("Check Up Completed");

                        break;
                    case 2:
                        recordCheckUp();
                        endCheckUp();
                        break;
                }
            }
        });

        btnSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLoggingOut = true;

                disconnectDoctor();

                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(DoctorMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DoctorMapActivity.this, DoctorSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });
        
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DoctorMapActivity.this, HistoryActivity.class);
                intent.putExtra("patientOrDoctor", "Doctors");
                startActivity(intent);
                return;
            }
        });
        getAssignedPatient();
    }

    private void init() {
        btnSettings = (Button) findViewById(R.id.btnSettings);
        btnSignOut = (Button) findViewById(R.id.btnSignOut);
        btnSelectPatient = (Button) findViewById(R.id.btnSelectPatient);
        btnHistory = (Button) findViewById(R.id.btnHistory);
        switchWorking = (Switch) findViewById(R.id.switchWorking);
        patientInfo = (LinearLayout) findViewById(R.id.patientInfo);
        patientProfileImage = (ImageView) findViewById(R.id.patientProfileImage);
        patientName = (TextView) findViewById(R.id.patientName);
        patientPhone = (TextView) findViewById(R.id.patientPhone);
        patientDestination = (TextView) findViewById(R.id.patientDestination);
        mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFrag);
    }

    private void getAssignedPatient(){
        String DoctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedPatientRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(DoctorId).child("patientRequest").child("patientCheckUpId");
        assignedPatientRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    flag = 1;
                    patientId = dataSnapshot.getValue().toString();
                    getAssignedPatientCheckUpLocation();
                    getAssignedPatientDestination();
                    getAssignedPatientInfo();
                }else{
                    endCheckUp();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    Marker checkUpMarker;
    private DatabaseReference assignedPatientCheckUpLocationRef;
    private ValueEventListener assignedPatientCheckUpLocationRefListener;
    private void getAssignedPatientCheckUpLocation(){
        assignedPatientCheckUpLocationRef = FirebaseDatabase.getInstance().getReference().child("patientRequest").child(patientId).child("l");
        assignedPatientCheckUpLocationRefListener = assignedPatientCheckUpLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && !patientId.equals("")){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    if(map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    checkUpLatLng = new LatLng(locationLat,locationLng);
                    checkUpMarker = mMap.addMarker(new MarkerOptions().position(checkUpLatLng).title("Check Up Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_checkup)));
                    getRouteToMarker(checkUpLatLng);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void getRouteToMarker(LatLng checkUpLatLng) {
        if (checkUpLatLng != null && lastLocation != null){
            Routing routing = new Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(false)
                    .waypoints(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), checkUpLatLng)
                    .build();
            routing.execute();
        }
    }

    private void getAssignedPatientDestination(){
        String DoctorId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedPatientRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(DoctorId).child("patientRequest");
        assignedPatientRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("destination")!=null){
                        destination = map.get("destination").toString();
                        patientDestination.setText("Destination: " + destination);
                    }
                    else{
                        patientDestination.setText("Destination: ");
                    }

                    Double destinationLat = 0.0;
                    Double destinationLng = 0.0;
                    if(map.get("destinationLat") != null){
                        destinationLat = Double.valueOf(map.get("destinationLat").toString());
                    }
                    if(map.get("destinationLng") != null){
                        destinationLng = Double.valueOf(map.get("destinationLng").toString());
                        destinationLatLng = new LatLng(destinationLat, destinationLng);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void getAssignedPatientInfo(){
        patientInfo.setVisibility(View.VISIBLE);
        DatabaseReference patientDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Patients").child(patientId);
        patientDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name")!=null){
                        patientName.setText(map.get("name").toString());
                    }
                    if(map.get("phone")!=null){
                        patientPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("profileImageUrl")!=null){
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(patientProfileImage);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }


    private void endCheckUp(){
        btnSelectPatient.setText("Patient Check Up Completed.");
        erasePolylines();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference doctorRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(userId).child("patientRequest");
        doctorRef.removeValue();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("patientRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(patientId);
        patientId="";
        distanceRide = 0;

        if(checkUpMarker != null){
            checkUpMarker.remove();
        }
        if (assignedPatientCheckUpLocationRefListener != null){
            assignedPatientCheckUpLocationRef.removeEventListener(assignedPatientCheckUpLocationRefListener);
        }

        patientInfo.setVisibility(View.GONE);
        patientName.setText("");
        patientPhone.setText("");
        patientDestination.setText("Destination: ");
        patientProfileImage.setImageResource(R.mipmap.ic_user);
    }

    private void recordCheckUp(){
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference doctorRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(userId).child("history");
        DatabaseReference patientRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Patients").child(patientId).child("history");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("history");
        String requestId = historyRef.push().getKey();
        doctorRef.child(requestId).setValue(true);
        patientRef.child(requestId).setValue(true);

        HashMap map = new HashMap();
        map.put("doctor", userId);
        map.put("patient", patientId);
        map.put("rating", 0);
        map.put("timestamp", getCurrentTimestamp());
        map.put("destination", destination);
        map.put("location/from/lat", checkUpLatLng.latitude);
        map.put("location/from/lng", checkUpLatLng.longitude);
        map.put("location/to/lat", destinationLatLng.latitude);
        map.put("location/to/lng", destinationLatLng.longitude);
        map.put("distance", distanceRide);
        historyRef.child(requestId).updateChildren(map);
    }

    private Long getCurrentTimestamp() {
        Long timestamp = System.currentTimeMillis()/1000;
        return timestamp;
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            }else{
                checkLocationPermission();
            }
        }
    }

    LocationCallback locationCallback = new LocationCallback(){
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for(Location location : locationResult.getLocations()){
                if(getApplicationContext()!=null){

                    if(!patientId.equals("") && lastLocation!=null && location != null){
                        distanceRide += lastLocation.distanceTo(location)/1000;
                    }
                    lastLocation = location;


                    LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("doctorsAvailable");
                    DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("doctorsWorking");
                    GeoFire geoFireAvailable = new GeoFire(refAvailable);
                    GeoFire geoFireWorking = new GeoFire(refWorking);

                    switch (patientId){
                        case "":
                            geoFireWorking.removeLocation(userId);
                            geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                            break;

                        default:
                            geoFireAvailable.removeLocation(userId);
                            geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                            break;
                    }
                }
            }
        }
    };

    private void checkLocationPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle("Give Permission")
                        .setMessage("Give Permission Message")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(DoctorMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                            }
                        })
                    .create()
                    .show();
            }
            else{
                ActivityCompat.requestPermissions(DoctorMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
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
                    Toast.makeText(getApplicationContext(), "Kindly give the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }





    private void connectDoctor(){
        checkLocationPermission();
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    private void disconnectDoctor(){
        if(fusedLocationClient != null){
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("doctorsAvailable");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
    }






    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};
    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRoutingStart() {
    }
    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ route.get(i).getDistanceValue()+": duration - "+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingCancelled() {
    }
    private void erasePolylines(){
        for(Polyline line : polylines){
            line.remove();
        }
        polylines.clear();
    }
}
