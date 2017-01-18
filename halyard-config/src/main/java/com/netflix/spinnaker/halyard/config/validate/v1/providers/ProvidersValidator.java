/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProvidersValidator extends Validator<Providers> {
  @Override
  public void validate(ProblemSetBuilder p, Providers n) {
    Set<String> accounts = new HashSet<>();

    NodeIterator nodeIterator = n.getChildren();

    Node child = nodeIterator.getNext();
    while (child != null) {
      Provider provider = (Provider) child;
      for (Account account : (List<Account>) provider.getAccounts()) {
        if (accounts.contains(account.getName())) {
          p.addProblem(Severity.FATAL, "Account \"" + account.getName() + "\" appears more than once")
            .setRemediation("Change the name of the account in " + provider.getNodeName());
        } else {
          accounts.add(account.getName());
        }
      }
      child = nodeIterator.getNext();
    }
  }
}
