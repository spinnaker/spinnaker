/*
 * Copyright 2023 Apple Inc.
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

package com.netflix.spinnaker.kork.crypto.test;

import com.netflix.spinnaker.kork.crypto.NestedSecurityRuntimeException;
import com.netflix.spinnaker.kork.crypto.SecureRandomBuilder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class TestCrypto {
  private static final ThreadLocal<KeyPairGenerator> KEY_PAIR_GENERATOR_THREAD_LOCAL =
      ThreadLocal.withInitial(
          () -> {
            try {
              var generator = KeyPairGenerator.getInstance("EC");
              var random =
                  SecureRandomBuilder.create()
                      .withStrength(256)
                      .withPersonalizationString("test keypair generator")
                      .build();
              generator.initialize(256, random);
              return generator;
            } catch (NoSuchAlgorithmException e) {
              throw new NestedSecurityRuntimeException(e);
            }
          });
  private static final ThreadLocal<SecureRandom> PASSWORD_GENERATOR =
      ThreadLocal.withInitial(
          () ->
              SecureRandomBuilder.create().withPersonalizationString("password generator").build());

  private TestCrypto() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static KeyPair generateKeyPair() {
    return KEY_PAIR_GENERATOR_THREAD_LOCAL.get().generateKeyPair();
  }

  public static char[] generatePassword(int length) {
    SecureRandom random = PASSWORD_GENERATOR.get();
    char[] password = new char[length];
    for (int i = 0; i < length; i++) {
      password[i] = (char) ('a' + random.nextInt(26));
    }
    return password;
  }
}
