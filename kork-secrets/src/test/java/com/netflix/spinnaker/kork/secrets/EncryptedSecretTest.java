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

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class EncryptedSecretTest {

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void isEncryptedSecretShouldReturnFalse() {
    String secretConfig = "foo";
    assertEquals(false, EncryptedSecret.isEncryptedSecret(secretConfig));
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
  public void parseSecretConfigValue() {
    String secretConfig = "encrypted:s3!first:key!second:another-valu:e!3rd:yetAnothervalue";
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
  }

  @Test
  public void formatEncryptedSecret() {
    String secretConfig =
        "encrypted:aws-kms!foo:bar!key:value!config:value:with:colon!lastKey:secret-param_3";
    EncryptedSecret encryptedSecret = new EncryptedSecret(secretConfig);
    String formattedSecret = encryptedSecret.formatString();
    EncryptedSecret encryptedSecretFromFormattedSecret = new EncryptedSecret(formattedSecret);
    assertEquals(encryptedSecret, encryptedSecretFromFormattedSecret);
  }

  @Test
  public void updateThrowsInvalidSecretFormatException() {
    exceptionRule.expect(InvalidSecretFormatException.class);
    exceptionRule.expectMessage(
        "Invalid encrypted secret format, must have at least one parameter");
    EncryptedSecret encryptedSecret = new EncryptedSecret();
    encryptedSecret.update("encrypted:s3");
  }

  @Test
  public void updateThrowsInvalidSecretFormatExceptionNoKeyValuePairs() {
    exceptionRule.expect(InvalidSecretFormatException.class);
    exceptionRule.expectMessage(
        "Invalid encrypted secret format, keys and values must be delimited by ':'");
    EncryptedSecret encryptedSecret = new EncryptedSecret();
    encryptedSecret.update("encrypted:s3!foobar");
  }
}
