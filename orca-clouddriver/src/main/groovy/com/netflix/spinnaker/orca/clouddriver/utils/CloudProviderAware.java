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

import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.util.Map;

public interface CloudProviderAware {
  String DEFAULT_CLOUD_PROVIDER = "aws";  // TODO: Should we fetch this from configuration instead?

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
}
