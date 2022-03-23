/*
 * Copyright 2022 Netflix, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Custom CredentialsParser for Kubernetes credentials to handle configuration errors when parsing
 * account credentials. As account credentials can be created by users through the credentials API,
 * this parser is provided for more robust protection from user error.
 */
@RequiredArgsConstructor
@Log4j2
public class KubernetesCredentialsParser
    implements CredentialsParser<ManagedAccount, KubernetesNamedAccountCredentials> {
  private final KubernetesCredentials.Factory credentialFactory;

  @Nullable
  @Override
  public KubernetesNamedAccountCredentials parse(@Nonnull ManagedAccount managedAccount) {
    try {
      return new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);
    } catch (RuntimeException e) {
      log.warn("Skipping invalid account definition account={}", managedAccount.getName(), e);
      return null;
    }
  }
}
