import { IComponentOptions, IController, module } from 'angular';

import { IManifestStatus } from '@spinnaker/core';

class KubernetesManifestStatusCtrl implements IController {
  public status: IManifestStatus;
}

const kubernetesManifestStatusComponent: IComponentOptions = {
  bindings: { status: '<' },
  controller: KubernetesManifestStatusCtrl,
  controllerAs: 'ctrl',
  template: `
    <div ng-if="!ctrl.status" class="horizontal middle center spinner-section">
      <loading-spinner size="'small'"></loading-spinner>
    </div>
    <div ng-if="ctrl.status">
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
    </div>
  `,
};

export const KUBERNETES_MANIFEST_STATUS = 'spinnaker.kubernetes.v2.kubernetes.manifest.status.component';
module(KUBERNETES_MANIFEST_STATUS, []).component('kubernetesManifestStatus', kubernetesManifestStatusComponent);
