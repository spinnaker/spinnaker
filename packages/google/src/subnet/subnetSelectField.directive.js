'use strict';

import { module } from 'angular';

export const GOOGLE_SUBNET_SUBNETSELECTFIELD_DIRECTIVE = 'spinnaker.google.subnet.subnetSelectField.directive';
export const name = GOOGLE_SUBNET_SUBNETSELECTFIELD_DIRECTIVE; // for backwards compatibility
module(GOOGLE_SUBNET_SUBNETSELECTFIELD_DIRECTIVE, []).directive('gceSubnetSelectField', function () {
  return {
    restrict: 'E',
    templateUrl: require('./subnetSelectField.directive.html'),
    scope: {
      subnets: '=',
      subnetPlaceholder: '=',
      autoCreateSubnets: '=',
      component: '=',
      field: '@',
      account: '=',
      region: '=',
      onChange: '&',
      labelColumns: '@',
      helpKey: '@',
    },
  };
});
