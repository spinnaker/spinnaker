/*
 * Copyright 2026 Spinnaker Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.fiat.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import retrofit2.Call;
import retrofit2.Response;

class FiatMethodSecurityTest {
  private AnnotationConfigWebApplicationContext context;
  private FiatService fiatService;
  private ProtectedService protectedService;

  @BeforeEach
  void setUp() throws Exception {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
        context,
        "services.fiat.enabled=true",
        "services.fiat.cache.max-entries=10",
        "services.fiat.cache.expires-after-write-seconds=3600");
    context.register(TestConfiguration.class);
    context.refresh();
    fiatService = context.getBean(FiatService.class);
    protectedService = context.getBean(ProtectedService.class);
    stubPermission("authorized", Set.of(Authorization.READ));
    stubPermission("unauthorized", Set.of());
    assertThat(AopUtils.isAopProxy(protectedService)).isTrue();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    if (context != null) {
      context.close();
    }
  }

  @Test
  void allowsAuthorizedCallerThroughSpringProxy() {
    authenticate("authorized");

    assertThat(protectedService.readApplication("application")).isEqualTo("application");
    assertThat(protectedService.getInvocations()).isEqualTo(1);
  }

  @Test
  void rejectsUnauthorizedCallerThroughSpringProxy() {
    authenticate("unauthorized");

    assertThatThrownBy(() -> protectedService.readApplication("application"))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(protectedService.getInvocations()).isZero();
  }

  @Test
  void invalidatingCachedPermissionFetchesAgainAndAppliesRevocation() throws Exception {
    authenticate("authorized");
    protectedService.readApplication("application");
    protectedService.readApplication("application");
    verify(fiatService, times(1)).getUserPermission("authorized");

    stubPermission("authorized", Set.of());
    FiatPermissionEvaluator evaluator = context.getBean(FiatPermissionEvaluator.class);
    assertThat(evaluator.hasCachedPermission("authorized")).isTrue();
    evaluator.invalidatePermission("authorized");
    assertThat(evaluator.hasCachedPermission("authorized")).isFalse();

    assertThatThrownBy(() -> protectedService.readApplication("application"))
        .isInstanceOf(AccessDeniedException.class);
    verify(fiatService, times(2)).getUserPermission("authorized");
    assertThat(protectedService.getInvocations()).isEqualTo(2);
  }

  private void stubPermission(String user, Set<Authorization> authorizations) throws Exception {
    UserPermission.View permission =
        new UserPermission.View()
            .setApplications(
                Set.of(
                    new Application.View()
                        .setName("application")
                        .setAuthorizations(authorizations)));
    Call<UserPermission.View> call = mock(Call.class);
    when(call.execute()).thenReturn(Response.success(permission));
    when(fiatService.getUserPermission(user)).thenReturn(call);
  }

  private void authenticate(String user) {
    SecurityContextHolder.getContext()
        .setAuthentication(new PreAuthenticatedAuthenticationToken(user, null, List.of()));
  }

  public static class ProtectedService {
    private int invocations;

    public int getInvocations() {
      return invocations;
    }

    @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
    public String readApplication(String application) {
      invocations++;
      return application;
    }
  }

  @Configuration
  @Import(FiatAuthenticationConfig.class)
  static class TestConfiguration {
    @Bean
    Registry registry() {
      return new NoopRegistry();
    }

    @Bean
    FiatService fiatService() {
      return mock(FiatService.class);
    }

    @Bean
    DynamicConfigService dynamicConfigService() {
      return DynamicConfigService.NOOP;
    }

    @Bean
    ProtectedService protectedService() {
      return new ProtectedService();
    }
  }
}
