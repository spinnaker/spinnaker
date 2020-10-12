'use strict';

import { module } from 'angular';

export const AZURE_SUBNET_SUBNETSELECTFIELD_DIRECTIVE = 'spinnaker.azure.subnet.subnetSelectField.directive';
export const name = AZURE_SUBNET_SUBNETSELECTFIELD_DIRECTIVE; // for backwards compatibility
module(AZURE_SUBNET_SUBNETSELECTFIELD_DIRECTIVE, []).directive('azureSubnetSelectField', function () {
  return {
    restrict: 'E',
    templateUrl: require('./subnetSelectField.directive.html'),
    scope: {
      subnets: '=',
      component: '=',
      field: '@',
      region: '=',
      onChange: '&',
      labelColumns: '@',
      helpKey: '@',
      readOnly: '=',
    },
    link: function (scope) {
      function setSubnets() {
        const subnets = scope.subnets || [];
        scope.activeSubnets = subnets.filter(function (subnet) {
          return !subnet.deprecated;
        });
        scope.deprecatedSubnets = subnets.filter(function (subnet) {
          return subnet.deprecated;
        });
      }

      scope.$watch('subnets', setSubnets);
    },
  };
});
