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

package com.netflix.spinnaker.clouddriver.artifacts.config;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * A set of restrictions and validations of the restrictions. These in combination provide some
 * safeguards around user input of remote urls and validation that the passed URLs are acceptable to
 * load for a given acccount. NOT this is NOT rbac enabled. E.g. these are TO THE artifact
 * credentials for example and as such must be validated for permissions by the account credentials.
 * We don't in this system allow some access for some users based upon any given user attribute.
 *
 * <p>BY DEFAULT, this class blocks any local CIDR ranges (10.0.0.0/8, 172.16.0.0/12,
 * 192.168.0.0/16), localhost, link local and raw IPs. As such you'll REALLY want to adjust these
 * restrictions depending upon your use case. It's HIGHLY recommended to set some sane default white
 * lists vs. blacklists, given that attacks using DNS Rebinding and other techniques are VERY viable
 * with unsanitized URLs.
 *
 * <p>FURTHER NOTE: Since these artifacts DO make requests WITH auth data, unsanitized URLs can be
 * used to extract said auth data via header extraction on the remote side. THIS MEANS you can
 * expose the artifact credentials easily UNLESS you're using trusted domains with these accounts.
 */
// TODO: A LOT of this code is duplicated in the #UserConfiguredUrlRestrictions object. It should be
// moved here. That object is ALSO broken in a few areas around the ip list system.   LONG term
// should move that code (and this) to maybe a common area, and possibly an OKHTTP interceptor
// INSTEAD of URL validation.  This would catch more easily rebind attacks and similar where it can
// get the resolved IP on a request.
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class HttpUrlRestrictions {

  @Builder.Default
  private String allowedHostnamesRegex =
      ".*\\..+"; // Exclude anything without a dot, since k8s resolves single-word names

  @Builder.Default
  private List<String> allowedSchemes = new ArrayList<>(Arrays.asList("http", "https"));

  @Builder.Default private boolean rejectLocalhost = true;
  @Builder.Default private boolean rejectLinkLocal = true;
  @Builder.Default private boolean rejectVerbatimIps = true;
  /** Whitelist range of addresses if set. */
  @Builder.Default private List<String> allowedDomains = List.of();
  /**
   * List of ip ranges (or IPs) to reject for access - this can protect against SOME attacks but not
   * all.
   */
  @Builder.Default private List<String> rejectedIps = List.of();

  // Blanket exclusion on certain domains
  // This default pattern will exclude anything that is suffixed with the excluded domain
  @Builder.Default private String excludedDomainTemplate = "(?=.+\\.%s$).*\\..+";

  @Builder.Default
  private List<String> excludedDomains = List.of("spinnaker", "local", "localdomain", "internal");
  // Generate exclusion patterns based on the values of environment variables
  // Useful for dynamically excluding all requests to the current k8s namespace, for example
  @Builder.Default private List<String> excludedDomainsFromEnvironment = List.of();
  @Builder.Default private List<String> extraExcludedPatterns = List.of();
  private Pattern allowedHostnames;
  private List<Pattern> excludedPatterns;

  public static HttpUrlRestrictionsBuilder builder() {
    return new PatternBuilder();
  }

  private static class PatternBuilder extends HttpUrlRestrictionsBuilder {
    @Override
    public HttpUrlRestrictions build() {
      HttpUrlRestrictions restrictions = super.build();

      // Combine and build all excluded domains based on the specified names, env vars, and pattern
      List<String> allExcludedDomains = new ArrayList<>();
      allExcludedDomains.addAll(restrictions.excludedDomains);
      allExcludedDomains.addAll(getEnvValues(restrictions.excludedDomainsFromEnvironment));

      // Collect any extra patterns and provide the final list of patterns
      List<Pattern> allExcludedPatterns = new ArrayList<>();
      allExcludedPatterns.addAll(
          compilePatterns(allExcludedDomains, restrictions.excludedDomainTemplate, true));
      allExcludedPatterns.addAll(compilePatterns(restrictions.extraExcludedPatterns, "%s", false));

      restrictions.allowedHostnames = Pattern.compile(restrictions.allowedHostnamesRegex);
      restrictions.excludedPatterns = allExcludedPatterns;
      return restrictions;
    }

    List<String> getEnvValues(List<String> envVars) {
      if (envVars == null) return List.of();

      return envVars.stream().map(System::getenv).filter(Objects::nonNull).toList();
    }

    List<Pattern> compilePatterns(List<String> values, String patternStr, boolean quote) {
      if (values == null || patternStr == null) {
        return List.of();
      }

      return values.stream()
          .map(value -> quote ? Pattern.quote(value) : value)
          .map(value -> Pattern.compile(String.format(patternStr, value)))
          .toList();
    }
  }

  boolean isLocalhost(InetAddress addr) throws SocketException {
    return addr.isLoopbackAddress()
        || Optional.ofNullable(NetworkInterface.getByInetAddress(addr)).isPresent();
  }

  boolean isValidHostname(String host) {
    return (allowedHostnames.matcher(host).matches()
            && excludedPatterns.stream().noneMatch(p -> p.matcher(host).matches()))
        || allowedDomains.contains(host);
  }

  boolean isValidIpAddress(String host) {
    return rejectedIps.stream()
        .noneMatch(restriction -> new IpAddressMatcher(restriction).matches(host));
  }

  public URI validateURI(URI url) throws IllegalArgumentException {
    try {
      URI u = url.normalize();
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
          if (InetAddresses.isInetAddress(authority)) {
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

      if (StringUtils.isBlank(allowedHostnames.pattern()) && allowedDomains.isEmpty()) {
        throw new IllegalArgumentException(
            "Allowed Hostnames are not set, external HTTP requests are not enabled. Please configure the account with 'url-restrictions.allowedHostnamesRegex' to allow access.");
      }

      // Strip ipv6 brackets if present
      // InetAddress.getHost() retains them, but other code doesn't quite understand
      host = host.replace("[", "").replace("]", "");

      if (InetAddresses.isInetAddress(host) && rejectVerbatimIps) {
        throw new IllegalArgumentException("Verbatim IP addresses are not allowed");
      }

      var addr = InetAddress.getByName(host);
      var isLocalhost = isLocalhost(addr);

      if ((isLocalhost && rejectLocalhost) || (addr.isLinkLocalAddress() && rejectLinkLocal)) {
        throw new IllegalArgumentException("Host not allowed: " + host);
      }

      if (!isValidHostname(host) && !InetAddresses.isInetAddress(host)) {
        // If localhost or link local is allowed, that takes precedence over the name filter
        // This avoids the need to include local names in the hostname pattern in addition to
        // setting the local config flag
        if (!(isLocalhost || addr.isLinkLocalAddress())) {
          throw new IllegalArgumentException("Host not allowed: " + host);
        }
      }

      if (!allowedDomains.isEmpty() && allowedDomains.stream().noneMatch(host::matches)) {
        throw new IllegalArgumentException("Host not allowed: " + host);
      }

      if (!isValidIpAddress(host)) {
        throw new IllegalArgumentException("Address not allowed: " + host);
      }

      return u;
    } catch (IllegalArgumentException iae) {
      throw iae;
    } catch (Exception ex) {
      log.error("Unexpected HttpUrlRestrictions Exception: {}", ex.getMessage(), ex);
      throw new IllegalArgumentException("URI not valid: " + url, ex);
    }
  }
}
