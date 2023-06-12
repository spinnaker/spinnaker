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

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.x500.X500PrivateCredential;
import lombok.Getter;

/**
 * Provides a static implementation of an {@link X509Identity}. This identity is configured with a
 * parsed {@link PrivateKey} and corresponding {@link X509Certificate} chain.
 */
public class StaticX509Identity implements X509Identity {
  private final X509Certificate[] certificateChain;
  @Getter private final X500PrivateCredential privateCredential;

  public StaticX509Identity(PrivateKey privateKey, X509Certificate[] certificateChain) {
    if (certificateChain.length == 0) {
      throw new IllegalArgumentException("Certificate chain must have at least one certificate");
    }
    this.certificateChain = certificateChain.clone();
    this.privateCredential = new X500PrivateCredential(certificateChain[0], privateKey);
  }

  @Override
  public X509Certificate[] getCertificateChain() {
    return certificateChain.clone();
  }

  @Override
  public void destroy() throws DestroyFailedException {
    privateCredential.destroy();
    Arrays.fill(certificateChain, null);
  }

  @Override
  public boolean isDestroyed() {
    return privateCredential.isDestroyed();
  }
}
