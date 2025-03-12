/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.config;

import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("tasks.core")
/** configuration properties for various Orca tasks that are in the orca-core module */
public class TaskConfigurationProperties {

  /** properties that pertain to {@link BindProducedArtifactsTask} task */
  private BindProducedArtifactsTaskConfig bindProducedArtifactsTask =
      new BindProducedArtifactsTaskConfig();

  @Data
  public static class BindProducedArtifactsTaskConfig {
    /**
     * set of keys that will be excluded from the "outputs" key in the stage execution context.
     * Default or empty set means that no keys will be excluded.
     */
    private Set<String> excludeKeysFromOutputs = Set.of();
  }
}
