/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.clouddriver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
public class MySqlContainerTest extends BaseContainerTest {

  private static final Logger logger = LoggerFactory.getLogger(MySqlContainerTest.class);

  private static final String MYSQL_NETWORK_ALIAS = "mysqlHost";

  private static final int MYSQL_PORT = 3306;

  private MySQLContainer<?> mysql;

  private String jdbcUrl = "";

  @BeforeEach
  void setup() throws Exception {
    mysql =
        new MySQLContainer<>("mysql:8.0.37")
            .withDatabaseName("clouddriver")
            .withUsername("root")
            .withPassword("root")
            .withNetwork(network)
            .withNetworkAliases(MYSQL_NETWORK_ALIAS)
            .withInitScript("mysql_init.sql");
    mysql.start();
    jdbcUrl = String.format("jdbc:mysql://%s:%d/clouddriver", MYSQL_NETWORK_ALIAS, MYSQL_PORT);
    clouddriverContainer
        .dependsOn(mysql)
        .withEnv("SPRING_APPLICATION_JSON", getSpringApplicationJson());
    // .start();

    // Only valid once the container has started
    // Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger);
    // clouddriverContainer.followOutput(logConsumer);
  }

  private String getSpringApplicationJson() throws JsonProcessingException {
    logger.info("--------- jdbcUrl: '{}'", jdbcUrl);
    Map<String, String> connectionPool =
        Map.of("jdbcUrl", jdbcUrl, "user", "clouddriver_service", "password", "c10uddriver");
    Map<String, String> migration =
        Map.of("jdbcUrl", jdbcUrl, "user", "clouddriver_migrate", "password", "c10uddriver");

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
            migration);
    Map<String, Object> allProperties = new HashMap<>();
    String artifactTypes = System.getProperty("artifactTypes", "");
    if (artifactTypes.contains("bitbucket")) {
      allProperties.put("artifacts.bitbucket.enabled", "true");
    }
    if (artifactTypes.contains("docker") || artifactTypes.contains("kubernetes")) {
      allProperties.put("kubernetes.enabled", "true");
    }
    if (artifactTypes.contains("gcs")) {
      allProperties.put("artifacts.gcs.enabled", "true");
    }
    if (artifactTypes.contains("github")) {
      allProperties.put("artifacts.github.enabled", "true");
    }
    if (artifactTypes.contains("gitlab")) {
      allProperties.put("artifacts.gitlab.enabled", "true");
    }
    if (artifactTypes.contains("git-repo")) {
      allProperties.put("artifacts.git-repo.enabled", "true");
    }
    if (artifactTypes.contains("helm")) {
      allProperties.put("artifacts.helm.enabled", "true");
    }
    if (artifactTypes.contains("http")) {
      allProperties.put("artifacts.http.enabled", "true");
    }
    if (artifactTypes.contains("ivy")) {
      allProperties.put("artifacts.ivy.enabled", "true");
    }
    if (artifactTypes.contains("jenkins")) {
      allProperties.put("jenkins.enabled", "true");
    }
    if (artifactTypes.contains("maven")) {
      allProperties.put("artifacts.maven.enabled", "true");
    }
    if (artifactTypes.contains("oracle")) {
      allProperties.put("artifacts.oracle.enabled", "true");
    }
    if (artifactTypes.contains("s3")) {
      allProperties.put("artifacts.s3.enabled", "true");
    }
    allProperties.putAll(properties);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(allProperties);
  }

  @AfterAll
  void cleanupOnce() {
    if (clouddriverContainer != null) {
      clouddriverContainer.stop();
    }

    if (mysql != null) {
      mysql.stop();
    }
  }

  // FIXME: expect the container to start
  //
  // @Test
  // void testHealthCheckWithMySql() throws Exception {
  //   super.testHealthCheck();
  // }

  @Test
  void testFailToStart() {
    assertThatThrownBy(() -> clouddriverContainer.start())
        .isInstanceOf(ContainerLaunchException.class)
        .hasMessage("Container startup failed for image " + dockerImageName.toString());
    assertThat(clouddriverContainer.getLogs())
        .contains(
            """
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.netflix.spinnaker.config.okhttp3.InsecureOkHttpClientBuilderProvider required a single bean, but 8 were found:
\t- bitbucketOkHttpClient: defined by method 'bitbucketOkHttpClient' in class path resource [com/netflix/spinnaker/clouddriver/artifacts/bitbucket/BitbucketArtifactConfiguration.class]
\t- helmOciOkHttpClient: defined by method 'helmOciOkHttpClient' in class path resource [com/netflix/spinnaker/clouddriver/artifacts/docker/HelmOciArtifactConfiguration.class]
\t- gitHubOkHttpClient: defined by method 'gitHubOkHttpClient' in class path resource [com/netflix/spinnaker/clouddriver/artifacts/github/GitHubArtifactConfiguration.class]
\t- gitlabOkHttpClient: defined by method 'gitlabOkHttpClient' in class path resource [com/netflix/spinnaker/clouddriver/artifacts/gitlab/GitlabArtifactConfiguration.class]
\t- helmOkHttpClient: defined by method 'helmOkHttpClient' in class path resource [com/netflix/spinnaker/clouddriver/artifacts/helm/HelmArtifactConfiguration.class]
\t- httpOkHttpClient: defined by method 'httpOkHttpClient' in class path resource [com/netflix/spinnaker/clouddriver/artifacts/http/HttpArtifactConfiguration.class]
\t- jenkinsOkHttpClient: defined by method 'jenkinsOkHttpClient' in class path resource [com/netflix/spinnaker/clouddriver/artifacts/jenkins/JenkinsArtifactConfiguration.class]
\t- mavenOkHttpClient: defined by method 'mavenOkHttpClient' in class path resource [com/netflix/spinnaker/clouddriver/artifacts/maven/MavenArtifactConfiguration.class]""");
  }
}
