'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.region.clusterSelectField.directive', [
  ])
  .directive('clusterSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: require('./selectField.directive.html'),
      scope: {
        clusters: '=',
        component: '=',
        field: '@',
        account: '=',
        provider: '=',
        hideLabel: '=',
        onChange: '&',
        labelColumns: '@',
        fieldColumns: '@',
        readOnly: '=',
      },
    };
  });
