'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { SETTINGS } from '@spinnaker/core';

export const GOOGLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER =
  'spinnaker.gce.pipeline.stage.bake.executionDetails.controller';
export const name = GOOGLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
module(GOOGLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER, [UIROUTER_ANGULARJS]).controller(
  'gceBakeExecutionDetailsCtrl',
  [
    '$scope',
    '$stateParams',
    'executionDetailsSectionService',
    '$interpolate',
    function ($scope, $stateParams, executionDetailsSectionService, $interpolate) {
      $scope.configSections = ['bakeConfig', 'taskStatus', 'artifactStatus'];

      const initialized = () => {
        $scope.detailsSection = $stateParams.details;
        $scope.provider = $scope.stage.context.cloudProviderType || 'gce';
        $scope.roscoMode =
          SETTINGS.feature.roscoMode ||
          (typeof SETTINGS.feature.roscoSelector === 'function' &&
            SETTINGS.feature.roscoSelector($scope.stage.context));
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
