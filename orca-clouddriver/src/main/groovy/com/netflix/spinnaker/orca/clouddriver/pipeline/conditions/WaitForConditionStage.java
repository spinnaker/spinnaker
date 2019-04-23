/*
 * Copyright 2019 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.conditions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.clouddriver.tasks.conditions.EvaluateConditionTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

@Component
public class WaitForConditionStage implements StageDefinitionBuilder {
  public static String STAGE_TYPE = "waitForCondition";

  @Override
  public void taskGraph(@NotNull Stage stage, @NotNull TaskNode.Builder builder) {
    builder.withTask(STAGE_TYPE, EvaluateConditionTask.class);
  }

  public static final class WaitForConditionContext {
    private Status status;
    private String region;
    private String cluster;
    private String account;

    @JsonCreator
    public WaitForConditionContext(
      @JsonProperty("status") Status status,
      @JsonProperty("region") @Nullable String region,
      @JsonProperty("cluster") @Nullable String cluster,
      @JsonProperty("account") @Nullable String account
    ) {
      this.status = status;
      this.region = region;
      this.cluster = cluster;
      this.account = account;
    }

    public enum Status {
      SKIPPED, WAITING, ERROR
    }

    public Status getStatus() {
      return status;
    }

    public void setStatus(Status status) {
      this.status = status;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getCluster() {
      return cluster;
    }

    public void setCluster(String cluster) {
      this.cluster = cluster;
    }

    public String getAccount() {
      return account;
    }

    public void setAccount(String account) {
      this.account = account;
    }
  }
}
