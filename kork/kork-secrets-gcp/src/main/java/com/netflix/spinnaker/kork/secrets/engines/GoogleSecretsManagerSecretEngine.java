/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.kork.secrets.engines;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import com.netflix.spinnaker.kork.secrets.SecretException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class GoogleSecretsManagerSecretEngine implements SecretEngine {
  private static final String PROJECT_NUMBER = "p";
  private static final String SECRET_ID = "s";
  private static final String SECRET_KEY = "k";
  private static final String VERSION_ID = "v";
  private static final String LATEST = "latest";

  private static final String IDENTIFIER = "google-secrets-manager";

  private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static SecretManagerServiceClient client;

  @Override
  public String identifier() {
    return GoogleSecretsManagerSecretEngine.IDENTIFIER;
  }

  @Override
  public byte[] decrypt(EncryptedSecret encryptedSecret) {
    String projectNumber = encryptedSecret.getParams().get(PROJECT_NUMBER);
    String secretId = encryptedSecret.getParams().get(SECRET_ID);
    String secretKey = encryptedSecret.getParams().get(SECRET_KEY);
    String secretVersion = encryptedSecret.getParams().get(VERSION_ID);
    if (encryptedSecret.isEncryptedFile()) {
      return getSecretPayload(projectNumber, secretId, secretVersion)
          .getData()
          .toStringUtf8()
          .getBytes();
    } else if (secretKey != null) {
      return getSecretPayloadString(projectNumber, secretId, secretVersion, secretKey);
    } else {
      return getSecretPayloadString(projectNumber, secretId, secretVersion);
    }
  }

  @Override
  public void validate(EncryptedSecret encryptedSecret) {
    Set<String> paramNamesSet = encryptedSecret.getParams().keySet();
    if (!paramNamesSet.contains(PROJECT_NUMBER)) {
      throw new InvalidSecretFormatException(
          "Project number parameter is missing (" + PROJECT_NUMBER + "=...)");
    }
    if (!paramNamesSet.contains(SECRET_ID)) {
      throw new InvalidSecretFormatException(
          "Secret id parameter is missing (" + SECRET_ID + "=...)");
    }
    if (encryptedSecret.isEncryptedFile() && paramNamesSet.contains(SECRET_KEY)) {
      throw new InvalidSecretFormatException("Encrypted file should not specify key");
    }
  }

  protected SecretPayload getSecretPayload(
      String projectNumber, String secretId, String secretVersion) {
    try {
      if (client == null) {
        client = SecretManagerServiceClient.create();
      }
      if (secretVersion == null) {
        secretVersion = LATEST;
      }
      SecretVersionName secretVersionName =
          SecretVersionName.of(projectNumber, secretId, secretVersion);
      AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
      return response.getPayload();
    } catch (IOException | ApiException e) {
      throw new SecretException(
          String.format(
              "Failed to parse secret when using Google Secrets Manager to fetch: [projectNumber: %s, secretId: %s]",
              projectNumber, secretId),
          e);
    }
  }

  @Override
  public void clearCache() {
    cache.clear();
  }

  private byte[] getSecretPayloadString(
      String projectNumber, String secretId, String secretVersion, String secretKey) {
    if (!cache.containsKey(secretId)) {
      String secretString =
          getSecretPayload(projectNumber, secretId, secretVersion).getData().toStringUtf8();
      try {
        Map<String, String> map = objectMapper.readValue(secretString, Map.class);
        cache.put(secretId, map);
      } catch (JsonProcessingException | IllegalArgumentException e) {
        throw new SecretException(
            String.format(
                "Failed to parse secret when using Google Secrets Manager to fetch: [projectNumber: %s, secretId: %s, secretKey: %s]",
                projectNumber, secretId, secretKey),
            e);
      }
    }
    return Optional.ofNullable(cache.get(secretId).get(secretKey))
        .orElseThrow(
            () ->
                new SecretException(
                    String.format(
                        "Specified key not found in Google Secrets Manager: [projectNumber: %s, secretId: %s, secretKey: %s]",
                        projectNumber, secretId, secretKey)))
        .getBytes();
  }

  private byte[] getSecretPayloadString(
      String projectNumber, String secretId, String secretVersion) {
    return getSecretPayload(projectNumber, secretId, secretVersion)
        .getData()
        .toStringUtf8()
        .getBytes();
  }
}
