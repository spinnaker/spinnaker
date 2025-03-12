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

package com.netflix.kayenta.atlas.controllers;

import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.metrics.AtlasCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import io.swagger.v3.oas.annotations.Parameter;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fetch/atlas")
@Slf4j
public class AtlasFetchController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;

  @Autowired
  public AtlasFetchController(
      AccountCredentialsRepository accountCredentialsRepository,
      SynchronousQueryProcessor synchronousQueryProcessor) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
  }

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(
      @RequestParam(required = false) final String metricsAccountName,
      @RequestParam(required = false) final String storageAccountName,
      @Parameter(example = "name,CpuRawUser,:eq,:sum") @RequestParam String q,
      @Parameter(example = "cpu") @RequestParam String metricSetName,
      @Parameter(example = "cluster") @RequestParam String type,
      @RequestParam String scope,
      @Parameter(example = "us-east-1") @RequestParam String location,
      @Parameter(example = "2000-01-01T00:00:00Z") @RequestParam Instant start,
      @Parameter(example = "2000-01-01T04:00:00Z") @RequestParam Instant end,
      @Parameter(example = "300") @RequestParam Long step)
      throws IOException {
    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    AtlasCanaryMetricSetQueryConfig atlasCanaryMetricSetQueryConfig =
        AtlasCanaryMetricSetQueryConfig.builder().q(q).build();
    CanaryMetricConfig canaryMetricConfig =
        CanaryMetricConfig.builder()
            .name(metricSetName)
            .query(atlasCanaryMetricSetQueryConfig)
            .build();

    AtlasCanaryScope atlasCanaryScope = new AtlasCanaryScope();
    atlasCanaryScope.setType(type);
    atlasCanaryScope.setScope(scope);
    atlasCanaryScope.setLocation(location);
    atlasCanaryScope.setStart(start);
    atlasCanaryScope.setEnd(end);
    atlasCanaryScope.setStep(step);

    String metricSetListId =
        synchronousQueryProcessor.executeQuery(
            resolvedMetricsAccountName,
            resolvedStorageAccountName,
            CanaryConfig.builder().metric(canaryMetricConfig).build(),
            0,
            atlasCanaryScope);

    return Collections.singletonMap("metricSetListId", metricSetListId);
  }
}
