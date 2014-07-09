package com.hpcloud.mon.domain.model;

import static com.hpcloud.dropwizard.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import com.hpcloud.mon.domain.model.common.Link;
import com.hpcloud.mon.domain.model.version.Version;
import com.hpcloud.mon.domain.model.version.Version.VersionStatus;

@Test
public class VersionTest extends AbstractModelTest {
  private final Version version;

  public VersionTest() {
    version =
        new Version("1.0", VersionStatus.CURRENT, new DateTime(1355253328000L, DateTimeZone.UTC));
    version.setLinks(Arrays.asList(new Link("self",
        "https://region-a.geo-1.maas.hpcloudsvc.com/v1.0")));
  }

  public void shouldSerializeToJson() throws Exception {
    String json = toJson(version);
    assertEquals(json, jsonFixture("fixtures/version.json"));
  }

  public void shouldDeserializeFromJson() throws Exception {
    String json = jsonFixture("fixtures/version.json");
    Version detail = fromJson(json, Version.class);
    assertEquals(version, detail);
  }
}
