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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.ami.AppVersion;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageFinder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Component
public class AmazonImageFinder implements ImageFinder {
  @Autowired
  OortService oortService;

  @Autowired
  ObjectMapper objectMapper;

  @Override
  public Collection<ImageDetails> byTags(Stage stage, String packageName, Map<String, String> tags) {
    StageData stageData = (StageData) stage.mapTo(StageData.class);
    List<AmazonImage> allMatchedImages =
        oortService.findImage(getCloudProvider(), packageName, null, null, prefixTags(tags)).stream()
        .map(image -> objectMapper.convertValue(image, AmazonImage.class))
        .filter(image -> image.tagsByImageId != null && image.tagsByImageId.size() != 0)
        .sorted()
        .collect(Collectors.toList());

    List<ImageDetails> imageDetails = new ArrayList<>();

    /*
     * For each region, find the most recently created image.
     * (optimized for readability over efficiency given the generally small # of images)
     */
    stageData.regions.forEach(region -> allMatchedImages.stream()
      .filter(image -> image.amis.containsKey(region))
      .findFirst()
      .map(image -> imageDetails.add(image.toAmazonImageDetails(region)))
    );

    return imageDetails;
  }

  @Override
  public String getCloudProvider() {
    return "aws";
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

  static class AmazonImage implements Comparable<AmazonImage> {
    @JsonProperty
    String imageName;

    @JsonProperty
    Map<String, Object> attributes;

    @JsonProperty
    Map<String, Map<String, String>> tagsByImageId;

    @JsonProperty
    Map<String, List<String>> amis;

    ImageDetails toAmazonImageDetails(String region) {
      String imageId = amis.get(region).get(0);

      Map<String, String> imageTags = tagsByImageId.get(imageId);
      AppVersion appVersion = AppVersion.parseName(imageTags.get("appversion"));
      JenkinsDetails jenkinsDetails = Optional
        .ofNullable(appVersion)
        .map(av -> new JenkinsDetails(imageTags.get("build_host"), av.getBuildJobName(), av.getBuildNumber()))
        .orElse(null);

      return new AmazonImageDetails(
        imageName, imageId, region, jenkinsDetails
      );
    }

    @Override
    public int compareTo(AmazonImage o) {
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

  private static class AmazonImageDetails extends HashMap<String, Object> implements ImageDetails {
    AmazonImageDetails(String imageName, String imageId, String region, JenkinsDetails jenkinsDetails) {
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
