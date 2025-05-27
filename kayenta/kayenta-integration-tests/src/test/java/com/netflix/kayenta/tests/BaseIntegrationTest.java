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
package com.netflix.kayenta.tests;

import com.netflix.kayenta.Main;
import com.netflix.kayenta.configuration.EmbeddedPrometheusBootstrapConfiguration;
import com.netflix.kayenta.configuration.MetricsReportingConfiguration;
import com.netflix.kayenta.prometheus.config.PrometheusManagedAccount;
import com.netflix.kayenta.prometheus.config.PrometheusResponseConverter;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.io.IOException;
import java.util.Set;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@AutoConfigureMetrics
@SpringBootTest(
    classes = {MetricsReportingConfiguration.class, Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    value = "spring.application.name=kayenta")
@ExtendWith(SpringExtension.class)
@ActiveProfiles({"base", "prometheus", "graphite", "cases"})
@Import(EmbeddedPrometheusBootstrapConfiguration.class)
@TestInstance(
    TestInstance.Lifecycle
        .PER_CLASS) // This way Spring will create only one instance of the test class and will
// allow non-static @BeforeAll.
/**
 * Base test class for integration tests that require a Prometheus server.
 *
 * <p>This abstract class is intended to be extended by other test classes that depend on
 * Prometheus. It provides lifecycle management for a locally embedded Prometheus server, including
 * setup and cleanup, and exposes utility methods to retrieve dynamically assigned ports for the
 * application, management endpoints, and Prometheus itself.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Starts an embedded Prometheus server before any tests in the class run (@BeforeAll).
 *   <li>Wires {@link PrometheusManagedAccount} instances to communicate with the embedded
 *       Prometheus.
 *   <li>Stops the Prometheus container after all tests in the class have run (@AfterAll).
 *   <li>Provides helper methods to access dynamically assigned ports.
 * </ul>
 *
 * <p>Dependencies are injected via Spring and expected to be available in the test context.
 */
public abstract class BaseIntegrationTest {
  @Autowired protected Environment environment;

  private Integer managementPort;

  private Integer serverPort;

  @Autowired private AccountCredentialsRepository accountCredentialsRepository;

  @Autowired PrometheusResponseConverter prometheusConverter;
  @Autowired RetrofitClientFactory retrofitClientFactory;
  @Autowired OkHttpClient okHttpClient;

  @Autowired EmbeddedPrometheusBootstrapConfiguration prometheusConfig;

  @Autowired ConfigurableEnvironment env;

  private int prometheusPort;

  /**
   * Sets up Prometheus-managed accounts before running any tests.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Starts the Prometheus server and gets its dynamic port.
   *   <li>Retrieves all {@link PrometheusManagedAccount} instances from the account repository.
   *   <li>Updates each account's endpoint to point to the running local Prometheus instance.
   *   <li>Creates and assigns a {@link PrometheusRemoteService} client for each account using
   *       Retrofit.
   * </ul>
   *
   * <p>This setup ensures that all Prometheus-managed accounts communicate with the locally started
   * Prometheus instance.
   */
  @BeforeAll
  public void setUp() {
    prometheusPort = prometheusConfig.startPrometheusServer(env);

    Set<AccountCredentials> accountCredentialsSet =
        accountCredentialsRepository.getAllOf(AccountCredentials.Type.METRICS_STORE);
    String dynamicEndpoint = "http://localhost:" + prometheusPort;

    accountCredentialsSet.stream()
        .filter(credentials -> credentials instanceof PrometheusManagedAccount)
        .map(PrometheusManagedAccount.class::cast)
        .forEach(
            prometheusManagedAccount -> {
              prometheusManagedAccount.getEndpoint().setBaseUrl(dynamicEndpoint);
              PrometheusRemoteService prometheusRemoteService;
              try {
                prometheusRemoteService =
                    retrofitClientFactory.createClient(
                        PrometheusRemoteService.class,
                        prometheusConverter,
                        prometheusManagedAccount.getEndpoint(),
                        okHttpClient,
                        prometheusManagedAccount.getUsername(),
                        prometheusManagedAccount.getPassword(),
                        prometheusManagedAccount.getUsernamePasswordFile(),
                        prometheusManagedAccount.getBearerToken());
              } catch (IOException e) {
                throw new RuntimeException(e);
              }

              prometheusManagedAccount.setPrometheusRemoteService(prometheusRemoteService);
            });
  }

  protected int getManagementPort() {
    if (managementPort != null) {
      return managementPort;
    }

    managementPort = environment.getProperty("local.management.port", Integer.class);

    return managementPort;
  }

  protected int getServerPort() {
    if (serverPort != null) {
      return serverPort;
    }

    serverPort = environment.getProperty("local.server.port", Integer.class);

    return serverPort;
  }

  protected int getPrometheusPort() {
    return prometheusPort;
  }

  @AfterAll
  public void cleanUp() {
    prometheusConfig.stopPrometheusContainer();
  }
}
