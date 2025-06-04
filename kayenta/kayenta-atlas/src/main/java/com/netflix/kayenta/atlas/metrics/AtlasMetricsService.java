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

import static java.time.temporal.ChronoUnit.SECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.atlas.backends.AtlasStorageDatabase;
import com.netflix.kayenta.atlas.backends.BackendDatabase;
import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.atlas.config.AtlasSSEConverter;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.model.AtlasResultsHelper;
import com.netflix.kayenta.atlas.model.Backend;
import com.netflix.kayenta.atlas.security.AtlasNamedAccountCredentials;
import com.netflix.kayenta.atlas.service.AtlasRemoteService;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.AtlasCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.util.Retry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;

@Builder
@Slf4j
public class AtlasMetricsService implements MetricsService {

  public final String URI_SCHEME = "https";

  public final int MAX_RETRIES = 10; // maximum number of times we'll retry an Atlas query
  public final long RETRY_BACKOFF = 1000; // time between retries in millis

  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired private final RetrofitClientFactory retrofitClientFactory;

  @Autowired private final ObjectMapper kayentaObjectMapper;

  @Autowired private final Registry registry;

  @Autowired private final OkHttp3ClientConfiguration okHttp3ClientConfig;

  private final Retry retry = new Retry();

  @Override
  public String getType() {
    return "atlas";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  private AtlasNamedAccountCredentials getCredentials(String accountName) {
    return accountCredentialsRepository.getRequiredOne(accountName);
  }

  @Override
  public List<MetricSet> queryMetrics(
      String accountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope) {

    OkHttpClient okHttpClient =
        okHttp3ClientConfig
            .createForRetrofit2()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();

    if (!(canaryScope instanceof AtlasCanaryScope)) {
      throw new IllegalArgumentException(
          "Canary scope not instance of AtlasCanaryScope: "
              + canaryScope
              + ". One common cause is having multiple METRICS_STORE accounts configured but "
              + "neglecting to explicitly specify which account to use for a given request.");
    }

    AtlasCanaryScope atlasCanaryScope = (AtlasCanaryScope) canaryScope;
    AtlasNamedAccountCredentials credentials = getCredentials(accountName);
    BackendDatabase backendDatabase = credentials.getBackendUpdater().getBackendDatabase();
    String uri = backendDatabase.getUriForLocation(URI_SCHEME, atlasCanaryScope.getLocation());

    // If the location was not explicitly given, look up the account in the atlastStorageService.
    // If it
    // is found there, use the proper cname found there.
    //
    // For a global dataset:
    //     accountId == the account ID to look up
    //     dataset == global
    // For a regional dataset:
    //     accountId == the account ID to look up
    //     dataset == regional
    //     location == region (us-east-1, etc)
    if (uri == null && atlasCanaryScope.getAccountId() != null) {
      String accountId = atlasCanaryScope.getAccountId();
      AtlasStorageDatabase atlasStorageDatabase =
          credentials.getAtlasStorageUpdater().getAtlasStorageDatabase();
      if (atlasCanaryScope.getDataset().equals("global")) {
        Optional<String> globalCname = atlasStorageDatabase.getGlobalUri(URI_SCHEME, accountId);
        if (globalCname.isPresent()) {
          uri = globalCname.get();
        }
      } else {
        String region = atlasCanaryScope.getLocation();
        Optional<String> regionalCname =
            atlasStorageDatabase.getRegionalUri(URI_SCHEME, accountId, region);
        if (regionalCname.isPresent()) {
          uri = regionalCname.get();
        }
      }
    }

    if (uri == null) {
      Optional<Backend> backend =
          backendDatabase.getOne(
              atlasCanaryScope.getDeployment(),
              atlasCanaryScope.getDataset(),
              atlasCanaryScope.getLocation(),
              atlasCanaryScope.getEnvironment());
      if (backend.isPresent()) {
        uri =
            backend
                .get()
                .getUri(
                    URI_SCHEME,
                    atlasCanaryScope.getDeployment(),
                    atlasCanaryScope.getDataset(),
                    atlasCanaryScope.getLocation(),
                    atlasCanaryScope.getEnvironment());
      }
    }

    if (uri == null) {
      throw new IllegalArgumentException(
          "Unable to find an appropriate Atlas cluster for"
              + " location="
              + atlasCanaryScope.getLocation()
              + " accountId="
              + atlasCanaryScope.getAccountId()
              + " dataset="
              + atlasCanaryScope.getDataset()
              + " deployment="
              + atlasCanaryScope.getDeployment()
              + " environment="
              + atlasCanaryScope.getEnvironment());
    }

    RemoteService remoteService = new RemoteService();
    log.info("Using Atlas backend {}", uri);
    remoteService.setBaseUrl(uri);
    AtlasSSEConverter atlasSSEConverter =
        new AtlasSSEConverter(
            kayentaObjectMapper,
            canaryConfig.getName(),
            canaryMetricConfig.getName(),
            canaryMetricConfig.getQuery().toString());

    AtlasRemoteService atlasRemoteService =
        retrofitClientFactory.createClient(
            AtlasRemoteService.class, atlasSSEConverter, remoteService, okHttpClient);
    AtlasCanaryMetricSetQueryConfig atlasMetricSetQuery =
        (AtlasCanaryMetricSetQueryConfig) canaryMetricConfig.getQuery();
    String decoratedQuery = atlasMetricSetQuery.getQ() + "," + atlasCanaryScope.cq();
    String isoStep = Duration.of(atlasCanaryScope.getStep(), SECONDS) + "";

    long start = registry.clock().monotonicTime();
    List<AtlasResults> atlasResultsList;
    try {
      atlasResultsList =
          retry.retry(
              () ->
                  atlasRemoteService.fetch(
                      decoratedQuery,
                      atlasCanaryScope.getStart().toEpochMilli(),
                      atlasCanaryScope.getEnd().toEpochMilli(),
                      isoStep,
                      credentials.getFetchId(),
                      UUID.randomUUID() + ""),
              MAX_RETRIES,
              RETRY_BACKOFF);
    } finally {
      long end = registry.clock().monotonicTime();
      registry.timer("atlas.fetchTime").record(end - start, TimeUnit.NANOSECONDS);
    }
    Map<String, AtlasResults> idToAtlasResultsMap = AtlasResultsHelper.merge(atlasResultsList);
    List<MetricSet> metricSetList = new ArrayList<>();

    for (AtlasResults atlasResults : idToAtlasResultsMap.values()) {
      Instant responseStartTimeInstant = Instant.ofEpochMilli(atlasResults.getStart());
      Instant responseEndTimeInstant = Instant.ofEpochMilli(atlasResults.getEnd());
      List<Double> timeSeriesList = atlasResults.getData().getValues();

      if (timeSeriesList == null) {
        timeSeriesList = new ArrayList<>();
      }

      MetricSet.MetricSetBuilder metricSetBuilder =
          MetricSet.builder()
              .name(canaryMetricConfig.getName())
              .startTimeMillis(atlasResults.getStart())
              .startTimeIso(responseStartTimeInstant.toString())
              .endTimeMillis(atlasResults.getEnd())
              .endTimeIso(responseEndTimeInstant.toString())
              .stepMillis(atlasResults.getStep())
              .values(timeSeriesList);

      Map<String, String> tags = atlasResults.getTags();

      if (tags != null) {
        List<String> groupByKeys;
        if (atlasResults.getGroupByKeys() == null) {
          groupByKeys = Collections.emptyList();
        } else {
          groupByKeys = atlasResults.getGroupByKeys();
        }
        Map<String, String> filteredTags =
            tags.entrySet().stream()
                .filter(entry -> groupByKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        metricSetBuilder.tags(filteredTags);
      }

      metricSetBuilder.attribute("query", decoratedQuery);
      metricSetBuilder.attribute("baseURL", uri);

      metricSetList.add(metricSetBuilder.build());
    }

    // If nothing was returned, add a placeholder for us to add metadata about this query.
    if (metricSetList.size() == 0) {
      MetricSet metricSet =
          MetricSet.builder()
              .name(canaryMetricConfig.getName())
              .startTimeMillis(atlasCanaryScope.getStart().toEpochMilli())
              .startTimeIso(atlasCanaryScope.getStart().toString())
              .endTimeMillis(atlasCanaryScope.getEnd().toEpochMilli())
              .endTimeIso(atlasCanaryScope.getEnd().toString())
              .stepMillis(atlasCanaryScope.getStep() * 1000)
              .tags(Collections.emptyMap())
              .values(Collections.emptyList())
              .attribute("query", decoratedQuery)
              .attribute("baseURL", uri)
              .build();
      metricSetList.add(metricSet);
    }

    return metricSetList;
  }
}
