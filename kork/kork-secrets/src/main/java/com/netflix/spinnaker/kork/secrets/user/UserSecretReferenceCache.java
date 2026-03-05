/*
 * Copyright 2024 Apple, Inc.
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

package com.netflix.spinnaker.kork.secrets.user;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches {@link UserSecretReference} URIs by resource id. This is used for tracking secrets used by
 * asynchronously loaded resources.
 */
class UserSecretReferenceCache {
  private final Map<String, Collection<UserSecretReference>> referencesByResourceId =
      new ConcurrentHashMap<>();

  void clearReferences(String resourceId) {
    referencesByResourceId.remove(resourceId);
  }

  void cacheReferences(String resourceId, Collection<UserSecretReference> references) {
    if (references.isEmpty()) {
      clearReferences(resourceId);
    } else {
      referencesByResourceId.put(resourceId, references);
    }
  }

  boolean hasAnyReferences(String resourceId) {
    return referencesByResourceId.containsKey(resourceId);
  }

  Collection<UserSecretReference> getReferences(String resourceId) {
    Collection<UserSecretReference> references = referencesByResourceId.get(resourceId);
    return references != null ? Set.copyOf(references) : Set.of();
  }
}
