'use strict';

import * as angular from 'angular';

export const CORE_PIPELINE_CONFIG_HEALTH_STAGEPLATFORMHEALTHOVERRIDE_DIRECTIVE =
  'spinnaker.core.pipeline.config.health.stagePlatformHealthOverride.directive';
export const name = CORE_PIPELINE_CONFIG_HEALTH_STAGEPLATFORMHEALTHOVERRIDE_DIRECTIVE; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_HEALTH_STAGEPLATFORMHEALTHOVERRIDE_DIRECTIVE, [])
  .directive('stagePlatformHealthOverride', function () {
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
