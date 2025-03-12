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

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

public class UserConfiguredUrlRestrictions {
  @Data
  public static class Builder {
    private String allowedHostnamesRegex =
        ".*\\..+"; // Exclude anything without a dot, since k8s resolves single-word names
    private List<String> allowedSchemes = new ArrayList<>(Arrays.asList("http", "https"));
    private boolean rejectLocalhost = true;
    private boolean rejectLinkLocal = true;
    private boolean rejectVerbatimIps = true;
    private HttpClientProperties httpClientProperties = new HttpClientProperties();
    private List<String> rejectedIps =
        new ArrayList<>(); // can contain IP addresses and/or IP ranges (CIDR block)

    // Blanket exclusion on certain domains
    // This default pattern will exclude anything that is suffixed with the excluded domain
    private String excludedDomainTemplate = "(?=.+\\.%s$).*\\..+";
    private List<String> excludedDomains = List.of("spinnaker", "local", "localdomain", "internal");
    // Generate exclusion patterns based on the values of environment variables
    // Useful for dynamically excluding all requests to the current k8s namespace, for example
    private List<String> excludedDomainsFromEnvironment = List.of();
    private List<String> extraExcludedPatterns = List.of();

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

    String getEnvValue(String envVarName) {
      return System.getenv(envVarName);
    }

    List<String> getEnvValues(List<String> envVars) {
      if (envVars == null) return List.of();

      return envVars.stream()
          .map(this::getEnvValue)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    }

    List<Pattern> compilePatterns(List<String> values, String patternStr, boolean quote) {
      if (values == null || patternStr == null) {
        return List.of();
      }

      return values.stream()
          .map(value -> quote ? Pattern.quote(value) : value)
          .map(value -> Pattern.compile(String.format(patternStr, value)))
          .collect(Collectors.toList());
    }

    public UserConfiguredUrlRestrictions build() {
      // Combine and build all excluded domains based on the specified names, env vars, and pattern
      List<String> allExcludedDomains = new ArrayList<>();
      allExcludedDomains.addAll(excludedDomains);
      allExcludedDomains.addAll(getEnvValues(excludedDomainsFromEnvironment));

      // Collect any extra patterns and provide the final list of patterns
      List<Pattern> allExcludedPatterns = new ArrayList<>();
      allExcludedPatterns.addAll(compilePatterns(allExcludedDomains, excludedDomainTemplate, true));
      allExcludedPatterns.addAll(compilePatterns(extraExcludedPatterns, "%s", false));

      return new UserConfiguredUrlRestrictions(
          Pattern.compile(allowedHostnamesRegex),
          allowedSchemes,
          rejectLocalhost,
          rejectLinkLocal,
          rejectVerbatimIps,
          rejectedIps,
          httpClientProperties,
          allExcludedPatterns);
    }
  }

  private final Pattern allowedHostnames;
  private final Set<String> allowedSchemes;
  private final boolean rejectLocalhost;
  private final boolean rejectLinkLocal;
  private final boolean rejectVerbatimIps;
  private final Set<String> rejectedIps;
  private final HttpClientProperties clientProperties;
  private final List<Pattern> excludedPatterns;

  protected UserConfiguredUrlRestrictions(
      Pattern allowedHostnames,
      Collection<String> allowedSchemes,
      boolean rejectLocalhost,
      boolean rejectLinkLocal,
      boolean rejectVerbatimIps,
      Collection<String> rejectedIps,
      HttpClientProperties clientProperties,
      List<Pattern> excludedPatterns) {
    this.allowedHostnames = allowedHostnames;
    this.allowedSchemes =
        allowedSchemes == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new HashSet<>(allowedSchemes));
    this.rejectLocalhost = rejectLocalhost;
    this.rejectLinkLocal = rejectLinkLocal;
    this.rejectVerbatimIps = rejectVerbatimIps;
    this.rejectedIps =
        rejectedIps == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new HashSet<>(rejectedIps));
    this.clientProperties = clientProperties;
    this.excludedPatterns = excludedPatterns;
  }

  InetAddress resolveHost(String host) throws UnknownHostException {
    return InetAddress.getByName(host);
  }

  boolean isLocalhost(InetAddress addr) throws SocketException {
    return addr.isLoopbackAddress()
        || Optional.ofNullable(NetworkInterface.getByInetAddress(addr)).isPresent();
  }

  boolean isLinkLocal(InetAddress addr) {
    return addr.isLinkLocalAddress();
  }

  boolean isValidHostname(String host) {
    return allowedHostnames.matcher(host).matches()
        && excludedPatterns.stream().noneMatch(p -> p.matcher(host).matches());
  }

  boolean isValidIpAddress(String host) {
    var matcher = new IpAddressMatcher(host);
    return rejectedIps.stream().noneMatch(matcher::matches);
  }

  boolean isIpAddress(String host) {
    return InetAddresses.isInetAddress(host);
  }

  public URI validateURI(String url) throws IllegalArgumentException {
    try {
      URI u = URI.create(url).normalize();
      if (!u.isAbsolute()) {
        throw new IllegalArgumentException("non absolute URI " + url);
      }
      if (!allowedSchemes.contains(u.getScheme().toLowerCase())) {
        throw new IllegalArgumentException("unsupported URI scheme " + url);
      }

      // fallback to `getAuthority()` in the event that the hostname contains an underscore and
      // `getHost()` returns null
      String host = u.getHost();
      if (host == null) {
        String authority = u.getAuthority();
        if (authority != null) {
          // Don't attempt to colon-substring ipv6 addresses
          if (isIpAddress(authority)) {
            host = authority;
          } else {
            int portIndex = authority.indexOf(":");
            host = (portIndex > -1) ? authority.substring(0, portIndex) : authority;
          }
        }
      }

      if (host == null || host.isEmpty()) {
        throw new IllegalArgumentException("Unable to determine host for the url provided " + url);
      }

      if (StringUtils.isBlank(allowedHostnames.pattern())) {
        throw new IllegalArgumentException(
            "Allowed Hostnames are not set, external HTTP requests are not enabled. Please configure 'user-configured-url-restrictions.allowedHostnamesRegex' in your orca config.");
      }

      // Strip ipv6 brackets if present
      // InetAddress.getHost() retains them, but other code doesn't quite understand
      host = host.replace("[", "").replace("]", "");

      if (isIpAddress(host) && rejectVerbatimIps) {
        throw new IllegalArgumentException("Verbatim IP addresses are not allowed");
      }

      var addr = resolveHost(host);
      var isLocalhost = isLocalhost(addr);
      var isLinkLocal = isLinkLocal(addr);

      if ((isLocalhost && rejectLocalhost) || (isLinkLocal && rejectLinkLocal)) {
        throw new IllegalArgumentException("Host not allowed: " + host);
      }

      if (!isValidHostname(host) && !isIpAddress(host)) {
        // If localhost or link local is allowed, that takes precedence over the name filter
        // This avoids the need to include local names in the hostname pattern in addition to
        // setting the local config flag
        if (!(isLocalhost || isLinkLocal)) {
          throw new IllegalArgumentException("Host not allowed: " + host);
        }
      }

      if (!isValidIpAddress(host)) {
        throw new IllegalArgumentException("Address not allowed: " + host);
      }

      return u;
    } catch (IllegalArgumentException iae) {
      throw iae;
    } catch (Exception ex) {
      throw new IllegalArgumentException("URI not valid: " + url, ex);
    }
  }

  public Pattern getAllowedHostnames() {
    return allowedHostnames;
  }

  public Set<String> getAllowedSchemes() {
    return allowedSchemes;
  }

  public boolean isRejectLocalhost() {
    return rejectLocalhost;
  }

  public boolean isRejectLinkLocal() {
    return rejectLinkLocal;
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
