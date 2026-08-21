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
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.OrchestrationJobs;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import retrofit2.mock.Calls;

/**
 * Verifies that {@code list_applications} is actually filtered by Fiat's {@code @PostFilter}
 * annotation when invoked through a real Spring AOP proxy - the exact mechanism that protects it in
 * production (gate-core's {@code SpringSecurityAnnotationConfig} enables
 * {@code @EnableGlobalMethodSecurity(prePostEnabled = true)} globally, and {@code ApplicationTools}
 * is registered as a Spring bean by {@code McpServerAutoConfiguration}).
 *
 * <p>{@link ApplicationToolsTest#listApplicationsFiltersByOwner} calls the method directly on a
 * plain object and so cannot exercise the AOP proxy; this test stands up a minimal Spring context
 * with method security enabled to prove the annotation is wired correctly, not just present in
 * source.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationToolsAuthorizationTest {

  @Mock private Front50Service front50Service;
  @Mock private TaskService taskService;

  @Configuration
  @EnableGlobalMethodSecurity(prePostEnabled = true)
  static class MethodSecurityConfig {
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
          // Mirrors Fiat: the test user is only authorized to READ "app-a".
          return "app-a".equals(targetId);
        }
      };
    }
  }

  @Configuration
  static class ToolConfig {
    @Bean
    ApplicationTools applicationTools(Front50Service front50Service, TaskService taskService) {
      McpServerProperties properties = new McpServerProperties();
      properties.setReadOnly(false);
      OrchestrationJobs orchestrationJobs =
          new OrchestrationJobs(taskService, new McpAccessGuard(properties));
      return new ApplicationTools(front50Service, orchestrationJobs);
    }
  }

  @Test
  void listApplicationsIsFilteredToOnlyAuthorizedApplicationsThroughTheSecurityProxy() {
    when(front50Service.getAllApplicationsUnrestricted())
        .thenReturn(
            Calls.response(
                List.of(
                    Map.of("name", "app-a", "email", "owner@example.com"),
                    Map.of("name", "app-b", "email", "owner@example.com"))));

    Authentication authentication = new TestingAuthenticationToken("test-user", "N/A", "ROLE_USER");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(MethodSecurityConfig.class, ToolConfig.class);
      context.registerBean(Front50Service.class, () -> front50Service);
      context.registerBean(TaskService.class, () -> taskService);
      context.refresh();

      ApplicationTools proxiedApplicationTools = context.getBean(ApplicationTools.class);

      List<Map<String, Object>> result = proxiedApplicationTools.listApplications(null);

      assertThat(result).extracting(app -> app.get("name")).containsExactly("app-a");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
