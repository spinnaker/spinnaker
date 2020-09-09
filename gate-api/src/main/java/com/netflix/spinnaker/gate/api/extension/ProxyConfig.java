/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.gate.api.extension;

import com.netflix.spinnaker.kork.annotations.Alpha;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@Alpha
public class ProxyConfig {
  /** Identifier for this proxy, must be unique. */
  private String id;

  /** Target uri for this proxy. */
  private String uri;

  /** Whether ssl hostname verification should be skipped. */
  private Boolean skipHostnameVerification = false;

  /** Fully qualified path to keystore file. */
  private String keyStore;

  /** Type of keystore, defaults to {@code KeyStore.getDefaultType()}. */
  private String keyStoreType = KeyStore.getDefaultType();

  /**
   * Plain text keystore password.
   *
   * <p>If keyStore is non-null, one of keyStorePassword or keyStorePasswordFile must be supplied.
   */
  private String keyStorePassword;

  /**
   * Fully qualified path to keystore password file.
   *
   * <p>If keyStore is non-null, one of keyStorePassword or keyStorePasswordFile must be supplied.
   */
  private String keyStorePasswordFile;

  /** Fully qualified path to truststore file. */
  private String trustStore;

  /** Type of truststore, defaults to {@code KeyStore.getDefaultType()}. */
  private String trustStoreType = KeyStore.getDefaultType();

  /**
   * Plain text truststore password.
   *
   * <p>If trustStore is non-null, one of trustStorePassword or trustStorePasswordFile must be
   * supplied.
   */
  private String trustStorePassword;

  /**
   * Fully qualified path to truststore password file.
   *
   * <p>If trustStore is non-null, one of trustStorePassword or trustStorePasswordFile must be
   * supplied.
   */
  private String trustStorePasswordFile;

  /** Supported http methods for this proxy. */
  private List<String> methods = new ArrayList<>();

  /** Connection timeout, defaults to 30s. */
  private Long connectTimeoutMs = 30_000L;

  /** Read timeout, defaults to 59s. */
  private Long readTimeoutMs = 59_000L;

  /** Write timeout, defaults to 30s. */
  private Long writeTimeoutMs = 30_000L;
}
