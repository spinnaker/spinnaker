/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.alicloud.controllers;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import groovy.util.logging.Slf4j;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/alicloud/images")
public class AliCloudImageController {

  private final Cache cacheView;

  @Autowired
  public AliCloudImageController(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @RequestMapping(value = "/find", method = RequestMethod.GET)
  List<Image> list(LookupOptions lookupOptions, HttpServletRequest request) {
    String glob = lookupOptions.getQ();
    if (StringUtils.isAllBlank(glob) && glob.length() < 3) {
      throw new InvalidRequestException("Lost search condition or length less 3");
    }
    glob = "*" + glob + "*";
    String imageSearchKey =
        Keys.getImageKey(
            glob,
            StringUtils.isAllBlank(lookupOptions.account) ? "*" : lookupOptions.account,
            StringUtils.isAllBlank(lookupOptions.region) ? "*" : lookupOptions.region);
    Collection<String> imageIdentifiers = cacheView.filterIdentifiers(IMAGES.ns, imageSearchKey);
    Collection<CacheData> images = cacheView.getAll(IMAGES.ns, imageIdentifiers, null);
    String nameKey =
        Keys.getNamedImageKey(
            StringUtils.isAllBlank(lookupOptions.account) ? "*" : lookupOptions.account, glob);
    Collection<String> nameImageIdentifiers = cacheView.filterIdentifiers(NAMED_IMAGES.ns, nameKey);
    Collection<CacheData> nameImages =
        cacheView.getAll(NAMED_IMAGES.ns, nameImageIdentifiers, null);
    return filter(render(nameImages, images), extractTagFilters(request));
  }

  private static List<Image> filter(List<Image> namedImages, Map<String, String> tagFilters) {
    if (tagFilters.isEmpty()) {
      return namedImages;
    }
    List<Image> filter = new ArrayList<>();
    for (Image namedImage : namedImages) {
      if (checkInclude(namedImage, tagFilters)) {
        filter.add(namedImage);
      }
    }
    return filter;
  }

  /*  private static boolean checkInclude(Image image, Map<String, String> tagFilters) {
    boolean flag = false;
    List<Map> tags = (List) image.getAttributes().get("tags");
    if (tags != null) {
      for (Map tag : tags) {
        String tagKey = tag.get("tagKey").toString();
        String tagValue = tag.get("tagValue").toString();
        if (StringUtils.isNotEmpty(tagFilters.get(tagKey))
          && tagFilters.get(tagKey).equalsIgnoreCase(tagValue)) {
          flag = true;
        } else {
          flag = false;
          break;
        }
      }
    }
    return flag;
  }*/

  private static boolean checkInclude(Image image, Map<String, String> tagFilters) {
    boolean flag = false;
    List<Map> tags = (List) image.getAttributes().get("tags");
    Map<String, String> imageMap = new HashMap<>(tags.size());
    for (Map tag : tags) {
      imageMap.put(tag.get("tagKey").toString(), tag.get("tagValue").toString());
    }
    for (Map.Entry<String, String> entry : tagFilters.entrySet()) {
      String tagKey = entry.getKey();
      String tagValue = entry.getValue();
      if (StringUtils.isNotEmpty(imageMap.get(tagKey))
          && imageMap.get(tagKey).equalsIgnoreCase(tagValue)) {
        flag = true;
      } else {
        flag = false;
        break;
      }
    }
    return flag;
  }

  private List<Image> render(Collection<CacheData> namedImages, Collection<CacheData> images) {
    List<Image> list = new ArrayList<>();
    for (CacheData image : images) {
      Map<String, Object> attributes = image.getAttributes();
      list.add(new Image(String.valueOf(attributes.get("imageName")), attributes));
    }

    for (CacheData nameImage : namedImages) {
      Map<String, Object> attributes = nameImage.getAttributes();
      list.add(new Image(String.valueOf(attributes.get("imageName")), attributes));
    }
    return list;
  }

  private static Map<String, String> extractTagFilters(HttpServletRequest request) {
    Map<String, String> parameters = new HashMap<>(16);
    Enumeration<String> parameterNames = request.getParameterNames();
    while (parameterNames.hasMoreElements()) {
      String parameterName = parameterNames.nextElement();
      if (parameterName.toLowerCase().startsWith("tag:")) {
        parameters.put(
            parameterName.replaceAll("tag:", "").toLowerCase(),
            request.getParameter(parameterName));
      }
    }
    return parameters;
  }

  @Data
  public static class Image {
    public Image(String imageName, Map<String, Object> attributes) {
      this.imageName = imageName;
      this.attributes = attributes;
    }

    String imageName;
    Map<String, Object> attributes;
  }

  @Data
  static class LookupOptions {
    String q;
    String account;
    String region;
  }

  @Data
  static class Tag {
    String tagKey;
    String tagValue;
  }
}
