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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class Provider<A extends Account> extends Node implements Cloneable {
  boolean enabled = false;
  List<A> accounts = new ArrayList<>();

  private String primaryAccount;

  public String getPrimaryAccount() {
    if (accounts.size() == 0) {
      primaryAccount = null;
    } else if (primaryAccount == null || !hasAccount(primaryAccount)) {
      DeploymentConfiguration deploymentConfiguration = parentOfType(DeploymentConfiguration.class);
      DeploymentEnvironment deploymentEnvironment =
          deploymentConfiguration.getDeploymentEnvironment();
      if (Boolean.TRUE.equals(deploymentEnvironment.getBootstrapOnly())) {
        List<Account> nonBootstrapAccounts =
            accounts.stream()
                .filter(a -> !a.name.equals(deploymentEnvironment.getAccountName()))
                .collect(Collectors.toList());
        if (nonBootstrapAccounts.size() == 0) {
          return null;
        } else {
          return nonBootstrapAccounts.get(0).getName();
        }
      } else {
        primaryAccount = accounts.get(0).getName();
      }
    }
    return primaryAccount;
  }

  private boolean hasAccount(String name) {
    return accounts.stream().anyMatch(a -> a.getName().equals(name));
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(
        accounts.stream().map(a -> (Node) a).collect(Collectors.toList()));
  }

  @Override
  public String getNodeName() {
    return providerType().getName();
  }

  public abstract ProviderType providerType();

  public enum ProviderVersion {
    V1("v1"),
    V2("v2");

    private final String name;

    ProviderVersion(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  public enum ProviderType {
    APPENGINE("appengine"),
    AWS("aws"),
    ECS("ecs"),
    AZURE("azure"),
    CLOUDFOUNDRY("cloudfoundry"),
    DCOS("dcos"),
    DOCKERREGISTRY("dockerRegistry"),
    GOOGLE("google", "gce"),
    HUAWEICLOUD("huaweicloud"),
    KUBERNETES("kubernetes"),
    ORACLE("oracle"),
    ORACLEBMCS("oraclebmcs"), // obsolete, replaced by ORACLE
    TENCENTCLOUD("tencentcloud");

    @Getter String name;

    @Getter String id;

    ProviderType(String name) {
      this.name = name;
      this.id = name;
    }

    ProviderType(String name, String id) {
      this.name = name;
      this.id = id;
    }
  }
}
