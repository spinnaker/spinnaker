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

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import lombok.RequiredArgsConstructor;

/**
 * Provides a simple {@link X509ExtendedKeyManager} that uses a single {@link X509Identity} as the
 * source for any keys and certificates required. This is most useful when paired with {@linkplain
 * X509IdentitySource#refreshable(Duration) a refreshable identity}, though if the lifetime of the
 * identity's certificate is expected to outlive the application instance, then a static identity
 * may also be used.
 */
@RequiredArgsConstructor
public class IdentityX509KeyManager extends X509ExtendedKeyManager {
  public static final String ALIAS = "identity";

  private final X509Identity identity;

  @Override
  public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
    return ALIAS;
  }

  @Override
  public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
    return ALIAS;
  }

  @Override
  public String[] getClientAliases(String keyType, Principal[] issuers) {
    return new String[] {ALIAS};
  }

  @Override
  public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
    return ALIAS;
  }

  @Override
  public String[] getServerAliases(String keyType, Principal[] issuers) {
    return new String[] {ALIAS};
  }

  @Override
  public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
    return ALIAS;
  }

  @Override
  public X509Certificate[] getCertificateChain(String alias) {
    return identity.getCertificateChain();
  }

  @Override
  public PrivateKey getPrivateKey(String alias) {
    return identity.getPrivateCredential().getPrivateKey();
  }
}
