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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for {@link UrlRestrictions}. Services bind this wherever they accept user-supplied
 * URLs, e.g. orca's {@code user-configured-url-restrictions} or a clouddriver artifact account's
 * {@code urlRestrictions}, then call {@link #toUrlRestrictions()} once binding is complete.
 *
 * <p>By default this blocks localhost, link local, raw IPs, single-label names and the {@code
 * spinnaker}/{@code local}/{@code localdomain}/{@code internal} domains. Private CIDR ranges are
 * NOT blocked unless listed in {@link #rejectedIps}. Prefer allow lists ({@link #allowedDomains} /
 * {@link #allowedHostnamesRegex}) over block lists.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlRestrictionsProperties {

  /** Excludes anything without a dot, since k8s resolves single-word names. */
  public static final String DEFAULT_ALLOWED_HOSTNAMES_REGEX = ".*\\..+";

  /** Excludes any hostname that ends with the excluded domain. */
  public static final String DEFAULT_EXCLUDED_DOMAIN_TEMPLATE = "(?=.+\\.%s$).*\\..+";

  @Builder.Default private String allowedHostnamesRegex = DEFAULT_ALLOWED_HOSTNAMES_REGEX;

  @Builder.Default private List<String> allowedSchemes = new ArrayList<>(List.of("http", "https"));

  @Builder.Default private boolean rejectLocalhost = true;
  @Builder.Default private boolean rejectLinkLocal = true;
  @Builder.Default private boolean rejectVerbatimIps = true;

  /**
   * If set, the host must match one of these (as a regex); an exact match also bypasses {@link
   * #allowedHostnamesRegex} and the excluded domains.
   */
  @Builder.Default private List<String> allowedDomains = new ArrayList<>();

  /**
   * IP addresses and/or CIDR ranges to reject, checked against every address a hostname resolves
   * to. This can protect against SOME attacks but not all.
   */
  @Builder.Default private List<String> rejectedIps = new ArrayList<>();

  @Builder.Default private String excludedDomainTemplate = DEFAULT_EXCLUDED_DOMAIN_TEMPLATE;

  @Builder.Default
  private List<String> excludedDomains =
      new ArrayList<>(List.of("spinnaker", "local", "localdomain", "internal"));

  /**
   * Names of environment variables whose values are added to {@link #excludedDomains}, e.g. {@code
   * POD_NAMESPACE} to exclude the current k8s namespace.
   */
  @Builder.Default private List<String> excludedDomainsFromEnvironment = new ArrayList<>();

  @Builder.Default private List<String> extraExcludedPatterns = new ArrayList<>();

  /**
   * @throws IllegalArgumentException if a regex or CIDR range is invalid
   */
  public UrlRestrictions toUrlRestrictions() {
    return toUrlRestrictions(System::getenv, UrlRestrictions.HostResolver.SYSTEM);
  }

  /**
   * @param environment looks up the variables named in {@link #excludedDomainsFromEnvironment}
   * @param hostResolver resolves hostnames; tests can supply one to avoid real DNS lookups
   * @throws IllegalArgumentException if a regex or CIDR range is invalid
   */
  public UrlRestrictions toUrlRestrictions(
      Function<String, String> environment, UrlRestrictions.HostResolver hostResolver) {
    return new UrlRestrictions(this, environment, hostResolver);
  }
}
