/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.kork.annotations.FieldsAreNullableByDefault;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;

@NonnullByDefault
@Value
public class KubernetesCoordinates {
  private final KubernetesKind kind;
  private final String namespace;
  private final String name;

  @Builder(toBuilder = true)
  @ParametersAreNullableByDefault
  private KubernetesCoordinates(@Nonnull KubernetesKind kind, String namespace, String name) {
    this.kind = Objects.requireNonNull(kind);
    this.namespace = Strings.nullToEmpty(namespace);
    this.name = Strings.nullToEmpty(name);
  }

  @FieldsAreNullableByDefault
  public static class KubernetesCoordinatesBuilder {
    @Nonnull private static final Splitter splitter = Splitter.on(' ').limit(3);

    /**
     * Given a full resource name of the type "kind name" (ex: "pod my-rs-v003-mnop"), parses out
     * the kind and the name, and sets the corresponding fields on the builder.
     *
     * @param fullResourceName the full resource name
     * @return this KubernetesCoordinatesBuilder object
     * @throws IllegalArgumentException if the input string does not contain exactly two tokens
     *     separated by a space
     */
    public KubernetesCoordinatesBuilder fullResourceName(String fullResourceName) {
      List<String> parts = splitter.splitToList(fullResourceName);
      if (parts.size() != 2) {
        throw new IllegalArgumentException(
            String.format(
                "Expected a full resource name of the form <kind> <name>. Got: %s",
                fullResourceName));
      }
      this.kind = KubernetesKind.fromString(parts.get(0));
      this.name = parts.get(1);
      return this;
    }
  }
}
