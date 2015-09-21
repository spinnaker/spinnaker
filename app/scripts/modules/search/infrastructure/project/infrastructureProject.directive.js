'use strict';

let angular = require('angular');

require('./infrastructureProject.directive.less');

module.exports = angular.module('spinnaker.search.infrastructure.project.infrastructureProject.directive', [])
  .directive('infrastructureProject', function() {
    return {
      restrict: 'E',
      templateUrl: require('./infrastructureProject.directive.html'),
      scope: {
        projectName: '=',
        applications: '=',
        canRemove: '=',
        onRemove: '&',
      }
    };
  }).name;
