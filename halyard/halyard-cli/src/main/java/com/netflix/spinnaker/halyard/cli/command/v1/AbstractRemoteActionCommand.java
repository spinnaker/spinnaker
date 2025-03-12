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

package com.netflix.spinnaker.halyard.cli.command.v1;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiParagraphBuilder;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiStoryBuilder;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiStyle;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Parameters(separators = "=")
public abstract class AbstractRemoteActionCommand extends AbstractConfigCommand {
  @Parameter(
      names = "--auto-run",
      arity = 1,
      description =
          "This command will generate a script to be run on your behalf. By default, the script will run "
              + "without intervention - if you want to override this, provide \"true\" or \"false\" to this flag.")
  Boolean autoRun;

  protected abstract OperationHandler<RemoteAction> getRemoteAction();

  protected void runRemoteAction(OperationHandler<RemoteAction> operation) {
    RemoteAction action = operation.get();
    String scriptPath = action.getScriptPath();

    if (StringUtils.isEmpty(scriptPath)) {
      return;
    }

    boolean shouldRun;
    if (autoRun == null) {
      shouldRun = action.isAutoRun();
    } else {
      shouldRun = action.isAutoRun() && autoRun;
    }

    if (!shouldRun) {
      AnsiStoryBuilder storyBuilder = new AnsiStoryBuilder();

      AnsiParagraphBuilder paragraphBuilder = storyBuilder.addParagraph();
      paragraphBuilder.addSnippet(action.getScriptDescription());
      storyBuilder.addNewline();

      paragraphBuilder = storyBuilder.addParagraph();
      paragraphBuilder.addSnippet("Please run the following script:");
      storyBuilder.addNewline();

      paragraphBuilder = storyBuilder.addParagraph();
      paragraphBuilder.addSnippet(action.getScriptPath()).addStyle(AnsiStyle.UNDERLINE);

      AnsiUi.raw(storyBuilder.toString());
    } else {
      List<String> command = new ArrayList<>();
      command.add(scriptPath);
      JobRequest request = new JobRequest().setTokenizedCommand(command);
      JobExecutor executor = getJobExecutor();
      String jobId = executor.startJobFromStandardStreams(request);

      JobStatus status = null;
      try {
        status = executor.backoffWait(jobId);
      } catch (InterruptedException e) {
        AnsiUi.failure("Interrupted.");
        System.exit(1);
      }

      if (status.getResult() != JobStatus.Result.SUCCESS) {
        AnsiUi.error("Error encountered running script. See above output for more details.");
        System.exit(1);
      }
    }
  }

  @Override
  protected void executeThis() {
    runRemoteAction(getRemoteAction());
  }
}
