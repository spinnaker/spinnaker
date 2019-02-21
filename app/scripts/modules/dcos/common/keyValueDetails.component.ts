import { module } from 'angular';

const dcosKeyValueDetailsComponent: ng.IComponentOptions = {
  bindings: {
    map: '<',
  },
  template: `
    <div ng-repeat="(key, value) in $ctrl.map">{{key}}: <i>{{value}}</i></div>
  `,
};

export const DCOS_KEY_VALUE_DETAILS = 'spinnaker.dcos.key.value.details.component';

module(DCOS_KEY_VALUE_DETAILS, []).component('dcosKeyValueDetails', dcosKeyValueDetailsComponent);
