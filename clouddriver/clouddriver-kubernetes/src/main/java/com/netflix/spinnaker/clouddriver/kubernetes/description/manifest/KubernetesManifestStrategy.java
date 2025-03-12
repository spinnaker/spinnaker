/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;

@Value
@NonnullByDefault
public final class KubernetesManifestStrategy {
  private static final String STRATEGY_ANNOTATION_PREFIX =
      "strategy." + KubernetesManifestAnnotater.SPINNAKER_ANNOTATION;
  private static final String VERSIONED = STRATEGY_ANNOTATION_PREFIX + "/versioned";
  static final String MAX_VERSION_HISTORY = STRATEGY_ANNOTATION_PREFIX + "/max-version-history";
  private static final String USE_SOURCE_CAPACITY =
      STRATEGY_ANNOTATION_PREFIX + "/use-source-capacity";

  private static final String SERVER_SIDE_APPLY_STRATEGY =
      STRATEGY_ANNOTATION_PREFIX + "/server-side-apply";
  private static final String SERVER_SIDE_APPLY_FORCE_CONFLICTS = "force-conflicts";

  private final DeployStrategy deployStrategy;
  private final Versioned versioned;
  private final OptionalInt maxVersionHistory;
  private final boolean useSourceCapacity;
  private final ServerSideApplyStrategy serverSideApplyStrategy;

  @Builder
  @ParametersAreNullableByDefault
  private KubernetesManifestStrategy(
      DeployStrategy deployStrategy,
      Versioned versioned,
      Integer maxVersionHistory,
      boolean useSourceCapacity,
      ServerSideApplyStrategy serverSideApplyStrategy) {
    this.deployStrategy = Optional.ofNullable(deployStrategy).orElse(DeployStrategy.APPLY);
    this.versioned = Optional.ofNullable(versioned).orElse(Versioned.DEFAULT);
    this.maxVersionHistory =
        maxVersionHistory == null ? OptionalInt.empty() : OptionalInt.of(maxVersionHistory);
    this.useSourceCapacity = useSourceCapacity;
    this.serverSideApplyStrategy =
        Optional.ofNullable(serverSideApplyStrategy).orElse(ServerSideApplyStrategy.DEFAULT);
  }

  static KubernetesManifestStrategy fromAnnotations(Map<String, String> annotations) {
    return KubernetesManifestStrategy.builder()
        .versioned(Versioned.fromAnnotations(annotations))
        .deployStrategy(DeployStrategy.fromAnnotations(annotations))
        .serverSideApplyStrategy(ServerSideApplyStrategy.fromAnnotations(annotations))
        .useSourceCapacity(Boolean.parseBoolean(annotations.get(USE_SOURCE_CAPACITY)))
        .maxVersionHistory(Ints.tryParse(annotations.getOrDefault(MAX_VERSION_HISTORY, "")))
        .build();
  }

  ImmutableMap<String, String> toAnnotations() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    builder.putAll(deployStrategy.toAnnotations());
    builder.putAll(versioned.toAnnotations());
    builder.putAll(serverSideApplyStrategy.toAnnotations());
    if (maxVersionHistory.isPresent()) {
      builder.put(MAX_VERSION_HISTORY, Integer.toString(maxVersionHistory.getAsInt()));
    }
    if (useSourceCapacity) {
      builder.put(USE_SOURCE_CAPACITY, Boolean.TRUE.toString());
    }
    return builder.build();
  }

  public enum Versioned {
    TRUE(ImmutableMap.of(VERSIONED, Boolean.TRUE.toString())),
    FALSE(ImmutableMap.of(VERSIONED, Boolean.FALSE.toString())),
    DEFAULT(ImmutableMap.of());

    private final ImmutableMap<String, String> annotations;

    Versioned(ImmutableMap<String, String> annotations) {
      this.annotations = annotations;
    }

    static Versioned fromAnnotations(Map<String, String> annotations) {
      if (annotations.containsKey(VERSIONED)) {
        return Boolean.parseBoolean(annotations.get(VERSIONED)) ? TRUE : FALSE;
      }
      return DEFAULT;
    }

    ImmutableMap<String, String> toAnnotations() {
      return annotations;
    }
  }

  public enum DeployStrategy {
    APPLY(null),
    RECREATE(STRATEGY_ANNOTATION_PREFIX + "/recreate"),
    REPLACE(STRATEGY_ANNOTATION_PREFIX + "/replace"),
    SERVER_SIDE_APPLY(SERVER_SIDE_APPLY_STRATEGY);

    @Nullable private final String annotation;

    DeployStrategy(@Nullable String annotation) {
      this.annotation = annotation;
    }

    static DeployStrategy fromAnnotations(Map<String, String> annotations) {
      if (Boolean.parseBoolean(annotations.get(RECREATE.annotation))) {
        return RECREATE;
      }
      if (Boolean.parseBoolean(annotations.get(REPLACE.annotation))) {
        return REPLACE;
      }
      if (annotations.containsKey(SERVER_SIDE_APPLY.annotation)
          && ServerSideApplyStrategy.fromAnnotations(annotations)
              != ServerSideApplyStrategy.DISABLED) {
        return SERVER_SIDE_APPLY;
      }
      return APPLY;
    }

    ImmutableMap<String, String> toAnnotations() {
      if (annotation == null) {
        return ImmutableMap.of();
      }
      return ImmutableMap.of(annotation, Boolean.TRUE.toString());
    }

    void setAnnotations(Map<String, String> annotations) {
      // First clear out any existing deploy strategy annotations, then apply the one appropriate to
      // the current strategy
      Arrays.stream(DeployStrategy.values())
          .map(s -> s.annotation)
          .filter(Objects::nonNull)
          .forEach(annotations::remove);
      annotations.putAll(toAnnotations());
    }
  }

  public enum ServerSideApplyStrategy {
    FORCE_CONFLICTS(ImmutableMap.of(SERVER_SIDE_APPLY_STRATEGY, SERVER_SIDE_APPLY_FORCE_CONFLICTS)),
    DISABLED(ImmutableMap.of(SERVER_SIDE_APPLY_STRATEGY, Boolean.FALSE.toString())),
    DEFAULT(ImmutableMap.of());
    private final ImmutableMap<String, String> annotations;

    ServerSideApplyStrategy(ImmutableMap<String, String> annotations) {
      this.annotations = annotations;
    }

    static ServerSideApplyStrategy fromAnnotations(Map<String, String> annotations) {
      if (annotations.containsKey(SERVER_SIDE_APPLY_STRATEGY)) {
        String strategy = annotations.get(SERVER_SIDE_APPLY_STRATEGY);
        if (Boolean.parseBoolean(strategy)) {
          return DEFAULT;
        }

        if (strategy.equals(SERVER_SIDE_APPLY_FORCE_CONFLICTS)) {
          return FORCE_CONFLICTS;
        }
      }
      return DISABLED;
    }

    ImmutableMap<String, String> toAnnotations() {
      return annotations;
    }
  }
}
