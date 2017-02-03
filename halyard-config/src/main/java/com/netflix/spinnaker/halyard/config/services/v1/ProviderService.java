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

import com.netflix.spinnaker.halyard.config.errors.v1.config.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the current halconfigs
 * providers.
 */
@Component
public class ProviderService {
  @Autowired
  LookupService lookupService;

  @Autowired
  ValidateService validateService;

  public Provider getProvider(String deploymentName, String providerName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setProvider(providerName);

    List<Provider> matching = lookupService.getMatchingNodesOfType(filter, Provider.class)
        .stream()
        .map(n -> (Provider) n)
        .collect(Collectors.toList());

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(new ProblemBuilder(Problem.Severity.FATAL,
            "No provider with name \"" + providerName + "\" could be found")
            .setRemediation("Create a new provider with name \"" + providerName + "\"").build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(new ProblemBuilder(Problem.Severity.FATAL,
            "More than one provider with name \"" + providerName + "\" found")
            .setRemediation("Manually delete or rename duplicate providers with name \"" + providerName + "\" in your halconfig file").build());
    }
  }

  public List<Provider> getAllProviders(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyProvider();

    List<Provider> matching = lookupService.getMatchingNodesOfType(filter, Provider.class)
        .stream()
        .map(n -> (Provider) n)
        .collect(Collectors.toList());

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ProblemBuilder(Problem.Severity.FATAL, "No providers could be found")
              .build());
    } else {
      return matching;
    }
  }

  public void setEnabled(String deploymentName, String providerName, boolean enabled) {
    Provider provider = getProvider(deploymentName, providerName);
    provider.setEnabled(enabled);
  }

  public ProblemSet validateProvider(String deploymentName, String providerName, Severity severity) {
    NodeFilter filter = new NodeFilter()
        .setDeployment(deploymentName)
        .setProvider(providerName)
        .withAnyAccount()
        .withAnyWebhook();

    return validateService.validateMatchingFilter(filter, severity);
  }

  public ProblemSet validateAllProviders(String deploymentName, Severity severity) {
    NodeFilter filter = new NodeFilter()
        .setDeployment(deploymentName)
        .withAnyProvider()
        .withAnyAccount()
        .withAnyWebhook();

    return validateService.validateMatchingFilter(filter, severity);
  }
}
