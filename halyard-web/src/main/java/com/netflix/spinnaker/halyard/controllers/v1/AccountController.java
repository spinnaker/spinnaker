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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.config.services.v1.UpdateService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers/{providerName:.+}/accounts")
public class AccountController {
  @Autowired
  AccountService accountService;

  @Autowired
  UpdateService updateService;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  List<Account> accounts(@PathVariable String deploymentName, @PathVariable String providerName) {
    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName);

    return accountService.getAllAccounts(reference);

  }

  @RequestMapping(value = "/{accountName:.+}", method = RequestMethod.GET)
  Account account(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @RequestParam(required = false, defaultValue = "false") boolean validate) {
    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName)
        .setAccount(accountName);

    Account result = accountService.getAccount(reference);

    if (validate) {
      accountService.validateAccount(reference);
    }

    return result;
  }

  @RequestMapping(value = "/{accountName:.+}", method = RequestMethod.PUT)
  void setAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @RequestParam(required = false, defaultValue = "false") boolean validate,
      @RequestBody Object rawAccount) {
    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName)
        .setAccount(accountName);

    Account account = objectMapper.convertValue(
        rawAccount,
        Providers.translateAccountType(providerName)
    );

    Runnable doUpdate = () -> accountService.setAccount(reference, account);
    Runnable doValidate = () -> {};

    if (validate) {
      doValidate = () -> accountService.validateAccount(reference);
    }

    updateService.safeUpdate(doUpdate, doValidate);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  void addAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = "false") boolean validate,
      @RequestBody Object rawAccount) {
    Account account = objectMapper.convertValue(
        rawAccount,
        Providers.translateAccountType(providerName)
    );

    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName)
        .setAccount(account.getName());

    Runnable doUpdate = () -> accountService.addAccount(reference, account);
    Runnable doValidate = () -> {};

    if (validate) {
      doValidate = () -> accountService.validateAccount(reference);
    }

    updateService.safeUpdate(doUpdate, doValidate);
  }
}
