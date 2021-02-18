'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { EXECUTION_DETAILS_SECTION_SERVICE, SETTINGS } from '@spinnaker/core';

export const ORACLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER =
  'spinnaker.oracle.pipeline.stage.bake.executionDetails.controller';
export const name = ORACLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER; // for backwards compatibility
module(ORACLE_PIPELINE_STAGES_BAKE_BAKEEXECUTIONDETAILS_CONTROLLER, [
  EXECUTION_DETAILS_SECTION_SERVICE,
  UIROUTER_ANGULARJS,
]).controller('oracleBakeExecutionDetailsCtrl', [
  '$log',
  '$scope',
  '$stateParams',
  'executionDetailsSectionService',
  '$interpolate',
  function ($log, $scope, $stateParams, executionDetailsSectionService, $interpolate) {
    $scope.configSections = ['bakeConfig', 'taskStatus'];

    const initialized = () => {
      $scope.detailsSection = $stateParams.details;
      $scope.provider = $scope.stage.context.cloudProviderType || 'oracle';
      $scope.roscoMode = SETTINGS.feature.roscoMode;
      $scope.bakeryDetailUrl = $interpolate(
        $scope.roscoMode && SETTINGS.roscoDetailUrl ? SETTINGS.roscoDetailUrl : SETTINGS.bakeryDetailUrl,
      );
    };

    const initialize = () => executionDetailsSectionService.synchronizeSection($scope.configSections, initialized);

    initialize();

    $scope.$on('$stateChangeSuccess', initialize);
  },
]);
