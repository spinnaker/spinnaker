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

package com.netflix.spinnaker.orca.clouddriver.utils;

import com.google.common.collect.ImmutableList;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface CloudProviderAware {
  String DEFAULT_CLOUD_PROVIDER = "aws"; // TODO: Should we fetch this from configuration instead?
  Logger cloudProviderAwareLog = LoggerFactory.getLogger(CloudProviderAware.class);

  default String getDefaultCloudProvider() {
    return DEFAULT_CLOUD_PROVIDER;
  }

  @Nullable
  default String getCloudProvider(StageExecution stage) {
    return getCloudProvider(stage.getContext());
  }

  @Nullable
  default String getCloudProvider(Map<String, Object> context) {
    return (String) context.getOrDefault("cloudProvider", getDefaultCloudProvider());
  }

  @Nullable
  default String getCredentials(StageExecution stage) {
    return getCredentials(stage.getContext());
  }

  @Nullable
  default String getCredentials(Map<String, Object> context) {
    return (String)
        context.getOrDefault(
            "account.name", context.getOrDefault("account", context.get("credentials")));
  }

  // may return a list with 0, 1 or more regions (no guarantees on the ordering)
  default List<String> getRegions(Map<String, Object> context) {
    Object region = context.getOrDefault("region", null);
    if (region != null) {
      if (region instanceof List) {
        return ImmutableList.copyOf((List<String>) region);
      }
      return ImmutableList.of(region.toString());
    }

    try {
      Map<String, Object> deployServerGroups =
          (Map<String, Object>) context.getOrDefault("deploy.server.groups", null);
      if (deployServerGroups == null || deployServerGroups.isEmpty()) {
        return ImmutableList.of();
      }

      Set<String> regions = (Set<String>) deployServerGroups.keySet();
      return ImmutableList.copyOf(regions);
    } catch (ClassCastException e) {
      cloudProviderAwareLog.error(
          "Failed to parse deploy.server.groups in stage context " + context, e);
      return ImmutableList.of();
    }
  }

  default List<String> getRegions(StageExecution stage) {
    return getRegions(stage.getContext());
  }

  default boolean hasCloudProvider(@Nonnull StageExecution stage) {
    return getCloudProvider(stage) != null;
  }

  default boolean hasCredentials(@Nonnull StageExecution stage) {
    return getCredentials(stage) != null;
  }

  default @Nullable String getServerGroupName(StageExecution stage) {
    String serverGroupName = (String) stage.getContext().get("serverGroupName");
    return serverGroupName != null ? serverGroupName : (String) stage.getContext().get("asgName");
  }

  default @Nullable Moniker getMoniker(StageExecution stage) {
    return (Moniker) stage.getContext().get("moniker");
  }

  default ClusterDescriptor getClusterDescriptor(StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = getCredentials(stage);

    // Names accept a null String, and all its fields will be null which is fine in this context
    Names namesFromServerGroup = Names.parseName(getServerGroupName(stage));
    val moniker = Optional.ofNullable(getMoniker(stage));

    String appName = moniker.isPresent() ? moniker.get().getApp() : namesFromServerGroup.getApp();
    String clusterName =
        moniker.isPresent() ? moniker.get().getCluster() : namesFromServerGroup.getCluster();
    return new ClusterDescriptor(appName, account, clusterName, cloudProvider);
  }

  default ServerGroupDescriptor getServerGroupDescriptor(StageExecution stage) {
    List<String> regions = getRegions(stage.getContext());
    String region = regions.isEmpty() ? null : regions.get(0);
    return new ServerGroupDescriptor(getCredentials(stage), getServerGroupName(stage), region);
  }
}
