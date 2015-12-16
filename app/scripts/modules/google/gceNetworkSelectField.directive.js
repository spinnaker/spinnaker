'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.google.networkSelectField.directive', [])
  .directive('gceNetworkSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: require('./networkSelectField.directive.html'),
      scope: {
        networks: '=',
        component: '=',
        field: '@',
        account: '=',
        onChange: '&',
        labelColumns: '@',
        fieldColumns: '@',
      }
    };
  });
