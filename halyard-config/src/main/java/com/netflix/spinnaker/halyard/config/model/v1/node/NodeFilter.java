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

import com.netflix.spinnaker.halyard.config.model.v1.security.*;
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

  public NodeFilter setMaster(String name) {
    matchers.add(Node.namedNodeAcceptor(Master.class, name));
    return this;
  }

  public NodeFilter setFeatures() {
    matchers.add(Node.thisNodeAcceptor(Features.class));
    return this;
  }

  public NodeFilter setDeploymentEnvironment() {
    matchers.add(Node.thisNodeAcceptor(DeploymentEnvironment.class));
    return this;
  }

  public NodeFilter setPersistentStorage() {
    matchers.add(Node.thisNodeAcceptor(PersistentStorage.class));
    return this;
  }

  public NodeFilter setSecurity() {
    matchers.add(Node.thisNodeAcceptor(Security.class));
    return this;
  }

  public NodeFilter setUiSecurity() {
    matchers.add(Node.thisNodeAcceptor(UiSecurity.class));
    return this;
  }

  public NodeFilter setApacheSsl() {
    matchers.add(Node.thisNodeAcceptor(ApacheSsl.class));
    return this;
  }

  public NodeFilter setApiSecurity() {
    matchers.add(Node.thisNodeAcceptor(ApiSecurity.class));
    return this;
  }

  public NodeFilter setSpringSsl() {
    matchers.add(Node.thisNodeAcceptor(SpringSsl.class));
    return this;
  }

  public NodeFilter setAuthnMethod(String name) {
    matchers.add(Node.thisNodeAcceptor(Authn.class));
    matchers.add(Node.namedNodeAcceptor(AuthnMethod.class, name));
    return this;
  }

  public NodeFilter setMetricStores() {
    matchers.add(Node.thisNodeAcceptor(MetricStores.class));
    return this;
  }

  public NodeFilter setMetricStore(String name) {
    matchers.add(Node.thisNodeAcceptor(MetricStores.class));
    matchers.add(Node.namedNodeAcceptor(MetricStore.class, name));
    return this;
  }

  public NodeFilter setRoleProvider(String name) {
    matchers.add(Node.thisNodeAcceptor(Authz.class));
    matchers.add(Node.thisNodeAcceptor(GroupMembership.class));
    matchers.add(Node.namedNodeAcceptor(RoleProvider.class, name));
    return this;
  }

  public NodeFilter withAnyRoleProvider() {
    matchers.add(Node.thisNodeAcceptor(Authz.class));
    matchers.add(Node.thisNodeAcceptor(GroupMembership.class));
    matchers.add(Node.thisNodeAcceptor(RoleProvider.class));
    return this;
  }

  public NodeFilter setBakeryDefaults() {
    matchers.add(Node.thisNodeAcceptor(BakeryDefaults.class));
    return this;
  }

  public NodeFilter setBaseImage(String name) {
    matchers.add(Node.thisNodeAcceptor(BakeryDefaults.class));
    matchers.add(Node.namedNodeAcceptor(BaseImage.class, name));
    return this;
  }

  public NodeFilter withAnyBaseImage() {
    matchers.add(Node.thisNodeAcceptor(BakeryDefaults.class));
    matchers.add(Node.thisNodeAcceptor(BaseImage.class));
    return this;
  }

  public NodeFilter() {
    withAnyHalconfigFile();
  }
}
