/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.health;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentialFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;

final class StubKubernetesCredentialsFactory
    implements KubernetesCredentialFactory<KubernetesV2Credentials> {
  private final KubernetesV2Credentials credentials;

  static KubernetesCredentialFactory<KubernetesV2Credentials> getInstance(
      KubernetesV2Credentials credentials) {
    return new StubKubernetesCredentialsFactory(credentials);
  }

  private StubKubernetesCredentialsFactory(KubernetesV2Credentials credentials) {
    this.credentials = credentials;
  }

  @Override
  public KubernetesV2Credentials build(ManagedAccount managedAccount) {
    return credentials;
  }

  @Override
  public String getKubeconfigFile(
      ConfigFileService configFileService, ManagedAccount managedAccount) {
    return System.getProperty("user.home") + "/.kube/config";
  }
}
