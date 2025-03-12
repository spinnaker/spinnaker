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

package com.netflix.kayenta.prometheus.metrics;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.kayenta.prometheus.config.PrometheusManagedAccount;
import com.netflix.kayenta.prometheus.model.PrometheusMetricDescriptorsResponse;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PrometheusMetricDescriptorsCacheTest {

  private static final String ACCOUNT_1 = "metrics-acc-1";
  private static final String ACCOUNT_2 = "metrics-acc-2";

  @Mock PrometheusRemoteService prometheusRemote1;

  @Mock AccountCredentialsRepository accountCredentialRepo;

  @InjectMocks PrometheusMetricDescriptorsCache cache;

  @Test
  public void returnsEmptyMapIfNoDataForEmptyFilter() {
    cache.updateMetricDescriptorsCache();

    List<Map> metadata = cache.getMetadata(ACCOUNT_1, "");

    assertThat(metadata).isEmpty();
  }

  @Test
  public void returnsEmptyMapIfNoDataForMetricFilter() {
    cache.updateMetricDescriptorsCache();

    List<Map> metadata = cache.getMetadata(ACCOUNT_1, "metric_1");

    assertThat(metadata).isEmpty();
  }

  @Test
  public void returnsMetricsByAnyCase() {
    when(accountCredentialRepo.getAllOf(AccountCredentials.Type.METRICS_STORE))
        .thenReturn(
            Collections.singleton(
                PrometheusManagedAccount.builder()
                    .name(ACCOUNT_1)
                    .prometheusRemoteService(prometheusRemote1)
                    .build()));
    when(prometheusRemote1.listMetricDescriptors())
        .thenReturn(
            PrometheusMetricDescriptorsResponse.builder()
                .data(Arrays.asList("metric_1", "METRIC_2", "MEtriC_3", "other_thing"))
                .status("success")
                .build());

    cache.updateMetricDescriptorsCache();

    List<Map> metadata = cache.getMetadata(ACCOUNT_1, "METR");

    assertThat(metadata)
        .containsOnly(
            singletonMap("name", "metric_1"),
            singletonMap("name", "METRIC_2"),
            singletonMap("name", "MEtriC_3"));
  }

  @Test
  public void returnsMetricsForSpecificAccount() {
    when(accountCredentialRepo.getAllOf(AccountCredentials.Type.METRICS_STORE))
        .thenReturn(
            Collections.singleton(
                PrometheusManagedAccount.builder()
                    .name(ACCOUNT_1)
                    .prometheusRemoteService(prometheusRemote1)
                    .build()));
    when(prometheusRemote1.listMetricDescriptors())
        .thenReturn(
            PrometheusMetricDescriptorsResponse.builder()
                .data(Arrays.asList("metric_1", "METRIC_2", "MEtriC_3", "other_thing"))
                .status("success")
                .build());

    cache.updateMetricDescriptorsCache();

    assertThat(cache.getMetadata(ACCOUNT_2, "METR")).isEmpty();
  }

  @Test
  public void returnsAllMetricsForEmptyFilter() {
    when(accountCredentialRepo.getAllOf(AccountCredentials.Type.METRICS_STORE))
        .thenReturn(
            Collections.singleton(
                PrometheusManagedAccount.builder()
                    .name(ACCOUNT_1)
                    .prometheusRemoteService(prometheusRemote1)
                    .build()));
    when(prometheusRemote1.listMetricDescriptors())
        .thenReturn(
            PrometheusMetricDescriptorsResponse.builder()
                .data(Arrays.asList("metric_1", "METRIC_2", "MEtriC_3", "other_thing"))
                .status("success")
                .build());

    cache.updateMetricDescriptorsCache();

    List<Map> metadata = cache.getMetadata(ACCOUNT_1, "");

    assertThat(metadata)
        .containsOnly(
            singletonMap("name", "metric_1"),
            singletonMap("name", "METRIC_2"),
            singletonMap("name", "MEtriC_3"),
            singletonMap("name", "other_thing"));
  }

  @Test
  public void returnsEmptyDataIfPrometheusReturnsSuccessAndEmptyData() {
    when(accountCredentialRepo.getAllOf(AccountCredentials.Type.METRICS_STORE))
        .thenReturn(
            Collections.singleton(
                PrometheusManagedAccount.builder()
                    .name(ACCOUNT_1)
                    .prometheusRemoteService(prometheusRemote1)
                    .build()));
    when(prometheusRemote1.listMetricDescriptors())
        .thenReturn(PrometheusMetricDescriptorsResponse.builder().status("success").build());

    cache.updateMetricDescriptorsCache();

    assertThat(cache.getMetadata(ACCOUNT_1, "METR")).isEmpty();
  }

  @Test
  public void returnsEmptyDataIfPrometheusReturnsError() {
    when(accountCredentialRepo.getAllOf(AccountCredentials.Type.METRICS_STORE))
        .thenReturn(
            Collections.singleton(
                PrometheusManagedAccount.builder()
                    .name(ACCOUNT_1)
                    .prometheusRemoteService(prometheusRemote1)
                    .build()));
    when(prometheusRemote1.listMetricDescriptors())
        .thenReturn(PrometheusMetricDescriptorsResponse.builder().status("error").build());

    cache.updateMetricDescriptorsCache();

    assertThat(cache.getMetadata(ACCOUNT_1, "METR")).isEmpty();
  }

  @Test
  public void updateMetricDescriptorsCache_callsRepo() {
    cache.updateMetricDescriptorsCache();

    verify(accountCredentialRepo).getAllOf(AccountCredentials.Type.METRICS_STORE);
  }

  @Test
  public void updateMetricDescriptorsCache_ignoresNonPrometheusAccounts() {
    when(accountCredentialRepo.getAllOf(AccountCredentials.Type.METRICS_STORE))
        .thenReturn(Collections.singleton(new TestAccountCredentials()));

    cache.updateMetricDescriptorsCache();

    verify(accountCredentialRepo).getAllOf(AccountCredentials.Type.METRICS_STORE);
  }

  public static class TestAccountCredentials extends AccountCredentials<String> {

    @Override
    public String getName() {
      return "name";
    }

    @Override
    public String getType() {
      return "type";
    }

    @Override
    public List<Type> getSupportedTypes() {
      return Arrays.asList(Type.METRICS_STORE);
    }

    @Override
    public String getCredentials() {
      return "";
    }
  }
}
