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

import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.security.AtlasNamedAccountCredentials;
import com.netflix.kayenta.atlas.service.AtlasRemoteService;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class AtlasMetricsService implements MetricsService {

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  // These are still placeholder arguments. Each metrics service will have its own set of required/optional arguments. The return type is a placeholder as well.
  public List<MetricSet> queryMetrics(String accountName,
                                      String metricSetName,
                                      String instanceNamePrefix,
                                      String intervalStartTime,
                                      String intervalEndTime) throws IOException {
    AtlasNamedAccountCredentials credentials = (AtlasNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    AtlasRemoteService atlasRemoteService = credentials.getAtlasRemoteService();
    AtlasResults atlasResults = atlasRemoteService.fetch("name,randomValue,:eq,:sum,(,name,),:by", "std.json");
    Instant responseStartTimeInstant = Instant.ofEpochMilli(atlasResults.getStart());
    List<List<Double>> timeSeriesList = atlasResults.getValues();

    if (timeSeriesList == null) {
      timeSeriesList = new ArrayList<>();
    }

    // TODO: Get sample Atlas response with more than one set of results.
    // Deferring this for now since we're going to move to the /fetch endpoint once that's available in oss Atlas.
    // We are currently developing against canned output retrieved via OSS Atlas's /graph endpoint.
    List<Double> pointValues =
      timeSeriesList
        .stream()
        .map(timeSeries -> timeSeries.get(0))
        .collect(Collectors.toList());

    // TODO: Get the metric set name from the request/canary-config.
    MetricSet.MetricSetBuilder metricSetBuilder =
      MetricSet.builder()
        .name(metricSetName)
        .startTimeMillis(atlasResults.getStart())
        .startTimeIso(responseStartTimeInstant.toString())
        .stepMillis(atlasResults.getStep())
        .values(pointValues);

    // TODO: These have to come from the Atlas response. Just not sure from where exactly yet.
    Map<String, String> tags = ImmutableMap.of("not-sure", "about-tags");

    if (tags != null) {
      metricSetBuilder.tags(tags);
    }

    return Collections.singletonList(metricSetBuilder.build());
  }
}
