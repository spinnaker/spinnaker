/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.image;

import com.netflix.spinnaker.orca.clouddriver.tasks.image.DeleteImageTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.MonitorDeleteImageTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.Set;

@Component
public class DeleteImageStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@NotNull Stage stage, @NotNull TaskNode.Builder builder) {
    builder
      .withTask("deleteImage", DeleteImageTask.class);
  }

  public static class DeleteImageRequest {
    @NotNull
    private String credentials;

    @NotNull
    private String cloudProvider;

    @NotNull
    private String region;

    @NotNull
    private Set<String> imageIds;

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

    public Set<String> getImageIds() {
      return imageIds;
    }

    public void setImageIds(Set<String> imageIds) {
      this.imageIds = imageIds;
    }
  }
}
