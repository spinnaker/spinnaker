import { IComponentOptions, IController, module } from 'angular';

import './manifestLabels.less';

class KubernetesManifestLabels implements IController {
  public manifest: any;

  constructor() {
    'ngInject';
  }
}

class KubernetesManifestLabelsComponent implements IComponentOptions {
  public bindings: any = { manifest: '<' };
  public controller: any = KubernetesManifestLabels;
  public controllerAs = 'ctrl';
  public template = `
    <div class="horizontal wrap">
      <div class="manifest-label sp-badge info" ng-repeat="(k, v) in ctrl.manifest.metadata.labels">
        {{k}}: {{v}}
      </div>
    </div>
  `;
}

export const KUBERNETES_MANIFEST_LABELS = 'spinnaker.kubernetes.v2.manifest.labels';
module(KUBERNETES_MANIFEST_LABELS, []).component('kubernetesManifestLabels', new KubernetesManifestLabelsComponent());
