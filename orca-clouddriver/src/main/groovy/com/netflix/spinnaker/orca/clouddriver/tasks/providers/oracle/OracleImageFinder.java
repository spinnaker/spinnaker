/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.oracle;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageFinder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OracleImageFinder implements ImageFinder {
  private static final Logger log = LoggerFactory.getLogger(OracleImageFinder.class);

  private final OortService oortService;

  private final ObjectMapper objectMapper;

  @Autowired
  public OracleImageFinder(OortService oortService, ObjectMapper objectMapper) {
    this.oortService = oortService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Collection<ImageDetails> byTags(Stage stage, String packageName,
                                         Map<String, String> freeformTags) {

    StageData stageData = (StageData) stage.mapTo(StageData.class);

    List<OracleImage> allMatchedImages = oortService
      .findImage(getCloudProvider(), packageName, null, null, prefixTags(freeformTags))
      .stream()
      .map(imageAsMap -> objectMapper.convertValue(imageAsMap, OracleImage.class))
      .sorted()
      .collect(Collectors.toList());

    // For each region, find the most recent matching image for that region. Note: sort order of
    // images is not defined by the OCI SDK, so we sorted them above
    return stageData.regions.stream().map(stageDataRegion -> allMatchedImages.stream()
      .filter(image -> stageDataRegion.equalsIgnoreCase(image.getRegion())).findFirst().orElse(null))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  static Map<String, String> prefixTags(Map<String, String> tags) {
    return tags.entrySet().stream()
      .collect(Collectors.toMap(tagEntry -> "tag:" + tagEntry.getKey(), tagEntry -> tagEntry.getValue()));
  }

  @Override
  public String getCloudProvider() {
    return "oracle";
  }

  static class StageData {
    @JsonProperty
    Collection<String> regions;
  }

  static class OracleImage implements Comparable<OracleImage>, ImageDetails {
    @JsonProperty
    String id;

    @JsonProperty
    String name;

    @JsonProperty
    String region;

    @JsonProperty
    String account;

    @JsonProperty
    String cloudProvider;

    @JsonProperty
    List<String> compatibleShapes;

    @JsonProperty
    Map<String, String> freeformTags;

    @JsonProperty
    long timeCreated; //clouddriver returns this in milliseconds since epoch format

    @Override
    public int compareTo(OracleImage o) {
      if (this.timeCreated == 0) return 1;
      if (o.timeCreated == 0) return -1;
      //we need descending order, so compare other to this
      return Long.valueOf(o.timeCreated).compareTo(this.timeCreated);
    }

    @Override
    public String getImageId() {
      return id;
    }

    @Override
    public String getImageName() {
      return name;
    }

    @Override
    public JenkinsDetails getJenkins() {
      return null;
    }

    @Override
    public String getRegion() {
      return region;
    }
  }
}
