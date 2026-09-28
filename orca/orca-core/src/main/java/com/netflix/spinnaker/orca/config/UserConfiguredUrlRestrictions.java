/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.config;

import com.netflix.spinnaker.kork.web.url.UrlRestrictions;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import okhttp3.HttpUrl;

/**
 * Orca's {@code user-configured-url-restrictions}: the shared {@link UrlRestrictions} validation
 * plus the HTTP client settings used when fetching user-supplied URLs.
 */
public class UserConfiguredUrlRestrictions {
  @Data
  public static class Builder {
    private String allowedHostnamesRegex = UrlRestrictions.DEFAULT_ALLOWED_HOSTNAMES_REGEX;
    private List<String> allowedSchemes = new ArrayList<>(UrlRestrictions.DEFAULT_ALLOWED_SCHEMES);
    private boolean rejectLocalhost = true;
    private boolean rejectLinkLocal = true;
    private boolean rejectVerbatimIps = true;
    private HttpClientProperties httpClientProperties = new HttpClientProperties();
    private List<String> rejectedIps =
        new ArrayList<>(); // can contain IP addresses and/or IP ranges (CIDR block)

    // Blanket exclusion on certain domains
    // This default pattern will exclude anything that is suffixed with the excluded domain
    private String excludedDomainTemplate = UrlRestrictions.DEFAULT_EXCLUDED_DOMAIN_TEMPLATE;
    private List<String> excludedDomains = UrlRestrictions.DEFAULT_EXCLUDED_DOMAINS;
    // Generate exclusion patterns based on the values of environment variables
    // Useful for dynamically excluding all requests to the current k8s namespace, for example
    private List<String> excludedDomainsFromEnvironment = List.of();
    private List<String> extraExcludedPatterns = List.of();

    // not bound from configuration; lets tests avoid real DNS lookups
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private UrlRestrictions.HostResolver hostResolver;

    public Builder withAllowedHostnamesRegex(String allowedHostnamesRegex) {
      setAllowedHostnamesRegex(allowedHostnamesRegex);
      return this;
    }

    public Builder withAllowedSchemes(List<String> allowedSchemes) {
      setAllowedSchemes(allowedSchemes);
      return this;
    }

    public Builder withRejectLocalhost(boolean rejectLocalhost) {
      setRejectLocalhost(rejectLocalhost);
      return this;
    }

    public Builder withRejectLinkLocal(boolean rejectLinkLocal) {
      setRejectLinkLocal(rejectLinkLocal);
      return this;
    }

    public Builder withRejectVerbatimIps(boolean rejectVerbatimIps) {
      setRejectVerbatimIps(rejectVerbatimIps);
      return this;
    }

    public Builder withRejectedIps(List<String> rejectedIpRanges) {
      setRejectedIps(rejectedIpRanges);
      return this;
    }

    public Builder withHttpClientProperties(HttpClientProperties httpClientProperties) {
      setHttpClientProperties(httpClientProperties);
      return this;
    }

    public Builder withExcludedDomainsFromEnvironment(List<String> envVars) {
      setExcludedDomainsFromEnvironment(envVars);
      return this;
    }

    public Builder withExtraExcludedPatterns(List<String> patterns) {
      setExtraExcludedPatterns(patterns);
      return this;
    }

    public Builder withHostResolver(UrlRestrictions.HostResolver hostResolver) {
      this.hostResolver = hostResolver;
      return this;
    }

    String getEnvValue(String envVarName) {
      return System.getenv(envVarName);
    }

    public UserConfiguredUrlRestrictions build() {
      return new UserConfiguredUrlRestrictions(
          UrlRestrictions.builder()
              .allowedHostnamesRegex(allowedHostnamesRegex)
              .allowedSchemes(allowedSchemes)
              .rejectLocalhost(rejectLocalhost)
              .rejectLinkLocal(rejectLinkLocal)
              .rejectVerbatimIps(rejectVerbatimIps)
              .rejectedIps(rejectedIps)
              .excludedDomainTemplate(excludedDomainTemplate)
              .excludedDomains(excludedDomains)
              .excludedDomainsFromEnvironment(excludedDomainsFromEnvironment)
              .extraExcludedPatterns(extraExcludedPatterns)
              .environment(this::getEnvValue)
              .hostResolver(hostResolver)
              .build(),
          httpClientProperties);
    }
  }

  private final UrlRestrictions urlRestrictions;
  private final HttpClientProperties clientProperties;

  private UserConfiguredUrlRestrictions(
      UrlRestrictions urlRestrictions, HttpClientProperties clientProperties) {
    this.urlRestrictions = urlRestrictions;
    this.clientProperties = clientProperties;
  }

  public URI validateURI(String uri) throws IllegalArgumentException {
    return urlRestrictions.validateURI(uri);
  }

  public URI validateURI(HttpUrl url) throws IllegalArgumentException {
    return urlRestrictions.validateURI(url);
  }

  public Pattern getAllowedHostnames() {
    return urlRestrictions.getAllowedHostnames();
  }

  public Set<String> getAllowedSchemes() {
    return urlRestrictions.getAllowedSchemes();
  }

  public boolean isRejectLocalhost() {
    return urlRestrictions.isRejectLocalhost();
  }

  public boolean isRejectLinkLocal() {
    return urlRestrictions.isRejectLinkLocal();
  }

  public HttpClientProperties getHttpClientProperties() {
    return clientProperties;
  }

  @Data
  @lombok.Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HttpClientProperties {
    @lombok.Builder.Default private boolean enableRetry = true;
    @lombok.Builder.Default private int maxRetryAttempts = 1;
    @lombok.Builder.Default private int retryInterval = 5000;
    @lombok.Builder.Default private int timeoutMillis = 30000;
  }
}
