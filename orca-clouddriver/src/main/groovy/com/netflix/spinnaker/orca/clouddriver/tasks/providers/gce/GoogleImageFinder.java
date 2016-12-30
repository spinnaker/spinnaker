/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.ami.AppVersion;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageFinder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Component
public class GoogleImageFinder implements ImageFinder {
  @Autowired
  OortService oortService;

  @Autowired
  ObjectMapper objectMapper;

  @Override
  public Collection<ImageDetails> byTags(Stage stage, String packageName, Map<String, String> tags) {
    List<GoogleImage> allMatchedImages =
      oortService.findImage(getCloudProvider(), packageName, null, null, prefixTags(tags)).stream()
        .map(image -> objectMapper.convertValue(image, GoogleImage.class))
        .sorted()
        .collect(Collectors.toList());

    /*
     * Find the most recently created image.
     * (optimized for readability over efficiency given the generally small # of images)
     */
    if (allMatchedImages.size() > 0) {
      GoogleImage latestImage = allMatchedImages.get(0);
      return Collections.singletonList(latestImage.toGoogleImageDetails());
    } else {
      return null;
    }
  }

  @Override
  public String getCloudProvider() {
    return "gce";
  }

  static Map<String, String> prefixTags(Map<String, String> tags) {
    return tags.entrySet()
      .stream()
      .collect(toMap(entry -> "tag:" + entry.getKey(), Map.Entry::getValue));
  }

  static class GoogleImage implements Comparable<GoogleImage> {
    @JsonProperty
    String imageName;

    @JsonProperty
    Map<String, Object> attributes;

    @JsonProperty
    Map<String, String> tags;

    ImageDetails toGoogleImageDetails() {
      AppVersion appVersion = AppVersion.parseName(tags.get("appversion"));
      JenkinsDetails jenkinsDetails = Optional
        .ofNullable(appVersion)
        .map(av -> new JenkinsDetails(tags.get("build_host"), av.getBuildJobName(), av.getBuildNumber()))
        .orElse(null);

      return new GoogleImageDetails(imageName, jenkinsDetails);
    }

    @Override
    public int compareTo(GoogleImage o) {
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

  private static class GoogleImageDetails extends HashMap<String, Object> implements ImageDetails {
    GoogleImageDetails(String imageName, JenkinsDetails jenkinsDetails) {
      put("imageName", imageName);
      put("imageId", imageName);

      put("ami", imageName);
      put("amiId", imageName);

      put("region", "global");

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
    public JenkinsDetails getJenkins() {
      return (JenkinsDetails) super.get("jenkins");
    }
  }
}
