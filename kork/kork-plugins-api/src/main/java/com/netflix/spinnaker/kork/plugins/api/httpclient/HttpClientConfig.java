/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.api.httpclient;

import com.netflix.spinnaker.kork.annotations.Beta;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** The {@link HttpClient} configuration. */
@Beta
public class HttpClientConfig {

  /** Security-related configuration options. */
  private final SecurityConfig security = new SecurityConfig();

  /** Connection-related configuration options. */
  private final ConnectionConfig connection = new ConnectionConfig();

  /** Connection pool-related configuration options. */
  private final ConnectionPoolConfig connectionPool = new ConnectionPoolConfig();

  private final LoggingConfig logging = new LoggingConfig();

  public SecurityConfig getSecurity() {
    return security;
  }

  public ConnectionConfig getConnection() {
    return connection;
  }

  public ConnectionPoolConfig getConnectionPool() {
    return connectionPool;
  }

  public LoggingConfig getLogging() {
    return logging;
  }

  public static class SecurityConfig {

    private static final String DEFAULT_TYPE = "PKCS12";

    /** Filesystem path to an optional KeyStore. */
    private Path keyStorePath;

    /** Password for the {@code keyStorePath}. */
    private String keyStorePassword;

    /** The type of KeyStore. Defaults to PKCS12. */
    private String keyStoreType = DEFAULT_TYPE;

    /** Filesystem path to an optional TrustStore. */
    private Path trustStorePath;

    /** Password for the {@code trustStorePath}. */
    private String trustStorePassword;

    /** The type of TrustStore. Defaults to PKCS12. */
    private String trustStoreType = DEFAULT_TYPE;

    /** List of acceptable TLS versions. */
    private List<String> tlsVersions;

    /** List of acceptable cipher suites. */
    private List<String> cipherSuites;

    public Path getKeyStorePath() {
      return keyStorePath;
    }

    public SecurityConfig setKeyStorePath(Path keyStorePath) {
      this.keyStorePath = keyStorePath;
      return this;
    }

    public Path getTrustStorePath() {
      return trustStorePath;
    }

    public SecurityConfig setTrustStorePath(Path trustStorePath) {
      this.trustStorePath = trustStorePath;
      return this;
    }

    public String getKeyStorePassword() {
      return keyStorePassword;
    }

    public SecurityConfig setKeyStorePassword(String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
      return this;
    }

    public String getTrustStorePassword() {
      return trustStorePassword;
    }

    public SecurityConfig setTrustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
      return this;
    }

    public String getKeyStoreType() {
      return keyStoreType;
    }

    public SecurityConfig setKeyStoreType(String keyStoreType) {
      this.keyStoreType = keyStoreType;
      return this;
    }

    public String getTrustStoreType() {
      return trustStoreType;
    }

    public SecurityConfig setTrustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
      return this;
    }

    public List<String> getTlsVersions() {
      return tlsVersions;
    }

    public SecurityConfig setTlsVersions(List<String> tlsVersions) {
      this.tlsVersions = tlsVersions;
      return this;
    }

    public List<String> getCipherSuites() {
      return cipherSuites;
    }

    public SecurityConfig setCipherSuites(List<String> cipherSuites) {
      this.cipherSuites = cipherSuites;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SecurityConfig that = (SecurityConfig) o;

      if (!Objects.equals(keyStorePath, that.keyStorePath)) return false;
      if (!Objects.equals(keyStorePassword, that.keyStorePassword)) return false;
      if (!Objects.equals(keyStoreType, that.keyStoreType)) return false;
      if (!Objects.equals(trustStorePath, that.trustStorePath)) return false;
      if (!Objects.equals(trustStorePassword, that.trustStorePassword)) return false;
      if (!Objects.equals(trustStoreType, that.trustStoreType)) return false;
      if (!Objects.equals(tlsVersions, that.tlsVersions)) return false;
      return Objects.equals(cipherSuites, that.cipherSuites);
    }

    @Override
    public int hashCode() {
      int result = keyStorePath != null ? keyStorePath.hashCode() : 0;
      result = 31 * result + (keyStorePassword != null ? keyStorePassword.hashCode() : 0);
      result = 31 * result + (keyStoreType != null ? keyStoreType.hashCode() : 0);
      result = 31 * result + (trustStorePath != null ? trustStorePath.hashCode() : 0);
      result = 31 * result + (trustStorePassword != null ? trustStorePassword.hashCode() : 0);
      result = 31 * result + (trustStoreType != null ? trustStoreType.hashCode() : 0);
      result = 31 * result + (tlsVersions != null ? tlsVersions.hashCode() : 0);
      result = 31 * result + (cipherSuites != null ? cipherSuites.hashCode() : 0);
      return result;
    }
  }

  public static class ConnectionConfig {

    /** Network connection timeout. */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** Response timeout. */
    private Duration readTimeout = Duration.ofSeconds(30);

    /** Whether or not to retry on a network connection failure. Defaults to true. */
    private boolean retryOnConnectionFailure = true;

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public ConnectionConfig setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    public Duration getReadTimeout() {
      return readTimeout;
    }

    public ConnectionConfig setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
      return this;
    }

    public boolean isRetryOnConnectionFailure() {
      return retryOnConnectionFailure;
    }

    public ConnectionConfig setRetryOnConnectionFailure(boolean retryOnConnectionFailure) {
      this.retryOnConnectionFailure = retryOnConnectionFailure;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ConnectionConfig that = (ConnectionConfig) o;

      if (retryOnConnectionFailure != that.retryOnConnectionFailure) return false;
      if (!Objects.equals(connectTimeout, that.connectTimeout)) return false;
      return Objects.equals(readTimeout, that.readTimeout);
    }

    @Override
    public int hashCode() {
      int result = connectTimeout != null ? connectTimeout.hashCode() : 0;
      result = 31 * result + (readTimeout != null ? readTimeout.hashCode() : 0);
      result = 31 * result + (retryOnConnectionFailure ? 1 : 0);
      return result;
    }
  }

  public static class ConnectionPoolConfig {
    /** Max number of idle connections to keep in memory. */
    private Integer maxIdleConnections = 5;

    /** The amount of time to keep a connection alive. */
    private Duration keepAlive = Duration.ofSeconds(60);

    public Integer getMaxIdleConnections() {
      return maxIdleConnections;
    }

    public void setMaxIdleConnections(Integer maxIdleConnections) {
      this.maxIdleConnections = maxIdleConnections;
    }

    public Duration getKeepAlive() {
      return keepAlive;
    }

    public void setKeepAlive(Duration keepAlive) {
      this.keepAlive = keepAlive;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ConnectionPoolConfig that = (ConnectionPoolConfig) o;

      if (!Objects.equals(maxIdleConnections, that.maxIdleConnections)) return false;
      return Objects.equals(keepAlive, that.keepAlive);
    }

    @Override
    public int hashCode() {
      int result = maxIdleConnections != null ? maxIdleConnections.hashCode() : 0;
      result = 31 * result + (keepAlive != null ? keepAlive.hashCode() : 0);
      return result;
    }
  }

  public static class LoggingConfig {
    public enum LoggingLevel {
      NONE,
      BASIC,
      HEADERS,
      BODY
    }

    private LoggingLevel level = LoggingLevel.BASIC;

    public LoggingLevel getLevel() {
      return level;
    }

    public void setLevel(LoggingLevel level) {
      this.level = level;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LoggingConfig that = (LoggingConfig) o;

      return level == that.level;
    }

    @Override
    public int hashCode() {
      return level != null ? level.hashCode() : 0;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HttpClientConfig that = (HttpClientConfig) o;

    if (!security.equals(that.security)) return false;
    if (!connection.equals(that.connection)) return false;
    return connectionPool.equals(that.connectionPool);
  }

  @Override
  public int hashCode() {
    int result = security.hashCode();
    result = 31 * result + connection.hashCode();
    result = 31 * result + connectionPool.hashCode();
    return result;
  }
}
