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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
      primaryAccount = accounts.get(0).getName();
    }
    return primaryAccount;
  }

  private boolean hasAccount(String name) {
    return accounts.stream().anyMatch(a -> a.getName().equals(name));
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(accounts.stream().map(a -> (Node) a).collect(Collectors.toList()));
  }

  @Override
  public String getNodeName() {
    return providerType().getName();
  }

  abstract public ProviderType providerType();

  public enum ProviderType {
    APPENGINE("appengine"),
    AWS("aws"),
    AZURE("azure"),
    DCOS("dcos"),
    DOCKERREGISTRY("dockerRegistry"),
    GOOGLE("google", "gce"),
    KUBERNETES("kubernetes"),
    OPENSTACK("openstack"),
    ORACLEBMCS("oraclebmcs");

    @Getter
    String name;

    @Getter
    String id;

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
