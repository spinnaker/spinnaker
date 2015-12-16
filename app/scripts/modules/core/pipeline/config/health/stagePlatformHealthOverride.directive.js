'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.health.stagePlatformHealthOverride.directive', [
  ])
  .directive('stagePlatformHealthOverride', function() {
    return {
      restrict: 'E',
      templateUrl: require('./stagePlatformHealthOverrideCheckbox.directive.html'),
      scope: {},
      controller: angular.noop,
      controllerAs: 'vm',
      bindToController: {
        stage: '=',
        application: '=',
        platformHealthType: '=',
      },
    };
  });
