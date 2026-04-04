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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
@ConditionalOnProperty(value = "k8s.secretEngine.enabled", havingValue = "true", matchIfMissing = false)
@Setter
public class KubernetesSecretEngine implements SecretEngine {
  private static final String IDENTIFIER = "k8s";
  private static final String SECRET_NAME = "n";
  private static final String SECRET_KEY = "k";
  private static final String SECRET_NAMESPACE= "ns";
  private CoreV1Api apiClient;
  private String namespace;

  KubernetesSecretEngine() {
    try {
      namespace = Namespaces.getPodNamespace();
    }catch (Exception e) {
      log.warn("WARNING!  Unable to determine the namespace.  This LIKELY means we're not in a container!  Defaulting to 'default'");
    }

    ApiClient apiClient = null;
    try {
      apiClient = Config.defaultClient();

    } catch (IOException e) {
      log.error("CRITICAL:  Unable to start a k8s client for secret engine handling!");
      throw new RuntimeException(e);
    }
    this.apiClient = new CoreV1Api(apiClient);


  }

  public String identifier() {
    return IDENTIFIER;
  }

  @Override
  public byte[] decrypt(EncryptedSecret encryptedSecret) {
    try {
      String namespace = this.namespace;
      if (namespace == null) {
        namespace = Optional.ofNullable(encryptedSecret.getParams().get(SECRET_NAMESPACE)).orElse("default");
        log.warn("Loading namespace from %s since it seems NOT running in a container.".formatted(namespace));
      }
      V1Secret secret = apiClient.readNamespacedSecret(encryptedSecret.getParams().get(SECRET_NAME).toLowerCase(), namespace, null);
      return secret.getData().get(encryptedSecret.getParams().get(SECRET_KEY));
    } catch (ApiException e) {
      log.error("Unable to load secret for "+ encryptedSecret.getUri(), e);
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
  public void clearCache() {

  }

}
