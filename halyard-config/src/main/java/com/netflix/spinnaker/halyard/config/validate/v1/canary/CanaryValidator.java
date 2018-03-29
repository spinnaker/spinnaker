/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.canary;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.GoogleCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CanaryValidator extends Validator<Canary> {

  @Autowired
  private String halyardVersion;

  @Autowired
  private Registry registry;

  @Override
  public void validate(ConfigProblemSetBuilder p, Canary n) {
    Set<String> accounts = new HashSet<>();

    for (AbstractCanaryServiceIntegration svc : n.getServiceIntegrations()) {
      for (AbstractCanaryAccount account : (List<AbstractCanaryAccount>)svc.getAccounts()) {
        if (accounts.contains(account.getName())) {
          p.addProblem(Problem.Severity.FATAL, "Canary account \"" + account.getName() + "\" appears more than once")
            .setRemediation("Change the name of the account in " + svc.getNodeName() + " service integration");
        } else {
          accounts.add(account.getName());
        }
      }
    }

    for (AbstractCanaryServiceIntegration s : n.getServiceIntegrations()) {
      if (s instanceof GoogleCanaryServiceIntegration) {
        new GoogleCanaryValidator().setHalyardVersion(halyardVersion).setRegistry(registry).validate(p, (GoogleCanaryServiceIntegration)s);
      }
    }
  }
}
