import { module } from 'angular';

const kubernetesKeyValueDetailsComponent: ng.IComponentOptions = {
  bindings: {
    map: '<',
  },
  template: `
    <div ng-repeat="(key, value) in $ctrl.map">{{key}}: <i>{{value}}</i></div>
  `
};

export const KUBERNETES_KEY_VALUE_DETAILS = 'spinnaker.kubernetes.key.value.details.component';

module(KUBERNETES_KEY_VALUE_DETAILS, []).component(
  'kubernetesKeyValueDetails',
  kubernetesKeyValueDetailsComponent,
);
