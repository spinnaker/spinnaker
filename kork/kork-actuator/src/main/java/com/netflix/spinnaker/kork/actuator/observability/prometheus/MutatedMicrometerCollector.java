/*
 * Copyright 2025 Netflix, Inc.
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

/*
 * This file uses the source code from https://github.com/micrometer-metrics/micrometer/pull/2653
 * imported in the 1.3.5 micrometer-core lib licensed under the Apache 2.0 license.
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.actuator.observability.prometheus;

import static java.util.stream.Collectors.toList;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.prometheus.PrometheusConfig;
import io.prometheus.client.Collector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** @author Jon Schneider */
class MutatedMicrometerCollector extends Collector {

  static final class TagsHolder {
    final List<String> keys;
    final List<String> values;

    private TagsHolder(List<String> keys, List<String> values) {
      this.keys = keys;
      this.values = values;
    }

    static TagsHolder from(List<Tag> tags) {
      Objects.requireNonNull(tags, "tags");

      List<String> keys = new ArrayList<>(tags.size());
      List<String> values = new ArrayList<>(tags.size());

      for (Tag tag : tags) {
        keys.add(
            NamingConvention.snakeCase.tagKey(tag.getKey()).replaceAll("-", "_")); // REMOVE IN 2.7!
        values.add(tag.getValue());
      }

      return new TagsHolder(
          Collections.unmodifiableList(keys), Collections.unmodifiableList(values));
    }

    public List<String> getKeys() {
      return keys;
    }

    public List<String> getValues() {
      return values;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TagsHolder that = (TagsHolder) o;
      return keys.equals(that.keys) && values.equals(that.values);
    }

    @Override
    public int hashCode() {
      return Objects.hash(keys, values);
    }
  }

  private final Meter.Id id;
  private final Map<TagsHolder, Child> children = new ConcurrentHashMap<>();
  private final String conventionName;
  private final String help;

  public MutatedMicrometerCollector(
      Meter.Id id, NamingConvention convention, PrometheusConfig config) {
    this.id = id;
    this.conventionName = id.getConventionName(convention);
    this.help = config.descriptions() ? Optional.ofNullable(id.getDescription()).orElse(" ") : " ";
  }

  public void add(List<Tag> tags, Child child) {
    children.put(TagsHolder.from(tags), child);
  }

  public void remove(List<Tag> tags) {
    children.remove(TagsHolder.from(tags));
  }

  public boolean isEmpty() {
    return children.isEmpty();
  }

  @Override
  public List<MetricFamilySamples> collect() {
    Map<String, Family> families = new HashMap<>();

    for (Map.Entry<TagsHolder, Child> e : children.entrySet()) {
      TagsHolder tags = e.getKey();
      Child child = e.getValue();

      child
          .samples(conventionName, tags)
          .forEach(
              family -> {
                families.compute(
                    family.getConventionName(),
                    (name, matchingFamily) ->
                        matchingFamily != null
                            ? matchingFamily.addSamples(family.samples)
                            : family);
              });
    }

    return families.values().stream()
        .map(
            family ->
                new MetricFamilySamples(family.conventionName, family.type, help, family.samples))
        .collect(toList());
  }

  interface Child {
    Stream<Family> samples(String conventionName, TagsHolder tags);
  }

  static class Family {
    final Type type;
    final String conventionName;
    final List<MetricFamilySamples.Sample> samples = new ArrayList<>();

    Family(Type type, String conventionName, MetricFamilySamples.Sample... samples) {
      this.type = type;
      this.conventionName = conventionName;
      Collections.addAll(this.samples, samples);
    }

    Family(Type type, String conventionName, Stream<MetricFamilySamples.Sample> samples) {
      this.type = type;
      this.conventionName = conventionName;
      samples.forEach(this.samples::add);
    }

    String getConventionName() {
      return conventionName;
    }

    Family addSamples(Collection<MetricFamilySamples.Sample> samples) {
      this.samples.addAll(samples);
      return this;
    }
  }
}
