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

package com.netflix.spinnaker.clouddriver.aws.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.model.Image;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The image's own fields (imageId, name, ownerId, etc.) are stored as a flat attribute map rather
 * than a typed AWS SDK model object, and are unwrapped into/out of the top-level JSON via {@link
 * JsonAnyGetter}/{@link JsonAnySetter} -- this mirrors exactly what's already stored in the cache
 * (see ImageCachingAgent), and avoids needing a v2 SDK model type to support Jackson's
 * {@code @JsonUnwrapped}, which AWS SDK v2's immutable, builder-only model classes don't support
 * natively.
 */
@Data
@NoArgsConstructor
public class AmazonImage implements Image {
  public static final String AMAZON_IMAGE_TYPE = "aws/image";

  String region;
  List<AmazonServerGroup> serverGroups = new ArrayList<>();

  @JsonIgnore private final Map<String, Object> imageAttributes = new LinkedHashMap<>();

  @JsonAnySetter
  public void setImageAttribute(String key, Object value) {
    imageAttributes.put(key, value);
  }

  @JsonAnyGetter
  public Map<String, Object> getImageAttributes() {
    return imageAttributes;
  }

  public String getName() {
    return (String) imageAttributes.get("name");
  }

  public String getId() {
    return (String) imageAttributes.get("imageId");
  }
}
