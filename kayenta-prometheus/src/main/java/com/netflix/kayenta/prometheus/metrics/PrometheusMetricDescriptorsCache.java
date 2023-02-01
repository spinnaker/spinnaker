/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.prometheus.metrics;

import com.netflix.kayenta.prometheus.config.PrometheusManagedAccount;
import com.netflix.kayenta.prometheus.model.PrometheusMetricDescriptor;
import com.netflix.kayenta.prometheus.model.PrometheusMetricDescriptorsResponse;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class PrometheusMetricDescriptorsCache {

  private volatile Map<String, List<PrometheusMetricDescriptor>> cache = Collections.emptyMap();

  private final AccountCredentialsRepository accountCredentialsRepository;

  public PrometheusMetricDescriptorsCache(
      AccountCredentialsRepository accountCredentialsRepository) {
    this.accountCredentialsRepository = accountCredentialsRepository;
  }

  public List<Map> getMetadata(String metricsAccountName, String filter) {
    List<PrometheusMetricDescriptor> accountSpecificMetricDescriptorsCache =
        this.cache.get(metricsAccountName);

    if (CollectionUtils.isEmpty(accountSpecificMetricDescriptorsCache)) {
      return Collections.emptyList();
    }

    if (StringUtils.isEmpty(filter)) {
      return accountSpecificMetricDescriptorsCache.stream()
          .map(metricDescriptor -> metricDescriptor.getMap())
          .collect(Collectors.toList());
    } else {
      String lowerCaseFilter = filter.toLowerCase();

      return accountSpecificMetricDescriptorsCache.stream()
          .filter(
              metricDescriptor ->
                  metricDescriptor.getName().toLowerCase().contains(lowerCaseFilter))
          .map(metricDescriptor -> metricDescriptor.getMap())
          .collect(Collectors.toList());
    }
  }

  @Scheduled(fixedDelayString = "#{@prometheusConfigurationProperties.metadataCachingIntervalMS}")
  public void updateMetricDescriptorsCache() {
    Set<AccountCredentials> accountCredentialsSet =
        accountCredentialsRepository.getAllOf(AccountCredentials.Type.METRICS_STORE);

    Map<String, List<PrometheusMetricDescriptor>> updatedCache =
        accountCredentialsSet.stream()
            .filter(credentials -> credentials instanceof PrometheusManagedAccount)
            .map(credentials -> (PrometheusManagedAccount) credentials)
            .map(this::listMetricDescriptors)
            .filter(this::isSuccessful)
            .filter(this::hasData)
            .collect(
                Collectors.toMap(
                    AccountResponse::getMetricsAccountName, this::toPrometheusMetricDescriptors));

    this.cache = updatedCache;
  }

  private List<PrometheusMetricDescriptor> toPrometheusMetricDescriptors(
      AccountResponse accountResponse) {
    List<PrometheusMetricDescriptor> descriptors =
        accountResponse.getResponse().getData().stream()
            .map(PrometheusMetricDescriptor::new)
            .collect(Collectors.toList());
    log.debug(
        "Updated cache with {} metric descriptors via account {}.",
        descriptors.size(),
        accountResponse.getMetricsAccountName());
    return descriptors;
  }

  private AccountResponse listMetricDescriptors(PrometheusManagedAccount credentials) {
    PrometheusRemoteService prometheusRemoteService = credentials.getPrometheusRemoteService();
    PrometheusMetricDescriptorsResponse remoteResponse =
        prometheusRemoteService.listMetricDescriptors();
    return new AccountResponse(credentials.getName(), remoteResponse);
  }

  private boolean hasData(AccountResponse accountResponse) {
    boolean empty = CollectionUtils.isEmpty(accountResponse.getResponse().getData());
    if (empty) {
      log.debug(
          "While updating cache, found no metric descriptors via account {}.",
          accountResponse.getMetricsAccountName());
    }
    return !empty;
  }

  private boolean isSuccessful(AccountResponse accountResponse) {
    PrometheusMetricDescriptorsResponse response = accountResponse.getResponse();
    boolean success = response != null && response.getStatus().equals("success");
    if (!success) {
      log.debug(
          "While updating cache, found no metric descriptors via account {}.",
          accountResponse.getMetricsAccountName());
    }
    return success;
  }

  @Value
  private static class AccountResponse {
    String metricsAccountName;
    PrometheusMetricDescriptorsResponse response;
  }
}
