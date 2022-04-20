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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretException;
import com.netflix.spinnaker.kork.secrets.user.OpaqueUserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMapper;
import com.netflix.spinnaker.kork.secrets.user.UserSecretMixin;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Spy;

public class SecretsManagerSecretEngineTest {
  @Spy
  private SecretsManagerSecretEngine secretsManagerSecretEngine = new SecretsManagerSecretEngine();

  private UserSecretMapper userSecretMapper;

  private GetSecretValueResult kvSecretValue =
      new GetSecretValueResult().withSecretString("{\"password\":\"hunter2\"}");
  private GetSecretValueResult plaintextSecretValue =
      new GetSecretValueResult().withSecretString("letmein");
  private GetSecretValueResult binarySecretValue =
      new GetSecretValueResult().withSecretBinary(ByteBuffer.wrap("i'm binary".getBytes()));
  private GetSecretValueResult secretStringFileValue =
      new GetSecretValueResult().withSecretString("BEGIN RSA PRIVATE KEY");

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    // logic copied from com.netflix.spinnaker.kork.secrets.SecretConfiguration to avoid turning
    // this into a Spring test
    var subtypeConfigurer = new ObjectMapperSubtypeConfigurer(true);
    var subtypeLocator =
        new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
            UserSecret.class, List.of("com.netflix.spinnaker.kork.secrets.user"));
    List<ObjectMapper> mappers = List.of(new ObjectMapper(), new YAMLMapper(), new CBORMapper());
    mappers.forEach(
        mapper -> {
          mapper.addMixIn(UserSecret.class, UserSecretMixin.class);
          subtypeConfigurer.registerSubtype(mapper, subtypeLocator);
        });
    userSecretMapper = new UserSecretMapper(mappers);
    secretsManagerSecretEngine.setUserSecretMapper(userSecretMapper);
    initMocks(this);
  }

  @Test
  public void decryptStringWithKey() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse("encrypted:secrets-manager!r:us-west-2!s:test-secret!k:password");
    doReturn(kvSecretValue).when(secretsManagerSecretEngine).getSecretValue(any(), any());
    assertArrayEquals("hunter2".getBytes(), secretsManagerSecretEngine.decrypt(kvSecret));
  }

  @Test
  public void decryptStringWithoutKey() {
    EncryptedSecret plaintextSecret =
        EncryptedSecret.parse("encrypted:secrets-manager!r:us-west-2!s:test-secret");
    doReturn(plaintextSecretValue).when(secretsManagerSecretEngine).getSecretValue(any(), any());
    assertArrayEquals("letmein".getBytes(), secretsManagerSecretEngine.decrypt(plaintextSecret));
  }

  @Test
  public void decryptFileWithKey() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse("encryptedFile:secrets-manager!r:us-west-2!s:private-key!k:password");
    exceptionRule.expect(InvalidSecretFormatException.class);
    doReturn(kvSecretValue).when(secretsManagerSecretEngine).getSecretValue(any(), any());
    secretsManagerSecretEngine.validate(kvSecret);
  }

  @Test
  public void decryptSecretStringAsFile() {
    EncryptedSecret secretStringFile =
        EncryptedSecret.parse("encryptedFile:secrets-manager!r:us-west-2!s:private-key");
    doReturn(secretStringFileValue).when(secretsManagerSecretEngine).getSecretValue(any(), any());
    assertArrayEquals(
        "BEGIN RSA PRIVATE KEY".getBytes(), secretsManagerSecretEngine.decrypt(secretStringFile));
  }

  @Test
  public void decryptSecretBinaryAsFile() {
    EncryptedSecret secretBinaryFile =
        EncryptedSecret.parse("encryptedFile:secrets-manager!r:us-west-2!s:private-key");
    doReturn(binarySecretValue).when(secretsManagerSecretEngine).getSecretValue(any(), any());
    assertArrayEquals(
        "i'm binary".getBytes(), secretsManagerSecretEngine.decrypt(secretBinaryFile));
  }

  @Test
  public void decryptStringWithBinaryResult() {
    EncryptedSecret kvSecret =
        EncryptedSecret.parse("encrypted:secrets-manager!r:us-west-2!s:test-secret!k:password");
    doReturn(binarySecretValue).when(secretsManagerSecretEngine).getSecretValue(any(), any());
    exceptionRule.expect(SecretException.class);
    secretsManagerSecretEngine.decrypt(kvSecret);
  }

  @Test
  public void decryptJsonUserSecret() {
    OpaqueUserSecret userSecret =
        OpaqueUserSecret.builder()
            .roles(List.of("a", "b", "c"))
            .stringData(Map.of("password", "hunter2"))
            .build();
    byte[] secretBytes = userSecretMapper.serialize(userSecret, "json");
    GetSecretValueResult stubResult =
        new GetSecretValueResult().withSecretBinary(ByteBuffer.wrap(secretBytes));
    doReturn(stubResult).when(secretsManagerSecretEngine).getSecretValue(any(), any());
    UserSecretReference reference =
        UserSecretReference.parse("secret://secrets-manager?r=us-west-2&s=private-key&e=json");
    assertEquals(
        "hunter2", secretsManagerSecretEngine.decrypt(reference).getSecretString("password"));
  }
}
