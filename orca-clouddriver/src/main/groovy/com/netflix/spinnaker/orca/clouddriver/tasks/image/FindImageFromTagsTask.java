/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.image;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FindImageFromTagsTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  List<ImageFinder> imageFinders;

  @Value("${tasks.findImageFromTagsTimeoutMillis:600000}")
  private Long findImageFromTagsTimeoutMillis;

  @Override
  public TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage);
    ImageFinder imageFinder = imageFinders.stream()
      .filter(it -> it.getCloudProvider().equals(cloudProvider))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("ImageFinder not found for cloudProvider " + cloudProvider));

    StageData stageData = (StageData) stage.mapTo(StageData.class);
    Collection<ImageFinder.ImageDetails> imageDetails = imageFinder.byTags(stage, stageData.packageName, stageData.tags);

    if (imageDetails == null || imageDetails.isEmpty()) {
      throw new IllegalStateException("Could not find tagged image for package: " + stageData.packageName + " and tags: " + stageData.tags);
    }

    List<Artifact> artifacts = new ArrayList<>();
    imageDetails.forEach(imageDetail -> artifacts.add(generateArtifactFrom(imageDetail, cloudProvider)));

    Map<String, Object> stageOutputs = new HashMap<>();
    stageOutputs.put("amiDetails", imageDetails);
    stageOutputs.put("artifacts", artifacts);

    return new TaskResult(
      ExecutionStatus.SUCCEEDED,
      stageOutputs,
      Collections.singletonMap("deploymentDetails", imageDetails)
    );

  }

  private Artifact generateArtifactFrom(ImageFinder.ImageDetails imageDetails, String cloudProvider) {
    Map<String, Object> metadata = new HashMap<>();
    try {
        ImageFinder.JenkinsDetails jenkinsDetails = imageDetails.getJenkins();
        metadata.put("build_info_url", jenkinsDetails.get("host"));
        metadata.put("build_number", jenkinsDetails.get("number"));
    } catch (Exception e) {
      // This is either all or nothing
    }

    Artifact artifact = new Artifact();
    artifact.setName(imageDetails.getImageName());
    artifact.setReference(imageDetails.getImageId());
    artifact.setLocation(imageDetails.getRegion());
    artifact.setType(cloudProvider + "/image");
    artifact.setMetadata(metadata);
    artifact.setUuid(UUID.randomUUID().toString());

    return artifact;
  }

  @Override
  public long getBackoffPeriod() {
    return 10000;
  }

  @Override
  public long getTimeout() {
    return this.findImageFromTagsTimeoutMillis;
  }

  static class StageData {
    @JsonProperty
    String packageName;

    @JsonProperty
    Map<String, String> tags;
  }
}
