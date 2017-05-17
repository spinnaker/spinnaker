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

import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.model.AtlasResultsHelper;
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
  // These are still placeholder arguments. Each metrics service will have its own set of required/optional arguments. The return type is a placeholder as well.
  // Using metricSetName to pass the sse filename for now.
  // TODO(duftler): Make this api generic.
  public List<MetricSet> queryMetrics(String accountName,
                                      String metricSetName,
                                      String instanceNamePrefix,
                                      String intervalStartTime,
                                      String intervalEndTime) throws IOException {
    AtlasNamedAccountCredentials credentials = (AtlasNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    AtlasRemoteService atlasRemoteService = credentials.getAtlasRemoteService();
    List<AtlasResults> atlasResultsList = atlasRemoteService.fetch(metricSetName);
    Map<String, AtlasResults> idToAtlasResultsMap = AtlasResultsHelper.merge(atlasResultsList);
    List<MetricSet> metricSetList = new ArrayList<>();

    for (AtlasResults atlasResults : idToAtlasResultsMap.values()) {
      Instant responseStartTimeInstant = Instant.ofEpochMilli(atlasResults.getStart());
      List<Double> timeSeriesList = atlasResults.getData().getValues();

      if (timeSeriesList == null) {
        timeSeriesList = new ArrayList<>();
      }

      // TODO: Get the metric set name from the request/canary-config.
      MetricSet.MetricSetBuilder metricSetBuilder =
        MetricSet.builder()
          .name("cpu")
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
