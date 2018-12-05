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
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/security")
public class SecurityController {
  private final HalconfigParser halconfigParser;
  private final SecurityService securityService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Security> getSecurity(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Security>builder()
        .getter(() -> securityService.getSecurity(deploymentName))
        .validator(() -> securityService.validateSecurity(deploymentName))
        .description("Get all security settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/ui/", method = RequestMethod.GET)
  DaemonTask<Halconfig, UiSecurity> getUiSecurity(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<UiSecurity>builder()
        .getter(() -> securityService.getUiSecurity(deploymentName))
        .validator(() -> securityService.validateUiSecurity(deploymentName))
        .description("Get UI security settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/ui/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setUiSecurity(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody UiSecurity uiSecurity) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> uiSecurity.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setUiSecurity(deploymentName, uiSecurity));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateUiSecurity(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit UI security settings");
  }

  @RequestMapping(value = "/ui/ssl/", method = RequestMethod.GET)
  DaemonTask<Halconfig, ApacheSsl> getApacheSsl(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<ApacheSsl>builder()
        .getter(() -> securityService.getApacheSsl(deploymentName))
        .validator(() -> securityService.validateApacheSsl(deploymentName))
        .description("Get UI SSL settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/ui/ssl/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setApacheSSl(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody ApacheSsl apacheSsl) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> apacheSsl.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setApacheSsl(deploymentName, apacheSsl));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateApacheSsl(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit UI SSL settings");
  }

  @RequestMapping(value = "/ui/ssl/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setApacheSSlEnabled(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setApacheSslEnabled(deploymentName, enabled));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateApacheSsl(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit UI SSL settings");
  }

  @RequestMapping(value = "/api/", method = RequestMethod.GET)
  DaemonTask<Halconfig, ApiSecurity> getApiSecurity(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<ApiSecurity>builder()
        .getter(() -> securityService.getApiSecurity(deploymentName))
        .validator(() -> securityService.validateApiSecurity(deploymentName))
        .description("Get API security settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/api/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setApiSecurity(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody ApiSecurity apiSecurity) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> apiSecurity.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setApiSecurity(deploymentName, apiSecurity));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateApiSecurity(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit API security settings");
  }

  @RequestMapping(value = "/api/ssl/", method = RequestMethod.GET)
  DaemonTask<Halconfig, SpringSsl> getSpringSsl(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<SpringSsl>builder()
        .getter(() -> securityService.getSpringSsl(deploymentName))
        .validator(() -> securityService.validateSpringSsl(deploymentName))
        .description("Get API SSL settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/api/ssl/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSpringSSl(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody SpringSsl apacheSsl) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> apacheSsl.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setSpringSsl(deploymentName, apacheSsl));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateSpringSsl(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit API SSL settings");
  }

  @RequestMapping(value = "/api/ssl/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setSpringSSlEnabled(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setSpringSslEnabled(deploymentName, enabled));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateSpringSsl(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit API SSL settings");
  }

  @RequestMapping(value = "/authz/groupMembership", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setGroupMembership(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody GroupMembership membership) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> membership.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setGroupMembership(deploymentName, membership));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateAuthz(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit group membership settings");
  }

  @RequestMapping(value = "/authz/groupMembership", method = RequestMethod.GET)
  DaemonTask<Halconfig, GroupMembership> getGroupMembership(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<GroupMembership>builder()
        .getter(() -> securityService.getGroupMembership(deploymentName))
        .validator(() -> securityService.validateAuthz(deploymentName))
        .description("Get group membership settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/authn/{methodName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, AuthnMethod> getAuthmethod(@PathVariable String deploymentName,
      @PathVariable String methodName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<AuthnMethod>builder()
        .getter(() -> securityService.getAuthnMethod(deploymentName, methodName))
        .validator(() -> securityService.validateAuthnMethod(deploymentName, methodName))
        .description("Get authentication settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/authz/groupMembership/{roleProviderName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, RoleProvider> getRoleProvider(@PathVariable String deploymentName,
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
  DaemonTask<Halconfig, Void> setSecurity(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Security security) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> security.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setSecurity(deploymentName, security));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateSecurity(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit security settings");
  }

  @RequestMapping(value = "/authn/{methodName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setAuthnMethod(@PathVariable String deploymentName,
      @PathVariable String methodName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawMethod) {
    AuthnMethod method = objectMapper.convertValue(
        rawMethod,
        AuthnMethod.translateAuthnMethodName(methodName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> method.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setAuthnMethod(deploymentName, method));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateAuthnMethod(deploymentName, methodName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler
        .submitTask(builder::build, "Edit " + methodName + " authentication settings");
  }

  @RequestMapping(value = "/authz/groupMembership/{roleProviderName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setRoleProvider(@PathVariable String deploymentName,
      @PathVariable String roleProviderName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawProvider) {
    RoleProvider roleProvider = objectMapper.convertValue(
        rawProvider,
        GroupMembership.translateRoleProviderType(roleProviderName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> roleProvider.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());
    builder.setUpdate(() -> securityService.setRoleProvider(deploymentName, roleProvider));

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(
          () -> securityService.validateRoleProvider(deploymentName, roleProviderName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler
        .submitTask(builder::build, "Edit " + roleProviderName + " group membership settings");
  }

  @RequestMapping(value = "/authn/{methodName:.+}/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMethodEnabled(@PathVariable String deploymentName,
      @PathVariable String methodName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(
        () -> securityService.setAuthnMethodEnabled(deploymentName, methodName, enabled));
    builder.setSeverity(validationSettings.getSeverity());

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateAuthnMethod(deploymentName, methodName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler
        .submitTask(builder::build, "Edit " + methodName + " authentication settings");
  }

  @RequestMapping(value = "/authz/enabled/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setMethodEnabled(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> securityService.setAuthzEnabled(deploymentName, enabled));
    builder.setSeverity(validationSettings.getSeverity());

    builder.setValidate(ProblemSet::new);
    if (validationSettings.isValidate()) {
      builder.setValidate(() -> securityService.validateAuthz(deploymentName));
    }

    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit authorization settings");
  }
}
