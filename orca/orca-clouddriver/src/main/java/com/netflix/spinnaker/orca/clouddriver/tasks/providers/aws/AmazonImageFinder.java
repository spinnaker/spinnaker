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

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.ami.AppVersion;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageFinder;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AmazonImageFinder implements ImageFinder {

  private static final int MAX_SEARCH_RESULTS = 1000;

  @Autowired OortService oortService;

  @Autowired ObjectMapper objectMapper;

  /**
   * A Predicate that passes if an AmazonImage has an ami with an appversion tag matching the
   * packageName in any requested region.
   */
  static class AppVersionFilter implements Predicate<AmazonImage> {
    private final String packageName;
    private final Set<String> regions;

    public AppVersionFilter(String packageName, Collection<String> regions) {
      this.packageName = packageName;
      this.regions = new HashSet<>(regions);
    }

    @Override
    public boolean test(AmazonImage amazonImage) {
      final Map<String, Map<String, String>> tags = amazonImage.tagsByImageId;
      if (tags == null || tags.isEmpty()) {
        return false;
      }

      return regions.stream()
          .anyMatch(
              region ->
                  amazonImage.amis.getOrDefault(region, Collections.emptyList()).stream()
                      .anyMatch(
                          ami ->
                              Optional.ofNullable(
                                      AppVersion.parseName(
                                          tags.getOrDefault(ami, Collections.emptyMap())
                                              .get("appversion")))
                                  .map(AppVersion::getPackageName)
                                  .map(packageName::equals)
                                  .orElse(false)));
    }
  }

  @Override
  public Collection<ImageDetails> byTags(
      StageExecution stage,
      String packageName,
      Map<String, String> tags,
      List<String> warningsCollector) {
    StageData stageData = (StageData) stage.mapTo(StageData.class);

    List<AmazonImage> allMatchedImages =
        Retrofit2SyncCall.execute(
                oortService.findImage(
                    getCloudProvider(),
                    packageName,
                    stageData.imageOwnerAccount,
                    null,
                    prefixTags(tags)))
            .stream()
            .map(image -> objectMapper.convertValue(image, AmazonImage.class))
            .filter(image -> image.tagsByImageId != null && image.tagsByImageId.size() != 0)
            .sorted()
            .collect(Collectors.toList());

    if (allMatchedImages.size() >= MAX_SEARCH_RESULTS) {
      warningsCollector.add(
          "Too many results matching search criteria: Consider refining the search.");
    }

    AppVersionFilter filter = new AppVersionFilter(packageName, stageData.regions);
    List<AmazonImage> appversionMatches =
        allMatchedImages.stream().filter(filter).collect(Collectors.toList());

    final List<AmazonImage> candidateImages =
        appversionMatches.isEmpty() ? allMatchedImages : appversionMatches;

    List<ImageDetails> imageDetails = new ArrayList<>();

    /*
     * For each region, find the most recently created image.
     * (optimized for readability over efficiency given the generally small # of images)
     */
    stageData.regions.forEach(
        region ->
            candidateImages.stream()
                .filter(image -> image.amis.containsKey(region))
                .findFirst()
                .map(image -> imageDetails.add(image.toAmazonImageDetails(region))));

    return imageDetails;
  }

  @Override
  public String getCloudProvider() {
    return "aws";
  }

  static Map<String, String> prefixTags(Map<String, String> tags) {
    return tags.entrySet().stream()
        .collect(toMap(entry -> "tag:" + entry.getKey(), Map.Entry::getValue));
  }

  static class StageData {
    @JsonProperty Collection<String> regions;
    public String imageOwnerAccount;
  }

  static class AmazonImage implements Comparable<AmazonImage> {
    @JsonProperty String imageName;

    @JsonProperty Map<String, Object> attributes;

    @JsonProperty Map<String, Map<String, String>> tagsByImageId;

    @JsonProperty Map<String, List<String>> amis;

    ImageDetails toAmazonImageDetails(String region) {
      String imageId = amis.get(region).get(0);

      Map<String, String> imageTags =
          Optional.ofNullable(tagsByImageId).map(it -> it.get(imageId)).orElse(emptyMap());
      AppVersion appVersion = AppVersion.parseName(imageTags.get("appversion"));
      JenkinsDetails jenkinsDetails =
          Optional.ofNullable(appVersion)
              .map(
                  av ->
                      new JenkinsDetails(
                          imageTags.get("build_host"), av.getBuildJobName(), av.getBuildNumber()))
              .orElse(null);

      return new AmazonImageDetails(imageName, imageId, region, jenkinsDetails);
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
      return o.attributes
          .get("creationDate")
          .toString()
          .compareTo(attributes.get("creationDate").toString());
    }
  }

  private static class AmazonImageDetails extends HashMap<String, Object> implements ImageDetails {
    AmazonImageDetails(
        String imageName, String imageId, String region, JenkinsDetails jenkinsDetails) {
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
