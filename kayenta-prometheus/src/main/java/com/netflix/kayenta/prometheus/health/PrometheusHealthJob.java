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

import com.netflix.kayenta.prometheus.config.PrometheusConfigurationProperties;
import com.netflix.kayenta.prometheus.config.PrometheusManagedAccount;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class PrometheusHealthJob {

  private final PrometheusConfigurationProperties prometheusConfigurationProperties;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final PrometheusHealthCache healthCache;

  public PrometheusHealthJob(
      PrometheusConfigurationProperties prometheusConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository,
      PrometheusHealthCache healthCache) {
    this.prometheusConfigurationProperties = prometheusConfigurationProperties;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.healthCache = healthCache;
  }

  @Scheduled(
      initialDelayString = "${kayenta.prometheus.health.initial-delay:PT2S}",
      fixedDelayString = "${kayenta.prometheus.health.fixed-delay:PT5M}")
  public void run() {
    List<PrometheusHealthStatus> healthStatuses =
        prometheusConfigurationProperties.getAccounts().stream()
            .map(
                account -> {
                  String name = account.getName();
                  return accountCredentialsRepository.getOne(name);
                })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(credentials -> credentials instanceof PrometheusManagedAccount)
            .map(credentials -> ((PrometheusManagedAccount) credentials))
            .map(
                credentials -> {
                  try {
                    PrometheusRemoteService remote = credentials.getPrometheusRemoteService();
                    remote.isHealthy();
                    return PrometheusHealthStatus.builder()
                        .accountName(credentials.getName())
                        .status(Status.UP)
                        .build();
                  } catch (Throwable ex) {
                    log.warn(
                        "Prometheus health FAILED for account: {} with exception: ",
                        credentials.getName(),
                        ex);
                    return PrometheusHealthStatus.builder()
                        .accountName(credentials.getName())
                        .status(Status.DOWN)
                        .errorDetails(ex.getClass().getName() + ": " + ex.getMessage())
                        .build();
                  }
                })
            .collect(Collectors.toList());
    healthCache.setHealthStatuses(healthStatuses);
  }

  @Builder
  @Value
  public static class PrometheusHealthStatus {
    @NonNull String accountName;
    @NonNull Status status;
    @Nullable String errorDetails;
  }
}
