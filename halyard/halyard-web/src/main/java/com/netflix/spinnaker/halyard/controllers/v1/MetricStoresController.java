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
 *
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import com.netflix.spinnaker.halyard.config.services.v1.MetricStoresService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericEnableDisableRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/metricStores/")
public class MetricStoresController {
  private final HalconfigParser halconfigParser;
  private final MetricStoresService metricStoresService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, MetricStores> getMetricStores(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<MetricStores>builder()
        .getter(() -> metricStoresService.getMetricStores(deploymentName))
        .validator(() -> metricStoresService.validateMetricStores(deploymentName))
        .description("Get all metric stores")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{metricStoreType:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, MetricStore> getMetricStore(
      @PathVariable String deploymentName,
      @PathVariable String metricStoreType,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<MetricStore>builder()
        .getter(() -> metricStoresService.getMetricStore(deploymentName, metricStoreType))
        .validator(() -> metricStoresService.validateMetricStore(deploymentName, metricStoreType))
        .description("Get " + metricStoreType + " metric store")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMetricStores(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody MetricStores metricStores) {
    return GenericUpdateRequest.<MetricStores>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(m -> metricStoresService.setMetricStores(deploymentName, m))
        .validator(() -> metricStoresService.validateMetricStores(deploymentName))
        .description("Edit all metric stores")
        .build()
        .execute(validationSettings, metricStores);
  }

  @RequestMapping(value = "/{metricStoreType:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMetricStore(
      @PathVariable String deploymentName,
      @PathVariable String metricStoreType,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawMetricStore) {
    MetricStore metricStore =
        objectMapper.convertValue(
            rawMetricStore, MetricStores.translateMetricStoreType(metricStoreType));
    return GenericUpdateRequest.<MetricStore>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(m -> metricStoresService.setMetricStore(deploymentName, m))
        .validator(() -> metricStoresService.validateMetricStore(deploymentName, metricStoreType))
        .description("Edit " + metricStoreType + " metric store")
        .build()
        .execute(validationSettings, metricStore);
  }

  @RequestMapping(value = "/{metricStoreType:.+}/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMethodEnabled(
      @PathVariable String deploymentName,
      @PathVariable String metricStoreType,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> metricStoresService.setMetricStoreEnabled(deploymentName, metricStoreType, e))
        .validator(() -> metricStoresService.validateMetricStore(deploymentName, metricStoreType))
        .description("Edit " + metricStoreType + " metric store")
        .build()
        .execute(validationSettings, enabled);
  }
}
