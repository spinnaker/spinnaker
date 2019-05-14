/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer;

import static com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class EcsLoadBalancerSummary implements Item {

  private String name;
  private Map<String, EcsLoadBalancerSummaryByAccount> byAccounts = new HashMap<>();

  public EcsLoadBalancerSummary withName(String name) {
    setName(name);
    return this;
  }

  @Override
  public List getByAccounts() {
    return byAccounts.values().stream().collect(Collectors.toList());
  }

  public EcsLoadBalancerSummaryByAccount getOrCreateAccount(String account) {
    if (!byAccounts.containsKey(account)) {
      byAccounts.put(account, new EcsLoadBalancerSummaryByAccount().withName(account));
    }
    return byAccounts.get(account);
  }
}
