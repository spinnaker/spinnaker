/*
 * Copyright 2020 Nike, Inc.
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

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.AWSSecretsManagerException;
import com.amazonaws.services.secretsmanager.model.DescribeSecretRequest;
import com.amazonaws.services.secretsmanager.model.DescribeSecretResult;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.Tag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import com.netflix.spinnaker.kork.secrets.SecretException;
import com.netflix.spinnaker.kork.secrets.StandardSecretParameter;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadata;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadataField;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerde;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerdeFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import org.springframework.stereotype.Component;

/**
 * Secret engine using AWS Secrets Manager. Authentication is performed using the AWS managing
 * credentials and must have permission to perform {@code secretsmanager:DescribeSecret} and {@code
 * secretsmanager:GetSecretValue} actions on relevant secrets. The "describe secret" action is used
 * for {@link UserSecretMetadata} data which encodes said metadata as tags on the corresponding
 * secret. Tag keys correspond to {@link UserSecretMetadataField} constants, and the {@code
 * spinnaker:roles} tag should contain a comma-separated list of roles in its tag value (tags are
 * string:string key/value pairs, not arbitrary JSON). User secrets without a {@code
 * spinnaker:encoding} tag are assumed to be encoded as JSON to match existing typical usage of AWS
 * Secrets Manager, though other user secret encoding formats are still supported via that tag.
 */
@Component
public class SecretsManagerSecretEngine implements SecretEngine {
  protected static final String SECRET_NAME = "s";
  protected static final String SECRET_REGION = "r";
  protected static final String SECRET_KEY = StandardSecretParameter.KEY.getParameterName();

  private static final String IDENTIFIER = "secrets-manager";

  private final Map<String, Map<String, String>> cache = new HashMap<>();
  private final ObjectMapper mapper;
  private final UserSecretSerdeFactory userSecretSerdeFactory;
  private final SecretsManagerClientProvider clientProvider;

  public SecretsManagerSecretEngine(
      ObjectMapper mapper,
      UserSecretSerdeFactory userSecretSerdeFactory,
      SecretsManagerClientProvider clientProvider) {
    this.mapper = mapper;
    this.userSecretSerdeFactory = userSecretSerdeFactory;
    this.clientProvider = clientProvider;
  }

  @Override
  public String identifier() {
    return SecretsManagerSecretEngine.IDENTIFIER;
  }

  @Override
  public byte[] decrypt(EncryptedSecret encryptedSecret) {
    if (encryptedSecret.isEncryptedFile()) {
      GetSecretValueResult secretFileValue = getSecretValue(encryptedSecret.getParams());
      if (secretFileValue.getSecretBinary() != null) {
        return toByteArray(secretFileValue.getSecretBinary());
      } else {
        return secretFileValue.getSecretString().getBytes(StandardCharsets.UTF_8);
      }
    } else {
      return getSecretString(encryptedSecret.getParams());
    }
  }

  @Override
  @NonNull
  public UserSecret decrypt(@NonNull UserSecretReference reference) {
    validate(reference);
    Map<String, String> parameters = reference.getParameters();
    Map<String, String> tags =
        getSecretDescription(parameters).getTags().stream()
            .filter(tag -> tag.getKey().startsWith(UserSecretMetadataField.PREFIX))
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    String type = tags.get(UserSecretMetadataField.TYPE.getTagKey());
    if (type == null) {
      throw new InvalidSecretFormatException(
          "No " + UserSecretMetadataField.TYPE.getTagKey() + " tag found for " + reference);
    }
    String encoding = tags.getOrDefault(UserSecretMetadataField.ENCODING.getTagKey(), "json");
    List<String> roles =
        Optional.ofNullable(tags.get(UserSecretMetadataField.ROLES.getTagKey())).stream()
            .flatMap(rolesValue -> Stream.of(rolesValue.split("\\s*,\\s*")))
            .collect(Collectors.toList());
    UserSecretMetadata metadata =
        UserSecretMetadata.builder().type(type).encoding(encoding).roles(roles).build();
    UserSecretSerde serde = userSecretSerdeFactory.serdeFor(metadata);
    GetSecretValueResult secretValue = getSecretValue(parameters);
    ByteBuffer secretBinary = secretValue.getSecretBinary();
    byte[] encodedData =
        secretBinary != null
            ? toByteArray(secretBinary)
            : secretValue.getSecretString().getBytes(StandardCharsets.UTF_8);
    return serde.deserialize(encodedData, metadata);
  }

  @Override
  public void validate(EncryptedSecret encryptedSecret) {
    Set<String> paramNames = encryptedSecret.getParams().keySet();
    if (!paramNames.contains(SECRET_NAME)) {
      throw new InvalidSecretFormatException(
          "Secret name parameter is missing (" + SECRET_NAME + "=...)");
    }
    if (!paramNames.contains(SECRET_REGION)) {
      throw new InvalidSecretFormatException(
          "Secret region parameter is missing (" + SECRET_REGION + "=...)");
    }
    if (encryptedSecret.isEncryptedFile() && paramNames.contains(SECRET_KEY)) {
      throw new InvalidSecretFormatException("Encrypted file should not specify key");
    }
  }

  @Override
  public void validate(@NonNull UserSecretReference reference) {
    Set<String> paramNames = reference.getParameters().keySet();
    if (!paramNames.contains(SECRET_NAME)) {
      throw new InvalidSecretFormatException(
          "Secret name parameter is missing (" + SECRET_NAME + "=...)");
    }
    if (!paramNames.contains(SECRET_REGION)) {
      throw new InvalidSecretFormatException(
          "Secret region parameter is missing (" + SECRET_REGION + "=...)");
    }
  }

  @Override
  public void clearCache() {
    cache.clear();
  }

  protected DescribeSecretResult getSecretDescription(Map<String, String> parameters) {
    String secretRegion = parameters.get(SECRET_REGION);
    String secretName = parameters.get(SECRET_NAME);
    AWSSecretsManager client = clientProvider.getClientForSecretParameters(parameters);
    var request = new DescribeSecretRequest().withSecretId(secretName);
    try {
      return client.describeSecret(request);
    } catch (AWSSecretsManagerException e) {
      throw new SecretException(
          String.format(
              "An error occurred when using AWS Secrets Manager to describe secret: [secretName: %s, secretRegion: %s]",
              secretName, secretRegion),
          e);
    }
  }

  protected GetSecretValueResult getSecretValue(Map<String, String> parameters) {
    String secretRegion = parameters.get(SECRET_REGION);
    String secretName = parameters.get(SECRET_NAME);
    AWSSecretsManager client = clientProvider.getClientForSecretParameters(parameters);

    GetSecretValueRequest getSecretValueRequest =
        new GetSecretValueRequest().withSecretId(secretName);

    try {
      return client.getSecretValue(getSecretValueRequest);
    } catch (AWSSecretsManagerException e) {
      throw new SecretException(
          String.format(
              "An error occurred when using AWS Secrets Manager to fetch: [secretName: %s, secretRegion: %s]",
              secretName, secretRegion),
          e);
    }
  }

  private byte[] getSecretString(Map<String, String> parameters) {
    String secretKey = parameters.get(SECRET_KEY);
    if (secretKey == null) {
      return getSecretValue(parameters).getSecretString().getBytes(StandardCharsets.UTF_8);
    }
    return Optional.ofNullable(
            cache
                .computeIfAbsent(
                    parameters.get(SECRET_NAME),
                    ignored -> {
                      try {
                        return mapper
                            .readerForMapOf(String.class)
                            .readValue(getSecretValue(parameters).getSecretString());
                      } catch (JsonProcessingException | IllegalArgumentException e) {
                        throw new SecretException(
                            String.format(
                                "Failed to parse secret when using AWS Secrets Manager to fetch: %s",
                                parameters),
                            e);
                      }
                    })
                .get(secretKey))
        .orElseThrow(
            () ->
                new SecretException(
                    String.format(
                        "Specified key not found in AWS Secrets Manager: %s", parameters)))
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] toByteArray(ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }
}
