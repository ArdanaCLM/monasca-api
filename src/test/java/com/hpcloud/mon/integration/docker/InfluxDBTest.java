package com.hpcloud.mon.integration.docker;

import com.github.dockerjava.client.DockerClient;
import com.github.dockerjava.client.DockerException;
import com.github.dockerjava.client.model.ContainerCreateResponse;
import com.github.dockerjava.client.model.ExposedPort;
import com.github.dockerjava.client.model.Ports;
import com.sun.jersey.api.client.ClientResponse;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.path.json.JsonPath.from;

@Test(groups = "integration", enabled = false)
public class InfluxDBTest {

  private final static String DDIETERLY_INFLUXDB_V1 = "ddieterly/influxdb:v1";
  private static final String DDIETERLY_MYSQL_V1 = "ddieterly/mysql:v1";
  private static final String DDIETERLY_MYSQL_V1_RUN_CMD = "/usr/bin/mysqld_safe";
  private static final String DDIETERLY_KAFKA_V1 = "ddieterly/kafka:v1";
  private static final String DDIETERLY_KAFKA_V1_RUN_CMD = "/run.sh";
  private static final String DOCKER_IP = "192.168.59.103";
  private static final String DOCKER_PORT = "2375";
  private static final String DOCKER_URL = "http://" + DOCKER_IP + ":" + DOCKER_PORT;
  private static final int MAX_CONNECT_PORT_TRIES = 20000;

  private static final String API_JAR = "./target/mon-api-0.1.0-1405114317929-387fb5.jar";

  private final static DockerClient dockerClient = new DockerClient(DOCKER_URL);
  private Process apiProcess = null;
  private ContainerCreateResponse influxDBContainer;
  private ContainerCreateResponse mysqlContainer;
  private ContainerCreateResponse kafkaContainer;

  @BeforeClass
  public void setup() throws DockerException, IOException {

    runKafka();

    runInfluxDB();

    runMYSQL();

    runAPI();

  }

  private void runAPI() throws IOException {

    apiProcess = Runtime.getRuntime().exec(new String[]{"java", "-cp", API_JAR,
        "com.hpcloud.mon.MonApiApplication", "server", "src/test/resources/mon-api-config.yml"});

    waitForPortReady("localhost", 8080);
  }

  private void waitForPortReady(String host, int port) {

    Socket s = null;
    boolean isPortReady = false;
    int tryCount = 0;
    while (!isPortReady) {

      if (tryCount >= MAX_CONNECT_PORT_TRIES) {
        System.err.println("Failed to connect to host [" + host + "] on port [" + port + "] in " +
            "[" + tryCount + "] tries");
        tearDown();
        System.exit(-1);
      }

      try {
        s = new Socket();
        s.setReuseAddress(true);
        SocketAddress sa = new InetSocketAddress(host, port);
        s.connect(sa, 50000);
        isPortReady = true;
        System.out.println("Took " + tryCount + " tries to connect to host [" + host + "] on port" +
            "[" + port + "]");
      } catch (Exception e) {
        tryCount++;
      }
    }

    if (s != null) {
      try {
        s.close();
      } catch (Exception e) {
        System.err.print(e);
      }
    }
  }

  private void runKafka() {

    ClientResponse response = dockerClient.pullImageCmd(DDIETERLY_KAFKA_V1).exec();

    ExposedPort tcp2181 = ExposedPort.tcp(2181);
    ExposedPort tcp9092 = ExposedPort.tcp(9092);

    kafkaContainer = dockerClient.createContainerCmd(DDIETERLY_KAFKA_V1).withCmd(new
        String[]{DDIETERLY_KAFKA_V1_RUN_CMD, DOCKER_IP}).withExposedPorts(tcp2181, tcp9092).exec();

    Ports portBindings2 = new Ports();
    portBindings2.bind(tcp2181, Ports.Binding(2181));
    portBindings2.bind(tcp9092, Ports.Binding(9092));

    dockerClient.startContainerCmd(kafkaContainer.getId()).withPortBindings(portBindings2).exec();

    waitForPortReady(DOCKER_IP, 2181);
    waitForPortReady(DOCKER_IP, 9092);
  }

  private void runMYSQL() {

    ClientResponse response = dockerClient.pullImageCmd(DDIETERLY_MYSQL_V1).exec();

    ExposedPort tcp3306 = ExposedPort.tcp(3306);

    mysqlContainer = dockerClient.createContainerCmd(DDIETERLY_MYSQL_V1).withCmd(new
        String[]{DDIETERLY_MYSQL_V1_RUN_CMD}).withExposedPorts(tcp3306).exec();

    Ports portBindings1 = new Ports();
    portBindings1.bind(tcp3306, Ports.Binding(3306));

    dockerClient.startContainerCmd(mysqlContainer.getId()).withPortBindings(portBindings1).exec();

    waitForPortReady(DOCKER_IP, 3306);
  }

  private void runInfluxDB() {

    ClientResponse response = dockerClient.pullImageCmd(DDIETERLY_INFLUXDB_V1).exec();

    ExposedPort tcp8083 = ExposedPort.tcp(8083);
    ExposedPort tcp8086 = ExposedPort.tcp(8086);
    ExposedPort tcp8090 = ExposedPort.tcp(8090);
    ExposedPort tcp8099 = ExposedPort.tcp(8099);

    influxDBContainer = dockerClient.createContainerCmd(DDIETERLY_INFLUXDB_V1).withExposedPorts
        (tcp8083, tcp8086, tcp8090, tcp8099).exec();

    Ports portBindings = new Ports();
    portBindings.bind(tcp8083, Ports.Binding(8083));
    portBindings.bind(tcp8086, Ports.Binding(8086));
    portBindings.bind(tcp8090, Ports.Binding(8090));
    portBindings.bind(tcp8099, Ports.Binding(8099));

    dockerClient.startContainerCmd(influxDBContainer.getId()).withPortBindings(portBindings).exec();

    waitForPortReady(DOCKER_IP, 8086);
  }

  @Test
  public void alarmCreateTest() {

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").body("{\"alarm_actions\": " +
        "[\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "" + "\"ok_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"name\": \"test-alarm-1\", \"description\": \"test-alarm-description\", " +
        "\"undetermined_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"expression\": \"max(cpu_system_perc) > 0 and max(load_avg_1_min{hostname=mini-mon}) > " +
        "0\", \"severity\": \"low\"}").post("/v2.0/alarms").then().assertThat().statusCode(201);

  }

  @Test
  public void alarmDeleteTest() {

    String json = given().headers("Accept", "application/json", "Content-Type",
        "application/json", "X-Auth-Token", "82510970543135").body("{\"alarm_actions\": " +
        "[\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "" + "\"ok_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"name\": \"test-alarm-2\", \"description\": \"test-alarm-description\", " +
        "\"undetermined_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"expression\": \"max(cpu_system_perc) > 0 and max(load_avg_1_min{hostname=mini-mon}) > " +
        "0\", \"severity\": \"low\"}").post("/v2.0/alarms").asString();

    String alarmId = from(json).get("id");

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").delete("/v2.0/alarms/" + alarmId).then().assertThat()
        .statusCode(204);

  }

  @Test
  public void alarmHistoryTest() {

    String json = given().headers("Accept", "application/json", "Content-Type",
        "application/json", "X-Auth-Token", "82510970543135").body("{\"alarm_actions\": " +
        "[\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "" + "\"ok_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"name\": \"test-alarm-3\", \"description\": \"test-alarm-description\", " +
        "\"undetermined_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"expression\": \"max(cpu_system_perc) > 0 and max(load_avg_1_min{hostname=mini-mon}) > " +
        "0\", \"severity\": \"low\"}").post("/v2.0/alarms").asString();

    String alarmId = from(json).get("id");

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").get("v2.0/alarms/" + alarmId + "/state-history").then()
        .assertThat().statusCode(200);

  }

  @Test
  public void alarmListTest() {

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").get("/v2.0/alarms").then().assertThat().statusCode(200);

  }

  @Test
  public void alarmPatchTest() {

    String json = given().headers("Accept", "application/json", "Content-Type",
        "application/json", "X-Auth-Token", "82510970543135").body("{\"alarm_actions\": " +
        "[\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "" + "\"ok_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"name\": \"test-alarm-4\", \"description\": \"test-alarm-description\", " +
        "\"undetermined_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"expression\": \"max(cpu_system_perc) > 0 and max(load_avg_1_min{hostname=mini-mon}) > " +
        "0\", \"severity\": \"low\"}").post("/v2.0/alarms").asString();

    String alarmId = from(json).get("id");

    given().headers("Accept", "application/json", "Content-Type", "application/json-patch+json",
        "X-Auth-Token", "82510970543135").body("{}").patch("v2.0/alarms/" + alarmId).then()
        .assertThat().statusCode(200);

  }

  @Test
  public void alarmShowTest() {

    String json = given().headers("Accept", "application/json", "Content-Type",
        "application/json", "X-Auth-Token", "82510970543135").body("{\"alarm_actions\": " +
        "[\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "" + "\"ok_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"name\": \"test-alarm-5\", \"description\": \"test-alarm-description\", " +
        "\"undetermined_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"expression\": \"max(cpu_system_perc) > 0 and max(load_avg_1_min{hostname=mini-mon}) > " +
        "0\", \"severity\": \"low\"}").post("/v2.0/alarms").asString();

    String alarmId = from(json).get("id");

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").get("v2.0/alarms/" + alarmId).then().assertThat()
        .statusCode(200);

  }

  @Test
  public void alarmUpdateTest() {

    String json = given().headers("Accept", "application/json", "Content-Type",
        "application/json", "X-Auth-Token", "82510970543135").body("{\"alarm_actions\": " +
        "[\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "" + "\"ok_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"name\": \"test-alarm-6\", \"description\": \"test-alarm-description\", " +
        "\"undetermined_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"expression\": \"max(cpu_system_perc) > 0 and max(load_avg_1_min{hostname=mini-mon}) > " +
        "0\", \"severity\": \"low\"}").post("/v2.0/alarms").asString();

    String alarmId = from(json).get("id");

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").body("{\"alarm_actions\": " +
        "[\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "" + "\"ok_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"name\": \"test-alarm-6\", \"description\": \"test-alarm-description\", " +
        "\"undetermined_actions\": [\"044fa9be-36ef-4e51-a1d9-67ec31734908\"], " +
        "\"expression\": \"max(cpu_system_perc) > 0 and max(load_avg_1_min{hostname=mini-mon}) > " +
        "0\", \"severity\": \"low\", \"actions_enabled\":\"true\", " +
        "\"state\": \"alarm\"}").put("/v2" +
        ".0/alarms/" + alarmId).then().assertThat().statusCode(200);

  }

  @Test
  public void measurementListTest() {

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").param("start_time", "1970-01-01T00:00:00Z").param
        ("name", "cpu_system_perc").get("v2.0/metrics/measurements").then().assertThat()
        .statusCode(200);

  }

  @Test
  public void metricCreateTest() {

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").body("{\"timestamp\": 0, \"name\": \"test-metric-1\", " +
        "\"value\": 1234.5678, \"dimensions\": {\"foo\": \"bar\", " +
        "\"biz\": \"baz\"}}").post("/v2.0/metrics ").then().assertThat().statusCode(204);

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").param("start_time", "1970-01-01T00:00:00Z").param
        ("name", "test-metric-1").get("v2.0/metrics/measurements").then().assertThat().statusCode
        (200);


  }

  @Test
  public void metricCreateRawTest() {

    long unixTime = System.currentTimeMillis() / 1000L;

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").body("{\"timestamp\":\"" + unixTime + "\" , " +
        "\"name\": \"test-metric-2\", " +
        "\"value\": 1234.5678, \"dimensions\": {\"foo\": \"bar\", " +
        "\"biz\": \"baz\"}}").post("/v2.0/metrics ").then().assertThat().statusCode(204);

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").param("start_time", "1970-01-01T00:00:00Z").param
        ("name", "test-metric-2").get("v2.0/metrics/measurements").then().assertThat().statusCode
        (200);

  }

  @Test
  public void metricList() {

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").get("/v2.0/metrics").then().assertThat().statusCode(200);

  }

  @Test
  public void metricStatisticsTest() {

    String[] stats = new String[]{"avg", "min", "max", "count", "sum"};

    for (String stat : stats) {
      given().headers("Accept", "application/json", "Content-Type", "application/json",
          "X-Auth-Token", "82510970543135").param("start_time", "1970-01-01T00:00:00Z").param
          ("statistics", stat).param("name", "cpu_system_perc").get("/v2.0/metrics/statistics")
          .then().assertThat().statusCode(200);
    }

  }

  @Test
  public void notificationCreateTest() {

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").body("{\"type\": \"email\", " +
        "" + "\"name\": \"test-notification-1\", \"address\": \"jdoe@gmail.com\"}").post("/v2" +
        ".0/notification-methods").then().assertThat().statusCode(201);
  }

  @Test
  public void notificationDeleteTest() {

    String json = given().headers("Accept", "application/json", "Content-Type",
        "application/json", "X-Auth-Token", "82510970543135").body("{\"type\": \"email\", " +
        "" + "\"name\": \"test-notification-2\", \"address\": \"jdoe@gmail.com\"}").post("/v2" +
        ".0/notification-methods").asString();

    String notificationId = from(json).get("id");

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").delete("/v2.0/notification-methods/" + notificationId)
        .then().assertThat().statusCode(204);


  }

  @Test
  public void notificationList() {

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").get("/v2.0/notification-methods").then().assertThat()
        .statusCode(200);

  }

  @Test
  public void notificationShowTest() {

    String json = given().headers("Accept", "application/json", "Content-Type",
        "application/json", "X-Auth-Token", "82510970543135").body("{\"type\": \"email\", " +
        "" + "\"name\": \"test-notification-3\", \"address\": \"jdoe@gmail.com\"}").post("/v2" +
        ".0/notification-methods").asString();

    String notificationId = from(json).get("id");

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").get("/v2.0/notification-methods/" + notificationId)
        .then().assertThat().statusCode(200);

  }

  @Test
  public void notificationUpdateTest() {

    String json = given().headers("Accept", "application/json", "Content-Type",
        "application/json", "X-Auth-Token", "82510970543135").body("{\"type\": \"email\", " +
        "" + "\"name\": \"test-notification-4\", \"address\": \"jdoe@gmail.com\"}").post("/v2" +
        ".0/notification-methods").asString();

    String notificationId = from(json).get("id");

    given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").body("{\"type\": \"email\", " +
        "" + "\"name\": \"test-notification-4\", \"address\": \"jsmith@gmail.com\"}").put("/v2" +
        ".0/notification-methods/" + notificationId).then().assertThat().statusCode(200);

    json = given().headers("Accept", "application/json", "Content-Type", "application/json",
        "X-Auth-Token", "82510970543135").get("/v2.0/notification-methods/" + notificationId)
        .asString();

    String address = from(json).get("address");

    assert (address.equals("jsmith@gmail.com"));


  }

  @AfterClass
  public void tearDown() {

    stopAPI();

    stopMYSQL();

    stopInfluxDB();

    stopKafka();


  }

  private void stopAPI() {
    apiProcess.destroy();
  }

  private void stopKafka() {
    dockerClient.stopContainerCmd(kafkaContainer.getId()).withTimeout(2).exec();

  }

  private void stopMYSQL() {
    dockerClient.stopContainerCmd(mysqlContainer.getId()).withTimeout(2).exec();


  }

  private void stopInfluxDB() {
    dockerClient.stopContainerCmd(influxDBContainer.getId()).withTimeout(2).exec();

  }
}
