/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.docker.registry.controllers;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys;
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProviderUtils;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

public abstract class AbstractDockerRegistryLookupController {
  @Autowired protected Cache cacheView;

  @Autowired protected AccountCredentialsProvider accountCredentialsProvider;

  /**
   * Get tags for a specific account and repository
   *
   * @param account The account to get tags for
   * @param repository The repository to get tags for
   * @return A list of tags
   */
  @RequestMapping(value = "/tags", method = RequestMethod.GET)
  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  public List<String> getTags(
      @RequestParam("account") String account, @RequestParam("repository") String repository) {
    DockerRegistryNamedAccountCredentials credentials =
        (DockerRegistryNamedAccountCredentials) accountCredentialsProvider.getCredentials(account);
    if (credentials == null) {
      return Collections.emptyList();
    }

    Set<CacheData> matchingItemsSet =
        DockerRegistryProviderUtils.getAllMatchingKeyPattern(
            cacheView, getTaggedImageNamespace(), getTaggedImageKey(account, repository, "*"));

    // Convert Set to List for sorting
    List<CacheData> matchingItems = new ArrayList<>(matchingItemsSet);

    matchingItems.sort(
        (a, b) -> {
          if (credentials.getSortTagsByDate()) {
            return ((Comparable) b.getAttributes().get("date"))
                .compareTo(a.getAttributes().get("date"));
          } else {
            return a.getId().compareTo(b.getId());
          }
        });

    return matchingItems.stream()
        .map(
            it -> {
              Map<String, String> parse = Keys.parse(it.getId());
              return parse != null ? parse.get("tag") : null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Find images based on lookup options
   *
   * @param lookupOptions The options to use for lookup
   * @return A list of images
   */
  @RequestMapping(value = "/find", method = RequestMethod.GET)
  @PostFilter("hasPermission(filterObject['account'], 'ACCOUNT', 'READ')")
  public List<Map<String, Object>> find(LookupOptions lookupOptions) {
    String account = lookupOptions.getAccount() != null ? lookupOptions.getAccount() : "";

    Set<CacheData> images;
    if (lookupOptions.getQ() != null && !lookupOptions.getQ().isEmpty()) {
      String key = getImageIdKey("*" + lookupOptions.getQ() + "*");
      Set<CacheData> keyDataSet =
          DockerRegistryProviderUtils.getAllMatchingKeyPattern(
              cacheView, Keys.Namespace.IMAGE_ID.getNs(), key);

      // Convert Set to List for sorting
      List<CacheData> keyData = new ArrayList<>(keyDataSet);

      if (!account.isEmpty()) {
        String finalAccount = account;
        keyData =
            keyData.stream()
                .filter(data -> finalAccount.equals(data.getAttributes().get("account")))
                .collect(Collectors.toList());
      }

      Collection<String> keys =
          keyData.stream()
              .map(data -> (String) data.getAttributes().get("tagKey"))
              .collect(Collectors.toList());

      images =
          new HashSet<>(
              DockerRegistryProviderUtils.loadResults(cacheView, getTaggedImageNamespace(), keys));
    } else {
      String image = "*";
      String tag = "*";
      account = !account.isEmpty() ? account : "*";

      String key = getTaggedImageKey(account, image, tag);

      // without trackDigests, all information is available in the image keys, so don't bother
      // fetching attributes
      if (isTrackDigestsDisabled()) {
        return listAllImagesWithoutDigests(key, lookupOptions);
      }

      images =
          new HashSet<>(
              DockerRegistryProviderUtils.getAllMatchingKeyPattern(
                  cacheView, getTaggedImageNamespace(), key));
    }

    if (lookupOptions.getCount() != null && lookupOptions.getCount() > 0) {
      images = images.stream().limit(lookupOptions.getCount()).collect(Collectors.toSet());
    }

    List<Map<String, Object>> result = new ArrayList<>();
    for (CacheData image : images) {
      DockerRegistryNamedAccountCredentials credentials =
          (DockerRegistryNamedAccountCredentials)
              accountCredentialsProvider.getCredentials(
                  (String) image.getAttributes().get("account"));
      if (credentials == null) {
        continue;
      }

      Map<String, String> parse = Keys.parse(image.getId());
      if (parse == null) {
        continue;
      }

      String repo = parse.get("repository");
      String tag = parse.get("tag");

      // if the request asks for specific repositories or tags,
      // do the filtering accordingly
      if ((lookupOptions.getRepository() != null && !lookupOptions.getRepository().equals(repo))
          || (lookupOptions.getRepository() != null
              && lookupOptions.getTag() != null
              && !lookupOptions.getTag().equals(tag))) {
        continue;
      }

      Map<String, Object> resultItem = new HashMap<>();
      resultItem.put("repository", repo);
      resultItem.put("tag", tag);
      resultItem.put("account", image.getAttributes().get("account"));
      resultItem.put("registry", credentials.getRegistry());
      resultItem.put("digest", image.getAttributes().get("digest"));

      if (Boolean.TRUE.equals(lookupOptions.getIncludeDetails())) {
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) image.getAttributes().get("labels");
        resultItem.put("commitId", labels != null ? labels.get("commitId") : null);
        resultItem.put("buildNumber", labels != null ? labels.get("buildNumber") : null);
        resultItem.put("branch", labels != null ? labels.get("branch") : null);
        resultItem.put("artifact", generateArtifact(credentials.getRegistry(), repo, tag, labels));
      } else {
        resultItem.put("artifact", generateArtifact(credentials.getRegistry(), repo, tag));
      }

      result.add(resultItem);
    }

    return result;
  }

  /**
   * Generate an artifact map with default values
   *
   * @param registry The registry
   * @param repository The repository
   * @param tag The tag
   * @return The artifact map
   */
  public Map<String, Object> generateArtifact(String registry, Object repository, Object tag) {
    return generateArtifact(registry, repository, tag, new HashMap<>());
  }

  /**
   * Generate an artifact map with the given labels
   *
   * @param registry The registry
   * @param repository The repository
   * @param tag The tag
   * @param labels The labels
   * @return The artifact map
   */
  public abstract Map<String, Object> generateArtifact(
      String registry, Object repository, Object tag, Object labels);

  /**
   * List all images without digests
   *
   * @param key The key to use for filtering
   * @param lookupOptions The lookup options
   * @return A list of images
   */
  protected List<Map<String, Object>> listAllImagesWithoutDigests(
      String key, LookupOptions lookupOptions) {
    Collection<String> images = cacheView.filterIdentifiers(getTaggedImageNamespace(), key);
    if (lookupOptions.getCount() != null && lookupOptions.getCount() > 0) {
      images = images.stream().limit(lookupOptions.getCount()).collect(Collectors.toList());
    }

    List<Map<String, Object>> result = new ArrayList<>();
    for (String imageId : images) {
      Map<String, String> parse = Keys.parse(imageId);
      if (parse == null) {
        continue;
      }

      DockerRegistryNamedAccountCredentials credentials =
          (DockerRegistryNamedAccountCredentials)
              accountCredentialsProvider.getCredentials(parse.get("account"));
      if (credentials == null) {
        continue;
      }

      Map<String, Object> resultItem = new HashMap<>();
      resultItem.put("repository", parse.get("repository"));
      resultItem.put("tag", parse.get("tag"));
      resultItem.put("account", parse.get("account"));
      resultItem.put("registry", credentials.getRegistry());
      resultItem.put(
          "artifact",
          generateArtifact(credentials.getRegistry(), parse.get("repository"), parse.get("tag")));

      result.add(resultItem);
    }

    return result;
  }

  /**
   * Get the key for a tagged image
   *
   * @param account The account
   * @param repository The repository
   * @param tag The tag
   * @return The key
   */
  protected abstract String getTaggedImageKey(String account, String repository, String tag);

  /**
   * Get the key for an image ID
   *
   * @param pattern The pattern
   * @return The key
   */
  protected abstract String getImageIdKey(String pattern);

  /**
   * Get the namespace for tagged images
   *
   * @return The namespace
   */
  protected abstract String getTaggedImageNamespace();

  /**
   * Check if track digests is disabled
   *
   * @return True if track digests is disabled
   */
  protected boolean isTrackDigestsDisabled() {
    return false;
  }

  /** Lookup options for finding images */
  public static class LookupOptions {
    private String q;
    private String account;
    private String repository;
    private String tag;
    private Integer count;
    private Boolean includeDetails;

    public String getQ() {
      return q;
    }

    public void setQ(String q) {
      this.q = q;
    }

    public String getAccount() {
      return account;
    }

    public void setAccount(String account) {
      this.account = account;
    }

    public String getRepository() {
      return repository;
    }

    public void setRepository(String repository) {
      this.repository = repository;
    }

    public String getTag() {
      return tag;
    }

    public void setTag(String tag) {
      this.tag = tag;
    }

    public Integer getCount() {
      return count;
    }

    public void setCount(Integer count) {
      this.count = count;
    }

    public Boolean getIncludeDetails() {
      return includeDetails;
    }

    public void setIncludeDetails(Boolean includeDetails) {
      this.includeDetails = includeDetails;
    }
  }
}
