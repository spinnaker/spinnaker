'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.google.subnet.subnetSelectField.directive', [
])
  .directive('gceSubnetSelectField', function () {
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
