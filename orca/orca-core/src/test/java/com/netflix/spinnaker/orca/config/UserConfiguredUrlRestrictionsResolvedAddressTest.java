/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.orca.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code rejectedIps} must be checked against the address a hostname resolves to. Previously it was
 * matched against the hostname string, which Spring Security 6's IpAddressMatcher rejects for every
 * hostname, so configuring any range blocked all hostname URLs.
 */
class UserConfiguredUrlRestrictionsResolvedAddressTest {

  private static final Map<String, String> DNS =
      Map.of(
          "internal.example.com", "10.1.2.3",
          "public.example.com", "93.184.215.14");

  private static UserConfiguredUrlRestrictions withRejectedIps(List<String> rejectedIps) {
    return new UserConfiguredUrlRestrictions.Builder()
        .withRejectedIps(rejectedIps)
        .withHostResolver(
            host -> new InetAddress[] {InetAddresses.forString(DNS.getOrDefault(host, host))})
        .build();
  }

  @Test
  void rejectsHostnameResolvingIntoRejectedRange() {
    UserConfiguredUrlRestrictions restrictions = withRejectedIps(List.of("10.0.0.0/8"));
    assertThatThrownBy(() -> restrictions.validateURI("https://internal.example.com/"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Address not allowed");
  }

  @Test
  void allowsHostnameResolvingOutsideRejectedRange() {
    UserConfiguredUrlRestrictions restrictions = withRejectedIps(List.of("10.0.0.0/8"));
    assertThat(restrictions.validateURI("https://public.example.com/"))
        .hasHost("public.example.com");
  }
}
