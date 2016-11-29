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

import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/config/deployments/{deployment:.+}/providers/{provider:.+}/accounts")
public class AccountController {
  @Autowired
  AccountService accountService;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  List<Account> accounts(@PathVariable String deployment, @PathVariable String provider) {
    NodeReference reference = new NodeReference()
        .setDeployment(deployment)
        .setProvider(provider);

    return accountService.getAllAccounts(reference);

  }

  @RequestMapping(value = "/{account:.+}", method = RequestMethod.GET)
  Account account(
      @PathVariable String deployment,
      @PathVariable String provider,
      @PathVariable String account,
      @RequestParam(required = false, defaultValue = "false") boolean validate) {
    NodeReference reference = new NodeReference()
        .setDeployment(deployment)
        .setProvider(provider)
        .setAccount(account);

    if (validate) {
      accountService.validateAccount(reference);
    }

    return accountService.getAccount(reference);
  }
}
