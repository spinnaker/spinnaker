/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PreviousImageRollbackSupport {
  private final ObjectMapper objectMapper;
  private final OortService oortService;
  private final RetrySupport retrySupport;

  public PreviousImageRollbackSupport(ObjectMapper objectMapper,
                                      OortService oortService,
                                      RetrySupport retrySupport) {
    this.objectMapper = objectMapper.configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
    );
    this.oortService = oortService;
    this.retrySupport = retrySupport;
  }

  public ImageDetails getImageDetailsFromEntityTags(String cloudProvider,
                                                    String credentials,
                                                    String region,
                                                    String serverGroupName) {
    List<Map> entityTags = retrySupport.retry(() -> oortService.getEntityTags(
      cloudProvider,
      "serverGroup",
      serverGroupName,
      credentials,
      region
    ), 15, 2000, false);

    if (entityTags != null && entityTags.size() > 1) {
      // this should _not_ happen
      String id = String.format("%s:serverGroup:%s:%s:%s", cloudProvider, serverGroupName, credentials, region);
      throw new IllegalStateException("More than one set of entity tags found for " + id);
    }

    if (entityTags == null || entityTags.isEmpty()) {
      return null;
    }

    List<Map> tags = (List<Map>) entityTags.get(0).get("tags");
    PreviousServerGroup previousServerGroup = tags
      .stream()
      .filter(t -> "spinnaker:metadata".equalsIgnoreCase((String) t.get("name")))
      .map(t -> (Map<String, Object>)((Map)t.get("value")).get("previousServerGroup"))
      .filter(Objects::nonNull)
      .map(m -> objectMapper.convertValue(m, PreviousServerGroup.class))
      .findFirst()
      .orElse(null);

    if (previousServerGroup == null || previousServerGroup.imageName == null) {
      return null;
    }

    return new ImageDetails(
      previousServerGroup.imageId,
      previousServerGroup.imageName,
      previousServerGroup.getBuildNumber()
    );
  }

  public static class ImageDetails {
    private final String imageId;
    private final String imageName;
    private final String buildNumber;

    ImageDetails(String imageId, String imageName, String buildNumber) {
      this.imageId = imageId;
      this.imageName = imageName;
      this.buildNumber = buildNumber;
    }

    public String getImageId() {
      return imageId;
    }

    public String getImageName() {
      return imageName;
    }

    public String getBuildNumber() {
      return buildNumber;
    }
  }

  static class PreviousServerGroup {
    public String imageId;
    public String imageName;
    public BuildInfo buildInfo;

    String getBuildNumber() {
      return (buildInfo == null || buildInfo.jenkins == null) ? null : buildInfo.jenkins.number;
    }

    static class BuildInfo {
      public Jenkins jenkins;

      static class Jenkins {
        public String number;
      }
    }
  }
}
