'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.google.zoneSelectField.directive', [])
  .directive('gceZoneSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: require('./zoneSelectField.directive.html'),
      scope: {
        zones: '=',
        component: '=',
        field: '@',
        account: '=',
        onChange: '&',
        labelColumns: '@'
      }
    };
  });
