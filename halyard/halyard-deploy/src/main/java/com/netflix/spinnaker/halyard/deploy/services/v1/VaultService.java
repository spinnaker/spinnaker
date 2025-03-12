/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VaultService {
  @Autowired JobExecutor jobExecutor;

  @Autowired String vaultSecretPrefix;

  @Autowired Integer vaultTimeoutSeconds;

  public void publishSecret(
      DeploymentConfiguration deploymentConfiguration, String name, Path path) {
    String contents;
    try {
      contents = IOUtils.toString(new FileInputStream(path.toFile()));
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.FATAL,
          "Failed to read config file " + path.toString() + ": " + e.getMessage());
    }

    publishSecret(deploymentConfiguration, name, contents);
  }

  public void publishSecret(
      DeploymentConfiguration deploymentConfiguration, String name, String contents) {
    String vaultAddress =
        deploymentConfiguration.getDeploymentEnvironment().getVault().getAddress();
    String encodedContents = Base64.getEncoder().encodeToString(contents.getBytes());
    String secretName = vaultSecretPrefix + name;

    List<String> command = new ArrayList<>();
    command.add("vault");
    command.add("write");
    command.add("--address");
    command.add(vaultAddress);
    command.add(secretName);
    command.add(encodedContents);

    JobRequest request =
        new JobRequest()
            .setTokenizedCommand(command)
            .setTimeoutMillis(TimeUnit.SECONDS.toMillis(vaultTimeoutSeconds));

    String id = jobExecutor.startJob(request);
    DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(5));
    JobStatus status = jobExecutor.updateJob(id);

    if (!status.getResult().equals(JobStatus.Result.SUCCESS)) {
      throw new HalException(
          Problem.Severity.FATAL,
          "Failed to publish secret " + name + ": " + status.getStdOut() + status.getStdErr());
    }
  }
}
