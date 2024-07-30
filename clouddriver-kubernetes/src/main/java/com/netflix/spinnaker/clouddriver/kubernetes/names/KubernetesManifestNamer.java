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

package com.netflix.spinnaker.clouddriver.kubernetes.names;

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestLabeler;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orchestration.OperationDescription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KubernetesManifestNamer implements NamingStrategy<KubernetesManifest> {
  private final boolean applyAppLabels;
  private final String managedBySuffix;

  @Autowired
  public KubernetesManifestNamer(
      @Value("${kubernetes.v2.apply-app-labels:true}") boolean applyAppLabels,
      @Value("${kubernetes.v2.managed-by-suffix:}") String managedBySuffix) {
    this.applyAppLabels = applyAppLabels;
    this.managedBySuffix = managedBySuffix;
  }

  public KubernetesManifestNamer() {
    this(true, "");
  }

  @Override
  public String getName() {
    return "kubernetesAnnotations";
  }

  @Override
  public void applyMoniker(KubernetesManifest obj, Moniker moniker) {
    applyMoniker(obj, moniker, null);
  }

  /**
   * Applies the given Moniker to the specified KubernetesManifest. If the provided
   * OperationDescription is an instance of KubernetesDeployManifestDescription, the method will
   * annotate and label the manifest. If
   * KubernetesDeployManifestDescription.isSkipSpecTemplateLabels() is true, skip applying the
   * Kubernetes and Moniker labels to the manifest's spec.template.metadata.labels. If the
   * OperationDescription is null, or the
   * KubernetesDeployManifestDescription.isSkipSpecTemplateLabels() is false, apply the Kubernetes
   * and Moniker labels to the manifest's spec.template.metadata.labels
   *
   * @param obj the KubernetesManifest to which the moniker will be applied
   * @param moniker the moniker to apply
   * @param description a description expected to be of type KubernetesDeployManifestDescription
   *     that provides context for the operation.
   */
  @Override
  public void applyMoniker(
      KubernetesManifest obj, Moniker moniker, OperationDescription description) {
    // The OperationDescription passed to this method must
    // always have the dynamic type of KubernetesDeployManifestDescription.
    // If not, fail the operation.
    if (description != null && !(description instanceof KubernetesDeployManifestDescription)) {
      throw new IllegalArgumentException(
          String.format(
              "OperationDescription passed to Namer.applyMoniker() must be a KubernetesDeployManifestDescription for the KubernetesDeployManifestOperation. Provided description: %s",
              description.getClass().getName()));
    }
    KubernetesManifestAnnotater.annotateManifest(obj, moniker);
    if (applyAppLabels) {
      KubernetesDeployManifestDescription kubernetesDeployManifestDescription =
          (KubernetesDeployManifestDescription) description;
      boolean skipSpecTemplateLabels =
          (kubernetesDeployManifestDescription != null)
              ? kubernetesDeployManifestDescription.isSkipSpecTemplateLabels()
              : false;
      KubernetesManifestLabeler.labelManifest(
          managedBySuffix, obj, moniker, skipSpecTemplateLabels);
    }
  }

  @Override
  public Moniker deriveMoniker(KubernetesManifest obj) {
    return KubernetesManifestAnnotater.getMoniker(obj);
  }
}
