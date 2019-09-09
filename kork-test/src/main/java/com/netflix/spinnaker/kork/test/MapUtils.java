/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/** Provides a standard library of helper functions for Map types. */
public class MapUtils {
  /**
   * Returns a new map that is the result of deeply merging original and overrides.
   *
   * <p>Each key in original is merged with the corresponding key in overrides as follows: - an
   * explicitly null entry in overrides removes a key in original - a map in original is merged with
   * a map from overrides (via call to merge) - a non map in overrides results in an
   * IllegalStateException - a collection in original is replaced with a collection in overrides - a
   * non collection in overrides results in an IllegalStateException - the value is taken from
   * overrides
   *
   * <p>Each remaining key in overrides is then added to the resulting map.
   *
   * @param original the original Map
   * @param override the Map to override/merge into original
   * @return a new Map containing the merge of original and overrides (never null)
   * @throws IllegalStateException if incompatible types exist between original and overrides
   */
  @Nonnull
  public static Map<String, Object> merge(
      @Nonnull Map<String, Object> original, @Nonnull Map<String, Object> override) {
    final Set<String> remainingKeys = new LinkedHashSet<>(override.keySet());
    final Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : original.entrySet()) {
      final String key = entry.getKey();
      result.put(key, entry.getValue());
      if (override.containsKey(key)) {
        final Object value = override.get(key);
        if (value == null) {
          result.remove(key);
        } else {
          result.merge(key, value, MapUtils::mergeObject);
        }
      }
      remainingKeys.remove(key);
    }
    for (String newKey : remainingKeys) {
      result.put(newKey, override.get(newKey));
    }

    return result;
  }

  private static Object mergeObject(Object original, Object override) {
    if (original instanceof Map) {
      if (!(override instanceof Map)) {
        throw new IllegalMergeTypeException(
            "Attempt to merge Map with " + override.getClass().getSimpleName());
      }

      return merge(toMap(original), toMap(override));
    } else if (original instanceof Collection) {
      if (!(override instanceof Collection)) {
        throw new IllegalMergeTypeException(
            "Attempt to replace Collection with " + override.getClass().getSimpleName());
      }
    }
    return override;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toMap(Object o) {
    return (Map<String, Object>) o;
  }

  public static class IllegalMergeTypeException extends KorkTestException {

    public IllegalMergeTypeException(String message) {
      super(message);
    }
  }
}
