/*
 * Copyright 2019 Playtika
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.kayenta.configuration;

import static com.playtika.test.common.utils.ContainerUtils.containerLogsConsumer;

import com.netflix.kayenta.utils.EnvironmentUtils;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

@Slf4j
@Configuration
public class EmbeddedGraphiteBootstrapConfiguration {
  private static final int PICKLE_RECEIVER_PORT = 2004;
  private static final int HTTP_PORT = 80;

  @Bean(name = "graphiteWaitStrategy")
  public WaitStrategy graphiteWaitStrategy() {
    return new HostPortWaitStrategy();
  }

  @Bean(name = "graphite", destroyMethod = "stop")
  public GenericContainer graphite(
      ConfigurableEnvironment environment, WaitStrategy graphiteWaitStrategy) {

    GenericContainer container =
        new GenericContainer("graphiteapp/graphite-statsd:1.1.5-12")
            .withLogConsumer(containerLogsConsumer(log))
            .withExposedPorts(PICKLE_RECEIVER_PORT)
            .waitingFor(graphiteWaitStrategy)
            .withClasspathResourceMapping(
                "/external/graphite/storage-schemas.conf",
                "/opt/graphite/conf/storage-schemas.conf",
                BindMode.READ_ONLY)
            .withStartupTimeout(Duration.ofSeconds(30));
    container.start();

    Map<String, Object> map = registerEnvironment(environment, container);
    log.info("Started Graphite server. Connection details: {}", map);
    return container;
  }

  @NotNull
  private Map<String, Object> registerEnvironment(
      ConfigurableEnvironment environment, GenericContainer container) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("embedded.graphite.picklePort", container.getMappedPort(PICKLE_RECEIVER_PORT));
    map.put("embedded.graphite.httpPort", container.getMappedPort(HTTP_PORT));
    EnvironmentUtils.registerPropertySource("embeddedGraphiteInfo", environment, map);
    return map;
  }
}
