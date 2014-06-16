package com.hpcloud.mon.infrastructure.persistence;

import com.google.inject.Inject;
import com.hpcloud.mon.MonApiConfiguration;
import com.hpcloud.mon.common.model.alarm.AlarmState;
import com.hpcloud.mon.domain.model.alarmstatehistory.AlarmStateHistory;
import com.hpcloud.mon.domain.model.alarmstatehistory.AlarmStateHistoryRepository;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Serie;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.util.StringMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AlarmStateHistoryInfluxDBRepositoryImpl implements AlarmStateHistoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(AlarmStateHistoryInfluxDBRepositoryImpl.class);

    private final MonApiConfiguration config;
    private final InfluxDB influxDB;
    private final DBI mysql;

    private static final String FIND_ALARMS_SQL = "select distinct a.id from alarm as a "
            + "join sub_alarm sa on a.id = sa.alarm_id "
            + "left outer join sub_alarm_dimension dim on sa.id = dim.sub_alarm_id%s "
            + "where a.tenant_id = :tenantId and a.deleted_at is NULL";

    @Inject
    public AlarmStateHistoryInfluxDBRepositoryImpl(@Named("mysql") DBI mysql, MonApiConfiguration config) {
        this.mysql = mysql;
        this.config = config;

        this.influxDB = InfluxDBFactory.connect(this.config.influxDB.getUrl(), this.config.influxDB.getUser(),
                this.config.influxDB.getPassword());
    }

    @Override
    public List<AlarmStateHistory> findById(String tenantId, String alarmId) {

        // InfluxDB orders queries by time stamp desc by default.
        String query = String.format("select alarm_id, old_state, new_state, reason, reason_data " +
                "from alarm_state_history " +
                "where tenant_id = '%1$s' and alarm_id = '%2$s'", tenantId, alarmId);

        return queryInfluxDBForAlarmStateHistory(query);
    }

    @Override
    public Collection<AlarmStateHistory> find(String tenantId, Map<String, String> dimensions, DateTime startTime, @Nullable DateTime endTime) {

        List<String> alarmIds = null;
        // Find alarm Ids for dimensions
        try (Handle h = mysql.open()) {
            String sql = String.format(FIND_ALARMS_SQL, SubAlarmQueries.buildJoinClauseFor(dimensions));
            Query<Map<String, Object>> query = h.createQuery(sql).bind("tenantId", tenantId);
            DimensionQueries.bindDimensionsToQuery(query, dimensions);
            alarmIds = query.map(StringMapper.FIRST).list();
        }

        if (alarmIds == null || alarmIds.isEmpty()) {
            return Collections.emptyList();
        }

        String timePart = buildTimePart(startTime, endTime);
        String alarmsPart = buildAlarmsPart(alarmIds);

        String query = String.format("select alarm_id, old_state, new_state, reason, reason_data " +
                "from alarm_state_history " +
                "where tenant_id = '%1$s' %2$s %3$s", tenantId, timePart, alarmsPart);

        return queryInfluxDBForAlarmStateHistory(query);

    }

    private String buildAlarmsPart(List<String> alarmIds) {

        String s = "";
        for (String alarmId : alarmIds) {
            if (s.length() > 0) {
                s += " or ";
            }
            s += String.format(" alarm_id = '%1$s' ", alarmId);
        }

        if (s.length() > 0) {
            s = String.format(" and (%1$s)", s);
        }
        return s;
    }

    private String buildTimePart(DateTime startTime, DateTime endTime) {

        String s = "";

        if (startTime != null) {
            s += String.format(" and time > %1$ds", startTime.getMillis() / 1000);
        }

        if (endTime != null) {
            s += String.format(" and time < %1$ds", endTime.getMillis() / 1000);
        }

        return s;
    }

    private List<AlarmStateHistory> queryInfluxDBForAlarmStateHistory(String query) {

        logger.debug("Query string: {}", query);

        List<Serie> result = this.influxDB.Query(this.config.influxDB.getName(), query, TimeUnit.SECONDS);

        List<AlarmStateHistory> alarmStateHistoryList = new LinkedList<>();

        // Should only be one serie -- alarm_state_history.
        for (Serie serie : result) {
            Object[][] valObjArryArry = serie.getPoints();
            for (int i = 0; i < valObjArryArry.length; i++) {

                AlarmStateHistory alarmStateHistory = new AlarmStateHistory();
                // Time is always in position 0.
                alarmStateHistory.setTimestamp(new DateTime(new Long((Integer) valObjArryArry[i][0]) * 1000, DateTimeZone.UTC));
                // Sequence_number is always in position 1.
                alarmStateHistory.setAlarmId((String) valObjArryArry[i][2]);
                alarmStateHistory.setNewState(AlarmState.valueOf((String) valObjArryArry[i][3]));
                alarmStateHistory.setOldState(AlarmState.valueOf((String) valObjArryArry[i][4]));
                alarmStateHistory.setReason((String) valObjArryArry[i][5]);
                alarmStateHistory.setReasonData((String) valObjArryArry[i][6]);

                alarmStateHistoryList.add(alarmStateHistory);
            }
        }

        return alarmStateHistoryList;
    }
}
