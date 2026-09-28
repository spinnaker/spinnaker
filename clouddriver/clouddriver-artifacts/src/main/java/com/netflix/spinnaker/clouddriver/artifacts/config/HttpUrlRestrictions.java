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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.kork.web.url.UrlRestrictions;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import okhttp3.HttpUrl;

/**
 * Per-account restrictions on URLs taken from user input, validated with the shared {@link
 * UrlRestrictions}. These are NOT rbac enabled: they apply to the artifact credentials as a whole,
 * so permissions must still be enforced by the account credentials.
 *
 * <p>By default this blocks localhost, link local, raw IPs, single-label names and the {@code
 * spinnaker}/{@code local}/{@code localdomain}/{@code internal} domains. Private CIDR ranges are
 * NOT blocked unless listed in {@code rejectedIps}, so you'll REALLY want to adjust these
 * restrictions for your environment. It's HIGHLY recommended to configure allow lists ({@code
 * allowedDomains}/{@code allowedHostnamesRegex}) rather than relying on block lists, given that DNS
 * rebinding and similar attacks remain viable with unsanitized URLs.
 *
 * <p>FURTHER NOTE: Since these artifacts DO make requests WITH auth data, unsanitized URLs can be
 * used to extract said auth data via header extraction on the remote side. THIS MEANS you can
 * expose the artifact credentials easily UNLESS you're using trusted domains with these accounts.
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HttpUrlRestrictions {

  @Builder.Default
  private String allowedHostnamesRegex = UrlRestrictions.DEFAULT_ALLOWED_HOSTNAMES_REGEX;

  @Builder.Default
  private List<String> allowedSchemes = new ArrayList<>(UrlRestrictions.DEFAULT_ALLOWED_SCHEMES);

  @Builder.Default private boolean rejectLocalhost = true;
  @Builder.Default private boolean rejectLinkLocal = true;
  @Builder.Default private boolean rejectVerbatimIps = true;

  /** Whitelist range of addresses if set. */
  @Builder.Default private List<String> allowedDomains = List.of();

  /**
   * List of ip ranges (or IPs) to reject for access, checked against every address a hostname
   * resolves to - this can protect against SOME attacks but not all.
   */
  @Builder.Default private List<String> rejectedIps = List.of();

  // Blanket exclusion on certain domains
  // This default pattern will exclude anything that is suffixed with the excluded domain
  @Builder.Default
  private String excludedDomainTemplate = UrlRestrictions.DEFAULT_EXCLUDED_DOMAIN_TEMPLATE;

  @Builder.Default private List<String> excludedDomains = UrlRestrictions.DEFAULT_EXCLUDED_DOMAINS;

  // Generate exclusion patterns based on the values of environment variables
  // Useful for dynamically excluding all requests to the current k8s namespace, for example
  @Builder.Default private List<String> excludedDomainsFromEnvironment = List.of();
  @Builder.Default private List<String> extraExcludedPatterns = List.of();

  /**
   * Built by {@link #builder()}, or on first use when the restrictions were bound from account
   * configuration (no-arg constructor and setters).
   */
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private volatile UrlRestrictions urlRestrictions;

  public static HttpUrlRestrictionsBuilder builder() {
    return new ValidatingBuilder();
  }

  private static class ValidatingBuilder extends HttpUrlRestrictionsBuilder {
    @Override
    public HttpUrlRestrictions build() {
      HttpUrlRestrictions restrictions = super.build();
      // fail fast on invalid patterns or ranges
      restrictions.urlRestrictions = restrictions.toUrlRestrictions();
      return restrictions;
    }
  }

  public URI validateURI(HttpUrl url) throws IllegalArgumentException {
    UrlRestrictions restrictions = urlRestrictions;
    if (restrictions == null) {
      restrictions = toUrlRestrictions();
      urlRestrictions = restrictions;
    }
    return restrictions.validateURI(url);
  }

  private UrlRestrictions toUrlRestrictions() {
    return UrlRestrictions.builder()
        .allowedHostnamesRegex(allowedHostnamesRegex)
        .allowedSchemes(allowedSchemes)
        .rejectLocalhost(rejectLocalhost)
        .rejectLinkLocal(rejectLinkLocal)
        .rejectVerbatimIps(rejectVerbatimIps)
        .allowedDomains(allowedDomains)
        .rejectedIps(rejectedIps)
        .excludedDomainTemplate(excludedDomainTemplate)
        .excludedDomains(excludedDomains)
        .excludedDomainsFromEnvironment(excludedDomainsFromEnvironment)
        .extraExcludedPatterns(extraExcludedPatterns)
        .build();
  }
}
