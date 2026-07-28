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

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.security.authz.Authorization;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Configuration for the service-account request filter.
 *
 * <p>The canonical/primary key is {@code authz.service-accounts.filter}. The legacy {@code
 * fiat.service-accounts.filter} key is still honored as a DEPRECATED alias for back-compat with
 * existing operator configs (the Fiat service has been removed, so the {@code fiat.*} prefix is now
 * misleading). This class is bound manually via {@link #bind(Environment)} (registered as an
 * explicit {@code @Bean} rather than via {@code @EnableConfigurationProperties}) so the dual-prefix
 * fallback can run: {@code authz.*} wins when present, otherwise the legacy {@code fiat.*} values
 * are used and a one-time deprecation warning is logged.
 */
public class ServiceAccountFilterConfigProps {
  private static final Logger log = LoggerFactory.getLogger(ServiceAccountFilterConfigProps.class);

  /** Canonical/primary configuration prefix. */
  public static final String PROPERTY_PREFIX = "authz.service-accounts.filter";

  /** Deprecated legacy prefix kept for back-compat with pre-Fiat-removal operator configs. */
  public static final String LEGACY_PROPERTY_PREFIX = "fiat.service-accounts.filter";

  /** Guards the legacy-key deprecation warning so it is logged at most once per process. */
  private static final AtomicBoolean LEGACY_WARNING_LOGGED = new AtomicBoolean(false);

  private static final Set<Authorization> DEFAULT_MATCH_AUTHORIZATIONS =
      Collections.unmodifiableSet(EnumSet.of(Authorization.WRITE, Authorization.EXECUTE));

  private final boolean enabled;
  private final Set<Authorization> matchAuthorizations;

  /**
   * Resolves the effective service-account filter config, preferring the canonical {@code authz.*}
   * key and falling back to the deprecated {@code fiat.*} alias.
   *
   * <ul>
   *   <li>If {@code authz.service-accounts.filter.*} is set, it is used.
   *   <li>Else if {@code fiat.service-accounts.filter.*} is set, it is used and a one-time WARN is
   *       logged advising migration to {@code authz.*}.
   *   <li>Otherwise built-in defaults apply.
   * </ul>
   */
  public static ServiceAccountFilterConfigProps bind(Environment environment) {
    Binder binder = Binder.get(environment);

    BindResult<ServiceAccountFilterConfigProps> primary =
        binder.bind(PROPERTY_PREFIX, ServiceAccountFilterConfigProps.class);
    if (primary.isBound()) {
      return primary.get();
    }

    BindResult<ServiceAccountFilterConfigProps> legacy =
        binder.bind(LEGACY_PROPERTY_PREFIX, ServiceAccountFilterConfigProps.class);
    if (legacy.isBound()) {
      if (LEGACY_WARNING_LOGGED.compareAndSet(false, true)) {
        log.warn(
            "Configuration key '{}' is deprecated and will be removed in a future release; "
                + "migrate to '{}'.",
            LEGACY_PROPERTY_PREFIX,
            PROPERTY_PREFIX);
      }
      return legacy.get();
    }

    return new ServiceAccountFilterConfigProps(null, null);
  }

  // Single constructor: Spring Boot's Binder uses it for constructor binding automatically, so no
  // @ConstructorBinding annotation is required (it was removed from the base package in Boot 3).
  public ServiceAccountFilterConfigProps(Boolean enabled, List<Authorization> matchAuthorizations) {
    this.enabled = enabled == null ? true : enabled;
    if (matchAuthorizations == null) {
      this.matchAuthorizations = DEFAULT_MATCH_AUTHORIZATIONS;
    } else if (matchAuthorizations.isEmpty()) {
      this.matchAuthorizations = Collections.emptySet();
    } else {
      this.matchAuthorizations = Collections.unmodifiableSet(EnumSet.copyOf(matchAuthorizations));
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Collection<Authorization> getMatchAuthorizations() {
    return matchAuthorizations;
  }
}
