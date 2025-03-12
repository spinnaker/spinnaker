/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.api.serde;

import com.netflix.spinnaker.kork.annotations.Beta;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Provides utility methods into Spinnaker's standard internal serialization libraries. */
@Beta
public interface SerdeService {

  /** Serializes the given {@code obj} to a JSON string. */
  @Nonnull
  String toJson(@Nonnull Object obj);

  /** Deserializes the given {@code json} blob into an object of {@code type}. */
  @Nonnull
  <T> T fromJson(@Nonnull String json, @Nonnull Class<T> type);

  /** Attempts to map the given {@code obj} to the given {@code type}. */
  @Nonnull
  <T> T mapTo(@Nonnull Object obj, @Nonnull Class<T> type);

  /**
   * Attempts to map the given {@code obj} at {@code pointer} to the given {@code type}.
   *
   * @param pointer Must be a JSONPath-compatible pointer
   */
  @Nonnull
  <T> T mapTo(@Nullable String pointer, @Nonnull Object obj, @Nonnull Class<T> type);
}
