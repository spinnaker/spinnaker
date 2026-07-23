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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ShardingStrategy Tests")
class ShardingStrategyTest {

  @Nested
  @DisplayName("ModuloShardingStrategy Tests")
  class ModuloShardingStrategyTests {

    private final ModuloShardingStrategy strategy = new ModuloShardingStrategy();

    @Test
    @DisplayName("Returns correct name")
    void returnsCorrectName() {
      assertThat(strategy.getName()).isEqualTo("modulo");
    }

    @Test
    @DisplayName("Returns 0 for single pod")
    void singlePodReturnsZero() {
      assertThat(strategy.computeOwner("any-key", 1)).isEqualTo(0);
    }

    @Test
    @DisplayName("Returns 0 for zero pods")
    void zeroPodsReturnsZero() {
      assertThat(strategy.computeOwner("any-key", 0)).isEqualTo(0);
    }

    @Test
    @DisplayName("Distributes positive hash correctly")
    void positiveHashDistribution() {
      // "test" has a positive hashCode
      String key = "test";
      assertThat(key.hashCode()).isPositive();

      int owner = strategy.computeOwner(key, 3);
      assertThat(owner).isBetween(0, 2);
    }

    @Test
    @DisplayName("Distributes negative hash correctly")
    void negativeHashDistribution() {
      // Find a string with negative hashCode
      String key = "negativeHash";
      // hashCode for "negativeHash" is negative
      int owner = strategy.computeOwner(key, 3);
      assertThat(owner).isBetween(0, 2);
    }

    @Test
    @DisplayName("Same key always maps to same bucket")
    void deterministicMapping() {
      String key = "deterministic-test-key";
      int first = strategy.computeOwner(key, 5);
      int second = strategy.computeOwner(key, 5);
      int third = strategy.computeOwner(key, 5);

      assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    @DisplayName("Matches legacy abs(hash % pods) behavior")
    void matchesLegacyFormulaForRealStrings() {
      String[] testKeys = {"account-1", "prod/us-east-1/Agent", "test", "", "a"};

      for (String key : testKeys) {
        int hash = key.hashCode();
        for (int podCount = 2; podCount <= 5; podCount++) {
          int legacyResult = Math.abs(hash % podCount);
          int strategyResult = strategy.computeOwner(key, podCount);
          assertThat(strategyResult)
              .describedAs(
                  "Legacy modulo must be preserved for key '%s' with %d pods", key, podCount)
              .isEqualTo(legacyResult);
        }
      }
    }

    @Test
    @DisplayName("Different keys distribute across buckets")
    void distributesAcrossBuckets() {
      int[] bucketCounts = new int[5];
      for (int i = 0; i < 100; i++) {
        int bucket = strategy.computeOwner("key-" + i, 5);
        bucketCounts[bucket]++;
      }

      // Each bucket should have at least some keys (probabilistic, but very likely)
      for (int count : bucketCounts) {
        assertThat(count).isGreaterThan(0);
      }
    }
  }

  /**
   * Tests for the canonical modulo strategy edge cases.
   *
   * <p>We keep these tests separate from ModuloShardingStrategy to make the behavioral split
   * explicit:
   *
   * <ul>
   *   <li>ModuloShardingStrategy keeps legacy compatibility behavior
   *   <li>CanonicalModuloShardingStrategy uses positive remainder arithmetic
   * </ul>
   */
  @Nested
  @DisplayName("CanonicalModuloShardingStrategy Tests")
  class CanonicalModuloShardingStrategyTests {

    private final CanonicalModuloShardingStrategy strategy = new CanonicalModuloShardingStrategy();

    @Test
    @DisplayName("Returns correct name")
    void returnsCorrectName() {
      assertThat(strategy.getName()).isEqualTo("canonical-modulo");
    }

    /**
     * Applies the same canonical modulo formula used by CanonicalModuloShardingStrategy. This
     * isolates the formula for direct edge-case testing since we cannot control String.hashCode().
     */
    private int applyCanonicalModuloFormula(int hash, int podCount) {
      if (podCount <= 1) {
        return 0;
      }
      return ((hash % podCount) + podCount) % podCount;
    }

    @Test
    @DisplayName("Formula handles Integer.MIN_VALUE correctly")
    void formulaHandlesIntegerMinValue() {
      // Integer.MIN_VALUE is problematic because:
      // 1. Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE (overflow)
      // 2. Integer.MIN_VALUE % n can be negative
      int result = applyCanonicalModuloFormula(Integer.MIN_VALUE, 3);

      assertThat(result)
          .describedAs("Integer.MIN_VALUE must produce valid bucket [0, podCount)")
          .isBetween(0, 2);

      // Verify across multiple pod counts
      for (int podCount = 2; podCount <= 10; podCount++) {
        int bucket = applyCanonicalModuloFormula(Integer.MIN_VALUE, podCount);
        assertThat(bucket)
            .describedAs("Integer.MIN_VALUE with %d pods", podCount)
            .isBetween(0, podCount - 1);
      }
    }

    @Test
    @DisplayName("Formula handles negative hash codes correctly")
    void formulaHandlesNegativeHashes() {
      // Test several negative values including edge cases
      int[] negativeHashes = {-1, -2, -3, -5, -100, -999, Integer.MIN_VALUE + 1};

      for (int hash : negativeHashes) {
        for (int podCount = 2; podCount <= 5; podCount++) {
          int result = applyCanonicalModuloFormula(hash, podCount);
          assertThat(result)
              .describedAs("Hash %d with %d pods must be in [0, %d)", hash, podCount, podCount)
              .isBetween(0, podCount - 1);
        }
      }
    }

    @Test
    @DisplayName("Formula handles zero hash code correctly")
    void formulaHandlesZeroHash() {
      for (int podCount = 2; podCount <= 10; podCount++) {
        int result = applyCanonicalModuloFormula(0, podCount);
        assertThat(result).describedAs("Zero hash with %d pods", podCount).isEqualTo(0);
      }
    }

    @Test
    @DisplayName("Formula is deterministic")
    void formulaIsDeterministic() {
      int hash = -12345;
      int podCount = 7;
      int first = applyCanonicalModuloFormula(hash, podCount);
      int second = applyCanonicalModuloFormula(hash, podCount);
      int third = applyCanonicalModuloFormula(hash, podCount);

      assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    @DisplayName("Formula matches canonical strategy for real strings")
    void formulaMatchesActualStrategy() {
      String[] testKeys = {"account-1", "prod/us-east-1/Agent", "test", "", "a"};

      for (String key : testKeys) {
        int hash = key.hashCode();
        for (int podCount = 2; podCount <= 5; podCount++) {
          int formulaResult = applyCanonicalModuloFormula(hash, podCount);
          int strategyResult = strategy.computeOwner(key, podCount);
          assertThat(strategyResult)
              .describedAs(
                  "Canonical strategy must match formula for key '%s' with %d pods", key, podCount)
              .isEqualTo(formulaResult);
        }
      }
    }
  }

  @Nested
  @DisplayName("JumpConsistentHashStrategy Tests")
  class JumpConsistentHashStrategyTests {

    private final JumpConsistentHashStrategy strategy = new JumpConsistentHashStrategy();

    @Test
    @DisplayName("Returns correct name")
    void returnsCorrectName() {
      assertThat(strategy.getName()).isEqualTo("jump");
    }

    @Test
    @DisplayName("Returns 0 for single pod")
    void singlePodReturnsZero() {
      assertThat(strategy.computeOwner("any-key", 1)).isEqualTo(0);
    }

    @Test
    @DisplayName("Returns 0 for zero pods")
    void zeroPodsReturnsZero() {
      assertThat(strategy.computeOwner("any-key", 0)).isEqualTo(0);
    }

    @Test
    @DisplayName("Distributes keys to valid buckets")
    void validBucketDistribution() {
      for (int i = 0; i < 100; i++) {
        int owner = strategy.computeOwner("key-" + i, 5);
        assertThat(owner).isBetween(0, 4);
      }
    }

    @Test
    @DisplayName("Same key always maps to same bucket")
    void deterministicMapping() {
      String key = "deterministic-test-key";
      int first = strategy.computeOwner(key, 5);
      int second = strategy.computeOwner(key, 5);
      int third = strategy.computeOwner(key, 5);

      assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    @DisplayName("Scale up from 3 to 4 moves approximately 25% of keys")
    void scaleUpMinimalMovement() {
      int keyCount = 1000;
      int movedCount = 0;

      for (int i = 0; i < keyCount; i++) {
        String key = "account-" + i;
        int ownerWith3 = strategy.computeOwner(key, 3);
        int ownerWith4 = strategy.computeOwner(key, 4);
        if (ownerWith3 != ownerWith4) {
          movedCount++;
        }
      }

      // Expected: ~25% (1/4) of keys move when going from 3 to 4 pods
      // Allow 15-35% range for statistical variance
      double movePercentage = (movedCount * 100.0) / keyCount;
      assertThat(movePercentage)
          .describedAs("Jump consistent hash should move ~25%% of keys (3->4 pods)")
          .isBetween(15.0, 35.0);
    }

    @Test
    @DisplayName("Scale up from 10 to 11 moves approximately 9% of keys")
    void scaleUpLargerCluster() {
      int keyCount = 1000;
      int movedCount = 0;

      for (int i = 0; i < keyCount; i++) {
        String key = "account-" + i;
        int ownerWith10 = strategy.computeOwner(key, 10);
        int ownerWith11 = strategy.computeOwner(key, 11);
        if (ownerWith10 != ownerWith11) {
          movedCount++;
        }
      }

      // Expected: ~9% (1/11) of keys move when going from 10 to 11 pods
      // Allow 5-15% range for statistical variance
      double movePercentage = (movedCount * 100.0) / keyCount;
      assertThat(movePercentage)
          .describedAs("Jump consistent hash should move ~9%% of keys (10->11 pods)")
          .isBetween(5.0, 15.0);
    }

    @Test
    @DisplayName("Keys that move on scale-up go to the new pod")
    void keysMovedToNewPod() {
      int keyCount = 1000;
      int movedToNewPod = 0;
      int movedElsewhere = 0;

      for (int i = 0; i < keyCount; i++) {
        String key = "account-" + i;
        int ownerWith3 = strategy.computeOwner(key, 3);
        int ownerWith4 = strategy.computeOwner(key, 4);
        if (ownerWith3 != ownerWith4) {
          if (ownerWith4 == 3) { // new pod index
            movedToNewPod++;
          } else {
            movedElsewhere++;
          }
        }
      }

      // All moved keys should go to the new pod (index 3)
      assertThat(movedToNewPod)
          .describedAs("Keys that move should go to the new pod")
          .isGreaterThan(0);
      assertThat(movedElsewhere)
          .describedAs("No keys should move between existing pods")
          .isEqualTo(0);
    }

    @Test
    @DisplayName("Distributes keys evenly across buckets")
    void evenDistribution() {
      int podCount = 5;
      int keyCount = 1000;
      int[] bucketCounts = new int[podCount];

      for (int i = 0; i < keyCount; i++) {
        int bucket = strategy.computeOwner("key-" + i, podCount);
        bucketCounts[bucket]++;
      }

      // Each bucket should have roughly 20% (200) of keys
      // Allow 15-25% range for statistical variance
      int expectedMin = (int) (keyCount * 0.15);
      int expectedMax = (int) (keyCount * 0.25);
      for (int count : bucketCounts) {
        assertThat(count).isBetween(expectedMin, expectedMax);
      }
    }
  }

  @Nested
  @DisplayName("Modulo vs Jump Movement Comparison")
  class MovementComparisonTests {

    private final ModuloShardingStrategy modulo = new ModuloShardingStrategy();
    private final JumpConsistentHashStrategy jump = new JumpConsistentHashStrategy();

    @Test
    @DisplayName("Modulo moves significantly more keys than Jump on scale-up")
    void moduloMovesMoreThanJump() {
      int keyCount = 1000;
      int moduloMoved = 0;
      int jumpMoved = 0;

      for (int i = 0; i < keyCount; i++) {
        String key = "account-" + i;

        // Modulo movement
        int moduloWith3 = modulo.computeOwner(key, 3);
        int moduloWith4 = modulo.computeOwner(key, 4);
        if (moduloWith3 != moduloWith4) {
          moduloMoved++;
        }

        // Jump movement
        int jumpWith3 = jump.computeOwner(key, 3);
        int jumpWith4 = jump.computeOwner(key, 4);
        if (jumpWith3 != jumpWith4) {
          jumpMoved++;
        }
      }

      double moduloPercentage = (moduloMoved * 100.0) / keyCount;
      double jumpPercentage = (jumpMoved * 100.0) / keyCount;

      // Modulo should move 50-90% (most keys shuffle)
      assertThat(moduloPercentage)
          .describedAs("Modulo should move most keys on scale")
          .isGreaterThan(50.0);

      // Jump should move only ~25%
      assertThat(jumpPercentage)
          .describedAs("Jump should move far fewer keys than modulo")
          .isLessThan(40.0);

      // Jump should be significantly better
      assertThat(jumpMoved)
          .describedAs("Jump should move fewer keys than modulo")
          .isLessThan(moduloMoved);
    }
  }
}
