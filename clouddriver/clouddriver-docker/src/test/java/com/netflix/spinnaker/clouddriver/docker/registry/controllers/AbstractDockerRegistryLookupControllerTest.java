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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import retrofit2.mock.Calls;

@SpringBootTest(
    classes = AbstractDockerRegistryLookupControllerTest.TestConfig.class,
    properties = "services.fiat.cache.max-entries=0")
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@AutoConfigureJson
@EnableFiatAutoConfig
@WithMockUser
class AbstractDockerRegistryLookupControllerTest {

  // Test implementation of AbstractDockerRegistryLookupController
  @RestController
  @RequestMapping("/test/registry")
  static class TestDockerRegistryLookupController extends AbstractDockerRegistryLookupController {
    @Override
    public Map<String, Object> generateArtifact(
        String registry, Object repository, Object tag, Object labels) {
      Map<String, Object> artifact = new HashMap<>();
      artifact.put("name", repository);
      artifact.put("type", "test-type");
      artifact.put("version", tag);
      artifact.put("reference", registry + "/" + repository + ":" + tag);

      Map<String, Object> metadata = new HashMap<>();
      metadata.put("registry", registry);
      metadata.put("labels", labels);
      artifact.put("metadata", metadata);

      return artifact;
    }

    @Override
    protected String getTaggedImageKey(String account, String repository, String tag) {
      return Keys.getTaggedImageKey(account, repository, tag);
    }

    @Override
    protected String getImageIdKey(String pattern) {
      return Keys.getImageIdKey(pattern);
    }

    @Override
    protected String getTaggedImageNamespace() {
      return Keys.Namespace.TAGGED_IMAGE.getNs();
    }

    @Override
    protected boolean isTrackDigestsDisabled() {
      return false;
    }
  }

  @Import(TestDockerRegistryLookupController.class)
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
  }

  @Autowired MockMvc mockMvc;
  @Autowired WriteableCache cache;
  @Autowired AccountCredentialsRepository accountCredentialsRepository;
  @MockBean FiatStatus fiatStatus;
  @MockBean FiatService fiatService;

  @BeforeEach
  void setUp() {
    cache
        .getAll(Keys.Namespace.TAGGED_IMAGE.getNs())
        .forEach(item -> cache.evict(Keys.Namespace.TAGGED_IMAGE.getNs(), item.getId()));
    accountCredentialsRepository
        .getAll()
        .forEach(cred -> accountCredentialsRepository.delete(cred.getName()));

    given(fiatStatus.isEnabled()).willReturn(true);
  }

  @Test
  void testGetTagsWithSortByDateTrue() throws Exception {
    var permissions = createAuthorizedUserPermission();
    given(fiatService.getUserPermission(eq("user"))).willReturn(Calls.response(permissions));

    // Create credentials with sortTagsByDate=true
    var credentials = createTestAccountCredentials(true);
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Add cache data with different dates
    String imageKey1 = Keys.getTaggedImageKey("test-account", "test-repository", "1.0");
    String imageKey2 = Keys.getTaggedImageKey("test-account", "test-repository", "2.0");

    Map<String, Object> attributes1 = new HashMap<>();
    attributes1.put("account", "test-account");
    attributes1.put("date", new Date(1000)); // Older date

    Map<String, Object> attributes2 = new HashMap<>();
    attributes2.put("account", "test-account");
    attributes2.put("date", new Date(2000)); // Newer date

    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(imageKey1, attributes1, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(imageKey2, attributes2, Map.of()));

    // Execute the request
    MvcResult result =
        mockMvc
            .perform(
                get("/test/registry/tags")
                    .queryParam("account", "test-account")
                    .queryParam("repository", "test-repository"))
            .andExpect(status().isOk())
            .andReturn();

    // Parse the response and verify the order (newer first when sortTagsByDate=true)
    String content = result.getResponse().getContentAsString();
    assertTrue(
        content.indexOf("2.0") < content.indexOf("1.0"),
        "Tags should be sorted by date with newer first when sortTagsByDate=true");
  }

  @Test
  void testGetTagsWithSortByDateFalse() throws Exception {
    var permissions = createAuthorizedUserPermission();
    given(fiatService.getUserPermission(eq("user"))).willReturn(Calls.response(permissions));

    // Create credentials with sortTagsByDate=false
    var credentials = createTestAccountCredentials(false);
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Add cache data with different dates
    String imageKey1 = Keys.getTaggedImageKey("test-account", "test-repository", "1.0");
    String imageKey2 = Keys.getTaggedImageKey("test-account", "test-repository", "2.0");

    Map<String, Object> attributes1 = new HashMap<>();
    attributes1.put("account", "test-account");
    attributes1.put("date", new Date(1000));

    Map<String, Object> attributes2 = new HashMap<>();
    attributes2.put("account", "test-account");
    attributes2.put("date", new Date(2000));

    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(imageKey1, attributes1, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(imageKey2, attributes2, Map.of()));

    // Execute the request
    MvcResult result =
        mockMvc
            .perform(
                get("/test/registry/tags")
                    .queryParam("account", "test-account")
                    .queryParam("repository", "test-repository"))
            .andExpect(status().isOk())
            .andReturn();

    // Parse the response and verify the order (alphabetical when sortTagsByDate=false)
    String content = result.getResponse().getContentAsString();
    assertTrue(
        content.indexOf("1.0") < content.indexOf("2.0"),
        "Tags should be sorted alphabetically when sortTagsByDate=false");
  }

  @Test
  void testFindWithQueryParameter() throws Exception {
    var permissions = createAuthorizedUserPermission();
    given(fiatService.getUserPermission(eq("user"))).willReturn(Calls.response(permissions));

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
    var credentials = createTestAccountCredentials(false);
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Test find with query parameter
    mockMvc
        .perform(get("/test/registry/find").queryParam("q", "test-repository"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].repository").value("test-repository"))
        .andExpect(jsonPath("$[0].tag").value("1.0"))
        .andExpect(jsonPath("$[0].account").value("test-account"));
  }

  @Test
  void testFindWithRepositoryAndTagParameters() throws Exception {
    var permissions = createAuthorizedUserPermission();
    given(fiatService.getUserPermission(eq("user"))).willReturn(Calls.response(permissions));

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
    var credentials = createTestAccountCredentials(false);
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Test find with repository and tag parameters
    mockMvc
        .perform(
            get("/test/registry/find")
                .queryParam("repository", "test-repository")
                .queryParam("tag", "1.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].repository").value("test-repository"))
        .andExpect(jsonPath("$[0].tag").value("1.0"));
  }

  @Test
  void testFindWithIncludeDetailsTrue() throws Exception {
    // Setup credentials
    var credentials = createTestAccountCredentials(false);
    accountCredentialsRepository.save(credentials.getName(), credentials);

    var permissions = createAuthorizedUserPermission();
    given(fiatService.getUserPermission(eq("user"))).willReturn(Calls.response(permissions));

    // Setup cache with tagged image data including labels
    String taggedImageKey = Keys.getTaggedImageKey("test-account", "test-repository", "1.0");
    Map<String, Object> tagAttributes = new HashMap<>();
    tagAttributes.put("account", "test-account");
    tagAttributes.put("digest", "test-digest");
    tagAttributes.put(
        "labels",
        Map.of(
            "commitId", "test-commit",
            "buildNumber", "123",
            "branch", "main"));

    cache.merge(
        Keys.Namespace.TAGGED_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey, tagAttributes, Map.of()));

    // Test find with includeDetails=true
    mockMvc
        .perform(get("/test/registry/find").queryParam("includeDetails", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].commitId").value("test-commit"))
        .andExpect(jsonPath("$[0].buildNumber").value("123"))
        .andExpect(jsonPath("$[0].branch").value("main"))
        .andExpect(jsonPath("$[0].artifact.metadata.labels.commitId").value("test-commit"));
  }

  private static UserPermission.View createAuthorizedUserPermission() {
    return new UserPermission()
        .setId("user")
        .addResources(
            List.of(
                new Account()
                    .setName("test-account")
                    .setPermissions(
                        new Permissions.Builder().add(Authorization.READ, "user").build()),
                new Role("user")))
        .getView();
  }

  private static DockerRegistryNamedAccountCredentials createTestAccountCredentials(
      boolean sortTagsByDate) {
    var credentials = mock(DockerRegistryNamedAccountCredentials.class);
    given(credentials.getName()).willReturn("test-account");
    given(credentials.getCloudProvider())
        .willReturn(DockerRegistryCloudProvider.getDOCKER_REGISTRY());
    given(credentials.getRegistry()).willReturn("test-registry");
    given(credentials.getSortTagsByDate()).willReturn(sortTagsByDate);
    return credentials;
  }
}
