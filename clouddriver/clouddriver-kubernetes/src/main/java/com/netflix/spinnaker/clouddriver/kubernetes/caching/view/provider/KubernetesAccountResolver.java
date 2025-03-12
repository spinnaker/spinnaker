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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
class KubernetesAccountResolver {
  private final CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository;
  private final ResourcePropertyRegistry globalResourcePropertyRegistry;

  KubernetesAccountResolver(
      CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository,
      ResourcePropertyRegistry globalResourcePropertyRegistry) {
    this.credentialsRepository = credentialsRepository;
    this.globalResourcePropertyRegistry = globalResourcePropertyRegistry;
  }

  Optional<KubernetesCredentials> getCredentials(String account) {
    return Optional.ofNullable(credentialsRepository.getOne(account))
        .map(AccountCredentials::getCredentials);
  }

  ResourcePropertyRegistry getResourcePropertyRegistry(String account) {
    return getCredentials(account)
        .map(KubernetesCredentials::getResourcePropertyRegistry)
        .orElse(globalResourcePropertyRegistry);
  }
}
