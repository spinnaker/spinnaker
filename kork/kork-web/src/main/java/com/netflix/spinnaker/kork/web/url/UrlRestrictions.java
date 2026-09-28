/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.kork.web.url;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import okhttp3.HttpUrl;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Validates URLs that come from user input (pipeline expressions, webhook stages, artifact
 * references) before Spinnaker makes a request to them.
 *
 * <p>A URL is rejected when any of the following hold:
 *
 * <ul>
 *   <li>its scheme isn't in {@code allowedSchemes};
 *   <li>its host is an IP literal and {@code rejectVerbatimIps} is set;
 *   <li>its host doesn't resolve, or <em>any</em> resolved address is loopback/any-local/one of
 *       this machine's interfaces ({@code rejectLocalhost}), link-local ({@code rejectLinkLocal}),
 *       or inside one of the {@code rejectedIps} CIDR ranges;
 *   <li>its hostname doesn't match {@code allowedHostnamesRegex} (and isn't listed in {@code
 *       allowedDomains}), or matches one of the excluded domains/patterns;
 *   <li>{@code allowedDomains} is non-empty and the host matches none of its entries.
 * </ul>
 */
// TODO: have HTTP clients connect using the addresses resolved here (OkHttp Dns / HttpClient
// DnsResolver) rather than resolving the host separately.
@Getter
public final class UrlRestrictions {

  /** Resolves a hostname (or IP literal) to every address it maps to. */
  @FunctionalInterface
  public interface HostResolver {
    HostResolver SYSTEM = InetAddress::getAllByName;

    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private final Pattern allowedHostnames;
  private final Set<String> allowedSchemes;
  private final boolean rejectLocalhost;
  private final boolean rejectLinkLocal;
  private final boolean rejectVerbatimIps;
  private final List<String> rejectedIps;
  private final List<String> allowedDomains;
  private final List<Pattern> excludedPatterns;

  @Getter(AccessLevel.NONE)
  private final List<IpAddressMatcher> rejectedIpMatchers;

  @Getter(AccessLevel.NONE)
  private final HostResolver hostResolver;

  /** Use {@link UrlRestrictionsProperties#toUrlRestrictions()}. */
  UrlRestrictions(
      UrlRestrictionsProperties properties,
      Function<String, String> environment,
      HostResolver hostResolver) {
    this.allowedHostnames =
        Pattern.compile(
            Optional.ofNullable(properties.getAllowedHostnamesRegex())
                .orElse(UrlRestrictionsProperties.DEFAULT_ALLOWED_HOSTNAMES_REGEX));
    this.allowedSchemes =
        copyOf(properties.getAllowedSchemes()).stream()
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());
    this.rejectLocalhost = properties.isRejectLocalhost();
    this.rejectLinkLocal = properties.isRejectLinkLocal();
    this.rejectVerbatimIps = properties.isRejectVerbatimIps();
    this.rejectedIps = copyOf(properties.getRejectedIps());
    this.rejectedIpMatchers =
        this.rejectedIps.stream()
            .map(IpAddressMatcher::new)
            .collect(Collectors.toUnmodifiableList());
    this.allowedDomains = copyOf(properties.getAllowedDomains());
    this.hostResolver = Objects.requireNonNull(hostResolver);

    List<String> allExcludedDomains = new ArrayList<>(copyOf(properties.getExcludedDomains()));
    copyOf(properties.getExcludedDomainsFromEnvironment()).stream()
        .map(environment)
        .filter(Objects::nonNull)
        .forEach(allExcludedDomains::add);

    String template =
        Optional.ofNullable(properties.getExcludedDomainTemplate())
            .orElse(UrlRestrictionsProperties.DEFAULT_EXCLUDED_DOMAIN_TEMPLATE);
    List<Pattern> patterns = new ArrayList<>();
    allExcludedDomains.stream()
        .map(domain -> Pattern.compile(String.format(template, Pattern.quote(domain))))
        .forEach(patterns::add);
    copyOf(properties.getExtraExcludedPatterns()).stream()
        .map(Pattern::compile)
        .forEach(patterns::add);
    this.excludedPatterns = List.copyOf(patterns);
  }

  /** Restrictions with every default applied. */
  public static UrlRestrictions defaults() {
    return new UrlRestrictionsProperties().toUrlRestrictions();
  }

  public URI validateURI(@Nullable String url) throws IllegalArgumentException {
    return validateURI(url == null ? null : HttpUrl.parse(url));
  }

  public URI validateURI(@Nullable HttpUrl url) throws IllegalArgumentException {
    if (url == null) {
      throw new IllegalArgumentException("URL is missing or malformed");
    }

    URI uri = url.uri().normalize();
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException("non absolute URI " + url);
    }
    if (!allowedSchemes.contains(uri.getScheme().toLowerCase())) {
      throw new IllegalArgumentException("unsupported URI scheme " + url);
    }

    // HttpUrl.host() handles hosts with underscores (where URI.getHost() returns null) and returns
    // IPv6 literals without brackets
    String host = url.host();
    if (host == null || host.isEmpty()) {
      throw new IllegalArgumentException("Unable to determine host for the url provided " + url);
    }

    if (allowedHostnames.pattern().isBlank() && allowedDomains.isEmpty()) {
      throw new IllegalArgumentException(
          "Allowed hostnames are not configured, so external HTTP requests are disabled. Configure"
              + " 'allowedHostnamesRegex' (or 'allowedDomains') in the URL restrictions.");
    }

    boolean isIpLiteral = InetAddresses.isInetAddress(host);
    if (isIpLiteral && rejectVerbatimIps) {
      throw new IllegalArgumentException("Verbatim IP addresses are not allowed");
    }

    boolean allLocal = true;
    for (InetAddress address : resolve(host)) {
      boolean isLocalhost = isLocalhost(address);
      boolean isLinkLocal = address.isLinkLocalAddress();
      if ((isLocalhost && rejectLocalhost) || (isLinkLocal && rejectLinkLocal)) {
        throw new IllegalArgumentException("Host not allowed: " + host);
      }
      String hostAddress = address.getHostAddress();
      if (rejectedIpMatchers.stream().anyMatch(matcher -> matcher.matches(hostAddress))) {
        throw new IllegalArgumentException("Address not allowed: " + host);
      }
      allLocal &= isLocalhost || isLinkLocal;
    }

    // When localhost or link local is explicitly allowed, that takes precedence over the name
    // filter, so local names don't also have to be added to the hostname pattern
    if (!isIpLiteral && !isValidHostname(host) && !allLocal) {
      throw new IllegalArgumentException("Host not allowed: " + host);
    }

    if (!allowedDomains.isEmpty() && allowedDomains.stream().noneMatch(host::matches)) {
      throw new IllegalArgumentException("Host not allowed: " + host);
    }

    return uri;
  }

  private InetAddress[] resolve(String host) {
    try {
      InetAddress[] addresses = hostResolver.resolve(host);
      if (addresses == null || addresses.length == 0) {
        throw new UnknownHostException(host);
      }
      return addresses;
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Unable to resolve host: " + host, e);
    }
  }

  private boolean isValidHostname(String host) {
    return (allowedHostnames.matcher(host).matches()
            && excludedPatterns.stream().noneMatch(p -> p.matcher(host).matches()))
        || allowedDomains.contains(host);
  }

  private static boolean isLocalhost(InetAddress address) {
    if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
      return true;
    }
    try {
      return NetworkInterface.getByInetAddress(address) != null;
    } catch (SocketException e) {
      throw new IllegalArgumentException("Unable to check address " + address, e);
    }
  }

  private static List<String> copyOf(@Nullable Collection<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
