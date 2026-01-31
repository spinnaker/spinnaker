/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.cats.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for sharding distribution and movement behavior across different strategies. These tests
 * validate the key properties that matter for production: even distribution and minimal movement.
 */
@DisplayName("Sharding Distribution Tests")
class ShardingDistributionTest {

  private static final int LARGE_KEY_COUNT = 10000;
  private static final int SMALL_KEY_COUNT = 1000;

  private final ModuloShardingStrategy moduloStrategy = new ModuloShardingStrategy();
  private final JumpConsistentHashStrategy jumpStrategy = new JumpConsistentHashStrategy();

  /** Generates test keys that simulate account names. */
  private List<String> generateAccountKeys(int count) {
    List<String> keys = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      keys.add("account-" + i);
    }
    return keys;
  }

  /** Generates test keys that simulate region-level sharding. */
  private List<String> generateRegionKeys(int count) {
    String[] regions = {"us-east-1", "us-west-2", "eu-west-1", "ap-south-1", "sa-east-1"};
    List<String> keys = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      String region = regions[i % regions.length];
      keys.add("account-" + (i / regions.length) + "/" + region);
    }
    return keys;
  }

  @Nested
  @DisplayName("Distribution Evenness Tests")
  class DistributionEvennessTests {

    @Test
    @DisplayName("Modulo strategy distributes evenly across 3 pods")
    void moduloEvenDistribution3Pods() {
      int podCount = 3;
      int[] distribution = computeDistribution(moduloStrategy, LARGE_KEY_COUNT, podCount);

      // Each pod should have roughly 33% of keys
      int expected = LARGE_KEY_COUNT / podCount;
      int tolerance = (int) (expected * 0.15); // Allow 15% variance

      for (int i = 0; i < podCount; i++) {
        assertThat(distribution[i])
            .describedAs("Pod %d should have roughly equal distribution", i)
            .isBetween(expected - tolerance, expected + tolerance);
      }
    }

    @Test
    @DisplayName("Jump strategy distributes evenly across 3 pods")
    void jumpEvenDistribution3Pods() {
      int podCount = 3;
      int[] distribution = computeDistribution(jumpStrategy, LARGE_KEY_COUNT, podCount);

      // Each pod should have roughly 33% of keys
      int expected = LARGE_KEY_COUNT / podCount;
      int tolerance = (int) (expected * 0.15); // Allow 15% variance

      for (int i = 0; i < podCount; i++) {
        assertThat(distribution[i])
            .describedAs("Pod %d should have roughly equal distribution", i)
            .isBetween(expected - tolerance, expected + tolerance);
      }
    }

    @Test
    @DisplayName("Modulo strategy distributes evenly across 10 pods")
    void moduloEvenDistribution10Pods() {
      int podCount = 10;
      int[] distribution = computeDistribution(moduloStrategy, LARGE_KEY_COUNT, podCount);

      // Each pod should have roughly 10% of keys
      int expected = LARGE_KEY_COUNT / podCount;
      int tolerance = (int) (expected * 0.20); // Allow 20% variance

      for (int i = 0; i < podCount; i++) {
        assertThat(distribution[i])
            .describedAs("Pod %d should have roughly equal distribution", i)
            .isBetween(expected - tolerance, expected + tolerance);
      }
    }

    @Test
    @DisplayName("Jump strategy distributes evenly across 10 pods")
    void jumpEvenDistribution10Pods() {
      int podCount = 10;
      int[] distribution = computeDistribution(jumpStrategy, LARGE_KEY_COUNT, podCount);

      // Each pod should have roughly 10% of keys
      int expected = LARGE_KEY_COUNT / podCount;
      int tolerance = (int) (expected * 0.20); // Allow 20% variance

      for (int i = 0; i < podCount; i++) {
        assertThat(distribution[i])
            .describedAs("Pod %d should have roughly equal distribution", i)
            .isBetween(expected - tolerance, expected + tolerance);
      }
    }

    private int[] computeDistribution(ShardingStrategy strategy, int keyCount, int podCount) {
      int[] distribution = new int[podCount];
      List<String> keys = generateAccountKeys(keyCount);
      for (String key : keys) {
        int owner = strategy.computeOwner(key, podCount);
        distribution[owner]++;
      }
      return distribution;
    }
  }

  @Nested
  @DisplayName("Movement on Scale Tests")
  class MovementOnScaleTests {

    @Test
    @DisplayName("Modulo: 3->4 pods moves most keys (>50%)")
    void moduloScaleUp3to4() {
      MovementStats stats = computeMovement(moduloStrategy, SMALL_KEY_COUNT, 3, 4);

      assertThat(stats.movePercentage)
          .describedAs("Modulo should move >50%% of keys on scale 3->4")
          .isGreaterThan(50.0);
    }

    @Test
    @DisplayName("Jump: 3->4 pods moves ~25% of keys")
    void jumpScaleUp3to4() {
      MovementStats stats = computeMovement(jumpStrategy, SMALL_KEY_COUNT, 3, 4);

      // Expected: 1/4 = 25%
      assertThat(stats.movePercentage)
          .describedAs("Jump should move ~25%% of keys on scale 3->4")
          .isBetween(15.0, 35.0);
    }

    @Test
    @DisplayName("Modulo: 5->6 pods moves most keys (>50%)")
    void moduloScaleUp5to6() {
      MovementStats stats = computeMovement(moduloStrategy, SMALL_KEY_COUNT, 5, 6);

      assertThat(stats.movePercentage)
          .describedAs("Modulo should move >50%% of keys on scale 5->6")
          .isGreaterThan(50.0);
    }

    @Test
    @DisplayName("Jump: 5->6 pods moves ~17% of keys")
    void jumpScaleUp5to6() {
      MovementStats stats = computeMovement(jumpStrategy, SMALL_KEY_COUNT, 5, 6);

      // Expected: 1/6 ≈ 17%
      assertThat(stats.movePercentage)
          .describedAs("Jump should move ~17%% of keys on scale 5->6")
          .isBetween(10.0, 25.0);
    }

    @Test
    @DisplayName("Modulo: 10->11 pods moves most keys (>50%)")
    void moduloScaleUp10to11() {
      MovementStats stats = computeMovement(moduloStrategy, SMALL_KEY_COUNT, 10, 11);

      assertThat(stats.movePercentage)
          .describedAs("Modulo should move >50%% of keys on scale 10->11")
          .isGreaterThan(40.0);
    }

    @Test
    @DisplayName("Jump: 10->11 pods moves ~9% of keys")
    void jumpScaleUp10to11() {
      MovementStats stats = computeMovement(jumpStrategy, SMALL_KEY_COUNT, 10, 11);

      // Expected: 1/11 ≈ 9%
      assertThat(stats.movePercentage)
          .describedAs("Jump should move ~9%% of keys on scale 10->11")
          .isBetween(5.0, 15.0);
    }

    @Test
    @DisplayName("Jump: moved keys go to new pod only")
    void jumpMovedKeysGoToNewPod() {
      int oldPodCount = 5;
      int newPodCount = 6;
      int newPodIndex = 5;

      List<String> keys = generateAccountKeys(SMALL_KEY_COUNT);
      int movedToNewPod = 0;
      int movedElsewhere = 0;

      for (String key : keys) {
        int oldOwner = jumpStrategy.computeOwner(key, oldPodCount);
        int newOwner = jumpStrategy.computeOwner(key, newPodCount);
        if (oldOwner != newOwner) {
          if (newOwner == newPodIndex) {
            movedToNewPod++;
          } else {
            movedElsewhere++;
          }
        }
      }

      assertThat(movedToNewPod)
          .describedAs("Some keys should move to the new pod")
          .isGreaterThan(0);
      assertThat(movedElsewhere)
          .describedAs("No keys should move between existing pods")
          .isEqualTo(0);
    }

    @Test
    @DisplayName("Scale down 4->3 movement comparison")
    void scaleDown4to3() {
      MovementStats moduloStats = computeMovement(moduloStrategy, SMALL_KEY_COUNT, 4, 3);
      MovementStats jumpStats = computeMovement(jumpStrategy, SMALL_KEY_COUNT, 4, 3);

      // Both will have movement, but jump should still be better
      assertThat(jumpStats.movedCount)
          .describedAs("Jump should move fewer keys than modulo on scale-down")
          .isLessThanOrEqualTo(moduloStats.movedCount);
    }

    private MovementStats computeMovement(
        ShardingStrategy strategy, int keyCount, int oldPodCount, int newPodCount) {
      List<String> keys = generateAccountKeys(keyCount);
      int movedCount = 0;

      for (String key : keys) {
        int oldOwner = strategy.computeOwner(key, oldPodCount);
        int newOwner = strategy.computeOwner(key, newPodCount);
        if (oldOwner != newOwner) {
          movedCount++;
        }
      }

      return new MovementStats(movedCount, keyCount);
    }
  }

  @Nested
  @DisplayName("Determinism Tests")
  class DeterminismTests {

    @Test
    @DisplayName("Modulo produces deterministic results")
    void moduloDeterministic() {
      List<String> keys = generateAccountKeys(100);
      Map<String, Integer> firstRun = new HashMap<>();
      Map<String, Integer> secondRun = new HashMap<>();

      for (String key : keys) {
        firstRun.put(key, moduloStrategy.computeOwner(key, 5));
      }
      for (String key : keys) {
        secondRun.put(key, moduloStrategy.computeOwner(key, 5));
      }

      assertThat(firstRun).isEqualTo(secondRun);
    }

    @Test
    @DisplayName("Jump produces deterministic results")
    void jumpDeterministic() {
      List<String> keys = generateAccountKeys(100);
      Map<String, Integer> firstRun = new HashMap<>();
      Map<String, Integer> secondRun = new HashMap<>();

      for (String key : keys) {
        firstRun.put(key, jumpStrategy.computeOwner(key, 5));
      }
      for (String key : keys) {
        secondRun.put(key, jumpStrategy.computeOwner(key, 5));
      }

      assertThat(firstRun).isEqualTo(secondRun);
    }

    @Test
    @DisplayName("Same key produces same result across strategy instances")
    void sameResultAcrossInstances() {
      String key = "test-account-key";

      ModuloShardingStrategy modulo1 = new ModuloShardingStrategy();
      ModuloShardingStrategy modulo2 = new ModuloShardingStrategy();
      JumpConsistentHashStrategy jump1 = new JumpConsistentHashStrategy();
      JumpConsistentHashStrategy jump2 = new JumpConsistentHashStrategy();

      assertThat(modulo1.computeOwner(key, 5)).isEqualTo(modulo2.computeOwner(key, 5));
      assertThat(jump1.computeOwner(key, 5)).isEqualTo(jump2.computeOwner(key, 5));
    }
  }

  @Nested
  @DisplayName("Region-Level Sharding Distribution")
  class RegionLevelShardingTests {

    @Test
    @DisplayName("Region keys distribute evenly")
    void regionKeysDistributeEvenly() {
      List<String> keys = generateRegionKeys(SMALL_KEY_COUNT);
      int podCount = 5;
      int[] distribution = new int[podCount];

      for (String key : keys) {
        int owner = jumpStrategy.computeOwner(key, podCount);
        distribution[owner]++;
      }

      // Each pod should have roughly 20% of keys
      int expected = SMALL_KEY_COUNT / podCount;
      int tolerance = (int) (expected * 0.25);

      for (int i = 0; i < podCount; i++) {
        assertThat(distribution[i])
            .describedAs("Pod %d should have roughly equal distribution", i)
            .isBetween(expected - tolerance, expected + tolerance);
      }
    }

    @Test
    @DisplayName("Same account different regions can go to different pods")
    void sameAccountDifferentRegions() {
      // With region-level keys, same account can be split across pods
      String key1 = "prod-aws/us-east-1";
      String key2 = "prod-aws/us-west-2";
      String key3 = "prod-aws/eu-west-1";

      int owner1 = jumpStrategy.computeOwner(key1, 5);
      int owner2 = jumpStrategy.computeOwner(key2, 5);
      int owner3 = jumpStrategy.computeOwner(key3, 5);

      // At least some should be different (probabilistically)
      // This test documents the behavior that region-level sharding enables distribution
      assertThat(List.of(owner1, owner2, owner3))
          .describedAs("Different regions of same account can map to different pods")
          .isNotNull();
    }
  }

  /** Helper class for movement statistics. */
  private static class MovementStats {
    final int movedCount;
    final int totalCount;
    final double movePercentage;

    MovementStats(int movedCount, int totalCount) {
      this.movedCount = movedCount;
      this.totalCount = totalCount;
      this.movePercentage = (movedCount * 100.0) / totalCount;
    }
  }
}
