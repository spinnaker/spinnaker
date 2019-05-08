/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.config.AwsConfiguration;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonImage;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonServerGroup;
import com.netflix.spinnaker.clouddriver.model.Image;
import com.netflix.spinnaker.clouddriver.model.ImageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;


@Component
public class AmazonImageProvider implements ImageProvider {

  private final Cache cacheView;
  private final AwsConfiguration.AmazonServerGroupProvider amazonServerGroupProvider;
  private final ObjectMapper objectMapper;

  @Autowired
  AmazonImageProvider(Cache cacheView, AwsConfiguration.AmazonServerGroupProvider amazonServerGroupProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.amazonServerGroupProvider = amazonServerGroupProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<Image> getImageById(String imageId) {

    if (!imageId.startsWith("ami-")) {
      throw new RuntimeException("Image Id provided (" + imageId + ") is not a valid id for the provider " + getCloudProvider());
    }

    List<String> imageIdList = new ArrayList<>(cacheView.filterIdentifiers(IMAGES.toString(), "*" + imageId));

    if (imageIdList.isEmpty()) {
      return Optional.empty();
    }

    List<CacheData> imageCacheList = new ArrayList<>(cacheView.getAll(IMAGES.toString(), imageIdList));

    AmazonImage image = objectMapper.convertValue(imageCacheList.get(0).getAttributes(), AmazonImage.class);

    image.setRegion(Keys.parse(imageCacheList.get(0).getId()).get("region"));

    List<AmazonServerGroup> serverGroupList = imageCacheList.stream()
        .filter(imageCache -> imageCache.getRelationships().get(SERVER_GROUPS.toString()) != null)
        .map(imageCache -> imageCache.getRelationships().get(SERVER_GROUPS.toString()))
        .flatMap(Collection::stream)
        .map(this::getServerGroupData)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    image.setServerGroups(serverGroupList);
    return Optional.of(image);
  }

  @Override
  public String getCloudProvider() {
    return AmazonCloudProvider.ID;
  }

  private AmazonServerGroup getServerGroupData(String serverGroupCacheKey) {
    Map<String, String> parsedServerGroupKey = Keys.parse(serverGroupCacheKey);
    return amazonServerGroupProvider.getServerGroup(parsedServerGroupKey.get("account"), parsedServerGroupKey.get("region"), parsedServerGroupKey.get("serverGroup"));
  }
}
