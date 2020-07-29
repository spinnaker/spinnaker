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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.config.FiatServerConfigurationProperties;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.*;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import com.netflix.spinnaker.fiat.providers.ResourcePermissionProvider;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.swagger.annotations.ApiOperation;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/authorize")
public class AuthorizeController {

  private final Registry registry;
  private final PermissionsRepository permissionsRepository;
  private final PermissionsResolver permissionsResolver;
  private final FiatServerConfigurationProperties configProps;
  private final ResourcePermissionProvider<Application> applicationResourcePermissionProvider;
  private final ObjectMapper objectMapper;
  private final List<Resource> resources;

  private final Id getUserPermissionCounterId;

  @Autowired
  public AuthorizeController(
      Registry registry,
      PermissionsRepository permissionsRepository,
      PermissionsResolver permissionsResolver,
      FiatServerConfigurationProperties configProps,
      ResourcePermissionProvider<Application> applicationResourcePermissionProvider,
      List<Resource> resources,
      ObjectMapper objectMapper) {
    this.registry = registry;
    this.permissionsRepository = permissionsRepository;
    this.permissionsResolver = permissionsResolver;
    this.configProps = configProps;
    this.applicationResourcePermissionProvider = applicationResourcePermissionProvider;
    this.resources = resources;
    this.objectMapper = objectMapper;

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
  public Set<? extends Viewable.BaseView> getServiceAccounts(
      @PathVariable String userId,
      @RequestParam(name = "expand", defaultValue = "false") boolean expand) {
    Set<ServiceAccount.View> serviceAccounts = getUserPermissionView(userId).getServiceAccounts();
    if (!expand) {
      return serviceAccounts;
    }

    if (serviceAccounts.size() > configProps.getMaxExpandedServiceAccounts()) {
      throw new InvalidRequestException(
          String.format(
              "Unable to expand service accounts for user %s. User has %s service accounts. Maximum expandable service accounts is %s.",
              userId, serviceAccounts.size(), configProps.getMaxExpandedServiceAccounts()));
    }

    return serviceAccounts.stream()
        .map(ServiceAccount.View::getName)
        .map(this::getUserPermissionView)
        .collect(Collectors.toSet());
  }

  @RequestMapping(
      value = "/{userId:.+}/serviceAccounts/{serviceAccountName:.+}",
      method = RequestMethod.GET)
  public ServiceAccount.View getServiceAccount(
      @PathVariable String userId, @PathVariable String serviceAccountName) {
    return getUserPermissionOrDefault(userId)
        .orElseThrow(NotFoundException::new)
        .getView()
        .getServiceAccounts()
        .stream()
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
      if (r.equals(ResourceType.ACCOUNT)) {
        authorizations = getUserAccount(userId, resourceName).getAuthorizations();
      } else if (r.equals(ResourceType.APPLICATION)) {
        authorizations = getUserApplication(userId, resourceName).getAuthorizations();
      } else {
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

  @RequestMapping(value = "/{userId:.+}/{resourceType:.+}/create", method = RequestMethod.POST)
  public void canCreate(
      @PathVariable String userId,
      @PathVariable String resourceType,
      @RequestBody @Nonnull Object resource,
      HttpServletResponse response)
      throws IOException {
    ResourceType rt = ResourceType.parse(resourceType);
    if (!rt.equals(ResourceType.APPLICATION)) {
      response.sendError(
          HttpServletResponse.SC_BAD_REQUEST,
          "Resource type " + resourceType + " does not support creation");
      return;
    }

    if (!configProps.isRestrictApplicationCreation()) {
      response.setStatus(HttpServletResponse.SC_OK);
      return;
    }

    UserPermission.View userPermissionView = getUserPermissionView(userId);
    List<String> userRoles =
        userPermissionView.getRoles().stream().map(Role.View::getName).collect(Collectors.toList());

    val modelClazz =
        resources.stream()
            .filter(r -> r.getResourceType().equals(rt))
            .findFirst()
            .orElseThrow(IllegalArgumentException::new)
            .getClass();
    Resource r = objectMapper.convertValue(resource, modelClazz);

    // can easily implement options other than APPLICATION, but it is not currently needed.
    if (userPermissionView.isAdmin()
        || applicationResourcePermissionProvider
            .getPermissions((Application) r)
            .getAuthorizations(userRoles)
            .contains(Authorization.CREATE)) {
      response.setStatus(HttpServletResponse.SC_OK);
    } else {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private Optional<UserPermission> getUserPermissionOrDefault(String userId) {
    String authenticatedUserId = AuthenticatedRequest.getSpinnakerUser().orElse(null);

    UserPermission userPermission =
        permissionsRepository.get(ControllerSupport.convert(userId)).orElse(null);

    if (userPermission != null) {
      registry
          .counter(getUserPermissionCounterId.withTag("success", true).withTag("fallback", false))
          .increment();
      return Optional.of(userPermission);
    }

    /*
     * User does not have any stored permissions but the requested userId matches the
     * X-SPINNAKER-USER header value, likely a request that has not transited gate.
     */
    if (userId.equalsIgnoreCase(authenticatedUserId)) {

      /*
       * First, attempt to resolve via the permissionsResolver.
       */
      if (configProps.isAllowPermissionResolverFallback()) {
        UserPermission resolvedUserPermission = permissionsResolver.resolve(authenticatedUserId);
        if (resolvedUserPermission.getAllResources().stream().anyMatch(Objects::nonNull)) {
          log.debug("Resolved fallback permissions for user {}", authenticatedUserId);
          userPermission = resolvedUserPermission;
        }
      }

      /*
       * If user permissions are not resolved, default to those of the unrestricted user.
       */
      if (userPermission == null && configProps.isDefaultToUnrestrictedUser()) {
        log.debug("Falling back to unrestricted user permissions for user {}", authenticatedUserId);
        userPermission =
            permissionsRepository
                .get(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME)
                .map(u -> u.setId(authenticatedUserId))
                .orElse(null);
      }
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
