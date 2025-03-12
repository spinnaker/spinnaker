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

package com.netflix.spinnaker.orca.clouddriver.config;

import com.netflix.spinnaker.orca.clouddriver.config.tasks.CheckIfApplicationExistsTaskConfig;
import com.netflix.spinnaker.orca.clouddriver.config.tasks.RetryConfig;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCheckIfApplicationExistsTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.WaitOnJobCompletion;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.PromoteManifestKatoOutputsTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ResolveDeploySourceManifestTask;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** configuration properties for various Orca tasks that are in the orca-clouddriver module */
@Data
@ConfigurationProperties("tasks.clouddriver")
public class TaskConfigurationProperties {

  /** properties that pertain to {@link WaitOnJobCompletion} task. */
  private WaitOnJobCompletionTaskConfig waitOnJobCompletionTask =
      new WaitOnJobCompletionTaskConfig();

  /** properties that pertain to {@link PromoteManifestKatoOutputsTask} task */
  private PromoteManifestKatoOutputsTaskConfig promoteManifestKatoOutputsTask =
      new PromoteManifestKatoOutputsTaskConfig();

  /** properties that pertain to {@link ResolveDeploySourceManifestTask} task */
  private ResolveDeploySourceManifestTaskConfig resolveDeploySourceManifestTask =
      new ResolveDeploySourceManifestTaskConfig();

  /** properties that pertain to {@link AbstractCheckIfApplicationExistsTask} task */
  private CheckIfApplicationExistsTaskConfig checkIfApplicationExistsTask =
      new CheckIfApplicationExistsTaskConfig();

  @Data
  public static class WaitOnJobCompletionTaskConfig {
    /**
     * set of keys that will be excluded from the "outputs" key in the stage execution context.
     * Default or empty set means that no keys will be excluded.
     */
    private Set<String> excludeKeysFromOutputs = Set.of();

    private RetryConfig jobStatusRetry = new RetryConfig();

    private RetryConfig fileContentRetry = new RetryConfig();
  }

  @Data
  public static class PromoteManifestKatoOutputsTaskConfig {
    /**
     * set of keys that will be excluded from the "outputs" key in the stage execution context.
     * Default or empty set means that no keys will be excluded.
     */
    private Set<String> excludeKeysFromOutputs = Set.of();
  }

  @Data
  public static class ResolveDeploySourceManifestTaskConfig {
    /**
     * set of keys that will be excluded from the "outputs" key in the stage execution context.
     * Default or empty set means that no keys will be excluded.
     */
    private Set<String> excludeKeysFromOutputs = Set.of();
  }
}
