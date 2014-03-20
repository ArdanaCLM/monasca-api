package com.hpcloud.mon.resource;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.testng.annotations.Test;

import com.hpcloud.mon.app.AlarmService;
import com.hpcloud.mon.app.command.CreateAlarmCommand;
import com.hpcloud.mon.app.representation.AlarmRepresentation;
import com.hpcloud.mon.app.representation.AlarmsRepresentation;
import com.hpcloud.mon.common.model.alarm.AlarmExpression;
import com.hpcloud.mon.common.model.alarm.AlarmState;
import com.hpcloud.mon.domain.exception.EntityNotFoundException;
import com.hpcloud.mon.domain.model.alarm.Alarm;
import com.hpcloud.mon.domain.model.alarm.AlarmDetail;
import com.hpcloud.mon.domain.model.alarm.AlarmRepository;
import com.hpcloud.mon.domain.model.common.Link;
import com.hpcloud.mon.resource.exception.ErrorMessages;
import com.sun.jersey.api.client.ClientResponse;

/**
 * @author Jonathan Halterman
 */
@Test(enabled = false)
public class AlarmResourceTest extends AbstractMonApiResourceTest {
  private AlarmDetail alarm;
  private Alarm alarmItem;
  private AlarmService service;
  private AlarmRepository repo;
  private List<String> alarmActions;

  @Override
  @SuppressWarnings("unchecked")
  protected void setupResources() throws Exception {
    super.setupResources();
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops}) >= 90";

    alarmItem = new Alarm("123", "Disk Exceeds 1k Operations", null, expression, AlarmState.OK);
    alarmActions = new ArrayList<String>();
    alarmActions.add("29387234");
    alarmActions.add("77778687");
    alarm = new AlarmDetail("123", "Disk Exceeds 1k Operations", null, expression, AlarmState.OK,
        alarmActions, null, null);

    service = mock(AlarmService.class);
    when(
        service.create(eq("abc"), eq("Disk Exceeds 1k Operations"), any(String.class),
            eq(expression), eq(AlarmExpression.of(expression)), any(List.class), any(List.class),
            any(List.class))).thenReturn(alarm);

    repo = mock(AlarmRepository.class);
    when(repo.findById(eq("abc"), eq("123"))).thenReturn(alarm);
    when(repo.find(anyString())).thenReturn(Arrays.asList(alarmItem));

    addResources(new AlarmResource(service, repo));
  }

  @SuppressWarnings("unchecked")
  public void shouldCreate() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops}) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    assertEquals(response.getStatus(), 201);
    AlarmDetail newAlarm = response.getEntity(AlarmRepresentation.class).alarm;
    String location = response.getHeaders().get("Location").get(0);
    assertEquals(location, "/v1.1/alarms/" + newAlarm.getId());
    assertEquals(newAlarm, alarm);
    verify(service).create(eq("abc"), eq("Disk Exceeds 1k Operations"), any(String.class),
        eq(expression), eq(AlarmExpression.of(expression)), any(List.class), any(List.class),
        any(List.class));
  }

  public void shouldErrorOnCreateWithInvalidDimensions() {
    String expression = "avg(hpcs.compute{instance_id=937, metric_name=disk_read_ops}) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "The required dimensions");
  }

  public void shouldErrorOnCreateWithDuplicateDimensions() {
    String expression = "avg(hpcs.compute{instance_id=937, instance_id=123, az=2, instance_uuid=abc123, metric_name=disk_read_ops}) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "The alarm expression is invalid",
        "More than one value was given for dimension instance_id");
  }

  public void shouldErrorOnCreateWithEmptyDimensionsForHpCloudNamespace() {
    String expression = "avg(hpcs.compute{metric_name=disk_read_ops}) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "The required dimensions [instance_id, instance_uuid, az] are not present");
  }

  @SuppressWarnings("unchecked")
  public void shouldNotRequireDimensionsForCustomNamespace() {
    String expression = "avg(foo{metric_name=bar}) >= 90";
    when(
        service.create(eq("abc"), eq("Disk Exceeds 1k Operations"), any(String.class),
            eq(expression), eq(AlarmExpression.of(expression)), any(List.class), any(List.class),
            any(List.class))).thenReturn(alarm);
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));
    assertEquals(response.getStatus(), 201);
  }

  public void shouldErrorOnCreateWithInvalidJson() {
    ClientResponse response = createResponseFor("{\"alarmasdf\"::{\"name\":\"Disk Exceeds 1k Operations\"}}");

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("bad_request", 400,
        "Unable to process the provided JSON", "Unexpected character (':'");
  }

  public void shouldErrorOnCreateWithInvalidMetricType() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=blah}) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "blah is not a valid metric name for namespace hpcs.compute");
  }

  public void shouldErrorOnCreateWithInvalidOperator() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops}) & 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "The alarm expression is invalid", "Syntax Error");
  }

  public void shouldErrorOnCreateWithInvalidAlarmActions() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops}) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, null));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("bad_request", 400,
        "The request entity had the following errors:",
        "[alarm.alarmActions may not be empty (was null)]");
  }

  public void shouldErrorOnCreateWith0Period() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops},0) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "Period must not be 0");
  }

  public void shouldErrorOnCreateWithNonMod60Period() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops},61) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "Period 61 must be a multiple of 60");
  }

  public void shouldErrorOnCreateWithPeriodsLessThan1() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops}) >= 90 times 0";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "Periods 0 must be greater than or equal to 1");
  }

  public void shouldErrorOnCreateWithPeriodTimesPeriodsGT2Weeks() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops},60) >= 90 times 20161";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class)).matches("unprocessable_entity", 422,
        "Period 60 times 20161 must total less than 2 weeks in seconds (1209600)");
  }

  public void shouldErrorOnCreateWithTooLongName() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops}) >= 90";
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
            + "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
            + "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789",
        expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class))
        .matches(
            "unprocessable_entity",
            422,
            "Name 012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789 must be 250 characters or less");
  }

  public void shouldErrorOnCreateWithTooLongAlarmAction() {
    String expression = "avg(hpcs.compute{instance_id=937, az=2, instance_uuid=0ff588fc-d298-482f-bb11-4b52d56801a4, metric_name=disk_read_ops}) >= 90";
    alarmActions = new ArrayList<String>();
    alarmActions.add("012345678901234567890123456789012345678901234567890");
    ClientResponse response = createResponseFor(new CreateAlarmCommand(
        "Disk Exceeds 1k Operations", expression, alarmActions));

    ErrorMessages.assertThat(response.getEntity(String.class))
        .matches("unprocessable_entity", 422,
            "Alarm action 012345678901234567890123456789012345678901234567890 must be 50 characters or less");
  }

  public void shouldList() {
    AlarmsRepresentation alarms = client().resource("/v1.1/alarms")
        .header("X-Tenant-Id", "abc")
        .get(AlarmsRepresentation.class);

    assertEquals(alarms, new AlarmsRepresentation(Arrays.asList(alarmItem)));
    verify(repo).find(eq("abc"));
  }

  public void shouldGet() {
    assertEquals(
        client().resource("/v1.1/alarms/123")
            .header("X-Tenant-Id", "abc")
            .get(AlarmRepresentation.class).alarm, alarm);
    verify(repo).findById(eq("abc"), eq("123"));
  }

  public void should404OnGetInvalid() {
    doThrow(new EntityNotFoundException(null)).when(repo).findById(eq("abc"), eq("999"));

    try {
      client().resource("/v1.1/alarms/999")
          .header("X-Tenant-Id", "abc")
          .get(AlarmRepresentation.class);
      fail();
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("404"));
    }
  }

  public void shouldDelete() {
    ClientResponse response = client().resource("/v1.1/alarms/123")
        .header("X-Tenant-Id", "abc")
        .delete(ClientResponse.class);
    assertEquals(response.getStatus(), 204);
    verify(service).delete(eq("abc"), eq("123"));
  }

  public void should404OnDeleteInvalid() {
    doThrow(new EntityNotFoundException(null)).when(service).delete(eq("abc"), eq("999"));

    try {
      client().resource("/v1.1/alarms/999").header("X-Tenant-Id", "abc").delete();
      fail();
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("404"));
    }
  }

  public void should500OnInternalException() {
    doThrow(new RuntimeException("")).when(repo).find(anyString());

    try {
      client().resource("/v1.1/alarms")
          .header("X-Tenant-Id", "abc")
          .get(AlarmsRepresentation.class);
      fail();
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("500"));
    }
  }

  public void shouldHydateLinksOnList() {
    List<Link> expected = Arrays.asList(new Link("self", "/v1.1/alarms/123"));
    List<Link> links = client().resource("/v1.1/alarms")
        .header("X-Tenant-Id", "abc")
        .get(AlarmsRepresentation.class).alarms.get(0).getLinks();
    assertEquals(links, expected);
  }

  public void shouldHydateLinksOnGet() {
    List<Link> links = Arrays.asList(new Link("self", "/v1.1/alarms/123"));
    assertEquals(
        client().resource("/v1.1/alarms/123")
            .header("X-Tenant-Id", "abc")
            .get(AlarmRepresentation.class).alarm.getLinks(), links);
  }

  private ClientResponse createResponseFor(Object request) {
    return client().resource("/v1.1/alarms")
        .header("X-Tenant-Id", "abc")
        .header("Content-Type", MediaType.APPLICATION_JSON)
        .post(ClientResponse.class, request);
  }
}