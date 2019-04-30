/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.echo.tasks;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.echo.JiraService;
import com.netflix.spinnaker.orca.echo.JiraService.CreateJiraIssueResponse;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class CreateJiraIssueTask implements RetryableTask {
  private final JiraService jiraService;

  @Autowired
  public CreateJiraIssueTask(JiraService jiraService) {
    this.jiraService = jiraService;
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(30);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(5);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    JiraService.CreateIssueRequest createIssueRequest = stage.mapTo(JiraService.CreateIssueRequest.class);
    Optional.ofNullable(stage.getExecution().getAuthentication())
      .map(Execution.AuthenticationDetails::getUser)
      .ifPresent(createIssueRequest::setReporter);

    CreateJiraIssueResponse createJiraIssueResponse = jiraService.createJiraIssue(createIssueRequest);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(ImmutableMap.of("createJiraIssueResponse", createJiraIssueResponse)).build();
  }
}
