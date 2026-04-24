/*
 * Copyright 2026 Harness, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.names;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.MonikerHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class LambdaTagNamer implements NamingStrategy<LambdaResource> {

  // Borrow naming convention from KubernetesManifestLabeler
  private static final String SPINNAKER_ANNOTATION = "spinnaker.io";
  private static final String MONIKER_ANNOTATION_PREFIX = "moniker." + SPINNAKER_ANNOTATION;
  public static final String CLUSTER = MONIKER_ANNOTATION_PREFIX + "/cluster";
  public static final String APPLICATION = MONIKER_ANNOTATION_PREFIX + "/application";
  public static final String STACK = MONIKER_ANNOTATION_PREFIX + "/stack";
  public static final String DETAIL = MONIKER_ANNOTATION_PREFIX + "/detail";
  public static final String SEQUENCE = MONIKER_ANNOTATION_PREFIX + "/sequence";

  public static void applyIfNeeded(
      LambdaResource description, String applicationName, boolean autoApplyTags) {
    if (autoApplyTags) {
      Moniker moniker = getMoniker(description);
      if (description.getResourceTags() == null) {
        description.setResourceTags(new HashMap<>());
      }
      // Make sure to set the app name REGARDLESS derived value in the case where an app has not
      // previously been set
      if (!description.getResourceTags().containsKey(LambdaTagNamer.APPLICATION)) {
        description.getResourceTags().put(LambdaTagNamer.APPLICATION, applicationName);
      }
      applyTags(description, moniker);
    }
  }

  @Override
  public String getName() {
    return "tags";
  }

  @Override
  public void applyMoniker(LambdaResource resource, Moniker moniker) {
    applyTags(resource, moniker);
  }

  @Override
  public Moniker deriveMoniker(LambdaResource resource) {
    return getMoniker(resource);
  }

  private static void applyTags(LambdaResource resource, Moniker moniker) {
    Map<String, String> tags = resource.getResourceTags();

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

  private static Moniker getMoniker(LambdaResource resource) {
    Map<String, String> tags = resource.getResourceTags();
    Moniker.MonikerBuilder builder = Moniker.builder();

    String name = resource.getName();
    Names parsed = Names.parseName(name);
    // Try to get values from tags first
    String appFromTag = tags.getOrDefault(APPLICATION, null);
    String clusterFromTag = tags.getOrDefault(CLUSTER, null);
    String stackFromTag = tags.getOrDefault(STACK, null);
    String detailFromTag = tags.getOrDefault(DETAIL, null);
    String sequenceFromTag = tags.getOrDefault(SEQUENCE, null);

    // Determine final values - tags take priority
    String app = appFromTag != null ? appFromTag : parsed.getApp();
    String stack = stackFromTag != null ? stackFromTag : parsed.getStack();
    String detail = detailFromTag != null ? detailFromTag : parsed.getDetail();

    // For cluster: use tag value if provided, otherwise regenerate if any tag affected
    // app/stack/detail
    String cluster;
    if (clusterFromTag != null) {
      // Explicit cluster tag takes priority
      cluster = clusterFromTag;
    } else if (appFromTag != null || stackFromTag != null || detailFromTag != null) {
      // If any component came from tags, regenerate cluster with the tag-influenced values
      if (app != null && (detail != null || stack != null)) {
        cluster = MonikerHelper.getClusterName(app, stack, detail);
      } else {
        cluster = app; // Just the app name if no stack/detail
      }
    } else {
      // No tags affected cluster, use parsed cluster
      cluster = parsed.getCluster();
    }

    // For sequence: tag takes priority
    Integer sequence = null;
    if (sequenceFromTag != null) {
      try {
        sequence = Integer.parseInt(sequenceFromTag);
      } catch (NumberFormatException e) {
        // Ignore invalid sequence numbers from tags
      }
    } else if (parsed.getSequence() != null) {
      sequence = parsed.getSequence();
    }

    // Build the moniker with all values
    builder.app(app);
    builder.cluster(cluster);
    builder.stack(stack);
    builder.detail(detail);
    builder.sequence(sequence);

    return builder.build();
  }

  private static <T> void setIfPresent(Consumer<T> setter, T value) {
    if (value != null) {
      setter.accept(value);
    }
  }
}
