/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Task;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Task.State;
import com.netflix.spinnaker.clouddriver.model.JobState;
import com.netflix.spinnaker.clouddriver.model.JobStatus;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@JsonDeserialize(builder = CloudFoundryJobStatus.CloudFoundryJobStatusBuilder.class)
public class CloudFoundryJobStatus implements JobStatus {
  @Nullable private String name;

  private String account;

  private String id;

  private String location;

  private final String provider = CloudFoundryCloudProvider.ID;

  private JobState jobState;

  private Long createdTime;

  @Nullable private Long completedTime;

  @Override
  public Map<String, ? extends Serializable> getCompletionDetails() {
    return Collections.emptyMap();
  }

  public static CloudFoundryJobStatus fromTask(Task task, String account, String location) {
    State state = task.getState();
    CloudFoundryJobStatusBuilder builder = CloudFoundryJobStatus.builder();
    switch (state) {
      case FAILED:
        builder.jobState(JobState.Failed);
        builder.completedTime(task.getUpdatedAt().toInstant().toEpochMilli());
        break;
      case RUNNING:
        builder.jobState(JobState.Running);
        break;
      case SUCCEEDED:
        builder.jobState(JobState.Succeeded);
        builder.completedTime(task.getUpdatedAt().toInstant().toEpochMilli());
        break;
      default:
        builder.jobState(JobState.Unknown);
    }
    return builder
        .name(task.getName())
        .account(account)
        .id(task.getGuid())
        .location(location)
        .createdTime(task.getCreatedAt().toInstant().toEpochMilli())
        .build();
  }
}
