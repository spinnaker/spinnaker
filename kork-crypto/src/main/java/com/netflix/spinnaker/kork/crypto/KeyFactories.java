/*
 * Copyright 2023 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.crypto;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;

public final class KeyFactories {
  private static final Map<ASN1ObjectIdentifier, KeyFactory> KEY_FACTORIES_BY_ALGORITHM_IDENTIFIER;

  static {
    try {
      KEY_FACTORIES_BY_ALGORITHM_IDENTIFIER =
          Map.of(
              X9ObjectIdentifiers.id_ecPublicKey, KeyFactory.getInstance("EC"),
              PKCSObjectIdentifiers.rsaEncryption, KeyFactory.getInstance("RSA"));
    } catch (NoSuchAlgorithmException e) {
      throw new NestedSecurityRuntimeException(e);
    }
  }

  private KeyFactories() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static KeyFactory getKeyFactory(ASN1ObjectIdentifier algorithmIdentifier) {
    KeyFactory keyFactory = KEY_FACTORIES_BY_ALGORITHM_IDENTIFIER.get(algorithmIdentifier);
    if (keyFactory == null) {
      throw new UnsupportedOperationException(
          "Unsupported key algorithm identifier: " + algorithmIdentifier);
    }
    return keyFactory;
  }
}
