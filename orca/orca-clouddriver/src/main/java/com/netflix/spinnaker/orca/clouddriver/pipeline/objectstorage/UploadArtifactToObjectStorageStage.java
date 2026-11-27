/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.objectstorage;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.objectstorage.UploadArtifactToObjectStorageTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * Stage that uploads a Spinnaker artifact to cloud object storage.
 *
 * <p>This stage:
 *
 * <ol>
 *   <li>Resolves the artifact and submits the upload operation to Clouddriver
 *   <li>Monitors the Clouddriver task until completion
 * </ol>
 *
 * <p>The stage context should contain:
 *
 * <ul>
 *   <li>cloudProvider: The cloud provider (e.g., "aws", "gce")
 *   <li>credentials: The account name to use for the upload
 *   <li>region: The region where the bucket is located
 *   <li>bucket: The bucket name
 *   <li>objectKeyPrefix: Optional prefix for uploaded objects (supports SpEL)
 *   <li>artifactId or artifact: Reference to the artifact to upload
 * </ul>
 *
 * <p>Clouddriver extracts the archive (ZIP, TAR, TAR.GZ) and uploads all files to the specified
 * bucket, preserving directory structure.
 */
@Component
public class UploadArtifactToObjectStorageStage implements StageDefinitionBuilder {

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("uploadArtifactToObjectStorage", UploadArtifactToObjectStorageTask.class)
        .withTask("monitorUpload", MonitorKatoTask.class);
  }
}
