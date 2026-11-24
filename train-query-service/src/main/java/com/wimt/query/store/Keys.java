package com.wimt.query.store;

import java.time.LocalDate;

public final class Keys {
    private Keys() {}

    public static String live(String trainNumber, LocalDate date) {
        return "train:live:" + trainNumber + ":" + date;
    }

    public static String board(String stationCode) {
        return "station:board:" + stationCode;
    }

    public static final String PLATFORM_CLOCK = "platform:clock";
    public static final String PROCESSED_EVENTS = "processed_events";
}
