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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import com.amazonaws.services.secretsmanager.model.DescribeSecretResult;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretException;
import com.netflix.spinnaker.kork.secrets.user.DefaultUserSecretSerde;
import com.netflix.spinnaker.kork.secrets.user.OpaqueUserSecretData;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretData;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadata;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMetadataField;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerde;
import com.netflix.spinnaker.kork.secrets.user.UserSecretSerdeFactory;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

public class SecretsManagerSecretEngineTest {
  @Spy private SecretsManagerSecretEngine secretsManagerSecretEngine;
  @Mock private SecretsManagerClientProvider clientProvider;

  private UserSecretSerdeFactory userSecretSerdeFactory;
  private UserSecretSerde userSecretSerde;

  private GetSecretValueResult kvSecretValue =
      new GetSecretValueResult().withSecretString("{\"password\":\"hunter2\"}");
  private GetSecretValueResult plaintextSecretValue =
      new GetSecretValueResult().withSecretString("letmein");
  private GetSecretValueResult binarySecretValue =
      new GetSecretValueResult().withSecretBinary(ByteBuffer.wrap("i'm binary".getBytes()));
  private GetSecretValueResult secretStringFileValue =
      new GetSecretValueResult().withSecretString("BEGIN RSA PRIVATE KEY");

  @BeforeEach
  public void setup() {
    ObjectMapper mapper = new ObjectMapper();
    List<ObjectMapper> mappers = List.of(mapper);
    userSecretSerde = new DefaultUserSecretSerde(mappers, List.of(OpaqueUserSecretData.class));
    userSecretSerdeFactory = new UserSecretSerdeFactory(List.of(userSecretSerde));
    secretsManagerSecretEngine =
        new SecretsManagerSecretEngine(mapper, userSecretSerdeFactory, clientProvider);
    initMocks(this);
  }

  @Test
  public void decryptStringWithKey() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse("encrypted:secrets-manager!r:us-west-2!s:test-secret!k:password");
    doReturn(kvSecretValue).when(secretsManagerSecretEngine).getSecretValue(any());
    assertArrayEquals("hunter2".getBytes(), secretsManagerSecretEngine.decrypt(kvSecret));
  }

  @Test
  public void decryptStringWithoutKey() {
    EncryptedSecret plaintextSecret =
        EncryptedSecret.parse("encrypted:secrets-manager!r:us-west-2!s:test-secret");
    doReturn(plaintextSecretValue).when(secretsManagerSecretEngine).getSecretValue(any());
    assertArrayEquals("letmein".getBytes(), secretsManagerSecretEngine.decrypt(plaintextSecret));
  }

  @Test
  public void decryptFileWithKey() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse("encryptedFile:secrets-manager!r:us-west-2!s:private-key!k:password");
    doReturn(kvSecretValue).when(secretsManagerSecretEngine).getSecretValue(any());
    assertThrows(
        InvalidSecretFormatException.class, () -> secretsManagerSecretEngine.validate(kvSecret));
  }

  @Test
  public void decryptSecretStringAsFile() {
    EncryptedSecret secretStringFile =
        EncryptedSecret.parse("encryptedFile:secrets-manager!r:us-west-2!s:private-key");
    doReturn(secretStringFileValue).when(secretsManagerSecretEngine).getSecretValue(any());
    assertArrayEquals(
        "BEGIN RSA PRIVATE KEY".getBytes(), secretsManagerSecretEngine.decrypt(secretStringFile));
  }

  @Test
  public void decryptSecretBinaryAsFile() {
    EncryptedSecret secretBinaryFile =
        EncryptedSecret.parse("encryptedFile:secrets-manager!r:us-west-2!s:private-key");
    doReturn(binarySecretValue).when(secretsManagerSecretEngine).getSecretValue(any());
    assertArrayEquals(
        "i'm binary".getBytes(), secretsManagerSecretEngine.decrypt(secretBinaryFile));
  }

  @Test
  public void decryptStringWithBinaryResult() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse("encrypted:secrets-manager!r:us-west-2!s:test-secret!k:password");
    doReturn(binarySecretValue).when(secretsManagerSecretEngine).getSecretValue(any());
    assertThrows(SecretException.class, () -> secretsManagerSecretEngine.decrypt(kvSecret));
  }

  @Test
  public void decryptJsonUserSecret() {
    DescribeSecretResult description =
        new DescribeSecretResult()
            .withTags(
                new Tag().withKey(UserSecretMetadataField.TYPE.getTagKey()).withValue("opaque"),
                new Tag().withKey(UserSecretMetadataField.ROLES.getTagKey()).withValue("a, b, c"));
    doReturn(description).when(secretsManagerSecretEngine).getSecretDescription(any());

    UserSecretData data = new OpaqueUserSecretData(Map.of("password", "hunter2"));
    UserSecretMetadata metadata =
        UserSecretMetadata.builder()
            .type("opaque")
            .encoding("json")
            .roles(List.of("a", "b", "c"))
            .build();
    byte[] secretBytes = userSecretSerde.serialize(data, metadata);
    GetSecretValueResult stubResult =
        new GetSecretValueResult().withSecretBinary(ByteBuffer.wrap(secretBytes));
    doReturn(stubResult).when(secretsManagerSecretEngine).getSecretValue(any());

    UserSecretReference reference =
        UserSecretReference.parse("secret://secrets-manager?r=us-west-2&s=private-key&k=password");
    UserSecret secret = secretsManagerSecretEngine.decrypt(reference);
    assertEquals("hunter2", secret.getSecretString(reference));
    assertEquals(List.of("a", "b", "c"), secret.getRoles());
  }
}
