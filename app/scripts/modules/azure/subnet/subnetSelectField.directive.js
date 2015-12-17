'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.subnet.subnetSelectField.directive', [

])
  .directive('azureSubnetSelectField', function () {
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
      link: function(scope) {

        function setSubnets() {
          var subnets = scope.subnets || [];
          scope.activeSubnets = subnets.filter(function(subnet) { return !subnet.deprecated; });
          scope.deprecatedSubnets = subnets.filter(function(subnet) { return subnet.deprecated; });
        }

        scope.$watch('subnets', setSubnets);
      }
    };
});
