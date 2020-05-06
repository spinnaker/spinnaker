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

package com.netflix.spinnaker.clouddriver.kubernetes.caching;

import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.Kind.KUBERNETES_METRIC;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Keys {
  /**
   * Keys are split into "logical" and "infrastructure" kinds. "logical" keys are for spinnaker
   * groupings that exist by naming/moniker convention, whereas "infrastructure" keys correspond to
   * real resources (e.g. replica set, service, ...).
   */
  public enum Kind {
    LOGICAL,
    ARTIFACT,
    INFRASTRUCTURE,
    KUBERNETES_METRIC;

    private final String lcName;

    Kind() {
      this.lcName = name().toLowerCase();
    }

    @Override
    public String toString() {
      return lcName;
    }

    @JsonCreator
    public static Kind fromString(String name) {
      try {
        return valueOf(name.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("No matching kind with name " + name + " exists");
      }
    }
  }

  public enum LogicalKind {
    APPLICATIONS,
    CLUSTERS;

    private final String lcName;

    LogicalKind() {
      this.lcName = name().toLowerCase();
    }

    public static boolean isLogicalGroup(String group) {
      return group.equals(APPLICATIONS.toString()) || group.equals(CLUSTERS.toString());
    }

    @Override
    public String toString() {
      return lcName;
    }

    public String singular() {
      String name = toString();
      return name.substring(0, name.length() - 1);
    }

    @JsonCreator
    public static LogicalKind fromString(String name) {
      try {
        return valueOf(name.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("No matching kind with name " + name + " exists");
      }
    }
  }

  private static final String provider = "kubernetes.v2";

  private static String createKeyFromParts(Object... elems) {
    List<String> components =
        Arrays.stream(elems)
            .map(s -> s == null ? "" : s.toString())
            .map(s -> s.contains(":") ? s.replaceAll(":", ";") : s)
            .collect(Collectors.toList());
    components.add(0, provider);
    return String.join(":", components);
  }

  public static Optional<CacheKey> parseKey(String key) {
    String[] parts = key.split(":", -1);

    if (parts.length < 3 || !parts[0].equals(provider)) {
      return Optional.empty();
    }

    for (int i = 0; i < parts.length; i++) {
      if (parts[i].contains(";")) {
        parts[i] = parts[i].replaceAll(";", ":");
      }
    }

    try {
      Kind kind = Kind.fromString(parts[1]);
      switch (kind) {
        case LOGICAL:
          return Optional.of(parseLogicalKey(parts));
        case ARTIFACT:
          return Optional.of(new ArtifactCacheKey(parts));
        case INFRASTRUCTURE:
          return Optional.of(new InfrastructureCacheKey(parts));
        case KUBERNETES_METRIC:
          return Optional.of(new MetricCacheKey(parts));
        default:
          throw new IllegalArgumentException("Unknown kind " + kind);
      }
    } catch (IllegalArgumentException e) {
      log.warn(
          "Kubernetes owned kind with unknown key structure '{}': {} (perhaps try flushing all clouddriver:* redis keys)",
          key,
          parts,
          e);
      return Optional.empty();
    }
  }

  private static CacheKey parseLogicalKey(String[] parts) {
    assert (parts.length >= 3);

    LogicalKind logicalKind = LogicalKind.fromString(parts[2]);

    switch (logicalKind) {
      case APPLICATIONS:
        return new ApplicationCacheKey(parts);
      case CLUSTERS:
        return new ClusterCacheKey(parts);
      default:
        throw new IllegalArgumentException("Unknown kind " + logicalKind);
    }
  }

  @EqualsAndHashCode
  public abstract static class CacheKey {
    @Getter private final String provider = KubernetesCloudProvider.ID;

    public abstract String getGroup();

    public abstract String getName();
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  public abstract static class LogicalKey extends CacheKey {
    @Getter private static final Kind kind = Kind.LOGICAL;

    public abstract LogicalKind getLogicalKind();

    @Override
    public final String getGroup() {
      return getLogicalKind().toString();
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  @RequiredArgsConstructor
  public static class ArtifactCacheKey extends CacheKey {
    @Getter private static final Kind kind = Kind.ARTIFACT;
    private final String type;
    private final String name;
    private final String location;
    private final String version;

    protected ArtifactCacheKey(String[] parts) {
      if (parts.length != 6) {
        throw new IllegalArgumentException("Malformed artifact key" + Arrays.toString(parts));
      }

      type = parts[2];
      name = parts[3];
      location = parts[4];
      version = parts[5];
    }

    public static String createKey(String type, String name, String location, String version) {
      return createKeyFromParts(kind, type, name, location, version);
    }

    @Override
    public String toString() {
      return createKeyFromParts(kind, type, name, location, version);
    }

    @Override
    public String getGroup() {
      return kind.toString();
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  @RequiredArgsConstructor
  public static class ApplicationCacheKey extends LogicalKey {
    private static final LogicalKind logicalKind = LogicalKind.APPLICATIONS;
    private final String name;

    protected ApplicationCacheKey(String[] parts) {
      if (parts.length != 4) {
        throw new IllegalArgumentException("Malformed application key" + Arrays.toString(parts));
      }

      name = parts[3];
    }

    public static String createKey(String name) {
      return createKeyFromParts(getKind(), logicalKind, name);
    }

    @Override
    public LogicalKind getLogicalKind() {
      return logicalKind;
    }

    @Override
    public String toString() {
      return createKeyFromParts(getKind(), logicalKind, name);
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  @RequiredArgsConstructor
  public static class ClusterCacheKey extends LogicalKey {
    private static final LogicalKind logicalKind = LogicalKind.CLUSTERS;
    private final String account;
    private final String application;
    private final String name;

    public ClusterCacheKey(String[] parts) {
      if (parts.length != 6) {
        throw new IllegalArgumentException("Malformed cluster key " + Arrays.toString(parts));
      }

      account = parts[3];
      application = parts[4];
      name = parts[5];
    }

    public static String createKey(String account, String application, String name) {
      return createKeyFromParts(getKind(), logicalKind, account, application, name);
    }

    @Override
    public LogicalKind getLogicalKind() {
      return logicalKind;
    }

    @Override
    public String toString() {
      return createKeyFromParts(getKind(), logicalKind, account, application, name);
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  @RequiredArgsConstructor
  public static class InfrastructureCacheKey extends CacheKey {
    @Getter private static final Kind kind = Kind.INFRASTRUCTURE;
    private final KubernetesKind kubernetesKind;
    private final String account;
    private final String namespace;
    private final String name;

    protected InfrastructureCacheKey(String[] parts) {
      if (parts.length != 6) {
        throw new IllegalArgumentException(
            "Malformed infrastructure key " + Arrays.toString(parts));
      }

      kubernetesKind = KubernetesKind.fromString(parts[2]);
      account = parts[3];
      namespace = parts[4];
      name = parts[5];
    }

    public InfrastructureCacheKey(KubernetesManifest manifest, String account) {
      this(manifest.getKind(), account, manifest.getNamespace(), manifest.getName());
    }

    public static String createKey(
        KubernetesKind kubernetesKind, String account, String namespace, String name) {
      return createKeyFromParts(kind, kubernetesKind, account, namespace, name);
    }

    public static String createKey(KubernetesManifest manifest, String account) {
      return createKey(manifest.getKind(), account, manifest.getNamespace(), manifest.getName());
    }

    @Override
    public String toString() {
      return createKeyFromParts(kind, kubernetesKind, account, namespace, name);
    }

    @Override
    public String getGroup() {
      return kubernetesKind.toString();
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  @RequiredArgsConstructor
  public static class MetricCacheKey extends CacheKey {
    @Getter private static final Kind kind = KUBERNETES_METRIC;
    private final KubernetesKind kubernetesKind;
    private final String account;
    private final String namespace;
    private final String name;

    protected MetricCacheKey(String[] parts) {
      if (parts.length != 6) {
        throw new IllegalArgumentException("Malformed metric key " + Arrays.toString(parts));
      }

      kubernetesKind = KubernetesKind.fromString(parts[2]);
      account = parts[3];
      namespace = parts[4];
      name = parts[5];
    }

    public static String createKey(
        KubernetesKind kubernetesKind, String account, String namespace, String name) {
      return createKeyFromParts(kind, kubernetesKind, account, namespace, name);
    }

    @Override
    public String toString() {
      return createKeyFromParts(kind, kubernetesKind, account, namespace, name);
    }

    @Override
    public String getGroup() {
      return KUBERNETES_METRIC.toString();
    }
  }
}
