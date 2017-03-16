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

package com.netflix.spinnaker.halyard.deploy.provider.v1;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerMonitoringDaemonService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class OperationFactory {
  abstract public Map<String, Object> createDeployPipeline(String accountName,
      SpinnakerService service,
      String artifact,
      SpinnakerMonitoringDaemonService monitoringService,
      String monitoringArtifact,
      List<ConfigSource> configSources,
      boolean update);
  abstract public Map<String, Object> createDeployPipeline(String accountName,
      SpinnakerService service,
      String artifact,
      List<ConfigSource> configSources,
      boolean update);
  abstract public Map<String, Object> createUpsertPipeline(String accountName, SpinnakerService service);

  abstract protected Provider.ProviderType getProviderType();

  protected Map<String, Object> redBlackStage(Map<String, Object> deployDescription, List<String> healthProviders, String region) {
    deployDescription = deployStage(deployDescription, healthProviders, region);
    deployDescription.put("strategy", "redblack");
    return deployDescription;
  }

  protected Map<String, Object> deployStage(Map<String, Object> deployDescription, List<String> healthProviders, String region) {
    deployDescription.put("interestingHealthProviders", healthProviders);
    deployDescription.put("region", region);
    deployDescription.put("type", AtomicOperations.CREATE_SERVER_GROUP);
    deployDescription.put("cloudProvider", getProviderType().getId());
    deployDescription.put("refId", "deployredblack");
    return deployDescription;
  }

  protected Map<String, Object> upsertTask(Map<String, Object> upsertDescription, Map<String, List<String>> availabilityZones) {
    upsertDescription.put("type", AtomicOperations.UPSERT_LOAD_BALANCER);
    upsertDescription.put("cloudProvider", getProviderType().getId());
    upsertDescription.put("refId", "upsertlb");
    upsertDescription.put("application", "spin");
    upsertDescription.put("availabilityZones", availabilityZones);
    return upsertDescription;
  }

  protected Map<String, Object> buildPipeline(String name, List<Map<String, Object>> stages) {
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("stages", stages);
    pipeline.put("description", name);
    pipeline.put("application", "spin");
    pipeline.put("name", name);
    return pipeline;
  }

  protected Map<String, Object> buildTask(String name, List<Map<String, Object>> stages) {
    Map<String, Object> task = new HashMap<>();
    task.put("job", stages);
    task.put("description", name);
    task.put("application", "spin");
    task.put("name", name);
    return task;
  }

  @Data
  public static class ConfigSource {
    String id;
    String mountPoint;
  }
}
