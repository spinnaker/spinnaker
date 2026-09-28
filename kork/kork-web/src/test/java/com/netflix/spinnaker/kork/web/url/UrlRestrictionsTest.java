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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlRestrictionsTest {

  private static final String PUBLIC_IP = "93.184.215.14";

  /**
   * Resolves names listed in {@code overrides}; IP literals to themselves; anything else public.
   */
  private static UrlRestrictions.HostResolver resolver(Map<String, List<String>> overrides) {
    return host -> {
      if (InetAddresses.isInetAddress(host)) {
        return new InetAddress[] {InetAddresses.forString(host)};
      }
      if (host.equals("localhost")) {
        return new InetAddress[] {InetAddress.getLoopbackAddress()};
      }
      if (host.startsWith("unresolvable")) {
        throw new UnknownHostException(host);
      }
      return overrides.getOrDefault(host, List.of(PUBLIC_IP)).stream()
          .map(InetAddresses::forString)
          .toArray(InetAddress[]::new);
    };
  }

  private static UrlRestrictions.UrlRestrictionsBuilder builder() {
    return builder(Map.of());
  }

  private static UrlRestrictions.UrlRestrictionsBuilder builder(
      Map<String, List<String>> overrides) {
    return UrlRestrictions.builder().hostResolver(resolver(overrides));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://google.com",
        "https://spinnaker.io/foo/bar",
        "https://echo.external",
        "http://foo.bar",
        "HTTPS://example.com"
      })
  void allowsExternalUrlsByDefault(String url) {
    assertThat(builder().build().validateURI(url)).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://orca",
        "https://spin-orca",
        "https://orca/admin",
        "https://clouddriver.svc.cluster.local",
        "http://spin-clouddriver.spinnaker.svc.cluster.local",
        "https://echo.internal",
        "https://orca.spinnaker",
        "https://orca.spinnaker/admin",
        "http://spinnaker-clouddriver.spinnaker:12345",
        "http://spin-clouddriver.local",
        "http://host.localdomain"
      })
  void rejectsInternalNamesByDefault(String url) {
    assertThatThrownBy(() -> builder().build().validateURI(url))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://example.com", "file:///etc/hosts", "gopher://example.com"})
  void rejectsDisallowedSchemes(String url) {
    assertThatThrownBy(() -> builder().build().validateURI(url))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMissingOrMalformedUrls() {
    UrlRestrictions restrictions = builder().build();
    assertThatThrownBy(() -> restrictions.validateURI((String) null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> restrictions.validateURI("not a url"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"https://www.test.com", "https://foobar.com", "https://www.test_underscore.com"})
  void honorsCustomHostnameRegex(String url) {
    assertThat(builder().allowedHostnamesRegex("^(.+).(.+).com(.*)$").build().validateURI(url))
        .isNotNull();
  }

  @Test
  void rejectsEverythingWhenNoHostnamesAreAllowed() {
    assertThatThrownBy(
            () -> builder().allowedHostnamesRegex("").build().validateURI("https://example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allowedHostnamesRegex");
  }

  @Test
  void authorityBypassIsRejectedWhenHostnameDoesNotMatch() {
    assertThatThrownBy(
            () ->
                builder()
                    .allowedHostnamesRegex("example.com")
                    .build()
                    .validateURI("https://example.com:badpassword@host_with_underscore.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void authorityBypassIsAllowedWhenHostnameMatches() {
    assertThat(
            builder()
                .allowedHostnamesRegex("host_with_underscore.com")
                .build()
                .validateURI("https://example.com:badpassword@host_with_underscore.com"))
        .isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://192.168.0.1",
        "http://172.16.0.1",
        "http://10.0.0.1",
        "http://155.155.155.155",
        "https://[fd12:3456:789a:1::1]",
        "https://[fd12:3456:789a:1::1]:8080"
      })
  void rejectsVerbatimIpsByDefault(String url) {
    assertThatThrownBy(() -> builder().build().validateURI(url))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://192.168.0.1",
        "http://172.16.0.1",
        "http://10.0.0.1",
        "https://example.com:badpassword@[fd12:3456:789a:1::1]:8080",
        "https://[fd12:3456:789a:1::1]:8080",
        "https://[fc12:3456:789a:1::1]:8080"
      })
  void allowsVerbatimIpsWhenConfigured(String url) {
    assertThat(builder().rejectVerbatimIps(false).build().validateURI(url)).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"https://localhost", "http://localhost", "http://127.0.0.1", "https://[::1]"})
  void rejectsLocalhostByDefault(String url) {
    assertThatThrownBy(() -> builder().rejectVerbatimIps(false).build().validateURI(url))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://localhost", "http://localhost"})
  void allowsLocalhostWhenConfiguredRegardlessOfNameFilter(String url) {
    assertThat(
            builder()
                .allowedHostnamesRegex("this_definitely_doesnt_match_localhost")
                .rejectLocalhost(false)
                .build()
                .validateURI(url))
        .isNotNull();
  }

  @ParameterizedTest
  @CsvSource({
    "loopback.example.com, 127.0.0.1",
    "anylocal.example.com, 0.0.0.0",
    "metadata.example.com, 169.254.169.254",
    "v6loopback.example.com, ::1",
    "v6linklocal.example.com, fe80::1"
  })
  void rejectsHostnamesResolvingToLocalOrLinkLocalAddresses(String host, String address) {
    UrlRestrictions restrictions = builder(Map.of(host, List.of(address))).build();
    assertThatThrownBy(() -> restrictions.validateURI("https://" + host + "/"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://192.168.16.22", "https://10.1.2.3"})
  void rejectedIpsBlockVerbatimIps(String url) {
    UrlRestrictions restrictions =
        builder()
            .rejectVerbatimIps(false)
            .rejectedIps(List.of("192.168.0.0/16", "10.0.0.0/8"))
            .build();
    assertThatThrownBy(() -> restrictions.validateURI(url))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Address not allowed");
  }

  @Test
  void rejectedIpsBlockHostnamesResolvingIntoTheRange() {
    UrlRestrictions restrictions =
        builder(Map.of("internal.example.com", List.of("10.1.2.3")))
            .rejectedIps(List.of("10.0.0.0/8"))
            .build();
    assertThatThrownBy(() -> restrictions.validateURI("https://internal.example.com/"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Address not allowed");
  }

  @Test
  void rejectedIpsAllowHostnamesResolvingOutsideTheRange() {
    UrlRestrictions restrictions = builder().rejectedIps(List.of("10.0.0.0/8")).build();
    assertThat(restrictions.validateURI("https://public.example.com/"))
        .hasHost("public.example.com");
  }

  @Test
  void rejectedIpsCheckEveryResolvedAddress() {
    UrlRestrictions restrictions =
        builder(Map.of("mixed.example.com", List.of(PUBLIC_IP, "10.1.2.3")))
            .rejectedIps(List.of("10.0.0.0/8"))
            .build();
    assertThatThrownBy(() -> restrictions.validateURI("https://mixed.example.com/"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectedIpsMatchIpv6Ranges() {
    UrlRestrictions restrictions =
        builder(Map.of("ula.example.com", List.of("fd12:3456:789a:1::1")))
            .rejectedIps(List.of("fc00::/7"))
            .build();
    assertThatThrownBy(() -> restrictions.validateURI("https://ula.example.com/"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void privateAddressesAreAllowedWhenNoRangesAreRejected() {
    UrlRestrictions restrictions =
        builder(Map.of("internal.example.com", List.of("10.1.2.3"))).build();
    assertThat(restrictions.validateURI("https://internal.example.com/"))
        .hasHost("internal.example.com");
  }

  @Test
  void rejectsUnresolvableHosts() {
    assertThatThrownBy(() -> builder().build().validateURI("https://unresolvable.example.com/"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unable to resolve host");
  }

  @Test
  void rejectsInvalidRangesWhenBuilt() {
    assertThatThrownBy(() -> builder().rejectedIps(List.of("not-a-range")).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowedDomainsWorkWithoutAHostnameRegex() {
    UrlRestrictions restrictions =
        builder().allowedHostnamesRegex("").allowedDomains(List.of("google.com")).build();
    assertThat(restrictions.validateURI("http://google.com")).hasHost("google.com");
    assertThatThrownBy(() -> restrictions.validateURI("http://microsoft.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowedDomainsBlockEverythingElse() {
    UrlRestrictions restrictions = builder().allowedDomains(List.of("example.com")).build();
    assertThat(restrictions.validateURI("http://example.com")).hasHost("example.com");
    assertThatThrownBy(() -> restrictions.validateURI("http://google.com"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> restrictions.validateURI("http://example.com:password@some_underscore_host.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "POD_NAMESPACE, kittens, http://fluffy.kittens, false",
    "POD_NAMESPACE, puppies, http://fluffy.kittens, true",
    "ISTIO_META_MESH_ID, istio.mesh, http://fluffy.kittens.istio.colander, true",
    "ISTIO_META_MESH_ID, istio.mesh, http://fluffy.kittens.istio.mesh, false",
    "ISTIO_META_MESH_ID, istio.mesh, http://fluffy.kittens.istiozmesh, true",
    "RANDOM_ENV_VAR, kittens, http://fluffy.kittens, true"
  })
  void excludesDomainsFromEnvironment(String envVar, String envValue, String url, boolean allowed) {
    Function<String, String> environment = name -> name.equals(envVar) ? envValue : null;
    UrlRestrictions restrictions =
        builder()
            .excludedDomainsFromEnvironment(List.of("POD_NAMESPACE", "ISTIO_META_MESH_ID"))
            .environment(environment)
            .build();
    if (allowed) {
      assertThat(restrictions.validateURI(url)).isNotNull();
    } else {
      assertThatThrownBy(() -> restrictions.validateURI(url))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void excludesExtraPatterns() {
    UrlRestrictions restrictions =
        builder().extraExcludedPatterns(List.of(".+\\d+.+", ".+-+.+")).build();
    for (String url : Arrays.asList("http://asdf2345.com", "https://foo-bar.com")) {
      assertThatThrownBy(() -> restrictions.validateURI(url))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(restrictions.validateURI("http://foobar.com")).isNotNull();
  }
}
