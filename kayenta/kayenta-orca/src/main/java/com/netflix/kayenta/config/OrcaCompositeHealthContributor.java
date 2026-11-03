/*
 * Copyright 2022 JPMorgan Chase & Co
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

package com.netflix.kayenta.config;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.*;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;

public class OrcaCompositeHealthContributor implements CompositeHealthContributor {

  private final StatusAggregator statusAggregator;
  private final Map<String, HealthContributor> contributors;

  public OrcaCompositeHealthContributor(
      StatusAggregator statusAggregator, HealthContributors healthContributors) {
    this.statusAggregator = statusAggregator;

    this.contributors = new LinkedHashMap<>();
    healthContributors.forEach(entry -> contributors.put(entry.name(), entry.contributor()));
  }

  @Override
  public HealthContributor getContributor(String name) {
    return contributors.get(name);
  }

  @Override
  public Stream<HealthContributors.Entry> stream() {
    return contributors.entrySet().stream()
        .map(e -> new HealthContributors.Entry(e.getKey(), e.getValue()));
  }

  @NotNull
  @Override
  public Iterator<HealthContributors.Entry> iterator() {
    return stream().iterator();
  }

  public Status status() {
    Set<Status> statuses =
        this.contributors.values().stream()
            .filter(c -> c instanceof HealthIndicator)
            .map(contributor -> ((HealthIndicator) contributor).health(false))
            .map(Health::getStatus)
            .collect(Collectors.toSet());
    statuses.addAll(getDiscoveryStatuses());

    return this.statusAggregator.getAggregateStatus(statuses);
  }

  private Set<Status> getDiscoveryStatuses() {
    HealthContributor discoveryComposite = contributors.get("discoveryComposite");

    if (discoveryComposite != null) {
      return ((DiscoveryCompositeHealthContributor) discoveryComposite)
          .getIndicators().values().stream()
              .map(i -> i.health().getStatus())
              .collect(Collectors.toSet());
    }

    return Collections.emptySet();
  }
}
