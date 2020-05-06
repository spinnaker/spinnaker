/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.health;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.List;

/** A stub implementation of KubernetesCredentials, only for use in tests. */
abstract class StubKubernetesCredentials implements KubernetesCredentials {
  static KubernetesCredentials withNamespaces(Iterable<String> namespaces) {
    return new WorkingCredentials(namespaces);
  }

  static KubernetesCredentials withNamespaceException(RuntimeException e) {
    return new FailingCredentials(e);
  }

  @Override
  public ImmutableMap<String, String> getSpinnakerKindMap() {
    return ImmutableMap.of();
  }

  @Override
  public ImmutableList<LinkedDockerRegistryConfiguration> getDockerRegistries() {
    return ImmutableList.of();
  }

  /**
   * An implementation of StubKubernetesCredentials that always returns a static list of namespaces.
   */
  private static final class WorkingCredentials extends StubKubernetesCredentials {
    private final ImmutableList<String> namespaces;

    WorkingCredentials(Iterable<String> namespaces) {
      this.namespaces = ImmutableList.copyOf(namespaces);
    }

    @Override
    public List<String> getDeclaredNamespaces() {
      return namespaces;
    }
  }

  /**
   * An implementation of StubKubernetesCredentials that always returns an exception when requesting
   * the declared namespaces.
   */
  private static final class FailingCredentials extends StubKubernetesCredentials {
    private final RuntimeException exception;

    FailingCredentials(RuntimeException exception) {
      this.exception = exception;
    }

    @Override
    public List<String> getDeclaredNamespaces() {
      throw exception;
    }
  }
}
