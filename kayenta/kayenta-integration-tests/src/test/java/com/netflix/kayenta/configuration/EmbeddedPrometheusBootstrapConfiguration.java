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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.MountableFile;

@Slf4j
@Configuration
public class EmbeddedPrometheusBootstrapConfiguration {

  // Exposes host machine port to be used by prometheus container for scraping metrics from
  // /prometehus endpoint
  // See for more details:
  // https://www.testcontainers.org/features/networking/#exposing-host-ports-to-the-container
  static {
    Testcontainers.exposeHostPorts(8081);
  }

  private static final int PORT = 9090;

  @Bean(name = "prometheusWaitStrategy")
  public WaitStrategy prometheusWaitStrategy() {
    return new HttpWaitStrategy().forPath("/status").forPort(PORT).forStatusCode(200);
  }

  @Bean(name = "prometheus", destroyMethod = "stop")
  public GenericContainer prometheus(
      ConfigurableEnvironment environment, WaitStrategy prometheusWaitStrategy) {

    GenericContainer container =
        new GenericContainer("prom/prometheus:v2.10.0")
            .withLogConsumer(containerLogsConsumer(log))
            .withExposedPorts(PORT)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("/external/prometheus/prometheus.yml"),
                "/etc/prometheus/prometheus.yml")
            .waitingFor(prometheusWaitStrategy)
            .withStartupTimeout(Duration.ofSeconds(30));
    container.start();
    Map<String, Object> env = registerEnvironment(environment, container.getMappedPort(PORT));
    log.info("Started Prometheus server. Connection details: {}", env);
    return container;
  }

  static Map<String, Object> registerEnvironment(ConfigurableEnvironment environment, int port) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("embedded.prometheus.port", port);
    EnvironmentUtils.registerPropertySource("embeddedPrometheusInfo", environment, map);
    return map;
  }
}
