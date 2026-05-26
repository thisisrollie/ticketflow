package com.rolliedev.ticketflow.entity.enums;

import java.util.List;

public enum SlaStatus {
    ON_TRACK,
    PAUSED,
    MET,
    BREACHED;

    public static List<SlaStatus> getResponseSlaStatuses() {
        return List.of(ON_TRACK, MET, BREACHED);
    }

    public static List<SlaStatus> getResolutionSlaStatuses() {
        return List.of(ON_TRACK, PAUSED, MET, BREACHED);
    }
}
