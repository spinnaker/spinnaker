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

import com.netflix.spinnaker.fiat.model.Authorization;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties("fiat.service-accounts.filter")
public class ServiceAccountFilterConfigProps {
  private static final Set<Authorization> DEFAULT_MATCH_AUTHORIZATIONS =
      Collections.unmodifiableSet(EnumSet.of(Authorization.WRITE, Authorization.EXECUTE));

  private final boolean enabled;
  private final Set<Authorization> matchAuthorizations;

  @ConstructorBinding
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
