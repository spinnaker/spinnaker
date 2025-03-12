'use strict';

import { module } from 'angular';

export const DCOS_COMMON_SELECTFIELD_DIRECTIVE = 'spinnaker.dcos.region.clusterSelectField.directive';
export const name = DCOS_COMMON_SELECTFIELD_DIRECTIVE; // for backwards compatibility
module(DCOS_COMMON_SELECTFIELD_DIRECTIVE, []).directive('clusterSelectField', function () {
  return {
    restrict: 'E',
    templateUrl: require('./selectField.directive.html'),
    scope: {
      clusters: '=',
      component: '=',
      field: '@',
      account: '=',
      provider: '=',
      hideLabel: '=',
      onChange: '&',
      labelColumns: '@',
      fieldColumns: '@',
      readOnly: '=',
    },
  };
});
