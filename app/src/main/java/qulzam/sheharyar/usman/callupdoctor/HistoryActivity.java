package qulzam.sheharyar.usman.callupdoctor;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import qulzam.sheharyar.usman.callupdoctor.historyRecyclerView.HistoryAdapter;
import qulzam.sheharyar.usman.callupdoctor.historyRecyclerView.HistoryObject;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private EditText etPayoutEmail;
    private Button btnPayout;
    private TextView tvBalance;
    private String patientOrDoctor, userId;
    private Double cost = 0.0;

    private RecyclerView historyRecyclerView;
    private RecyclerView.Adapter historyAdapter;
    private RecyclerView.LayoutManager historyLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        init();
        
        historyRecyclerView.setNestedScrollingEnabled(false);
        historyRecyclerView.setHasFixedSize(true);
        historyLayoutManager = new LinearLayoutManager(HistoryActivity.this);
        historyRecyclerView.setLayoutManager(historyLayoutManager);
        historyAdapter = new HistoryAdapter(getDataSetHistory(), HistoryActivity.this);
        historyRecyclerView.setAdapter(historyAdapter);
        
        patientOrDoctor = getIntent().getExtras().getString("patientOrDoctor");
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        getUserHistoryId();

        if(patientOrDoctor.equals("Doctors")){
            tvBalance.setVisibility(View.VISIBLE);
            btnPayout.setVisibility(View.VISIBLE);
            etPayoutEmail.setVisibility(View.VISIBLE);
        }

        btnPayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPayout();
            }
        });
    }

    private void init() {
        tvBalance = findViewById(R.id.tvBalance);
        btnPayout = findViewById(R.id.btnPayout);
        etPayoutEmail = findViewById(R.id.etPayoutEmail);
        historyRecyclerView = (RecyclerView) findViewById(R.id.historyRecyclerView);
    }

    private void getUserHistoryId() {
        DatabaseReference userhistoryDatabaseRefRef = FirebaseDatabase.getInstance().getReference().child("Users").child(patientOrDoctor).child(userId).child("history");
        userhistoryDatabaseRefRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    for(DataSnapshot history : dataSnapshot.getChildren()){
                        FetchRideInformation(history.getKey());
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void FetchRideInformation(String checkUpKey) {
        DatabaseReference historyDatabaseRef = FirebaseDatabase.getInstance().getReference().child("history").child(checkUpKey);
        historyDatabaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    String checkUpId = dataSnapshot.getKey();
                    Long timestamp = 0L;
                    String distance = "";
                    Double checkUpPrice = 0.0;

                    if(dataSnapshot.child("timestamp").getValue() != null){
                        timestamp = Long.valueOf(dataSnapshot.child("timestamp").getValue().toString());
                    }

                    if(dataSnapshot.child("patientPaid").getValue() != null && dataSnapshot.child("doctorPaidOut").getValue() == null){
                        if(dataSnapshot.child("distance").getValue() != null){
                            checkUpPrice = Double.valueOf(dataSnapshot.child("price").getValue().toString());
                            cost += checkUpPrice;
                            tvBalance.setText("Cost: Rs. " + String.valueOf(cost));
                        }
                    }
                    
                    HistoryObject historyObject = new HistoryObject(checkUpId, getDate(timestamp));
                    historyResults.add(historyObject);
                    historyAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private String getDate(Long time) {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.setTimeInMillis(time*1000);
        String date = DateFormat.format("MM-dd-yyyy hh:mm", calendar).toString();
        return date;
    }

    private ArrayList historyResults = new ArrayList<HistoryObject>();
    private ArrayList<HistoryObject> getDataSetHistory() {
        return historyResults;
    }

    public static final MediaType MEDIA_TYPE = MediaType.parse("application/json");
    ProgressDialog progress;
    private void requestPayout() {
        progress = new ProgressDialog(this);
        progress.setTitle("Processing your payment...");
        progress.setMessage("Kindly wait...");
        progress.setCancelable(false);
        progress.show();

        final OkHttpClient client = new OkHttpClient();
        JSONObject postData = new JSONObject();
        try {
            postData.put("uid", FirebaseAuth.getInstance().getCurrentUser().getUid());
            postData.put("email", etPayoutEmail.getText());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(MEDIA_TYPE,
                postData.toString());

        final Request request = new Request.Builder()
                .url("https://us-central1-uberapp-408c8.cloudfunctions.net/payout")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Your Token")
                .addHeader("cache-control", "no-cache")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String mMessage = e.getMessage().toString();
                Log.w("Failure Response", mMessage);
                progress.dismiss();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int responseCode = response.code();

                if (response.isSuccessful())
                    switch (responseCode) {
                        case 200:
                            Snackbar.make(findViewById(R.id.layout), "Payment Successful!", Snackbar.LENGTH_LONG).show();
                            break;
                        case 501:
                            Snackbar.make(findViewById(R.id.layout), "Error: No payment available!", Snackbar.LENGTH_LONG).show();
                            break;
                        default:
                            Snackbar.make(findViewById(R.id.layout), "Error: Transaction unsuccessful!", Snackbar.LENGTH_LONG).show();
                            break;
                    }
                else
                    Snackbar.make(findViewById(R.id.layout), "Error: Transaction unsuccessful!", Snackbar.LENGTH_LONG).show();

                progress.dismiss();
            }
        });
    }
}
