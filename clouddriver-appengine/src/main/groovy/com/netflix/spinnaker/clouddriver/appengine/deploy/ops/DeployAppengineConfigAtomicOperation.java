/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor;
import com.netflix.spinnaker.clouddriver.appengine.config.AppengineConfigurationProperties;
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineConfigDescription;
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppengineOperationException;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class DeployAppengineConfigAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DEPLOY_APPENGINE_CONFIG";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private DeployAppengineConfigDescription description;

  @Autowired private ArtifactDownloader artifactDownloader;

  @Autowired private Registry registry;

  @Autowired private AppengineJobExecutor jobExecutor;

  public DeployAppengineConfigAtomicOperation(DeployAppengineConfigDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    String serviceAccount = description.getCredentials().getServiceAccountEmail();
    String region = description.getCredentials().getRegion();

    registry
        .counter(
            registry.createId(
                "appengine.deployConfigStart", "account", serviceAccount, "region", region))
        .increment();
    long startTime = registry.clock().monotonicTime();

    AppengineConfigurationProperties.ManagedAccount.GcloudReleaseTrack gCloudReleaseTrack =
        description.getCredentials().getGcloudReleaseTrack();
    List<String> deployCommand = new ArrayList<>();
    deployCommand.add(description.getCredentials().getGcloudPath());
    if (gCloudReleaseTrack != null
        && gCloudReleaseTrack
            != AppengineConfigurationProperties.ManagedAccount.GcloudReleaseTrack.STABLE) {
      deployCommand.add(gCloudReleaseTrack.toString().toLowerCase());
    }
    deployCommand.add("app");
    deployCommand.add("deploy");

    Path directory = createEmptyDirectory();
    String success = "false";

    try {
      if (description.getCronArtifact() != null) {
        getTask().updateStatus(BASE_PHASE, "Downloading cron configuration...");
        File cronFile =
            downloadFileToDirectory(
                description.getCronArtifact(), directory, SupportedConfigTypes.CRON);
        deployCommand.add(cronFile.getPath());
      }

      if (description.getDispatchArtifact() != null) {
        getTask().updateStatus(BASE_PHASE, "Downloading dispatch configuration...");
        File dispatchFile =
            downloadFileToDirectory(
                description.getDispatchArtifact(), directory, SupportedConfigTypes.DISPATCH);
        deployCommand.add(dispatchFile.getPath());
      }

      if (description.getIndexArtifact() != null) {
        getTask().updateStatus(BASE_PHASE, "Downloading index configuration...");
        File indexFile =
            downloadFileToDirectory(
                description.getIndexArtifact(), directory, SupportedConfigTypes.INDEX);
        deployCommand.add(indexFile.getPath());
      }

      if (description.getQueueArtifact() != null) {
        getTask().updateStatus(BASE_PHASE, "Downloading queue configuration...");
        File queueFile =
            downloadFileToDirectory(
                description.getQueueArtifact(), directory, SupportedConfigTypes.QUEUE);
        deployCommand.add(queueFile.getPath());
      }

      deployCommand.add("--project=" + description.getCredentials().getProject());
      deployCommand.add("--account=" + description.getCredentials().getServiceAccountEmail());
      getTask().updateStatus(BASE_PHASE, "Deploying configuration...");
      jobExecutor.runCommand(deployCommand);
      success = "true";
      getTask().updateStatus(BASE_PHASE, "Done deploying configuration");
    } catch (Exception e) {
      throw new AppengineOperationException(
          "Failed to deploy to App Engine with command: " + deployCommand);
    } finally {
      try {
        long duration = registry.clock().monotonicTime() - startTime;
        registry
            .timer(
                registry.createId(
                    "appengine.deployConfig", "account", serviceAccount, "success", success))
            .record(duration, TimeUnit.NANOSECONDS);
        FileUtils.cleanDirectory(directory.toFile());
        FileUtils.forceDelete(directory.toFile());
      } catch (Exception e) {
        throw new AppengineOperationException(
            "Failed to clean up and delete directory: " + directory);
      }
    }
    return null;
  }

  Path createEmptyDirectory() {
    Path path;
    try {
      path = Files.createTempDirectory("appengineconfig-");
      FileUtils.cleanDirectory(path.toFile());
    } catch (IOException ex) {
      throw new AppengineOperationException("Failed to create directory");
    }
    return path;
  }

  File downloadFileToDirectory(Artifact artifact, Path directory, SupportedConfigTypes type) {
    File targetFile;
    try {
      InputStream inStream = artifactDownloader.download(artifact);
      targetFile = new File(directory + "/" + type.toString().toLowerCase() + ".yaml");
      FileUtils.copyInputStreamToFile(inStream, targetFile);
      IOUtils.closeQuietly(inStream);
    } catch (IOException e) {
      throw new AppengineOperationException("Failed to download cron configuration");
    }
    return targetFile;
  }

  enum SupportedConfigTypes {
    CRON,
    QUEUE,
    DISPATCH,
    INDEX
  }
}
