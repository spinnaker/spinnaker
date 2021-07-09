import { IComponentOptions, IController, module } from 'angular';

import { IArtifact } from '@spinnaker/core';

class KubernetesManifestArtifactCtrl implements IController {
  public artifact: IArtifact;
}

const kubernetesManifestArtifactComponent: IComponentOptions = {
  bindings: { artifact: '<' },
  controller: KubernetesManifestArtifactCtrl,
  controllerAs: 'ctrl',
  template: `
      <span>
        <b>{{ctrl.artifact.type}}</b>
        <i>{{ctrl.artifact.reference}}</i>
      </span>
  `,
};

export const KUBERNETES_MANIFEST_ARTIFACT = 'spinnaker.kubernetes.v2.manifest.artifact.component';
module(KUBERNETES_MANIFEST_ARTIFACT, []).component('kubernetesManifestArtifact', kubernetesManifestArtifactComponent);
