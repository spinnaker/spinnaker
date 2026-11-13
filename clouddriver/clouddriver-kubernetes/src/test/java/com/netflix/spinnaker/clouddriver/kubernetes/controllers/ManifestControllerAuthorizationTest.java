/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesManifestContainer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.controllers.ManifestControllerAuthorizationTest.TestConfig;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue;
import com.netflix.spinnaker.config.ErrorConfiguration;
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
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import retrofit2.mock.Calls;

/**
 * Runs the manifests-by-kinds-and-labels endpoint through Spring MVC and method security with a
 * real Fiat permission evaluator, so the unauthorized case is proven to be rejected before the
 * controller body runs.
 */
@SpringBootTest(classes = TestConfig.class, properties = "services.fiat.cache.max-entries=0")
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@AutoConfigureJson
@EnableFiatAutoConfig
@WithMockUser
class ManifestControllerAuthorizationTest {

  private static final String ACCOUNT = "test-account";
  private static final String NAMESPACE = "test-namespace";

  @Import({ManifestController.class, ErrorConfiguration.class})
  static class TestConfig {
    // Required by Fiat's permission evaluator.
    @Bean
    Registry registry() {
      return new NoopRegistry();
    }

    @Bean
    RequestQueue requestQueue() {
      return RequestQueue.noop();
    }

    @Bean
    DynamicConfigService dynamicConfigService() {
      return new SpringDynamicConfigService();
    }
  }

  @Autowired MockMvc mockMvc;
  @MockitoBean FiatStatus fiatStatus;
  @MockitoBean FiatService fiatService;
  @MockitoBean KubernetesManifestProvider manifestProvider;

  @BeforeEach
  void setUp() {
    given(fiatStatus.isEnabled()).willReturn(true);
    mirrorMockUserToMdc();
  }

  @AfterEach
  void tearDownMdc() {
    AuthenticatedRequest.clear();
  }

  static void mirrorMockUserToMdc() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      AuthenticatedRequest.setUser(authentication.getName());
    }
  }

  @Test
  void returnsManifestsForUserWithReadOnTheAccount() throws Exception {
    givenUserCanReadAccount(ACCOUNT);
    given(
            manifestProvider.getManifestsByKindsAndLabels(
                ACCOUNT, NAMESPACE, List.of("deployment"), Map.of("app", "myapp")))
        .willReturn(List.of(container(KubernetesKind.DEPLOYMENT, "my-deployment")));

    mockMvc
        .perform(manifestsByLabels(ACCOUNT, "deployment").queryParam("app", "myapp"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].account").value(ACCOUNT))
        .andExpect(jsonPath("$[0].name").value("deployment my-deployment"))
        .andExpect(jsonPath("$[0].location").value(NAMESPACE));
  }

  @Test
  void rejectsUserWithoutReadOnTheAccount() throws Exception {
    given(fiatService.getUserPermission(eq("user")))
        .willReturn(Calls.response(unauthorizedUserPermission()));

    mockMvc
        .perform(manifestsByLabels(ACCOUNT, "deployment").queryParam("app", "myapp"))
        .andExpect(status().isForbidden());

    // The controller body must not have run on behalf of the unauthorized caller.
    verifyNoInteractions(manifestProvider);
  }

  @Test
  void rejectsUserWithReadOnlyOnADifferentAccount() throws Exception {
    givenUserCanReadAccount("some-other-account");

    mockMvc
        .perform(manifestsByLabels(ACCOUNT, "deployment").queryParam("app", "myapp"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(manifestProvider);
  }

  @Test
  void returnsNotFoundWhenNoManifestsMatch() throws Exception {
    givenUserCanReadAccount(ACCOUNT);
    given(manifestProvider.getManifestsByKindsAndLabels(any(), any(), any(), any()))
        .willReturn(List.of());

    mockMvc
        .perform(manifestsByLabels(ACCOUNT, "deployment").queryParam("app", "myapp"))
        .andExpect(status().isNotFound());
  }

  @Test
  void trimsKindsAndIgnoresBlankEntries() throws Exception {
    givenUserCanReadAccount(ACCOUNT);
    given(manifestProvider.getManifestsByKindsAndLabels(any(), any(), any(), any()))
        .willReturn(
            List.of(
                container(KubernetesKind.DEPLOYMENT, "my-deployment"),
                container(KubernetesKind.SERVICE, "my-service")));

    mockMvc
        .perform(manifestsByLabels(ACCOUNT, " deployment, ,service ,").queryParam("app", "myapp"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    verify(manifestProvider)
        .getManifestsByKindsAndLabels(
            ACCOUNT, NAMESPACE, List.of("deployment", "service"), Map.of("app", "myapp"));
  }

  @Test
  void returnsBadRequestWhenNoKindsAreGiven() throws Exception {
    givenUserCanReadAccount(ACCOUNT);

    mockMvc
        .perform(manifestsByLabels(ACCOUNT, " , ").queryParam("app", "myapp"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(manifestProvider);
  }

  private void givenUserCanReadAccount(String account) {
    given(fiatService.getUserPermission(eq("user")))
        .willReturn(Calls.response(authorizedUserPermission(account)));
  }

  private static MockHttpServletRequestBuilder manifestsByLabels(String account, String kinds) {
    return get("/manifests/{account}/{location}/{kinds}/labels", account, NAMESPACE, kinds);
  }

  private static KubernetesManifestContainer container(KubernetesKind kind, String name) {
    KubernetesManifest manifest = new KubernetesManifest();
    manifest.put("metadata", new HashMap<>());
    manifest.setNamespace(NAMESPACE);
    manifest.setKind(kind);
    manifest.setName(name);
    return KubernetesManifestContainer.builder()
        .account(ACCOUNT)
        .name(manifest.getFullResourceName())
        .location(NAMESPACE)
        .manifest(manifest)
        .build();
  }

  private static UserPermission.View authorizedUserPermission(String account) {
    return new UserPermission()
        .setId("user")
        .addResources(
            List.of(
                new Account()
                    .setName(account)
                    .setPermissions(
                        new Permissions.Builder().add(Authorization.READ, "user").build()),
                new Role("user")))
        .getView();
  }

  private static UserPermission.View unauthorizedUserPermission() {
    return new UserPermission().setId("user").addResources(List.of(new Role("user"))).getView();
  }
}
