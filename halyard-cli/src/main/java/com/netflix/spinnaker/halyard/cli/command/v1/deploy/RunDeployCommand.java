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

package com.netflix.spinnaker.halyard.cli.command.v1.deploy;

import com.amazonaws.util.StringUtils;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.*;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.Deployment;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Parameters()
public class RunDeployCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "run";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Deploy the currently configured instance of Spinnaker to a selected environment.";

  @Override
  protected void executeThis() {
    Deployment.DeployResult result = new OperationHandler<Deployment.DeployResult>()
        .setFailureMesssage("Failed to deploy Spinnaker.")
        .setOperation(Daemon.deployDeployment(getCurrentDeployment(), !noValidate))
        .get();

    AnsiStoryBuilder storyBuilder = new AnsiStoryBuilder();
    AnsiParagraphBuilder paragraphBuilder = storyBuilder.addParagraph();
    paragraphBuilder.addSnippet(result.getPostInstallMessage());
    String scriptPath = result.getScriptPath();
    if (!StringUtils.isNullOrEmpty(scriptPath)) {
      List<String> command = new ArrayList<>();
      command.add(scriptPath);
      JobRequest request = new JobRequest().setTokenizedCommand(command);
      JobExecutor executor = getJobExecutor();
      String jobId = executor.startJobFromStandardStreams(request);

      JobStatus status = executor.backoffWait(jobId, 10, TimeUnit.SECONDS.toMillis(5));

      if (status.getResult() != JobStatus.Result.SUCCESS) {
        AnsiUi.error("Failed to install Spinnaker. See above output for details.");
        return;
      }
    }

    AnsiUi.success("Installation completed.\n");
  }
}
