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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.actuate.health.*;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;

public class OrcaCompositeHealthContributor implements CompositeHealthContributor {

  private final StatusAggregator statusAggregator;
  private final Map<String, NamedContributor<HealthContributor>> contributors;

  public OrcaCompositeHealthContributor(
      StatusAggregator statusAggregator, HealthContributorRegistry healthContributorRegistry) {
    this.statusAggregator = statusAggregator;

    this.contributors = new LinkedHashMap<>();
    healthContributorRegistry.forEach(
        contributor ->
            contributors.put(
                contributor.getName(),
                NamedContributor.of(contributor.getName(), contributor.getContributor())));
  }

  @Override
  public HealthContributor getContributor(String name) {
    return contributors.get(name).getContributor();
  }

  @Override
  public Stream<NamedContributor<HealthContributor>> stream() {
    return CompositeHealthContributor.super.stream();
  }

  @NotNull
  @Override
  public Iterator<NamedContributor<HealthContributor>> iterator() {
    return contributors.values().iterator();
  }

  @Override
  public void forEach(Consumer<? super NamedContributor<HealthContributor>> action) {
    CompositeHealthContributor.super.forEach(action);
  }

  @Override
  public Spliterator<NamedContributor<HealthContributor>> spliterator() {
    return CompositeHealthContributor.super.spliterator();
  }

  public Status status() {
    Set<Status> statuses =
        this.contributors.values().stream()
            .filter(c -> c.getContributor() instanceof HealthIndicator)
            .map(contributor -> ((HealthIndicator) contributor.getContributor()).getHealth(false))
            .map(Health::getStatus)
            .collect(Collectors.toSet());
    statuses.addAll(getDiscoveryStatuses());

    return this.statusAggregator.getAggregateStatus(statuses);
  }

  private Set<Status> getDiscoveryStatuses() {
    NamedContributor<HealthContributor> discoveryComposite = contributors.get("discoveryComposite");

    if (discoveryComposite != null) {
      return ((DiscoveryCompositeHealthContributor) discoveryComposite.getContributor())
          .getIndicators().values().stream()
              .map(i -> i.health().getStatus())
              .collect(Collectors.toSet());
    }

    return Collections.emptySet();
  }
}
