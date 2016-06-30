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

import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import com.netflix.spinnaker.fiat.roles.UserRolesSyncer;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    permissionsRepository.put(userId, permissionsResolver.resolve(userId));
  }

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.PUT)
  public void putUserPermission(@PathVariable String userId,
                                @RequestBody @NonNull List<String> externalRoles) {
    // TODO(ttomsu): Add role merging capability to permissionsRepo.
  }

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.DELETE)
  public void deleteUserPermission(@PathVariable String userId) {
    permissionsRepository.remove(userId);
  }

  @RequestMapping(value = "/sync", method = RequestMethod.POST)
  public void sync() {
    syncer.sync();
  }
}
