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

import java.util.ArrayList;
import java.util.List;

/**
 * A way to identify a spot in your halconfig.
 */
@Data
public class NodeFilter implements Cloneable {
  List<NodeMatcher> matchers = new ArrayList<>();

  public boolean matches(Node n) {
    return matchers.stream().anyMatch(m -> m.matches(n));
  }

  private NodeFilter withAnyHalconfigFile() {
    matchers.add(Node.thisNodeAcceptor(Halconfig.class));
    return this;
  }

  public NodeFilter withAnyDeployment() {
    matchers.add(Node.thisNodeAcceptor(DeploymentConfiguration.class));
    return this;
  }

  public NodeFilter setDeployment(String name) {
    matchers.add(Node.namedNodeAcceptor(DeploymentConfiguration.class, name));
    return this;
  }

  public NodeFilter withAnyWebhook() {
    matchers.add(Node.thisNodeAcceptor(Webhooks.class));
    matchers.add(Node.thisNodeAcceptor(Webhook.class));
    return this;
  }

  public NodeFilter setWebhook(String name) {
    matchers.add(Node.thisNodeAcceptor(Webhooks.class));
    matchers.add(Node.namedNodeAcceptor(Webhook.class, name));
    return this;
  }

  public NodeFilter withAnyProvider() {
    matchers.add(Node.thisNodeAcceptor(Providers.class));
    matchers.add(Node.thisNodeAcceptor(Provider.class));
    return this;
  }

  public NodeFilter setProvider(String name) {
    matchers.add(Node.thisNodeAcceptor(Providers.class));
    matchers.add(Node.namedNodeAcceptor(Provider.class, name));
    return this;
  }

  public NodeFilter withAnyAccount() {
    matchers.add(Node.thisNodeAcceptor(Account.class));
    return this;
  }

  public NodeFilter setAccount(String name) {
    matchers.add(Node.namedNodeAcceptor(Account.class, name));
    return this;
  }

  public NodeFilter withAnyMaster() {
    matchers.add(Node.thisNodeAcceptor(Master.class));
    return this;
  }

  public NodeFilter setFeatures() {
    matchers.add(Node.thisNodeAcceptor(Features.class));
    return this;
  }

  public NodeFilter() {
    withAnyHalconfigFile();
  }
}
