package com.hpcloud.mon.app.command;

import static com.hpcloud.dropwizard.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.Test;

import com.hpcloud.mon.app.command.CreateAlarmCommand;
import com.hpcloud.mon.domain.model.AbstractModelTest;

@Test
public class CreateAlarmCommandTest extends AbstractModelTest {
  public void shouldDeserializeFromJson() throws Exception {
    Map<String, String> dimensions = new HashMap<String, String>();
    dimensions.put("instanceId", "392633");
    /** todo: Check the null value to get works **/
    CreateAlarmCommand newAlarm =
        new CreateAlarmCommand("Disk Exceeds 1k Operations", null, null,
            "avg(hpcs.compute:cpu:1:{instance_id=5}) > 5", Arrays.asList("123345345", "23423"),
            null, null);

    String json = jsonFixture("fixtures/newAlarm.json");
    CreateAlarmCommand alarm = fromJson(json, CreateAlarmCommand.class);
    assertEquals(alarm, newAlarm);
  }
}
