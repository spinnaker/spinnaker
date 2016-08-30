'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.namespace.selectField.directive', [])
  .directive('namespaceSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: require('./selectField.directive.html'),
      scope: {
        namespaces: '=',
        component: '=',
        field: '@',
        columns: '@',
        account: '=',
        onChange: '&',
        hideLabel: '=',
      }
    };
  });
