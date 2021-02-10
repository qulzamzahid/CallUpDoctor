package qulzam.sheharyar.usman.callupdoctor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.paypal.android.sdk.payments.PayPalConfiguration;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentActivity;
import com.paypal.android.sdk.payments.PaymentConfirmation;


import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistorySingleActivity extends AppCompatActivity implements OnMapReadyCallback, RoutingListener {
    
    private TextView tvLocation, tvDistance, tvDate, tvUserName, tvUserPhone;
    private Button btnPay;
    private ImageView ivUserImage;
    private RatingBar rating;
    private Double price;
    private Boolean patientPaid = false;
    private DatabaseReference historyCheckUpInfoDbRef;
    private LatLng destinationLatLng, checkUpLatLng;
    private String checkUpId, userId, patientId, doctorId, userDoctorOrPatient, distance;

    private GoogleMap mMap;
    private SupportMapFragment mapFrag;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_single);
        
        init();

        Intent intent = new Intent(this, PayPalService.class);
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
        startService(intent);

        polylines = new ArrayList<>();

        checkUpId = getIntent().getExtras().getString("checkUpId");
        
        mapFrag.getMapAsync(this);

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        historyCheckUpInfoDbRef = FirebaseDatabase.getInstance().getReference().child("history").child(checkUpId);
        getCheckUpInfo();
    }

    private void init() {
        tvUserName = (TextView) findViewById(R.id.tvUserName);
        tvUserPhone = (TextView) findViewById(R.id.tvUserPhone);
        tvLocation = (TextView) findViewById(R.id.tvLocation);
        tvDistance = (TextView) findViewById(R.id.tvDistance);
        tvDate = (TextView) findViewById(R.id.tvDate);
        ivUserImage = (ImageView) findViewById(R.id.ivUserImage);
        rating = (RatingBar) findViewById(R.id.rating);
        btnPay = findViewById(R.id.btnPay);
        mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFrag);
    }

    private void getCheckUpInfo() {
        historyCheckUpInfoDbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    for (DataSnapshot child:dataSnapshot.getChildren()){
                        if (child.getKey().equals("patient")){
                            patientId = child.getValue().toString();
                            if(!patientId.equals(userId)){
                                userDoctorOrPatient = "Doctors";
                                getUserInfo("Patients", patientId);
                            }
                        }
                        if (child.getKey().equals("doctor")){
                            doctorId = child.getValue().toString();
                            if(!doctorId.equals(userId)){
                                userDoctorOrPatient = "Patients";
                                getUserInfo("Doctors", doctorId);
                                customerRelatedObjects();
                            }
                        }
                        if (child.getKey().equals("timestamp")){
                            tvDate.setText(getDate(Long.valueOf(child.getValue().toString())));
                        }
                        if (child.getKey().equals("rating")){
                            rating.setRating(Integer.valueOf(child.getValue().toString()));

                        }
                        if (child.getKey().equals("patientPaid")){
                            patientPaid =true;
                        }
                        if (child.getKey().equals("distance")){
                            distance = child.getValue().toString();
                            tvDistance.setText(distance.substring(0, Math.min(distance.length(), 5)) + " km");
                            price = Double.valueOf(distance) * 0.5;
                        }
                        if (child.getKey().equals("destination")){
                            tvLocation.setText(child.getValue().toString());
                        }
                        if (child.getKey().equals("location")){
                            checkUpLatLng = new LatLng(Double.valueOf(child.child("from").child("lat").getValue().toString()), Double.valueOf(child.child("from").child("lng").getValue().toString()));
                            destinationLatLng = new LatLng(Double.valueOf(child.child("to").child("lat").getValue().toString()), Double.valueOf(child.child("to").child("lng").getValue().toString()));
                            if(destinationLatLng != new LatLng(0,0)){
                                getRouteToMarker();
                            }
                        }
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void customerRelatedObjects() {
        rating.setVisibility(View.VISIBLE);
        btnPay.setVisibility(View.VISIBLE);
        rating.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                historyCheckUpInfoDbRef.child("rating").setValue(rating);
                DatabaseReference doctorRatingDbRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Doctors").child(doctorId).child("rating");
                doctorRatingDbRef.child(checkUpId).setValue(rating);
            }
        });
        if(patientPaid){
            btnPay.setEnabled(false);
        }else{
            btnPay.setEnabled(true);
        }
        btnPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                payPalPayment();
            }
        });
    }

    private int PAYPAL_REQUEST_CODE = 1;
    private static PayPalConfiguration config = new PayPalConfiguration()
            .environment(PayPalConfiguration.ENVIRONMENT_SANDBOX)
            .clientId(PayPalConfig.PAYPAL_CLIENT_ID);

    private void payPalPayment() {
        PayPalPayment payment = new PayPalPayment(new BigDecimal(price), "PKR", "Doctor Check Up",
                PayPalPayment.PAYMENT_INTENT_SALE);

        Intent intent = new Intent(this, PaymentActivity.class);

        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
        intent.putExtra(PaymentActivity.EXTRA_PAYMENT, payment);

        startActivityForResult(intent, PAYPAL_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PAYPAL_REQUEST_CODE){
            if(resultCode == Activity.RESULT_OK){
                PaymentConfirmation paymentConfirmation = data.getParcelableExtra(PaymentActivity.EXTRA_RESULT_CONFIRMATION);
                if(paymentConfirmation != null){
                    try{
                        JSONObject jsonObj = new JSONObject(paymentConfirmation.toJSONObject().toString());

                        String paymentResponse = jsonObj.getJSONObject("response").getString("state");

                        if(paymentResponse.equals("approved")){
                            Toast.makeText(getApplicationContext(), "Payment Successful!", Toast.LENGTH_LONG).show();
                            historyCheckUpInfoDbRef.child("patientPaid").setValue(true);
                            btnPay.setEnabled(false);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }else{
                Toast.makeText(getApplicationContext(), "Payment Unsuccessful!", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, PayPalService.class));
        super.onDestroy();
    }

    private void getUserInfo(String otherUserDoctorOrPatient, String otherUserId) {
        DatabaseReference otherUserDbRef = FirebaseDatabase.getInstance().getReference().child("Users").child(otherUserDoctorOrPatient).child(otherUserId);
        otherUserDbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if(map.get("name") != null){
                        tvUserName.setText(map.get("name").toString());
                    }
                    if(map.get("phone") != null){
                        tvUserPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("profileImageUrl") != null){
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(ivUserImage);
                    }
                }

            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private String getDate(Long time) {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(time*1000);
        String date = DateFormat.format("MM-dd-yyyy hh:mm", cal).toString();
        return date;
    }
    private void getRouteToMarker() {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(checkUpLatLng, destinationLatLng)
                .build();
        routing.execute();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};
    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong! Try again!", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onRoutingStart() {
    }
    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(checkUpLatLng);
        builder.include(destinationLatLng);
        LatLngBounds bounds = builder.build();

        int width = getResources().getDisplayMetrics().widthPixels;
        int padding = (int) (width*0.2);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);

        mMap.animateCamera(cameraUpdate);

        mMap.addMarker(new MarkerOptions().position(checkUpLatLng).title("Check Up Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_checkup)));
        mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination"));

        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();

        for (int i = 0; i <route.size(); i++) {

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
