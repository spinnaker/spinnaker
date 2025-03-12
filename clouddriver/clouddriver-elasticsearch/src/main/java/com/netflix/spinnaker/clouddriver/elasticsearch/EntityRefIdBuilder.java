/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EntityRefIdBuilder {
  public static EntityRefId buildId(
      String cloudProvider, String entityType, String entityId, String accountId, String region) {
    Objects.requireNonNull(cloudProvider, "cloudProvider must be non-null");
    Objects.requireNonNull(entityType, "entityType must be non-null");
    Objects.requireNonNull(entityId, "entityId must be non-null");

    String id =
        Stream.of(cloudProvider, entityType, entityId, accountId, region)
            .map(s -> Optional.ofNullable(s).orElse("*"))
            .collect(Collectors.joining(":"));

    return new EntityRefId(
        id.toLowerCase(), "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}");
  }

  public static class EntityRefId {
    public final String id;
    public final String idPattern;

    EntityRefId(String id, String idPattern) {
      this.id = id;
      this.idPattern = idPattern;
    }
  }
}
