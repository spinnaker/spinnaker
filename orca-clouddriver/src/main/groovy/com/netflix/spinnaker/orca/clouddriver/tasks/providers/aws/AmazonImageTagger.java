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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageTagger;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class AmazonImageTagger extends ImageTagger implements CloudProviderAware {
  private static final String ALLOW_LAUNCH_OPERATION = "allowLaunchDescription";
  private static final Set<String> BUILT_IN_TAGS = new HashSet<>(
    Arrays.asList("appversion", "base_ami_version", "build_host", "creation_time", "creator")
  );

  @Value("${default.bake.account:default}")
  String defaultBakeAccount;

  @Autowired
  public AmazonImageTagger(OortService oortService, ObjectMapper objectMapper) {
    super(oortService, objectMapper);
  }

  @Override
  public ImageTagger.OperationContext getOperationContext(Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);

    Collection<MatchedImage> matchedImages = findImages(stageData.imageNames, stageData.consideredStages, stage, MatchedImage.class);
    if (stageData.regions == null || stageData.regions.isEmpty()) {
      stageData.regions = matchedImages.stream()
        .flatMap(matchedImage -> matchedImage.amis.keySet().stream())
        .collect(Collectors.toSet());
    }

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
      Collection<String> regions = matchedImage.amis.keySet();
      Image targetImage = new Image(
        matchedImage.imageName,
        defaultBakeAccount,
        regions,
        tags
      );
      targetImages.add(targetImage);

      log.info(format("Tagging '%s' with '%s' (executionId: %s)", targetImage.imageName, targetImage.tags, stage.getExecution().getId()));

      // Update the tags on the image in the `defaultBakeAccount`
      operations.add(
        ImmutableMap.<String, Map>builder()
          .put(OPERATION, ImmutableMap.builder()
            .put("amiName", targetImage.imageName)
            .put("tags", targetImage.tags)
            .put("regions", targetImage.regions)
            .put("credentials", targetImage.account)
            .build()
          ).build()
      );

      // Re-share the image in all other accounts (will result in tags being updated)
      matchedImage.accounts.stream()
        .filter(account -> !account.equalsIgnoreCase(defaultBakeAccount))
        .forEach(account -> {
          regions.forEach(region ->
            operations.add(
              ImmutableMap.<String, Map>builder()
                .put(ALLOW_LAUNCH_OPERATION, ImmutableMap.builder()
                  .put("account", account)
                  .put("credentials", defaultBakeAccount)
                  .put("region", region)
                  .put("amiName", targetImage.imageName)
                  .build()
                ).build()
            )
          );
        });


      originalTags.put(matchedImage.imageName, matchedImage.tagsByImageId);
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
  public boolean areImagesTagged(Collection<Image> targetImages, Collection<String> consideredStageRefIds, Stage stage) {
    Collection<MatchedImage> matchedImages = findImages(
      targetImages.stream().map(targetImage -> targetImage.imageName).collect(Collectors.toSet()),
      consideredStageRefIds,
      stage,
      MatchedImage.class
    );

    AtomicBoolean isUpserted = new AtomicBoolean(true);
    for (Image targetImage : targetImages) {
      targetImage.regions.forEach(region -> {
          MatchedImage matchedImage = matchedImages.stream()
            .filter(m -> m.imageName.equals(targetImage.imageName))
            .findFirst()
            .orElse(null);

          if (matchedImage == null) {
            isUpserted.set(false);
            return;
          }

          List<String> imagesForRegion = matchedImage.amis.get(region);
          imagesForRegion.forEach(image -> {
            Map<String, String> allImageTags = matchedImage.tagsByImageId.getOrDefault(image, new HashMap<>());
            targetImage.tags.entrySet().forEach(entry -> {
              // assert tag equality
              isUpserted.set(isUpserted.get() && entry.getValue().equals(allImageTags.get(entry.getKey().toLowerCase())));
            });
          });
        }
      );
    }

    return isUpserted.get();
  }

  @Override
  public String getCloudProvider() {
    return "aws";
  }

  static class StageData {
    public Set<String> imageNames;
    public Set<String> regions = new HashSet<>();
    public Map<String, String> tags = new HashMap<>();
    public Set<String> consideredStages = new HashSet<>();
  }

  private static class MatchedImage {
    public String imageName;
    public Collection<String> accounts;
    public Map<String, List<String>> amis;
    public Map<String, Map<String, String>> tagsByImageId;
  }
}
