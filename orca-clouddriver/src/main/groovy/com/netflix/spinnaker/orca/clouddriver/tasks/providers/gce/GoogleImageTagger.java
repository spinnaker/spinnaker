/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageTagger;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Component
public class GoogleImageTagger extends ImageTagger implements CloudProviderAware {
  private static final Logger log = LoggerFactory.getLogger(GoogleImageTagger.class);
  private static final Set<String> BUILT_IN_TAGS = new HashSet<>(Arrays.asList("appversion", "build_host"));

  @Autowired
  public GoogleImageTagger(OortService oortService, ObjectMapper objectMapper) {
    super(oortService, objectMapper, log);
  }

  @Override
  public ImageTagger.OperationContext getOperationContext(Stage stage) {
    StageData stageData = (StageData) stage.mapTo(StageData.class);

    Collection<MatchedImage> matchedImages = findImages(stageData.imageNames, stage, MatchedImage.class);

    stageData.imageNames = matchedImages.stream()
      .map(matchedImage -> matchedImage.imageName)
      .collect(Collectors.toSet());

    // Built-in tags are not updatable
    Map<String, String> tags = stageData.tags.entrySet().stream()
      .filter(entry -> !BUILT_IN_TAGS.contains(entry.getKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    List<Image> targetImages = new ArrayList<>();
    Map<String, Object> originalTags = new HashMap<>();
    List<Map<String, Map>> operations = new ArrayList<>();

    for (MatchedImage matchedImage : matchedImages) {
      Image targetImage = new Image(
        matchedImage.imageName,
        getCredentials(stage),
        null,
        tags
      );
      targetImages.add(targetImage);

      log.info(format("Tagging '%s' with '%s' (executionId: %s)", targetImage.imageName, targetImage.tags, stage.getExecution().getId()));

      // Update the tags on the image
      operations.add(
        ImmutableMap.<String, Map>builder()
          .put(OPERATION, ImmutableMap.builder()
            .put("imageName", targetImage.imageName)
            .put("tags", targetImage.tags)
            .put("credentials", targetImage.account)
            .build()
          ).build()
      );

      originalTags.put(matchedImage.imageName, matchedImage.tags);
    }

    Map<String, Object> extraOutput = objectMapper.convertValue(stageData, Map.class);
    extraOutput.put("targets", targetImages);
    extraOutput.put("originalTags", originalTags);

    return new ImageTagger.OperationContext(operations, extraOutput);
  }

  /**
   * Return true iff the tags on the current machine image match the desired.
   */
  @Override
  public boolean areImagesTagged(Collection<Image> targetImages, Stage stage) {
    Collection<MatchedImage> matchedImages = findImages(
      targetImages.stream().map(targetImage -> targetImage.imageName).collect(Collectors.toSet()),
      stage,
      MatchedImage.class
    );

    AtomicBoolean isUpserted = new AtomicBoolean(true);

    for (Image targetImage : targetImages) {
      MatchedImage matchedImage = matchedImages.stream()
        .filter(m -> m.imageName.equals(targetImage.imageName))
        .findFirst()
        .orElse(null);

      if (matchedImage == null) {
        isUpserted.set(false);
      } else {
        targetImage.tags.entrySet().forEach(entry -> {
          // assert tag equality
          isUpserted.set(isUpserted.get() && entry.getValue().equals(matchedImage.tags.get(entry.getKey())));
        });
      }
    }

    return isUpserted.get();
  }

  @Override
  public String getCloudProvider() {
    return "gce";
  }

  static class StageData {
    public Set<String> imageNames;
    public Map<String, String> tags = new HashMap<>();
  }

  private static class MatchedImage {
    public String account;
    public String imageName;
    public Map<String, String> tags = new HashMap<>();
  }
}
