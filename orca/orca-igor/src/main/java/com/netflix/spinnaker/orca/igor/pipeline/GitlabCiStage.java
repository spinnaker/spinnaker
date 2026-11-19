/*
 * Copyright 2026 Ericsson AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.igor.pipeline;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.igor.tasks.StopBuildJobTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * Stage for triggering and monitoring a GitLab CI pipeline. Reuses the shared CI build task graph
 * (start / wait-for-start / monitor / get-properties / get-artifacts) defined in {@link CIStage}.
 *
 * <p>The stage type resolves to {@code gitlabCi}.
 */
@Component
public class GitlabCiStage extends CIStage {

  /**
   * Sentinel property-file value used to signal that GitLab CI build properties should be scraped
   * from the job log. GitLab CI (like Travis) has no archived property file; Igor ignores the file
   * name and always scrapes {@code SPINNAKER_PROPERTY_*} lines from the log. Defaulting it here
   * lets property read-back work without the user supplying a meaningless value, matching {@code
   * TravisStage}.
   */
  static final String GITLAB_CI_PROPERTY_FILE = "gitlab-ci";

  public GitlabCiStage(StopBuildJobTask stopBuildJobTask) {
    super(stopBuildJobTask);
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    // Default the property file to the sentinel when the user has not supplied one. The Deck stage
    // sends propertyFile as an empty string (present-but-blank) rather than omitting it, so we must
    // treat empty as "not supplied" here -- putIfAbsent alone would not overwrite "".
    Object propertyFile = stage.getContext().get("propertyFile");
    if (propertyFile == null || propertyFile.toString().isEmpty()) {
      stage.getContext().put("propertyFile", GITLAB_CI_PROPERTY_FILE);
    }
    super.taskGraph(stage, builder);
  }
}
