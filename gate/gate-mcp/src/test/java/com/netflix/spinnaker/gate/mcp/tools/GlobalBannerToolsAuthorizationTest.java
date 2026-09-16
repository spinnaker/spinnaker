/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.gate.mcp.tools;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.gate.banner.GlobalBannerProperties;
import com.netflix.spinnaker.gate.banner.GlobalBannerService;
import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Verifies every {@link GlobalBannerTools} method's {@code @PreAuthorize("@fiatPermissionEvaluator
 * .isAdmin()")} is actually enforced through a real Spring AOP proxy - {@link GlobalBannerService}
 * has no Fiat awareness of its own (see {@link GlobalBannerTools}'s class Javadoc), so these
 * annotations are the only thing standing between any authenticated MCP caller and every global
 * banner. Mirrors {@link AdminToolsAuthorizationTest}.
 */
@ExtendWith(MockitoExtension.class)
class GlobalBannerToolsAuthorizationTest {

  @Mock private GlobalBannerService globalBannerService;

  @Configuration
  @EnableGlobalMethodSecurity(prePostEnabled = true)
  static class MethodSecurityConfig {
    @Bean(name = "fiatPermissionEvaluator")
    AdminToolsAuthorizationTest.FakeFiatPermissionEvaluator fiatPermissionEvaluator() {
      return new AdminToolsAuthorizationTest.FakeFiatPermissionEvaluator();
    }
  }

  @Configuration
  static class ToolConfig {
    @Bean
    GlobalBannerTools globalBannerTools(GlobalBannerService globalBannerService) {
      McpServerProperties properties = new McpServerProperties();
      properties.setReadOnly(false);
      return new GlobalBannerTools(
          globalBannerService, new GlobalBannerProperties(), new McpAccessGuard(properties));
    }
  }

  @Test
  void listGlobalBannersRejectsNonAdminCallerThroughTheSecurityProxy() {
    Authentication authentication = new TestingAuthenticationToken("test-user", "N/A", "ROLE_USER");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(MethodSecurityConfig.class, ToolConfig.class);
      context.registerBean(GlobalBannerService.class, () -> globalBannerService);
      context.refresh();

      AdminToolsAuthorizationTest.FakeFiatPermissionEvaluator evaluator =
          context.getBean(AdminToolsAuthorizationTest.FakeFiatPermissionEvaluator.class);
      evaluator.admin = false;

      GlobalBannerTools proxiedBannerTools = context.getBean(GlobalBannerTools.class);

      assertThatThrownBy(proxiedBannerTools::listGlobalBanners)
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> proxiedBannerTools.deleteGlobalBanner("b1"))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(
              () ->
                  proxiedBannerTools.setGlobalBanner(
                      "b1", "hello", true, null, null, null, null, null))
          .isInstanceOf(AccessDeniedException.class);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void listGlobalBannersAllowsAdminCallerThroughTheSecurityProxy() {
    org.mockito.Mockito.when(globalBannerService.getAllBanners()).thenReturn(List.of());

    Authentication authentication =
        new TestingAuthenticationToken("admin-user", "N/A", "ROLE_ADMIN");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(MethodSecurityConfig.class, ToolConfig.class);
      context.registerBean(GlobalBannerService.class, () -> globalBannerService);
      context.refresh();

      AdminToolsAuthorizationTest.FakeFiatPermissionEvaluator evaluator =
          context.getBean(AdminToolsAuthorizationTest.FakeFiatPermissionEvaluator.class);
      evaluator.admin = true;

      GlobalBannerTools proxiedBannerTools = context.getBean(GlobalBannerTools.class);

      proxiedBannerTools.listGlobalBanners();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
