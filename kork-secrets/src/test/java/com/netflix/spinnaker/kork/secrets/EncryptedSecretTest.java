/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class EncryptedSecretTest {

  @Test
  public void isEncryptedSecretShouldReturnFalse() {
    String secretConfig = "foo";
    assertEquals(false, EncryptedSecret.isEncryptedSecret(secretConfig));
    assertEquals(false, EncryptedSecret.isEncryptedFile(secretConfig));
  }

  @Test
  public void invalidFormat() {
    String secretConfig = "encrypted:aws-kms!no-colon-included!";
    assertEquals(false, EncryptedSecret.isEncryptedSecret(secretConfig));
  }

  @Test
  public void isEncryptedSecretShouldReturnTrue() {
    String secretConfig = "encrypted:aws-kms!foo:bar";
    assertEquals(true, EncryptedSecret.isEncryptedSecret(secretConfig));
  }

  @Test
  public void isEncryptedFileShouldReturnTrue() {
    String secretConfig = "encryptedFile:aws-kms!foo:bar";
    assertEquals(true, EncryptedSecret.isEncryptedSecret(secretConfig));
    assertEquals(true, EncryptedSecret.isEncryptedFile(secretConfig));
  }

  @Test
  public void parseSecretConfigValue() {
    String secretConfig = "encrypted:s3!first:key!second:another-valu:e!3rd:yetAnothervalue";
    EncryptedSecret encryptedSecret = EncryptedSecret.parse(secretConfig);
    assertEquals("s3", encryptedSecret.getEngineIdentifier());
    EncryptedSecret tryEncrypted = EncryptedSecret.tryParse(secretConfig).orElseThrow();
    assertEquals(encryptedSecret, tryEncrypted);
  }

  @Test
  public void parseSecretFileConfigValue() {
    String secretConfig = "encryptedFile:s3!first:key!second:another-valu:e!3rd:yetAnothervalue";
    EncryptedSecret encryptedSecret = EncryptedSecret.parse(secretConfig);
    assertEquals("s3", encryptedSecret.getEngineIdentifier());
  }

  @Test
  public void parseNotSecretConfigValue() {
    String secretConfig = "invalid config value";
    EncryptedSecret encryptedSecret = EncryptedSecret.parse(secretConfig);
    assertEquals(null, encryptedSecret);
  }

  @Test
  public void constructorTest() {
    String secretConfig = "encrypted:aws-kms!foo:bar!key:secret-param-1!Key:secret-param:2";
    EncryptedSecret encryptedSecret = new EncryptedSecret(secretConfig);
    assertEquals("aws-kms", encryptedSecret.getEngineIdentifier());
    assertEquals("bar", encryptedSecret.getParams().get("foo"));
    assertEquals("secret-param-1", encryptedSecret.getParams().get("key"));
    assertEquals("secret-param:2", encryptedSecret.getParams().get("Key"));

    String secretFileConfig = "encryptedFile:aws-kms!foo:bar!key:secret-param-1!Key:secret-param:2";
    EncryptedSecret encryptedSecretFile = new EncryptedSecret(secretConfig);
    assertEquals("aws-kms", encryptedSecretFile.getEngineIdentifier());
    assertEquals("bar", encryptedSecretFile.getParams().get("foo"));
    assertEquals("secret-param-1", encryptedSecretFile.getParams().get("key"));
    assertEquals("secret-param:2", encryptedSecretFile.getParams().get("Key"));
  }

  @Test
  public void updateThrowsInvalidSecretFormatException() {
    Exception exception =
        assertThrows(
            InvalidSecretFormatException.class, () -> new EncryptedSecret().update("encrypted:s3"));
    assertTrue(
        exception
            .getMessage()
            .contains("Invalid encrypted secret format, must have at least one parameter"));
  }

  @Test
  public void updateThrowsInvalidSecretFormatExceptionNoKeyValuePairs() {
    Exception exception =
        assertThrows(
            InvalidSecretFormatException.class,
            () -> new EncryptedSecret().update("encrypted:s3!foobar"));
    assertTrue(
        exception
            .getMessage()
            .contains("Invalid encrypted secret format, keys and values must be delimited by ':'"));
  }

  @Test
  public void tryParseReturnsEmptyOnInvalidSecretFormat() {
    assertFalse(EncryptedSecret.tryParse("encrypted:s3!foobar").isPresent());
  }
}
