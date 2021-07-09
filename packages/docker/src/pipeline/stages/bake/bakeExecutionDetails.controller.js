'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { SETTINGS } from '@spinnaker/core';

export const DOCKER_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER =
  'spinnaker.docker.pipeline.stage.bake.executionDetails.controller';
export const name = DOCKER_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
module(DOCKER_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER, [UIROUTER_ANGULARJS]).controller(
  'dockerBakeExecutionDetailsCtrl',
  [
    '$scope',
    '$stateParams',
    'executionDetailsSectionService',
    '$interpolate',
    function ($scope, $stateParams, executionDetailsSectionService, $interpolate) {
      $scope.configSections = ['bakeConfig', 'taskStatus'];

      const initialized = () => {
        $scope.detailsSection = $stateParams.details;
        $scope.provider = $scope.stage.context.cloudProviderType || 'docker';
        $scope.bakeryDetailUrl = $interpolate(
          $scope.roscoMode && SETTINGS.roscoDetailUrl ? SETTINGS.roscoDetailUrl : SETTINGS.bakeryDetailUrl,
        );
      };

      const initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

      initialize();

      $scope.$on('$stateChangeSuccess', initialize);
    },
  ],
);
