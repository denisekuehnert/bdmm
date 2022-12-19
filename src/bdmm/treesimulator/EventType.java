package bdmm.treesimulator;

public class EventType {
    Event typeOfEvent;
    int demeAffected;
    int demeTarget;

    public EventType(Event event, int deme) {
        typeOfEvent = event;
        demeAffected = deme;
    }

    public EventType(Event event, int demeAffected, int demeTarget) {
        if(event != Event.MIGRATION)
            throw new RuntimeException("EventType is not defined with anything other than MIGRATION for two types.");
        typeOfEvent = event;
        this.demeAffected = demeAffected;
        this.demeTarget = demeTarget;
    }
}
