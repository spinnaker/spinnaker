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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.names;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;

public class KubernetesManifestNamer implements Namer<KubernetesManifest> {
  @Override
  public void applyMoniker(KubernetesManifest obj, Moniker moniker) {
    KubernetesManifestAnnotater.annotateManifest(obj, moniker);
  }

  @Override
  public Moniker deriveMoniker(KubernetesManifest obj) {
    return KubernetesManifestAnnotater.getMoniker(obj);
  }
}
