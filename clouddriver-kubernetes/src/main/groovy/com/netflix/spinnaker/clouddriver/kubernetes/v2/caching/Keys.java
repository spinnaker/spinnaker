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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Keys {
  /**
   * Keys are split into "logical" and "infrastructure" kinds. "logical" keys
   * are for spinnaker groupings that exist by naming/moniker convention, whereas
   * "infrastructure" keys correspond to real resources (e.g. replica set, service, ...).
   */
  public enum Kind {
    LOGICAL,
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
        .map(Object::toString)
        .collect(Collectors.toList());
    components.add(0, provider);
    return String.join(":", components);
  }

  public static String application(String name) {
    return createKey(Kind.LOGICAL, LogicalKind.APPLICATION, name);
  }

  public static String cluster(String account, String application, String name) {
    return createKey(Kind.LOGICAL, LogicalKind.CLUSTER, account, application, name);
  }

  public static String infrastructure(KubernetesKind kind, KubernetesApiVersion version, String account, String application, String namespace, String name) {
    return createKey(Kind.INFRASTRUCTURE, kind, version, account, application, namespace, name);
  }
}
