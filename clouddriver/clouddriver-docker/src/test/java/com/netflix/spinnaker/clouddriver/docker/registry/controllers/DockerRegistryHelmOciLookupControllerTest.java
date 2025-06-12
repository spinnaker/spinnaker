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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider;
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys;
import com.netflix.spinnaker.clouddriver.docker.registry.controllers.DockerRegistryHelmOciLookupControllerTest.TestConfig;
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryCredentials;
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
import retrofit2.mock.Calls;

@SpringBootTest(classes = TestConfig.class, properties = "services.fiat.cache.max-entries=0")
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@AutoConfigureJson
@EnableFiatAutoConfig
@WithMockUser
class DockerRegistryHelmOciLookupControllerTest {
  @Import(DockerRegistryHelmOciLookupController.class)
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

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Autowired MockMvc mockMvc;
  @Autowired WriteableCache cache;
  @Autowired AccountCredentialsRepository accountCredentialsRepository;
  @MockBean FiatStatus fiatStatus;
  @MockBean FiatService fiatService;

  @BeforeEach
  void setUp() {
    // Clear previous test state
    cache
        .getAll(Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs())
        .forEach(item -> cache.evict(Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(), item.getId()));
    cache
        .getAll(Keys.Namespace.IMAGE_ID.getNs())
        .forEach(item -> cache.evict(Keys.Namespace.IMAGE_ID.getNs(), item.getId()));

    accountCredentialsRepository
        .getAll()
        .forEach(cred -> accountCredentialsRepository.delete(cred.getName()));

    given(fiatStatus.isEnabled()).willReturn(true);
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));
    given(fiatService.getUserPermission("userForbidden"))
        .willReturn(Calls.response(createUnauthorizedUserPermission()));
  }

  @Test
  @WithMockUser
  void authorizedToReadTags() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));

    mockMvc
        .perform(
            get("/dockerRegistry/charts/tags")
                .queryParam("account", "test-account")
                .queryParam("repository", "test-repository"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "userForbidden")
  void notAuthorizedToReadTags() throws Exception {
    given(fiatService.getUserPermission("userForbidden"))
        .willReturn(Calls.response(createUnauthorizedUserPermission()));

    mockMvc
        .perform(
            get("/dockerRegistry/charts/tags")
                .queryParam("account", "test-account")
                .queryParam("repository", "test-repository"))
        .andExpect(status().isForbidden());
  }

  @Test
  void canSearchForAuthorizedItems() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));
    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(), createTestAccountTaggedImageCacheData());
    var credentials = createTestAccountCredentials(false, List.of("test-repository"));
    accountCredentialsRepository.save(credentials.getName(), credentials);

    mockMvc
        .perform(get("/dockerRegistry/charts/find"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].account").value("test-account"))
        .andExpect(jsonPath("$[0].artifact.type").value("helm/image"));
  }

  @Test
  @WithMockUser(username = "userForbidden")
  void filtersOutUnauthorizedItems() throws Exception {
    given(fiatService.getUserPermission("userForbidden"))
        .willReturn(Calls.response(createUnauthorizedUserPermission()));
    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(), createTestAccountTaggedImageCacheData());
    var credentials = createTestAccountCredentials(false, List.of("test-repository"));
    accountCredentialsRepository.save(credentials.getName(), credentials);

    mockMvc
        .perform(get("/dockerRegistry/charts/find"))
        .andExpectAll(status().isOk(), jsonPath("$.length()").value(0));
  }

  @Test
  void generatesCorrectArtifactType() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));
    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(), createTestAccountTaggedImageCacheData());
    var credentials = createTestAccountCredentials(false, List.of("test-repository"));
    accountCredentialsRepository.save(credentials.getName(), credentials);

    mockMvc
        .perform(get("/dockerRegistry/charts/find").queryParam("includeDetails", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].artifact.type").value("helm/image"))
        .andExpect(jsonPath("$[0].artifact.metadata.registry").value("test-registry"))
        .andExpect(jsonPath("$[0].artifact.metadata.labels.commitId").value("test-commit"));
  }

  @Test
  void findWithQueryParameter() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));

    // Setup cache with image ID data
    String imageIdKey = Keys.getImageIdKey("test-repository");
    Map<String, Object> idAttributes = new HashMap<>();
    idAttributes.put("account", "test-account");
    idAttributes.put(
        "tagKey", Keys.getHelmOciTaggedImageKey("test-account", "test-repository", "1.0"));

    cache.merge(
        Keys.Namespace.IMAGE_ID.getNs(), new DefaultCacheData(imageIdKey, idAttributes, Map.of()));

    // Setup cache with tagged image data
    String taggedImageKey = Keys.getHelmOciTaggedImageKey("test-account", "test-repository", "1.0");
    Map<String, Object> tagAttributes = new HashMap<>();
    tagAttributes.put("account", "test-account");
    tagAttributes.put("digest", "test-digest");
    tagAttributes.put("labels", Map.of("commitId", "test-commit"));

    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey, tagAttributes, Map.of()));

    // Setup credentials
    var credentials = createTestAccountCredentials(false, List.of("test-repository"));
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Test find with query parameter
    mockMvc
        .perform(get("/dockerRegistry/charts/find").queryParam("q", "test-repository"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].repository").value("test-repository"))
        .andExpect(jsonPath("$[0].tag").value("1.0"))
        .andExpect(jsonPath("$[0].account").value("test-account"));
  }

  @Test
  void findWithAccountFilter() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));

    // Setup multiple accounts in cache
    String taggedImageKey1 =
        Keys.getHelmOciTaggedImageKey("test-account", "test-repository", "1.0");
    Map<String, Object> tagAttributes1 = new HashMap<>();
    tagAttributes1.put("account", "test-account");
    tagAttributes1.put("digest", "test-digest-1");

    String taggedImageKey2 =
        Keys.getHelmOciTaggedImageKey("other-account", "test-repository", "1.0");
    Map<String, Object> tagAttributes2 = new HashMap<>();
    tagAttributes2.put("account", "other-account");
    tagAttributes2.put("digest", "test-digest-2");

    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey1, tagAttributes1, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey2, tagAttributes2, Map.of()));

    // Setup credentials for both accounts
    var credentials1 = createTestAccountCredentials(false, List.of("test-repository"));
    accountCredentialsRepository.save(credentials1.getName(), credentials1);

    var credentials2 = mock(DockerRegistryNamedAccountCredentials.class);
    given(credentials2.getName()).willReturn("other-account");
    given(credentials2.getCloudProvider())
        .willReturn(DockerRegistryCloudProvider.getDOCKER_REGISTRY());
    given(credentials2.getRegistry()).willReturn("other-registry");
    given(credentials2.getCredentials()).willReturn(mock(DockerRegistryCredentials.class));
    given(credentials2.getCredentials().getHelmOciRepositories())
        .willReturn(List.of("test-repository"));
    accountCredentialsRepository.save(credentials2.getName(), credentials2);

    // Test find with account filter
    mockMvc
        .perform(get("/dockerRegistry/charts/find").queryParam("account", "test-account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].account").value("test-account"))
        .andExpect(jsonPath("$[0].registry").value("test-registry"))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void findWithRepositoryAndTagFilters() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));

    // Setup multiple repository/tag combinations
    String taggedImageKey1 = Keys.getHelmOciTaggedImageKey("test-account", "repo1", "tag1");
    Map<String, Object> tagAttributes1 = new HashMap<>();
    tagAttributes1.put("account", "test-account");
    tagAttributes1.put("digest", "digest1");

    String taggedImageKey2 = Keys.getHelmOciTaggedImageKey("test-account", "repo1", "tag2");
    Map<String, Object> tagAttributes2 = new HashMap<>();
    tagAttributes2.put("account", "test-account");
    tagAttributes2.put("digest", "digest2");

    String taggedImageKey3 = Keys.getHelmOciTaggedImageKey("test-account", "repo2", "tag1");
    Map<String, Object> tagAttributes3 = new HashMap<>();
    tagAttributes3.put("account", "test-account");
    tagAttributes3.put("digest", "digest3");

    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey1, tagAttributes1, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey2, tagAttributes2, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey3, tagAttributes3, Map.of()));

    // Setup credentials
    var credentials = createTestAccountCredentials(false, List.of("repo1", "repo2"));
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Test find with repository and tag filter
    mockMvc
        .perform(
            get("/dockerRegistry/charts/find")
                .queryParam("repository", "repo1")
                .queryParam("tag", "tag1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].repository").value("repo1"))
        .andExpect(jsonPath("$[0].tag").value("tag1"));
  }

  @Test
  void testTagsSortingByDate() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));

    // Create credentials with sortTagsByDate=true
    var credentials = createTestAccountCredentials(true, List.of("test-repository"));
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Add cache data with different dates
    String imageKey1 = Keys.getHelmOciTaggedImageKey("test-account", "test-repository", "1.0");
    String imageKey2 = Keys.getHelmOciTaggedImageKey("test-account", "test-repository", "2.0");

    Map<String, Object> attributes1 = new HashMap<>();
    attributes1.put("account", "test-account");
    attributes1.put("date", new Date(1000)); // Older date

    Map<String, Object> attributes2 = new HashMap<>();
    attributes2.put("account", "test-account");
    attributes2.put("date", new Date(2000)); // Newer date

    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(imageKey1, attributes1, Map.of()));
    cache.merge(
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(imageKey2, attributes2, Map.of()));

    // Execute the request
    MvcResult result =
        mockMvc
            .perform(
                get("/dockerRegistry/charts/tags")
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
  void testLookupOptionsWithAllParameters() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));

    // Setup cache with tagged image data
    String taggedImageKey = Keys.getHelmOciTaggedImageKey("test-account", "test-repository", "1.0");
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
        Keys.Namespace.TAGGED_HELM_OCI_IMAGE.getNs(),
        new DefaultCacheData(taggedImageKey, tagAttributes, Map.of()));

    // Setup credentials
    var credentials = createTestAccountCredentials(false, List.of("test-repository"));
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Test find with all lookup options
    mockMvc
        .perform(
            get("/dockerRegistry/charts/find")
                .queryParam("account", "test-account")
                .queryParam("repository", "test-repository")
                .queryParam("tag", "1.0")
                .queryParam("count", "10")
                .queryParam("includeDetails", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].repository").value("test-repository"))
        .andExpect(jsonPath("$[0].tag").value("1.0"))
        .andExpect(jsonPath("$[0].account").value("test-account"))
        .andExpect(jsonPath("$[0].digest").value("test-digest"))
        .andExpect(jsonPath("$[0].commitId").value("test-commit"))
        .andExpect(jsonPath("$[0].buildNumber").value("123"))
        .andExpect(jsonPath("$[0].branch").value("main"))
        .andExpect(jsonPath("$[0].artifact.type").value("helm/image"));
  }

  @Test
  void testEmptyResults() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));

    // Setup credentials but no cache data
    var credentials = createTestAccountCredentials(false, List.of());
    accountCredentialsRepository.save(credentials.getName(), credentials);

    // Test find with no matching results
    mockMvc
        .perform(get("/dockerRegistry/charts/find"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void testNonExistentAccount() throws Exception {
    given(fiatService.getUserPermission("user"))
        .willReturn(Calls.response(createAuthorizedUserPermission()));

    // Test tags with non-existent account
    mockMvc
        .perform(
            get("/dockerRegistry/charts/tags")
                .queryParam("account", "non-existent-account")
                .queryParam("repository", "test-repository"))
        .andExpect(status().isForbidden());
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

  private static UserPermission.View createUnauthorizedUserPermission() {
    return new UserPermission()
        .setId("userForbidden")
        .addResources(List.of(new Role("userForbidden")))
        .getView();
  }

  private static CacheData createTestAccountTaggedImageCacheData() {
    String imageKey = Keys.getHelmOciTaggedImageKey("test-account", "test-repository", "1.0");
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

  private static DockerRegistryNamedAccountCredentials createTestAccountCredentials(
      boolean sortTagsByDate, List<String> helmOciRepositories) {
    var credentials = mock(DockerRegistryNamedAccountCredentials.class);
    given(credentials.getName()).willReturn("test-account");
    given(credentials.getCloudProvider())
        .willReturn(DockerRegistryCloudProvider.getDOCKER_REGISTRY());
    given(credentials.getRegistry()).willReturn("test-registry");
    given(credentials.getCredentials()).willReturn(mock(DockerRegistryCredentials.class));
    given(credentials.getCredentials().getHelmOciRepositories()).willReturn(helmOciRepositories);
    given(credentials.getSortTagsByDate()).willReturn(sortTagsByDate);
    return credentials;
  }
}
