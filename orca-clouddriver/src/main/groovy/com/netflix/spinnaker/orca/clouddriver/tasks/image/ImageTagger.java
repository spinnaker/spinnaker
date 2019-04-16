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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public abstract class ImageTagger {
  protected static final String OPERATION = "upsertImageTags";

  protected final OortService oortService;
  protected final ObjectMapper objectMapper;
  protected final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * @return the OperationContext object that contains the cloud provider-specific list of operations as well as
   * cloud provider-specific output key/value pairs to be included in the task's output.
   */
  abstract protected OperationContext getOperationContext(Stage stage);

  /**
   * @return true when, according to the underlying cloud provider, image tags have been updated to match the respective target
   */
  abstract protected boolean areImagesTagged(Collection<Image> targetImages, Collection<String> consideredStageRefIds, Stage stage);

  /**
   * @return The cloud provider type that this object supports.
   */
  abstract protected String getCloudProvider();

  protected ImageTagger(OortService oortService, ObjectMapper objectMapper) {
    this.oortService = oortService;
    this.objectMapper = objectMapper;
  }

  protected Collection findImages(Collection<String> imageNames, Collection<String> consideredStageRefIds, Stage stage, Class matchedImageType) {
    List<String> upstreamImageIds = new ArrayList<>();

    if (imageNames == null || imageNames.isEmpty()) {
      imageNames = new HashSet<>();

      // attempt to find upstream images in the event that one was not explicitly provided
      upstreamImageIds.addAll(upstreamImageIds(stage, consideredStageRefIds, getCloudProvider()));
      if (upstreamImageIds.isEmpty()) {
        throw new IllegalStateException("Unable to determine source image(s)");
      }

      for (String upstreamImageId : upstreamImageIds) {
        // attempt to lookup the equivalent image name (given the upstream amiId/imageId)
        List<Map> allMatchedImages = oortService.findImage(getCloudProvider(), upstreamImageId, null, null, null);
        if (allMatchedImages.isEmpty()) {
          throw new ImageNotFound(format("No image found (imageId: %s)", upstreamImageId), true);
        }

        String upstreamImageName = (String) allMatchedImages.get(0).get("imageName");
        imageNames.add(upstreamImageName);

        log.info(format("Found upstream image '%s' (executionId: %s)", upstreamImageName, stage.getExecution().getId()));
      }
    }

    Collection foundImages = new ArrayList();

    for (String targetImageName : imageNames) {
      List<Map> allMatchedImages = oortService.findImage(getCloudProvider(), targetImageName, null, null, null);
      Map matchedImage = allMatchedImages.stream()
        .filter(image -> image.get("imageName").equals(targetImageName))
        .findFirst()
        .orElseThrow(() ->
          new ImageNotFound(format("No image found (imageName: %s)", targetImageName), !upstreamImageIds.isEmpty())
        );

      foundImages.add(objectMapper.convertValue(matchedImage, matchedImageType));
    }

    foundAllImages(upstreamImageIds, foundImages);

    return foundImages;
  }

  @VisibleForTesting
  Collection<String> upstreamImageIds(Stage sourceStage, Collection<String> consideredStageRefIds, String cloudProviderType) {
    List<Stage> ancestors = sourceStage.ancestors();
    List<Stage> imageProvidingAncestorStages = ancestors.stream()
      .filter(stage -> {
        String cloudProvider = (String) stage.getContext().getOrDefault("cloudProvider", stage.getContext().get("cloudProviderType"));
        boolean consideredStageRefIdMatches = consideredStageRefIds == null || consideredStageRefIds.isEmpty() || consideredStageRefIds.contains(stage.getRefId()) || (stage.getParent() != null && consideredStageRefIds.contains(stage.getParent().getRefId()));
        return consideredStageRefIdMatches && (stage.getContext().containsKey("imageId") || stage.getContext().containsKey("amiDetails")) && cloudProviderType.equals(cloudProvider);
      }).collect(toList());

    return imageProvidingAncestorStages.stream().map(it -> {
      if (it.getContext().containsKey("imageId")) {
        return (String) it.getContext().get("imageId");
      } else {
        return (String) ((List<Map>) it.getContext().get("amiDetails")).get(0).get("imageId");
      }
    }).collect(toList());
  }

  protected class OperationContext {
    final List<Map<String, Map>> operations;
    final Map<String, Object> extraOutput;

    public OperationContext(List<Map<String, Map>> operations, Map<String, Object> extraOutput) {
      this.operations = operations;
      this.extraOutput = extraOutput;
    }
  }

  /**
   * This method is a helper for AmazonImageTagger; Amazon images are regional with a one-to-many relationship
   * between image names (treated globally) and regional image ids. Clouddriver caches Amazon images and
   * namedImages as distinct and eventually consistent collections but findImages() uses the output of a lookup
   * by names to determine which imageIds to tag. If upstream images were baked in n regions for "myapp-1.0.0" but
   * findImage("aws", "myapp-1.0.0") returns n-1 amis as the namedImages collection for an account/region
   * is mid-update, UpsertImageTags would appear successful despite only applying tags to a subset of upstream images.
   *
   * @param upstreamImageIds list of upstream image ids
   * @param foundImages collection of cloudprovider specific MatchedImage objects
   *
   * Throws ImageNotFound with shouldRetry=true in AmazonImageTagger.foundAllImages
   */
  protected void foundAllImages(List<String> upstreamImageIds, Collection<?> foundImages) {
  }

  protected static class Image {
    public final String imageName;
    public final String account;
    public final Collection<String> regions;
    public final Map<String, String> tags;

    @JsonCreator
    public Image(@JsonProperty("imageName") String imageName,
                 @JsonProperty("account") String account,
                 @JsonProperty("regions") Collection<String> regions,
                 @JsonProperty("tags") Map<String, String> tags) {
      this.imageName = imageName;
      this.account = account;
      this.regions = regions;
      this.tags = tags;
    }
  }

  protected class ImageNotFound extends RuntimeException {
    final boolean shouldRetry;

    public ImageNotFound(String message, boolean shouldRetry) {
      super(message);
      this.shouldRetry = shouldRetry;
    }
  }
}
