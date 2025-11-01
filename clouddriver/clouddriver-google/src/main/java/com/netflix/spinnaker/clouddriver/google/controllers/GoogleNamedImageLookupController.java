/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.controllers;

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.IMAGES;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.compute.model.Image;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import groovy.util.logging.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/gce/images")
public class GoogleNamedImageLookupController {

  private final Cache cacheView;
  private final GsonFactory jsonMapper = new GsonFactory();
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);

  @Autowired
  private GoogleNamedImageLookupController(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @RequestMapping(value = "/find", method = RequestMethod.GET)
  public List<NamedImage> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String account,
      HttpServletRequest request) {
    Collection<CacheData> imageCacheData = getImageCacheData(account);
    Predicate<NamedImage> queryFilter = getQueryFilter(q);
    Predicate<NamedImage> tagFilter = getTagFilter(request);
    return imageCacheData.stream()
        .map(this::createNamedImageFromCacheData)
        .filter(queryFilter)
        .filter(tagFilter)
        .sorted(Comparator.comparing(image -> image.imageName))
        .collect(Collectors.toList());
  }

  private Collection<CacheData> getImageCacheData(String account) {
    // If no account supplied, return images from all accounts
    String pattern =
        (account == null || account.isEmpty())
            ? String.format("%s:images:*", GoogleCloudProvider.getID())
            : String.format("%s:images:%s:*", GoogleCloudProvider.getID(), account);
    Collection<String> identifiers = cacheView.filterIdentifiers(IMAGES.getNs(), pattern);
    return cacheView.getAll(IMAGES.getNs(), identifiers, RelationshipCacheFilter.none());
  }

  private NamedImage createNamedImageFromCacheData(CacheData cacheDatum) throws RuntimeException {
    try {
      Object hashImage = cacheDatum.getAttributes().get("image");
      Image image = jsonMapper.fromString(objectMapper.writeValueAsString(hashImage), Image.class);
      String imageAccount = Keys.parse(cacheDatum.getId()).get("account");
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("creationDate", image.get("creationTimestamp"));
      return new NamedImage(imageAccount, image.getName(), attributes, buildTagsMap(image));
    } catch (IOException e) {
      throw new RuntimeException("Image deserialization failed");
    }
  }

  private Predicate<NamedImage> getQueryFilter(String q) {
    Predicate<NamedImage> queryFilter = i -> true;
    if (q != null && q.trim().length() > 0) {
      String glob = q.trim();
      // Wrap in '*' if there are no glob-style characters in the query string.
      if (!glob.contains("*")
          && !glob.contains("?")
          && !glob.contains("[")
          && !glob.contains("\\")) {
        glob = "*" + glob + "*";
      }
      Pattern pattern = new InMemoryCache.Glob(glob).toPattern();
      queryFilter = i -> pattern.matcher(i.imageName).matches();
    }
    return queryFilter;
  }

  private Predicate<NamedImage> getTagFilter(HttpServletRequest request) {
    Predicate<NamedImage> tagFilter = i -> true;
    Map<String, String> tagFilters = extractTagFilters(request);
    if (!tagFilters.isEmpty()) {
      tagFilter = i -> matchesTagFilters(i, tagFilters);
    }
    return tagFilter;
  }

  @VisibleForTesting
  public static Map<String, String> buildTagsMap(Image image) {
    Map<String, String> tags = new HashMap<>();

    String description = image.getDescription();
    // For a description of the form:
    // key1: value1, key2: value2, key3: value3
    // we'll build a map associating each key with
    // its associated value
    if (description != null) {
      tags =
          Arrays.stream(description.split(","))
              .filter(token -> token.contains(": "))
              .map(token -> token.split(": ", 2))
              .collect(
                  Collectors.toMap(
                      token -> token[0].trim(), token -> token[1].trim(), (a, b) -> b));
    }

    Map<String, String> labels = image.getLabels();
    if (labels != null) {
      tags.putAll(labels);
    }

    return tags;
  }

  /**
   * Apply tag-based filtering to the list of named images.
   *
   * <p>For example: /gce/images/find?q=PackageName&tag:stage=released&tag:somekey=someval
   */
  private static List<NamedImage> filter(
      List<NamedImage> namedImages, Map<String, String> tagFilters) {
    return namedImages.stream()
        .filter(namedImage -> matchesTagFilters(namedImage, tagFilters))
        .collect(Collectors.toList());
  }

  private static boolean matchesTagFilters(NamedImage namedImage, Map<String, String> tagFilters) {
    Map<String, String> tags = namedImage.tags;
    return tagFilters.keySet().stream()
        .allMatch(
            tag ->
                tags.containsKey(tag.toLowerCase())
                    && tags.get(tag.toLowerCase()).equalsIgnoreCase(tagFilters.get(tag)));
  }

  private static Map<String, String> extractTagFilters(HttpServletRequest httpServletRequest) {
    List<String> parameterNames = Collections.list(httpServletRequest.getParameterNames());

    return parameterNames.stream()
        .filter(Objects::nonNull)
        .filter(parameter -> parameter.toLowerCase().startsWith("tag:"))
        .collect(
            Collectors.toMap(
                tagParameter -> tagParameter.replaceAll("tag:", "").toLowerCase(),
                httpServletRequest::getParameter,
                (a, b) -> b));
  }

  @VisibleForTesting
  public static class NamedImage {
    public String account;
    public String imageName;
    public Map<String, Object> attributes = new HashMap<>();
    public Map<String, String> tags = new HashMap<>();

    private NamedImage(
        String account,
        String imageName,
        Map<String, Object> attributes,
        Map<String, String> tags) {
      this.account = account;
      this.imageName = imageName;
      this.attributes = attributes;
      this.tags = tags;
    }
  }
}
