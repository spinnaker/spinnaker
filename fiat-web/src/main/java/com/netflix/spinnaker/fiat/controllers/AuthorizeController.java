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
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/authorize")
public class AuthorizeController {

  @Autowired
  private PermissionsRepository permissionsRepository;

  @RequestMapping(value = "/{userId:.+}", method = RequestMethod.GET)
  public UserPermission.View getUserPermission(@PathVariable String userId) {
    return permissionsRepository.get(userId)
                                .orElseThrow(NotFoundException::new)
                                .getView();
  }

  @RequestMapping(value = "/{userId:.+}/accounts", method = RequestMethod.GET)
  public Set<Account.View> getUserAccounts(@PathVariable String userId) {
    return permissionsRepository.get(userId)
                                .orElseThrow(NotFoundException::new)
                                .getAccounts()
                                .stream()
                                .map(Account::getView)
                                .collect(Collectors.toSet());
  }

  @RequestMapping(value = "/{userId:.+}/accounts/{accountName:.+}", method = RequestMethod.GET)
  public Account.View getUserAccount(@PathVariable String userId, @PathVariable String accountName) {
    return permissionsRepository.get(userId)
                                .orElseThrow(NotFoundException::new)
                                .getAccounts()
                                .stream()
                                .filter(account -> accountName.equalsIgnoreCase(account.getName()))
                                .findFirst()
                                .map(Account::getView)
                                .orElseThrow(NotFoundException::new);
  }
}
