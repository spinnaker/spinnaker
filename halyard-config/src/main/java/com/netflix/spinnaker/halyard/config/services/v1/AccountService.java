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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalRequestException;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeReference;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * deployments.
 */
@Component
public class AccountService {
  @Autowired
  ProviderService providerService;

  @Autowired
  ValidateService validateService;

  public Account getAccount(NodeReference reference) {
    Provider provider = providerService.getProvider(reference);

    String accountName = reference.getAccount();

    List<Account> accounts = provider.getAccounts();

    List<Account> matchingAccounts = accounts
        .stream()
        .filter(a -> a.getName().equals(accountName))
        .collect(Collectors.toList());

    switch (matchingAccounts.size()) {
      case 0:
        throw new IllegalRequestException(new ProblemBuilder(
            Problem.Severity.FATAL, "No matching account found account")
            .setReference(reference)
            .setRemediation("Check if this account was defined in another provider, or create a new one").build());
      case 1:
        return matchingAccounts.get(0);
      default:
        throw new IllegalConfigException(new ProblemBuilder(
            Problem.Severity.FATAL, "More than one matching account found")
            .setReference(reference)
            .setRemediation("Manually delete/rename duplicate accounts in your halconfig file").build());
    }
  }

  public void validateAccount(NodeReference nodeReference) {
    NodeFilter filter = new NodeFilter(nodeReference).anyHalconfigFile();

    validateService.validateMatchingFilter(filter).throwIfProblem();
  }
}
