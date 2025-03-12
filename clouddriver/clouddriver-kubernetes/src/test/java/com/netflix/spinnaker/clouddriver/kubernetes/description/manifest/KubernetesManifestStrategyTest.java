/*
 * Copyright 2020 Google, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy.DeployStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy.ServerSideApplyStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy.Versioned;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class KubernetesManifestStrategyTest {
  @Test
  void deployStrategyDefaultsToApply() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(ImmutableMap.of());
    assertThat(strategy).isEqualTo(DeployStrategy.APPLY);
  }

  @Test
  void otherStrategiesFalse() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of(
                "strategy.spinnaker.io/recreate", "false",
                "strategy.spinnaker.io/replace", "false"));
    assertThat(strategy).isEqualTo(DeployStrategy.APPLY);
  }

  @Test
  void recreateStrategy() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/recreate", "true"));
    assertThat(strategy).isEqualTo(DeployStrategy.RECREATE);
  }

  @Test
  void replaceStrategy() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/replace", "true"));
    assertThat(strategy).isEqualTo(DeployStrategy.REPLACE);
  }

  @Test
  void deployStrategysSrverSideApplyForce() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "force-conflicts"));
    assertThat(strategy).isEqualTo(DeployStrategy.SERVER_SIDE_APPLY);
  }

  @Test
  void deployStrategyServerSideApplyDefault() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "true"));
    assertThat(strategy).isEqualTo(DeployStrategy.SERVER_SIDE_APPLY);
  }

  @Test
  void deployStrategyServerSideApplyDisabled() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "false"));
    assertThat(strategy).isEqualTo(DeployStrategy.APPLY);
  }

  @Test
  void serverSideApplyStrategyForceConflict() {
    KubernetesManifestStrategy.ServerSideApplyStrategy conflictResolution =
        KubernetesManifestStrategy.ServerSideApplyStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "force-conflicts"));
    assertThat(conflictResolution)
        .isEqualTo(KubernetesManifestStrategy.ServerSideApplyStrategy.FORCE_CONFLICTS);
  }

  @Test
  void serverSideApplyStrategyDefault() {
    KubernetesManifestStrategy.ServerSideApplyStrategy conflictResolution =
        KubernetesManifestStrategy.ServerSideApplyStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "true"));
    assertThat(conflictResolution).isEqualTo(ServerSideApplyStrategy.DEFAULT);
  }

  @Test
  void serverSideApplyStrategyDisabled() {
    KubernetesManifestStrategy.ServerSideApplyStrategy conflictResolution =
        KubernetesManifestStrategy.ServerSideApplyStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "false"));
    assertThat(conflictResolution).isEqualTo(ServerSideApplyStrategy.DISABLED);
  }

  @Test
  void serverSideApplyStrategyInvalidValue() {
    KubernetesManifestStrategy.ServerSideApplyStrategy conflictResolution =
        KubernetesManifestStrategy.ServerSideApplyStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "zzzz"));
    assertThat(conflictResolution).isEqualTo(ServerSideApplyStrategy.DISABLED);
  }

  @Test
  void nonBooleanValue() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/replace", "zzzz"));
    assertThat(strategy).isEqualTo(DeployStrategy.APPLY);
  }

  @Test
  void recreatePreferredOverReplace() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of(
                "strategy.spinnaker.io/replace", "true",
                "strategy.spinnaker.io/recreate", "true"));
    assertThat(strategy).isEqualTo(DeployStrategy.RECREATE);
  }

  @Test
  void replacePreferredOverServerSideApply() {
    KubernetesManifestStrategy.DeployStrategy strategy =
        KubernetesManifestStrategy.DeployStrategy.fromAnnotations(
            ImmutableMap.of(
                "strategy.spinnaker.io/replace", "true",
                "strategy.spinnaker.io/server-side-apply", "true"));
    assertThat(strategy).isEqualTo(DeployStrategy.REPLACE);
  }

  @Test
  void applyToAnnotations() {
    Map<String, String> annotations = DeployStrategy.APPLY.toAnnotations();
    assertThat(annotations).isEmpty();
  }

  @Test
  void recreateToAnnotations() {
    Map<String, String> annotations = DeployStrategy.RECREATE.toAnnotations();
    assertThat(annotations).containsOnly(entry("strategy.spinnaker.io/recreate", "true"));
  }

  @Test
  void replaceToAnnotations() {
    Map<String, String> annotations = DeployStrategy.REPLACE.toAnnotations();
    assertThat(annotations).containsOnly(entry("strategy.spinnaker.io/replace", "true"));
  }

  @Test
  void versionedDefaultsToDefault() {
    KubernetesManifestStrategy.Versioned versioned =
        KubernetesManifestStrategy.Versioned.fromAnnotations(ImmutableMap.of());
    assertThat(versioned).isEqualTo(Versioned.DEFAULT);
  }

  @Test
  void versionedTrue() {
    KubernetesManifestStrategy.Versioned versioned =
        KubernetesManifestStrategy.Versioned.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/versioned", "true"));
    assertThat(versioned).isEqualTo(Versioned.TRUE);
  }

  @Test
  void versionedFalse() {
    KubernetesManifestStrategy.Versioned versioned =
        KubernetesManifestStrategy.Versioned.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/versioned", "false"));
    assertThat(versioned).isEqualTo(Versioned.FALSE);
  }

  @Test
  void versionedNonsense() {
    KubernetesManifestStrategy.Versioned versioned =
        KubernetesManifestStrategy.Versioned.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/versioned", "zzz"));
    assertThat(versioned).isEqualTo(Versioned.FALSE);
  }

  @Test
  void versionedDefaultToAnnotations() {
    Map<String, String> annotations = Versioned.DEFAULT.toAnnotations();
    assertThat(annotations).isEmpty();
  }

  @Test
  void versionedTrueToAnnotations() {
    Map<String, String> annotations = Versioned.TRUE.toAnnotations();
    assertThat(annotations).containsOnly(entry("strategy.spinnaker.io/versioned", "true"));
  }

  @Test
  void versionedFalseToAnnotations() {
    Map<String, String> annotations = Versioned.FALSE.toAnnotations();
    assertThat(annotations).containsOnly(entry("strategy.spinnaker.io/versioned", "false"));
  }

  @Test
  void fromEmptyAnnotations() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(ImmutableMap.of());
    assertThat(strategy.getDeployStrategy()).isEqualTo(DeployStrategy.APPLY);
    assertThat(strategy.getVersioned()).isEqualTo(Versioned.DEFAULT);
    assertThat(strategy.getMaxVersionHistory()).isEqualTo(OptionalInt.empty());
    assertThat(strategy.isUseSourceCapacity()).isFalse();
  }

  @Test
  void fromDeployStrategyAnnotation() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/replace", "true"));
    assertThat(strategy.getDeployStrategy()).isEqualTo(DeployStrategy.REPLACE);
  }

  @Test
  void fromVersionedAnnotation() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/versioned", "true"));
    assertThat(strategy.getVersioned()).isEqualTo(Versioned.TRUE);
  }

  @Test
  void fromMaxVersionHistoryAnnotation() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/max-version-history", "10"));
    assertThat(strategy.getMaxVersionHistory()).isEqualTo(OptionalInt.of(10));
  }

  @Test
  void fromNonIntegerMaxVersionHistoryAnnotation() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/max-version-history", "zz"));
    assertThat(strategy.getMaxVersionHistory()).isEqualTo(OptionalInt.empty());
  }

  @Test
  void fromUseSourceCapacityAnnotation() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/use-source-capacity", "true"));
    assertThat(strategy.isUseSourceCapacity()).isTrue();
  }

  @Test
  void fromUseSourceCapacityAnnotationFalse() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/use-source-capacity", "false"));
    assertThat(strategy.isUseSourceCapacity()).isFalse();
  }

  @Test
  void fromUseSourceCapacityAnnotationNonsense() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(
            ImmutableMap.of("strategy.spinnaker.io/use-source-capacity", "zzz"));
    assertThat(strategy.isUseSourceCapacity()).isFalse();
  }

  @Test
  void allAnnotationsPresent() {
    KubernetesManifestStrategy strategy =
        KubernetesManifestStrategy.fromAnnotations(
            ImmutableMap.of(
                "strategy.spinnaker.io/replace", "true",
                "strategy.spinnaker.io/versioned", "true",
                "strategy.spinnaker.io/max-version-history", "20",
                "strategy.spinnaker.io/use-source-capacity", "true",
                "strategy.spinnaker.io/random-annotation", "abc"));

    assertThat(strategy.getDeployStrategy()).isEqualTo(DeployStrategy.REPLACE);
    assertThat(strategy.getVersioned()).isEqualTo(Versioned.TRUE);
    assertThat(strategy.getMaxVersionHistory()).isEqualTo(OptionalInt.of(20));
    assertThat(strategy.isUseSourceCapacity()).isTrue();
  }

  @Test
  void builderDefaults() {
    KubernetesManifestStrategy strategy = KubernetesManifestStrategy.builder().build();
    assertThat(strategy.getDeployStrategy()).isEqualTo(DeployStrategy.APPLY);
    assertThat(strategy.getVersioned()).isEqualTo(Versioned.DEFAULT);
    assertThat(strategy.getMaxVersionHistory()).isEqualTo(OptionalInt.empty());
    assertThat(strategy.isUseSourceCapacity()).isFalse();
  }

  @Test
  void emptyAnnotations() {
    Map<String, String> annotations = KubernetesManifestStrategy.builder().build().toAnnotations();
    assertThat(annotations).isEmpty();
  }

  @Test
  void deployStrategyRecreateToAnnotations() {
    Map<String, String> annotations =
        KubernetesManifestStrategy.builder()
            .deployStrategy(DeployStrategy.RECREATE)
            .build()
            .toAnnotations();
    assertThat(annotations).containsOnly(entry("strategy.spinnaker.io/recreate", "true"));
  }

  @Test
  void deployStrategyReplaceToAnnotations() {
    Map<String, String> annotations =
        KubernetesManifestStrategy.builder()
            .deployStrategy(DeployStrategy.REPLACE)
            .build()
            .toAnnotations();
    assertThat(annotations).containsOnly(entry("strategy.spinnaker.io/replace", "true"));
  }

  @Test
  void versionedToAnnotations() {
    Map<String, String> annotations =
        KubernetesManifestStrategy.builder().versioned(Versioned.FALSE).build().toAnnotations();
    assertThat(annotations).containsOnly(entry("strategy.spinnaker.io/versioned", "false"));
  }

  @Test
  void maxVersionHistoryToAnnotations() {
    Map<String, String> annotations =
        KubernetesManifestStrategy.builder().maxVersionHistory(10).build().toAnnotations();
    assertThat(annotations).containsOnly(entry("strategy.spinnaker.io/max-version-history", "10"));
  }

  @Test
  void useSourceCapacityToAnnotations() {
    Map<String, String> annotations =
        KubernetesManifestStrategy.builder().useSourceCapacity(true).build().toAnnotations();
    assertThat(annotations)
        .containsOnly(entry("strategy.spinnaker.io/use-source-capacity", "true"));
  }

  @ParameterizedTest
  @EnumSource(DeployStrategy.class)
  void deploymentStrategySetsAnnotations(DeployStrategy deployStrategy) {
    Map<String, String> annotations = new HashMap<>();
    deployStrategy.setAnnotations(annotations);
    assertThat(annotations).isEqualTo(deployStrategy.toAnnotations());
  }

  @ParameterizedTest
  @EnumSource(DeployStrategy.class)
  void deploymentStrategyOverwritesAnnotations(DeployStrategy deployStrategy) {
    Map<String, String> annotations = new HashMap<>(DeployStrategy.RECREATE.toAnnotations());
    deployStrategy.setAnnotations(annotations);
    assertThat(annotations).isEqualTo(deployStrategy.toAnnotations());
  }

  @ParameterizedTest
  @EnumSource(DeployStrategy.class)
  void deploymentStrategyIgnoresIrrelevantAnnotations(DeployStrategy deployStrategy) {
    ImmutableMap<String, String> irrelevantAnnotations =
        ImmutableMap.of(
            "strategy.spinnaker.io/versioned", "false",
            "artifact.spinnaker.io/version", "v001",
            "my-custom-annotation", "my-custom-value");
    Map<String, String> annotations = new HashMap<>(irrelevantAnnotations);
    deployStrategy.setAnnotations(annotations);
    assertThat(annotations).containsAllEntriesOf(irrelevantAnnotations);
  }

  @Test
  void toAnnotationsMultipleAnnotations() {
    Map<String, String> annotations =
        KubernetesManifestStrategy.builder()
            .deployStrategy(DeployStrategy.RECREATE)
            .versioned(Versioned.TRUE)
            .maxVersionHistory(30)
            .useSourceCapacity(true)
            .build()
            .toAnnotations();
    assertThat(annotations)
        .containsOnly(
            entry("strategy.spinnaker.io/recreate", "true"),
            entry("strategy.spinnaker.io/versioned", "true"),
            entry("strategy.spinnaker.io/max-version-history", "30"),
            entry("strategy.spinnaker.io/use-source-capacity", "true"));
  }
}
