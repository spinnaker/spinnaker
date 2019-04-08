/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.snapshot;

import java.util.Set;
import javax.validation.constraints.NotNull;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.snapshot.DeleteSnapshotTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

@Component
public class DeleteSnapshotStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@NotNull Stage stage, @NotNull TaskNode.Builder builder) {
    builder
      .withTask("deleteSnapshot", DeleteSnapshotTask.class)
      .withTask("monitorDeleteSnapshot", MonitorKatoTask.class);
  }

  public static class DeleteSnapshotRequest {
    @NotNull
    private String credentials;

    @NotNull
    private String cloudProvider;

    @NotNull
    private String region;

    @NotNull
    private Set<String> snapshotIds;

    public String getCredentials() {
      return credentials;
    }

    public void setCredentials(String credentials) {
      this.credentials = credentials;
    }

    public String getCloudProvider() {
      return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
      this.cloudProvider = cloudProvider;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public Set<String> getSnapshotIds() {
      return snapshotIds;
    }

    public void setSnapshotIds(Set<String> snapshotIds) {
      this.snapshotIds = snapshotIds;
    }

  }
}
