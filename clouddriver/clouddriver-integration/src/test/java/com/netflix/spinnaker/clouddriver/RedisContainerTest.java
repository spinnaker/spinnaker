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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
public class RedisContainerTest extends BaseContainerTest {

  private static final Logger logger = LoggerFactory.getLogger(RedisContainerTest.class);

  private static final String REDIS_NETWORK_ALIAS = "redisHost";

  private static final int REDIS_PORT = 6379;

  private GenericContainer<?> redis;

  @BeforeEach
  void setup() throws Exception {
    redis =
        new GenericContainer<>(DockerImageName.parse("library/redis:5-alpine"))
            .withNetwork(network)
            .withNetworkAliases(REDIS_NETWORK_ALIAS)
            .withExposedPorts(REDIS_PORT);
    redis.start();
    clouddriverContainer
        .dependsOn(redis)
        .withEnv("SPRING_APPLICATION_JSON", getSpringApplicationJson())
        .start();

    Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(logger);
    clouddriverContainer.followOutput(logConsumer);
  }

  private String getSpringApplicationJson() throws JsonProcessingException {
    String redisUrl = "redis://" + REDIS_NETWORK_ALIAS + ":" + REDIS_PORT;
    logger.info("----------- redisUrl: '{}'", redisUrl);
    Map<String, String> properties =
        Map.of("redis.connection", redisUrl, "services.fiat.baseUrl", "http://nowhere");
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(properties);
  }

  @AfterAll
  void cleanupOnce() {
    if (clouddriverContainer != null) {
      clouddriverContainer.stop();
    }

    if (redis != null) {
      redis.stop();
    }
  }

  @Test
  void testHealthCheckWithRedis() throws Exception {
    super.testHealthCheck();
  }
}
