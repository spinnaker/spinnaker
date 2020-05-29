/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.front50.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Controls configuration for the plugin version pinning metadata storage cleanup. */
@ConfigurationProperties("storage-service.plugin-version-pinning.cleanup")
public class PluginVersionCleanupProperties {
  /** The maximum number of pinned version records to keep by cluster (and location). */
  public int maxVersionsPerCluster = 10;

  /** The interval that the cleanup agent will run. */
  public Duration interval = Duration.ofDays(1);
}
