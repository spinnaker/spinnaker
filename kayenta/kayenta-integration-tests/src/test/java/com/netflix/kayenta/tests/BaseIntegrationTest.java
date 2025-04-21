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
import com.netflix.kayenta.configuration.MetricsReportingConfiguration;
import com.netflix.kayenta.configuration.TestPrometheusConfiguration;
import com.netflix.kayenta.prometheus.config.PrometheusConfigurationProperties;
import com.netflix.kayenta.prometheus.config.PrometheusManagedAccount;
import com.netflix.kayenta.prometheus.config.PrometheusResponseConverter;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.io.IOException;
import java.util.Set;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import(TestPrometheusConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

  @Autowired protected Environment environment;

  private Integer managementPort;

  private Integer serverPort;

  @Autowired private AccountCredentialsRepository accountCredentialsRepository;

  @Autowired PrometheusResponseConverter prometheusConverter;
  @Autowired PrometheusConfigurationProperties prometheusConfigurationProperties;
  @Autowired RetrofitClientFactory retrofitClientFactory;
  @Autowired OkHttpClient okHttpClient;

  private boolean setupDone = false;

  @BeforeAll
  public void setupPrometheusAccounts() throws InterruptedException {
    if (setupDone) {
      return;
    }

    int retries = 30; // wait up to 30 seconds
    String prometheusPortStr = null;

    while (retries-- > 0) {
      prometheusPortStr = environment.getProperty("embedded.prometheus.port");
      if (prometheusPortStr != null) {
        break;
      }
      Thread.sleep(1000);
    }

    if (prometheusPortStr == null) {
      throw new IllegalStateException("embedded.prometheus.port not set even after waiting!");
    }
    Set<AccountCredentials> accountCredentialsSet =
        accountCredentialsRepository.getAllOf(AccountCredentials.Type.METRICS_STORE);
    String dynamicEndpoint = "http://localhost:" + prometheusPortStr;

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

    Thread.sleep(60000); // 1 minute
    setupDone = true;
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
}
