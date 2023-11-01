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

package com.netflix.spinnaker.kork.crypto;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a common source for lists of TLS cipher suite baselines.
 *
 * @see <a href="https://wiki.mozilla.org/Security/Server_Side_TLS">Mozilla Server Side TLS
 *     recommendations</a>
 */
public final class CipherSuites {
  private static final List<String> REQUIRED =
      List.of("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256");
  private static final List<String> BROWSER_COMPATIBILITY =
      List.of(
          "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
          "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
          "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
          "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
          "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
          "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256");
  private static final List<String> RESTRICTED =
      List.of(
          "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
          "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
          "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256");

  /**
   * Returns the list of baseline ciphers that should be enabled for TLS. These include the required
   * ciphers for TLSv1.3.
   *
   * @see <a href="https://wiki.mozilla.org/Security/Server_Side_TLS#Modern_compatibility">Modern
   *     compatibility recommendations</a>
   */
  public static List<String> getRequiredCiphers() {
    return REQUIRED;
  }

  public static List<String> getRecommendedCiphers() {
    var ciphers = new ArrayList<>(getRequiredCiphers());
    ciphers.addAll(BROWSER_COMPATIBILITY);
    return ciphers;
  }

  public static List<String> getIntermediateCompatibilityCiphers() {
    var ciphers = getRecommendedCiphers();
    ciphers.addAll(RESTRICTED);
    return ciphers;
  }
}
