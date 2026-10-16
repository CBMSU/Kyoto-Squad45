package backend.dataTypes;

import java.time.LocalDateTime;

public class Timer {
    private final LocalDateTime origin;
    private final LocalDateTime callBackTime;
    private final Runnable callBack;

    public Timer(LocalDateTime origin, LocalDateTime callBackTime, Runnable callBack) {
        this.origin = origin;
        this.callBackTime = callBackTime;
        this.callBack = callBack;
    }

    public Timer(Timer orig) {
        this(orig.origin, orig.callBackTime, orig.callBack);
    }

    public Timer(Timer orig, LocalDateTime callBackTime) {
        this(orig.origin, callBackTime, orig.callBack);
    }

    public Timer(LocalDateTime callBackTime, Runnable callBack) {
        this(LocalDateTime.now(), callBackTime, callBack);
    }

    public boolean Update() {
        boolean hasPassed = callBackTime.isBefore(LocalDateTime.now());
        if(hasPassed) callBack.run();
        return hasPassed;
    }
}
