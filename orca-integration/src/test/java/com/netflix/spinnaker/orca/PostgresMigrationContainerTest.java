/*
 * Copyright 2024 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
public class PostgresMigrationContainerTest extends BaseContainerTest {

  private static final Logger logger =
      LoggerFactory.getLogger(PostgresMigrationContainerTest.class);

  private static final String POSTGRES_NETWORK_ALIAS = "postgresHost";

  private static final int POSTGRES_PORT = 5432;

  private PostgreSQLContainer<?> postgres;

  private GenericContainer<?> orcaInitialContainer;

  // this is the latest image that is still running on liquibase 3.10.3 which create the conditions
  // similar to real scenario so that test identifies when validChecksums are not added in the later
  // version of orca where higher liquibase versions are used
  private static final DockerImageName previousDockerImageName =
      DockerImageName.parse(
          "us-docker.pkg.dev/spinnaker-community/docker/orca:8.36.3-dev-release-1.32.x-3f8965d03-202406101625-unvalidated");

  private String jdbcUrl = "";

  @BeforeEach
  void setup() throws Exception {
    postgres =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("orca")
            .withUsername("postgres")
            .withPassword("postgres")
            .withNetwork(network)
            .withNetworkAliases(POSTGRES_NETWORK_ALIAS)
            .withInitScript("postgres_init.sql");
    postgres.start();
    jdbcUrl = String.format("jdbc:postgresql://%s:%d/orca", POSTGRES_NETWORK_ALIAS, POSTGRES_PORT);

    // Start the first orca(from previous release) container so that all the db changelog
    // sets are executed
    orcaInitialContainer =
        new GenericContainer(previousDockerImageName)
            .withNetwork(network)
            .withExposedPorts(ORCA_PORT)
            .waitingFor(Wait.forHealthcheck().withStartupTimeout(Duration.ofSeconds(150)))
            .dependsOn(postgres)
            .withEnv("SPRING_APPLICATION_JSON", getSpringApplicationJson());
    orcaInitialContainer.start();
    Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger);
    orcaInitialContainer.followOutput(logConsumer);
    orcaInitialContainer.stop();

    orcaContainer
        .dependsOn(postgres)
        .withEnv("SPRING_APPLICATION_JSON", getSpringApplicationJson())
        .start();

    orcaContainer.followOutput(logConsumer);
  }

  private String getSpringApplicationJson() throws JsonProcessingException {
    logger.info("--------- jdbcUrl: '{}'", jdbcUrl);
    Map<String, String> connectionPool =
        Map.of(
            "dialect",
            "POSTGRES",
            "jdbcUrl",
            jdbcUrl,
            "user",
            "orca_service",
            "password",
            "0rcaPassw0rd");
    Map<String, String> migration =
        Map.of("jdbcUrl", jdbcUrl, "user", "orca_migrate", "password", "0rcaPassw0rd");
    Map<String, Boolean> sql = Map.of("enabled", true);
    Map<String, Boolean> redis = Map.of("enabled", false);
    Map<String, Object> pendingExecutionService = Map.of("sql", sql, "redis", redis);
    Map<String, Object> executionRepository = Map.of("sql", sql, "redis", redis);
    Map<String, Object> keiko = Map.of("sql", sql, "redis", redis);

    Map<String, Object> properties =
        Map.of(
            "sql.enabled",
            "true",
            "services.fiat.baseUrl",
            "http://nowhere",
            "sql.connectionPool",
            connectionPool,
            "redis.enabled",
            "false",
            "sql.migration",
            migration,
            "executionRepository",
            executionRepository,
            "keiko.queue",
            keiko,
            "queue.pendingExecutionService",
            pendingExecutionService,
            "monitor.activeExecutions.redis",
            "false");
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(properties);
  }

  @AfterAll
  void cleanupOnce() {
    if (orcaContainer != null) {
      orcaContainer.stop();
    }

    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void testHealthCheckWithPostgres() throws Exception {
    super.testHealthCheck();
  }
}
