package sosina.terefe.adu.ac.ae.sawt;

public class Reminder {

    private int id;
    private String title;
    private String time;
    private String frequency;
    private int isDone;
    private String patientName;
    private long triggerAtMillis;

    public Reminder(int id, String title, String time, String frequency, int isDone,
                    String patientName, long triggerAtMillis) {
        this.id = id;
        this.title = title;
        this.time = time;
        this.frequency = frequency;
        this.isDone = isDone;
        this.patientName = patientName;
        this.triggerAtMillis = triggerAtMillis;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getTime() { return time; }
    public String getFrequency() { return frequency; }
    public int getIsDone() { return isDone; }
    public String getPatientName() { return patientName; }
    public long getTriggerAtMillis() { return triggerAtMillis; }

    public void setIsDone(int isDone) { this.isDone = isDone; }
    public void setId(int id) { this.id = id; }
}