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
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;

final class StubKubernetesCredentialsFactory<T extends KubernetesCredentials>
    implements KubernetesCredentialFactory<T> {
  private final T credentials;

  static <U extends KubernetesCredentials> KubernetesCredentialFactory<U> getInstance(
      U credentials) {
    return new StubKubernetesCredentialsFactory<>(credentials);
  }

  private StubKubernetesCredentialsFactory(T credentials) {
    this.credentials = credentials;
  }

  @Override
  public T build(ManagedAccount managedAccount) {
    return credentials;
  }

  @Override
  public void validateAccount(ManagedAccount managedAccount) {}

  @Override
  public String getKubeconfigFile(
      ConfigFileService configFileService, ManagedAccount managedAccount) {
    return System.getProperty("user.home") + "/.kube/config";
  }
}
