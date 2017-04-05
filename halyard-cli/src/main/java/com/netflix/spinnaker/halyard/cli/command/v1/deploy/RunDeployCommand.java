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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiParagraphBuilder;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiStoryBuilder;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiStyle;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Parameters()
public class RunDeployCommand extends AbstractConfigCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "run";

  @Getter(AccessLevel.PUBLIC)
  private String description = "Deploy the currently configured instance of Spinnaker to a selected environment.";

  @Parameter(
      names = "--install-only",
      description = "Download the Spinnaker artifacts without configuring them. This does not work for remote deployments of Spinnaker"
  )
  boolean installOnly;

  @Override
  protected void executeThis() {
    RemoteAction result = new OperationHandler<RemoteAction>()
        .setFailureMesssage("Failed to deploy Spinnaker.")
        .setOperation(Daemon.deployDeployment(getCurrentDeployment(), !noValidate, installOnly))
        .get();

    AnsiStoryBuilder storyBuilder = new AnsiStoryBuilder();
    AnsiParagraphBuilder paragraphBuilder = storyBuilder.addParagraph();
    String scriptDescription = result.getScriptDescription();
    String scriptPath = result.getScriptPath();
    if (!StringUtils.isEmpty(scriptPath)) {
      if (result.isAutoRun()) {
        paragraphBuilder.addSnippet(scriptDescription);

        List<String> command = new ArrayList<>();
        command.add(scriptPath);
        JobRequest request = new JobRequest().setTokenizedCommand(command);
        JobExecutor executor = getJobExecutor();
        String jobId = executor.startJobFromStandardStreams(request);

        JobStatus status = executor.backoffWait(jobId, 10, TimeUnit.SECONDS.toMillis(5));

        if (status.getResult() != JobStatus.Result.SUCCESS) {
          AnsiUi.error("Failed to install Spinnaker. See above output for details.");
          System.exit(1);
        }
      } else {
        paragraphBuilder.addSnippet("Your deployment is almost complete.");
        storyBuilder.addNewline();
        paragraphBuilder = storyBuilder.addParagraph();
        paragraphBuilder.addSnippet(scriptDescription);
        storyBuilder.addNewline();
        paragraphBuilder = storyBuilder.addParagraph();
        paragraphBuilder.addSnippet("Please run the following command: ");
        storyBuilder.addNewline();
        paragraphBuilder = storyBuilder.addParagraph();
        paragraphBuilder.addSnippet(scriptPath).addStyle(AnsiStyle.UNDERLINE);
      }
    }

    AnsiUi.success("Deployment successful.\n");
  }
}
