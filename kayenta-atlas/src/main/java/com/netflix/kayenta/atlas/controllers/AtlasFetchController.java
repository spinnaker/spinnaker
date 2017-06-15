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
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.AtlasCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;

@RestController
@RequestMapping("/fetch/atlas")
@Slf4j
public class AtlasFetchController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  SynchronousQueryProcessor synchronousQueryProcessor;

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public String queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                             @RequestParam(required = false) final String storageAccountName,
                             @ApiParam(defaultValue = "name,CpuRawUser,:eq,:sum") @RequestParam String q,
                             @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                             @ApiParam(defaultValue = "cluster") @RequestParam String type,
                             @RequestParam String scope,
                             @ApiParam(defaultValue = "0") @RequestParam String start,
                             @ApiParam(defaultValue = "6000000") @RequestParam String end,
                             @ApiParam(defaultValue = "PT1M") @RequestParam String step) throws IOException {
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    AtlasCanaryMetricSetQueryConfig atlasCanaryMetricSetQueryConfig =
      AtlasCanaryMetricSetQueryConfig
        .builder()
        .q(q)
        .build();
    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig
        .builder()
        .name(metricSetName)
        .query(atlasCanaryMetricSetQueryConfig)
        .build();

    AtlasCanaryScope atlasCanaryScope = new AtlasCanaryScope();
    atlasCanaryScope.setType(type);
    atlasCanaryScope.setScope(scope);
    atlasCanaryScope.setStart(start);
    atlasCanaryScope.setEnd(end);
    atlasCanaryScope.setStep(step);

    return synchronousQueryProcessor.processQuery(resolvedMetricsAccountName,
                                                  resolvedStorageAccountName,
                                                  Collections.singletonList(canaryMetricConfig),
                                                  atlasCanaryScope).get(0);
  }
}
