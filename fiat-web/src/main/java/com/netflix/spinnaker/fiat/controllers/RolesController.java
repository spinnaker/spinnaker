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

import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import com.netflix.spinnaker.fiat.roles.UserRolesSyncer;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/roles")
public class RolesController {

  @Autowired
  @Setter
  PermissionsResolver permissionsResolver;

  @Autowired
  @Setter
  PermissionsRepository permissionsRepository;

  @Autowired
  @Setter
  UserRolesSyncer syncer;

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.POST)
  public void putUserPermission(@PathVariable String userId) {
    permissionsRepository.put(
        permissionsResolver.resolve(ControllerSupport.decode(userId))
                           .orElseThrow(UserPermissionModificationException::new)
    );
  }

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.PUT)
  public void putUserPermission(@PathVariable String userId,
                                @RequestBody @NonNull List<String> externalRoles) {
    Set<Role> convertedRoles = externalRoles
        .stream()
        .map(extRole -> new Role().setSource(Role.Source.EXTERNAL).setName(extRole))
        .collect(Collectors.toSet());
    permissionsRepository.put(
        permissionsResolver.resolveAndMerge(ControllerSupport.decode(userId), convertedRoles)
                           .orElseThrow(UserPermissionModificationException::new)
    );
  }

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.DELETE)
  public void deleteUserPermission(@PathVariable String userId) {
    permissionsRepository.remove(ControllerSupport.decode(userId));
  }

  @RequestMapping(value = "/sync", method = RequestMethod.POST)
  public long sync(HttpServletResponse response,
                   @RequestBody List<String> specificRoles) throws IOException {
    if (specificRoles == null) {
      long count = syncer.syncAndReturn();
      if (count == 0) {
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           "Error occurred syncing permissions. See Fiat Logs.");
      }
      return count;
    }

    Map<String, UserPermission> affectedUsers = permissionsRepository.getAllByRoles(specificRoles);
    if (affectedUsers.size() == 0) {
      return 0;
    }
    return syncer.updateUserPermissions(affectedUsers);
  }
}
