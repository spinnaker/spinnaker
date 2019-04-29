/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.fiat.controllers;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.config.FiatServerConfigurationProperties;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.swagger.annotations.ApiOperation;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/authorize")
public class AuthorizeController {

  private final Registry registry;
  private final PermissionsRepository permissionsRepository;
  private final FiatServerConfigurationProperties configProps;

  private final Id getUserPermissionCounterId;

  @Autowired
  public AuthorizeController(
      Registry registry,
      PermissionsRepository permissionsRepository,
      FiatServerConfigurationProperties configProps) {
    this.registry = registry;
    this.permissionsRepository = permissionsRepository;
    this.configProps = configProps;

    this.getUserPermissionCounterId = registry.createId("fiat.getUserPermission");
  }

  @ApiOperation(
      value =
          "Used mostly for testing. Not really any real value to the rest of "
              + "the system. Disabled by default.")
  @RequestMapping(method = RequestMethod.GET)
  public Set<UserPermission.View> getAll(HttpServletResponse response) throws IOException {
    if (!configProps.isGetAllEnabled()) {
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "/authorize is disabled");
      return null;
    }

    log.debug("UserPermissions requested for all users");
    return permissionsRepository.getAllById().values().stream()
        .map(UserPermission::getView)
        .map(
            u ->
                u.setAllowAccessToUnknownApplications(
                    configProps.isAllowAccessToUnknownApplications()))
        .collect(Collectors.toSet());
  }

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.GET)
  public UserPermission.View getUserPermission(@PathVariable String userId) {
    return getUserPermissionView(userId);
  }

  @RequestMapping(value = "/{userId:.+}/accounts", method = RequestMethod.GET)
  public Set<Account.View> getUserAccounts(@PathVariable String userId) {
    return new HashSet<>(getUserPermissionView(userId).getAccounts());
  }

  @RequestMapping(value = "/{userId:.+}/roles", method = RequestMethod.GET)
  public Set<Role.View> getUserRoles(@PathVariable String userId) {
    return new HashSet<>(getUserPermissionView(userId).getRoles());
  }

  @RequestMapping(value = "/{userId:.+}/accounts/{accountName:.+}", method = RequestMethod.GET)
  public Account.View getUserAccount(
      @PathVariable String userId, @PathVariable String accountName) {
    return getUserPermissionView(userId).getAccounts().stream()
        .filter(account -> accountName.equalsIgnoreCase(account.getName()))
        .findFirst()
        .orElseThrow(NotFoundException::new);
  }

  @RequestMapping(value = "/{userId:.+}/applications", method = RequestMethod.GET)
  public Set<Application.View> getUserApplications(@PathVariable String userId) {
    return new HashSet<>(getUserPermissionView(userId).getApplications());
  }

  @RequestMapping(
      value = "/{userId:.+}/applications/{applicationName:.+}",
      method = RequestMethod.GET)
  public Application.View getUserApplication(
      @PathVariable String userId, @PathVariable String applicationName) {
    return getUserPermissionView(userId).getApplications().stream()
        .filter(application -> applicationName.equalsIgnoreCase(application.getName()))
        .findFirst()
        .orElseThrow(NotFoundException::new);
  }

  @RequestMapping(value = "/{userId:.+}/serviceAccounts", method = RequestMethod.GET)
  public Set<ServiceAccount.View> getServiceAccounts(@PathVariable String userId) {
    return new HashSet<>(getUserPermissionView(userId).getServiceAccounts());
  }

  @RequestMapping(
      value = "/{userId:.+}/serviceAccounts/{serviceAccountName:.+}",
      method = RequestMethod.GET)
  public ServiceAccount.View getServiceAccount(
      @PathVariable String userId, @PathVariable String serviceAccountName) {
    return getUserPermissionOrDefault(userId).orElseThrow(NotFoundException::new).getView()
        .getServiceAccounts().stream()
        .filter(
            serviceAccount ->
                serviceAccount
                    .getName()
                    .equalsIgnoreCase(ControllerSupport.convert(serviceAccountName)))
        .findFirst()
        .orElseThrow(NotFoundException::new);
  }

  @RequestMapping(
      value = "/{userId:.+}/{resourceType:.+}/{resourceName:.+}/{authorization:.+}",
      method = RequestMethod.GET)
  public void getUserAuthorization(
      @PathVariable String userId,
      @PathVariable String resourceType,
      @PathVariable String resourceName,
      @PathVariable String authorization,
      HttpServletResponse response)
      throws IOException {
    Authorization a = Authorization.valueOf(authorization.toUpperCase());
    ResourceType r = ResourceType.parse(resourceType);
    Set<Authorization> authorizations = new HashSet<>(0);

    try {
      switch (r) {
        case ACCOUNT:
          authorizations = getUserAccount(userId, resourceName).getAuthorizations();
          break;
        case APPLICATION:
          authorizations = getUserApplication(userId, resourceName).getAuthorizations();
          break;
        default:
          response.sendError(
              HttpServletResponse.SC_BAD_REQUEST,
              "Resource type " + resourceType + " does not contain authorizations");
          return;
      }
    } catch (NotFoundException nfe) {
      // Ignore. Will return 404 below.
    }

    if (authorizations.contains(a)) {
      response.setStatus(HttpServletResponse.SC_OK);
      return;
    }

    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  private Optional<UserPermission> getUserPermissionOrDefault(String userId) {
    String authenticatedUserId = AuthenticatedRequest.getSpinnakerUser().orElse(null);

    UserPermission userPermission =
        permissionsRepository.get(ControllerSupport.convert(userId)).orElse(null);

    if (userPermission != null || !configProps.isDefaultToUnrestrictedUser()) {
      registry
          .counter(
              getUserPermissionCounterId
                  .withTag("success", userPermission != null)
                  .withTag("fallback", false))
          .increment();
      return Optional.ofNullable(userPermission);
    }

    if (userId.equalsIgnoreCase(authenticatedUserId)) {
      /*
       * User does not have any stored permissions, likely a request that has not transited gate.
       *
       * If the request is for the permissions of the currently authenticated user, default to those of the
       * unrestricted user.
       */
      userPermission =
          permissionsRepository
              .get(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME)
              .map(u -> u.setId(authenticatedUserId))
              .orElse(null);
    }

    log.debug(
        "Returning fallback permissions (user: {}, accounts: {}, roles: {})",
        userId,
        (userPermission != null) ? userPermission.getAccounts() : Collections.emptyList(),
        (userPermission != null)
            ? userPermission.getRoles().stream().map(Role::getName).collect(Collectors.toList())
            : Collections.emptyList());

    registry
        .counter(
            getUserPermissionCounterId
                .withTag("success", userPermission != null)
                .withTag("fallback", true))
        .increment();

    return Optional.ofNullable(userPermission);
  }

  private UserPermission.View getUserPermissionView(String userId) {
    return getUserPermissionOrDefault(userId)
        .orElseThrow(NotFoundException::new)
        .getView()
        .setAllowAccessToUnknownApplications(configProps.isAllowAccessToUnknownApplications());
  }
}
