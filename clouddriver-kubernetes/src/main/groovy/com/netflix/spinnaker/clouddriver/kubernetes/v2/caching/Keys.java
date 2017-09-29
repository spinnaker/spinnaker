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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class Keys {
  /**
   * Keys are split into "logical" and "infrastructure" kinds. "logical" keys
   * are for spinnaker groupings that exist by naming/moniker convention, whereas
   * "infrastructure" keys correspond to real resources (e.g. replica set, service, ...).
   */
  public enum Kind {
    LOGICAL,
    ARTIFACT,
    INFRASTRUCTURE;

    @Override
    public String toString() {
      return name().toLowerCase();
    }

    public static Kind fromString(String name) {
      return Arrays.stream(values())
          .filter(k -> k.toString().equalsIgnoreCase(name))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("No matching kind with name " + name + " exists"));
    }
  }

  public enum LogicalKind {
    APPLICATION,
    CLUSTER;

    @Override
    public String toString() {
      return name().toLowerCase();
    }

    public static LogicalKind fromString(String name) {
      return Arrays.stream(values())
          .filter(k -> k.toString().equalsIgnoreCase(name))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("No matching kind with name " + name + " exists"));
    }
  }

  private static final String provider = "kubernetes.v2";

  private static String createKey(Object... elems) {
    List<String> components = Arrays.stream(elems)
        .map(s -> s == null ? "" : s.toString())
        .collect(Collectors.toList());
    components.add(0, provider);
    return String.join(":", components);
  }

  public static String artifact(String type, String name, String location, String version) {
    return createKey(Kind.ARTIFACT, type, name, location, version);
  }

  public static String application(String name) {
    return createKey(Kind.LOGICAL, LogicalKind.APPLICATION, name);
  }

  public static String cluster(String account, String name) {
    return createKey(Kind.LOGICAL, LogicalKind.CLUSTER, account, name);
  }

  public static String infrastructure(KubernetesApiVersion version, KubernetesKind kind, String account, String namespace, String name) {
    return createKey(Kind.INFRASTRUCTURE, version, kind, account, namespace, name);
  }

  public static String infrastructure(KubernetesManifest manifest, String account) {
    return infrastructure(manifest.getApiVersion(), manifest.getKind(), account, manifest.getNamespace(), manifest.getName());
  }

  public static Optional<CacheKey> parseKey(String key) {
    String[] parts = key.split(":", -1);

    if (parts.length < 3 || !parts[0].equals(provider)) {
      return Optional.empty();
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
        default:
          throw new IllegalArgumentException("Unknown kind " + kind);
      }
    } catch (IllegalArgumentException e) {
      log.warn("Kubernetes owned kind with unknown key structure '{}': {} (perhaps try flushing all clouddriver:* redis keys)", key, parts, e);
      return Optional.empty();
    }
  }

  private static CacheKey parseLogicalKey(String[] parts) {
    assert(parts.length >= 3);

    LogicalKind logicalKind = LogicalKind.fromString(parts[2]);

    switch (logicalKind) {
      case APPLICATION:
        return new ApplicationCacheKey(parts);
      case CLUSTER:
        return new ClusterCacheKey(parts);
      default:
        throw new IllegalArgumentException("Unknown kind " + logicalKind);
    }
  }

  @Data
  public static abstract class CacheKey {
    private Kind kind;
    public abstract String getGroup();
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class ArtifactCacheKey extends CacheKey {
    private Kind kind = Kind.ARTIFACT;
    private String type;
    private String name;
    private String location;
    private String version;

    public ArtifactCacheKey(String[] parts) {
      if (parts.length != 6) {
        throw new IllegalArgumentException("Malformed artifact key" + Arrays.toString(parts));
      }

      type = parts[2];
      name = parts[3];
      location = parts[4];
      version = parts[5];
    }

    @Override
    public String toString() {
      return createKey(kind, type, name, version);
    }

    @Override
    public String getGroup() {
      return kind.toString();
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class ApplicationCacheKey extends CacheKey {
    private Kind kind = Kind.LOGICAL;
    private LogicalKind logicalKind = LogicalKind.APPLICATION;
    private String name;

    public ApplicationCacheKey(String[] parts) {
      if (parts.length != 4) {
        throw new IllegalArgumentException("Malformed application key" + Arrays.toString(parts));
      }

      name = parts[3];
    }

    @Override
    public String toString() {
      return createKey(kind, logicalKind, name);
    }

    @Override
    public String getGroup() {
      return logicalKind.toString();
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class ClusterCacheKey extends CacheKey {
    private Kind kind = Kind.LOGICAL;
    private LogicalKind logicalKind = LogicalKind.CLUSTER;
    private String account;
    private String name;

    public ClusterCacheKey(String[] parts) {
      if (parts.length != 5) {
        throw new IllegalArgumentException("Malformed cluster key " + Arrays.toString(parts));
      }

      account = parts[3];
      name = parts[4];
    }

    @Override
    public String toString() {
      return createKey(kind, logicalKind, account, name);
    }

    @Override
    public String getGroup() {
      return logicalKind.toString();
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class InfrastructureCacheKey extends CacheKey {
    private Kind kind = Kind.INFRASTRUCTURE;
    private KubernetesKind kubernetesKind;
    private KubernetesApiVersion kubernetesApiVersion;
    private String account;
    private String namespace;
    private String name;

    public InfrastructureCacheKey(String[] parts) {
      if (parts.length != 7) {
        throw new IllegalArgumentException("Malformed infrastructure key " + Arrays.toString(parts));
      }

      kubernetesApiVersion = KubernetesApiVersion.fromString(parts[2]);
      kubernetesKind = KubernetesKind.fromString(parts[3]);
      account = parts[4];
      namespace = parts[5];
      name = parts[6];
    }

    @Override
    public String toString() {
      return createKey(kind, kubernetesKind, kubernetesApiVersion, account, namespace, name);
    }

    @Override
    public String getGroup() {
      return kubernetesKind.toString();
    }
  }
}
