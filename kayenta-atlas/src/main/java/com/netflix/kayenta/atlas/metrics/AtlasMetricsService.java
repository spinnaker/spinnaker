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

package com.netflix.kayenta.atlas.metrics;

import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.atlas.config.AtlasSSEConverter;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.model.AtlasResultsHelper;
import com.netflix.kayenta.atlas.model.Backend;
import com.netflix.kayenta.atlas.security.AtlasNamedAccountCredentials;
import com.netflix.kayenta.atlas.service.AtlasRemoteService;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.AtlasCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.squareup.okhttp.OkHttpClient;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.SECONDS;

@Builder
@Slf4j
public class AtlasMetricsService implements MetricsService {

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  private final RetrofitClientFactory retrofitClientFactory;

  @Autowired
  private final AtlasSSEConverter atlasSSEConverter;

  @Autowired
  private final Registry registry;

  @Override
  public String getType() {
    return "atlas";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public List<MetricSet> queryMetrics(String accountName,
                                      CanaryMetricConfig canaryMetricConfig,
                                      CanaryScope canaryScope) throws IOException {

    OkHttpClient okHttpClient = new OkHttpClient();
    // TODO: (mgraff, duftler) -- we should find out the defaults, and if too small, make this reasonable / configurable
    okHttpClient.setConnectTimeout(90, TimeUnit.SECONDS);
    okHttpClient.setReadTimeout(90, TimeUnit.SECONDS);

    if (!(canaryScope instanceof AtlasCanaryScope)) {
      throw new IllegalArgumentException("Canary scope not instance of AtlasCanaryScope: " + canaryScope);
    }

    AtlasCanaryScope atlasCanaryScope = (AtlasCanaryScope)canaryScope;
    AtlasNamedAccountCredentials credentials = (AtlasNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    Optional<Backend> backend = credentials.getBackendUpdater().getBackendDatabase().getOne(atlasCanaryScope.getDeployment(),
                                                                                            atlasCanaryScope.getDataset(),
                                                                                            atlasCanaryScope.getRegion(),
                                                                                            atlasCanaryScope.getEnvironment());
    if (!backend.isPresent()) {
      throw new IllegalArgumentException("Unable to find an appropriate Atlas cluster for" +
                                          " region=" + atlasCanaryScope.getRegion() +
                                          " dataset=" + atlasCanaryScope.getDataset() +
                                          " deployment=" + atlasCanaryScope.getDeployment() +
                                          " environment=" + atlasCanaryScope.getEnvironment());
    }

    String uri = backend.get().getUri("http",
                                      atlasCanaryScope.getDeployment(),
                                      atlasCanaryScope.getDataset(),
                                      atlasCanaryScope.getRegion(),
                                      atlasCanaryScope.getEnvironment());
    RemoteService remoteService = new RemoteService();
    log.info("Using Atlas backend {}", uri);
    remoteService.setBaseUrl(uri);
    AtlasRemoteService atlasRemoteService = retrofitClientFactory.createClient(AtlasRemoteService.class,
                                                                               atlasSSEConverter,
                                                                               remoteService,
                                                                               okHttpClient);
    AtlasCanaryMetricSetQueryConfig atlasMetricSetQuery = (AtlasCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();
    String decoratedQuery = atlasMetricSetQuery.getQ() + "," + atlasCanaryScope.cq();
    String isoStep = Duration.of(atlasCanaryScope.getStep(), SECONDS) + "";

    long start = registry.clock().monotonicTime();
    List < AtlasResults > atlasResultsList;
    try {
      atlasResultsList = atlasRemoteService.fetch(decoratedQuery,
                                                  atlasCanaryScope.getStart().toEpochMilli(),
                                                  atlasCanaryScope.getEnd().toEpochMilli(),
                                                  isoStep,
                                                  credentials.getFetchId(),
                                                  UUID.randomUUID() + "");
    } finally {
      long end = registry.clock().monotonicTime();
      registry.timer("atlas.fetchTime").record(end - start, TimeUnit.NANOSECONDS);
    }
    Map<String, AtlasResults> idToAtlasResultsMap = AtlasResultsHelper.merge(atlasResultsList);
    List<MetricSet> metricSetList = new ArrayList<>();

    // Gather a list of tags which have multiple values across all results.
    // This is the set of keys we wish to keep.
    // TODO:  this should happen later, during the mixing stage, where we do this per metric name across both control and experiment
    List<String> interestingKeys = idToAtlasResultsMap.values()
      .stream()
      .flatMap(result -> result.getTags().entrySet().stream())
      .collect(Collectors.groupingBy(Map.Entry::getKey))
      .entrySet().stream().filter(stringListEntry -> stringListEntry.getValue().size() > 1)
      .map(Map.Entry::getKey).collect(Collectors.toList());

    for (AtlasResults atlasResults : idToAtlasResultsMap.values()) {
      Instant responseStartTimeInstant = Instant.ofEpochMilli(atlasResults.getStart());
      List<Double> timeSeriesList = atlasResults.getData().getValues();

      if (timeSeriesList == null) {
        timeSeriesList = new ArrayList<>();
      }

      MetricSet.MetricSetBuilder metricSetBuilder =
        MetricSet.builder()
          .name(canaryMetricConfig.getName())
          .startTimeMillis(atlasResults.getStart())
          .startTimeIso(responseStartTimeInstant.toString())
          .stepMillis(atlasResults.getStep())
          .values(timeSeriesList);

      Map<String, String> tags = atlasResults.getTags();

      if (tags != null) {
        Map<String, String> filteredTags = tags.entrySet().stream()
          .filter(entry -> interestingKeys.contains(entry.getKey()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        metricSetBuilder.tags(filteredTags);
      }

      metricSetList.add(metricSetBuilder.build());
    }

    return metricSetList;
  }
}
