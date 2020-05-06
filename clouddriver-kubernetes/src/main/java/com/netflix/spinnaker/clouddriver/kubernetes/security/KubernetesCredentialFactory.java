/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import org.apache.commons.lang3.StringUtils;

public interface KubernetesCredentialFactory<C extends KubernetesCredentials> {
  C build(KubernetesConfigurationProperties.ManagedAccount managedAccount);

  default void validateAccount(KubernetesConfigurationProperties.ManagedAccount managedAccount) {
    if (StringUtils.isEmpty(managedAccount.getName())) {
      throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
    }

    if (!managedAccount.getOmitNamespaces().isEmpty()
        && !managedAccount.getNamespaces().isEmpty()) {
      throw new IllegalArgumentException(
          "At most one of 'namespaces' and 'omitNamespaces' can be specified");
    }

    if (!managedAccount.getOmitKinds().isEmpty() && !managedAccount.getKinds().isEmpty()) {
      throw new IllegalArgumentException("At most one of 'kinds' and 'omitKinds' can be specified");
    }
  }

  default String getKubeconfigFile(
      ConfigFileService configFileService,
      KubernetesConfigurationProperties.ManagedAccount managedAccount) {
    if (StringUtils.isNotEmpty(managedAccount.getKubeconfigFile())) {
      return configFileService.getLocalPath(managedAccount.getKubeconfigFile());
    }

    if (StringUtils.isNotEmpty(managedAccount.getKubeconfigContents())) {
      return configFileService.getLocalPathForContents(
          managedAccount.getKubeconfigContents(), managedAccount.getName());
    }

    return System.getProperty("user.home") + "/.kube/config";
  }
}
