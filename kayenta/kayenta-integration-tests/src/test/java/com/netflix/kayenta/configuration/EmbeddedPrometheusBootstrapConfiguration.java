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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.MountableFile;

@Slf4j
@TestConfiguration
@RequiredArgsConstructor
public class EmbeddedPrometheusBootstrapConfiguration {

  private static final int PROMETHEUS_INTERNAL_PORT = 9090;
  private final Environment environment;

  private WaitStrategy prometheusWaitStrategy() {
    return new HttpWaitStrategy()
        .forPath("/status")
        .forPort(PROMETHEUS_INTERNAL_PORT)
        .forStatusCode(200);
  }

  private GenericContainer<?> prometheusContainer;

  /**
   * Builds and configures a Prometheus container using Testcontainers.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Reads the application's management port from the environment.
   *   <li>Generates a Prometheus config file to scrape metrics from that port.
   *   <li>Creates a {@link GenericContainer} for Prometheus with the config file and required
   *       settings.
   * </ul>
   *
   * @param env the Spring environment used to access configuration properties
   * @return a configured Prometheus container (not yet started)
   */
  private GenericContainer prometheus(ConfigurableEnvironment env) {
    int managementPort = Integer.parseInt(environment.getProperty("local.management.port"));
    exposeManagementPort(managementPort);

    File prometheusConfigFile = createPrometheusConfigFile(managementPort);

    prometheusContainer =
        new GenericContainer<>("prom/prometheus:v2.10.0")
            .withLogConsumer(containerLogsConsumer(log))
            .withExposedPorts(PROMETHEUS_INTERNAL_PORT)
            .withCopyFileToContainer(
                MountableFile.forHostPath(prometheusConfigFile.getAbsolutePath()),
                "/etc/prometheus/prometheus.yml")
            .waitingFor(prometheusWaitStrategy())
            .withStartupTimeout(Duration.ofSeconds(30));

    return prometheusContainer;
  }

  public int startPrometheusServer(ConfigurableEnvironment env) {
    if (prometheusContainer == null) {
      prometheusContainer = prometheus(env);
    }
    prometheusContainer.start();
    return prometheusContainer.getMappedPort(PROMETHEUS_INTERNAL_PORT);
  }

  private void exposeManagementPort(int managementPort) {
    log.info("Exposing management port {} to Testcontainers", managementPort);
    Testcontainers.exposeHostPorts(managementPort);
  }

  private File createPrometheusConfigFile(int managementPort) {
    try {
      File tempFile = File.createTempFile("prometheus", ".yml");
      try (FileWriter writer = new FileWriter(tempFile)) {
        writer.write(
            String.format(
                """
                                global:
                                  scrape_interval: 1s

                                scrape_configs:
                                  - job_name: kayenta-test-metrics
                                    metrics_path: /prometheus
                                    static_configs:
                                      - targets: ['host.testcontainers.internal:%d']
                                """,
                managementPort));
      }
      return tempFile;
    } catch (IOException e) {
      throw new RuntimeException("Failed to create prometheus.yml dynamically", e);
    }
  }

  public void stopPrometheusContainer() {
    if (prometheusContainer != null) {
      log.info("Stopping Prometheus container...");
      prometheusContainer.stop();
      log.info("Prometheus container stopped.");
    }
  }
}
