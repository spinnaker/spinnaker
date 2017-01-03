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
import com.netflix.spinnaker.halyard.DaemonResponse;
import com.netflix.spinnaker.halyard.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import java.util.List;
import java.util.function.Supplier;
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
  HalconfigParser halconfigParser;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonResponse<List<Account>> accounts(@PathVariable String deploymentName, @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName);

    StaticRequestBuilder<List<Account>> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> accountService.getAllAccounts(reference));

    if (validate) {
      builder.setValidateResponse(() -> accountService.validateAllAccounts(reference, severity));
    }

    return builder.build();
  }

  @RequestMapping(value = "/{accountName:.+}", method = RequestMethod.GET)
  DaemonResponse<Account> account(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName)
        .setAccount(accountName);

    StaticRequestBuilder<Account> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> accountService.getAccount(reference));

    if (validate) {
      builder.setValidateResponse(() -> accountService.validateAccount(reference, severity));
    }

    return builder.build();
  }

  @RequestMapping(value = "/{accountName:.+}", method = RequestMethod.DELETE)
  DaemonResponse<Void> deleteAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName)
        .setAccount(accountName);

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> accountService.deleteAccount(reference));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> accountService.validateAccount(reference, severity);
    }

    builder.setValidate(doValidate);
    builder.setHalconfigParser(halconfigParser);

    return builder.build();
  }

  @RequestMapping(value = "/{accountName:.+}", method = RequestMethod.PUT)
  DaemonResponse<Void> setAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawAccount) {
    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName)
        .setAccount(accountName);

    Account account = objectMapper.convertValue(
        rawAccount,
        Providers.translateAccountType(providerName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> accountService.setAccount(reference, account));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> accountService.validateAccount(reference, severity);
    }

    builder.setValidate(doValidate);
    builder.setHalconfigParser(halconfigParser);

    return builder.build();
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonResponse<Void> addAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawAccount) {
    Account account = objectMapper.convertValue(
        rawAccount,
        Providers.translateAccountType(providerName)
    );

    NodeReference reference = new NodeReference()
        .setDeployment(deploymentName)
        .setProvider(providerName)
        .setAccount(account.getName());

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> accountService.addAccount(reference, account));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> accountService.validateAccount(reference, severity);
    }

    builder.setValidate(doValidate);
    builder.setHalconfigParser(halconfigParser);

    return builder.build();
  }
}
