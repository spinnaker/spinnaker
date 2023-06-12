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

import java.nio.charset.StandardCharsets;
import java.security.DrbgParameters;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomParameters;
import javax.annotation.Nullable;

/**
 * Builder class for creating a {@link SecureRandom} instance using a deterministic random bit
 * generator (DRBG).
 *
 * @see <a href="https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-90Ar1.pdf">NIST
 *     Special Publication 800-90A Revision 1</a> (Recommendation for Random Number Generation Using
 *     Deterministic Random Bit Generators)
 * @see DrbgParameters
 */
public class SecureRandomBuilder {
  private String algorithm = "DRBG";
  @Nullable private String providerName;
  @Nullable private Provider provider;
  private int bitStrength = -1;
  private boolean reseed;
  private boolean predictionResistance;
  @Nullable private byte[] personalizationString;

  /**
   * Overrides the {@linkplain SecureRandom#getInstance(String) algorithm name} to use. By default,
   * this is {@code DRBG}.
   */
  public SecureRandomBuilder withAlgorithm(String algorithm) {
    this.algorithm = algorithm;
    return this;
  }

  /** Specifies a particular security provider name to use. */
  public SecureRandomBuilder withProvider(String provider) {
    providerName = provider;
    return this;
  }

  /** Specifies a particular security provider to use. */
  public SecureRandomBuilder withProvider(Provider provider) {
    this.provider = provider;
    return this;
  }

  /**
   * Specifies the required security strength in bits for the built random generator. If set to -1
   * or otherwise left unspecified, then the default strength will be used depending on the system
   * configuration. The default Sun provider uses a strength of 128.
   *
   * @see DrbgParameters
   */
  public SecureRandomBuilder withStrength(int bitStrength) {
    this.bitStrength = bitStrength;
    return this;
  }

  /**
   * Enables support for {@link SecureRandom#reseed()} and {@link
   * SecureRandom#reseed(SecureRandomParameters)}. Long-running use of a random generator may
   * periodically desire reseeding from an underlying entropy source. The default Sun provider
   * supports reseeding.
   *
   * @see DrbgParameters
   */
  public SecureRandomBuilder withReseedSupport() {
    reseed = true;
    return this;
  }

  /**
   * Enables support for prediction resistance (and by extension, reseeding). The default Sun
   * provider supports prediction resistance.
   *
   * @see DrbgParameters
   */
  public SecureRandomBuilder withPredictionResistance() {
    predictionResistance = true;
    return this;
  }

  /**
   * Specifies a personalization string to use during instantiation of the random generator. A
   * personalization string is useful for separating different uses of random generators.
   *
   * @see DrbgParameters
   */
  public SecureRandomBuilder withPersonalizationString(byte[] personalizationString) {
    this.personalizationString = personalizationString.clone();
    return this;
  }

  /**
   * Specifies a personalization string which is converted to UTF-8.
   *
   * @see #withPersonalizationString(byte[])
   */
  public SecureRandomBuilder withPersonalizationString(String personalizationString) {
    this.personalizationString = personalizationString.getBytes(StandardCharsets.UTF_8);
    return this;
  }

  /** Creates a random generator using the settings from this builder. */
  public SecureRandom build() {
    DrbgParameters.Capability capability;
    if (predictionResistance) {
      capability = DrbgParameters.Capability.PR_AND_RESEED;
    } else if (reseed) {
      capability = DrbgParameters.Capability.RESEED_ONLY;
    } else {
      capability = DrbgParameters.Capability.NONE;
    }
    var parameters = DrbgParameters.instantiation(bitStrength, capability, personalizationString);
    var name = providerName;
    var prov = provider;
    try {
      if (name != null) {
        return SecureRandom.getInstance(algorithm, parameters, name);
      }
      if (prov != null) {
        return SecureRandom.getInstance(algorithm, parameters, prov);
      }
      return SecureRandom.getInstance(algorithm, parameters);
    } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
      throw new NestedSecurityRuntimeException(e);
    }
  }

  /** Creates a new builder for {@link SecureRandom} instances. */
  public static SecureRandomBuilder create() {
    return new SecureRandomBuilder();
  }
}
