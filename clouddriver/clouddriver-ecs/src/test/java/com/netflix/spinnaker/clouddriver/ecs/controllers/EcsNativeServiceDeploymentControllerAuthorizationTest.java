/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.controllers.EcsNativeServiceDeploymentControllerAuthorizationTest.TestConfig;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
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
import java.util.List;
import java.util.Set;
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
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServiceRevisionsRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServiceRevisionsResponse;
import software.amazon.awssdk.services.ecs.model.ServiceDeployment;
import software.amazon.awssdk.services.ecs.model.ServiceRevision;
import software.amazon.awssdk.services.ecs.model.ServiceRevisionSummary;

/**
 * Runs the deploymentStatus endpoint through Spring method security with a real Fiat permission
 * evaluator, so the unauthorized case is proven to be rejected before the controller body runs.
 */
@SpringBootTest(classes = TestConfig.class, properties = "services.fiat.cache.max-entries=0")
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@AutoConfigureJson
@EnableFiatAutoConfig
@WithMockUser
class EcsNativeServiceDeploymentControllerAuthorizationTest {

  private static final String ACCOUNT = "test-account";
  private static final String REGION = "us-west-2";
  private static final String SERVER_GROUP = "myapp";
  private static final String DEPLOYMENT_ARN =
      "arn:aws:ecs:us-west-2:123:service-deployment/myapp/1";
  private static final String REVISION_ARN = "arn:aws:ecs:us-west-2:123:service-revision/myapp/2";
  private static final String SERVICE_ARN = "arn:aws:ecs:us-west-2:123:service/cluster/myapp";
  private static final String CLUSTER_ARN = "arn:aws:ecs:us-west-2:123:cluster/cluster";

  @Import(EcsNativeServiceDeploymentController.class)
  static class TestConfig {
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
  @MockitoBean FiatStatus fiatStatus;
  @MockitoBean FiatService fiatService;
  @MockitoBean CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  @MockitoBean AmazonClientProvider amazonClientProvider;
  @MockitoBean ServiceCacheClient serviceCacheClient;

  private final EcsClient ecs = mock(EcsClient.class);

  @BeforeEach
  void setUp() {
    given(fiatStatus.isEnabled()).willReturn(true);
    mirrorMockUserToMdc();

    NetflixECSCredentials credentials = mock(NetflixECSCredentials.class);
    given(credentials.getName()).willReturn(ACCOUNT);
    given(credentialsRepository.getOne(ACCOUNT)).willReturn(credentials);
    given(credentialsRepository.getAll()).willReturn(Set.of(credentials));

    Service cachedService = new Service();
    cachedService.setServiceArn(SERVICE_ARN);
    cachedService.setClusterArn(CLUSTER_ARN);
    given(serviceCacheClient.get(Keys.getServiceKey(ACCOUNT, REGION, SERVER_GROUP)))
        .willReturn(cachedService);

    given(amazonClientProvider.getAmazonEcsV2(any(), eq(REGION))).willReturn(ecs);
    given(ecs.describeServiceDeployments(any(DescribeServiceDeploymentsRequest.class)))
        .willReturn(
            DescribeServiceDeploymentsResponse.builder()
                .serviceDeployments(
                    ServiceDeployment.builder()
                        .serviceDeploymentArn(DEPLOYMENT_ARN)
                        .serviceArn(SERVICE_ARN)
                        .clusterArn(CLUSTER_ARN)
                        .status("SUCCESSFUL")
                        .targetServiceRevision(
                            ServiceRevisionSummary.builder().arn(REVISION_ARN).build())
                        .build())
                .build());
    given(ecs.describeServiceRevisions(any(DescribeServiceRevisionsRequest.class)))
        .willReturn(
            DescribeServiceRevisionsResponse.builder()
                .serviceRevisions(
                    ServiceRevision.builder()
                        .serviceRevisionArn(REVISION_ARN)
                        .taskDefinition("task-def-arn")
                        .build())
                .build());
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
  void rejectsUserWithoutReadOnTheAccount() throws Exception {
    given(fiatService.getUserPermission(eq("user")))
        .willReturn(Calls.response(unauthorizedUserPermission()));

    mockMvc.perform(deploymentStatus(ACCOUNT)).andExpect(status().isForbidden());

    // The controller body must not have run: nothing was resolved, read from the cache, or sent
    // to ECS on behalf of the unauthorized caller.
    verifyNoInteractions(credentialsRepository, serviceCacheClient, amazonClientProvider, ecs);
  }

  @Test
  void rejectsUserWithReadOnlyOnADifferentAccount() throws Exception {
    given(fiatService.getUserPermission(eq("user")))
        .willReturn(Calls.response(authorizedUserPermission("some-other-account")));

    mockMvc.perform(deploymentStatus("TEST-ACCOUNT")).andExpect(status().isForbidden());

    verifyNoInteractions(credentialsRepository, serviceCacheClient, amazonClientProvider, ecs);
  }

  @Test
  void allowsUserWithReadOnTheAccount() throws Exception {
    given(fiatService.getUserPermission(eq("user")))
        .willReturn(Calls.response(authorizedUserPermission(ACCOUNT)));

    mockMvc
        .perform(deploymentStatus(ACCOUNT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceDeploymentArn").value(DEPLOYMENT_ARN))
        .andExpect(jsonPath("$.targetServiceRevisionArn").value(REVISION_ARN));
  }

  @Test
  void authorizesAndResolvesAnAccountNameThatDiffersOnlyInCase() throws Exception {
    given(fiatService.getUserPermission(eq("user")))
        .willReturn(Calls.response(authorizedUserPermission(ACCOUNT)));

    // The credentials repository is registered lower-case, so "TEST-ACCOUNT" only resolves through
    // the case-insensitive fallback; Fiat matches account names case-insensitively as well.
    given(credentialsRepository.getOne("TEST-ACCOUNT")).willReturn(null);

    mockMvc
        .perform(deploymentStatus("TEST-ACCOUNT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceDeploymentArn").value(DEPLOYMENT_ARN));
  }

  private static MockHttpServletRequestBuilder deploymentStatus(String account) {
    return get(
            "/ecs-native/serverGroups/{account}/{region}/{serverGroupName}/deploymentStatus",
            account,
            REGION,
            SERVER_GROUP)
        .queryParam("expectedServiceDeploymentArn", DEPLOYMENT_ARN);
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
