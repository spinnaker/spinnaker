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
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.model.AtlasResultsHelper;
import com.netflix.kayenta.atlas.security.AtlasNamedAccountCredentials;
import com.netflix.kayenta.atlas.service.AtlasRemoteService;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.metrics.AtlasMetricSetQuery;
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
import java.util.List;
import java.util.Map;

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
  public List<MetricSet> queryMetrics(String accountName,
                                      CanaryMetricConfig canaryMetricConfig,
                                      CanaryScope canaryScope) throws IOException {
    if (!(canaryScope instanceof AtlasCanaryScope)) {
      throw new IllegalArgumentException("Canary scope not instance of AtlasCanaryScope: " + canaryScope);
    }

    AtlasCanaryScope atlasCanaryScope = (AtlasCanaryScope)canaryScope;
    AtlasNamedAccountCredentials credentials = (AtlasNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    AtlasRemoteService atlasRemoteService = credentials.getAtlasRemoteService();
    // TODO(mgraff): Is this how we decorate the base query?
    AtlasMetricSetQuery atlasMetricSetQuery = (AtlasMetricSetQuery)canaryMetricConfig.getQuery();
    String decoratedQuery = atlasMetricSetQuery.getQ() + "," + atlasCanaryScope.cq();
    List<AtlasResults> atlasResultsList = atlasRemoteService.fetch(decoratedQuery,
                                                                   atlasCanaryScope.getStart() + "",
                                                                   atlasCanaryScope.getEnd() + "",
                                                                   atlasCanaryScope.getStep());
    Map<String, AtlasResults> idToAtlasResultsMap = AtlasResultsHelper.merge(atlasResultsList);
    List<MetricSet> metricSetList = new ArrayList<>();

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
        metricSetBuilder.tags(tags);
      }

      metricSetList.add(metricSetBuilder.build());
    }

    return metricSetList;
  }
}
