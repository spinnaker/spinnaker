/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.atlas.backends;

import com.netflix.kayenta.atlas.model.AtlasStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AtlasStorageDatabase {

  private Map<String, AtlasStorage> atlasStorages = new HashMap<>();

  public synchronized Optional<String> getGlobalUri(String scheme, String accountId) {
    Optional<String> uri = atlasStorages.entrySet().stream()
      .filter(map -> map.getKey().equals(accountId))
      .findFirst()
      .map(Map.Entry::getValue)
      .map(AtlasStorage::getGlobal)
      .map(s -> scheme + "://" + s);
    return uri;
  }

  public synchronized Optional<String> getRegionalUri(String scheme, String accountId, String region) {
    Optional<String> uri = atlasStorages.entrySet().stream()
      .filter(map -> map.getKey().equals(accountId))
      .findFirst()
      .map(Map.Entry::getValue)
      .flatMap(s -> s.getRegionalCnameForRegion(region))
      .map(s -> scheme + "://" + s);
    return uri;
  }

  public synchronized void update(Map<String, Map<String, AtlasStorage>> newAtlasStorages) {
    if (!newAtlasStorages.containsKey("atlas_storage")) {
      throw new IllegalArgumentException("Expected fetched AtlasStorage URI to contain a top level key 'atlas_storage'");
    }
    atlasStorages = newAtlasStorages.get("atlas_storage");
  }
}
