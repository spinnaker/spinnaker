'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.namespace.namespaceSelectField.directive', [])
  .directive('namespaceSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: require('./namespaceSelectField.directive.html'),
      scope: {
        namespaces: '=',
        component: '=',
        field: '@',
        account: '=',
        onChange: '&',
      }
    };
  });
