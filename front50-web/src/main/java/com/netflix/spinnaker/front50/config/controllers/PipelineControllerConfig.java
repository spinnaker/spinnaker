/*
 * Copyright 2024 Salesforce, Inc.
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
 *
 */

package com.netflix.spinnaker.front50.config.controllers;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "controller.pipeline")
public class PipelineControllerConfig {

  /** Holds the configurations to be used for save/update controller mappings */
  private SavePipelineConfiguration save = new SavePipelineConfiguration();

  @Data
  public static class SavePipelineConfiguration {
    /** This controls whether cache should be refreshes while checking for duplicate pipelines */
    boolean refreshCacheOnDuplicatesCheck = true;
  }
}
