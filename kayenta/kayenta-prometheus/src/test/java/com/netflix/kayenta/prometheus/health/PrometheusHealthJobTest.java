/*
 * Copyright 2020 Playtika.
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

package com.netflix.kayenta.prometheus.health;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.kayenta.prometheus.config.PrometheusConfigurationProperties;
import com.netflix.kayenta.prometheus.config.PrometheusManagedAccount;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
public class PrometheusHealthJobTest {

  private static final String PROM_ACCOUNT_1 = "a1";
  private static final String PROM_ACCOUNT_2 = "a2";
  @Mock PrometheusRemoteService PROM_REMOTE_1;
  @Mock PrometheusRemoteService PROM_REMOTE_2;

  @Mock PrometheusConfigurationProperties prometheusConfigurationProperties;
  @Mock AccountCredentialsRepository accountCredentialsRepository;
  @Mock PrometheusHealthCache healthCache;

  @InjectMocks PrometheusHealthJob healthJob;

  @BeforeEach
  public void setUp() {
    when(prometheusConfigurationProperties.getAccounts())
        .thenReturn(
            Arrays.asList(
                getPrometheusManagedAccount(PROM_ACCOUNT_1),
                getPrometheusManagedAccount(PROM_ACCOUNT_2)));

    when(accountCredentialsRepository.getOne(PROM_ACCOUNT_1))
        .thenReturn(
            Optional.of(
                PrometheusManagedAccount.builder()
                    .name(PROM_ACCOUNT_1)
                    .prometheusRemoteService(PROM_REMOTE_1)
                    .build()));

    when(accountCredentialsRepository.getOne(PROM_ACCOUNT_2))
        .thenReturn(
            Optional.of(
                PrometheusManagedAccount.builder()
                    .name(PROM_ACCOUNT_2)
                    .prometheusRemoteService(PROM_REMOTE_2)
                    .build()));
  }

  @Test
  public void allRemotesAreUp() {
    when(PROM_REMOTE_1.isHealthy()).thenReturn("OK");
    when(PROM_REMOTE_2.isHealthy()).thenReturn("OK");

    healthJob.run();

    verify(healthCache)
        .setHealthStatuses(
            Arrays.asList(
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName(PROM_ACCOUNT_1)
                    .status(Status.UP)
                    .build(),
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName(PROM_ACCOUNT_2)
                    .status(Status.UP)
                    .build()));
  }

  @Test
  public void allRemotesAreDown() {
    when(PROM_REMOTE_1.isHealthy()).thenThrow(new RuntimeException("test 1"));
    when(PROM_REMOTE_2.isHealthy()).thenThrow(new RuntimeException("test 2"));

    healthJob.run();

    verify(healthCache)
        .setHealthStatuses(
            Arrays.asList(
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName(PROM_ACCOUNT_1)
                    .status(Status.DOWN)
                    .errorDetails("java.lang.RuntimeException: test 1")
                    .build(),
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName(PROM_ACCOUNT_2)
                    .status(Status.DOWN)
                    .errorDetails("java.lang.RuntimeException: test 2")
                    .build()));
  }

  @Test
  public void oneRemoteIsDown() {
    when(PROM_REMOTE_1.isHealthy()).thenReturn("OK");
    when(PROM_REMOTE_2.isHealthy()).thenThrow(new RuntimeException("test 2"));

    healthJob.run();

    verify(healthCache)
        .setHealthStatuses(
            Arrays.asList(
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName(PROM_ACCOUNT_1)
                    .status(Status.UP)
                    .build(),
                PrometheusHealthJob.PrometheusHealthStatus.builder()
                    .accountName(PROM_ACCOUNT_2)
                    .status(Status.DOWN)
                    .errorDetails("java.lang.RuntimeException: test 2")
                    .build()));
  }

  private PrometheusManagedAccount getPrometheusManagedAccount(String name) {
    PrometheusManagedAccount account = new PrometheusManagedAccount();
    account.setName(name);
    RemoteService endpoint = new RemoteService();
    endpoint.setBaseUrl("any");
    account.setEndpoint(endpoint);
    return account;
  }
}
