/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys;
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProviderUtils;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractDockerRegistryLookupControllerTest {

  // Concrete implementation of the abstract class for testing
  private static class TestDockerRegistryLookupController
      extends AbstractDockerRegistryLookupController {
    @Override
    public Map<String, Object> generateArtifact(
        String registry, Object repository, Object tag, Object labels) {
      Map<String, Object> artifact = new HashMap<>();
      artifact.put("type", "docker");
      artifact.put("reference", registry + "/" + repository + ":" + tag);
      artifact.put("name", repository);
      artifact.put("version", tag);

      Map<String, Object> metadata = new HashMap<>();
      metadata.put("registry", registry);

      if (labels instanceof Map) {
        metadata.put("labels", labels);
      }

      artifact.put("metadata", metadata);
      return artifact;
    }

    @Override
    protected String getTaggedImageKey(String account, String repository, String tag) {
      return Keys.getTaggedImageKey(account, repository, tag);
    }

    @Override
    protected String getImageIdKey(String pattern) {
      return Keys.getTaggedImageKey("*", "*", pattern);
    }

    @Override
    protected String getTaggedImageNamespace() {
      return Keys.Namespace.TAGGED_IMAGE.getNs();
    }
  }

  @Mock private Cache cacheView;
  @Mock private AccountCredentialsProvider accountCredentialsProvider;
  @Mock private DockerRegistryNamedAccountCredentials credentials;

  @InjectMocks private TestDockerRegistryLookupController controller;

  @BeforeEach
  void setUp() {
    when(accountCredentialsProvider.getCredentials(anyString())).thenReturn(credentials);
    when(credentials.getRegistry()).thenReturn("test-registry");
  }

  @Test
  void getTags_shouldReturnTagsForRepository() {
    // Given
    String account = "test-account";
    String repository = "test-repository";
    String taggedImageKey = Keys.getTaggedImageKey(account, repository, "*");
    String namespace = Keys.Namespace.TAGGED_IMAGE.getNs();

    CacheData cacheData1 = createTaggedImageCacheData(account, repository, "1.0", "2025-01-01");
    CacheData cacheData2 = createTaggedImageCacheData(account, repository, "2.0", "2025-01-02");
    Set<CacheData> cacheDataSet = new HashSet<>(Arrays.asList(cacheData1, cacheData2));

    try (MockedStatic<DockerRegistryProviderUtils> providerUtilsMock =
        Mockito.mockStatic(DockerRegistryProviderUtils.class)) {

      providerUtilsMock
          .when(
              () ->
                  DockerRegistryProviderUtils.getAllMatchingKeyPattern(
                      eq(cacheView), eq(namespace), eq(taggedImageKey)))
          .thenReturn(cacheDataSet);

      when(credentials.getSortTagsByDate()).thenReturn(true);

      // When
      List<String> tags = controller.getTags(account, repository);

      // Then
      assertEquals(2, tags.size());
      assertTrue(tags.contains("1.0"));
      assertTrue(tags.contains("2.0"));
    }
  }

  @Test
  void getTags_shouldReturnEmptyListWhenCredentialsNotFound() {
    // Given
    when(accountCredentialsProvider.getCredentials("non-existent")).thenReturn(null);

    // When
    List<String> tags = controller.getTags("non-existent", "test-repository");

    // Then
    assertTrue(tags.isEmpty());
  }

  @Test
  void find_shouldReturnImagesWithQueryParameter() {
    // Given
    AbstractDockerRegistryLookupController.LookupOptions lookupOptions =
        new AbstractDockerRegistryLookupController.LookupOptions();
    lookupOptions.setQ("test");

    String imageIdKey = Keys.getTaggedImageKey("*", "*", "*test*");
    String namespace = Keys.Namespace.IMAGE_ID.getNs();
    String taggedNamespace = Keys.Namespace.TAGGED_IMAGE.getNs();

    CacheData imageIdCacheData = mock(CacheData.class);
    when(imageIdCacheData.getAttributes())
        .thenReturn(Map.of("account", "test-account", "tagKey", "tagKey1"));

    Set<CacheData> imageIdCacheDataSet = new HashSet<>(Collections.singletonList(imageIdCacheData));

    CacheData taggedImageCacheData =
        createTaggedImageCacheData("test-account", "test-repo", "1.0", null);
    Set<CacheData> taggedImageCacheDataSet =
        new HashSet<>(Collections.singletonList(taggedImageCacheData));

    try (MockedStatic<DockerRegistryProviderUtils> providerUtilsMock =
        Mockito.mockStatic(DockerRegistryProviderUtils.class)) {

      providerUtilsMock
          .when(
              () ->
                  DockerRegistryProviderUtils.getAllMatchingKeyPattern(
                      eq(cacheView), eq(namespace), eq(imageIdKey)))
          .thenReturn(imageIdCacheDataSet);

      providerUtilsMock
          .when(
              () ->
                  DockerRegistryProviderUtils.loadResults(
                      eq(cacheView), eq(taggedNamespace), anyCollection()))
          .thenReturn(taggedImageCacheDataSet);

      // When
      List<Map<String, Object>> results = controller.find(lookupOptions);

      // Then
      assertEquals(1, results.size());
      assertEquals("test-repo", results.get(0).get("repository"));
      assertEquals("1.0", results.get(0).get("tag"));
      assertEquals("test-account", results.get(0).get("account"));
      assertEquals("test-registry", results.get(0).get("registry"));
    }
  }

  @Test
  void find_shouldReturnImagesWithoutQuery() {
    // Given
    AbstractDockerRegistryLookupController.LookupOptions lookupOptions =
        new AbstractDockerRegistryLookupController.LookupOptions();

    String taggedImageKey = Keys.getTaggedImageKey("*", "*", "*");
    String namespace = Keys.Namespace.TAGGED_IMAGE.getNs();

    CacheData taggedImageCacheData =
        createTaggedImageCacheData("test-account", "test-repo", "1.0", null);
    Set<CacheData> taggedImageCacheDataSet =
        new HashSet<>(Collections.singletonList(taggedImageCacheData));

    try (MockedStatic<DockerRegistryProviderUtils> providerUtilsMock =
        Mockito.mockStatic(DockerRegistryProviderUtils.class)) {

      providerUtilsMock
          .when(
              () ->
                  DockerRegistryProviderUtils.getAllMatchingKeyPattern(
                      eq(cacheView), eq(namespace), eq(taggedImageKey)))
          .thenReturn(taggedImageCacheDataSet);

      // When
      List<Map<String, Object>> results = controller.find(lookupOptions);

      // Then
      assertEquals(1, results.size());
      assertEquals("test-repo", results.get(0).get("repository"));
      assertEquals("1.0", results.get(0).get("tag"));
      assertEquals("test-account", results.get(0).get("account"));
      assertEquals("test-registry", results.get(0).get("registry"));
    }
  }

  @Test
  void find_shouldFilterByRepositoryAndTag() {
    // Given
    AbstractDockerRegistryLookupController.LookupOptions lookupOptions =
        new AbstractDockerRegistryLookupController.LookupOptions();
    lookupOptions.setRepository("test-repo");
    lookupOptions.setTag("1.0");

    String taggedImageKey = Keys.getTaggedImageKey("*", "*", "*");
    String namespace = Keys.Namespace.TAGGED_IMAGE.getNs();

    CacheData matchingCacheData =
        createTaggedImageCacheData("test-account", "test-repo", "1.0", null);
    CacheData nonMatchingCacheData =
        createTaggedImageCacheData("test-account", "other-repo", "2.0", null);
    Set<CacheData> taggedImageCacheDataSet =
        new HashSet<>(Arrays.asList(matchingCacheData, nonMatchingCacheData));

    try (MockedStatic<DockerRegistryProviderUtils> providerUtilsMock =
        Mockito.mockStatic(DockerRegistryProviderUtils.class)) {

      providerUtilsMock
          .when(
              () ->
                  DockerRegistryProviderUtils.getAllMatchingKeyPattern(
                      eq(cacheView), eq(namespace), eq(taggedImageKey)))
          .thenReturn(taggedImageCacheDataSet);

      // When
      List<Map<String, Object>> results = controller.find(lookupOptions);

      // Then
      assertEquals(1, results.size());
      assertEquals("test-repo", results.get(0).get("repository"));
      assertEquals("1.0", results.get(0).get("tag"));
    }
  }

  @Test
  void find_shouldLimitResultCount() {
    // Given
    AbstractDockerRegistryLookupController.LookupOptions lookupOptions =
        new AbstractDockerRegistryLookupController.LookupOptions();
    lookupOptions.setCount(1);

    String taggedImageKey = Keys.getTaggedImageKey("*", "*", "*");
    String namespace = Keys.Namespace.TAGGED_IMAGE.getNs();

    CacheData cacheData1 = createTaggedImageCacheData("test-account", "test-repo1", "1.0", null);
    CacheData cacheData2 = createTaggedImageCacheData("test-account", "test-repo2", "2.0", null);
    Set<CacheData> taggedImageCacheDataSet = new HashSet<>(Arrays.asList(cacheData1, cacheData2));

    try (MockedStatic<DockerRegistryProviderUtils> providerUtilsMock =
        Mockito.mockStatic(DockerRegistryProviderUtils.class)) {

      providerUtilsMock
          .when(
              () ->
                  DockerRegistryProviderUtils.getAllMatchingKeyPattern(
                      eq(cacheView), eq(namespace), eq(taggedImageKey)))
          .thenReturn(taggedImageCacheDataSet);

      // When
      List<Map<String, Object>> results = controller.find(lookupOptions);

      // Then
      assertEquals(1, results.size());
    }
  }

  @Test
  void find_shouldIncludeDetailsWhenRequested() {
    // Given
    AbstractDockerRegistryLookupController.LookupOptions lookupOptions =
        new AbstractDockerRegistryLookupController.LookupOptions();
    lookupOptions.setIncludeDetails(true);

    String taggedImageKey = Keys.getTaggedImageKey("*", "*", "*");
    String namespace = Keys.Namespace.TAGGED_IMAGE.getNs();

    Map<String, Object> labels = new HashMap<>();
    labels.put("commitId", "abc123");
    labels.put("buildNumber", "42");
    labels.put("branch", "main");

    CacheData cacheData = createTaggedImageCacheData("test-account", "test-repo", "1.0", null);
    when(cacheData.getAttributes())
        .thenReturn(Map.of("account", "test-account", "digest", "sha256:123", "labels", labels));

    Set<CacheData> taggedImageCacheDataSet = new HashSet<>(Collections.singletonList(cacheData));

    try (MockedStatic<DockerRegistryProviderUtils> providerUtilsMock =
        Mockito.mockStatic(DockerRegistryProviderUtils.class)) {

      providerUtilsMock
          .when(
              () ->
                  DockerRegistryProviderUtils.getAllMatchingKeyPattern(
                      eq(cacheView), eq(namespace), eq(taggedImageKey)))
          .thenReturn(taggedImageCacheDataSet);

      // When
      List<Map<String, Object>> results = controller.find(lookupOptions);

      // Then
      assertEquals(1, results.size());
      assertEquals("abc123", results.get(0).get("commitId"));
      assertEquals("42", results.get(0).get("buildNumber"));
      assertEquals("main", results.get(0).get("branch"));

      Map<String, Object> artifact = (Map<String, Object>) results.get(0).get("artifact");
      Map<String, Object> metadata = (Map<String, Object>) artifact.get("metadata");
      assertNotNull(metadata.get("labels"));
    }
  }

  @Test
  void listAllImagesWithoutDigests_shouldReturnImages() {
    // Given
    AbstractDockerRegistryLookupController.LookupOptions lookupOptions =
        new AbstractDockerRegistryLookupController.LookupOptions();
    String key = Keys.getTaggedImageKey("*", "*", "*");

    List<String> identifiers =
        Arrays.asList(
            Keys.getTaggedImageKey("test-account", "test-repo1", "1.0"),
            Keys.getTaggedImageKey("test-account", "test-repo2", "2.0"));

    when(cacheView.filterIdentifiers(eq(Keys.Namespace.TAGGED_IMAGE.getNs()), eq(key)))
        .thenReturn(identifiers);

    // When
    List<Map<String, Object>> results = controller.listAllImagesWithoutDigests(key, lookupOptions);

    // Then
    assertEquals(2, results.size());
  }

  @Test
  void generateArtifact_shouldCreateCorrectArtifact() {
    // Given
    String registry = "test-registry";
    String repository = "test-repo";
    String tag = "1.0";

    // When
    Map<String, Object> artifact = controller.generateArtifact(registry, repository, tag);

    // Then
    assertEquals("docker", artifact.get("type"));
    assertEquals("test-registry/test-repo:1.0", artifact.get("reference"));
    assertEquals("test-repo", artifact.get("name"));
    assertEquals("1.0", artifact.get("version"));

    Map<String, Object> metadata = (Map<String, Object>) artifact.get("metadata");
    assertEquals("test-registry", metadata.get("registry"));
  }

  // Helper method to create tagged image cache data
  private CacheData createTaggedImageCacheData(
      String account, String repository, String tag, String date) {
    String id = Keys.getTaggedImageKey(account, repository, tag);
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", account);

    if (date != null) {
      attributes.put("date", date);
    }

    CacheData cacheData = mock(CacheData.class);
    when(cacheData.getId()).thenReturn(id);
    when(cacheData.getAttributes()).thenReturn(attributes);

    return cacheData;
  }
}
