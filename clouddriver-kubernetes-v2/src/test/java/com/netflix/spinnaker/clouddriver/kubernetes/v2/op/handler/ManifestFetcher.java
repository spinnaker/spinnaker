/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Helper class to fetch Kubernetes manifest objects stored as resources on the classpath. Only
 * intended for use in tests.
 */
@NonnullByDefault
final class ManifestFetcher {
  static KubernetesManifest getManifest(String basePath, String overlayPath) {
    KubernetesManifest base = getManifest(basePath);
    KubernetesManifest overlay = getManifest(overlayPath);
    base.putAll(overlay);
    return base;
  }

  static KubernetesManifest getManifest(String basePath) {
    return Yaml.loadAs(getResource(basePath), KubernetesManifest.class);
  }

  private static String getResource(String name) {
    try {
      return Resources.toString(ManifestFetcher.class.getResource(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
