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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.ecs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageFinder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Component
public class EcsImageFinder implements ImageFinder {
  @Autowired
  OortService oortService;

  @Autowired
  ObjectMapper objectMapper;

  @Override
  public Collection<ImageDetails> byTags(Stage stage, String packageName, Map<String, String> tags) {
    StageData stageData = (StageData) stage.mapTo(StageData.class);

    List<Map> result = oortService.findImage(getCloudProvider(),
      (String) stage.getContext().get("imageLabelOrSha"),
      null,
      null,
      null);

    List<EcsImage> allMatchedImages = result
      .stream()
      .map(image -> objectMapper.convertValue(image, EcsImage.class))
      .sorted()
      .collect(Collectors.toList());

    HashSet<ImageDetails> response = Sets.newHashSet(allMatchedImages.get(0).toImageDetails());
    return response;
  }

  @Override
  public String getCloudProvider() {
    return "ecs";
  }

  static Map<String, String> prefixTags(Map<String, String> tags) {
    return tags.entrySet()
      .stream()
      .collect(toMap(entry -> "tag:" + entry.getKey(), Map.Entry::getValue));
  }

  static class StageData {
    @JsonProperty
    Collection<String> regions;
  }

  public static class EcsImage implements Comparable<EcsImage> {
    @JsonProperty
    String imageName;

    @JsonProperty
    Map<String, Object> attributes;

    @JsonProperty
    Map<String, Map<String, String>> tagsByImageId;

    @JsonProperty
    Map<String, List<String>> amis;

    @JsonProperty
    String region;

    ImageDetails toImageDetails() {
      String imageId;

      if (getEcrRepositoryResult() != null) {
        imageId = getEcrImageId();
      } else {
        imageId = imageName;
      }



      return new EcsImageDetails(
        imageId, imageName, region, new JenkinsDetails("host", "name", "1337001")
      );
    }

    private List<String> getEcrRepositoryResult() {
      return amis.get(region);
    }

    private String getEcrImageId() {
      return getEcrRepositoryResult().get(0);
    }

    @Override
    public int compareTo(EcsImage o) {
      if (attributes.get("creationDate") == null) {
        return 1;
      }

      if (o.attributes.get("creationDate") == null) {
        return -1;
      }

      // a lexicographic sort is sufficient given that `creationDate` is ISO 8601
      return o.attributes.get("creationDate").toString().compareTo(attributes.get("creationDate").toString());
    }
  }

  private static class EcsImageDetails extends HashMap<String, Object> implements ImageDetails {
    EcsImageDetails(String imageName, String imageId, String region, JenkinsDetails jenkinsDetails) {
      put("imageName", imageName);
      put("imageId", imageId);

      put("ami", imageId);

      put("region", region);

      put("jenkins", jenkinsDetails);
    }

    @Override
    public String getImageId() {
      return (String) super.get("imageId");
    }

    @Override
    public String getImageName() {
      return (String) super.get("imageName");
    }

    @Override
    public String getRegion() {
      return (String) super.get("region");
    }

    @Override
    public JenkinsDetails getJenkins() {
      return (JenkinsDetails) super.get("jenkins");
    }
  }
}
