/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.kork.secrets.engines;

import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Namespaces;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(
    value = "spinnaker.secrets.kubernetes.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class KubernetesSecretEngine implements SecretEngine {
  private static final String IDENTIFIER = "k8s";
  private static final String SECRET_NAME = "n";
  private static final String SECRET_KEY = "k";
  private static final String SECRET_NAMESPACE = "ns";
  private final CoreV1Api apiClient;
  private final String namespace;

  /*
  There is NOT a great way to "test" this namespace sane default WITHOUT running in a container in k8s.  As such, do the best we can to capture
  defaults in the executions via integration tests.
   */
  KubernetesSecretEngine() {
    String tempNamespace;
    try {
      tempNamespace = Namespaces.getPodNamespace();
    } catch (Exception e) {
      log.warn(
          "WARNING!  Unable to determine the namespace.  This LIKELY means we're not in a container!  Defaulting to 'default'");
      tempNamespace = "default";
    }

    this.namespace = tempNamespace;

    ApiClient apiClient = null;
    try {
      apiClient = Config.defaultClient();

    } catch (IOException e) {
      log.error("CRITICAL:  Unable to start a k8s client for secret engine handling!");
      throw new RuntimeException(e);
    }
    this.apiClient = new CoreV1Api(apiClient);
  }

  /** Constructor for testing */
  KubernetesSecretEngine(String namespace, CoreV1Api apiClient) {
    this.namespace = namespace;
    this.apiClient = apiClient;
  }

  public String identifier() {
    return IDENTIFIER;
  }

  @Override
  public byte[] decrypt(EncryptedSecret encryptedSecret) {
    try {
      String namespace =
          Optional.ofNullable(encryptedSecret.getParams().get(SECRET_NAMESPACE))
              .orElse(this.namespace);
      if (namespace == null) {
        namespace = "default";
        log.warn(
            "Loading secret from namespace %s since unable to calculate from the pod environment."
                .formatted(namespace));
      }
      V1Secret secret =
          apiClient.readNamespacedSecret(
              encryptedSecret.getParams().get(SECRET_NAME).toLowerCase(), namespace, null);
      return secret.getData().get(encryptedSecret.getParams().get(SECRET_KEY));
    } catch (ApiException e) {
      log.error("Unable to load secret for " + encryptedSecret.getUri(), e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void validate(EncryptedSecret encryptedSecret) throws InvalidSecretFormatException {
    Set<String> paramNames = encryptedSecret.getParams().keySet();
    if (!paramNames.contains(SECRET_NAME)) {
      throw new InvalidSecretFormatException(
          "Secret is missing missing name (" + SECRET_NAME + "=...)");
    }
    if (!paramNames.contains(SECRET_KEY)) {
      throw new InvalidSecretFormatException(
          "Secret parameter is missing key (" + SECRET_KEY + "=...)");
    }
  }

  @Override
  public void clearCache() {}
}
