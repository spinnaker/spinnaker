import {module} from 'angular';

class KubernetesKeyValueDetailsComponent implements ng.IComponentOptions {
  public bindings: any = {
    map: '<'
  };
  public template = `
    <div ng-repeat="(key, value) in $ctrl.map">{{key}}: <i>{{value}}</i></div>
  `;
}

export const KUBERNETES_KEY_VALUE_DETAILS = 'spinnaker.kubernetes.key.value.details.component';

module(KUBERNETES_KEY_VALUE_DETAILS, [])
  .component('kubernetesKeyValueDetails', new KubernetesKeyValueDetailsComponent());
