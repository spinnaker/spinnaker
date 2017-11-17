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
    <div class="band band-warning" ng-if="!ctrl.status.available.state"
         uib-tooltip="{{ctrl.status.available.message}}">
      Not Fully Available
    </div>
    <div class="band band-active" ng-if="!ctrl.status.stable.state"
         uib-tooltip="{{ctrl.status.stable.message}}">
      Transitioning
    </div>
    <div class="band band-info" ng-if="ctrl.status.paused.state"
         uib-tooltip="{{ctrl.status.paused.message}}">
      Rollout Paused
    </div>
  `;
}

export const KUBERNETES_MANIFEST_STATUS = 'spinnaker.kubernetes.v2.kubernetes.manifest.status.component';
module(KUBERNETES_MANIFEST_STATUS, [])
  .component('kubernetesManifestStatus', new KubernetesManifestStatusComponent());
