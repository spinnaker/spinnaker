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

import com.netflix.spinnaker.fiat.model.PermissionsRepository;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/authorize")
public class AuthorizeController {

  @Autowired
  private PermissionsRepository permissionsRepository;

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.GET)
  public UserPermission getUserPermission(@PathVariable String userId) {
    return Optional.ofNullable(permissionsRepository.get(userId)).orElseThrow(NotFoundException::new);
  }

  @RequestMapping(value = "/{userId:.+}/accounts", method = RequestMethod.GET)
  public Set<Account> getUserAccounts(@PathVariable String userId) {
    return getUserPermission(userId).getAccounts();
  }

  @RequestMapping(value = "/{userId:.+}/accounts/{accountName:.+}", method = RequestMethod.GET)
  public Account getUserAccount(@PathVariable String userId, @PathVariable String accountName) {
    return getUserAccounts(userId)
        .stream()
        .filter(account -> accountName.equalsIgnoreCase(account.getName()))
        .findFirst()
        .orElseThrow(NotFoundException::new);
  }

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.PUT)
  public void putUserPermission(@PathVariable String userId, @RequestBody @NonNull UserPermission permission) {
    permissionsRepository.put(userId, permission);
  }
}
