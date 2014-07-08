package com.hpcloud.mon.infrastructure.persistence.influxdb;

import com.hpcloud.mon.MonApiConfiguration;
import com.hpcloud.mon.infrastructure.persistence.influxdb.AlarmStateHistoryInfluxDbRepositoryImpl;

import org.influxdb.InfluxDB;
import org.joda.time.DateTime;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.skife.jdbi.v2.DBI;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import scala.actors.threadpool.Arrays;

@Test
public class AlarmStateHistoryInfluxDbRepositoryImplTest {

  @Mock(name = "mysql")
  private DBI mysql;

  @Mock
  private MonApiConfiguration monApiConfiguration;

  @Mock
  private InfluxDB influxDB;

  @InjectMocks
  private AlarmStateHistoryInfluxDbRepositoryImpl alarmStateHistoryInfluxDBRepository;

  @BeforeMethod(alwaysRun = true)
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  public void buildQueryForFindByIdTest() throws Exception {

    String er =
        "select alarm_id, old_state, new_state, reason, "
            + "reason_data from alarm_state_history where tenant_id = 'tenant-id' and alarm_id = "
            + "'alarm-id'";
    String r =
        this.alarmStateHistoryInfluxDBRepository.buildQueryForFindById("tenant-id", "alarm-id");

    assert (er.equals(r));

  }

  public void buildTimePartTest() {
    String er = " and time > 1388559600s and time < 1388559601s";
    String r =
        this.alarmStateHistoryInfluxDBRepository.buildTimePart(new DateTime(2014, 01, 01, 0, 0, 0),
            new DateTime(2014, 01, 01, 0, 0, 1));
    assert (er.equals(r));

  }

  @SuppressWarnings("unchecked")
  public void buildAlarmsPartTest() {
    String er = " and ( alarm_id = 'id-1'  or  alarm_id = 'id-2' )";
    String r =
        this.alarmStateHistoryInfluxDBRepository.buildAlarmsPart(Arrays.asList(new String[] {
            "id-1", "id-2"}));
    assert (er.equals(r));
  }

  public void buildQueryForFindTest() throws Exception {
    String er =
        "select alarm_id, old_state, new_state, reason, "
            + "reason_data from alarm_state_history where tenant_id = 'tenant-id'  and time > "
            + "1388559600s and time < 1388559601s  and ( alarm_id = 'id-1'  or  alarm_id = 'id-2' )";
    String r =
        this.alarmStateHistoryInfluxDBRepository.buildQueryForFind("tenant-id",
            " and time > 1388559600s and time < 1388559601s", " and ( alarm_id = 'id-1'  or  "
                + "alarm_id" + " = 'id-2' )");

    assert (er.equals(r));
  }

}
