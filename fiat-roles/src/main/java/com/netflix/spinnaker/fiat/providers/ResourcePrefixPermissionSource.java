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

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class ResourcePrefixPermissionSource<T extends Resource.AccessControlled>
    implements ResourcePermissionSource<T> {

  public enum ResolutionStrategy {
    AGGREGATE,
    MOST_SPECIFIC
  }

  private List<PrefixEntry<T>> prefixes;
  private ResolutionStrategy resolutionStrategy = ResolutionStrategy.AGGREGATE;

  @Data
  public static class PrefixEntry<T extends Resource.AccessControlled> {
    private String prefix;
    private Permissions permissions;

    private boolean isFullApplicationName;

    public PrefixEntry setPrefix(String prefix) {
      if (StringUtils.isEmpty(prefix)) {
        throw new IllegalArgumentException(
            "Prefix must either end with *, or be a full application name");
      }
      isFullApplicationName = !prefix.endsWith("*");
      this.prefix = prefix;

      return this;
    }

    public PrefixEntry setPermissions(Map<Authorization, List<String>> permissions) {
      this.permissions = Permissions.factory(permissions);
      return this;
    }

    public boolean contains(T resource) {
      if (isFullApplicationName) {
        return prefix.equals(resource.getName());
      }

      String prefixWithoutStar = prefix.substring(0, prefix.length() - 1);
      return resource.getName().startsWith(prefixWithoutStar);
    }
  }

  @Nonnull
  @Override
  public Permissions getPermissions(@Nonnull T resource) {

    List<PrefixEntry<T>> matchingPrefixes =
        prefixes.stream().filter(prefix -> prefix.contains(resource)).collect(Collectors.toList());

    if (matchingPrefixes.isEmpty()) {
      return Permissions.EMPTY;
    }

    switch (resolutionStrategy) {
      case AGGREGATE:
        return getAggregatePermissions(matchingPrefixes);
      case MOST_SPECIFIC:
        return getMostSpecificPermissions(matchingPrefixes);
      default:
        throw new IllegalStateException(
            "Unrecognized Resolution Stratgey " + resolutionStrategy.name());
    }
  }

  private Permissions getAggregatePermissions(List<PrefixEntry<T>> matchingPrefixes) {
    Permissions.Builder builder = new Permissions.Builder();
    for (PrefixEntry<T> prefix : matchingPrefixes) {
      Permissions permissions = prefix.getPermissions();
      if (permissions.isRestricted()) {
        for (Authorization auth : Authorization.values()) {
          builder.add(auth, permissions.get(auth));
        }
      }
    }

    return builder.build();
  }

  private Permissions getMostSpecificPermissions(List<PrefixEntry<T>> matchingPrefixes) {
    return matchingPrefixes.stream()
        .min(
            (p1, p2) -> {
              if (p1.isFullApplicationName()) {
                return -1;
              }
              return p2.getPrefix().length() - p1.getPrefix().length();
            })
        .get()
        .getPermissions();
  }
}
