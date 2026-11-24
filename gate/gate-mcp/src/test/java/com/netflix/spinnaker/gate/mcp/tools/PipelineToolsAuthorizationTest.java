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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import retrofit2.mock.Calls;

/**
 * Verifies that {@code trigger_pipeline} requires EXECUTE on the application through a real Spring
 * AOP proxy, mirroring gate-web's {@code PipelineController} ({@code hasPermission(#application,
 * 'APPLICATION', 'EXECUTE')}).
 */
class PipelineToolsAuthorizationTest {

  private static final Front50Service FRONT50 = mock(Front50Service.class);
  private static final OrcaService ORCA = mock(OrcaService.class);

  @Configuration
  @EnableMethodSecurity(prePostEnabled = true)
  static class MethodSecurityConfig {
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
        PermissionEvaluator permissionEvaluator) {
      var handler = new DefaultMethodSecurityExpressionHandler();
      handler.setPermissionEvaluator(permissionEvaluator);
      return handler;
    }

    @Bean
    PermissionEvaluator permissionEvaluator() {
      return new PermissionEvaluator() {
        @Override
        public boolean hasPermission(
            Authentication authentication, Object targetDomainObject, Object permission) {
          return false;
        }

        @Override
        public boolean hasPermission(
            Authentication authentication,
            java.io.Serializable targetId,
            String targetType,
            Object permission) {
          // Mirrors Fiat: the test user may only EXECUTE "app-a".
          return "app-a".equals(targetId) && "EXECUTE".equals(permission);
        }
      };
    }
  }

  @Configuration
  static class ToolConfig {
    @Bean
    PipelineTools pipelineTools() {
      McpServerProperties properties = new McpServerProperties();
      properties.setReadOnly(false);
      OrcaServiceSelector selector = mock(OrcaServiceSelector.class);
      when(selector.select()).thenReturn(ORCA);
      return new PipelineTools(selector, FRONT50, new McpAccessGuard(properties));
    }
  }

  @Test
  void triggerPipelineRequiresExecuteOnTheApplication() {
    Authentication authentication = new TestingAuthenticationToken("test-user", "N/A", "ROLE_USER");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(MethodSecurityConfig.class, ToolConfig.class);
      context.refresh();
      PipelineTools tools = context.getBean(PipelineTools.class);

      assertThatThrownBy(() -> tools.triggerPipeline("app-b", "my-pipeline", null))
          .isInstanceOf(AccessDeniedException.class);
      verifyNoInteractions(FRONT50, ORCA);

      when(FRONT50.getPipelineConfigByApplicationAndName("app-a", "my-pipeline", true))
          .thenReturn(
              Calls.response(new HashMap<>(Map.of("name", "my-pipeline", "application", "app-a"))));
      when(ORCA.startPipeline(any(), any()))
          .thenReturn(Calls.response(Map.of("ref", "/pipelines/01")));
      assertThat(tools.triggerPipeline("app-a", "my-pipeline", null))
          .containsEntry("ref", "/pipelines/01");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
