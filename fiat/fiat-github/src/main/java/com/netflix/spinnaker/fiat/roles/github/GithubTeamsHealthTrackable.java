/*
 * Copyright 2026 Razorpay.
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

package com.netflix.spinnaker.fiat.roles.github;

import com.netflix.spinnaker.fiat.providers.HealthTrackable;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;

/**
 * Adds the GitHub team role provider to Fiat's health, when {@code
 * auth.group-membership.github.health-check-enabled} is true. Registered by {@link
 * com.netflix.spinnaker.fiat.config.GitHubConfig}.
 *
 * <p>This is a separate bean so that it can be left out: {@code ResourceProvidersHealthIndicator}
 * reports DOWN until every {@link HealthTrackable} has succeeded once, so a pod starting during a
 * GitHub outage would stay DOWN. Where readiness follows {@code /health}, that can take Fiat, and
 * every service authorizing through it, down over GitHub being unreachable.
 */
public class GithubTeamsHealthTrackable implements HealthTrackable {

  private final GithubTeamsUserRolesProvider provider;

  public GithubTeamsHealthTrackable(GithubTeamsUserRolesProvider provider) {
    this.provider = provider;
  }

  @Override
  public ProviderHealthTracker getHealthTracker() {
    return provider.getHealthTracker();
  }
}
