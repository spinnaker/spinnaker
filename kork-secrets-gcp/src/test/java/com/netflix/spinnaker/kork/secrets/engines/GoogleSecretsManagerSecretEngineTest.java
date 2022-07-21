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
 */
package com.netflix.spinnaker.kork.secrets.engines;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.protobuf.ByteString;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Spy;

public class GoogleSecretsManagerSecretEngineTest {

  @Spy
  private GoogleSecretsManagerSecretEngine googleSecretsManagerSecretEngine =
      new GoogleSecretsManagerSecretEngine();

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  private final SecretPayload minioAccessKeyId =
      SecretPayload.newBuilder()
          .setData(ByteString.copyFromUtf8("{\"minioAccessKeyId\":\"minioadmin\"}"))
          .build();

  private final SecretPayload binarySecretValue =
      SecretPayload.newBuilder()
          .setData(ByteString.copyFromUtf8("-----BEGIN CERTIFICATE-----"))
          .build();

  private final SecretPayload secretStringFileValue =
      SecretPayload.newBuilder()
          .setData(ByteString.copyFromUtf8("-----BEGIN CERTIFICATE-----"))
          .build();

  private final SecretPayload kvSecretValue =
      SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("minioadmin")).build();

  private final SecretPayload plaintextSecretValue =
      SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("my-k8s-v2-account-name")).build();

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  public void decryptStringWithKey() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse(
            "encrypted:google-secrets-manager!p:824069899151!s:spinnaker-store!k:minioAccessKeyId");
    doReturn(minioAccessKeyId)
        .when(googleSecretsManagerSecretEngine)
        .getSecretPayload(any(), any(), any());
    assertArrayEquals("minioadmin".getBytes(), googleSecretsManagerSecretEngine.decrypt(kvSecret));
  }

  @Test
  public void decryptStringWithoutKey() {
    EncryptedSecret plaintextSecret =
        EncryptedSecret.parse("encrypted:google-secrets-manager!p:824069899151!s:account-name");
    doReturn(plaintextSecretValue)
        .when(googleSecretsManagerSecretEngine)
        .getSecretPayload(any(), any(), any());
    assertArrayEquals(
        "my-k8s-v2-account-name".getBytes(),
        googleSecretsManagerSecretEngine.decrypt(plaintextSecret));
  }

  @Test
  public void decryptFileWithKey() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse(
            "encryptedFile:google-secrets-manager!p:824069899151!s:spinnaker-store!k:minioAccessKeyId");
    exceptionRule.expect(InvalidSecretFormatException.class);
    doReturn(kvSecretValue)
        .when(googleSecretsManagerSecretEngine)
        .getSecretPayload(any(), any(), any());
    googleSecretsManagerSecretEngine.validate(kvSecret);
  }

  @Test
  public void decryptSecretStringAsFile() {
    EncryptedSecret secretStringFile =
        EncryptedSecret.parse("encryptedFile:google-secrets-manager!p:824069899151!s:certificate");
    doReturn(secretStringFileValue)
        .when(googleSecretsManagerSecretEngine)
        .getSecretPayload(any(), any(), any());
    assertArrayEquals(
        "-----BEGIN CERTIFICATE-----".getBytes(),
        googleSecretsManagerSecretEngine.decrypt(secretStringFile));
  }

  @Test
  public void decryptSecretBinaryAsFile() {
    EncryptedSecret secretBinaryFile =
        EncryptedSecret.parse("encryptedFile:google-secrets-manager!p:824069899151!s:certificate");
    doReturn(binarySecretValue)
        .when(googleSecretsManagerSecretEngine)
        .getSecretPayload(any(), any(), any());
    assertArrayEquals(
        "-----BEGIN CERTIFICATE-----".getBytes(),
        googleSecretsManagerSecretEngine.decrypt(secretBinaryFile));
  }

  @Test
  public void decryptStringWithBinaryResult() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse(
            "encrypted:google-secrets-manager!p:824069899151!s:spinnaker-store!k:minioAccessKeyId");
    doReturn(binarySecretValue)
        .when(googleSecretsManagerSecretEngine)
        .getSecretPayload(any(), any(), any());
    exceptionRule.expect(SecretException.class);
    googleSecretsManagerSecretEngine.decrypt(kvSecret);
  }

  @Test
  public void decryptStringWithInvalidParam() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse(
            "encrypted:google-secrets-manager!j:824069899151!s:spinnaker-store!k:account-name");
    doReturn(binarySecretValue)
        .when(googleSecretsManagerSecretEngine)
        .getSecretPayload(any(), any(), any());
    exceptionRule.expect(SecretException.class);
    googleSecretsManagerSecretEngine.decrypt(kvSecret);
  }
}
