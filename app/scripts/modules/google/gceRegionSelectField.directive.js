'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.google.regionSelectField.directive', [
])
  .directive('gceRegionSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: require('./regionSelectField.directive.html'),
      scope: {
        regions: '=',
        component: '=',
        field: '@',
        account: '=',
        onChange: '&',
        labelColumns: '@',
        readOnly: '=',
      }
    };
});
