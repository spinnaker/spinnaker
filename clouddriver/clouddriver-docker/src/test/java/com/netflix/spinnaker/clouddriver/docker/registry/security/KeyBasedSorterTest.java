/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.docker.registry.security;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.junit.jupiter.api.Test;

final class KeyBasedSorterTest {
  @Test
  void naturalOrderSort() {
    ImmutableList<IntegerWrapper> listToSort = IntegerWrapper.from(2, 0, 7, -100, 27, -38, -2, -3);
    assertThat(KeyBasedSorter.sort(listToSort, IntegerWrapper::getValue, Comparator.naturalOrder()))
        .containsExactlyElementsOf(IntegerWrapper.from(-100, -38, -3, -2, 0, 2, 7, 27));
  }

  @Test
  void reverseOrderSort() {
    ImmutableList<IntegerWrapper> listToSort = IntegerWrapper.from(2, 0, 7, -100, 27, -38, -2, -3);
    assertThat(KeyBasedSorter.sort(listToSort, IntegerWrapper::getValue, Comparator.reverseOrder()))
        .containsExactlyElementsOf(IntegerWrapper.from(27, 7, 2, 0, -2, -3, -38, -100));
  }

  @Test
  void callsKeyFunctionOnce() {
    ImmutableList<IntegerWrapper> listToSort = IntegerWrapper.from(2, 0, 7, -100, 27, -38, -2, -3);

    // Check that no exceptions are thrown by trying to look up the sort key more than once for
    // a given element.
    KeyBasedSorter.sort(listToSort, IntegerWrapper::getSortKey, Comparator.naturalOrder());
  }

  /**
   * Test class that wraps a simple integer used to test sorting. The integer can either be accessed
   * by getInteger or by getSortKey, with the difference being that getSortKey will throw an
   * IllegalStageException when called more than once on the same instance, which is useful for
   * validating that we only extract the sort key once per element per sort.
   */
  @EqualsAndHashCode
  @ToString
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static class IntegerWrapper {
    @EqualsAndHashCode.Exclude private AtomicBoolean sortKeyCalled = new AtomicBoolean(false);

    @Getter private final int value;

    static ImmutableList<IntegerWrapper> from(int... values) {
      return IntStream.of(values).mapToObj(IntegerWrapper::new).collect(toImmutableList());
    }

    int getSortKey() {
      if (sortKeyCalled.getAndSet(true)) {
        throw new IllegalStateException("Sort key can only be called once!");
      }
      return value;
    }
  }
}
