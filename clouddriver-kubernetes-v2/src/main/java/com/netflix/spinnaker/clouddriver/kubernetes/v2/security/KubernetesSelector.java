/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class KubernetesSelector {
  public enum Kind {
    ANY,
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    EXISTS,
    NOT_EXISTS,
  }

  private final Kind kind;
  private final String key;
  private final List<String> values;

  @JsonCreator
  public KubernetesSelector(
      @JsonProperty("kind") @NotNull Kind kind,
      @JsonProperty("key") String key,
      @JsonProperty("values") List<String> values) {
    if (StringUtils.isEmpty(key) && kind != Kind.ANY) {
      throw new IllegalArgumentException("Only an 'any' selector can have no key specified");
    }

    this.kind = kind;
    this.key = key;
    this.values = values;
  }

  @Override
  public String toString() {
    switch (kind) {
      case ANY:
        return "";
      case EQUALS:
        return String.format("%s = %s", key, values.get(0));
      case NOT_EQUALS:
        return String.format("%s != %s", key, values.get(0));
      case CONTAINS:
        return String.format("%s in (%s)", key, String.join(", ", values));
      case NOT_CONTAINS:
        return String.format("%s notin (%s)", key, String.join(", ", values));
      case EXISTS:
        return String.format("%s", key);
      case NOT_EXISTS:
        return String.format("!%s", key);
      default:
        throw new IllegalStateException("Unknown kind " + kind);
    }
  }

  public static KubernetesSelector any() {
    return new KubernetesSelector(Kind.ANY, null, null);
  }

  public static KubernetesSelector equals(String key, String value) {
    return new KubernetesSelector(Kind.EQUALS, key, Collections.singletonList(value));
  }

  public static KubernetesSelector notEquals(String key, String value) {
    return new KubernetesSelector(Kind.NOT_EQUALS, key, Collections.singletonList(value));
  }

  public static KubernetesSelector contains(String key, List<String> values) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one value must be supplied to a 'contains' selector");
    }

    return new KubernetesSelector(Kind.CONTAINS, key, values);
  }

  public static KubernetesSelector notContains(String key, List<String> values) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one value must be supplied to a 'notcontains' selector");
    }

    return new KubernetesSelector(Kind.NOT_CONTAINS, key, values);
  }

  public static KubernetesSelector exists(String key) {
    return new KubernetesSelector(Kind.EXISTS, key, null);
  }

  public static KubernetesSelector notExists(String key) {
    return new KubernetesSelector(Kind.NOT_EXISTS, key, null);
  }
}
