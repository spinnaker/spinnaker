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
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import com.netflix.spinnaker.halyard.config.services.v1.MetricStoresService;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/metricStores/")
public class MetricStoresController {
  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  MetricStoresService metricStoresService;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, MetricStores> getMetricStores(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    DaemonResponse.StaticRequestBuilder<MetricStores> builder = new DaemonResponse.StaticRequestBuilder<>();

    builder.setSeverity(severity);
    builder.setBuildResponse(() -> metricStoresService.getMetricStores(deploymentName));

    if (validate) {
      builder.setValidateResponse(() -> metricStoresService.validateMetricStores(deploymentName));
    }

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/{metricStoreType:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, MetricStore> getMetricStore(@PathVariable String deploymentName,
      @PathVariable String metricStoreType,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    DaemonResponse.StaticRequestBuilder<MetricStore> builder = new DaemonResponse.StaticRequestBuilder<>();

    builder.setSeverity(severity);
    builder.setBuildResponse(() -> metricStoresService.getMetricStore(deploymentName, metricStoreType));

    if (validate) {
      builder.setValidateResponse(() -> metricStoresService.validateMetricStore(deploymentName, metricStoreType));
    }

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMetricStores(@PathVariable String deploymentName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawMetricStores) {
    MetricStores metricStores = objectMapper.convertValue(rawMetricStores, MetricStores.class);

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setSeverity(severity);
    builder.setUpdate(() -> metricStoresService.setMetricStores(deploymentName, metricStores));

    builder.setValidate(ProblemSet::new);
    if (validate) {
      builder.setValidate(() -> metricStoresService.validateMetricStores(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/{metricStoreType:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMetricStore(@PathVariable String deploymentName,
      @PathVariable String metricStoreType,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawMetricStore) {
    MetricStore metricStore = objectMapper.convertValue(
        rawMetricStore,
        MetricStores.translateMetricStoreType(metricStoreType)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setSeverity(severity);
    builder.setUpdate(() -> metricStoresService.setMetricStore(deploymentName, metricStore));

    builder.setValidate(ProblemSet::new);
    if (validate) {
      builder.setValidate(() -> metricStoresService.validateMetricStore(deploymentName, metricStoreType));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/{metricStoreType:.+}/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMethodEnabled(@PathVariable String deploymentName,
      @PathVariable String metricStoreType,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> metricStoresService.setMetricStoreEnabled(deploymentName, metricStoreType, enabled));
    builder.setSeverity(severity);

    builder.setValidate(ProblemSet::new);
    if (validate) {
      builder.setValidate(() -> metricStoresService.validateMetricStore(deploymentName, metricStoreType));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }
}
