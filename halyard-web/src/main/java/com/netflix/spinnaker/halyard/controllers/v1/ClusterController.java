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
 *
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cluster;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.services.v1.ClusterService;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.models.v1.DefaultValidationSettings;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * Controller for adding clusters to a provider
 */
@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers/{providerName:.+}/clusters")
public class ClusterController {
  @Autowired
  ClusterService clusterService;

  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Cluster>> clusters(@PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Cluster>>builder()
        .getter(() -> clusterService.getAllClusters(deploymentName, providerName))
        .validator(() -> clusterService.validateAllClusters(deploymentName, providerName))
        .description("Get all " + providerName + " clusters")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/cluster/{clusterName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Cluster> cluster(@PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String clusterName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Cluster>builder()
        .getter(() -> clusterService.getProviderCluster(deploymentName, providerName, clusterName))
        .validator(() -> clusterService.validateCluster(deploymentName, providerName, clusterName))
        .description("Get " + clusterName + " cluster")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/cluster/{clusterName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteCluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String clusterName,
      @RequestParam(required = false, defaultValue = DefaultValidationSettings.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultValidationSettings.severity) Problem.Severity severity) {
    DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();

    builder.setUpdate(() -> clusterService.deleteCluster(deploymentName, providerName, clusterName));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> clusterService.validateAllClusters(deploymentName, providerName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Delete the " + clusterName + " cluster");
  }

  @RequestMapping(value = "/cluster/{clusterName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setCluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String clusterName,
      @RequestParam(required = false, defaultValue = DefaultValidationSettings.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultValidationSettings.severity) Problem.Severity severity,
      @RequestBody Object rawCluster) {
    Cluster cluster = objectMapper.convertValue(
        rawCluster,
        Providers.translateClusterType(providerName)
    );

    DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();

    builder.setUpdate(() -> clusterService.setCluster(deploymentName, providerName, clusterName, cluster));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> clusterService.validateCluster(deploymentName, providerName, cluster.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + clusterName + " cluster");
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addCluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultValidationSettings.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultValidationSettings.severity) Problem.Severity severity,
      @RequestBody Object rawCluster) {
    Cluster cluster = objectMapper.convertValue(
        rawCluster,
        Providers.translateClusterType(providerName)
    );

    DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();
    builder.setSeverity(severity);

    builder.setUpdate(() -> clusterService.addCluster(deploymentName, providerName, cluster));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> clusterService.validateCluster(deploymentName, providerName, cluster.getName());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Add the " + cluster.getName() + " cluster");
  }
}
