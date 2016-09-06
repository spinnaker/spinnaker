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

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import io.swagger.annotations.ApiOperation;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/authorize")
public class AuthorizeController {

  @Autowired
  private PermissionsRepository permissionsRepository;

  @Value("${auth.getAll.enabled:false}")
  @Setter
  private boolean getAllEnabled;

  @ApiOperation(value = "Used mostly for testing. Not really any real value to the rest of " +
      "the system. Disabled by default.")
  @RequestMapping(method = RequestMethod.GET)
  public Set<UserPermission.View> getAll() {
    if (!getAllEnabled) {
      return new HashSet<>(0);
    }

    return permissionsRepository
        .getAllById()
        .values()
        .stream()
        .map(UserPermission::getView)
        .collect(Collectors.toSet());
  }

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.GET)
  public UserPermission.View getUserPermission(@PathVariable String userId) {
    return permissionsRepository.get(ControllerSupport.decode(userId))
                                .orElseThrow(NotFoundException::new)
                                .getView();
  }

  @RequestMapping(value = "/{userId:.+}/accounts", method = RequestMethod.GET)
  public Set<Account.View> getUserAccounts(@PathVariable String userId) {
    return permissionsRepository.get(ControllerSupport.decode(userId))
                                .orElseThrow(NotFoundException::new)
                                .getAccounts()
                                .stream()
                                .map(Account::getView)
                                .collect(Collectors.toSet());
  }

  @RequestMapping(value = "/{userId:.+}/accounts/{accountName:.+}", method = RequestMethod.GET)
  public Account.View getUserAccount(@PathVariable String userId, @PathVariable String accountName) {
    return permissionsRepository.get(ControllerSupport.decode(userId))
                                .orElseThrow(NotFoundException::new)
                                .getAccounts()
                                .stream()
                                .filter(account -> accountName.equalsIgnoreCase(account.getName()))
                                .findFirst()
                                .map(Account::getView)
                                .orElseThrow(NotFoundException::new);
  }

  @RequestMapping(value = "/{userId:.+}/{resourceType:.+}/{resourceName:.+}/{authorization:.+}", method = RequestMethod.GET)
  public void getUserAuthorization(@PathVariable String userId,
                                   @PathVariable String resourceType,
                                   @PathVariable String resourceName,
                                   @PathVariable String authorization,
                                   HttpServletResponse response) throws IOException {
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
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Resource type " + resourceType +
              " does not contain authorizations");
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

  @RequestMapping(value = "/{userId:.+}/applications", method = RequestMethod.GET)
  public Set<Application.View> getUserApplications(@PathVariable String userId) {
    return permissionsRepository.get(ControllerSupport.decode(userId))
                                .orElseThrow(NotFoundException::new)
                                .getApplications()
                                .stream()
                                .map(Application::getView)
                                .collect(Collectors.toSet());
  }

  @RequestMapping(value = "/{userId:.+}/applications/{applicationName:.+}", method = RequestMethod.GET)
  public Application.View getUserApplication(@PathVariable String userId, @PathVariable String applicationName) {
    return permissionsRepository.get(ControllerSupport.decode(userId))
                                .orElseThrow(NotFoundException::new)
                                .getApplications()
                                .stream()
                                .filter(application -> applicationName.equalsIgnoreCase(application.getName()))
                                .findFirst()
                                .map(Application::getView)
                                .orElseThrow(NotFoundException::new);
  }
}
