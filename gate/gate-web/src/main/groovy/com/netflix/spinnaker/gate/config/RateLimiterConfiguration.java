/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.gate.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("rate-limit")
public class RateLimiterConfiguration {

  /**
   * When learning mode is enabled, principals that go over their allocated rate limit will not
   * receive a 429, but gate will still log that they would have been rate limited.
   */
  private boolean learning = true;

  /** The number of requests allowed in the given rateSeconds window. */
  private int capacity = 60;

  /**
   * The number of seconds for each rate limit bucket. Capacity will be filled at the beginning of
   * each rate interval.
   */
  private int rateSeconds = 10;

  /**
   * The registration order for {@link com.netflix.spinnaker.gate.ratelimit.RateLimitingFilter}.
   *
   * <p>100 - Run after the spring security filter chain.
   *
   * <p>-100 - Run prior to the spring security filter chain.
   *
   * <p>When the filter is run prior to the spring security filter chain, it will only rate limit
   * x509 certificate-based requests.
   */
  private int filterOrder = 100;

  /**
   * A principal-specific capacity override map. This can be defined if you want to give a specific
   * principal more or less capacity per rateSeconds than the default.
   */
  private List<PrincipalOverride> capacityByPrincipal = new ArrayList<>();

  /**
   * A role-specific capacity override list, e.g. to give administrators or service accounts more
   * capacity per rateSeconds than the default. In Spinnaker roles are groups: a caller's roles are
   * their Fiat roles (including ones synced from LDAP, SAML, OAuth2 or GitHub) and the role
   * authorities of their authenticated session or token; Spinnaker administrators additionally
   * carry the role {@code SPINNAKER_ADMIN}. Roles are only looked up when this list is non-empty.
   * When a caller has several roles listed here the highest capacity wins. Precedence, highest
   * first: capacity set in Redis for the principal, {@link #capacityByPrincipal}, {@code
   * capacityByRole}, {@link #capacityBySourceApp}, {@link #capacity}. Anonymous callers have no
   * roles.
   */
  private List<RoleOverride> capacityByRole = new ArrayList<>();

  /**
   * A source app-specific capacity override map. This can be defined if you want to give a specific
   * source app more or less capacity per rateSeconds than the default.
   */
  private List<SourceAppOverride> capacityBySourceApp = new ArrayList<>();

  /**
   * Endpoint-specific limits. The first rule matching a request's method and path is applied
   * <em>instead of</em> the principal's default bucket; its counter is separate per rule and per
   * principal. Requests that match no rule use the default bucket. Whether a rule is enforced or
   * only logged follows the principal's learning state unless the rule sets {@code learning}.
   *
   * <p>Example:
   *
   * <pre>
   * rate-limit:
   *   rules:
   *     - name: pipeline-start
   *       methods: [POST]
   *       path-patterns: ["/pipelines/**", "/webhooks/**"]
   *       capacity: 10
   *       rate-seconds: 60
   * </pre>
   */
  private List<Rule> rules = new ArrayList<>();

  /**
   * The number of requests allowed in each rateSeconds window across <em>all</em> unauthenticated
   * callers combined. Anonymous callers share one bucket since they can't be told apart reliably (a
   * caller could change IP or any header at will), so this should comfortably exceed the default
   * {@code capacity}. Health checks, actuator and {@code /auth} endpoints are excluded from
   * limiting, see {@code excludePathPatterns}.
   */
  private int anonymousCapacity = 600;

  /**
   * {@link org.springframework.web.util.pattern.PathPattern}s that are never rate limited, no
   * matter who calls them. Health checks and actuator endpoints are polled by load balancers and
   * orchestrators; throttling them would take healthy instances out of service. {@code /auth}
   * drives login pages and is reached before the caller has an identity.
   */
  private List<String> excludePathPatterns =
      new ArrayList<>(List.of("/health/**", "/actuator/**", "/auth/**"));

  /** A principal-specific rate override map. */
  private List<PrincipalOverride> rateSecondsByPrincipal = new ArrayList<>();

  /**
   * A list of principals whose capacities are being enforced. This setting will only be used when
   * learning mode is ENABLED, allowing operators to incrementally enable rate limiting on a
   * per-principal basis.
   */
  private List<String> enforcing = new ArrayList<>();

  /**
   * A list of principals whose capacities are not being enforced. This setting will only be used
   * when learning mode is DISABLED, allowing operators to enable learning-mode on a per-principal
   * basis.
   */
  private List<String> ignoring = new ArrayList<>();

  // Spring doesn't enjoy principals that have dots in their name, so it can't be a map.
  @Data
  public static class PrincipalOverride {
    private String principal;
    private Integer override;
  }

  @Data
  public static class RoleOverride {
    private String role;
    private Integer override;
  }

  @Data
  public static class SourceAppOverride {
    private String sourceApp;
    private Integer override;
  }

  /** An endpoint-specific limit, see {@link RateLimiterConfiguration#rules}. */
  @Data
  public static class Rule {
    /** Unique identifier; used in the Redis key and metrics, so it may not contain ':'. */
    private String name;

    /** HTTP methods the rule applies to; empty means all methods. */
    private List<String> methods = new ArrayList<>();

    /**
     * Spring {@link org.springframework.web.util.pattern.PathPattern}s matched against the request
     * path, e.g. {@code /pipelines/**}. A trailing slash on the request is ignored.
     */
    private List<String> pathPatterns = new ArrayList<>();

    /** The number of requests allowed in each rateSeconds window. */
    private int capacity;

    private int rateSeconds;

    /** Overrides the principal's learning state for this rule when set. */
    private Boolean learning;
  }
}
