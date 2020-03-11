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

import com.netflix.spinnaker.halyard.config.model.v1.artifacts.ArtifactTemplate;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;
import com.netflix.spinnaker.halyard.config.model.v1.security.*;
import com.netflix.spinnaker.halyard.config.model.v1.webook.WebhookTrust;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** A way to identify a spot in your halconfig. */
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

  public NodeFilter setCi(String name) {
    matchers.add(Node.thisNodeAcceptor(Cis.class));
    matchers.add(Node.namedNodeAcceptor(Ci.class, name));
    return this;
  }

  public NodeFilter withAnyRepository() {
    matchers.add(Node.thisNodeAcceptor(Repositories.class));
    matchers.add(Node.thisNodeAcceptor(Repository.class));
    return this;
  }

  public NodeFilter setRepository(String name) {
    matchers.add(Node.thisNodeAcceptor(Repositories.class));
    matchers.add(Node.namedNodeAcceptor(Repository.class, name));
    return this;
  }

  public NodeFilter withAnySearch() {
    matchers.add(Node.thisNodeAcceptor(Search.class));
    return this;
  }

  public NodeFilter setSearch(String name) {
    matchers.add(Node.namedNodeAcceptor(Search.class, name));
    return this;
  }

  public NodeFilter withAnyNotification() {
    matchers.add(Node.thisNodeAcceptor(Notifications.class));
    matchers.add(Node.thisNodeAcceptor(Notification.class));
    return this;
  }

  public NodeFilter setNotification(String name) {
    matchers.add(Node.thisNodeAcceptor(Notifications.class));
    matchers.add(Node.namedNodeAcceptor(Notification.class, name));
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

  public NodeFilter withAnyPublisher() {
    matchers.add(Node.thisNodeAcceptor(Publisher.class));
    return this;
  }

  public NodeFilter setPublisher(String name) {
    matchers.add(Node.namedNodeAcceptor(Publisher.class, name));
    return this;
  }

  public NodeFilter withAnyPubsub() {
    matchers.add(Node.thisNodeAcceptor(Pubsubs.class));
    matchers.add(Node.thisNodeAcceptor(Pubsub.class));
    return this;
  }

  public NodeFilter setPubsub(String name) {
    matchers.add(Node.thisNodeAcceptor(Pubsubs.class));
    matchers.add(Node.namedNodeAcceptor(Pubsub.class, name));
    return this;
  }

  public NodeFilter withAnySubscription() {
    matchers.add(Node.thisNodeAcceptor(Subscription.class));
    return this;
  }

  public NodeFilter setSubscription(String name) {
    matchers.add(Node.namedNodeAcceptor(Subscription.class, name));
    return this;
  }

  public NodeFilter withAnyArtifactProvider() {
    matchers.add(Node.thisNodeAcceptor(Artifacts.class));
    matchers.add(Node.thisNodeAcceptor(ArtifactProvider.class));
    return this;
  }

  public NodeFilter setArtifactProvider(String name) {
    matchers.add(Node.thisNodeAcceptor(Artifacts.class));
    matchers.add(Node.namedNodeAcceptor(ArtifactProvider.class, name));
    return this;
  }

  public NodeFilter withAnyArtifactAccount() {
    matchers.add(Node.thisNodeAcceptor(ArtifactAccount.class));
    return this;
  }

  public NodeFilter setArtifactAccount(String name) {
    matchers.add(Node.namedNodeAcceptor(ArtifactAccount.class, name));
    return this;
  }

  public NodeFilter withAnyCluster() {
    matchers.add(Node.thisNodeAcceptor(Cluster.class));
    return this;
  }

  public NodeFilter setCluster(String name) {
    matchers.add(Node.namedNodeAcceptor(Cluster.class, name));
    return this;
  }

  public NodeFilter withAnyMaster() {
    matchers.add(Node.thisNodeAcceptor(CIAccount.class));
    return this;
  }

  public NodeFilter setMaster(String name) {
    matchers.add(Node.namedNodeAcceptor(CIAccount.class, name));
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

  public NodeFilter withAnyHaService() {
    matchers.add(Node.thisNodeAcceptor(HaServices.class));
    matchers.add(Node.thisNodeAcceptor(HaService.class));
    return this;
  }

  public NodeFilter setHaService(String name) {
    matchers.add(Node.thisNodeAcceptor(HaServices.class));
    matchers.add(Node.namedNodeAcceptor(HaService.class, name));
    return this;
  }

  public NodeFilter setPersistentStorage() {
    matchers.add(Node.thisNodeAcceptor(PersistentStorage.class));
    return this;
  }

  public NodeFilter setPersistentStore(String name) {
    matchers.add(Node.thisNodeAcceptor(PersistentStorage.class));
    matchers.add(Node.namedNodeAcceptor(PersistentStore.class, name));
    return this;
  }

  public NodeFilter withAnyPersistentStore() {
    matchers.add(Node.thisNodeAcceptor(PersistentStorage.class));
    matchers.add(Node.thisNodeAcceptor(PersistentStore.class));
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

  public NodeFilter setCanary() {
    matchers.add(Node.thisNodeAcceptor(Canary.class));
    return this;
  }

  public NodeFilter setWebhook() {
    matchers.add(Node.thisNodeAcceptor(Webhook.class));
    return this;
  }

  public NodeFilter setWebhookTrust() {
    matchers.add(Node.thisNodeAcceptor(Webhook.class));
    matchers.add(Node.thisNodeAcceptor(WebhookTrust.class));
    return this;
  }

  public NodeFilter setArtifacts() {
    matchers.add(Node.thisNodeAcceptor(Artifacts.class));
    return this;
  }

  public NodeFilter withAnyArtifactTemplate() {
    matchers.add(Node.thisNodeAcceptor(Artifacts.class));
    matchers.add(Node.thisNodeAcceptor(ArtifactTemplate.class));
    return this;
  }

  public NodeFilter setArtifactTemplate(String name) {
    matchers.add(Node.thisNodeAcceptor(Artifacts.class));
    matchers.add(Node.namedNodeAcceptor(ArtifactTemplate.class, name));
    return this;
  }

  public NodeFilter setPlugin(String name) {
    matchers.add(Node.thisNodeAcceptor(Spinnaker.class));
    matchers.add(Node.thisNodeAcceptor(Extensibility.class));
    matchers.add(Node.namedNodeAcceptor(Plugin.class, name));
    return this;
  }

  public NodeFilter withAnyPlugin() {
    matchers.add(Node.thisNodeAcceptor(Spinnaker.class));
    matchers.add(Node.thisNodeAcceptor(Extensibility.class));
    matchers.add(Node.thisNodeAcceptor(Plugin.class));
    return this;
  }

  public NodeFilter setPluginRepository(String name) {
    matchers.add(Node.thisNodeAcceptor(Spinnaker.class));
    matchers.add(Node.thisNodeAcceptor(Extensibility.class));
    matchers.add(Node.namedNodeAcceptor(PluginRepository.class, name));
    return this;
  }

  public NodeFilter withAnyPluginRepository() {
    matchers.add(Node.thisNodeAcceptor(Spinnaker.class));
    matchers.add(Node.thisNodeAcceptor(Extensibility.class));
    matchers.add(Node.thisNodeAcceptor(PluginRepository.class));
    return this;
  }

  public NodeFilter setStats() {
    matchers.add(Node.thisNodeAcceptor(Stats.class));
    return this;
  }

  public NodeFilter() {
    withAnyHalconfigFile();
  }
}
