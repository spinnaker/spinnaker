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
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
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
  /**
   * The namespace kubectl falls back to when a namespace-scoped resource is submitted with no
   * namespace, absent an explicit override. Matches kubectl's own default so that {@link
   * #withDefaultedNamespace(KubernetesCredentials)} validates the namespace a request will actually
   * be applied to.
   */
  public static final String DEFAULT_NAMESPACE = "default";

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

  /**
   * Given a full KubernetesManifest object, parses out the kind, namespace, and name to create a
   * corresponding KubernetesCoordinates object.
   *
   * @param manifest the manifest to parse
   * @return the KubernetesCoordinates object
   */
  public static KubernetesCoordinates fromManifest(KubernetesManifest manifest) {
    return KubernetesCoordinates.builder()
        .kind(manifest.getKind())
        .namespace(manifest.getNamespace())
        .name(manifest.getName())
        .build();
  }

  /**
   * Returns a copy of these coordinates with the namespace resolved to {@link #DEFAULT_NAMESPACE}
   * when it is unset and {@link #kind} is namespace-scoped; otherwise returns these coordinates
   * unchanged.
   *
   * <p>When no namespace is supplied, kubectl silently falls back to the namespace of the
   * kubeconfig context (typically "default"), a target Spinnaker never inspects. Resolving that
   * same default here - before validating against the account's configured namespace allow-list -
   * closes that gap instead of allowing an unspecified namespace to bypass the check entirely.
   */
  public KubernetesCoordinates withDefaultedNamespace(KubernetesCredentials credentials) {
    if (!namespace.isEmpty() || !credentials.getKindProperties(kind).isNamespaced()) {
      return this;
    }
    return toBuilder().namespace(DEFAULT_NAMESPACE).build();
  }
}
