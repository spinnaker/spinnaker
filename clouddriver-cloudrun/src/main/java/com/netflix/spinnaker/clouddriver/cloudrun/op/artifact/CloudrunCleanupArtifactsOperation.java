/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.op.artifact;

import com.netflix.spinnaker.clouddriver.cloudrun.description.manifest.CloudrunCleanupArtifactsDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.op.CloudrunManifestOperationResult;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudrunCleanupArtifactsOperation implements AtomicOperation<DeploymentResult> {

  private static final Logger log =
      LoggerFactory.getLogger(CloudrunCleanupArtifactsOperation.class);
  private final CloudrunCleanupArtifactsDescription description;
  private final CloudrunCredentials credentials;
  @Nonnull private final String accountName;
  private static final String OP_NAME = "CLEANUP_CLOUDRUN_ARTIFACTS";

  public CloudrunCleanupArtifactsOperation(CloudrunCleanupArtifactsDescription description) {
    this.description = description;
    this.credentials = description.getCredentials().getCredentials();
    this.accountName = description.getCredentials().getName();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public CloudrunManifestOperationResult operate(List priorOutputs) {
    CloudrunManifestOperationResult result = new CloudrunManifestOperationResult();
    result.setManifests(null);
    return result;
  }
}
