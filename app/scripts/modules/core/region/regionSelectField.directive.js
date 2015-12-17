'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.region.regionSelectField.directive', [
  ])
  .directive('regionSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: require('./regionSelectField.directive.html'),
      scope: {
        regions: '=',
        component: '=',
        field: '@',
        account: '=',
        provider: '=',
        onChange: '&',
        labelColumns: '@',
        fieldColumns: '@',
        readOnly: '=',
      },
    };
  });
