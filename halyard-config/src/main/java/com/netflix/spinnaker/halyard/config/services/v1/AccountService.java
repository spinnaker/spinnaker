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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigCoordinates;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigProblem;
import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalRequestException;
import com.netflix.spinnaker.halyard.config.model.v1.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.Provider;
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
  DeploymentService deploymentService;

  public Account getAccount(HalconfigCoordinates coordinates) {
    Provider provider = providerService.getProvider(coordinates);

    String accountName = coordinates.getAccount();

    List<Account> accounts = provider.getAccounts();

    List<Account> matchingAccounts = accounts
        .stream()
        .filter(a -> a.getName().equals(accountName))
        .collect(Collectors.toList());

    switch (matchingAccounts.size()) {
      case 0:
        throw new IllegalRequestException(coordinates,
            "No matching account found account",
            "Check if this account was defined in another provider, or create a new one");
      case 1:
        return matchingAccounts.get(0);
      default:
        throw new IllegalConfigException(coordinates,
            "More than one matching account found",
            "Manually delete/rename duplicate accounts in your halconfig file");
    }
  }

  public void validateAccount(HalconfigCoordinates coordinates) {
    Account acccount = getAccount(coordinates);
    DeploymentConfiguration deployment = deploymentService.getDeploymentConfiguration(coordinates);

    List<HalconfigProblem> problems = acccount.validate(deployment, coordinates);

    if (!problems.isEmpty()) {
      throw new IllegalConfigException(problems);
    }
  }
}
