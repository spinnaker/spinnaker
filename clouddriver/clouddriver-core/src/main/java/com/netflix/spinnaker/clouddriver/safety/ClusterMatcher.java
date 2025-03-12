/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */
package com.netflix.spinnaker.clouddriver.safety;

import com.netflix.spinnaker.moniker.Moniker;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ClusterMatcher {

  public static ClusterMatchRule getMatchingRule(
      String account, String location, Moniker clusterMoniker, List<ClusterMatchRule> rules) {
    if (!Optional.ofNullable(rules).isPresent()) {
      return null;
    }

    String stack = clusterMoniker.getStack() == null ? "" : clusterMoniker.getStack();
    String detail = clusterMoniker.getDetail() == null ? "" : clusterMoniker.getDetail();

    List<ClusterMatchRule> candidates =
        rules.stream()
            .filter(
                rule -> {
                  String ruleAccount = rule.getAccount();
                  String ruleLocation = rule.getLocation();
                  String ruleStack = rule.getStack();
                  String ruleDetail = rule.getDetail();
                  return (ruleAccount.equals("*") || ruleAccount.equals(account))
                      && (ruleLocation.equals("*") || ruleLocation.equals(location))
                      && (ruleStack.equals("*")
                          || ruleStack.equals(stack)
                          || ruleStack.isEmpty() && stack.isEmpty())
                      && (ruleDetail.equals("*")
                          || ruleDetail.equals(detail)
                          || ruleDetail.isEmpty() && detail.isEmpty());
                })
            .sorted(
                (o1, o2) -> {
                  if (!o1.getAccount().equals(o2.getAccount())) {
                    return "*".equals(o1.getAccount()) ? 1 : -1;
                  }
                  if (!o1.getLocation().equals(o2.getLocation())) {
                    return "*".equals(o1.getLocation()) ? 1 : -1;
                  }
                  if (!o1.getStack().equals(o2.getStack())) {
                    return "*".equals(o1.getStack()) ? 1 : -1;
                  }
                  if (!o1.getDetail().equals(o2.getDetail())) {
                    return "*".equals(o1.getDetail()) ? 1 : -1;
                  }
                  return o1.getPriority() - o2.getPriority();
                })
            .collect(Collectors.toList());

    if (candidates.isEmpty()) {
      return null;
    }

    return candidates.get(0);
  }
}
