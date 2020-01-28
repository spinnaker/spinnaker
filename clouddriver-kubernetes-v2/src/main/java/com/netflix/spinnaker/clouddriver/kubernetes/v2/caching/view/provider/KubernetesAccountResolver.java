/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
class KubernetesAccountResolver {
  private final AccountCredentialsRepository credentialsRepository;
  private final ResourcePropertyRegistry globalResourcePropertyRegistry;

  KubernetesAccountResolver(
      AccountCredentialsRepository credentialsRepository,
      ResourcePropertyRegistry globalResourcePropertyRegistry) {
    this.credentialsRepository = credentialsRepository;
    this.globalResourcePropertyRegistry = globalResourcePropertyRegistry;
  }

  Optional<KubernetesV2Credentials> getCredentials(String account) {
    return Optional.ofNullable(credentialsRepository.getOne(account))
        .filter(c -> c instanceof KubernetesNamedAccountCredentials)
        .map(c -> (KubernetesNamedAccountCredentials) c)
        .map(AccountCredentials::getCredentials)
        .filter(c -> c instanceof KubernetesV2Credentials)
        .map(c -> (KubernetesV2Credentials) c);
  }

  ResourcePropertyRegistry getResourcePropertyRegistry(String account) {
    return getCredentials(account)
        .map(KubernetesV2Credentials::getResourcePropertyRegistry)
        .orElse(globalResourcePropertyRegistry);
  }
}
