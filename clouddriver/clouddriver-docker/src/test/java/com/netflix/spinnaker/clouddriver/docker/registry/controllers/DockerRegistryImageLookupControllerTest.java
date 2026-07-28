/*
 * Copyright 2024 Apple, Inc.
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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys;
import com.netflix.spinnaker.clouddriver.docker.registry.controllers.DockerRegistryImageLookupControllerTest.TestConfig;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Owner-local authorization: the controller's {@code @PreAuthorize}/{@code @PostFilter} annotations
 * bind to a {@code PermissionEvaluator} bean (named {@code spinnakerPermissionEvaluator}). These
 * tests drive that evaluator directly via {@link TestPermissionEvaluator#readableAccounts}.
 */
@SpringBootTest(classes = TestConfig.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@AutoConfigureJson
@WithMockUser
class DockerRegistryImageLookupControllerTest {
  @Import(DockerRegistryImageLookupController.class)
  @EnableGlobalMethodSecurity(prePostEnabled = true)
  static class TestConfig {
    @Bean
    WriteableCache cache() {
      return new InMemoryCache();
    }

    @Bean
    AccountCredentialsRepository accountCredentialsRepository() {
      return new MapBackedAccountCredentialsRepository();
    }

    @Bean
    AccountCredentialsProvider accountCredentialsProvider(
        AccountCredentialsRepository accountCredentialsRepository) {
      return new DefaultAccountCredentialsProvider(accountCredentialsRepository);
    }

    @Bean
    Registry registry() {
      return new NoopRegistry();
    }

    @Bean
    DynamicConfigService dynamicConfigService() {
      return new SpringDynamicConfigService();
    }

    @Bean(name = "spinnakerPermissionEvaluator")
    TestPermissionEvaluator spinnakerPermissionEvaluator() {
      return new TestPermissionEvaluator();
    }
  }

  /**
   * Minimal {@link PermissionEvaluator} standing in for the owner-local evaluator: grants {@code
   * ACCOUNT}/{@code READ} only for account names explicitly added to {@link #readableAccounts}.
   */
  static class TestPermissionEvaluator implements PermissionEvaluator {
    final Set<String> readableAccounts = new HashSet<>();

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
      return false;
    }

    @Override
    public boolean hasPermission(
        Authentication authentication,
        Serializable targetId,
        String targetType,
        Object permission) {
      return readableAccounts.contains(String.valueOf(targetId));
    }
  }

  @Autowired MockMvc mockMvc;
  @Autowired WriteableCache cache;
  @Autowired AccountCredentialsRepository accountCredentialsRepository;
  @Autowired TestPermissionEvaluator permissionEvaluator;

  @BeforeEach
  void setUp() {
    cache
        .getAll(Keys.Namespace.TAGGED_IMAGE.getNs())
        .forEach(item -> cache.evict(Keys.Namespace.TAGGED_IMAGE.getNs(), item.getId()));
    accountCredentialsRepository
        .getAll()
        .forEach(cred -> accountCredentialsRepository.delete(cred.getName()));
    permissionEvaluator.readableAccounts.clear();
  }

  @Test
  void authorizedToReadTags() throws Exception {
    permissionEvaluator.readableAccounts.add("test-account");

    mockMvc
        .perform(
            get("/dockerRegistry/images/tags")
                .queryParam("account", "test-account")
                .queryParam("repository", "test-repository"))
        .andExpect(status().isOk());
  }

  @Test
  void notAuthorizedToReadTags() throws Exception {
    mockMvc
        .perform(
            get("/dockerRegistry/images/tags")
                .queryParam("account", "test-account")
                .queryParam("repository", "test-repository"))
        .andExpect(status().isForbidden());
  }

  @Test
  void canSearchForAuthorizedItems() throws Exception {
    permissionEvaluator.readableAccounts.add("test-account");
    cache.merge(Keys.Namespace.TAGGED_IMAGE.getNs(), createTestAccountTaggedImageCacheData());
    var credentials = createTestAccountCredentials();
    accountCredentialsRepository.save(credentials.getName(), credentials);

    mockMvc
        .perform(get("/dockerRegistry/images/find"))
        .andExpect(jsonPath("$[0].account").value("test-account"));
  }

  @Test
  void filtersOutUnauthorizedItems() throws Exception {
    cache.merge(Keys.Namespace.TAGGED_IMAGE.getNs(), createTestAccountTaggedImageCacheData());
    var credentials = createTestAccountCredentials();
    accountCredentialsRepository.save(credentials.getName(), credentials);

    mockMvc
        .perform(get("/dockerRegistry/images/find"))
        .andExpectAll(status().isOk(), jsonPath("$.length()").value(0));
  }

  @Test
  void generatesCorrectArtifactType() throws Exception {
    permissionEvaluator.readableAccounts.add("test-account");
    cache.merge(Keys.Namespace.TAGGED_IMAGE.getNs(), createTestAccountTaggedImageCacheData());
    var credentials = createTestAccountCredentials();
    accountCredentialsRepository.save(credentials.getName(), credentials);

    mockMvc
        .perform(get("/dockerRegistry/images/find").queryParam("includeDetails", "true"))
        .andExpect(jsonPath("$[0].artifact.type").value("docker"))
        .andExpect(jsonPath("$[0].artifact.metadata.registry").value("test-registry"))
        .andExpect(jsonPath("$[0].artifact.metadata.labels.commitId").value("test-commit"));
  }

  @Test
  void findWithQueryParameter() throws Exception {
    permissionEvaluator.readableAccounts.add("test-account");

    // Setup cache with image ID data
    String imageIdKey = Keys.getImageIdKey("test-repository");
    Map<String, Object> idAttributes = new HashMap<>();
    idAttributes.put("account", "test-account");
    idAttributes.put("tagKey", Keys.getTaggedImageKey("test-account", "test-repository", "1.0"));

    cache.merge(
        Keys.Namespace.IMAGE_ID.getNs(), new DefaultCacheData(imageIdKey, idAttributes, Map.of()));

    // Setup cache with tagged image data
    String taggedImageKey = Keys.getTaggedImageKey("test-account", "test-repository", "1.0");
    Map<String, Object> tagAttributes = new HashMap<>();
    tagAttributes.put("account", "test-account");
    tagAttributes.put("digest", "test-digest");
    tagAttributes.put("labels", Map.of("commitId", "test-commit"));

    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey, tagAttributes, Map.of()));

    // Setup credentials
    var credentials = createTestAccountCredentials();
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Test find with query parameter
    mockMvc
        .perform(get("/dockerRegistry/images/find").queryParam("q", "test-repository"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].repository").value("test-repository"))
        .andExpect(jsonPath("$[0].tag").value("1.0"))
        .andExpect(jsonPath("$[0].account").value("test-account"));
  }

  @Test
  void findWithAccountFilter() throws Exception {
    permissionEvaluator.readableAccounts.add("test-account");
    permissionEvaluator.readableAccounts.add("other-account");

    // Setup multiple accounts in cache
    String taggedImageKey1 = Keys.getTaggedImageKey("test-account", "test-repository", "1.0");
    Map<String, Object> tagAttributes1 = new HashMap<>();
    tagAttributes1.put("account", "test-account");
    tagAttributes1.put("digest", "test-digest-1");

    String taggedImageKey2 = Keys.getTaggedImageKey("other-account", "test-repository", "1.0");
    Map<String, Object> tagAttributes2 = new HashMap<>();
    tagAttributes2.put("account", "other-account");
    tagAttributes2.put("digest", "test-digest-2");

    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey1, tagAttributes1, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey2, tagAttributes2, Map.of()));

    // Setup credentials for both accounts
    var credentials1 = createTestAccountCredentials();
    accountCredentialsRepository.save(credentials1.getName(), credentials1);

    var credentials2 = mock(DockerRegistryNamedAccountCredentials.class);
    given(credentials2.getName()).willReturn("other-account");
    given(credentials2.getCloudProvider())
        .willReturn(DockerRegistryCloudProvider.getDOCKER_REGISTRY());
    given(credentials2.getRegistry()).willReturn("other-registry");
    accountCredentialsRepository.save(credentials2.getName(), credentials2);

    // Test find with account filter
    mockMvc
        .perform(get("/dockerRegistry/images/find").queryParam("account", "test-account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].account").value("test-account"))
        .andExpect(jsonPath("$[0].registry").value("test-registry"))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void findWithRepositoryAndTagFilters() throws Exception {
    permissionEvaluator.readableAccounts.add("test-account");
    // Setup credentials
    var credentials = createTestAccountCredentials();
    accountCredentialsRepository.getAll();
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Setup multiple repository/tag combinations
    String taggedImageKey1 = Keys.getTaggedImageKey("test-account", "repo1", "tag1");
    Map<String, Object> tagAttributes1 = new HashMap<>();
    tagAttributes1.put("account", "test-account");
    tagAttributes1.put("digest", "digest1");

    String taggedImageKey2 = Keys.getTaggedImageKey("test-account", "repo1", "tag2");
    Map<String, Object> tagAttributes2 = new HashMap<>();
    tagAttributes2.put("account", "test-account");
    tagAttributes2.put("digest", "digest2");

    String taggedImageKey3 = Keys.getTaggedImageKey("test-account", "repo2", "tag1");
    Map<String, Object> tagAttributes3 = new HashMap<>();
    tagAttributes3.put("account", "test-account");
    tagAttributes3.put("digest", "digest3");

    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey1, tagAttributes1, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey2, tagAttributes2, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey3, tagAttributes3, Map.of()));

    // Test find with repository and tag filter
    mockMvc
        .perform(
            get("/dockerRegistry/images/find")
                .queryParam("repository", "repo1")
                .queryParam("tag", "tag1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].repository").value("repo1"))
        .andExpect(jsonPath("$[0].tag").value("tag1"));
  }

  private static CacheData createTestAccountTaggedImageCacheData() {
    String imageKey = Keys.getTaggedImageKey("test-account", "test-repository", "1.0");
    return new DefaultCacheData(
        imageKey,
        Map.of(
            "account",
            "test-account",
            "digest",
            "test-digest",
            "labels",
            Map.of(
                "commitId",
                "test-commit",
                "buildNumber",
                "1",
                "branch",
                "test-branch",
                "jobName",
                "test-job")),
        Map.of());
  }

  private static DockerRegistryNamedAccountCredentials createTestAccountCredentials() {
    var credentials = mock(DockerRegistryNamedAccountCredentials.class);
    given(credentials.getName()).willReturn("test-account");
    given(credentials.getCloudProvider())
        .willReturn(DockerRegistryCloudProvider.getDOCKER_REGISTRY());
    given(credentials.getRegistry()).willReturn("test-registry");
    return credentials;
  }
}
