/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.cats.cache;

import java.util.*;

public class RelationshipCacheFilter implements CacheFilter {
  private final List<String> allowableRelationshipPrefixes;

  private RelationshipCacheFilter(List<String> allowableRelationshipPrefixes) {
    this.allowableRelationshipPrefixes = allowableRelationshipPrefixes;
  }

  /** @return CacheFilter that will filter out all relationships */
  public static RelationshipCacheFilter none() {
    return new RelationshipCacheFilter(Collections.<String>emptyList());
  }

  /**
   * @param relationshipPrefixes Allowable relationship prefixes
   * @return CacheFilter that will filter out all relationships not prefixed with one of the <code>
   *     relationshipPrefixes</code>
   */
  public static RelationshipCacheFilter include(String... relationshipPrefixes) {
    return new RelationshipCacheFilter(Arrays.asList(relationshipPrefixes));
  }

  @Override
  public Collection<String> filter(Type type, Collection<String> identifiers) {
    if (type != Type.RELATIONSHIP) {
      return identifiers;
    }

    Collection<String> filteredIdentifiers = new ArrayList<>();

    for (String identifier : identifiers) {
      for (String allowableRelationshipPrefix : allowableRelationshipPrefixes) {
        if (identifier.startsWith(allowableRelationshipPrefix)) {
          filteredIdentifiers.add(identifier);
        }
      }
    }

    return filteredIdentifiers;
  }

  public List<String> getAllowableRelationshipPrefixes() {
    return allowableRelationshipPrefixes;
  }
}
