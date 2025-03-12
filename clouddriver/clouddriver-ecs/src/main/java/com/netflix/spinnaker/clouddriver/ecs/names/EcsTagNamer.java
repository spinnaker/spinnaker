/*
 * Copyright 2020 Expedia, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.names;

import com.amazonaws.services.ecs.model.Tag;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class EcsTagNamer implements NamingStrategy<EcsResource> {

  // Borrow naming convention from KubernetesManifestLabeler
  private static final String SPINNAKER_ANNOTATION = "spinnaker.io";
  private static final String MONIKER_ANNOTATION_PREFIX = "moniker." + SPINNAKER_ANNOTATION;
  public static final String CLUSTER = MONIKER_ANNOTATION_PREFIX + "/cluster";
  public static final String APPLICATION = MONIKER_ANNOTATION_PREFIX + "/application";
  public static final String STACK = MONIKER_ANNOTATION_PREFIX + "/stack";
  public static final String DETAIL = MONIKER_ANNOTATION_PREFIX + "/detail";
  public static final String SEQUENCE = MONIKER_ANNOTATION_PREFIX + "/sequence";

  @Override
  public String getName() {
    return "tags";
  }

  @Override
  public void applyMoniker(EcsResource resource, Moniker moniker) {
    applyTags(resource, moniker);
  }

  @Override
  public Moniker deriveMoniker(EcsResource resource) {
    return getMoniker(resource);
  }

  private static void applyTags(EcsResource resource, Moniker moniker) {
    Map<String, String> tags = new TagMap(resource.getResourceTags());
    setIfPresent(value -> tags.putIfAbsent(APPLICATION, value), moniker.getApp());
    setIfPresent(value -> tags.putIfAbsent(CLUSTER, value), moniker.getCluster());
    setIfPresent(value -> tags.putIfAbsent(DETAIL, value), moniker.getDetail());
    setIfPresent(value -> tags.putIfAbsent(STACK, value), moniker.getStack());
    setIfPresent(
        value -> tags.put(SEQUENCE, value),
        moniker.getSequence() != null
            ? moniker.getSequence().toString()
            : null); // Always overwrite sequence
  }

  private static Moniker getMoniker(EcsResource resource) {
    String name = resource.getName();
    Names parsed = Names.parseName(name);

    Moniker moniker =
        Moniker.builder()
            .app(parsed.getApp())
            .cluster(parsed.getCluster())
            .detail(parsed.getDetail())
            .stack(parsed.getStack())
            .sequence(parsed.getSequence())
            .build();

    Map<String, String> tags = new TagMap(resource.getResourceTags());
    if (moniker.getApp() != null && tags != null) {
      setIfPresent(moniker::setApp, tags.get(APPLICATION));
      String cluster = tags.get(CLUSTER);
      String stack = tags.get(STACK);
      String detail = tags.get(DETAIL);
      String sequence = tags.get(SEQUENCE);
      if (cluster == null && (detail != null || stack != null)) {
        // If detail or stack is set and not cluster, we generate the cluster name using frigga
        // convention (app-stack-detail)
        cluster = MonikerHelper.getClusterName(moniker.getApp(), stack, detail);
      }
      setIfPresent(moniker::setStack, stack);
      setIfPresent(moniker::setDetail, detail);
      setIfPresent(moniker::setCluster, cluster);
      setIfPresent(moniker::setSequence, sequence != null ? Integer.parseInt(sequence) : null);
    }

    return moniker;
  }

  private static <T> void setIfPresent(Consumer<T> setter, T value) {
    if (value != null) {
      setter.accept(value);
    }
  }

  private static class TagMap extends AbstractMap<String, String> {

    private final List<Tag> tags;

    private TagMap(final List<Tag> tags) {
      this.tags = tags;
    }

    @NotNull
    @Override
    public Set<Entry<String, String>> entrySet() {
      return tags.stream().collect(Collectors.toMap(Tag::getKey, Tag::getValue)).entrySet();
    }

    @Override
    public String put(String key, String value) {
      String prev = remove(key);
      tags.add(new Tag().withKey(key).withValue(value));
      return prev;
    }
  }
}
