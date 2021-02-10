package qulzam.sheharyar.usman.callupdoctor.historyRecyclerView;

public class HistoryObject {
    private String checkUpId, time;

    public HistoryObject(String checkUpId, String time){
        this.checkUpId = checkUpId;
        this.time = time;
    }

    public String getCheckUpId(){return checkUpId;}
    public void setCheckUpId(String checkUpId) {
        this.checkUpId = checkUpId;
    }

    public String getTime(){return time;}
    public void setTime(String time) {
        this.time = time;
    }
}
