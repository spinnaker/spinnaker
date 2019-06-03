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
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cluster;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.services.v1.ClusterService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Controller for adding clusters to a provider */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers/{providerName:.+}/clusters")
public class ClusterController {
  private final ClusterService clusterService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Cluster>> clusters(
      @PathVariable String deploymentName,
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
  DaemonTask<Halconfig, Cluster> cluster(
      @PathVariable String deploymentName,
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
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(() -> clusterService.deleteCluster(deploymentName, providerName, clusterName))
        .validator(() -> clusterService.validateAllClusters(deploymentName, providerName))
        .description("Delete the " + clusterName + " cluster")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/cluster/{clusterName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setCluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String clusterName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawCluster) {
    Cluster cluster =
        objectMapper.convertValue(rawCluster, Providers.translateClusterType(providerName));
    return GenericUpdateRequest.<Cluster>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(c -> clusterService.setCluster(deploymentName, providerName, clusterName, c))
        .validator(
            () -> clusterService.validateCluster(deploymentName, providerName, cluster.getName()))
        .description("Edit the " + clusterName + " cluster")
        .build()
        .execute(validationSettings, cluster);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addCluster(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawCluster) {
    Cluster cluster =
        objectMapper.convertValue(rawCluster, Providers.translateClusterType(providerName));
    return GenericUpdateRequest.<Cluster>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(c -> clusterService.addCluster(deploymentName, providerName, c))
        .validator(
            () -> clusterService.validateCluster(deploymentName, providerName, cluster.getName()))
        .description("Add the " + cluster.getName() + " cluster")
        .build()
        .execute(validationSettings, cluster);
  }
}
