package com.wimt.eta.service;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleRepository {
    private final JdbcClient jdbc;

    public ScheduleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScheduleStop> findSchedule(String trainNumber) {
        return jdbc.sql("""
                        select ts.seq, ts.station_code, s.name, s.latitude, s.longitude, ts.platform,
                               ts.sch_arr, ts.sch_dep, ts.day_offset, ts.distance_km
                        from train_schedule ts join stations s on s.code = ts.station_code
                        where ts.train_number = :t order by ts.seq
                        """)
                .param("t", trainNumber)
                .query((rs, i) -> new ScheduleStop(rs.getInt("seq"), rs.getString("station_code"), rs.getString("name"),
                        rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getString("platform"),
                        rs.getObject("sch_arr", java.time.LocalTime.class), rs.getObject("sch_dep", java.time.LocalTime.class),
                        rs.getInt("day_offset"), rs.getDouble("distance_km")))
                .list();
    }

    public Optional<String> findTrainName(String trainNumber) {
        return jdbc.sql("select name from trains where number = :t").param("t", trainNumber)
                .query(String.class).optional();
    }
}
