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
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CloudProviderAware {
  String DEFAULT_CLOUD_PROVIDER = "aws";  // TODO: Should we fetch this from configuration instead?
  Logger cloudProviderAwareLog = LoggerFactory.getLogger(CloudProviderAware.class);

  default String getDefaultCloudProvider() {
    return DEFAULT_CLOUD_PROVIDER;
  }

  default String getCloudProvider(Stage stage) {
    return getCloudProvider(stage.getContext());
  }

  default String getCloudProvider(Map<String, Object> context) {
    return (String) context.getOrDefault("cloudProvider", getDefaultCloudProvider());
  }

  default String getCredentials(Stage stage) {
    return getCredentials(stage.getContext());
  }

  default String getCredentials(Map<String, Object> context) {
    return (String) context.getOrDefault("account.name",
      context.getOrDefault("account",
        context.get("credentials")));
  }

  // may return a list with 0, 1 or more regions (no guarantees on the ordering)
  default List<String> getRegions(Map<String, Object> context) {
    String region = (String) context.getOrDefault("region", null);
    if (region != null) {
      return ImmutableList.of(region);
    }

    try {
      Map<String, Object> deployServerGroups = (Map<String, Object>) context.getOrDefault("deploy.server.groups", null);
      if (deployServerGroups == null || deployServerGroups.isEmpty()) {
        return ImmutableList.of();
      }

      Set<String> regions = (Set<String>) deployServerGroups.keySet();
      return ImmutableList.copyOf(regions);
    } catch (ClassCastException e) {
      cloudProviderAwareLog.error("Failed to parse deploy.server.groups in stage context " + context, e);
      return ImmutableList.of();
    }
  }

  default List<String> getRegions(Stage stage) {
    return getRegions(stage.getContext());
  }
}
