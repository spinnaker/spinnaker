/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.security.*;
import com.netflix.spinnaker.halyard.config.services.v1.SecurityService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericEnableDisableRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/security")
public class SecurityController {
  private final HalconfigParser halconfigParser;
  private final SecurityService securityService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Security> getSecurity(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Security>builder()
        .getter(() -> securityService.getSecurity(deploymentName))
        .validator(() -> securityService.validateSecurity(deploymentName))
        .description("Get all security settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/ui/", method = RequestMethod.GET)
  DaemonTask<Halconfig, UiSecurity> getUiSecurity(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<UiSecurity>builder()
        .getter(() -> securityService.getUiSecurity(deploymentName))
        .validator(() -> securityService.validateUiSecurity(deploymentName))
        .description("Get UI security settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/ui/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setUiSecurity(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody UiSecurity uiSecurity) {
    return GenericUpdateRequest.<UiSecurity>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(u -> securityService.setUiSecurity(deploymentName, u))
        .validator(() -> securityService.validateUiSecurity(deploymentName))
        .description("Edit UI security settings")
        .build()
        .execute(validationSettings, uiSecurity);
  }

  @RequestMapping(value = "/ui/ssl/", method = RequestMethod.GET)
  DaemonTask<Halconfig, ApacheSsl> getApacheSsl(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<ApacheSsl>builder()
        .getter(() -> securityService.getApacheSsl(deploymentName))
        .validator(() -> securityService.validateApacheSsl(deploymentName))
        .description("Get UI SSL settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/ui/ssl/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setApacheSSl(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody ApacheSsl apacheSsl) {
    return GenericUpdateRequest.<ApacheSsl>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(a -> securityService.setApacheSsl(deploymentName, a))
        .validator(() -> securityService.validateApacheSsl(deploymentName))
        .description("Edit UI SSL settings")
        .build()
        .execute(validationSettings, apacheSsl);
  }

  @RequestMapping(value = "/ui/ssl/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setApacheSSlEnabled(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> securityService.setApacheSslEnabled(deploymentName, e))
        .validator(() -> securityService.validateApacheSsl(deploymentName))
        .description("Edit UI SSL settings")
        .build()
        .execute(validationSettings, enabled);
  }

  @RequestMapping(value = "/api/", method = RequestMethod.GET)
  DaemonTask<Halconfig, ApiSecurity> getApiSecurity(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<ApiSecurity>builder()
        .getter(() -> securityService.getApiSecurity(deploymentName))
        .validator(() -> securityService.validateApiSecurity(deploymentName))
        .description("Get API security settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/api/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setApiSecurity(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody ApiSecurity apiSecurity) {
    return GenericUpdateRequest.<ApiSecurity>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(a -> securityService.setApiSecurity(deploymentName, a))
        .validator(() -> securityService.validateApiSecurity(deploymentName))
        .description("Edit API security settings")
        .build()
        .execute(validationSettings, apiSecurity);
  }

  @RequestMapping(value = "/api/ssl/", method = RequestMethod.GET)
  DaemonTask<Halconfig, SpringSsl> getSpringSsl(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<SpringSsl>builder()
        .getter(() -> securityService.getSpringSsl(deploymentName))
        .validator(() -> securityService.validateSpringSsl(deploymentName))
        .description("Get API SSL settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/api/ssl/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSpringSSl(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody SpringSsl apacheSsl) {
    return GenericUpdateRequest.<SpringSsl>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(a -> securityService.setSpringSsl(deploymentName, a))
        .validator(() -> securityService.validateSpringSsl(deploymentName))
        .description("Edit API SSL settings")
        .build()
        .execute(validationSettings, apacheSsl);
  }

  @RequestMapping(value = "/api/ssl/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSpringSSlEnabled(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> securityService.setSpringSslEnabled(deploymentName, e))
        .validator(() -> securityService.validateSpringSsl(deploymentName))
        .description("Edit API SSL settings")
        .build()
        .execute(validationSettings, enabled);
  }

  @RequestMapping(value = "/authz/groupMembership", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setGroupMembership(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody GroupMembership membership) {
    return GenericUpdateRequest.<GroupMembership>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(m -> securityService.setGroupMembership(deploymentName, m))
        .validator(() -> securityService.validateAuthz(deploymentName))
        .description("Edit group membership settings")
        .build()
        .execute(validationSettings, membership);
  }

  @RequestMapping(value = "/authz/groupMembership", method = RequestMethod.GET)
  DaemonTask<Halconfig, GroupMembership> getGroupMembership(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<GroupMembership>builder()
        .getter(() -> securityService.getGroupMembership(deploymentName))
        .validator(() -> securityService.validateAuthz(deploymentName))
        .description("Get group membership settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/authn/{methodName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, AuthnMethod> getAuthmethod(
      @PathVariable String deploymentName,
      @PathVariable String methodName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<AuthnMethod>builder()
        .getter(() -> securityService.getAuthnMethod(deploymentName, methodName))
        .validator(() -> securityService.validateAuthnMethod(deploymentName, methodName))
        .description("Get authentication settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(
      value = "/authz/groupMembership/{roleProviderName:.+}",
      method = RequestMethod.GET)
  DaemonTask<Halconfig, RoleProvider> getRoleProvider(
      @PathVariable String deploymentName,
      @PathVariable String roleProviderName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<RoleProvider>builder()
        .getter(() -> securityService.getRoleProvider(deploymentName, roleProviderName))
        .validator(() -> securityService.validateRoleProvider(deploymentName, roleProviderName))
        .description("Get " + roleProviderName + " group membership settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSecurity(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Security security) {
    return GenericUpdateRequest.<Security>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(s -> securityService.setSecurity(deploymentName, s))
        .validator(() -> securityService.validateSecurity(deploymentName))
        .description("Edit security settings")
        .build()
        .execute(validationSettings, security);
  }

  @RequestMapping(value = "/authn/{methodName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setAuthnMethod(
      @PathVariable String deploymentName,
      @PathVariable String methodName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawMethod) {
    AuthnMethod method =
        objectMapper.convertValue(rawMethod, AuthnMethod.translateAuthnMethodName(methodName));
    return GenericUpdateRequest.<AuthnMethod>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(m -> securityService.setAuthnMethod(deploymentName, m))
        .validator(() -> securityService.validateAuthnMethod(deploymentName, methodName))
        .description("Edit " + methodName + " authentication settings")
        .build()
        .execute(validationSettings, method);
  }

  @RequestMapping(
      value = "/authz/groupMembership/{roleProviderName:.+}",
      method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setRoleProvider(
      @PathVariable String deploymentName,
      @PathVariable String roleProviderName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawProvider) {
    RoleProvider roleProvider =
        objectMapper.convertValue(
            rawProvider, GroupMembership.translateRoleProviderType(roleProviderName));
    return GenericUpdateRequest.<RoleProvider>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(r -> securityService.setRoleProvider(deploymentName, r))
        .validator(() -> securityService.validateRoleProvider(deploymentName, roleProviderName))
        .description("Edit " + roleProviderName + " group membership settings")
        .build()
        .execute(validationSettings, roleProvider);
  }

  @RequestMapping(value = "/authn/{methodName:.+}/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMethodEnabled(
      @PathVariable String deploymentName,
      @PathVariable String methodName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> securityService.setAuthnMethodEnabled(deploymentName, methodName, e))
        .validator(() -> securityService.validateAuthnMethod(deploymentName, methodName))
        .description("Edit " + methodName + " authentication settings")
        .build()
        .execute(validationSettings, enabled);
  }

  @RequestMapping(value = "/authz/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMethodEnabled(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> securityService.setAuthzEnabled(deploymentName, e))
        .validator(() -> securityService.validateAuthz(deploymentName))
        .description("Edit authorization settings")
        .build()
        .execute(validationSettings, enabled);
  }
}
