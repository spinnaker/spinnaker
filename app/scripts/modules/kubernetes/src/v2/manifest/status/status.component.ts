import { IComponentOptions, IController, module } from 'angular';

import { IManifestStatus } from '@spinnaker/core';

class KubernetesManifestStatusCtrl implements IController {
  public status: IManifestStatus;
}

class KubernetesManifestStatusComponent implements IComponentOptions {
  public bindings: any = { status: '<' };
  public controller: any = KubernetesManifestStatusCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <div class="band band-active" ng-if="!ctrl.status.stable"
         uib-tooltip="{{ctrl.status.message}}">
      Transitioning
    </div>
  `;
}

export const KUBERNETES_MANIFEST_STATUS = 'spinnaker.kubernetes.v2.kubernetes.manifest.status.component';
module(KUBERNETES_MANIFEST_STATUS, [])
  .component('kubernetesManifestStatus', new KubernetesManifestStatusComponent());
