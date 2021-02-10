package qulzam.sheharyar.usman.callupdoctor.historyRecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import qulzam.sheharyar.usman.callupdoctor.HistorySingleActivity;
import qulzam.sheharyar.usman.callupdoctor.R;

public class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener{

    public TextView checkUpId, time;
    public HistoryViewHolders(View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);

        checkUpId = (TextView) itemView.findViewById(R.id.tvCheckUpId);
        time = (TextView) itemView.findViewById(R.id.tvTime);
    }


    @Override
    public void onClick(View v) {
        Intent intent = new Intent(v.getContext(), HistorySingleActivity.class);
        Bundle b = new Bundle();
        b.putString("checkUpId", checkUpId.getText().toString());
        intent.putExtras(b);
        v.getContext().startActivity(intent);
    }
}
