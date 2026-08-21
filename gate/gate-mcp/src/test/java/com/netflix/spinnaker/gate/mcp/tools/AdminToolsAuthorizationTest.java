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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
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
import retrofit2.mock.Calls;

/**
 * Verifies {@code cancel_zombie_pipeline}'s {@code @PreAuthorize("@fiatPermissionEvaluator
 * .isAdmin()")} is actually enforced through a real Spring AOP proxy - orca's own {@code
 * /admin/forceCancelExecution} endpoint has no authorization of its own (see {@link AdminTools}'s
 * class Javadoc), so this annotation is the *only* thing standing between any authenticated MCP
 * caller and force-cancelling an arbitrary execution. A plain unit test calling the method directly
 * (as {@link AdminToolsTest} does) can't exercise this - it needs a real proxy, exactly like {@code
 * ApplicationToolsAuthorizationTest} for {@code list_applications}.
 */
@ExtendWith(MockitoExtension.class)
class AdminToolsAuthorizationTest {

  @Mock private OrcaServiceSelector orcaServiceSelector;
  @Mock private OrcaService orcaService;

  @Configuration
  @EnableGlobalMethodSecurity(prePostEnabled = true)
  static class MethodSecurityConfig {
    @Bean(name = "fiatPermissionEvaluator")
    FakeFiatPermissionEvaluator fiatPermissionEvaluator() {
      return new FakeFiatPermissionEvaluator();
    }
  }

  static class FakeFiatPermissionEvaluator {
    boolean admin = false;

    public boolean isAdmin() {
      return admin;
    }
  }

  @Configuration
  static class ToolConfig {
    @Bean
    AdminTools adminTools(OrcaServiceSelector orcaServiceSelector) {
      McpServerProperties properties = new McpServerProperties();
      properties.setReadOnly(false);
      return new AdminTools(orcaServiceSelector, new McpAccessGuard(properties));
    }
  }

  @Test
  void cancelZombiePipelineRejectsNonAdminCallerThroughTheSecurityProxy() {
    Authentication authentication = new TestingAuthenticationToken("test-user", "N/A", "ROLE_USER");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(MethodSecurityConfig.class, ToolConfig.class);
      context.registerBean(OrcaServiceSelector.class, () -> orcaServiceSelector);
      context.refresh();

      FakeFiatPermissionEvaluator evaluator = context.getBean(FakeFiatPermissionEvaluator.class);
      evaluator.admin = false;

      AdminTools proxiedAdminTools = context.getBean(AdminTools.class);

      assertThatThrownBy(() -> proxiedAdminTools.cancelZombiePipeline("exec-1", null))
          .isInstanceOf(AccessDeniedException.class);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void cancelZombiePipelineAllowsAdminCallerThroughTheSecurityProxy() {
    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.forceCancelPipeline(any(), any(), any()))
        .thenReturn(Calls.response((Void) null));

    Authentication authentication =
        new TestingAuthenticationToken("admin-user", "N/A", "ROLE_ADMIN");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(MethodSecurityConfig.class, ToolConfig.class);
      context.registerBean(OrcaServiceSelector.class, () -> orcaServiceSelector);
      context.refresh();

      FakeFiatPermissionEvaluator evaluator = context.getBean(FakeFiatPermissionEvaluator.class);
      evaluator.admin = true;

      AdminTools proxiedAdminTools = context.getBean(AdminTools.class);

      proxiedAdminTools.cancelZombiePipeline("exec-1", null);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
