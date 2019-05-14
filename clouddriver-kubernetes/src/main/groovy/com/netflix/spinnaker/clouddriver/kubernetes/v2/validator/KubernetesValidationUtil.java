/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.validator;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;

@Slf4j
public class KubernetesValidationUtil {
  private final String context;
  private final Errors errors;

  public KubernetesValidationUtil(String context, Errors errors) {
    this.context = context;
    this.errors = errors;
  }

  private String joinAttributeChain(String... attributes) {
    List<String> chain = new ArrayList<>();
    chain.add(context);
    Collections.addAll(chain, attributes);
    return String.join(".", chain);
  }

  public void reject(String errorName, String... attributes) {
    String field = joinAttributeChain(attributes);
    String error = joinAttributeChain(field, errorName);
    errors.reject(field, error);
  }

  public boolean validateNotEmpty(String attribute, Object value) {
    if (value == null) {
      reject("empty", attribute);
      return false;
    }

    return true;
  }

  public boolean validateSizeEquals(String attribute, Collection items, int size) {
    if (items.size() != size) {
      reject("size!=" + size, attribute);
      return false;
    }

    return true;
  }

  public boolean validateNotEmpty(String attribute, String value) {
    if (StringUtils.isEmpty(value)) {
      reject("empty", attribute);
      return false;
    }

    return true;
  }

  public boolean validateV2Credentials(
      AccountCredentialsProvider provider, String accountName, KubernetesManifest manifest) {
    KubernetesKind kind = manifest.getKind();
    String namespace = manifest.getNamespace();
    return validateV2Credentials(provider, accountName, kind, namespace);
  }

  public boolean validateV2Credentials(
      AccountCredentialsProvider provider,
      String accountName,
      KubernetesKind kind,
      String namespace) {
    log.info("Validating credentials for {} {} {}", accountName, kind, namespace);
    if (!validateNotEmpty("account", accountName)) {
      return false;
    }

    if (StringUtils.isEmpty(namespace)) {
      return true;
    }

    AccountCredentials credentials = provider.getCredentials(accountName);
    if (credentials == null) {
      reject("notFound", "account");
      return false;
    }

    if (!(credentials.getCredentials() instanceof KubernetesV2Credentials)) {
      reject("wrongVersion", "account");
      return false;
    }

    if (!validateNamespace(namespace, (KubernetesV2Credentials) credentials.getCredentials())) {
      return false;
    }

    if (!validateKind(kind, (KubernetesV2Credentials) credentials.getCredentials())) {
      return false;
    }

    return true;
  }

  private boolean validateKind(KubernetesKind kind, KubernetesV2Credentials credentials) {
    if (!credentials.isValidKind(kind)) {
      KubernetesV2Credentials.InvalidKindReason invalidReason =
          credentials.getInvalidKindReason(kind);
      if (invalidReason != null) {
        reject(invalidReason.getErrorMessage(kind), kind.toString());
      } else {
        reject("notValidKind", kind.toString());
      }
      return false;
    }

    return true;
  }

  protected boolean validateNamespace(String namespace, KubernetesV2Credentials credentials) {
    final List<String> configuredNamespaces = credentials.getDeclaredNamespaces();
    if (!configuredNamespaces.contains(namespace)) {
      reject(
          String.format(
              "Account %s is not configured to deploy to namespace %s",
              credentials.getAccountName(), namespace),
          namespace);
      return false;
    }
    return true;
  }
}
