/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.controller;

import com.google.common.base.Strings;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudImage;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexImageProvider;
import groovy.util.logging.Slf4j;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/yandex/images")
public class YandexImageController {
  private final YandexImageProvider yandexImageProvider;

  @Autowired
  private YandexImageController(YandexImageProvider yandexImageProvider) {
    this.yandexImageProvider = yandexImageProvider;
  }

  @RequestMapping(value = "/find", method = RequestMethod.GET)
  public List<YandexImage> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String account,
      HttpServletRequest request) {
    return (Strings.isNullOrEmpty(account)
            ? yandexImageProvider.getAll()
            : yandexImageProvider.findByAccount(account))
        .stream()
            .map(YandexImageController::convertToYandexImage)
            .filter(getQueryFilter(q))
            .filter(getTagFilter(request))
            .sorted(Comparator.comparing(YandexImage::getImageName))
            .collect(Collectors.toList());
  }

  private static YandexImage convertToYandexImage(YandexCloudImage image) {
    return new YandexImage(
        image.getId(), image.getName(), image.getRegion(), image.getCreatedAt(), image.getLabels());
  }

  private Predicate<YandexImage> getQueryFilter(String q) {
    if (q == null || q.trim().length() <= 0) {
      return yandexImage -> true;
    }
    String glob = q.trim();
    // Wrap in '*' if there are no glob-style characters in the query string.
    if (!glob.contains("*") && !glob.contains("?") && !glob.contains("[") && !glob.contains("\\")) {
      glob = "*" + glob + "*";
    }
    Pattern pattern = new InMemoryCache.Glob(glob).toPattern();
    return i -> pattern.matcher(i.imageName).matches();
  }

  private Predicate<YandexImage> getTagFilter(HttpServletRequest request) {
    Map<String, String> tagFilters = extractTagFilters(request);
    if (tagFilters.isEmpty()) {
      return i -> true;
    }
    return i -> matchesTagFilters(i, tagFilters);
  }

  private static boolean matchesTagFilters(YandexImage image, Map<String, String> tagFilters) {
    Map<String, String> tags = image.getTags();
    return tagFilters.keySet().stream()
        .allMatch(
            tag ->
                tags.containsKey(tag.toLowerCase())
                    && tags.get(tag.toLowerCase()).equalsIgnoreCase(tagFilters.get(tag)));
  }

  private static Map<String, String> extractTagFilters(HttpServletRequest httpServletRequest) {
    return Collections.list(httpServletRequest.getParameterNames()).stream()
        .filter(Objects::nonNull)
        .filter(name -> name.toLowerCase().startsWith("tag:"))
        .collect(
            Collectors.toMap(
                tagParameter -> tagParameter.replaceAll("tag:", "").toLowerCase(),
                httpServletRequest::getParameter,
                (a, b) -> b));
  }

  /** Used in deck and orca, probably better rename in <code>YandexCloudImage</code> */
  @Data
  @AllArgsConstructor
  public static class YandexImage {
    String imageId;
    String imageName;
    String region;
    Long createdAt;
    Map<String, String> tags;
  }
}
