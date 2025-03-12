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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This class implements a Schwartzian transform to sort the elements of an array on a sort key
 * while guaranteeing that the sort key will only be computed once per element, and is thus suitable
 * for cases where computation of the sort key is expensive.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class KeyBasedSorter {
  /**
   * Sorts a collection and returns the result as an array without modifying the input collection.
   * The sort is performed by first extracting a sort key using the supplied extractor, then
   * comparing the sort keys using the supplied comparator.
   *
   * <p>The algorithm is guaranteed to only apply the extractor once per method, and is thus
   * suitable for cases where this computation is expensive. It may execute the extractor in
   * parallel.
   *
   * @param input The collection to sort
   * @param extractor A function to extract a sort key from each element of the input collection
   * @param comparator A comparator that defines an ordering on the sort keys
   * @param <T> The class of objects in the collection to be sorted
   * @param <U> The class of the sort key extracted from objects in the collection
   * @return A list containing the sorted elements of the collection
   */
  public static <T, U> List<T> sort(
      Collection<T> input, Function<T, U> extractor, Comparator<U> comparator) {
    return input.parallelStream()
        .map(t -> new ElementWithComparisonField<>(t, extractor.apply(t)))
        .sorted(Comparator.comparing(ElementWithComparisonField::getComparisonField, comparator))
        .map(ElementWithComparisonField::getElement)
        .collect(Collectors.toList());
  }

  @AllArgsConstructor
  @Getter
  private static class ElementWithComparisonField<P, Q> {
    private P element;
    private Q comparisonField;
  }
}
