/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.front50;

import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CompositeStorageContainerTest {

  private static final String MYSQL_NETWORK_ALIAS = "mysqlPrimary";
  private static final String MYSQL_NETWORK_ALIAS_SECONDARY = "mysqlSecondary";

  private static final int MYSQL_PORT = 3306;

  private static final Logger logger = LoggerFactory.getLogger(CompositeStorageContainerTest.class);

  private static final Network network = Network.newNetwork();

  // withNetwork/withNetworkAliases return a GenericContainer, so call them elsewhere.
  private static final MySQLContainer mysql =
      new MySQLContainer(DockerImageName.parse("mysql:8.0.37")).withDatabaseName("front50");

  private static final MySQLContainer mysqlSecondary =
      new MySQLContainer(DockerImageName.parse("mysql:8.0.37")).withDatabaseName("front50migrated");

  private static GenericContainer front50Container;

  @BeforeAll
  static void setupOnce() throws Exception {
    String fullDockerImageName = System.getenv("FULL_DOCKER_IMAGE_NAME");

    // Skip the tests if there's no docker image.  This allows gradlew build to work.
    assumeTrue(fullDockerImageName != null);

    mysql.withNetworkAliases(MYSQL_NETWORK_ALIAS).withNetwork(network);
    mysql.start();

    mysqlSecondary.withNetworkAliases(MYSQL_NETWORK_ALIAS_SECONDARY).withNetwork(network);
    mysqlSecondary.start();

    DockerImageName dockerImageName = DockerImageName.parse(fullDockerImageName);

    front50Container =
        new GenericContainer(dockerImageName)
            .withNetwork(network)
            .withExposedPorts(8080)
            .dependsOn(mysql)
            .dependsOn(mysqlSecondary)
            .waitingFor(Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(90)))
            .withEnv("SPRING_APPLICATION_JSON", getSpringApplicationJson());

    Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger);
    front50Container.start();
    front50Container.followOutput(logConsumer);
  }

  private static String getSpringApplicationJson() throws JsonProcessingException {
    String jdbcUrl =
        "jdbc:mysql://"
            + MYSQL_NETWORK_ALIAS
            + ":"
            + MYSQL_PORT
            + "/"
            + mysql.getDatabaseName()
            + "?useSSL=false";
    logger.info("jdbcUrl: '{}'", jdbcUrl);
    String jdbcUrlSecondary =
        "jdbc:mysql://"
            + MYSQL_NETWORK_ALIAS_SECONDARY
            + ":"
            + MYSQL_PORT
            + "/"
            + mysqlSecondary.getDatabaseName()
            + "?useSSL=false";
    logger.info("jdbcUrlSecondary: '{}'", jdbcUrlSecondary);
    Map<String, String> properties =
        Map.ofEntries(
            Map.entry("spinnaker.s3.enabled", "false"),
            Map.entry("spinnaker.migration.enabled", "true"),
            Map.entry("spinnaker.migration.primaryName", "secondarySqlStorageService"),
            Map.entry("spinnaker.migration.previousName", "sqlStorageService"),
            Map.entry("spinnaker.migration.writeOnly", "false"),
            Map.entry("spinnaker.migration.deleteOrphans", "false"),
            Map.entry("spinnaker.migration.compositeStorageService.enabled", "true"),
            Map.entry("spinnaker.migration.compositeStorageService.reads.primary", "true"),
            Map.entry("spinnaker.migration.compositeStorageService.reads.previous", "false"),
            Map.entry("sql.enabled", "true"),
            Map.entry("sql.secondary.enabled", "true"),
            Map.entry("sql.secondary.poolName", "secondary"),
            Map.entry("sql.connectionPools.default.default", "true"),
            Map.entry("sql.connectionPools.default.jdbcUrl", jdbcUrl),
            Map.entry("sql.connectionPools.default.user", mysql.getUsername()),
            Map.entry("sql.connectionPools.default.password", mysql.getPassword()),
            Map.entry("sql.connectionPools.secondary.enabled", "true"),
            Map.entry("sql.connectionPools.secondary.jdbcUrl", jdbcUrlSecondary),
            Map.entry("sql.connectionPools.secondary.user", mysqlSecondary.getUsername()),
            Map.entry("sql.connectionPools.secondary.password", mysqlSecondary.getPassword()),
            Map.entry("sql.migration.jdbcUrl", jdbcUrl),
            Map.entry("sql.migration.user", mysql.getUsername()),
            Map.entry("sql.migration.password", mysql.getPassword()),
            Map.entry("sql.secondaryMigration.jdbcUrl", jdbcUrlSecondary),
            Map.entry("sql.secondaryMigration.user", mysqlSecondary.getUsername()),
            Map.entry("sql.secondaryMigration.password", mysqlSecondary.getPassword()),
            Map.entry("services.fiat.baseUrl", "http://nowhere"));
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(properties);
  }

  @AfterAll
  static void cleanupOnce() {
    if (front50Container != null) {
      front50Container.stop();
    }

    if (mysql != null) {
      mysql.stop();
    }

    if (mysqlSecondary != null) {
      mysqlSecondary.stop();
    }
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void verifySecondaryPoolLiquibaseMigration() throws Exception {
    String query = "SELECT ID FROM DATABASECHANGELOG;";
    Connection connectionSecondary =
        DriverManager.getConnection(
            mysqlSecondary.getJdbcUrl(),
            mysqlSecondary.getUsername(),
            mysqlSecondary.getPassword());
    ResultSet outputSecondary = connectionSecondary.prepareStatement(query).executeQuery();
    Connection connectionPrimary =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    ResultSet outputPrimary = connectionPrimary.prepareStatement(query).executeQuery();

    List<Map<String, Object>> expected = convertResultSetToList(outputPrimary);
    List<Map<String, Object>> actual = convertResultSetToList(outputSecondary);
    assertEquals(expected, actual);

    connectionPrimary.close();
    connectionSecondary.close();
  }

  @Test
  void verifyFront50CompositeMigrationSqlToSql() throws Exception {
    // Verify empty Applications prior to migration from previousClass
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "http://"
                        + front50Container.getHost()
                        + ":"
                        + front50Container.getFirstMappedPort()
                        + "/v2/applications"))
            .GET()
            .build();

    HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response).isNotNull();
    logger.info("response: {}, {}", response.statusCode(), response.body());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("[]");

    // Add data to previous class. Normally this exists but for the sake of the test the data is
    // added here
    Connection connectionPreviousClass =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    executeSqlScript(
        connectionPreviousClass,
        new File(getClass().getClassLoader().getResource("front50-test-applications.sql").toURI()));
    executeSqlScript(
        connectionPreviousClass,
        new File(
            getClass()
                .getClassLoader()
                .getResource("front50-test-applications-history.sql")
                .toURI()));

    sleep(45000);

    // Verify that the data is migrated to the primaryClass db and returned by the Front50 API
    String query = "SELECT ID FROM applications ORDER BY ID;";
    ResultSet outputPrimary = connectionPreviousClass.prepareStatement(query).executeQuery();
    List<Map<String, Object>> expected = convertResultSetToList(outputPrimary);

    Connection connectionPrimaryClass =
        DriverManager.getConnection(
            mysqlSecondary.getJdbcUrl(),
            mysqlSecondary.getUsername(),
            mysqlSecondary.getPassword());

    await()
        .atMost(120, SECONDS)
        .pollInterval(5, SECONDS)
        .untilAsserted(
            () -> {
              ResultSet outputSecondary =
                  connectionPrimaryClass.prepareStatement(query).executeQuery();
              List<Map<String, Object>> actual = convertResultSetToList(outputSecondary);
              assertEquals(expected, actual);
              assertThat(actual.size()).isEqualTo(2);
              assertThat(expected.size()).isEqualTo(2);
            });

    request =
        HttpRequest.newBuilder()
            .uri(
                new URI(
                    "http://"
                        + front50Container.getHost()
                        + ":"
                        + front50Container.getFirstMappedPort()
                        + "/v2/applications"))
            .GET()
            .build();

    response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response).isNotNull();
    logger.info("response: {}, {}", response.statusCode(), response.body());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isNotNull();
    assertThat(response.body()).containsIgnoringCase("app1");
    assertThat(response.body()).containsIgnoringCase("dummy-application2");

    connectionPrimaryClass.close();
    connectionPreviousClass.close();
  }

  private static List<Map<String, Object>> convertResultSetToList(ResultSet rs)
      throws SQLException {
    List<Map<String, Object>> results = new ArrayList<>();
    ResultSetMetaData rsmd = rs.getMetaData();
    int columnCount = rsmd.getColumnCount();

    while (rs.next()) {
      Map<String, Object> row = new HashMap<>();
      for (int i = 1; i <= columnCount; i++) {
        row.put(rsmd.getColumnName(i), rs.getObject(i));
      }
      results.add(row);
    }

    return results;
  }

  private void executeSqlScript(Connection connection, File sqlFile) throws IOException {
    try (FileReader reader = new FileReader(sqlFile);
        Statement statement = connection.createStatement()) {

      // Execute the SQL from the file
      StringBuilder sqlScript = new StringBuilder();
      int ch;
      while ((ch = reader.read()) != -1) {
        sqlScript.append((char) ch);
      }

      // Execute the script in the database
      statement.execute(sqlScript.toString());
    } catch (Exception e) {
      throw new IOException("Error executing SQL script", e);
    }
  }
}
