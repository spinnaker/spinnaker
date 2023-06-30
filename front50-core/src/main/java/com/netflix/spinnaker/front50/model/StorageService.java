/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.model;

import com.netflix.spinnaker.front50.api.model.Timestamped;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface StorageService {

  /** @return true if the storage service supports versioning. */
  boolean supportsVersioning();

  default boolean supportsEventing(ObjectType objectType) {
    return true;
  }

  <T extends Timestamped> T loadObject(ObjectType objectType, String objectKey)
      throws NotFoundException;

  default <T extends Timestamped> List<T> loadObjects(
      ObjectType objectType, List<String> objectKeys) {
    throw new UnsupportedOperationException();
  }

  /**
   * Loads objects which have the last_modified_at time larger than the provided threshold
   *
   * @param objectType {@link ObjectType} of the objects to be loaded
   * @param lastModifiedThreshold threshold to use for filtering based on the last_modified_at time
   *     in ms
   * @return {@link Map<String, List<T>>} with two keys, "deleted" and "not_deleted". Each value is
   *     a list of fetched objects that satisfy the provided last_modified_at threshold.
   */
  default <T extends Timestamped> Map<String, List<T>> loadObjectsNewerThan(
      ObjectType objectType, long lastModifiedThreshold) {
    throw new UnsupportedOperationException();
  }

  void deleteObject(ObjectType objectType, String objectKey);

  default void bulkDeleteObjects(ObjectType objectType, Collection<String> objectKeys) {
    for (String objectKey : objectKeys) {
      deleteObject(objectType, objectKey);
    }
  }

  <T extends Timestamped> void storeObject(ObjectType objectType, String objectKey, T item);

  Map<String, Long> listObjectKeys(ObjectType objectType);

  <T extends Timestamped> Collection<T> listObjectVersions(
      ObjectType objectType, String objectKey, int maxResults) throws NotFoundException;

  long getLastModified(ObjectType objectType);

  /** How frequently to refresh health information (e.g. for the health endpoint). */
  default long getHealthIntervalMillis() {
    return Duration.ofSeconds(30).toMillis();
  }
}
