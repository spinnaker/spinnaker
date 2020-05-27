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
package com.netflix.spinnaker.front50.plugins;

import static java.lang.String.format;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A specialized storage service for plugin binaries. */
public interface PluginBinaryStorageService {

  /**
   * Store a new version of a plugin binary.
   *
   * <p>If the key already exists, the storage service should not accept the request.
   *
   * @param key The plugin binary key
   * @param item The plugin binary
   */
  void store(@Nonnull String key, @Nonnull byte[] item);

  /**
   * Deletes an existing plugin binary.
   *
   * @param key The plugin binary key
   */
  void delete(@Nonnull String key);

  /**
   * Get a list of all plugin binaries that are currently stored.
   *
   * @return A list of all plugin binary keys
   */
  @Nonnull
  List<String> listKeys();

  /**
   * Load a single plugin binary, returning its raw data.
   *
   * @param key The plugin binary key
   * @return The plugin binary, if it exists
   */
  @Nullable
  byte[] load(@Nonnull String key);

  /**
   * Create a binary key.
   *
   * @param pluginId The plugin ID.
   * @param version The plugin version.
   * @return The plugin version binary storage key.
   */
  default String getKey(String pluginId, String version) {
    return format("%s/%s.zip", pluginId, version);
  }
}
