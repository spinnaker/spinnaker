/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.mcp.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.gate.mcp.support.McpAuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the audit-log resources' {@code @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")} is
 * enforced through a real Spring AOP proxy. The log spans every application and names the user
 * behind each action, so a non-admin caller must not be able to read it. A plain unit test calling
 * the methods directly (as {@link McpAuditLogResourceTest} does) can't exercise this.
 */
class McpAuditLogResourceAuthorizationTest {

  @Configuration
  @EnableMethodSecurity(prePostEnabled = true)
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
  static class ResourceConfig {
    @Bean
    McpAuditLogResource mcpAuditLogResource() {
      McpAuditLog auditLog = new McpAuditLog(10);
      auditLog.record("create_application", "app-a");
      return new McpAuditLogResource(auditLog, JsonMapper.builder().build());
    }
  }

  private static void authenticateAs(String user) {
    Authentication authentication = new TestingAuthenticationToken(user, "N/A", "ROLE_USER");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private static AnnotationConfigApplicationContext newContext() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(MethodSecurityConfig.class, ResourceConfig.class);
    context.refresh();
    return context;
  }

  @Test
  void nonAdminCannotReadTheAuditLog() {
    authenticateAs("test-user");

    try (AnnotationConfigApplicationContext context = newContext()) {
      context.getBean(FakeFiatPermissionEvaluator.class).admin = false;
      McpAuditLogResource proxied = context.getBean(McpAuditLogResource.class);

      assertThatThrownBy(proxied::auditLog).isInstanceOf(AccessDeniedException.class);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void nonAdminCannotReadTheAuditLogForAnApplication() {
    authenticateAs("test-user");

    try (AnnotationConfigApplicationContext context = newContext()) {
      context.getBean(FakeFiatPermissionEvaluator.class).admin = false;
      McpAuditLogResource proxied = context.getBean(McpAuditLogResource.class);

      assertThatThrownBy(() -> proxied.auditLogForApplication("app-a"))
          .isInstanceOf(AccessDeniedException.class);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void adminCanReadTheAuditLog() {
    authenticateAs("admin-user");

    try (AnnotationConfigApplicationContext context = newContext()) {
      context.getBean(FakeFiatPermissionEvaluator.class).admin = true;
      McpAuditLogResource proxied = context.getBean(McpAuditLogResource.class);

      assertThat(proxied.auditLog()).contains("create_application");
      assertThat(proxied.auditLogForApplication("app-a")).contains("create_application");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
