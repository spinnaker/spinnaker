/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.clouddriver.tasks.DetermineHealthProvidersTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.utils.ClusterLockHelper;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard;
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.ReleaseLockStage;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import javax.annotation.Nonnull;
import java.beans.Introspector;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractClusterWideClouddriverOperationStage implements StageDefinitionBuilder {

  private final TrafficGuard trafficGuard;
  private final LockingConfigurationProperties lockingConfigurationProperties;
  private final DynamicConfigService dynamicConfigService;

  protected AbstractClusterWideClouddriverOperationStage(TrafficGuard trafficGuard,
                                                         LockingConfigurationProperties lockingConfigurationProperties,
                                                         DynamicConfigService dynamicConfigService) {
    this.trafficGuard = Objects.requireNonNull(trafficGuard);
    this.lockingConfigurationProperties = Objects.requireNonNull(lockingConfigurationProperties);
    this.dynamicConfigService = dynamicConfigService;
  }

  protected abstract Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask();

  protected abstract Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask();

  protected static String getStepName(String taskClassSimpleName) {
    if (taskClassSimpleName.endsWith("Task")) {
      return taskClassSimpleName.substring(0, taskClassSimpleName.length() - "Task".length());
    }
    return taskClassSimpleName;
  }

  @Override
  public final void beforeStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    if (lockingConfigurationProperties.isEnabled()) {
      List<Location> locations = locationsFromStage(parent.getContext());
      ClusterSelection clusterSelection = parent.mapTo(ClusterSelection.class);
      for (Location location : locations) {
        String lockName = ClusterLockHelper.clusterLockName(
          clusterSelection.getMoniker(),
          clusterSelection.getCredentials(),
          location);
        if (trafficGuard.hasDisableLock(clusterSelection.getMoniker(), clusterSelection.getCredentials(), location)) {
          graph.add(stage -> {
            stage.setType(AcquireLockStage.PIPELINE_TYPE);
            stage.getContext().put("lock", Collections.singletonMap("lockName", lockName));
          });
        }
      }
    }
    addAdditionalBeforeStages(parent, graph);
  }

  protected void addAdditionalBeforeStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {

  }

  @Override
  public final void afterStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    addAdditionalAfterStages(parent, graph);
    if (lockingConfigurationProperties.isEnabled()) {
      List<Location> locations = locationsFromStage(parent.getContext());
      ClusterSelection clusterSelection = parent.mapTo(ClusterSelection.class);
      for (Location location : locations) {
        String lockName = ClusterLockHelper.clusterLockName(
          clusterSelection.getMoniker(),
          clusterSelection.getCredentials(),
          location);
        if (trafficGuard.hasDisableLock(clusterSelection.getMoniker(), clusterSelection.getCredentials(), location)) {
          graph.append(stage -> {
            stage.setType(ReleaseLockStage.PIPELINE_TYPE);
            stage.getContext().put("lock", Collections.singletonMap("lockName", lockName));
          });
        }
      }
    }
  }

  protected void addAdditionalAfterStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {

  }

  public static class ClusterSelection {
    private final String cluster;
    private final Moniker moniker;
    private final String cloudProvider;
    private final String credentials;

    @JsonCreator
    public ClusterSelection(
      @JsonProperty("cluster") String cluster,
      @JsonProperty("moniker") Moniker moniker,
      @JsonProperty("cloudProvider") String cloudProvider,
      @JsonProperty("credentials") String credentials) {
      if (cluster == null) {
        if (moniker == null || moniker.getCluster() == null) {
          throw new NullPointerException("At least one of 'cluster' and 'moniker' is required");
        } else {
          this.cluster = moniker.getCluster();
        }
      } else {
        this.cluster = cluster;
      }

      if (moniker == null || moniker.getCluster() == null) {
        this.moniker = MonikerHelper.friggaToMoniker(this.cluster);
      } else {
        this.moniker = moniker;
      }

      this.cloudProvider = Optional.ofNullable(cloudProvider).orElse("aws");
      this.credentials = Objects.requireNonNull(credentials);
    }

    @Override
    public String toString() {
      return String.format("Cluster %s/%s/%s/%s", cloudProvider, credentials, cluster, moniker);
    }

    public String getApplication() {
      return Optional
        .ofNullable(moniker)
        .map(Moniker::getApp)
        .orElseGet(() -> Names.parseName(cluster).getApp());
    }

    public String getCluster() {
      return cluster;
    }

    public Moniker getMoniker() {
      return moniker;
    }

    public String getCloudProvider() {
      return cloudProvider;
    }

    public String getCredentials() {
      return credentials;
    }
  }

  public static List<Location> locationsFromStage(Map<String, Object> context) {

    //LinkedHashMap because we want to iterate in order:
    Map<String, Location.Type> types = new LinkedHashMap<>();

    types.put("namespaces", Location.Type.NAMESPACE);
    types.put("regions", Location.Type.REGION);
    types.put("zones", Location.Type.ZONE);
    types.put("namespace", Location.Type.NAMESPACE);
    types.put("region", Location.Type.REGION);

    for (Map.Entry<String, Location.Type> entry : types.entrySet()) {
      if (context.containsKey(entry.getKey())) {
        Object value = context.get(entry.getKey());
        if (value instanceof Collection && !((Collection<String>) value).isEmpty()) {
          return new LinkedHashSet<>((Collection<String>) value)
            .stream()
            .map(l -> new Location(entry.getValue(), l))
            .collect(Collectors.toList());
        } else if (value instanceof String && !value.toString().isEmpty()) {
          return Collections.singletonList(new Location(entry.getValue(), (String) value));
        }
      }
    }

    return Collections.emptyList();
  }

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    stage.resolveStrategyParams();
    Class<? extends AbstractClusterWideClouddriverTask> operationTask = getClusterOperationTask();
    String name = getStepName(operationTask.getSimpleName());
    String opName = Introspector.decapitalize(name);
    Class<? extends AbstractWaitForClusterWideClouddriverTask> waitTask = getWaitForTask();
    String waitName = Introspector.decapitalize(getStepName(waitTask.getSimpleName()));

    builder
      .withTask("determineHealthProviders", DetermineHealthProvidersTask.class)
      .withTask(opName, operationTask)
      .withTask("monitor" + name, MonitorKatoTask.class);

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
    }

    builder
      .withTask(waitName, waitTask);

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
    }
  }
}
